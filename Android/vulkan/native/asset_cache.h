#pragma once
#include <stddef.h>
#include <stdint.h>
typedef struct AssetFile AssetFile;
AssetFile *asset_cache_open(const char *path);
void asset_cache_close(AssetFile *file);
/* Positional reads never touch a guest handle or the original file cursor. */
int64_t asset_cache_read(AssetFile *file, uint64_t offset, void *out, size_t size);
void asset_cache_prefetch(AssetFile *file, uint64_t offset);
uint64_t asset_cache_size(const AssetFile *file);
void asset_cache_stats(char *out, int size);
