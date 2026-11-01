#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "cubiomes_bridge.h"
#include "liyue_mc.h"

/*
 * Thin JNI surface over Axolotl's seed map bridge (GPL-3.0, see NOTICE).
 * Only the pieces LiYue uses today are exposed; the bridge itself is compiled
 * unchanged from upstream so later features (details, underground, ores) can be
 * wired without touching vendored code.
 */

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_findFeatures(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dimension,
    jint minX, jint minZ, jint maxX, jint maxZ,
    jint featureMask, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || out == NULL)
        return -1;
    int capacity = (*env)->GetArrayLength(env, out) / 4;
    if (capacity <= 0)
        return -1;

    AxolotlSeedMapFeature *features =
        (AxolotlSeedMapFeature *)malloc(sizeof(AxolotlSeedMapFeature) * capacity);
    if (features == NULL)
        return -1;

    size_t count = axolotl_seed_map_find_features(
        (uint64_t)seed, mc, 0, dimension,
        minX, minZ, maxX, maxZ,
        (uint32_t)featureMask, features, (size_t)capacity);

    if (count > (size_t)capacity)
        count = (size_t)capacity;

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL) {
        free(features);
        return -1;
    }
    for (size_t i = 0; i < count; i++) {
        dst[i * 4] = (jint)features[i].x;
        dst[i * 4 + 1] = (jint)features[i].z;
        dst[i * 4 + 2] = (jint)features[i].kind;
        dst[i * 4 + 3] = (jint)features[i].end_ship;
    }
    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
    free(features);
    return (jint)count;
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_renderTile(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dimension,
    jint scale, jint x, jint z, jint width, jint height,
    jint elevation, jint terrain, jint contours,
    jbyteArray highlightMask, jbyteArray outRgb)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || outRgb == NULL)
        return -1;
    jsize length = (*env)->GetArrayLength(env, outRgb);
    if (length < width * height * 3)
        return -1;

    uint8_t *mask = NULL;
    if (highlightMask != NULL && (*env)->GetArrayLength(env, highlightMask) >= 256) {
        mask = (uint8_t *)(*env)->GetByteArrayElements(env, highlightMask, NULL);
    }

    jbyte *rgb = (*env)->GetByteArrayElements(env, outRgb, NULL);
    if (rgb == NULL) {
        if (mask != NULL)
            (*env)->ReleaseByteArrayElements(env, highlightMask, (jbyte *)mask, JNI_ABORT);
        return -1;
    }

    int status = axolotl_seed_map_render(
        (uint64_t)seed, mc, 0, dimension,
        x, z, scale, width, height,
        elevation, terrain, contours,
        mask, (uint8_t *)rgb, (size_t)length);

    (*env)->ReleaseByteArrayElements(env, outRgb, rgb, 0);
    if (mask != NULL)
        (*env)->ReleaseByteArrayElements(env, highlightMask, (jbyte *)mask, JNI_ABORT);
    return status;
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_seedMapCapability(JNIEnv *env, jobject thiz,
    jint version, jint capability, jint value)
{
    (void)env;
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0)
        return 0;
    return axolotl_seed_map_capability(mc, capability, value);
}
