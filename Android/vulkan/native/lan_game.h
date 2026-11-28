#pragma once
#include "hle.h"
int lan_game_active(void);
int lan_game_patch_table(HleEntry *table,unsigned count);
void lan_game_tick(void);
