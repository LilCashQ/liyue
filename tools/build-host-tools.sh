#!/usr/bin/env bash
# Builds the host-side reference tools (refgen, capmatrix, upstream-check).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CUBIOMES="$ROOT/core/seedgen/src/main/cpp/cubiomes"
OUT="$ROOT/build"
CC="${CC:-cc}"

mkdir -p "$OUT"

SOURCES=(
    "$CUBIOMES/biomenoise.c"
    "$CUBIOMES/biomes.c"
    "$CUBIOMES/finders.c"
    "$CUBIOMES/generator.c"
    "$CUBIOMES/layers.c"
    "$CUBIOMES/noise.c"
    "$CUBIOMES/quadbase.c"
    "$CUBIOMES/util.c"
)

"$CC" -O2 -std=c11 -I"$CUBIOMES" "$ROOT/tools/refgen/refgen.c" "${SOURCES[@]}" -o "$OUT/refgen" -lm
"$CC" -O2 -std=c11 -I"$CUBIOMES" "$ROOT/tools/refgen/refgrid.c" "${SOURCES[@]}" -o "$OUT/refgrid" -lm
"$CC" -O2 -std=c11 -I"$CUBIOMES" "$ROOT/tools/refgen/refpoints.c" "${SOURCES[@]}" -o "$OUT/refpoints" -lm
"$CC" -O2 -std=c11 -I"$CUBIOMES" "$ROOT/tools/capmatrix/capmatrix.c" "${SOURCES[@]}" -o "$OUT/capmatrix" -lm

# cubiomes' own regression test, with its main renamed so check.c drives it.
"$CC" -O2 -std=gnu11 -Dmain=cubiomes_unused_main -I"$CUBIOMES" \
    -c "$ROOT/tools/upstream-check/tests.c" -o "$OUT/upstream-tests.o"
"$CC" -O2 -std=gnu11 -I"$CUBIOMES" \
    -c "$ROOT/tools/upstream-check/check.c" -o "$OUT/upstream-check.o"
"$CC" "$OUT/upstream-check.o" "$OUT/upstream-tests.o" "${SOURCES[@]}" \
    -o "$OUT/upstream-check" -lm

echo "built: $OUT/refgen $OUT/refgrid $OUT/refpoints $OUT/capmatrix $OUT/upstream-check"
