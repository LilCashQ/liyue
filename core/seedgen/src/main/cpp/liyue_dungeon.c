// Dungeon (monster room) finder.
//
// Dungeons are not grid-placed structures; they are chunk-decoration features:
// every chunk runs monster_room (10 attempts, Y 0..319) and monster_room_deep
// (4 attempts, Y -58..-1). Each attempt draws a position from a decorator RNG
// and then has to pass a room test (solid floor and ceiling, 1..5 two-block
// openings in the walls). Only positions that pass are real dungeons.
//
// Positions are exact. The room test uses cubiomes' cave/canyon carvers as the
// solid/air oracle, so aquifer fluids are treated as solid: the result is the
// set of dungeons whose rooms are physically dug into a cave system.

#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include "generator.h"
#include "finders.h"
#include "carver.h"

#define DUNGEON_MAX_SPAN_CHUNKS 128

typedef struct {
    uint64_t lo, hi;
} XoRng;

static uint64_t mix13(uint64_t x) {
    x = (x ^ (x >> 30)) * UINT64_C(0xbf58476d1ce4e5b9);
    x = (x ^ (x >> 27)) * UINT64_C(0x94d049bb133111eb);
    return x ^ (x >> 31);
}

static void xo_seed(XoRng *r, uint64_t seed) {
    uint64_t t = UINT64_C(0x6a09e667f3bcc909) ^ seed;
    uint64_t lo = mix13(t);
    uint64_t hi = mix13(t + UINT64_C(0x9e3779b97f4a7c15));
    if ((lo | hi) == 0) {
        lo = UINT64_C(0x9e3779b97f4a7c15);
        hi = UINT64_C(0x6a09e667f3bcc909);
    }
    r->lo = lo;
    r->hi = hi;
}

static uint64_t xo_rotl(uint64_t v, int k) { return (v << k) | (v >> (64 - k)); }

static uint64_t xo_next_raw(XoRng *r) {
    uint64_t s0 = r->lo, s1 = r->hi;
    uint64_t result = xo_rotl(s0 + s1, 17) + s0;
    s1 ^= s0;
    r->lo = xo_rotl(s0, 49) ^ s1 ^ (s1 << 21);
    r->hi = xo_rotl(s1, 28);
    return result;
}

static int32_t xo_next(XoRng *r, int bits) {
    return (int32_t)(xo_next_raw(r) >> (64 - bits));
}

static int64_t xo_next_long(XoRng *r) {
    int64_t hi = xo_next(r, 32);
    int64_t lo = xo_next(r, 32);
    return (hi << 32) + lo;
}

static int32_t xo_next_int(XoRng *r, int32_t bound) {
    if (bound <= 0) return 0;
    if ((bound & (bound - 1)) == 0)
        return (int32_t)(((int64_t)bound * xo_next(r, 31)) >> 31);
    for (;;) {
        int32_t bits = xo_next(r, 31);
        int32_t val = bits % bound;
        if (bits - val + (bound - 1) >= 0) return val;
    }
}

// ---------------------------------------------------------------------------
// Carved-air lookup
// ---------------------------------------------------------------------------

typedef struct {
    uint64_t *keys;   // stored key = packed coord + 1 (0 means empty)
    size_t cap;
    size_t n;
    int chunkZ;       // which chunk row this set holds, INT_MIN if unused
    int used;
} CarveRow;

static uint64_t pack_coord(int x, int y, int z) {
    uint64_t ux = (uint64_t)(int64_t)(x + (1 << 20)) & 0x1FFFFF;
    uint64_t uz = (uint64_t)(int64_t)(z + (1 << 20)) & 0x1FFFFF;
    uint64_t uy = (uint64_t)(int64_t)(y + 1024) & 0x7FF;
    return (uy << 42) | (ux << 21) | uz;
}

