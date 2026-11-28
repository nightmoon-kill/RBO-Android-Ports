#include <vulkan/vulkan.h>

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include "gpu_raster.h"
#include "draw_batch.h"
#include "vulkan_shaders.h"
#ifdef RBO_VULKAN_DRIVERS
#include "vulkan_dispatch.h"
#include "vulkan_driver.h"
#endif

enum {
    TEXTURE_SLOTS = 1024,
    SAMPLERS = 6,
    DESCRIPTOR_LIMIT = 1024,
    ARENA_MIN_SIZE = 8 * 1024 * 1024,
    BLEND_FACTORS = 10
};
#define TARGET_FORMAT VK_FORMAT_B8G8R8A8_UNORM

typedef struct {
    VkBuffer handle;
    VkDeviceMemory memory;
    void *mapped;
    VkDeviceSize size;
} Buffer;

typedef struct {
    VkImage handle;
    VkDeviceMemory memory;
    VkImageView view;
    VkImageLayout layout;
    uint32_t width, height;
} Image;

typedef struct {
    Image color, mask;
    VkDescriptorSet sets[SAMPLERS];
    uint64_t set_epochs[SAMPLERS];
} Texture;

typedef struct {
    float size[4];
    int32_t color_stage[4], alpha_stage[4], test[4];
    float factor[4];
} ShaderState;
_Static_assert(sizeof(ShaderState) == 80, "shader push constant layout");

static VkInstance instance;
static VkPhysicalDevice physical_device;
static VkPhysicalDeviceMemoryProperties memory_properties;
static VkDevice device;
static VkQueue queue;
static VkCommandPool command_pool;
static VkCommandBuffer command_buffer;
static VkFence fence;
static VkDescriptorPool descriptors;
static VkDescriptorSetLayout texture_layout;
static VkPipelineLayout pipeline_layout;
static VkRenderPass render_pass;
static VkFramebuffer framebuffer;
static VkShaderModule vertex_shader, fragment_shader;
static VkPipeline opaque_pipelines[2];
static VkPipeline blend_pipelines[2][BLEND_FACTORS][BLEND_FACTORS];
static VkSampler samplers[SAMPLERS];
static VkPipeline bound_pipeline;

static Image target;
static Texture textures[TEXTURE_SLOTS], dummy;
static Buffer arena, readback;
static VkDeviceSize arena_used;
static uint64_t epoch;
static unsigned descriptor_count;
static int recording, rendering, submitted, initialized, available, failed;
static char error_message[192];
static unsigned long waits, vertices, draws;
static double wait_time_ms;
static DrawBatch pending_draws;
static unsigned long input_draws, readbacks, pipeline_builds;
static double record_ms, readback_ms, pipeline_ms;
static void emit_draw(const GpuState *state,const float *verts,int count);
static void flush_draws(void){draw_batch_flush(&pending_draws,emit_draw);}
static int batching_enabled=1;
void vulkan_set_batching(int enabled){flush_draws();batching_enabled=!!enabled;}


static double clock_ms(void) {
    struct timespec stamp;
    clock_gettime(CLOCK_MONOTONIC, &stamp);
    return stamp.tv_sec * 1000.0 + stamp.tv_nsec / 1e6;
}

static int check(VkResult result, const char *operation) {
    if (result == VK_SUCCESS) return 1;
    if (!failed) {
        snprintf(error_message, sizeof error_message, "%s (VkResult %d)", operation, result);
        fprintf(stderr, "[Vulkan] %s\n", error_message);
    }
    failed = 1;
    return 0;
}

static int memory_type(uint32_t bits, VkMemoryPropertyFlags required, uint32_t *index) {
    for (uint32_t i = 0; i < memory_properties.memoryTypeCount; i++) {
        VkMemoryPropertyFlags flags = memory_properties.memoryTypes[i].propertyFlags;
        if ((bits & (1u << i)) && (flags & required) == required) {
            *index = i;
            return 1;
        }
    }
    return 0;
}

static void free_buffer(Buffer *buffer) {
    if (buffer->mapped) vkUnmapMemory(device, buffer->memory);
    if (buffer->handle) vkDestroyBuffer(device, buffer->handle, NULL);
    if (buffer->memory) vkFreeMemory(device, buffer->memory, NULL);
    *buffer = (Buffer){0};
}

