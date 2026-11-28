#include "draw_batch.h"
#include <string.h>
void draw_batch_flush(DrawBatch *batch,DrawBatchEmit emit){
    int count=batch->count;
    if(!count)return;
    batch->count=0; /* emit may flush a full Vulkan staging arena recursively */
    emit(&batch->state,batch->vertices,count);
}
void draw_batch_push(DrawBatch *batch,const GpuState *state,const float *vertices,int count,DrawBatchEmit emit){
    if(count<=0)return;
    if(count>DRAW_BATCH_VERTICES||count%3){
        draw_batch_flush(batch,emit);emit(state,vertices,count);return;
    }
    if(batch->count&&(memcmp(&batch->state,state,sizeof *state)||count>DRAW_BATCH_VERTICES-batch->count))
        draw_batch_flush(batch,emit);
    if(!batch->count)batch->state=*state;
    memcpy(batch->vertices+batch->count*8,vertices,(size_t)count*8*sizeof *vertices);
    batch->count+=count;
}
