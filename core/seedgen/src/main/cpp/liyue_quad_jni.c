#include <jni.h>
#include <stdint.h>

int liyue_quad_huts(int64_t seed, int mc, int minRegionX, int minRegionZ,
                    int maxRegionX, int maxRegionZ, int32_t *out, int capacity);

int liyue_mc_version(int version);

JNIEXPORT jint JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_quadHuts(
        JNIEnv *env, jobject thiz,
        jlong seed, jint version,
        jint minRegionX, jint minRegionZ, jint maxRegionX, jint maxRegionZ,
        jintArray out)
{
    (void)thiz;
    if (!out) return 0;
    jsize length = (*env)->GetArrayLength(env, out);
    if (length < 3) return 0;
    jint *data = (*env)->GetIntArrayElements(env, out, NULL);
    if (!data) return 0;

    int mc = liyue_mc_version(version);
    int count = liyue_quad_huts((int64_t)seed, mc, minRegionX, minRegionZ,
                                maxRegionX, maxRegionZ, (int32_t *)data, (int)length);

    (*env)->ReleaseIntArrayElements(env, out, data, 0);
    return (jint)count;
}