static int make_buffer(Buffer *buffer, VkDeviceSize size, VkBufferUsageFlags usage) {
    VkBufferCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, .size = size, .usage = usage,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE
    };
    if (!check(vkCreateBuffer(device, &info, NULL, &buffer->handle), "create staging buffer")) return 0;
    VkMemoryRequirements requirements;
    vkGetBufferMemoryRequirements(device, buffer->handle, &requirements);
    VkMemoryAllocateInfo alloc = {
        .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = requirements.size
    };
    if (!memory_type(requirements.memoryTypeBits,
                     VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                     &alloc.memoryTypeIndex))
        return check(VK_ERROR_FEATURE_NOT_PRESENT, "find coherent staging memory");
    if (!check(vkAllocateMemory(device, &alloc, NULL, &buffer->memory), "allocate staging memory") ||
        !check(vkBindBufferMemory(device, buffer->handle, buffer->memory, 0), "bind staging memory") ||
        !check(vkMapMemory(device, buffer->memory, 0, VK_WHOLE_SIZE, 0, &buffer->mapped), "map staging memory"))
        return 0;
    buffer->size = size;
    return 1;
}

static const VkImageSubresourceRange color_range = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};

static void free_image(Image *image) {
    if (image->view) vkDestroyImageView(device, image->view, NULL);
    if (image->handle) vkDestroyImage(device, image->handle, NULL);
    if (image->memory) vkFreeMemory(device, image->memory, NULL);
    *image = (Image){0};
}

static int make_image(Image *image, uint32_t width, uint32_t height, VkFormat format,
                      VkImageUsageFlags usage) {
    VkImageCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .imageType = VK_IMAGE_TYPE_2D,
        .format = format, .extent = {width, height, 1}, .mipLevels = 1, .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_OPTIMAL,
        .usage = usage, .sharingMode = VK_SHARING_MODE_EXCLUSIVE
    };
    if (!check(vkCreateImage(device, &info, NULL, &image->handle), "create image")) return 0;
    VkMemoryRequirements requirements;
    vkGetImageMemoryRequirements(device, image->handle, &requirements);
    VkMemoryAllocateInfo alloc = {
        .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = requirements.size
    };
    if (!memory_type(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, &alloc.memoryTypeIndex) &&
        !memory_type(requirements.memoryTypeBits, 0, &alloc.memoryTypeIndex))
        return check(VK_ERROR_FEATURE_NOT_PRESENT, "find image memory");
    if (!check(vkAllocateMemory(device, &alloc, NULL, &image->memory), "allocate image memory") ||
        !check(vkBindImageMemory(device, image->handle, image->memory, 0), "bind image memory"))
        return 0;
    VkImageViewCreateInfo view = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, .image = image->handle,
        .viewType = VK_IMAGE_VIEW_TYPE_2D, .format = format, .subresourceRange = color_range
    };
    if (!check(vkCreateImageView(device, &view, NULL, &image->view), "create image view")) return 0;
    image->width = width;
    image->height = height;
    return 1;
}

static void end_pass(void) {
    if (!rendering) return;
    vkCmdEndRenderPass(command_buffer);
    rendering = 0;
}

void gpu_submit(void) {
    flush_draws();
    if (failed || !recording) return;
    end_pass();
    recording = 0;
    if (!check(vkEndCommandBuffer(command_buffer), "finish commands")) return;
    VkSubmitInfo submission = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1, .pCommandBuffers = &command_buffer
    };
    submitted = check(vkQueueSubmit(queue, 1, &submission, fence), "submit commands");
}

void gpu_sync(void) {
    gpu_submit();
    if (failed || !submitted) return;
    double start = clock_ms();
    check(vkWaitForFences(device, 1, &fence, VK_TRUE, UINT64_MAX), "wait for GPU");
    wait_time_ms += clock_ms() - start;
    waits++;
    submitted = 0;
}

static int begin_commands(void) {
    if (failed) return 0;
    if (recording) return 1;
    gpu_sync();
    if (failed ||
        !check(vkResetFences(device, 1, &fence), "reset fence") ||
        !check(vkResetCommandPool(device, command_pool, 0), "reset commands") ||
        !check(vkResetDescriptorPool(device, descriptors, 0), "reset descriptors")) return 0;
    VkCommandBufferBeginInfo begin = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT
    };
    if (!check(vkBeginCommandBuffer(command_buffer, &begin), "begin commands")) return 0;
    recording = 1;
    arena_used = 0;
    descriptor_count = 0;
    epoch++;
    return 1;
}

