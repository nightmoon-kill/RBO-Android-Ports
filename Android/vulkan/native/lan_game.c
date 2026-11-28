/* LAN input lockstep. All guest access remains on the emulator thread. */
#include "lan_game.h"
#include "rbo_api.h"
#include <jni.h>
#ifndef LAN_HOST_TEST
#include <dlfcn.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static JavaVM *vm;
static jclass peer;
static jmethodID exchange;
static hle_fn device_state;
static int enabled,last_frame=-1;
static uint64_t deadline;
static const unsigned p1[9]={0xc8,0xd0,0xcb,0xcd,0x1e,0x1f,0x20,0x21,0x02};
static const unsigned p2[9]={0x48,0x50,0x4b,0x4d,0x4f,0x4c,0x51,0x52,0x03};
static uint64_t hash_bytes(uint64_t h,const void *p,size_t n){
    const unsigned char *b=p;while(n--){h^=*b++;h*=1099511628211ull;}return h;
}
static uint64_t digest(void){
    extern int g_frames;
    uint64_t h=14695981039346656037ull;
    h=hash_bytes(h,cpu.r,sizeof cpu.r);h=hash_bytes(h,&cpu.eip,sizeof cpu.eip);
    h=hash_bytes(h,&cpu.CF,7);h=hash_bytes(h,&cpu.fs_base,sizeof cpu.fs_base);
    h=hash_bytes(h,cpu.st,sizeof cpu.st);h=hash_bytes(h,&cpu.top,sizeof cpu.top);
    h=hash_bytes(h,&cpu.fcw,sizeof cpu.fcw);h=hash_bytes(h,&cpu.fsw_cc,sizeof cpu.fsw_cc);
    h=hash_bytes(h,&g_frames,sizeof g_frames);
    // Profiles and player roots complement the CPU checks without GPU pixels.
    h=hash_bytes(h,gmem+0x25109c8,0x660);
    h=hash_bytes(h,gmem+0xadff5c,12);
    return h;
}
static void keyboard(void){
    uint32_t size=A(1),buffer=A(2);
    device_state();
    if(!enabled||size!=256||buffer>MEM_SIZE-256)return;
    JNIEnv *env=NULL;int attached=0;
    if((*vm)->GetEnv(vm,(void**)&env,JNI_VERSION_1_6)!=JNI_OK){
        #ifdef __ANDROID__
        if((*vm)->AttachCurrentThread(vm,&env,NULL)!=JNI_OK){cpu.halted=1;return;}
#else
        if((*vm)->AttachCurrentThread(vm,(void**)&env,NULL)!=JNI_OK){cpu.halted=1;return;}
#endif
        attached=1;
    }
    jint buttons=(*env)->CallStaticIntMethod(env,peer,exchange,(jlong)digest());
    if((*env)->ExceptionCheck(env)){(*env)->ExceptionDescribe(env);(*env)->ExceptionClear(env);buttons=-1;}
    memset(gmem+buffer,0,256);
    if(buttons<0){fprintf(stderr,"[LAN] session stopped at frame %d\n",g_frames);cpu.halted=1;}
    else for(int i=0;i<9;i++){
        gmem[buffer+p1[i]]=(buttons&(1<<i))?0x80:0;
        gmem[buffer+p2[i]]=(buttons&(1<<(i+9)))?0x80:0;
    }
    if(attached)(*vm)->DetachCurrentThread(vm);
}
int lan_game_active(void){return enabled;}
int lan_game_patch_table(HleEntry *table,unsigned count){
    if(!enabled||!table||count>4096)return 0;
    for(unsigned i=0;i<count;i++)if(table[i].name&&table[i].dll&&table[i].nargs==3&&table[i].fn&&
        !strcmp(table[i].dll,"COM")&&!strcmp(table[i].name,"IDirectInputDevice8A::GetDeviceState")){
        if(table[i].fn!=keyboard){device_state=table[i].fn;table[i].fn=keyboard;}
        fprintf(stderr,"[LAN] keyboard handler connected, deterministic clock enabled\n");return 1;
    }
    return 0;
}
void lan_game_tick(void){
    if(!enabled||last_frame==g_frames)return;
    struct timespec now;clock_gettime(CLOCK_MONOTONIC,&now);
    uint64_t ns=(uint64_t)now.tv_sec*1000000000ull+(uint64_t)now.tv_nsec;
    if(last_frame>=0&&g_frames>last_frame){
        deadline+=(uint64_t)(g_frames-last_frame)*1000000000ull/60;
        if(deadline>ns){uint64_t wait=deadline-ns;struct timespec t={(time_t)(wait/1000000000ull),(long)(wait%1000000000ull)};nanosleep(&t,NULL);}
        else if(ns-deadline>100000000ull)deadline=ns;
    }else deadline=ns;
    last_frame=g_frames;
}
#ifndef LAN_HOST_TEST
JNIEXPORT jint JNICALL Java_com_nakpaaiyaa_rbo_LanGame_startNative(JNIEnv *env,jclass cls,
        jstring root,jstring log,jboolean widescreen,jboolean lan){
    enabled=lan;last_frame=-1;deadline=0;
    if(lan){
        (*env)->GetJavaVM(env,&vm);peer=(*env)->NewGlobalRef(env,cls);
        exchange=(*env)->GetStaticMethodID(env,cls,"exchange","(J)I");
        if(!peer||!exchange)return -3;
        setenv("RBO_FAST","1",1);setenv("TZ","UTC",1);tzset();
    }else unsetenv("RBO_FAST");
    void *lib=dlopen("librbo_android.so",RTLD_NOLOAD|RTLD_NOW);
    jint (*start)(JNIEnv*,jclass,jstring,jstring,jboolean)=lib?dlsym(lib,"Java_com_nakpaaiyaa_rbo_NativeCore_start"):NULL;
    if(!start)return -3;
    return start(env,cls,root,log,widescreen);
}
JNIEXPORT void JNICALL Java_com_nakpaaiyaa_rbo_LanGame_keyNative(JNIEnv *env,jclass cls,jint scan,jboolean down){
    (void)env;(void)cls;rbo_set_key(scan,down);
}

#endif
