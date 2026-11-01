#ifndef LIYUE_MC_H_
#define LIYUE_MC_H_

#include <jni.h>

/* Maps a LiYue version id to a cubiomes MCVersion, or -1 when out of range. */
int liyue_mc_version(jint version);

#endif
