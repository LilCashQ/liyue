/*
 * Bedrock Edition structure placement.
 *
 * Bedrock does not use the Java LCG. It builds a 32-bit region seed from the
 * region coordinates, the structure salt and the low 32 bits of the world
 * seed, feeds that into a Mersenne Twister and then averages two or four
 * draws inside the region. The generator itself is the modified MT19937 that
 * Bedrock ships: the first n outputs are produced from a state seeded so that
 * the caller can ask for exactly n numbers up front.
 *
 * The algorithm and the per-structure tables are ported from
 * bedrock-dev/MCBEStructureFinder (MIT, https://github.com/bedrock-dev/MCBEStructureFinder).
 * Biome checks reuse the bundled cubiomes generator: from 1.18 on Bedrock and
 * Java share the same Overworld biome generation, so the Java biome filters
 * apply unchanged. That is why the caller passes a Java McVersion id.
 */

#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "generator.h"
#include "biomes.h"
#include "util.h"

#define BE_MT_MAX 8

static void be_mt_n(uint32_t seed, int n, uint32_t *out)
{
    uint32_t head[BE_MT_MAX + 1];
    uint32_t last[BE_MT_MAX + 1];
    int i;
    if (n < 1)
        return;
    if (n > BE_MT_MAX)
        n = BE_MT_MAX;

    head[0] = seed;
    for (i = 1; i <= n; i++)
        head[i] = 0x6c078965u * (head[i - 1] ^ (head[i - 1] >> 30)) + (uint32_t)i;

    {
        uint32_t temp = head[n];
        for (i = n; i < 397; i++)
            temp = 0x6c078965u * (temp ^ (temp >> 30)) + (uint32_t)(i + 1);
        last[0] = temp;
    }
    for (i = 1; i <= n; i++)
        last[i] = 0x6c078965u * (last[i - 1] ^ (last[i - 1] >> 30)) + (uint32_t)(i + 397);

    for (i = 0; i < n; i++) {
        uint32_t t = (head[i] & 0x80000000u) + (head[i + 1] & 0x7fffffffu);
        head[i] = (t >> 1) ^ last[i];
        if (t % 2 != 0)
            head[i] ^= 0x9908b0dfu;
    }

    for (i = 0; i < n; i++) {
        uint32_t y = head[i];
        y ^= y >> 11;
        y ^= (y << 7) & 2636928640u;
        y ^= (y << 15) & 4022730752u;
        y ^= y >> 18;
        out[i] = y;
    }
}

static float be_int2float(uint32_t x)
{
    return (float)x * 2.328306436538696e-10f;
}

/* Smallest number >= begin that is congruent to remain modulo `modulo`. */
static int be_align(int begin, int modulo, int remain)
{
    int r = begin - (begin % modulo);
    int c1;
    if (begin > 0)
        r += modulo;
    c1 = r + remain;
    if (c1 >= begin && c1 <= begin + modulo)
        return c1;
    return c1 - modulo;
}

typedef struct {
    int spacing;
    int spawnRange;
    int salt;
    int num;
} BeConfig;

/* Biome filters, same lists the reference implementation uses. */
static const int BE_WORLD_SPAWN_FILTER[] = {
    forest, plains, taiga_hills, forestHills, jungle_hills, jungle,
};
static const int BE_VILLAGE_FILTER[] = {
    plains, savanna, icePlains, taiga, taiga_hills, coldTaiga, coldTaigaHills, desert,
};
static const int BE_OCEAN_MONUMENT_SPAWN_FILTER[] = {
    deep_ocean, deep_cold_ocean, deep_warm_ocean, deep_frozen_ocean, deep_lukewarm_ocean,
};
static const int BE_OCEAN_MONUMENT_FILTER[] = {
    ocean, deep_ocean, lukewarm_ocean, cold_ocean, frozen_ocean, warm_ocean,
    deep_lukewarm_ocean, deep_warm_ocean, deep_frozen_ocean, deep_cold_ocean,
    river, frozen_river,
};
static const int BE_DESERT_TEMPLE_FILTER[] = { desert, desertHills, desert_lakes };
static const int BE_JUNGLE_TEMPLE_FILTER[] = { jungle, jungleHills };
static const int BE_WITCH_HUT_FILTER[] = { swamp, swamp_hills };
static const int BE_IGLOO_FILTER[] = { icePlains, coldTaiga };
static const int BE_BURIED_TREASURE_FILTER[] = {
    beach, coldBeach, stone_shore, mushroomIslandShore,
};
static const int BE_WOODLAND_MANSION_FILTER[] = { roofedForest };
static const int BE_PILLAGER_OUTPOST_FILTER[] = {
    plains, sunflower_plains, savanna, icePlains, taiga_hills,
    taiga, coldTaiga, coldTaigaHills, desert,
};

