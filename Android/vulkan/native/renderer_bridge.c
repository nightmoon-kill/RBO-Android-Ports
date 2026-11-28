#include <dlfcn.h>
#include <jni.h>
#include <limits.h>
#include <string.h>
#include <stdio.h>

#include "dxshared.h"
#include "gpu_raster.h"
#include "state.h"
#include "vulkan_driver.h"
#include "companion_data.h"
#include "asset_io.h"
#include "asset_cache.h"
#include "skill_runtime.h"

void vulkan_d3d_register(void);
uint32_t vulkan_d3d_get_object(void);
void vulkan_d3d_state(StateIO *io);
void vulkan_d3d_state_flush(void);
int vulkan_d3d_gpu_report(char *out, int size);
void vulkan_perf_report(char *out,int size);
void vulkan_set_batching(int enabled);
void vulkan_gpu_surf_access(Surf *surface, int write);
void vulkan_gpu_surf_release(Surf *surface);

_Static_assert(sizeof(Surf) == 128, "RBO Android 0.5.2 surface ABI");

static struct {
    void (*register_d3d)(void);
    uint32_t (*get_object)(void);
    void (*state)(StateIO *);
    void (*state_flush)(void);
    int (*report)(char *, int);
    void (*surface_access)(Surf *, int);
    void (*surface_release)(Surf *);
    void (*audio_tick)(void);
    int (*register_hle)(const char *,const char *,int,hle_fn);
    void (*hooks_resync)(void);
    void (*init_hle)(void);
    void (*install_hooks)(void);
} original;
static int requested, active;
static char driver_directory[PATH_MAX], hooks_directory[PATH_MAX], cache_directory[PATH_MAX], driver_library[256];

JNIEXPORT void JNICALL Java_com_nakpaaiyaa_rbo_RendererSettings_configurePerformanceNative(
        JNIEnv *env,jclass cls,jboolean enabled) {
    (void)env;(void)cls;
    asset_io_set_enabled(enabled);
    vulkan_set_batching(enabled);
    skill_runtime_enable(enabled);
    fprintf(stderr,"[Performance] asset_cache=%d draw_batching=%d skill_native=%d\n",!!enabled,!!enabled,!!enabled);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    void *library = dlopen("librbo_android.so", RTLD_NOLOAD | RTLD_NOW);
    if (!library) return JNI_ERR;

    jint (*load)(JavaVM *, void *) = dlsym(library, "JNI_OnLoad");
    original.register_d3d = dlsym(library, "d3d_register");
    original.get_object = dlsym(library, "d3d_get_object");
    original.state = dlsym(library, "d3d_state");
    original.state_flush = dlsym(library, "d3d_state_flush");
    original.report = dlsym(library, "d3d_gpu_report");
    original.surface_access = dlsym(library, "gpu_surf_access");
    original.surface_release = dlsym(library, "gpu_surf_release");
    original.audio_tick = dlsym(library, "ds_tick");
    original.register_hle = dlsym(library, "hle_register");
    original.hooks_resync = dlsym(library, "rbo_hooks_resync");
    original.init_hle = dlsym(library, "hle_init");
    original.install_hooks = dlsym(library, "rbo_install_hooks");
    if (!load || !original.register_d3d || !original.get_object || !original.state ||
        !original.state_flush || !original.report || !original.surface_access ||
        !original.surface_release || !original.audio_tick || !original.register_hle ||
        !original.hooks_resync || !original.init_hle || !original.install_hooks) return JNI_ERR;
    return load(vm, reserved);
}

/* The fixed 0.5.2 core inlines its kernel handler registrations in hle_init.
   Decode the same table address used by its exported registration function,
   guarded by the exact ARM64 instruction forms and the handlers' names/ABI. */
static void connect_asset_handlers(void) {
    _Static_assert(sizeof(HleEntry)==32,"0.5.2 ARM64 HLE entry ABI");
    uintptr_t fn=(uintptr_t)original.register_hle;uint32_t adrp,add;
    memcpy(&adrp,(const void*)(fn+40),4);memcpy(&add,(const void*)(fn+44),4);
    if((adrp&0x9f00001fu)!=0x90000008u||(add&0xffc003ffu)!=0x91000108u){
        fprintf(stderr,"[Asset I/O] unsupported core registration layout\n");return;
    }
    int32_t pages=(int32_t)(((adrp>>29)&3)|(((adrp>>5)&0x7ffff)<<2));
    if(pages&(1<<20))pages|=~((1<<21)-1);
    uintptr_t table=((fn+40)&~(uintptr_t)4095)+(intptr_t)pages*4096+((add>>10)&4095);
    if(table<fn||table-fn>1024*1024)return;
    extern uint32_t hle_tab_count(void);
    int count=asset_io_patch_table((HleEntry*)table,hle_tab_count());
    fprintf(stderr,"[Asset I/O] connected_handlers=%d\n",count);
}

__attribute__((visibility("default"))) void hle_init(void) {
    original.init_hle();
    connect_asset_handlers();
    skill_runtime_bind();
}

