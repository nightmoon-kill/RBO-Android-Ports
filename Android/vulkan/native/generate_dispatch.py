"""Generate the Vulkan 1.0 entry points used by the renderer."""
import re
import sys
from pathlib import Path

raster, output = map(Path, sys.argv[1:])
names = sorted(set(re.findall(r"\b(vk[A-Z][A-Za-z0-9]*)\s*\(", raster.read_text())))
header = [
    "#pragma once",
    "#include <vulkan/vulkan.h>",
    "#define RBO_VULKAN_COMMANDS(X) " + " ".join(f"X({name})" for name in names),
    "#define RBO_VULKAN_DECLARE(name) extern PFN_##name rbo_##name;",
    "RBO_VULKAN_COMMANDS(RBO_VULKAN_DECLARE)",
    "#undef RBO_VULKAN_DECLARE",
    *[f"#define {name} rbo_{name}" for name in names],
]
output.write_text("\n".join(header) + "\n")