static int stage_data(const void *data, VkDeviceSize bytes, VkDeviceSize *offset) {
    if (failed) return 0;
    VkDeviceSize aligned_bytes = (bytes + 15u) & ~(VkDeviceSize)15u;
    if (aligned_bytes > arena.size) {
        gpu_sync();
        if (failed) return 0;
        free_buffer(&arena);
        if (!make_buffer(&arena, aligned_bytes < ARENA_MIN_SIZE ? ARENA_MIN_SIZE : aligned_bytes,
                         VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)) return 0;
    }
    if (!begin_commands()) return 0;
    if (aligned_bytes > arena.size - arena_used || descriptor_count == DESCRIPTOR_LIMIT) {
        gpu_sync();
        if (!begin_commands()) return 0;
    }
    *offset = arena_used;
    memcpy((uint8_t *)arena.mapped + *offset, data, (size_t)bytes);
    arena_used += aligned_bytes;
    return 1;
}

static void image_barrier(Image *image, VkImageLayout layout, VkAccessFlags dst_access,
                          VkPipelineStageFlags dst_stage) {
    VkImageMemoryBarrier barrier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask = image->layout == VK_IMAGE_LAYOUT_UNDEFINED ? 0 :
                         VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
        .dstAccessMask = dst_access, .oldLayout = image->layout, .newLayout = layout,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = image->handle, .subresourceRange = color_range
    };
    vkCmdPipelineBarrier(command_buffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, dst_stage,
                         0, 0, NULL, 0, NULL, 1, &barrier);
    image->layout = layout;
}

static VkBufferImageCopy whole_image(const Image *image, VkDeviceSize offset) {
    return (VkBufferImageCopy){
        .bufferOffset = offset, .imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
        .imageExtent = {image->width, image->height, 1}
    };
}

