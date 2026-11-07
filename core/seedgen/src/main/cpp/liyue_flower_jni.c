#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "liyue_mc.h"

extern int liyue_flower_search(int64_t seed64, int mc, int min_x, int min_z, int max_x,
                               int max_z, int long_side, int short_side, int square_only,
                               int require_pale, int require_both_bonus, int require_basalt,
                               int orientation, int flower_margin, int fast_mode,
                               int max_rows, int32_t *out, int capacity);

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_flowerSearch(
    JNIEnv *env, jclass clazz, jlong seed, jint version, jint minX, jint minZ, jint maxX,
    jint maxZ, jint longSide, jint shortSide, jint squareOnly, jint requirePale,
    jint requireBothBonus, jint requireBasalt, jint orientation, jint flowerMargin,
    jint fastMode, jint maxRows, jintArray outBlocks)
{
    int mc;
    jsize length;
    jint *blocks;
    int rows;
    int32_t *buffer;
    (void) clazz;

    if (!outBlocks)
        return 0;
    length = (*env)->GetArrayLength(env, outBlocks);
    if (length < 16)
        return 0;
    mc = liyue_mc_version((int) version);
    if (mc <= 0)
        return 0;

    buffer = (int32_t *) calloc((size_t) length, sizeof(int32_t));
    if (!buffer)
        return 0;
    rows = liyue_flower_search((int64_t) seed, mc, (int) minX, (int) minZ, (int) maxX,
                               (int) maxZ, (int) longSide, (int) shortSide, (int) squareOnly,
                               (int) requirePale, (int) requireBothBonus, (int) requireBasalt,
                               (int) orientation, (int) flowerMargin, (int) fastMode,
                               (int) maxRows, buffer, (int) length);
    if (rows > 0)
    {
        blocks = (*env)->GetIntArrayElements(env, outBlocks, NULL);
        if (blocks)
        {
            for (int i = 0; i < rows * 16 && i < length; i++)
                blocks[i] = (jint) buffer[i];
            (*env)->ReleaseIntArrayElements(env, outBlocks, blocks, 0);
        }
        else
        {
            rows = 0;
        }
    }
    free(buffer);
    return (jint) rows;
}