#define BE_COUNT(a) ((int)(sizeof(a) / sizeof((a)[0])))

/* Feature bits mirror the Kotlin StructureType.featureBit values. */
enum {
    BE_FEAT_VILLAGE = 1 << 0,
    BE_FEAT_OUTPOST = 1 << 1,
    BE_FEAT_SHIPWRECK = 1 << 2,
    BE_FEAT_MONUMENT = 1 << 3,
    BE_FEAT_MANSION = 1 << 4,
    BE_FEAT_RUINED_PORTAL = 1 << 8,
    BE_FEAT_STRONGHOLD = 1 << 9,
    BE_FEAT_DESERT_PYRAMID = 1 << 11,
    BE_FEAT_JUNGLE_TEMPLE = 1 << 12,
    BE_FEAT_SWAMP_HUT = 1 << 13,
    BE_FEAT_IGLOO = 1 << 14,
    BE_FEAT_OCEAN_RUIN = 1 << 15,
    BE_FEAT_TREASURE = 1 << 16,
    BE_FEAT_FORTRESS = 1 << 20,
    BE_FEAT_BASTION = 1 << 21,
    BE_FEAT_END_CITY = 1 << 22,
};

enum {
    BE_CHECK_NONE = 0,
    BE_CHECK_VILLAGE,
    BE_CHECK_MONUMENT,
    BE_CHECK_MANSION,
    BE_CHECK_OUTPOST,
    BE_CHECK_DESERT_TEMPLE,
    BE_CHECK_JUNGLE_TEMPLE,
    BE_CHECK_WITCH_HUT,
    BE_CHECK_IGLOO,
    BE_CHECK_TREASURE,
};

static int be_config_for(int feature, BeConfig *out)
{
    switch (feature) {
    case BE_FEAT_VILLAGE:       *out = (BeConfig){ 27, 17, 10387312, 4 };   return BE_CHECK_VILLAGE;
    case BE_FEAT_MONUMENT:      *out = (BeConfig){ 32, 27, 10387313, 4 };   return BE_CHECK_MONUMENT;
    case BE_FEAT_MANSION:       *out = (BeConfig){ 80, 60, 10387319, 4 };   return BE_CHECK_MANSION;
    case BE_FEAT_OUTPOST:       *out = (BeConfig){ 80, 56, 165745296, 4 };  return BE_CHECK_OUTPOST;
    case BE_FEAT_DESERT_PYRAMID:
    case BE_FEAT_JUNGLE_TEMPLE:
    case BE_FEAT_SWAMP_HUT:
    case BE_FEAT_IGLOO:         *out = (BeConfig){ 32, 24, 14357617, 2 };
        if (feature == BE_FEAT_DESERT_PYRAMID) return BE_CHECK_DESERT_TEMPLE;
        if (feature == BE_FEAT_JUNGLE_TEMPLE) return BE_CHECK_JUNGLE_TEMPLE;
        if (feature == BE_FEAT_SWAMP_HUT) return BE_CHECK_WITCH_HUT;
        return BE_CHECK_IGLOO;
    case BE_FEAT_TREASURE:      *out = (BeConfig){ 4, 2, 16842397, 4 };     return BE_CHECK_TREASURE;
    case BE_FEAT_OCEAN_RUIN:    *out = (BeConfig){ 12, 5, 14357621, 4 };    return BE_CHECK_NONE;
    case BE_FEAT_SHIPWRECK:     *out = (BeConfig){ 10, 5, 1, 4 };           return BE_CHECK_NONE;
    case BE_FEAT_RUINED_PORTAL: *out = (BeConfig){ 25, 15, 40552231, 4 };   return BE_CHECK_NONE;
    case BE_FEAT_FORTRESS:
    case BE_FEAT_BASTION:       *out = (BeConfig){ 30, 26, 430084232, 4 };  return BE_CHECK_NONE;
    case BE_FEAT_END_CITY:      *out = (BeConfig){ 20, 9, 10387313, 4 };    return BE_CHECK_NONE;
    default:                    return -1;
    }
}

