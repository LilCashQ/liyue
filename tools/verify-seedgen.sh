#!/usr/bin/env bash
# Builds the JNI library for the host JVM and runs the seedgen unit tests against it.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ROOT/build/host-native"
LIB="$BUILD_DIR/libliyue_seedgen.so"

if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -d /usr/lib/jvm/java-17-openjdk ]]; then
        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
    fi
fi

cmake -S "$ROOT/core/seedgen/src/main/cpp" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release -G Ninja
cmake --build "$BUILD_DIR"

if command -v gradle >/dev/null 2>&1; then
    GRADLE=gradle
else
    GRADLE="$ROOT/gradlew"
fi

"$GRADLE" :core:seedgen:testDebugUnitTest -Dliyue.nativeLib="$LIB"
