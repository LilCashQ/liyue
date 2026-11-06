#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "liyue_mc.h"

/*
 * JNI surface for Bedrock Edition structure placement. The algorithm lives in
 * liyue_bedrock.c; this file only marshals arrays.
 */

int liyue_bedrock_find(int64_t seed, int mc, int dimension, int feature,
                       int x0, int z0, int x1, int z1, int32_t *out, int capacity);
int liyue_bedrock_strongholds(int64_t seed, int32_t *out, int capacity);

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_bedrockFeatures(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dimension, jint feature,
    jint minX, jint minZ, jint maxX, jint maxZ, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    jint *buf;
    int capacity, written;
    jsize length;

    if (mc < 0 || out == NULL)
        return -1;
    length = (*env)->GetArrayLength(env, out);
    if (length < 2)
        return -1;
    buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (buf == NULL)
        return -1;
    capacity = (int)(length - length % 2);
    written = liyue_bedrock_find(seed, mc, dimension, feature,
        minX, minZ, maxX, maxZ, buf, capacity);
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return (jint)(written / 2);
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_bedrockStrongholds(JNIEnv *env, jobject thiz,
    jlong seed, jintArray out)
{
    (void)thiz;
    jint *buf;
    int written;
    jsize length;

    if (out == NULL)
        return -1;
    length = (*env)->GetArrayLength(env, out);
    if (length < 6)
        return -1;
    buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (buf == NULL)
        return -1;
    written = liyue_bedrock_strongholds(seed, buf, (int)length);
    /* written holds pair count; expand to coordinates already stored. */
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return (jint)written;
}
