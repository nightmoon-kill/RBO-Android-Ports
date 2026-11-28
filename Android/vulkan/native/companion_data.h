#pragma once
#include <stddef.h>
#include <stdint.h>

#define COMPANION_PLAYERS 1
#define COMPANION_SPECIES 128
#define COMPANION_PLAYER_WORDS 24
#define COMPANION_WORDS (8 + COMPANION_PLAYERS * COMPANION_PLAYER_WORDS + COMPANION_SPECIES * 2)

/* Pure, bounded decoder. Call only on the emulator thread. */
void companion_decode(const uint8_t *memory, size_t size, int frame, int32_t *out);
void companion_capture(void);
void companion_reset(void);
