/* Exercise the actual keyboard hook with a mock Java transport callback. */
#define LAN_HOST_TEST
#include "../native/lan_game.c"
#undef NDEBUG
#include <assert.h>
#include <stdarg.h>
CPU cpu;uint8_t *gmem;int g_frames;
static int buttons,exchanges,original_calls;static uint64_t last_digest;
void gfault(const char *what,uint32_t address){(void)what;(void)address;abort();}
static jint call(JNIEnv *env,jclass cls,jmethodID method,...){
    (void)env;(void)cls;(void)method;va_list args;va_start(args,method);last_digest=(uint64_t)va_arg(args,jlong);va_end(args);exchanges++;return buttons;
}
static jboolean clean(JNIEnv *env){(void)env;return JNI_FALSE;}
static struct JNINativeInterface_ funcs={.CallStaticIntMethod=call,.ExceptionCheck=clean};
static JNIEnv jenv=&funcs;
static jint get_env(JavaVM *v,void **out,jint version){(void)v;(void)version;*out=&jenv;return JNI_OK;}
static struct JNIInvokeInterface_ vm_funcs={.GetEnv=get_env};
static JavaVM jvm=&vm_funcs;
static void original_keyboard(void){original_calls++;memset(gmem+A(2),0x35,A(1));RET(0);}
int main(void){
    gmem=calloc(1,0x2600000);assert(gmem);cpu.r[ESP]=0x1000;wr32(0x1008,256);wr32(0x100c,0x2000);
    HleEntry table={"COM","IDirectInputDevice8A::GetDeviceState",3,original_keyboard};
    enabled=0;assert(lan_game_patch_table(&table,1)==0&&table.fn==original_keyboard);
    enabled=1;vm=&jvm;assert(lan_game_patch_table(&table,1)==1&&table.fn==keyboard);
    assert(lan_game_patch_table(&table,1)==1);buttons=(1<<0)|(1<<4)|(1<<(9+4));table.fn();
    assert(exchanges==1&&original_calls==1&&cpu.r[EAX]==0);
    for(int i=0;i<256;i++)assert(gmem[0x2000+i]==((i==p1[0]||i==p1[4]||i==p2[4])?0x80:0));
    uint64_t before=last_digest;buttons=0;table.fn();assert(before==last_digest);
    for(int i=0;i<256;i++)assert(gmem[0x2000+i]==0);
    gmem[0x25109c8]=1;table.fn();assert(last_digest!=before);
    int previous=exchanges;wr32(0x1008,80);table.fn();assert(exchanges==previous&&gmem[0x2000]==0x35);
    wr32(0x1008,256);buttons=-1;table.fn();assert(cpu.halted==1);
    for(int i=0;i<256;i++)assert(gmem[0x2000+i]==0);
    enabled=0;cpu.halted=0;previous=exchanges;table.fn();assert(exchanges==previous&&gmem[0x2000]==0x35&&!cpu.halted);
    free(gmem);puts("LAN native: handler ABI/idempotence, P1/P2 isolation, releases, checksum changes, disconnect and solo bypass passed");
}
