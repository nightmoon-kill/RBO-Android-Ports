#include "companion_data.h"
#include "point_cheats.h"
#include "cpu.h"
#include "rbo_api.h"
#include <jni.h>
#include <pthread.h>
#include <string.h>
#include <stdio.h>

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static int32_t published[COMPANION_WORDS];
static int last_frame = -1;
static int last_character = -2, last_support = -1;
static unsigned point_flags;

void companion_reset(void) {
    __atomic_store_n(&point_flags, 0, __ATOMIC_RELEASE);
    pthread_mutex_lock(&lock);
    memset(published, 0, sizeof published);
    last_frame = -1;
    last_character = -2;
    last_support = -1;
    pthread_mutex_unlock(&lock);
}

/* gpu_surf_access is entered on the emulator thread for CPU and Vulkan.
   Never read guest memory on Android's UI thread or during a state restore. */
void companion_capture(void) {
    extern int g_frames;
    if (g_frames == last_frame || (last_frame >= 0 && g_frames > last_frame && g_frames - last_frame < 6)) return;
    int32_t next[COMPANION_WORDS];
    if (last_frame > g_frames) __atomic_store_n(&point_flags, 0, __ATOMIC_RELEASE);
    point_cheats_apply(gmem, MEM_SIZE, __atomic_load_n(&point_flags, __ATOMIC_ACQUIRE));
    companion_decode(gmem, MEM_SIZE, g_frames, next);
    int character = next[2] ? next[9] : -1;
    if (character != last_character || next[1] != last_support) {
        fprintf(stderr, "[Companion] supported=%d character=%d level=%d enemies_ready=%d enemies=%d\n",
                next[1], character, next[10], next[3], next[4]);
        last_character = character;
        last_support = next[1];
    }
    pthread_mutex_lock(&lock);
    memcpy(published, next, sizeof next);
    last_frame = g_frames;
    pthread_mutex_unlock(&lock);
}

JNIEXPORT jint JNICALL Java_com_nakpaaiyaa_rbo_PointCheats_flagsNative(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    /* Enable before selection. The emulator-thread writer checks the EX3
       signatures and initialized profile when a character becomes available. */
    return rbo_is_running() ? (jint)__atomic_load_n(&point_flags, __ATOMIC_ACQUIRE) : -1;
}

JNIEXPORT void JNICALL Java_com_nakpaaiyaa_rbo_PointCheats_enableNative(
        JNIEnv *env, jclass cls, jint which, jboolean enabled) {
    (void)env; (void)cls;
    if (which < 0 || which > 1) return;
    unsigned bit = 1u << which;
    if (enabled) __atomic_fetch_or(&point_flags, bit, __ATOMIC_RELEASE);
    else __atomic_fetch_and(&point_flags, ~bit, __ATOMIC_RELEASE);
}

JNIEXPORT jintArray JNICALL Java_com_nakpaaiyaa_rbo_CompanionData_readNative(JNIEnv *env, jclass cls) {
    (void)cls;
    int32_t copy[COMPANION_WORDS] = {0};
    if (rbo_is_running()) {
        pthread_mutex_lock(&lock);
        memcpy(copy, published, sizeof copy);
        pthread_mutex_unlock(&lock);
    }
    jintArray result = (*env)->NewIntArray(env, COMPANION_WORDS);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, COMPANION_WORDS, copy);
    return result;
}