static void upload(Image *image, const void *pixels, unsigned pixel_bytes, int sampled) {
    flush_draws();
    VkDeviceSize offset, bytes = (VkDeviceSize)image->width * image->height * pixel_bytes;
    if (!stage_data(pixels, bytes, &offset)) return;
    if (pixel_bytes == 1) {
        // The surface cache stores a boolean per pixel. UNORM sampling needs 0 or 255.
        uint8_t *staged_mask = (uint8_t *)arena.mapped + offset;
        for (size_t i = 0; i < bytes; i++) staged_mask[i] = staged_mask[i] ? UINT8_MAX : 0;
    }
    end_pass();
    image_barrier(image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT,
                  VK_PIPELINE_STAGE_TRANSFER_BIT);
    VkBufferImageCopy region = whole_image(image, offset);
    vkCmdCopyBufferToImage(command_buffer, arena.handle, image->handle, image->layout, 1, &region);
    if (sampled)
        image_barrier(image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_SHADER_READ_BIT,
                      VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
}

static int begin_pass(void) {
    if (rendering) return 1;
    image_barrier(&target, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                  VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                  VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
    VkRenderPassBeginInfo pass = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO, .renderPass = render_pass,
        .framebuffer = framebuffer, .renderArea = {{0, 0}, {target.width, target.height}}
    };
    vkCmdBeginRenderPass(command_buffer, &pass, VK_SUBPASS_CONTENTS_INLINE);
    VkViewport viewport = {0, 0, (float)target.width, (float)target.height, 0, 1};
    vkCmdSetViewport(command_buffer, 0, 1, &viewport);
    bound_pipeline = VK_NULL_HANDLE;
    rendering = 1;
    return 1;
}

static VkBlendFactor blend_factor(int code) {
    static const VkBlendFactor factors[] = {
        VK_BLEND_FACTOR_ZERO, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_SRC_COLOR,
        VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR, VK_BLEND_FACTOR_SRC_ALPHA,
        VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK_BLEND_FACTOR_DST_ALPHA,
        VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA, VK_BLEND_FACTOR_DST_COLOR,
        VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR
    };
    return factors[code - 1];
}

static VkPipeline pipeline_for(const GpuState *state) {
    int alpha = !!state->write_alpha;
    int src = state->sf >= 1 && state->sf <= BLEND_FACTORS ? state->sf : 2;
    int dst = state->df >= 1 && state->df <= BLEND_FACTORS ? state->df : 2;
    VkPipeline *pipeline = state->blend ? &blend_pipelines[alpha][src - 1][dst - 1] : &opaque_pipelines[alpha];
    if (*pipeline) return *pipeline;

    VkPipelineShaderStageCreateInfo stages[] = {
        {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
         .stage = VK_SHADER_STAGE_VERTEX_BIT, .module = vertex_shader, .pName = "main"},
        {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
         .stage = VK_SHADER_STAGE_FRAGMENT_BIT, .module = fragment_shader, .pName = "main"}
    };
    VkVertexInputBindingDescription binding = {0, 8 * sizeof(float), VK_VERTEX_INPUT_RATE_VERTEX};
    VkVertexInputAttributeDescription attributes[] = {
        {0, 0, VK_FORMAT_R32G32B32A32_SFLOAT, 0},
        {1, 0, VK_FORMAT_R32G32B32A32_SFLOAT, 4 * sizeof(float)}
    };
    VkPipelineVertexInputStateCreateInfo input = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
        .vertexBindingDescriptionCount = 1, .pVertexBindingDescriptions = &binding,
        .vertexAttributeDescriptionCount = 2, .pVertexAttributeDescriptions = attributes
    };
    VkPipelineInputAssemblyStateCreateInfo assembly = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
    };
    VkPipelineViewportStateCreateInfo viewport = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1, .scissorCount = 1
    };
    VkPipelineRasterizationStateCreateInfo raster = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .polygonMode = VK_POLYGON_MODE_FILL, .lineWidth = 1.0f
    };
    VkPipelineMultisampleStateCreateInfo samples = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT
    };
    VkBlendFactor sf = !alpha && src == 7 ? VK_BLEND_FACTOR_ONE :
                       !alpha && src == 8 ? VK_BLEND_FACTOR_ZERO : blend_factor(src);
    VkBlendFactor df = !alpha && dst == 7 ? VK_BLEND_FACTOR_ONE :
                       !alpha && dst == 8 ? VK_BLEND_FACTOR_ZERO : blend_factor(dst);
    VkPipelineColorBlendAttachmentState attachment = {
        .blendEnable = !!state->blend, .srcColorBlendFactor = sf, .dstColorBlendFactor = df,
        .colorBlendOp = VK_BLEND_OP_ADD, .srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE,
        .dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO, .alphaBlendOp = VK_BLEND_OP_ADD,
        .colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT |
                          (alpha ? VK_COLOR_COMPONENT_A_BIT : 0)
    };
    VkPipelineColorBlendStateCreateInfo blend = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &attachment
    };
    VkDynamicState states[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamic = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
        .dynamicStateCount = 2, .pDynamicStates = states
    };
    VkGraphicsPipelineCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO, .stageCount = 2, .pStages = stages,
        .pVertexInputState = &input, .pInputAssemblyState = &assembly, .pViewportState = &viewport,
        .pRasterizationState = &raster, .pMultisampleState = &samples, .pColorBlendState = &blend,
        .pDynamicState = &dynamic, .layout = pipeline_layout, .renderPass = render_pass
    };
    double start_pipeline = clock_ms();
    if (!check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &info, NULL, pipeline), "create draw pipeline"))
        return VK_NULL_HANDLE;
    pipeline_ms += clock_ms()-start_pipeline; pipeline_builds++;
    return *pipeline;
}

static VkDescriptorSet texture_set(Texture *texture, unsigned sampler) {
    if (texture->set_epochs[sampler] == epoch) return texture->sets[sampler];
    VkDescriptorSetAllocateInfo info = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO, .descriptorPool = descriptors,
        .descriptorSetCount = 1, .pSetLayouts = &texture_layout
    };
    VkDescriptorSet set;
    if (!check(vkAllocateDescriptorSets(device, &info, &set), "allocate texture descriptors")) return VK_NULL_HANDLE;
    VkDescriptorImageInfo images[] = {
        {samplers[sampler], texture->color.view, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL},
        {samplers[sampler], texture->mask.view ? texture->mask.view : dummy.color.view,
         VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL}
    };
    VkWriteDescriptorSet writes[2];
    for (uint32_t i = 0; i < 2; i++) writes[i] = (VkWriteDescriptorSet){
        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = set, .dstBinding = i,
        .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        .pImageInfo = &images[i]
    };
    vkUpdateDescriptorSets(device, 2, writes, 0, NULL);
    texture->sets[sampler] = set;
    texture->set_epochs[sampler] = epoch;
    descriptor_count++;
    return set;
}