static int carve_row_grow(CarveRow *row, size_t cap) {
    uint64_t *keys = (uint64_t *)calloc(cap, sizeof(uint64_t));
    if (!keys) return 0;
    free(row->keys);
    row->keys = keys;
    row->cap = cap;
    row->n = 0;
    return 1;
}

static void carve_row_clear(CarveRow *row) {
    if (row->keys) memset(row->keys, 0, row->cap * sizeof(uint64_t));
    row->n = 0;
}

static void carve_row_insert(CarveRow *row, uint64_t key) {
    if (row->n * 10 >= row->cap * 7) {
        size_t ncap = row->cap ? row->cap * 2 : 4096;
        uint64_t *old = row->keys;
        size_t oldcap = row->cap;
        uint64_t *keys = (uint64_t *)calloc(ncap, sizeof(uint64_t));
        if (!keys) return;
        row->keys = keys;
        row->cap = ncap;
        for (size_t i = 0; i < oldcap; i++) {
            if (!old[i]) continue;
            uint64_t v = old[i];
            size_t h = (size_t)((v * UINT64_C(0x9e3779b97f4a7c15)) >> 20) & (ncap - 1);
            while (row->keys[h]) h = (h + 1) & (ncap - 1);
            row->keys[h] = v;
        }
        free(old);
    }
    uint64_t v = key + 1;
    size_t h = (size_t)((v * UINT64_C(0x9e3779b97f4a7c15)) >> 20) & (row->cap - 1);
    while (row->keys[h]) {
        if (row->keys[h] == v) return;
        h = (h + 1) & (row->cap - 1);
    }
    row->keys[h] = v;
    row->n++;
}

static int carve_row_has(const CarveRow *row, uint64_t key) {
    if (!row->keys || !row->cap) return 0;
    uint64_t v = key + 1;
    size_t h = (size_t)((v * UINT64_C(0x9e3779b97f4a7c15)) >> 20) & (row->cap - 1);
    while (row->keys[h]) {
        if (row->keys[h] == v) return 1;
        h = (h + 1) & (row->cap - 1);
    }
    return 0;
}

typedef struct {
    Generator gen;
    uint64_t seed;
    int mc;
    CarveRow rows[3];
    XoRng rng;
} DungeonCtx;

static void carve_chunk_into(DungeonCtx *ctx, CarveRow *row, int cx, int cz) {
    int biomes[17][17];
    for (int rz = 0; rz < 17; rz++)
        for (int rx = 0; rx < 17; rx++)
            biomes[rz][rx] = getBiomeAt(&ctx->gen, 4, (cx + rx - 8) * 4, 0, (cz + rz - 8) * 4);

    Pos3List positions;
    createPos3List(&positions, 1024);

    int biome = getBiomeAt(&ctx->gen, 4, cx * 4, 0, cz * 4);
    for (int type = 0; type < CAVE_CARVER_NUM; type++) {
        CaveCarverConfig config;
        if (!getCaveCarverConfig(type, ctx->mc, biome, &config)) continue;
        if (config.dim != DIM_OVERWORLD) continue;
        positions.size = 0;
        carveCave(ctx->seed, ctx->mc, cx, cz, config, type, biomes, &positions);
        for (int i = 0; i < positions.size; i++) {
            Pos3 p = positions.pos3s[i];
            carve_row_insert(row, pack_coord(p.x, p.y, p.z));
        }
    }
    for (int type = 0; type < CANYON_CARVER_NUM; type++) {
        CanyonCarverConfig config;
        if (!getCanyonCarverConfig(type, ctx->mc, &config)) continue;
        if (config.dim != DIM_OVERWORLD) continue;
        positions.size = 0;
        carveCanyon(ctx->seed, ctx->mc, cx, cz, config, type, biomes, &positions);
        for (int i = 0; i < positions.size; i++) {
            Pos3 p = positions.pos3s[i];
            carve_row_insert(row, pack_coord(p.x, p.y, p.z));
        }
    }
    freePos3List(&positions);
}

