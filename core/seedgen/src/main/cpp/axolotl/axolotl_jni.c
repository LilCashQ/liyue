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

/*
 * Ore scan for one chunk. `kind` selects the ore group (see ore_groups in the
 * bridge); `out` receives (x, y, z) triples and the return value is the number
 * of blocks written. Unsupported combinations yield 0 instead of an error.
 */
JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_scanOres(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dimension, jint chunkX, jint chunkZ,
    jint kind, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || out == NULL)
        return -1;
    int capacity = (*env)->GetArrayLength(env, out) / 3;
    if (capacity <= 0)
        return -1;

    int32_t *buffer = (int32_t *)malloc(sizeof(int32_t) * 3 * (size_t)capacity);
    if (buffer == NULL)
        return -1;

    size_t count = axolotl_seed_map_scan_ores(
        (uint64_t)seed, mc, 0, dimension, chunkX, chunkZ, kind, buffer, (size_t)capacity);
    if (count > (size_t)capacity)
        count = (size_t)capacity;

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL) {
        free(buffer);
        return -1;
    }
    for (size_t i = 0; i < count * 3; i++)
        dst[i] = (jint)buffer[i];
    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
    free(buffer);
    return (jint)count;
}

/* Biome id at a block position through the Axolotl path (raw Y, no scaling). */
JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_biomeAtRaw(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dimension, jint x, jint y, jint z)
{
    (void)env;
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0)
        return -1;
    return axolotl_seed_map_biome_at((uint64_t)seed, mc, 0, dimension, x, y, z);
}

/*
 * Structure details as a JSON document (see cubiomes_details.c). Returns NULL
 * when the version is unknown or the bridge cannot describe the position; the
 * caller receives a Java string and the native buffer is freed here.
 */
JNIEXPORT jstring JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_structureDetails(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dimension, jint x, jint z,
    jint featureBit, jint container)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || featureBit <= 0)
        return NULL;

    char *json = axolotl_seed_map_structure_details(
        (uint64_t)seed, mc, 0, dimension, x, z, (uint32_t)featureBit, container);
    if (json == NULL)
        return NULL;

    jstring result = (*env)->NewStringUTF(env, json);
    axolotl_seed_map_free_result(json);
    return result;
}

/*
 * Renders one underground tile as RGBA. `layers` is a 3-bit mask: 1 = solid
 * blocks, 2 = carved caves, 4 = carved canyons. Width/height must be multiples
 * of 16 and at most 64.
 */
JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_undergroundTile(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dimension,
    jint scale, jint x, jint z, jint width, jint height, jint y, jint layers,
    jbyteArray outRgba)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || outRgba == NULL)
        return -1;
    jsize length = (*env)->GetArrayLength(env, outRgba);
    jsize needed = (jsize)(width * height * 4);
    if (width <= 0 || height <= 0 || length < needed)
        return -1;

    jbyte *rgba = (*env)->GetByteArrayElements(env, outRgba, NULL);
    if (rgba == NULL)
        return -1;

    /* The bridge requires the buffer length to match the tile exactly, so
     * hand it the computed size instead of the whole Java array. */
    int status = axolotl_seed_map_underground(
        (uint64_t)seed, mc, 0, dimension,
        x, z, scale, width, height, y, (uint32_t)layers,
        (uint8_t *)rgba, (uint64_t)needed);

    (*env)->ReleaseByteArrayElements(env, outRgba, rgba, 0);
    return status;
}
