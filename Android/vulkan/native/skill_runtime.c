#include "skill_runtime.h"
#include "skill_quad.h"
#include "skill_quad_signature.h"
#include <string.h>
#include <stdio.h>
#include <time.h>
extern uint32_t hle_tab_count(void);
enum { RESERVED_SLOT=4095 };
_Static_assert(sizeof(CPU)==136,"0.5.2 ARM64 CPU register ABI");
static int enabled=1,installed;
static void (*dispatch_original)(uint32_t);
static unsigned long calls,fallbacks;
static double elapsed_ms;
static double now_ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1000.+t.tv_nsec/1e6;}
static void skill_dispatch(uint32_t index){
    if(index!=RESERVED_SLOT){dispatch_original(index);return;}
    double start=now_ms();calls++;
    if(!enabled||!skill_quad_apply(gmem,MEM_SIZE,&cpu)){
        /* Exact first two instructions replaced by the eight-byte patch. */
        cpu.r[ECX]=cpu.r[EDI]+12;cpu.r[EDX]=4;cpu.eip=SKILL_QUAD_START+8;fallbacks++;
    }
    elapsed_ms+=now_ms()-start;
}
void skill_runtime_enable(int on){enabled=!!on;}
void skill_runtime_bind(void){
    if(hle_dispatch!=skill_dispatch)dispatch_original=hle_dispatch;
    if(dispatch_original)hle_dispatch=skill_dispatch;
}
void skill_runtime_install(void){
    installed=0;
    if(!gmem||!dispatch_original||hle_tab_count()>RESERVED_SLOT)return;
    uint8_t patch[8]={0xe9,0,0,0,0,0x90,0x90,0x90};
    uint32_t displacement=(HLE_BASE+RESERVED_SLOT*4u)-SKILL_QUAD_START-5;
    memcpy(patch+1,&displacement,4);
    uint8_t *code=gmem+SKILL_QUAD_START;
    if(memcmp(code+8,skill_quad_signature+8,sizeof skill_quad_signature-8))return;
    if(memcmp(code,skill_quad_signature,8)&&memcmp(code,patch,8))return;
    /* An older state can stop between the original LEA and MOV. Do not resume
       that state inside the middle of the new jump displacement. */
    if(enabled&&cpu.eip==SKILL_QUAD_START+3){cpu.r[EDX]=4;cpu.eip=SKILL_QUAD_START+8;}
    memcpy(code,enabled?patch:skill_quad_signature,8);installed=enabled;
    fprintf(stderr,"[Skill native] installed=%d address=0044f340 slot=4095\n",installed);
}
void skill_runtime_report(char *out,int size){
    static unsigned long previous_calls,previous_fallbacks;static double previous_ms;
    snprintf(out,(size_t)size,"[Skill native] active=%d calls=%lu fallback=%lu own_ms=%.2f\n",
            installed,calls-previous_calls,fallbacks-previous_fallbacks,elapsed_ms-previous_ms);
    previous_calls=calls;previous_fallbacks=fallbacks;previous_ms=elapsed_ms;
}
