#pragma once
#include "hle.h"
hle_fn asset_io_wrap(const char *dll,const char *name,hle_fn original);
void asset_io_reset(void);
void asset_io_set_enabled(int enabled);
int asset_io_patch_table(HleEntry *table,unsigned count);
void asset_io_stats(char *out,int size);
