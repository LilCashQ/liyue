#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "liyue_mc.h"

int liyue_dungeon_find(
    uint64_t seed, int mc, int x0, int z0, int x1, int z1,
    int32_t *out, int capacity);

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_dungeons(
    JNIEnv *env, jclass cls, jlong seed, jint version,
    jint x0, jint z0, jint x1, jint z1, jintArray outArr
) {
    (void)cls;
    if (!outArr) return -1;
    jsize len = (*env)->GetArrayLength(env, outArr);
    jint *buf = (*env)->GetIntArrayElements(env, outArr, NULL);
    if (!buf) return -1;
    int mc = liyue_mc_version(version);
    if (mc < 0) return -1;
    int found = liyue_dungeon_find(
        (uint64_t)seed, mc, (int)x0, (int)z0, (int)x1, (int)z1,
        (int32_t *)buf, (int)len);
    (*env)->ReleaseIntArrayElements(env, outArr, buf, 0);
    return (jint)found;
}