void gpu_draw(const GpuState *state, const float *verts, int count) {
    if (failed || !framebuffer || count <= 0 || state->sw <= 0 || state->sh <= 0) return;
    input_draws++;
    if(!batching_enabled){emit_draw(state,verts,count);return;}
    draw_batch_push(&pending_draws,state,verts,count,emit_draw);
}
static void emit_draw(const GpuState *state, const float *verts, int count) {
    if (failed || !framebuffer || count <= 0 || state->sw <= 0 || state->sh <= 0) return;
    double start_record = clock_ms();
    Texture *texture = state->tex_on ? &textures[state->tex_id] : &dummy;
    unsigned sampler = (state->point ? 0 : 3) + state->addr;
    VkPipeline pipeline = pipeline_for(state);
    VkDeviceSize offset;
    if (!pipeline || !stage_data(verts, (VkDeviceSize)count * 8 * sizeof(float), &offset)) return;
    VkDescriptorSet set = texture_set(texture, sampler);
    if (!set) return;
    begin_pass();
    if (bound_pipeline != pipeline) {
        vkCmdBindPipeline(command_buffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        bound_pipeline = pipeline;
    }
    VkRect2D clip = {{state->sx, state->sy}, {(uint32_t)state->sw, (uint32_t)state->sh}};
    ShaderState shader = {
        .size = {(float)target.width, (float)target.height, (float)state->tw, (float)state->th},
        .color_stage = {state->tex_on, state->cop, state->ca1, state->ca2},
        .alpha_stage = {state->aop, state->aa1, state->aa2, state->use_key},
        .test = {state->atest, state->afunc, state->aref, state->point}
    };
    memcpy(shader.factor, state->tfac, sizeof shader.factor);
    vkCmdSetScissor(command_buffer, 0, 1, &clip);
    vkCmdPushConstants(command_buffer, pipeline_layout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                       0, sizeof shader, &shader);
    vkCmdBindDescriptorSets(command_buffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline_layout, 0, 1, &set, 0, NULL);
    vkCmdBindVertexBuffers(command_buffer, 0, 1, &arena.handle, &offset);
    vkCmdDraw(command_buffer, (uint32_t)count, 1, 0, 0);
    vertices += (unsigned)count;
    draws++;
    record_ms += clock_ms()-start_record;
}

void gpu_rt_set(int width, int height) {
    if (failed || (target.width == (uint32_t)width && target.height == (uint32_t)height)) return;
    gpu_sync();
    if (failed) return;
    if (framebuffer) vkDestroyFramebuffer(device, framebuffer, NULL);
    framebuffer = VK_NULL_HANDLE;
    free_image(&target);
    if (!make_image(&target, (uint32_t)width, (uint32_t)height, TARGET_FORMAT,
                    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT)) return;
    VkFramebufferCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO, .renderPass = render_pass,
        .attachmentCount = 1, .pAttachments = &target.view,
        .width = target.width, .height = target.height, .layers = 1
    };
    check(vkCreateFramebuffer(device, &info, NULL, &framebuffer), "create framebuffer");
}

void gpu_rt_upload(const uint32_t *argb, int width, int height) {
    gpu_rt_set(width, height);
    if (!failed) upload(&target, argb, 4, 0);
}

void gpu_rt_read(uint32_t *argb) {
    flush_draws();
    double start_readback = clock_ms();
    if (failed || !target.handle) return;
    VkDeviceSize bytes = (VkDeviceSize)target.width * target.height * 4;
    if (readback.size < bytes) {
        free_buffer(&readback);
        if (!make_buffer(&readback, bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT)) return;
    }
    if (!begin_commands()) return;
    end_pass();
    image_barrier(&target, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_READ_BIT,
                  VK_PIPELINE_STAGE_TRANSFER_BIT);
    VkBufferImageCopy copy = whole_image(&target, 0);
    vkCmdCopyImageToBuffer(command_buffer, target.handle, target.layout, readback.handle, 1, &copy);
    VkBufferMemoryBarrier visibility = {
        .sType = VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER,
        .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT, .dstAccessMask = VK_ACCESS_HOST_READ_BIT,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .buffer = readback.handle, .size = bytes
    };
    vkCmdPipelineBarrier(command_buffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                         0, 0, NULL, 1, &visibility, 0, NULL);
    gpu_sync();
    if (!failed) { memcpy(argb, readback.mapped, (size_t)bytes); readbacks++; readback_ms += clock_ms()-start_readback; }
}

