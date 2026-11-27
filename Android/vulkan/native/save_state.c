#include <jni.h>
#include "rbo_api.h"

JNIEXPORT jint JNICALL Java_com_nakpaaiyaa_rbo_SaveStateMenu_request(
        JNIEnv *env, jclass type, jint operation, jstring file) {
    (void)type;
    if (!rbo_is_running()) return -3;
    const char *path = (*env)->GetStringUTFChars(env, file, NULL);
    if (!path) return -3;
    int result = rbo_state_request(operation, path);
    (*env)->ReleaseStringUTFChars(env, file, path);
    return result;
}

JNIEXPORT jint JNICALL Java_com_nakpaaiyaa_rbo_SaveStateMenu_result(JNIEnv *env, jclass type) {
    (void)env;
    (void)type;
    return rbo_state_result();
}

JNIEXPORT jlong JNICALL Java_com_nakpaaiyaa_rbo_SaveStateMenu_savedAt(
        JNIEnv *env, jclass type, jstring file) {
    (void)type;
    const char *path = (*env)->GetStringUTFChars(env, file, NULL);
    if (!path) return 0;
    int64_t timestamp = 0;
    int result = rbo_state_info(path, &timestamp, NULL);
    (*env)->ReleaseStringUTFChars(env, file, path);
    return result == 0 ? timestamp : 0;
}
