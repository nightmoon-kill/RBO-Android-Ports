/* Production HLE wrappers with fake original handlers; no guest instruction
   interpreter is needed to verify original cursor and save-state behavior. */
#include "asset_io.h"
#include "asset_cache.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#undef NDEBUG
#include <assert.h>
CPU cpu;
static uint8_t memory[2*1024*1024];
uint8_t *gmem=memory;
const char *g_root;
static FILE *handles[256];
static int original_reads;
void gfault(const char *what,uint32_t at){fprintf(stderr,"%s %u",what,at);abort();}
int resolve_path(const char *name,char *out,size_t size){
    if(!strncmp(name,".\\",2))name+=2;
    snprintf(out,size,"%s/%s",g_root,name);
    for(char *p=out;*p;p++)if(*p=='\\')*p='/';
    return 1;
}
static void original_create(void){
    char path[1024];resolve_path((char*)gmem+A(0),path,sizeof path);
    int slot=1;while(slot<256&&handles[slot])slot++;
    FILE *f=fopen(path,"rb");
    if(!f||slot==256){if(f)fclose(f);RET(0xffffffffu);return;}
    handles[slot]=f;RET(0x1000+slot*4);
}
static void original_read(void){
    unsigned slot=(A(0)-0x1000)/4;assert(slot<256&&handles[slot]);
    size_t n=fread(gmem+A(1),1,A(2),handles[slot]);original_reads++;
    if(A(3))wr32(A(3),(uint32_t)n);RET(1);
}
static void original_seek(void){
    unsigned slot=(A(0)-0x1000)/4;
    assert(slot<256&&handles[slot]);
    int wh=A(3)==0?SEEK_SET:A(3)==1?SEEK_CUR:SEEK_END;
    if(fseek(handles[slot],(int32_t)A(1),wh)){RET(0xffffffffu);return;}
    RET(ftell(handles[slot]));
}
static void original_close(void){
    unsigned slot=(A(0)-0x1000)/4;assert(slot<256&&handles[slot]);
    fclose(handles[slot]);handles[slot]=NULL;RET(1);
}
static uint32_t call(hle_fn fn,uint32_t a,uint32_t b,uint32_t c,uint32_t d,uint32_t e){
    cpu.r[ESP]=0x10000;
    uint32_t args[]={0xDEADBEEF,a,b,c,d,e};
    memcpy(gmem+cpu.r[ESP],args,sizeof args);
    fn();assert(!memcmp(gmem+cpu.r[ESP],args,sizeof args));
    return cpu.r[EAX];
}
int main(int argc,char **argv){
    if(argc!=2)return 2;g_root=argv[1];
    HleEntry table[]={
        {"KERNEL32.dll","CreateFileA",7,original_create},
        {"KERNEL32.dll","ReadFile",5,original_read},
        {"KERNEL32.dll","SetFilePointer",4,original_seek},
        {"KERNEL32.dll","CloseHandle",1,original_close}
    };
    assert(asset_io_patch_table(table,4)==4);
    assert(asset_io_patch_table(table,4)==4); /* idempotent, never wraps itself */
    hle_fn create=table[0].fn,read=table[1].fn,seek=table[2].fn,close=table[3].fn;
    strcpy((char*)gmem+0x1000,".\\DATA\\skill.PAC");
    uint32_t handle=call(create,0x1000,0x80000000,0,0,3);
    assert(handle!=0xffffffffu);
    assert(call(read,handle,0x20000,100,0x1800,0)==1&&rd32(0x1800)==100);
    assert(original_reads==0&&ftell(handles[(handle-0x1000)/4])==100);
    for(int i=0;i<100;i++)assert(gmem[0x20000+i]==(uint8_t)i);
    assert(call(seek,handle,(uint32_t)-10,0,1,0)==90);
    call(read,handle,0x20000,100,0x1800,0);
    for(int i=0;i<100;i++)assert(gmem[0x20000+i]==(uint8_t)(90+i));
    assert(ftell(handles[(handle-0x1000)/4])==190);
    call(seek,handle,128*1024-7,0,0,0);
    call(read,handle,0x20000,200000,0x1800,0);
    assert(rd32(0x1800)==200000&&ftell(handles[(handle-0x1000)/4])==128*1024-7+200000);
    for(int i=0;i<200000;i++)assert(gmem[0x20000+i]==(uint8_t)(128*1024-7+i));
    /* Simulate a state restore to another original file cursor. */
    fseek(handles[(handle-0x1000)/4],11,SEEK_SET);asset_io_reset();
    call(read,handle,0x20000,20,0x1800,0);
    assert(original_reads==1&&gmem[0x20000]==11);
    call(close,handle,0,0,0,0);
    /* A reopened real handle is cached again, and original state remains valid. */
    handle=call(create,0x1000,0x80000000,0,0,3);
    call(seek,handle,(uint32_t)-10,0,2,0);
    call(read,handle,0x20000,100,0x1800,0);
    assert(rd32(0x1800)==10);
    call(read,handle,0x20000,100,0x1800,0);assert(rd32(0x1800)==0);
    call(close,handle,0,0,0,0);
    /* Writable opens and save-data paths always retain original handlers. */
    handle=call(create,0x1000,0xC0000000,0,0,3);
    call(read,handle,0x20000,10,0x1800,0);assert(original_reads==2);
    call(close,handle,0,0,0,0);
    strcpy((char*)gmem+0x1000,".\\DATA\\SaveData\\save.PAC");
    handle=call(create,0x1000,0x80000000,0,0,3);
    call(read,handle,0x20000,10,0x1800,0);assert(original_reads==3);
    call(close,handle,0,0,0,0);
    asset_io_set_enabled(0);
    assert(asset_io_wrap("KERNEL32.dll","ReadFile",original_read)==original_read);
    assert(asset_io_wrap("KERNEL32.dll","CreateFileA",original_create)==original_create);
    puts("Asset HLE: bytes/cursor/EOF/stack/close/reopen/state restore/write/save bypass passed.");
    return 0;
}