static void free_texture(Texture *texture) {
    free_image(&texture->color);
    free_image(&texture->mask);
    *texture = (Texture){0};
}

void vulkan_texture_release(int slot) {
    if (!available || failed) return;
    Texture *texture = &textures[slot];
    if (!texture->color.handle) return;
    gpu_sync();
    if (!failed) free_texture(texture);
}

void gpu_tex_upload(int slot, const uint32_t *argb, const uint8_t *mask, int width, int height) {
    if (failed) return;
    Texture *texture = &textures[slot];
    if (texture->color.width != (uint32_t)width || texture->color.height != (uint32_t)height ||
        (!!texture->mask.handle != !!mask)) {
        vulkan_texture_release(slot);
        if (failed || !make_image(&texture->color, (uint32_t)width, (uint32_t)height, TARGET_FORMAT,
                                 VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)) return;
        if (mask && !make_image(&texture->mask, (uint32_t)width, (uint32_t)height, VK_FORMAT_R8_UNORM,
                                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)) return;
    }
    upload(&texture->color, argb, 4, 1);
    if (mask && !failed) upload(&texture->mask, mask, 1, 1);
}

static int select_device(uint32_t *family) {
    VkPhysicalDevice devices[16];
    uint32_t count = sizeof devices / sizeof devices[0];
    VkResult result = vkEnumeratePhysicalDevices(instance, &count, devices);
    if (result != VK_INCOMPLETE && !check(result, "enumerate devices")) return 0;
    for (uint32_t i = 0; i < count; i++) {
#ifdef RBO_VULKAN_DRIVERS
        if (!vulkan_driver_matches(instance, devices[i])) continue;
#endif
        VkFormatProperties color, mask;
        vkGetPhysicalDeviceFormatProperties(devices[i], TARGET_FORMAT, &color);
        vkGetPhysicalDeviceFormatProperties(devices[i], VK_FORMAT_R8_UNORM, &mask);
        VkFormatFeatureFlags sampled = VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT |
                                       VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
        VkFormatFeatureFlags attachment = VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT |
                                          VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT;
        if ((color.optimalTilingFeatures & (sampled | attachment)) != (sampled | attachment) ||
            (mask.optimalTilingFeatures & sampled) != sampled) continue;
        VkQueueFamilyProperties queues[32];
        uint32_t queue_count = sizeof queues / sizeof queues[0];
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queue_count, queues);
        for (uint32_t q = 0; q < queue_count; q++) {
            if (!(queues[q].queueFlags & VK_QUEUE_GRAPHICS_BIT)) continue;
            physical_device = devices[i];
            *family = q;
            vkGetPhysicalDeviceMemoryProperties(physical_device, &memory_properties);
            return 1;
        }
    }
    return check(VK_ERROR_FEATURE_NOT_PRESENT, "find compatible graphics device");
}

static int make_queue(uint32_t family) {
    float priority = 1.0f;
    VkDeviceQueueCreateInfo queue_info = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, .queueFamilyIndex = family,
        .queueCount = 1, .pQueuePriorities = &priority
    };
    VkDeviceCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1, .pQueueCreateInfos = &queue_info
    };
    if (!check(vkCreateDevice(physical_device, &info, NULL, &device), "create graphics device")) return 0;
    vkGetDeviceQueue(device, family, 0, &queue);
    VkCommandPoolCreateInfo pool = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, .queueFamilyIndex = family
    };
    if (!check(vkCreateCommandPool(device, &pool, NULL, &command_pool), "create command pool")) return 0;
    VkCommandBufferAllocateInfo commands = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, .commandPool = command_pool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1
    };
    VkFenceCreateInfo fence_info = {.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    return check(vkAllocateCommandBuffers(device, &commands, &command_buffer), "allocate commands") &&
           check(vkCreateFence(device, &fence_info, NULL, &fence), "create fence");
}

