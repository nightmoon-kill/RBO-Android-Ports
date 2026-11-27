"""Embed the Vulkan shaders as aligned C arrays of SPIR-V words."""
from pathlib import Path
import struct
import sys

build = Path(sys.argv[1])
header = ["#pragma once", "#include <stdint.h>"]
for stage, symbol in (("vert", "vulkan_vertex_spv"), ("frag", "vulkan_fragment_spv")):
    data = (build / f"d3d.{stage}.spv").read_bytes()
    words = [word for (word,) in struct.iter_unpack("<I", data)]
    if not words or words[0] != 0x07230203:
        raise ValueError(f"Invalid SPIR-V for {stage}")
    header.append(f"static const uint32_t {symbol}[] = {{")
    for i in range(0, len(words), 8):
        header.append("    " + ", ".join(f"0x{word:08x}" for word in words[i:i + 8]) + ",")
    header.append("};")
(build / "vulkan_shaders.h").write_text("\n".join(header) + "\n")
