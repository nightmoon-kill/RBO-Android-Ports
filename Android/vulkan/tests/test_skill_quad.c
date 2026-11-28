/* Compare production native code against execution of the supplied EX3 bytes
   by the real x86 interpreter. No duplicate math implementation is used. */
#include "skill_quad.h"
#include "hle.h"
#include "skill_runtime.h"
#include "skill_quad_signature.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#undef NDEBUG
#include <assert.h>
volatile int g_state_req,g_paused,g_cheat_flag;
void state_service(void){abort();}void hle_pause_wait(void){abort();}
void cheat_service(void){abort();}void sched_switch(void){abort();}void sched_thread_exit(void){abort();}
uint32_t hle_tab_count(void){return 500;}
static uint32_t seed=47813;
static uint32_t random32(void){seed^=seed<<13;seed^=seed>>17;seed^=seed<<5;return seed;}
static uint32_t load32(FILE *f,long offset){uint32_t v;fseek(f,offset,SEEK_SET);assert(fread(&v,4,1,f)==1);return v;}
static uint16_t load16(FILE *f,long offset){uint16_t v;fseek(f,offset,SEEK_SET);assert(fread(&v,2,1,f)==1);return v;}
static void write_float(uint32_t a,float v){memcpy(gmem+a,&v,4);}
static void stop(uint32_t index){assert(index==100||index==101);cpu.eip=index==100?SKILL_QUAD_VISIBLE:SKILL_QUAD_CULLED;cpu.halted=1;}
static void jump(uint32_t address,uint32_t to){gmem[address]=0xe9;wr32(address+1,to-address-5);}
int main(int argc,char **argv){
    if(argc!=2)return 2;
    const size_t size=16*1024*1024;
    gmem=calloc(1,size);assert(gmem);
    FILE *f=fopen(argv[1],"rb");assert(f);
    uint32_t pe=load32(f,0x3c);unsigned count=load16(f,pe+6),optional=load16(f,pe+20);
    for(unsigned i=0;i<count;i++){
        long header=pe+24+optional+i*40;
        uint32_t address=load32(f,header+12)+0x400000,bytes=load32(f,header+16),offset=load32(f,header+20);
        if(address>=size)continue;if(bytes>size-address)bytes=(uint32_t)(size-address);
        fseek(f,offset,SEEK_SET);assert(fread(gmem+address,1,bytes,f)==bytes);
    }
    fclose(f);hle_dispatch=stop;
    jump(SKILL_QUAD_VISIBLE,HLE_BASE+100*4);jump(SKILL_QUAD_CULLED,HLE_BASE+101*4);
    unsigned visible=0,culled=0;CPU last_input={0},last_reference={0};
    unsigned char last_before[512],last_expected[512];
    for(int test=0;test<30000;test++){
        CPU input={0};for(int i=0;i<8;i++){input.r[i]=random32();input.st[i]=(int32_t)random32()*.001;}
        input.eip=SKILL_QUAD_START;input.fcw=(uint16_t)(random32()&0xfff);input.fsw_cc=(uint16_t)random32();input.top=test%8;
        input.CF=input.PF=input.AF=input.ZF=input.SF=input.OF=input.DF=test%2;
        input.r[ESI]=0x600000;input.r[EDI]=0x600080;input.r[ESP]=0x610000;
        input.r[EBP]=0x620000;input.r[EBX]=0x630000;
        wr32(0x6101d4,0x640000);wr32(0x640808,0x650000);wr32(0x656e90,0x660000);
        wr32(0x630024,random32());wr32(0x610020,random32());
        unsigned index=random32()%20;wr16(0x620014,(uint16_t)index);wr32(0x660000+index*4,random32());
        for(int i=0;i<512;i++)gmem[0x600000+i]=(uint8_t)random32();
        for(int i=0;i<4;i++){
            float x=((int)(random32()%40000)-20000)*.1f,y=((int)(random32()%40000)-20000)*.1f;
            float z=((int)(random32()%30000)-10000)*.01f;
            if(test%10==0)z=i==3?0:z;
            if(test%23==0)z=i==3?NAN:z;
            if(test%37==0)z=i==3?-0.f:z;
            if(test%47==0)x=INFINITY;
            if(test%53==0)y=NAN;
            write_float(0x600080+i*32,x);write_float(0x600084+i*32,y);write_float(0x600088+i*32,z);
        }
        unsigned char before[512],expected[512];memcpy(before,gmem+0x600000,512);
        cpu=input;cpu_run(0);CPU reference=cpu;reference.halted=0;reference.icount=0;
        memcpy(expected,gmem+0x600000,512);memcpy(gmem+0x600000,before,512);
        CPU native=input;assert(skill_quad_apply(gmem,size,&native));
        if(memcmp(expected,gmem+0x600000,512)||memcmp(&reference,&native,sizeof native)){
            fprintf(stderr,"Mismatch case %d branch ref=%x native=%x flags=%d%d%d%d/%d%d%d%d\n",test,reference.eip,native.eip,
                reference.ZF,reference.PF,reference.SF,reference.CF,native.ZF,native.PF,native.SF,native.CF);
            for(unsigned k=0;k<sizeof native;k++)if(((unsigned char*)&reference)[k]!=((unsigned char*)&native)[k])fprintf(stderr,"CPU byte %u: %02x vs %02x\n",k,((unsigned char*)&reference)[k],((unsigned char*)&native)[k]);
            return 1;
        }
        if(native.eip==SKILL_QUAD_VISIBLE)visible++;else culled++;
        last_input=input;last_reference=reference;memcpy(last_before,before,512);memcpy(last_expected,expected,512);
    }
    skill_runtime_bind();skill_runtime_enable(1);cpu=last_input;skill_runtime_install();
    assert(gmem[SKILL_QUAD_START]==0xe9);
    memcpy(gmem+0x600000,last_before,512);cpu=last_input;cpu_run(0);cpu.halted=0;cpu.icount=0;
    assert(!memcmp(&cpu,&last_reference,sizeof cpu)&&!memcmp(gmem+0x600000,last_expected,512));
    skill_runtime_enable(0);skill_runtime_install();
    assert(!memcmp(gmem+SKILL_QUAD_START,skill_quad_signature,8));
    memcpy(gmem+0x600000,last_before,512);cpu=last_input;cpu_run(0);cpu.halted=0;cpu.icount=0;
    assert(!memcmp(&cpu,&last_reference,sizeof cpu)&&!memcmp(gmem+0x600000,last_expected,512));
    /* Restore an older CPU snapshot stopped at the MOV in the patched prologue. */
    skill_runtime_enable(1);cpu=last_input;cpu.eip=SKILL_QUAD_START+3;cpu.r[ECX]=cpu.r[EDI]+12;
    memcpy(gmem+0x600000,last_before,512);skill_runtime_install();
    assert(cpu.eip==SKILL_QUAD_START+8);cpu_run(0);cpu.halted=0;cpu.icount=0;
    assert(!memcmp(&cpu,&last_reference,sizeof cpu)&&!memcmp(gmem+0x600000,last_expected,512));
    CPU invalid=cpu;invalid.r[EDI]++;CPU snapshot=invalid;
    assert(!skill_quad_apply(gmem,size,&invalid)&&!memcmp(&invalid,&snapshot,sizeof invalid));
    /* Guard rejection must execute the original body, including its prologue. */
    CPU unusual=last_input;unusual.r[EDI]+=4;
    memcpy(gmem+SKILL_QUAD_START,skill_quad_signature,8);
    memcpy(gmem+0x600000,last_before,512);cpu=unusual;cpu_run(0);
    CPU fallback_reference=cpu;fallback_reference.halted=0;fallback_reference.icount=0;
    unsigned char fallback_memory[512];memcpy(fallback_memory,gmem+0x600000,512);
    memcpy(gmem+0x600000,last_before,512);cpu=unusual;skill_runtime_install();cpu_run(0);cpu.halted=0;cpu.icount=0;
    assert(!memcmp(&cpu,&fallback_reference,sizeof cpu)&&!memcmp(gmem+0x600000,fallback_memory,512));
    printf("EX3 equivalence: 30000 quads, visible=%u culled=%u; float/register/flags/FPU match; installed hook, OFF and mid-prologue state restore passed.\n",visible,culled);
    free(gmem);return 0;
}