static int make_render_pass(void) {
    VkAttachmentDescription attachment = {
        .format = TARGET_FORMAT, .samples = VK_SAMPLE_COUNT_1_BIT,
        .loadOp = VK_ATTACHMENT_LOAD_OP_LOAD, .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE, .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
        .finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
    };
    VkAttachmentReference ref = {0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass = {
        .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1, .pColorAttachments = &ref
    };
    VkRenderPassCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &attachment, .subpassCount = 1, .pSubpasses = &subpass
    };
    return check(vkCreateRenderPass(device, &info, NULL, &render_pass), "create render pass");
}

static int make_layouts(void) {
    VkDescriptorSetLayoutBinding bindings[] = {
        {0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_FRAGMENT_BIT, NULL},
        {1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_FRAGMENT_BIT, NULL}
    };
    VkDescriptorSetLayoutCreateInfo set = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO, .bindingCount = 2, .pBindings = bindings
    };
    if (!check(vkCreateDescriptorSetLayout(device, &set, NULL, &texture_layout), "create texture layout")) return 0;
    VkPushConstantRange push = {VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(ShaderState)};
    VkPipelineLayoutCreateInfo layout = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
        .setLayoutCount = 1, .pSetLayouts = &texture_layout,
        .pushConstantRangeCount = 1, .pPushConstantRanges = &push
    };
    if (!check(vkCreatePipelineLayout(device, &layout, NULL, &pipeline_layout), "create pipeline layout")) return 0;
    VkDescriptorPoolSize capacity = {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, DESCRIPTOR_LIMIT * 2};
    VkDescriptorPoolCreateInfo pool = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO, .maxSets = DESCRIPTOR_LIMIT,
        .poolSizeCount = 1, .pPoolSizes = &capacity
    };
    return check(vkCreateDescriptorPool(device, &pool, NULL, &descriptors), "create descriptor pool");
}

static int make_shader(VkShaderModule *module, const uint32_t *code, size_t bytes) {
    VkShaderModuleCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO, .codeSize = bytes, .pCode = code
    };
    return check(vkCreateShaderModule(device, &info, NULL, module), "load SPIR-V shader");
}

static int make_samplers(void) {
    const VkSamplerAddressMode modes[] = {
        VK_SAMPLER_ADDRESS_MODE_REPEAT, VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT,
        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
    };
    for (int i = 0; i < SAMPLERS; i++) {
        VkFilter filter = i < 3 ? VK_FILTER_NEAREST : VK_FILTER_LINEAR;
        VkSamplerCreateInfo info = {
            .sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO, .magFilter = filter, .minFilter = filter,
            .addressModeU = modes[i % 3], .addressModeV = modes[i % 3], .addressModeW = modes[i % 3]
        };
        if (!check(vkCreateSampler(device, &info, NULL, &samplers[i]), "create sampler")) return 0;
    }
    return 1;
}

void vulkan_raster_shutdown(void) {
    if (device) {
        vkDeviceWaitIdle(device);
        if (command_pool) vkDestroyCommandPool(device, command_pool, NULL);
        if (fence) vkDestroyFence(device, fence, NULL);
        if (descriptors) vkDestroyDescriptorPool(device, descriptors, NULL);
        if (framebuffer) vkDestroyFramebuffer(device, framebuffer, NULL);
        for (int i = 0; i < TEXTURE_SLOTS; i++) free_texture(&textures[i]);
        free_texture(&dummy);
        free_image(&target);
        free_buffer(&arena);
        free_buffer(&readback);
        for (int alpha = 0; alpha < 2; alpha++) {
            if (opaque_pipelines[alpha]) vkDestroyPipeline(device, opaque_pipelines[alpha], NULL);
            for (int src = 0; src < BLEND_FACTORS; src++)
                for (int dst = 0; dst < BLEND_FACTORS; dst++) {
                    VkPipeline pipeline = blend_pipelines[alpha][src][dst];
                    if (pipeline) vkDestroyPipeline(device, pipeline, NULL);
                }
        }
        for (int i = 0; i < SAMPLERS; i++)
            if (samplers[i]) vkDestroySampler(device, samplers[i], NULL);
        if (vertex_shader) vkDestroyShaderModule(device, vertex_shader, NULL);
        if (fragment_shader) vkDestroyShaderModule(device, fragment_shader, NULL);
        if (pipeline_layout) vkDestroyPipelineLayout(device, pipeline_layout, NULL);
        if (texture_layout) vkDestroyDescriptorSetLayout(device, texture_layout, NULL);
        if (render_pass) vkDestroyRenderPass(device, render_pass, NULL);
        vkDestroyDevice(device, NULL);
        device = VK_NULL_HANDLE;
    }
    if (instance) vkDestroyInstance(instance, NULL);
    instance = VK_NULL_HANDLE;
    available = 0;
#ifdef RBO_VULKAN_DRIVERS
    command_pool = VK_NULL_HANDLE;
    fence = VK_NULL_HANDLE;
    descriptors = VK_NULL_HANDLE;
    framebuffer = VK_NULL_HANDLE;
    vertex_shader = VK_NULL_HANDLE;
    fragment_shader = VK_NULL_HANDLE;
    pipeline_layout = VK_NULL_HANDLE;
    texture_layout = VK_NULL_HANDLE;
    render_pass = VK_NULL_HANDLE;
    memset(opaque_pipelines, 0, sizeof opaque_pipelines);
    memset(blend_pipelines, 0, sizeof blend_pipelines);
    memset(samplers, 0, sizeof samplers);
    recording = rendering = submitted = 0;
#endif
}