__attribute__((visibility("default"))) void rbo_install_hooks(void) {
    original.install_hooks();
    skill_runtime_install();
}

__attribute__((visibility("default"))) int hle_register(const char *dll,const char *name,int nargs,hle_fn fn) {
    return original.register_hle(dll,name,nargs,asset_io_wrap(dll,name,fn));
}

__attribute__((visibility("default"))) void rbo_hooks_resync(void) {
    /* Save states restore the original handles/cursors. Forget mirrors so that
       existing handles resume through the original reader until reopened. */
    asset_io_reset();
    original.hooks_resync();
    skill_runtime_install();
}

/* The emulator also ticks audio in 2D menus before any 3D actor is created.
   Service queued point toggles there; companion_capture limits work to 10 Hz. */
__attribute__((visibility("default"))) void ds_tick(void) {
    companion_capture();
    original.audio_tick();
}

static void copy_java_string(JNIEnv *env, jstring value, char *out, size_t size, const char *suffix) {
    out[0] = '\0';
    if (!value) return;
    const char *text = (*env)->GetStringUTFChars(env, value, NULL);
    if (!text) return;
    if (strlen(text) + strlen(suffix) < size) snprintf(out, size, "%s%s", text, suffix);
    (*env)->ReleaseStringUTFChars(env, value, text);
}

JNIEXPORT void JNICALL Java_com_nakpaaiyaa_rbo_RendererSettings_configureNative(
        JNIEnv *env, jclass type, jboolean enabled, jstring directory, jstring library,
        jstring hooks, jstring cache) {
    (void)type;
    requested = enabled;
    copy_java_string(env, directory, driver_directory, sizeof driver_directory, "/");
    copy_java_string(env, library, driver_library, sizeof driver_library, "");
    copy_java_string(env, hooks, hooks_directory, sizeof hooks_directory, "/");
    copy_java_string(env, cache, cache_directory, sizeof cache_directory, "");
}

static int initialize_vulkan(void) {
    if (driver_directory[0] && driver_library[0]) {
        fprintf(stderr, "[Vulkan driver] Requested Turnip: %s\n", driver_library);
        if (vulkan_load_driver(driver_directory, driver_library, hooks_directory, cache_directory)
                && gpu_init()) {
            fprintf(stderr, "[Vulkan driver] Turnip active\n");
            return 1;
        }
        fprintf(stderr, "[Vulkan driver] Turnip unavailable, trying system driver\n");
        vulkan_reset_initialization();
    }
    int ready = vulkan_load_driver(NULL, NULL, NULL, NULL) && gpu_init();
    if (ready) fprintf(stderr, "[Vulkan driver] System driver active\n");
    return ready;
}

__attribute__((visibility("default"))) void d3d_register(void) {
    companion_reset();
    active = requested && initialize_vulkan();
    fprintf(stderr, "[Android renderer] %s\n",
            active ? "Vulkan" : (requested ? "Vulkan unavailable; using 0.5.2 CPU" : "0.5.2 CPU"));
    if (active) {
        d3d_android_set_renderer(1);
        vulkan_d3d_register();
    } else {
        original.register_d3d();
    }
}

__attribute__((visibility("default"))) uint32_t d3d_get_object(void) {
    return active ? vulkan_d3d_get_object() : original.get_object();
}

__attribute__((visibility("default"))) void gpu_surf_access(Surf *surface, int write) {
    companion_capture();
    if (!active) {
        original.surface_access(surface, write);
        return;
    }
    vulkan_gpu_surf_access(surface, write);
    static int last_report;
    if (g_frames - last_report >= 120) {
        char stats[2048];
        vulkan_d3d_gpu_report(stats, sizeof stats);
        fprintf(stderr, "%s", stats);
        vulkan_perf_report(stats,sizeof stats);fprintf(stderr,"%s",stats);
        asset_cache_stats(stats,sizeof stats);fprintf(stderr,"%s",stats);
        asset_io_stats(stats,sizeof stats);fprintf(stderr,"%s",stats);
        skill_runtime_report(stats,sizeof stats);fprintf(stderr,"%s",stats);
        if(cpu_prof_report(stats,sizeof stats)>0)fprintf(stderr,"[CPU hotspots]%s\n",stats);
        last_report = g_frames;
    }
}

__attribute__((visibility("default"))) void gpu_surf_release(Surf *surface) {
    if (active) vulkan_gpu_surf_release(surface);
    else original.surface_release(surface);
}

__attribute__((visibility("default"))) void d3d_state(StateIO *io) {
    if (active) vulkan_d3d_state(io);
    else original.state(io);
}

__attribute__((visibility("default"))) void d3d_state_flush(void) {
    if (active) vulkan_d3d_state_flush();
    else original.state_flush();
}

__attribute__((visibility("default"))) int d3d_gpu_report(char *out, int size) {
    return active ? vulkan_d3d_gpu_report(out, size) : original.report(out, size);
}