static CarveRow *row_for(DungeonCtx *ctx, int chunkZ, int *fresh) {
    int slot = ((chunkZ % 3) + 3) % 3;
    CarveRow *row = &ctx->rows[slot];
    if (row->used && row->chunkZ == chunkZ) {
        if (fresh) *fresh = 0;
        return row;
    }
    carve_row_clear(row);
    row->used = 1;
    row->chunkZ = chunkZ;
    if (fresh) *fresh = 1;
    return row;
}

static int is_air(DungeonCtx *ctx, int x, int y, int z) {
    uint64_t key = pack_coord(x, y, z);
    int cz = z >> 4;
    for (int d = -1; d <= 1; d++) {
        int slot = (((cz + d) % 3) + 3) % 3;
        CarveRow *row = &ctx->rows[slot];
        if (!row->used || row->chunkZ != cz + d) continue;
        if (carve_row_has(row, key)) return 1;
    }
    return 0;
}

static void furnish_room(DungeonCtx *ctx, int x, int y, int z, int r, int i);

static int test_room(DungeonCtx *ctx, int x, int y, int z, int *outHw, int *outHh) {
    int hw = xo_next_int(&ctx->rng, 2) + 2;
    int hh = xo_next_int(&ctx->rng, 2) + 2;
    int a = -hw - 1, s = hw + 1;
    int h = -hh - 1, c = hh + 1;
    int openings = 0;
    for (int t = a; t <= s; t++) {
        for (int dy = -1; dy <= 4; dy++) {
            for (int oz = h; oz <= c; oz++) {
                int solid = !is_air(ctx, x + t, y + dy, z + oz);
                if ((dy == -1 || dy == 4) && !solid) return 0;
                if ((t == a || t == s || oz == h || oz == c) && dy == 0) {
                    if (is_air(ctx, x + t, y, z + oz) && is_air(ctx, x + t, y + 1, z + oz))
                        openings++;
                }
            }
        }
    }
    if (openings < 1 || openings > 5) return 0;
    *outHw = hw;
    *outHh = hh;
    return 1;
}

static void furnish_room(DungeonCtx *ctx, int x, int y, int z, int r, int i) {
    int a = -r - 1, u = r + 1;
    int s = -i - 1, f = i + 1;
    for (int t = a; t <= u; t++)
        for (int k = s; k <= f; k++)
            if (!is_air(ctx, x + t, y - 2, z + k)) xo_next_int(&ctx->rng, 4);

    // Chest placement: mirror the vanilla loop exactly so that the RNG stream
    // stays aligned for the remaining attempts in this chunk.
    uint32_t occupied[64];
    int occupied_n = 0;
    for (int n = 0; n < 2; n++) {
        for (int m = 0; m < 3; m++) {
            int ox = xo_next_int(&ctx->rng, 2 * r + 1) - r;
            int oz = xo_next_int(&ctx->rng, 2 * i + 1) - i;
            uint32_t key = ((uint32_t)(ox + 512) << 16) ^ (uint32_t)(oz + 512);
            int seen = 0;
            for (int q = 0; q < occupied_n; q++)
                if (occupied[q] == key) { seen = 1; break; }
            if (seen) continue;
            int sides = 0;
            for (int d = 0; d < 4; d++) {
                int dx = d == 0 ? -1 : (d == 1 ? 1 : 0);
                int dz = d == 2 ? -1 : (d == 3 ? 1 : 0);
                int px = ox + dx, pz = oz + dz;
                uint32_t k2 = ((uint32_t)(px + 512) << 16) ^ (uint32_t)(pz + 512);
                int hit = 0;
                for (int q = 0; q < occupied_n; q++)
                    if (occupied[q] == k2) { hit = 1; break; }
                if (!hit) {
                    int onBorder = (px == a || px == u || pz == s || pz == f);
                    if (onBorder && !is_air(ctx, x + px, y, z + pz)) hit = 1;
                }
                if (hit) sides++;
            }
            if (sides == 1) {
                if (occupied_n < 64) occupied[occupied_n++] = key;
                xo_next_long(&ctx->rng);
                break;
            }
        }
    }
}

