#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "biomes.h"
#include "finders.h"
#include "generator.h"
#include "util.h"
#include "liyue_mc.h"

/*
 * Stable LiYue version ids. The order must match McVersion.kt on the Kotlin
 * side and is asserted by NativeVersionTableTest.
 */
static const int LIYUE_MC_VERSIONS[] = {
    MC_1_7_10,  // 0
    MC_1_8_9,   // 1
    MC_1_9_4,   // 2
    MC_1_10_2,  // 3
    MC_1_11_2,  // 4
    MC_1_12_2,  // 5
    MC_1_13_2,  // 6
    MC_1_14_4,  // 7
    MC_1_15_2,  // 8
    MC_1_16_1,  // 9
    MC_1_16_5,  // 10
    MC_1_17_1,  // 11
    MC_1_18_2,  // 12
    MC_1_19_2,  // 13
    MC_1_19_4,  // 14
    MC_1_20_6,  // 15
    MC_1_21_1,  // 16
    MC_1_21_3,   // 17
    MC_1_21_4,   // 18
    MC_1_21_5,   // 19
    MC_1_21_6,   // 20
    MC_1_21_9,   // 21
    MC_1_21_11,  // 22
    MC_26_1,     // 23
    MC_26_2,     // 24
    MC_26_3,     // 25
};

static const int LIYUE_MC_VERSION_COUNT =
    (int)(sizeof(LIYUE_MC_VERSIONS) / sizeof(LIYUE_MC_VERSIONS[0]));