#ifdef RBO_VULKAN_DRIVERS
void vulkan_reset_initialization(void) {
    initialized = available = failed = 0;
    error_message[0] = '\0';
    waits = vertices = draws = 0;
    wait_time_ms = 0;
    pending_draws.count=0; input_draws=readbacks=pipeline_builds=0;
    record_ms=readback_ms=pipeline_ms=0;
}
#endif

int gpu_init(void) {
    if (initialized) return available;
    initialized = 1;
    VkApplicationInfo app = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .pApplicationName = "RBO Android",
#ifdef RBO_VULKAN_DRIVERS
        .apiVersion = vulkan_driver_api()
#else
        .apiVersion = VK_API_VERSION_1_0
#endif
    };
    VkInstanceCreateInfo info = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    uint32_t family;
    if (!check(vkCreateInstance(&info, NULL, &instance), "create Vulkan instance") ||
        !select_device(&family) || !make_queue(family) || !make_render_pass() || !make_layouts() ||
        !make_shader(&vertex_shader, vulkan_vertex_spv, sizeof vulkan_vertex_spv) ||
        !make_shader(&fragment_shader, vulkan_fragment_spv, sizeof vulkan_fragment_spv) ||
        !make_samplers() ||
        !make_image(&dummy.color, 1, 1, TARGET_FORMAT,
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)) {
        vulkan_raster_shutdown();
        return 0;
    }
    const uint32_t white = UINT32_MAX;
    upload(&dummy.color, &white, 4, 1);
    gpu_sync();
    GpuState opaque = {0};
    GpuState alpha = {.blend = 1, .sf = 5, .df = 6};
    if (failed || !pipeline_for(&opaque) || !pipeline_for(&alpha)) {
        vulkan_raster_shutdown();
        return 0;
    }
    VkPhysicalDeviceProperties properties;
    vkGetPhysicalDeviceProperties(physical_device, &properties);
    fprintf(stderr, "[Vulkan] Drawing device: %s (vendor 0x%x, driver 0x%x)\n",
            properties.deviceName, properties.vendorID, properties.driverVersion);
    available = 1;
    return 1;
}

const char *gpu_error(void) { return error_message; }
int gpu_failed(void) { return failed; }
double gpu_busy_ms(void) { return 0.0; }

void gpu_stats(unsigned long *syncs, double *wait_ms, unsigned long *verts, unsigned long *runs) {
    if (syncs) *syncs = waits;
    if (wait_ms) *wait_ms = wait_time_ms;
    if (verts) *verts = vertices;
    if (runs) *runs = draws;
}

void vulkan_perf_report(char *out,int size) {
    static unsigned long prev_input,prev_draws,prev_reads,prev_pipelines;
    static double prev_record,prev_readback,prev_pipeline;
    snprintf(out,(size_t)size,"[Vulkan perf] input_draws=%lu batches=%lu readbacks=%lu record_ms=%.2f readback_ms=%.2f pipelines=%lu pipeline_ms=%.2f\n",
        input_draws-prev_input,draws-prev_draws,readbacks-prev_reads,record_ms-prev_record,
        readback_ms-prev_readback,pipeline_builds-prev_pipelines,pipeline_ms-prev_pipeline);
    prev_input=input_draws;prev_draws=draws;prev_reads=readbacks;prev_pipelines=pipeline_builds;
    prev_record=record_ms;prev_readback=readback_ms;prev_pipeline=pipeline_ms;
}