static int be_biome_ok(Generator *g, int px, int pz, int r, const int *filter, int n)
{
    int x0 = (px - r) >> 2;
    int x1 = (px + r) >> 2;
    int z0 = (pz - r) >> 2;
    int z1 = (pz + r) >> 2;
    int x, z, i;
    for (z = z0; z <= z1; z++) {
        for (x = x0; x <= x1; x++) {
            int b = getBiomeAt(g, 4, x, 15, z);
            int hit = 0;
            for (i = 0; i < n; i++) {
                if (b == filter[i]) {
                    hit = 1;
                    break;
                }
            }
            if (!hit)
                return 0;
        }
    }
    return 1;
}

static int be_check(Generator *g, int check, int bx, int bz)
{
    switch (check) {
    case BE_CHECK_VILLAGE:
        return be_biome_ok(g, bx, bz, 2, BE_VILLAGE_FILTER, BE_COUNT(BE_VILLAGE_FILTER));
    case BE_CHECK_MONUMENT:
        if (!be_biome_ok(g, bx, bz, 16, BE_OCEAN_MONUMENT_SPAWN_FILTER,
                         BE_COUNT(BE_OCEAN_MONUMENT_SPAWN_FILTER)))
            return 0;
        return be_biome_ok(g, bx, bz, 29, BE_OCEAN_MONUMENT_FILTER,
                           BE_COUNT(BE_OCEAN_MONUMENT_FILTER));
    case BE_CHECK_MANSION:
        return be_biome_ok(g, bx, bz, 32, BE_WOODLAND_MANSION_FILTER,
                           BE_COUNT(BE_WOODLAND_MANSION_FILTER));
    case BE_CHECK_OUTPOST:
        return be_biome_ok(g, bx, bz, 0, BE_PILLAGER_OUTPOST_FILTER,
                           BE_COUNT(BE_PILLAGER_OUTPOST_FILTER));
    case BE_CHECK_DESERT_TEMPLE:
        return be_biome_ok(g, bx, bz, 0, BE_DESERT_TEMPLE_FILTER, BE_COUNT(BE_DESERT_TEMPLE_FILTER));
    case BE_CHECK_JUNGLE_TEMPLE:
        return be_biome_ok(g, bx, bz, 0, BE_JUNGLE_TEMPLE_FILTER, BE_COUNT(BE_JUNGLE_TEMPLE_FILTER));
    case BE_CHECK_WITCH_HUT:
        return be_biome_ok(g, bx, bz, 0, BE_WITCH_HUT_FILTER, BE_COUNT(BE_WITCH_HUT_FILTER));
    case BE_CHECK_IGLOO:
        return be_biome_ok(g, bx, bz, 0, BE_IGLOO_FILTER, BE_COUNT(BE_IGLOO_FILTER));
    case BE_CHECK_TREASURE:
        return be_biome_ok(g, bx, bz, 3, BE_BURIED_TREASURE_FILTER,
                           BE_COUNT(BE_BURIED_TREASURE_FILTER));
    default:
        return 1;
    }
}

/* Fortress / bastion share one grid; the draw also picks which one it is. */
static int be_pick_nether(BeConfig *cfg, uint32_t seed, int ax, int az, int *cx, int *cz, int *isBastion)
{
    uint32_t cand = (uint32_t)cfg->salt - 245998635u * (uint32_t)az - 1724254968u * (uint32_t)ax;
    uint32_t area_seed = cand + seed;
    uint32_t mt[8];
    uint32_t r1, r2, r3, r4;
    int avgx, avgz;
    be_mt_n(area_seed, 5, mt);
    r1 = mt[0] % (uint32_t)cfg->spawnRange;
    r2 = mt[1] % (uint32_t)cfg->spawnRange;
    r3 = mt[2] % (uint32_t)cfg->spawnRange;
    r4 = mt[3] % (uint32_t)cfg->spawnRange;
    avgx = (int)((r1 + r2) / 2);
    avgz = (int)((r3 + r4) / 2);
    *cx = be_align(ax * cfg->spacing, cfg->spacing, avgx);
    *cz = be_align(az * cfg->spacing, cfg->spacing, avgz);
    *isBastion = (mt[4] % 6) >= 2;
    return 1;
}

static int be_area_of(int block, int scale)
{
    /* floor division, matches scala_down() for the values we use */
    if (block >= 0)
        return block / scale;
    return -(((-block) + scale - 1) / scale);
}

/*
 * Enumerate one Bedrock structure type inside a block-space box.
 * Returns the number of entries written as (x, z) pairs in `out`.
 */
