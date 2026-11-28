#pragma once
#include "cpu.h"
enum { SKILL_QUAD_START=0x44f340,SKILL_QUAD_VISIBLE=0x44f518,SKILL_QUAD_CULLED=0x44f536 };
/* Exactly reproduces the emulated double arithmetic and float store boundaries.
   Returns 0 without modifying memory/registers when the guarded layout differs. */
int skill_quad_apply(uint8_t *memory,size_t size,CPU *state);
