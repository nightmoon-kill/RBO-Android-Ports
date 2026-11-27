#include <dlfcn.h>
#include <jni.h>
#include <limits.h>
#include <string.h>
#include <stdio.h>

#include "dxshared.h"
#include "gpu_raster.h"
#include "state.h"
#include "vulkan_driver.h"

void vulkan_d3d_register(void);
uint32_t vulkan_d3d_get_object(void);
void vulkan_d3d_state(StateIO *io);
void vulkan_d3d_state_flush(void);
int vulkan_d3d_gpu_report(char *out, int size);
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
} original;
static int requested, active;
static char driver_directory[PATH_MAX], hooks_directory[PATH_MAX], cache_directory[PATH_MAX], driver_library[256];

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
    if (!load || !original.register_d3d || !original.get_object || !original.state ||
        !original.state_flush || !original.report || !original.surface_access ||
        !original.surface_release) return JNI_ERR;
    return load(vm, reserved);
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
    if (!active) {
        original.surface_access(surface, write);
        return;
    }
    vulkan_gpu_surf_access(surface, write);
    static int last_report;
    if (g_frames - last_report >= 120) {
        char stats[384];
        vulkan_d3d_gpu_report(stats, sizeof stats);
        fprintf(stderr, "%s", stats);
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