int liyue_mc_version(jint version)
{
    if (version < 0 || version >= LIYUE_MC_VERSION_COUNT)
        return -1;
    return LIYUE_MC_VERSIONS[version];
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_versionCount(JNIEnv *env, jobject thiz)
{
    (void)env;
    (void)thiz;
    return LIYUE_MC_VERSION_COUNT;
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_cubiomesVersion(JNIEnv *env, jobject thiz, jint version)
{
    (void)env;
    (void)thiz;
    return liyue_mc_version(version);
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_biomeAt(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dim, jint scale, jint x, jint y, jint z)
{
    (void)env;
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || (dim != DIM_NETHER && dim != DIM_OVERWORLD && dim != DIM_END))
        return none;

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, dim, (uint64_t)seed);
    return getBiomeAt(&g, scale, x, y, z);
}

/*
 * Fills 'out' with a row-major (z, x) grid of 4-block biome cells, aligned to
 * the block grid. For 1.18+ the exact voronoi map is generated at block
 * resolution and downsampled at cell centres; the pre-1.18 overworld uses the
 * 1:4 layer directly. Returns 0 on success, -1 for invalid arguments, or a
 * cubiomes error code.
 */
JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_biomeGrid(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dim, jint blockX, jint blockZ, jint w, jint h,
    jint cellBlocks, jint y, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || (dim != DIM_NETHER && dim != DIM_OVERWORLD && dim != DIM_END))
        return -1;
    if (w <= 0 || h <= 0 || cellBlocks < 4 || (cellBlocks & (cellBlocks - 1)) != 0 || out == NULL)
        return -1;
    if ((*env)->GetArrayLength(env, out) < (jsize)(w * h))
        return -1;

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, dim, (uint64_t)seed);

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL)
        return -3;

    jint err = 0;
    if (cellBlocks == 4) {
        if (mc <= MC_1_17 && dim == DIM_OVERWORLD) {
            Range r = {4, blockX / 4, blockZ / 4, w, h, 63, 1};
            int *cache = allocCache(&g, r);
            if (cache == NULL) {
                (*env)->ReleasePrimitiveArrayCritical(env, out, dst, JNI_ABORT);
                return -2;
            }
            err = genBiomes(&g, cache, r);
            if (err == 0) {
                for (int i = 0; i < w * h; i++)
                    dst[i] = (jint)cache[i];
            }
            free(cache);
        } else {
            int bw = w * 4;
            int bh = h * 4;
            Range r = {1, blockX, blockZ, bw, bh, y, 1};
            int *cache = allocCache(&g, r);
            if (cache == NULL) {
                (*env)->ReleasePrimitiveArrayCritical(env, out, dst, JNI_ABORT);
                return -2;
            }
            err = genBiomes(&g, cache, r);
            if (err == 0) {
                for (int z = 0; z < h; z++)
                    for (int x = 0; x < w; x++)
                        dst[z * w + x] = (jint)cache[(z * 4 + 2) * bw + (x * 4 + 2)];
            }
            free(cache);
        }
    } else if (cellBlocks >= 16) {
        /* Overview: sample the biome at every cell centre with the exact point
         * path. genBiomes(scale>4) is unusable here because it skips the 1.18+
         * voronoi jitter and degenerates into per-cell noise. */
        int layerWorld = (mc <= MC_1_17 && dim == DIM_OVERWORLD);
        int half = cellBlocks / 2;
        for (int z = 0; z < h && err == 0; z++) {
            for (int x = 0; x < w; x++) {
                int bx = blockX + x * cellBlocks + half;
                int bz = blockZ + z * cellBlocks + half;
                int id = layerWorld
                    ? getBiomeAt(&g, 4, bx / 4, y, bz / 4)
                    : getBiomeAt(&g, 1, bx, y, bz);
                if (id < 0) {
                    err = -1;
                    break;
                }
                dst[z * w + x] = (jint)id;
            }
        }
    } else {
        err = -1;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, err == 0 ? 0 : JNI_ABORT);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_strongholds(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint maxCount, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || out == NULL || maxCount <= 0)
        return -1;
    int capacity = (*env)->GetArrayLength(env, out) / 2;
    if (capacity > maxCount)
        capacity = maxCount;

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, DIM_OVERWORLD, (uint64_t)seed);

    StrongholdIter sh;
    initFirstStronghold(&sh, mc, (uint64_t)seed);

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL)
        return -1;
    int count = 0;
    for (int i = 0; i < capacity; i++) {
        if (nextStronghold(&sh, &g) <= 0)
            break;
        dst[count * 2] = (jint)sh.pos.x;
        dst[count * 2 + 1] = (jint)sh.pos.z;
        count++;
    }
    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
    return count;
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_slimeChunks(JNIEnv *env, jobject thiz,
    jlong seed, jint chunkX0, jint chunkZ0, jint chunkX1, jint chunkZ1,
    jint maxCount, jintArray out)
{
    (void)thiz;
    if (out == NULL || maxCount <= 0)
        return -1;
    int capacity = (*env)->GetArrayLength(env, out) / 2;
    if (capacity > maxCount)
        capacity = maxCount;

    if (chunkX0 > chunkX1) { int t = chunkX0; chunkX0 = chunkX1; chunkX1 = t; }
    if (chunkZ0 > chunkZ1) { int t = chunkZ0; chunkZ0 = chunkZ1; chunkZ1 = t; }

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL)
        return -1;
    int count = 0;
    for (int z = chunkZ0; z <= chunkZ1 && count < capacity; z++) {
        for (int x = chunkX0; x <= chunkX1 && count < capacity; x++) {
            if (!isSlimeChunk((uint64_t)seed, x, z))
                continue;
            dst[count * 2] = (jint)(x * 16 + 8);
            dst[count * 2 + 1] = (jint)(z * 16 + 8);
            count++;
        }
    }
    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
    return count;
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_mineshafts(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint chunkX0, jint chunkZ0, jint chunkX1, jint chunkZ1,
    jint maxCount, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || out == NULL || maxCount <= 0)
        return -1;
    int capacity = (*env)->GetArrayLength(env, out) / 2;
    if (capacity > maxCount)
        capacity = maxCount;
    if (chunkX0 > chunkX1) { int t = chunkX0; chunkX0 = chunkX1; chunkX1 = t; }
    if (chunkZ0 > chunkZ1) { int t = chunkZ0; chunkZ0 = chunkZ1; chunkZ1 = t; }
    int width = chunkX1 - chunkX0 + 1;
    int height = chunkZ1 - chunkZ0 + 1;

    Pos *buffer = (Pos *)malloc(sizeof(Pos) * capacity);
    if (buffer == NULL)
        return -1;
    int count = getMineshafts(mc, (uint64_t)seed, chunkX0, chunkZ0, width, height, buffer, capacity);
    if (count > capacity)
        count = capacity;

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL) {
        free(buffer);
        return -1;
    }
    for (int i = 0; i < count; i++) {
        dst[i * 2] = (jint)buffer[i].x;
        dst[i * 2 + 1] = (jint)buffer[i].z;
    }
    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
    free(buffer);
    return count;
}

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_spawnPoint(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || out == NULL || (*env)->GetArrayLength(env, out) < 2)
        return -1;

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, DIM_OVERWORLD, (uint64_t)seed);
    Pos pos = estimateSpawn(&g, NULL);

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL)
        return -1;
    dst[0] = (jint)pos.x;
    dst[1] = (jint)pos.z;
    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
    return 0;
}

