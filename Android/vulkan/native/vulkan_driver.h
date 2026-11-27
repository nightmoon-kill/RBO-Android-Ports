#pragma once

#include <vulkan/vulkan.h>

int vulkan_load_driver(const char *directory, const char *library, const char *hooks, const char *cache);
uint32_t vulkan_driver_api(void);
int vulkan_driver_matches(VkInstance instance, VkPhysicalDevice physical_device);
void vulkan_reset_initialization(void);
