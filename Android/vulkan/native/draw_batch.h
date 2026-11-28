#pragma once
#include "gpu_raster.h"
enum { DRAW_BATCH_VERTICES=8190 };
typedef void (*DrawBatchEmit)(const GpuState *,const float *,int);
typedef struct {
    GpuState state;
    float vertices[DRAW_BATCH_VERTICES*8];
    int count;
} DrawBatch;
void draw_batch_flush(DrawBatch *batch,DrawBatchEmit emit);
void draw_batch_push(DrawBatch *batch,const GpuState *state,const float *vertices,int count,DrawBatchEmit emit);
