#include "asset_io.h"
#include "asset_cache.h"
#include <string.h>
#include <strings.h>
#include <limits.h>
#include <stdio.h>

static hle_fn create_file,read_file,seek_file,close_file;
typedef struct { AssetFile *file; uint64_t position; } Mirror;
static Mirror mirrors[256];
static int enabled=1;
static int installed;
static unsigned long opens,cached_opens,cached_reads,original_reads,resets;
void asset_io_set_enabled(int value){enabled=!!value;}
static Mirror *mirror(uint32_t handle){
    if(handle<0x1004||(handle-0x1000)%4)return NULL;
    unsigned index=(handle-0x1000)/4;
    return index<256?mirrors+index:NULL;
}
static void forget(Mirror *m){if(m){asset_cache_close(m->file);*m=(Mirror){0};}}
void asset_io_reset(void){for(unsigned i=0;i<256;i++)forget(mirrors+i);resets++;}
static int immutable_asset(const char *p){
    char path[1024];size_t i=0;
    for(;p[i]&&i<sizeof path-1;i++){
        char c=p[i]=='\\'?'/':p[i];path[i]=c>='A'&&c<='Z'?c+32:c;
    }
    path[i]=0;
    const char *key=path;
    if(!strncmp(key,"./",2))key+=2;
    if(strncmp(key,"data/",5)||strstr(key,"/../")||strstr(key,"/savedata/"))return 0;
    const char *dot=strrchr(key,'.');
    return dot&&(!strcmp(dot,".pac")||!strcmp(dot,".dat")||!strcmp(dot,".img")||
                 !strcmp(dot,".fob")||!strcmp(dot,".bmp")||!strcmp(dot,".wav"));
}
static void cached_create(void){
    opens++;
    uint32_t path=A(0),access=A(1),disposition=A(4);
    create_file();
    Mirror *m=mirror(cpu.r[EAX]);if(!m)return;
    forget(m);
    if(disposition!=3||(access&0x40000000u)||!seek_file)return;
    const char *name=(const char*)gmem+path;
    if(!immutable_asset(name))return;
    char host[PATH_MAX];
    if(resolve_path(name,host,sizeof host))m->file=asset_cache_open(host);
    if(m->file&&asset_cache_size(m->file)>0x7fffffffull)forget(m);
    if(m->file)cached_opens++;
}
static void cached_seek(void){
    Mirror *m=mirror(A(0));
    seek_file();
    if(m&&m->file){
        if(cpu.r[EAX]==0xffffffffu)forget(m);
        else m->position=cpu.r[EAX];
    }
}
static int advance_original(uint32_t handle,uint64_t position){
    /* The original SetFilePointer is a synchronous host function. Preserve the
       caller's guest stack arguments; no extra thunk/handle changes are made. */
    uint32_t saved[4];
    for(int i=0;i<4;i++)saved[i]=A(i);
    wr32(cpu.r[ESP]+4,handle);wr32(cpu.r[ESP]+8,(uint32_t)position);
    wr32(cpu.r[ESP]+12,0);wr32(cpu.r[ESP]+16,0);
    seek_file();
    int success=cpu.r[EAX]==position;
    for(int i=0;i<4;i++)wr32(cpu.r[ESP]+4+4*i,saved[i]);
    return success;
}
static void cached_read(void){
    uint32_t handle=A(0),buffer=A(1),size=A(2),written=A(3);
    Mirror *m=mirror(handle);
    if(!m||!m->file||(uint64_t)buffer+size>=MEM_SIZE){original_reads++;read_file();return;}
    if(size>=32768)asset_cache_prefetch(m->file,(m->position/(128*1024)+1)*(128*1024));
    int64_t got=asset_cache_read(m->file,m->position,gmem+buffer,size);
    if(got<0||(got>0&&!advance_original(handle,m->position+(uint64_t)got))){
        forget(m);read_file();return;
    }
    m->position+=(uint64_t)got;
    cached_reads++;
    if(written)wr32(written,(uint32_t)got);
    if(size>=32768)asset_cache_prefetch(m->file,m->position+128*1024-1);
    RET(1);
}
static void cached_close(void){Mirror *m=mirror(A(0));close_file();forget(m);}
hle_fn asset_io_wrap(const char *dll,const char *name,hle_fn original){
    if(!enabled)return original;
    if(strcasecmp(dll,"kernel32.dll")&&strcasecmp(dll,"kernel32"))return original;
    if(!strcmp(name,"CreateFileA")){if(original!=cached_create)create_file=original;return cached_create;}
    if(!strcmp(name,"ReadFile")){if(original!=cached_read)read_file=original;return cached_read;}
    if(!strcmp(name,"SetFilePointer")){if(original!=cached_seek)seek_file=original;return cached_seek;}
    if(!strcmp(name,"CloseHandle")){if(original!=cached_close)close_file=original;return cached_close;}
    return original;
}
int asset_io_patch_table(HleEntry *table,unsigned count){
    installed=0;
    if(!enabled||!table||count>4096)return 0;
    const char *names[]={"CreateFileA","ReadFile","SetFilePointer","CloseHandle"};
    const int nargs[]={7,5,4,1};HleEntry *entries[4]={0};
    for(unsigned i=0;i<count;i++){
        HleEntry *e=table+i;
        if(!e->dll||!e->name||!e->fn)continue;
        if(strcasecmp(e->dll,"kernel32.dll")&&strcasecmp(e->dll,"kernel32"))continue;
        for(int k=0;k<4;k++)if(!strcmp(e->name,names[k])&&e->nargs==nargs[k])entries[k]=e;
    }
    for(int k=0;k<4;k++)if(!entries[k])return 0;
    for(int k=0;k<4;k++)entries[k]->fn=asset_io_wrap(entries[k]->dll,entries[k]->name,entries[k]->fn);
    installed=4;return installed;
}
void asset_io_stats(char *out,int size){
    if(out&&size>0)snprintf(out,(size_t)size,"[Asset I/O] active=%d opens=%lu cached_opens=%lu cached_reads=%lu original_reads=%lu resets=%lu\n",
            installed,opens,cached_opens,cached_reads,original_reads,resets);
}