static int liyue_floordiv(int a, int b)
{
    int q = a / b;
    if ((a % b != 0) && ((a < 0) != (b < 0)))
        q--;
    return q;
}

/*
 * Enumerates structure generation attempts inside the block rectangle and keeps
 * the ones whose biome requirements are met. 'out' receives (x, z) pairs.
 * Returns the number of structures written, or -1 if the type is unsupported
 * for this version/dimension.
 */
JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_structurePositions(JNIEnv *env, jobject thiz,
    jlong seed, jint version, jint dim, jint type,
    jint x0, jint z0, jint x1, jint z1, jint maxCount, jintArray out)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0 || out == NULL || maxCount <= 0)
        return -1;

    StructureConfig conf;
    if (!getStructureConfig(type, mc, &conf))
        return -1;
    if (conf.dim != dim)
        return 0;

    int capacity = (*env)->GetArrayLength(env, out) / 2;
    if (capacity > maxCount)
        capacity = maxCount;
    if (capacity <= 0)
        return -1;

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, dim, (uint64_t)seed);

    int regionChunks = conf.regionSize > 0 ? conf.regionSize : 1;
    int regX0 = liyue_floordiv(liyue_floordiv(x0, 16), regionChunks);
    int regX1 = liyue_floordiv(liyue_floordiv(x1, 16), regionChunks);
    int regZ0 = liyue_floordiv(liyue_floordiv(z0, 16), regionChunks);
    int regZ1 = liyue_floordiv(liyue_floordiv(z1, 16), regionChunks);

    /* Guard against pathological areas for 1-chunk structures such as geodes. */
    long long regions = (long long)(regX1 - regX0 + 1) * (long long)(regZ1 - regZ0 + 1);
    if (regions > 250000) {
        int midX = (regX0 + regX1) / 2;
        int midZ = (regZ0 + regZ1) / 2;
        regX0 = midX - 250;
        regX1 = midX + 250;
        regZ0 = midZ - 250;
        regZ1 = midZ + 250;
    }

    jint *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (dst == NULL)
        return -1;

    int count = 0;
    for (int rz = regZ0; rz <= regZ1 && count < capacity; rz++) {
        for (int rx = regX0; rx <= regX1 && count < capacity; rx++) {
            Pos pos;
            if (!getStructurePos(type, mc, (uint64_t)seed, rx, rz, &pos))
                continue;
            if (!isViableStructurePos(type, &g, pos.x, pos.z, 0))
                continue;
            if (mc >= MC_1_18 && dim == DIM_OVERWORLD &&
                !isViableStructureTerrain(type, &g, pos.x, pos.z))
                continue;
            dst[count * 2] = (jint)pos.x;
            dst[count * 2 + 1] = (jint)pos.z;
            count++;
        }
    }

    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
    return count;
}

/* Biome name for display; version-aware because biomes were renamed over time. */
JNIEXPORT jstring JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_biomeName(JNIEnv *env, jobject thiz,
    jint version, jint biomeId)
{
    (void)thiz;
    int mc = liyue_mc_version(version);
    if (mc < 0)
        return NULL;
    const char *name = biome2str(mc, biomeId);
    if (name == NULL)
        return NULL;
    return (*env)->NewStringUTF(env, name);
}

