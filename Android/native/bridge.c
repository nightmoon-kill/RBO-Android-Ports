#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <fcntl.h>
#include <jni.h>
#include <limits.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "rbo_api.h"
#include "hle.h"

static pthread_mutex_t surface_lock = PTHREAD_MUTEX_INITIALIZER;
static ANativeWindow *surface_window;
static unsigned surface_generation;
static _Atomic int exit_code = -1;

static pthread_mutex_t music_lock = PTHREAD_MUTEX_INITIALIZER;
static struct {
    char path[PATH_MAX];
    int playing, volume, dirty;
    unsigned generation;
} music;

void bgm_host_play(const char *path, int volume) {
    char host_path[PATH_MAX], canonical[PATH_MAX], root[PATH_MAX];
    struct stat info;
    if (!path[0] || !resolve_path(path, host_path, sizeof host_path) ||
            !realpath(host_path, canonical) || !realpath(g_root, root) ||
            strncmp(canonical, root, strlen(root)) || canonical[strlen(root)] != '/' ||
            stat(canonical, &info) != 0 || !S_ISREG(info.st_mode)) {
        fprintf(stderr, "[Android audio] DirectShow track unavailable: %s\n", path);
        return;
    }
    pthread_mutex_lock(&music_lock);
    snprintf(music.path, sizeof music.path, "%s", canonical);
    music.playing = 1;
    music.volume = volume;
    music.dirty = 1;
    pthread_mutex_unlock(&music_lock);
}

void bgm_host_stop(void) {
    pthread_mutex_lock(&music_lock);
    music.path[0] = '\0';
    music.playing = 0;
    music.dirty = 1;
    music.generation++;
    pthread_mutex_unlock(&music_lock);
}

void bgm_host_pause(void) {
    pthread_mutex_lock(&music_lock);
    music.playing = 0;
    music.dirty = 1;
    pthread_mutex_unlock(&music_lock);
}

void bgm_host_volume(int volume) {
    pthread_mutex_lock(&music_lock);
    music.volume = volume;
    music.dirty = 1;
    pthread_mutex_unlock(&music_lock);
}

static void present(void *ctx, const uint32_t *pixels, int width, int height) {
    (void)ctx;
    static unsigned last_generation;
    static int last_width, last_height;

    pthread_mutex_lock(&surface_lock);
    ANativeWindow *window = surface_window;
    unsigned generation = surface_generation;
    if (window) ANativeWindow_acquire(window);
    pthread_mutex_unlock(&surface_lock);
    if (!window) return;

    if (generation != last_generation || width != last_width || height != last_height) {
        if (ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBA_8888) != 0) {
            ANativeWindow_release(window);
            return;
        }
        last_generation = generation;
        last_width = width;
        last_height = height;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
        if (buffer.width == width && buffer.height == height &&
                buffer.stride >= width && buffer.format == WINDOW_FORMAT_RGBA_8888) {
            for (int row = 0; row < height; row++) {
                uint32_t *out = (uint32_t *)buffer.bits + (size_t)row * buffer.stride;
                const uint32_t *in = pixels + (size_t)row * width;
                for (int col = 0; col < width; col++) {
                    uint32_t bgra = in[col];
                    out[col] = 0xff000000u | (bgra & 0xff00u) |
                        ((bgra & 0xffu) << 16) | ((bgra >> 16) & 0xffu);
                }
            }
        }
        ANativeWindow_unlockAndPost(window);
    }
    ANativeWindow_release(window);
}

static void finished(void *ctx, int code) {
    (void)ctx;
    atomic_store(&exit_code, code);
}

JNIEXPORT jint JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_start(JNIEnv *env, jclass cls, jstring root, jstring log) {
    (void)cls;
    const char *game_root = (*env)->GetStringUTFChars(env, root, NULL);
    if (!game_root) return -3;
    const char *log_path = (*env)->GetStringUTFChars(env, log, NULL);
    if (!log_path) {
        (*env)->ReleaseStringUTFChars(env, root, game_root);
        return -3;
    }
    int fd = open(log_path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd >= 0) {
        if (fd != STDERR_FILENO) {
            dup2(fd, STDERR_FILENO);
            close(fd);
        }
        setvbuf(stderr, NULL, _IONBF, 0);
    }
    (*env)->ReleaseStringUTFChars(env, log, log_path);

    atomic_store(&exit_code, -1);
    RboCallbacks cb = {.present = present, .finished = finished};
    int status = rbo_start(game_root, "RBO_Ex3.exe", &cb);
    (*env)->ReleaseStringUTFChars(env, root, game_root);
    return status;
}

JNIEXPORT void JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_setSurface(JNIEnv *env, jclass cls, jobject surface) {
    (void)cls;
    ANativeWindow *next = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
    pthread_mutex_lock(&surface_lock);
    ANativeWindow *previous = surface_window;
    surface_window = next;
    surface_generation++;
    pthread_mutex_unlock(&surface_lock);
    if (previous) ANativeWindow_release(previous);
}

JNIEXPORT void JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_setKey(JNIEnv *env, jclass cls, jint code, jboolean pressed) {
    (void)env; (void)cls;
    rbo_set_key(code, pressed);
}

JNIEXPORT void JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_setPaused(JNIEnv *env, jclass cls, jboolean paused) {
    (void)env; (void)cls;
    rbo_set_paused(paused);
}

JNIEXPORT jint JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_pullAudio(JNIEnv *env, jclass cls, jshortArray samples) {
    (void)cls;
    jsize count = (*env)->GetArrayLength(env, samples);
    short pcm[1024];
    if (count > 1024 || count % 2 != 0) return 0;
    int frames = rbo_audio_pull(pcm, count / 2);
    (*env)->SetShortArrayRegion(env, samples, 0, count, pcm);
    return frames;
}

JNIEXPORT jobject JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_pollMusic(JNIEnv *env, jclass cls) {
    (void)cls;
    pthread_mutex_lock(&music_lock);
    if (!music.dirty) {
        pthread_mutex_unlock(&music_lock);
        return NULL;
    }
    char path[PATH_MAX];
    strcpy(path, music.path);
    jint volume = music.volume;
    jboolean playing = music.playing;
    jint generation = (jint)music.generation;
    music.dirty = 0;
    pthread_mutex_unlock(&music_lock);
    jclass request = (*env)->FindClass(env, "com/nakpaaiyaa/rbo/GameAudio$MusicRequest");
    if (!request) return NULL;
    jmethodID constructor = (*env)->GetMethodID(env, request, "<init>", "(Ljava/lang/String;IZI)V");
    if (!constructor) return NULL;
    jstring file = (*env)->NewStringUTF(env, path);
    if (!file) return NULL;
    return (*env)->NewObject(env, request, constructor, file, volume, playing, generation);
}

JNIEXPORT void JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_audioLog(JNIEnv *env, jclass cls, jstring message) {
    (void)cls;
    const char *s = (*env)->GetStringUTFChars(env, message, NULL);
    if (s) {
        fprintf(stderr, "[Android audio] %s\n", s);
        (*env)->ReleaseStringUTFChars(env, message, s);
    }
}

JNIEXPORT jint JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_exitCode(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return atomic_load(&exit_code);
}

JNIEXPORT void JNICALL
Java_com_nakpaaiyaa_rbo_NativeCore_stop(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    rbo_stop();
}
