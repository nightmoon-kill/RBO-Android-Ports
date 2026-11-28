#include "draw_batch.h"
#undef NDEBUG
#include <assert.h>
#include <stdio.h>
#include <string.h>
static DrawBatch batch;
static int calls,total,expect_texture;
static float next_value;
static void emit(const GpuState *s,const float *v,int count){
    assert(s->tex_id==expect_texture);
    draw_batch_flush(&batch,emit); /* Vulkan may sync during staging rollover. */
    for(int i=0;i<count*8;i++)assert(v[i]==next_value++);
    calls++;total+=count;
}
static void push(GpuState *s,int n){
    static float input[9000*8];
    static float input_next;
    for(int i=0;i<n*8;i++)input[i]=input_next++;
    draw_batch_push(&batch,s,input,n,emit);
    memset(input,0,sizeof input); /* queued vertices must be owned snapshots */
}
int main(void){
    GpuState s={0};s.tex_id=4;expect_texture=4;
    for(int i=0;i<100;i++)push(&s,6);
    assert(calls==0);draw_batch_flush(&batch,emit);
    assert(calls==1&&total==600);
    push(&s,3);s.blend=1;push(&s,3);assert(calls==2);
    s.sx=11;push(&s,3);assert(calls==3);
    s.tfac[2]=.5f;push(&s,3);assert(calls==4);
    draw_batch_flush(&batch,emit);
    s.tex_id=7;expect_texture=7;push(&s,8190);push(&s,3);
    assert(batch.count==3);draw_batch_flush(&batch,emit);
    push(&s,9000);assert(batch.count==0);
    assert(total==600+12+8193+9000);
    puts("Batch vertex order, snapshots, blend/scissor/factor boundaries, capacity and reentrant flush passed.");
    return 0;
}