static void decorator_rng(XoRng *rng, uint64_t seed, int blockX, int blockZ, int index, int step) {
    XoRng pop;
    xo_seed(&pop, seed);
    uint64_t m1 = (uint64_t)xo_next_long(&pop) | 1;
    uint64_t m2 = (uint64_t)xo_next_long(&pop) | 1;
    uint64_t population =
        ((uint64_t)(int64_t)blockX * m1 + (uint64_t)(int64_t)blockZ * m2) ^ seed;
    xo_seed(rng, population + (uint64_t)(10000 * step) + (uint64_t)index);
}

typedef struct {
    int featureIndex;
    int count;
    int yMin;
    int ySpan;
} DungeonFeature;

static const DungeonFeature DUNGEON_FEATURES[2] = {
    {2, 10, 0, 320},     // monster_room
    {3, 4, -58, 58},     // monster_room_deep
};

// dungeon type index -> 0 zombie, 1 spider, 2 skeleton
static const int DUNGEON_TYPES[4] = {2, 0, 0, 1};

int liyue_dungeon_find(
    uint64_t seed, int mc, int x0, int z0, int x1, int z1,
    int32_t *out, int capacity
) {
    if (mc < MC_1_18) return 0;
    if (x1 <= x0 || z1 <= z0) return 0;
    int cx0 = x0 >> 4, cx1 = x1 >> 4;
    int cz0 = z0 >> 4, cz1 = z1 >> 4;
    if (cx1 - cx0 > DUNGEON_MAX_SPAN_CHUNKS) cx1 = cx0 + DUNGEON_MAX_SPAN_CHUNKS;
    if (cz1 - cz0 > DUNGEON_MAX_SPAN_CHUNKS) cz1 = cz0 + DUNGEON_MAX_SPAN_CHUNKS;

    DungeonCtx *ctx = (DungeonCtx *)calloc(1, sizeof(DungeonCtx));
    if (!ctx) return -1;
    ctx->seed = seed;
    ctx->mc = mc;
    setupGenerator(&ctx->gen, mc, 0);
    applySeed(&ctx->gen, DIM_OVERWORLD, seed);

    int found = 0;
    for (int cz = cz0; cz <= cz1; cz++) {
        for (int lz = cz - 1; lz <= cz + 1; lz++) {
            int fresh = 0;
            CarveRow *row = row_for(ctx, lz, &fresh);
            if (!fresh) continue;
            for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) carve_chunk_into(ctx, row, cx, lz);
        }
        for (int cx = cx0; cx <= cx1; cx++) {
            int blockX = cx * 16, blockZ = cz * 16;
            for (int fi = 0; fi < 2; fi++) {
                const DungeonFeature *f = &DUNGEON_FEATURES[fi];
                decorator_rng(&ctx->rng, seed, blockX, blockZ, f->featureIndex, 3);
                for (int attempt = 0; attempt < f->count; attempt++) {
                    int x = blockX + xo_next_int(&ctx->rng, 16);
                    int z = blockZ + xo_next_int(&ctx->rng, 16);
                    int y = f->yMin + xo_next_int(&ctx->rng, f->ySpan);
                    int hw = 0, hh = 0;
                    if (!test_room(ctx, x, y, z, &hw, &hh)) continue;
                    furnish_room(ctx, x, y, z, hw, hh);
                    int type = DUNGEON_TYPES[xo_next_int(&ctx->rng, 4)];
                    if (out && found * 4 + 3 < capacity) {
                        out[found * 4 + 0] = x;
                        out[found * 4 + 1] = y;
                        out[found * 4 + 2] = z;
                        out[found * 4 + 3] = type;
                    }
                    found++;
                }
            }
        }
    }

    for (int i = 0; i < 3; i++) free(ctx->rows[i].keys);
    free(ctx);
    return found;
}
