#!/usr/bin/env bash
# Cross-compiles the cubiomes JNI library for Android arm64-v8a on an
# aarch64 host (the NDK ships no linux-aarch64 host toolchain, so the NDK
# sysroot is used together with the system clang).
#
# usage: ANDROID_NDK_HOME=/path/to/ndk tools/build-android-so.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CPP="$ROOT/core/seedgen/src/main/cpp"
CUBIOMES="$CPP/cubiomes"
OUT_DIR="$ROOT/core/seedgen/src/main/jniLibs/arm64-v8a"
OUT="$OUT_DIR/libliyue_seedgen.so"

NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-/opt/android-sdk}/ndk/27.3.13750724}"
SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
if [[ ! -d "$SYSROOT" ]]; then
    echo "NDK sysroot not found at $SYSROOT" >&2
    exit 1
fi

API="${API:-26}"
CC="${CC:-clang}"

# Keep this list in sync with core/seedgen/src/main/cpp/CMakeLists.txt. A
# missing source does not fail the link (undefined symbols are legal in a
# shared object) and only blows up at dlopen time on the device, so the list
# is built from globs and the link uses --no-undefined to catch drift early.
SOURCES=(
    "$CUBIOMES/biomenoise.c"
    "$CUBIOMES/biomes.c"
    "$CUBIOMES/carver.c"
    "$CUBIOMES/finders.c"
    "$CUBIOMES/generator.c"
    "$CUBIOMES/layers.c"
    "$CUBIOMES/noise.c"
    "$CUBIOMES/quadbase.c"
    "$CUBIOMES/terrainnoise.c"
    "$CUBIOMES/util.c"
    "$CUBIOMES/xradv.c"
    "$CUBIOMES/features/"*.c
    "$CUBIOMES/loot/"*.c
    "$CUBIOMES/loot/cjson/cJSON.c"
    "$CUBIOMES/loot/loot_tables/chests/"*.c
    "$CUBIOMES/loot/loot_tables/chests/trial_chambers/"*.c
    "$CUBIOMES/loot/loot_tables/barrels/"*.c
    "$CPP/liyue_seedgen.c"
    "$CPP/liyue_bedrock.c"
    "$CPP/liyue_bedrock_jni.c"
    "$CPP/liyue_dungeon.c"
  "$CPP/liyue_quad.c"
  "$CPP/liyue_flower.c"
  "$CPP/liyue_flower_jni.c"
    "$CPP/liyue_dungeon_jni.c"
  "$CPP/liyue_quad_jni.c"
  "$CPP/liyue_lua.c"
    "$CPP/lua/"*.c
  "$CPP/axolotl/"*.c
)

mkdir -p "$OUT_DIR"

"$CC" \
    --target="aarch64-linux-android$API" \
    --sysroot="$SYSROOT" \
    -rtlib=compiler-rt -resource-dir="$NDK/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/18" -O2 -std=c11 -fPIC -fno-strict-aliasing -fvisibility=hidden \
    -Wl,-z,max-page-size=16384 -Wl,-z,relro -Wl,-z,now -Wl,--no-undefined \
    -shared \
    -I"$CUBIOMES" -I"$CPP" -I"$CPP/axolotl" \
    "${SOURCES[@]}" \
    -o "$OUT" \
    -lm -llog

echo "built: $OUT"
