#pragma once
#include <stdint.h>
#include <stddef.h>
/* Bits: 1 stat points, 2 skill points. Called only on the emulator thread. */
int point_cheats_apply(uint8_t *memory, size_t size, unsigned mask);