int liyue_bedrock_find(int64_t seed64, int mc, int dimension, int feature,
                       int x0, int z0, int x1, int z1, int32_t *out, int capacity)
{
    BeConfig cfg;
    Generator g;
    int check;
    int cx0, cx1, cz0, cz1, ax, az, ax0, ax1, az0, az1;
    int count = 0;
    uint32_t seed = (uint32_t)seed64;
    int useNether = 0;

    if (!out || capacity <= 0 || x1 < x0 || z1 < z0)
        return 0;

    check = be_config_for(feature, &cfg);
    if (check < 0)
        return 0;

    if (feature == BE_FEAT_END_CITY) {
        if (dimension != DIM_END)
            return 0;
    } else if (feature == BE_FEAT_FORTRESS || feature == BE_FEAT_BASTION) {
        if (dimension != DIM_NETHER)
            return 0;
        useNether = 1;
    } else if (dimension != DIM_OVERWORLD) {
        return 0;
    }

    setupGenerator(&g, mc, 0);
    applySeed(&g, dimension, seed);

    cx0 = be_area_of(x0, 16);
    cx1 = be_area_of(x1, 16);
    cz0 = be_area_of(z0, 16);
    cz1 = be_area_of(z1, 16);
    ax0 = be_area_of(cx0, cfg.spacing) - 1;
    ax1 = be_area_of(cx1, cfg.spacing) + 1;
    az0 = be_area_of(cz0, cfg.spacing) - 1;
    az1 = be_area_of(cz1, cfg.spacing) + 1;

    for (az = az0; az <= az1; az++) {
        for (ax = ax0; ax <= ax1; ax++) {
            int ccx, ccz, bx, bz, isBastion = 0;
            uint32_t cand = (uint32_t)cfg.salt - 245998635u * (uint32_t)az - 1724254968u * (uint32_t)ax;
            uint32_t area_seed = cand + seed;
            uint32_t mt[BE_MT_MAX];
            uint32_t r1, r2, avgx, avgz;

            be_mt_n(area_seed, cfg.num, mt);
            r1 = mt[0] % (uint32_t)cfg.spawnRange;
            r2 = mt[1] % (uint32_t)cfg.spawnRange;
            if (cfg.num == 2) {
                avgx = r1;
                avgz = r2;
            } else {
                uint32_t r3 = mt[2] % (uint32_t)cfg.spawnRange;
                uint32_t r4 = mt[3] % (uint32_t)cfg.spawnRange;
                avgx = (r1 + r2) / 2;
                avgz = (r3 + r4) / 2;
            }
            ccx = be_align(ax * cfg.spacing, cfg.spacing, (int)avgx);
            ccz = be_align(az * cfg.spacing, cfg.spacing, (int)avgz);
            bx = ccx * 16 + 8;
            bz = ccz * 16 + 8;
            if (bx < x0 || bx > x1 || bz < z0 || bz > z1)
                continue;

            if (useNether) {
                int ncx, ncz;
                be_pick_nether(&cfg, seed, ax, az, &ncx, &ncz, &isBastion);
                if ((feature == BE_FEAT_BASTION) != (isBastion != 0))
                    continue;
                bx = ncx * 16 + 8;
                bz = ncz * 16 + 8;
                if (bx < x0 || bx > x1 || bz < z0 || bz > z1)
                    continue;
            } else if (!be_check(&g, check, bx, bz)) {
                continue;
            }

            if (count + 2 > capacity)
                return count;
            out[count++] = bx;
            out[count++] = bz;
        }
    }
    return count;
}

/* Bedrock strongholds: a ring around the origin, angle stepped per stronghold. */
int liyue_bedrock_strongholds(int64_t seed64, int32_t *out, int capacity)
{
    uint32_t seed = (uint32_t)seed64;
    uint32_t mt[2];
    double angle;
    int dist;
    int i, count = 0;

    if (!out || capacity < 6)
        return 0;
    be_mt_n(seed, 2, mt);
    angle = 6.2831855 * (double)be_int2float(mt[0]);
    dist = (int)(mt[1] % 16u) + 40;
    for (i = 0; i < 3; i++) {
        int cx = (int)floor(cos(angle) * (double)dist);
        int cz = (int)floor(sin(angle) * (double)dist);
        out[count++] = cx * 16;
        out[count++] = cz * 16;
        angle += 1.8849558;
        dist += 8;
    }
    return count / 2;
}
