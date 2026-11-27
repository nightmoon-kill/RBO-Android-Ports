#include <dlfcn.h>
#include <stdio.h>

#include <adrenotools/driver.h>

#include "vulkan_driver.h"
#include "vulkan_dispatch.h"

#define DEFINE(name) PFN_##name rbo_##name;
RBO_VULKAN_COMMANDS(DEFINE)
#undef DEFINE

static int custom;
static PFN_vkGetInstanceProcAddr instance_proc;
static void *active_loader;

int vulkan_load_driver(const char *directory, const char *library, const char *hooks, const char *cache) {
    void *loader;
    if (directory && library) {
        void *adreno = dlopen("libadrenotools.so", RTLD_NOW | RTLD_LOCAL);
        if (!adreno) {
            fprintf(stderr, "[Vulkan driver] Could not load AdrenoTools: %s\n", dlerror());
            return 0;
        }
        __typeof__(&adrenotools_open_libvulkan) open_custom =
                dlsym(adreno, "adrenotools_open_libvulkan");
        loader = open_custom ? open_custom(RTLD_NOW | RTLD_LOCAL, ADRENOTOOLS_DRIVER_CUSTOM,
                                           cache, hooks, directory, library, NULL, NULL) : NULL;
    } else {
        loader = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (!loader) return 0;
    int loaded = 1;
#define LOAD(name) do { \
    rbo_##name = (PFN_##name)dlsym(loader, #name); \
    if (!rbo_##name) { fprintf(stderr, "[Vulkan driver] Missing %s\n", #name); loaded = 0; } \
} while (0);
    RBO_VULKAN_COMMANDS(LOAD)
#undef LOAD
    instance_proc = (PFN_vkGetInstanceProcAddr)dlsym(loader, "vkGetInstanceProcAddr");
    if (!loaded || !instance_proc) {
        dlclose(loader);
        return 0;
    }
    if (active_loader) dlclose(active_loader);
    active_loader = loader;
    custom = directory && library;
    return 1;
}

uint32_t vulkan_driver_api(void) {
    return custom ? VK_API_VERSION_1_2 : VK_API_VERSION_1_0;
}

int vulkan_driver_matches(VkInstance instance, VkPhysicalDevice physical_device) {
    if (!custom) return 1;
    VkPhysicalDeviceProperties ordinary;
    vkGetPhysicalDeviceProperties(physical_device, &ordinary);
    if (ordinary.apiVersion < VK_API_VERSION_1_2) return 0;

    PFN_vkGetPhysicalDeviceProperties2 get_properties = (PFN_vkGetPhysicalDeviceProperties2)
        instance_proc(instance, "vkGetPhysicalDeviceProperties2");
    if (!get_properties) return 0;
    VkPhysicalDeviceDriverProperties driver = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES
    };
    VkPhysicalDeviceProperties2 properties = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, .pNext = &driver
    };
    get_properties(physical_device, &properties);
    fprintf(stderr, "[Vulkan driver] Reported %s (%u): %s\n", driver.driverName,
            (unsigned)driver.driverID, driver.driverInfo);
    return driver.driverID == VK_DRIVER_ID_MESA_TURNIP;
}
