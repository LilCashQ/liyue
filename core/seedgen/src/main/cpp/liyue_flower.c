/*
 * Full-flower-farm finder (port of FlowerFinderBase's CTS idea to plain C).
 *
 * A "full flower farm" window is a rectangle that contains, at the same time:
 *   - flower forest   (edge of the flower-colour gradient -> dandelion + lily)
 *   - swamp           (for the swamp farm half)
 *   - pale garden     (only for 1.21.4+, optional filter)
 *
 * The original tool first screens erosion/humidity climate noise at 1:4 and then
 * verifies candidates with real biome lookups.  Here the screen only uses the
 * two necessary climate conditions (erosion >= swamp threshold, humidity <=
 * flower-forest threshold) which keeps every possible window, and verification
 * does the real biome sampling at y=256 (above the caves).
 */

#include <math.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "biomenoise.h"
#include "biomes.h"
#include "generator.h"
#include "noise.h"

#define FLW_BIOME_Y 256
#define FLW_NETHER_Y 64
#define FLW_FLOWER_AREA_MIN 2000
#define FLW_MAX_CANDIDATES 512
#define FLW_MAX_ROWS 64
#define FLW_MARGIN 0.02f

typedef struct
{
    float *e;
    float *h;
    int gw, gh;
    int step;
    int min_x, min_z, max_x, max_z;
} ClimateField;

typedef struct
{
    int x, z, side_x, side_z;
    int score;
    int accepted;
} Candidate;

typedef struct
{
    int flower_cells, pale_cells, swamp_cells;
    int birch_cells, old_cells, meadow_cells, cherry_cells;
    int flower_x, flower_z, pale_x, pale_z, swamp_x, swamp_z;
    int area_per_cell;
} WindowStats;

enum
{
    FLW_NETHER_NONE = 0,
    FLW_NETHER_BASALT = 1,
    FLW_NETHER_WASTES = 2,
    FLW_NETHER_CRIMSON = 3,
    FLW_NETHER_SOUL = 4,
    FLW_NETHER_WARPED = 5,
};

enum
{
    FLW_BONUS_WILDFLOWERS = 1,
    FLW_BONUS_PINK_PETALS = 2,
};

typedef struct
{
    DoublePerlinNoise dpn;
    PerlinNoise oct[4];
} FlowerNoise;

static int floor_div(int v, int d)
{
    int q = v / d;
    if ((v % d) != 0 && ((v < 0) != (d < 0)))
        q--;
    return q;
}

static void climate_eh(const BiomeNoise *bn, double qx, double qz, float *out_e, float *out_h)
{
    const double sx = sampleDoublePerlin(&bn->climate[NP_SHIFT], qx, 0.0, qz) * 4.0;
    const double sz = sampleDoublePerlin(&bn->climate[NP_SHIFT], qz, qx, 0.0) * 4.0;
    const double px = qx + sx;
    const double pz = qz + sz;
    *out_e = (float) sampleDoublePerlin(&bn->climate[NP_EROSION], px, 0.0, pz);
    *out_h = (float) sampleDoublePerlin(&bn->climate[NP_HUMIDITY], px, 0.0, pz);
}

static int build_field(ClimateField *field, uint64_t seed, int mc, int min_x, int min_z,
                       int max_x, int max_z)
{
    BiomeNoise bn;
    int i, j;
    int step = 16;

    memset(field, 0, sizeof(*field));
    if (max_x <= min_x || max_z <= min_z)
        return 0;
    for (;;)
    {
        const int64_t gw = (int64_t) (max_x - min_x) / step + 1;
        const int64_t gh = (int64_t) (max_z - min_z) / step + 1;
        if (gw * gh <= 400000 || step >= 256)
            break;
        step *= 2;
    }
    field->step = step;
    field->min_x = min_x;
    field->min_z = min_z;
    field->max_x = max_x;
    field->max_z = max_z;
    field->gw = (max_x - min_x) / step + 1;
    field->gh = (max_z - min_z) / step + 1;
    field->e = (float *) malloc(sizeof(float) * (size_t) field->gw * (size_t) field->gh);
    field->h = (float *) malloc(sizeof(float) * (size_t) field->gw * (size_t) field->gh);
    if (!field->e || !field->h)
        return 0;

    initBiomeNoise(&bn, mc);
    setBiomeSeed(&bn, seed, 0);

    for (j = 0; j < field->gh; j++)
    {
        for (i = 0; i < field->gw; i++)
        {
            /* Climate noise is sampled at 1:1 block coordinates. */
            const double qx = (double) (min_x + i * step);
            const double qz = (double) (min_z + j * step);
            const size_t idx = (size_t) j * (size_t) field->gw + (size_t) i;
            climate_eh(&bn, qx, qz, &field->e[idx], &field->h[idx]);
        }
    }
    return 1;
}

/* Integral image over a predicate, returned as an (gw+1)x(gh+1) table. */
static int *integral_image(const ClimateField *field, int kind)
{
    const int gw = field->gw, gh = field->gh;
    const size_t w = (size_t) gw + 1;
    int *sum = (int *) calloc(w * ((size_t) gh + 1), sizeof(int));
    int i, j;
    if (!sum)
        return NULL;
    for (j = 0; j < gh; j++)
    {
        int row = 0;
        for (i = 0; i < gw; i++)
        {
            const size_t idx = (size_t) j * (size_t) gw + (size_t) i;
            const float e = field->e[idx];
            const float h = field->h[idx];
            int hit = 0;
            if (kind == 0)
                hit = e >= (0.55f - 0.05f);
            else if (kind == 1)
                hit = h <= (-0.35f + 0.05f);
            else
                hit = e <= (0.05f + 0.02f) && h >= (0.30f - 0.02f);
            row += hit;
            sum[(size_t) (j + 1) * w + (size_t) (i + 1)] =
                sum[(size_t) j * w + (size_t) (i + 1)] + row;
        }
    }
    return sum;
}

static int window_sum(const int *sum, int w, int i0, int j0, int i1, int j1)
{
    return sum[(size_t) (j1) * (size_t) w + (size_t) (i1)]
         - sum[(size_t) (j0) * (size_t) w + (size_t) (i1)]
         - sum[(size_t) (j1) * (size_t) w + (size_t) (i0)]
         + sum[(size_t) (j0) * (size_t) w + (size_t) (i0)];
}

static int cmp_candidate(const void *lhs, const void *rhs)
{
    const Candidate *a = (const Candidate *) lhs;
    const Candidate *b = (const Candidate *) rhs;
    if (a->score != b->score)
        return b->score - a->score;
    if (a->x != b->x)
        return a->x - b->x;
    return a->z - b->z;
}

static void init_flower_noise(FlowerNoise *fn)
{
    Xoroshiro xr;
    const double amp[1] = {1.0};
    memset(fn, 0, sizeof(*fn));
    xSetSeed(&xr, 2345);
    xDoublePerlinInit(&fn->dpn, &xr, fn->oct, amp, 0, 1, -1);
}

static int flower_id(const FlowerNoise *fn, int x, int y, int z)
{
    const double scale = 0.020833334;
    const double value = sampleDoublePerlin(&fn->dpn, x * scale, y * scale, z * scale);
    double t = (1.0 + value) / 2.0;
    if (t < 0.0)
        t = 0.0;
    else if (t > 0.9999)
        t = 0.9999;
    return (int) (t * 11.0);
}

/* Both gradient ends (0 = dandelion, 10 = lily of the valley) within 32 blocks. */
static int flower_extremes(const FlowerNoise *fn, const int *xs, const int *zs, int count,
                           int *out_y)
{
    int dandy[160], lily[160];
    int nd = 0, nl = 0;
    int y, i, a, b;
    int best = 1 << 30;
    int best_y = 0;
    if (count <= 0)
        return 0;
    for (y = 80; y <= 319; y += 2)
    {
        int has_dandelion = 0, has_lily = 0;
        for (i = 0; i < count; i++)
        {
            const int id = flower_id(fn, xs[i], y, zs[i]);
            if (id == 0)
                has_dandelion = 1;
            else if (id == 10)
                has_lily = 1;
            if (has_dandelion && has_lily)
                break;
        }
        if (has_dandelion && nd < 160)
            dandy[nd++] = y;
        if (has_lily && nl < 160)
            lily[nl++] = y;
    }
    for (a = 0; a < nd; a++)
    {
        for (b = 0; b < nl; b++)
        {
            const int span = abs(dandy[a] - lily[b]);
            if (span <= 32 && span < best)
            {
                best = span;
                best_y = (int) lround((dandy[a] + lily[b]) / 2.0);
            }
        }
    }
    if (best > 32)
        return 0;
    *out_y = best_y;
    return 1;
}

static int nether_rank(const Generator *nether, int wx, int wz, int side_x, int side_z)
{
    static const int order[5] = {basalt_deltas, nether_wastes, crimson_forest, soul_sand_valley,
                                 warped_forest};
    static const int ranks[5] = {FLW_NETHER_BASALT, FLW_NETHER_WASTES, FLW_NETHER_CRIMSON,
                                 FLW_NETHER_SOUL, FLW_NETHER_WARPED};
    unsigned char seen[256];
    int min_nx = floor_div(wx, 8);
    int max_nx = floor_div(wx + side_x - 1, 8);
    int min_nz = floor_div(wz, 8);
    int max_nz = floor_div(wz + side_z - 1, 8);
    int span_x = max_nx - min_nx + 1;
    int span_z = max_nz - min_nz + 1;
    int step = (span_x > 64 || span_z > 64) ? 2 : 1;
    int nx, nz, i;

    memset(seen, 0, sizeof(seen));
    for (nz = min_nz; nz <= max_nz; nz += step)
        for (nx = min_nx; nx <= max_nx; nx += step)
        {
            const int id = getBiomeAt(nether, 4, nx, FLW_NETHER_Y, nz);
            if (id >= 0 && id < 256)
                seen[id] = 1;
        }
    for (i = 0; i < 5; i++)
        if (seen[order[i]])
            return ranks[i];
    return FLW_NETHER_NONE;
}

static int in_window(int x, int z, int side_x, int side_z, const int *boxes, int count)
{
    int i;
    for (i = 0; i < count; i++)
    {
        const int ox = boxes[i * 4 + 0];
        const int oz = boxes[i * 4 + 1];
        const int sx = boxes[i * 4 + 2];
        const int sz = boxes[i * 4 + 3];
        if (x < ox + sx && ox < x + side_x && z < oz + sz && oz < z + side_z)
            return 1;
    }
    return 0;
}

static void classify_window(const Generator *overworld, int wx, int wz, int side_x, int side_z,
                            int cell, WindowStats *stats)
{
    int gx, gz;
    const int mid_x = wx + side_x / 2;
    const int mid_z = wz + side_z / 2;
    int flower_best = 1 << 30, pale_best = 1 << 30, swamp_best = 1 << 30;

    memset(stats, 0, sizeof(*stats));
    stats->area_per_cell = cell * cell;
    stats->flower_x = wx + side_x / 2;
    stats->flower_z = wz + side_z / 2;
    stats->pale_x = stats->flower_x;
    stats->pale_z = stats->flower_z;
    stats->swamp_x = stats->flower_x;
    stats->swamp_z = stats->flower_z;

    for (gz = 0; gz * cell < side_z; gz++)
    {
        for (gx = 0; gx * cell < side_x; gx++)
        {
            const int bx = wx + gx * cell;
            const int bz = wz + gz * cell;
            const int id = getBiomeAt(overworld, 4, bx, FLW_BIOME_Y, bz);
            switch (id)
            {
            case flower_forest:
            {
                const int d = abs(bx - mid_x) + abs(bz - mid_z);
                stats->flower_cells++;
                if (d < flower_best)
                {
                    flower_best = d;
                    stats->flower_x = bx;
                    stats->flower_z = bz;
                }
                break;
            }
            case pale_garden:
            {
                const int d = abs(bx - mid_x) + abs(bz - mid_z);
                stats->pale_cells++;
                if (d < pale_best)
                {
                    pale_best = d;
                    stats->pale_x = bx;
                    stats->pale_z = bz;
                }
                break;
            }
            case swamp:
            {
                const int d = abs(bx - mid_x) + abs(bz - mid_z);
                stats->swamp_cells++;
                if (d < swamp_best)
                {
                    swamp_best = d;
                    stats->swamp_x = bx;
                    stats->swamp_z = bz;
                }
                break;
            }
            case birch_forest:
                stats->birch_cells++;
                break;
            case old_growth_birch_forest:
                stats->old_cells++;
                break;
            case meadow:
                stats->meadow_cells++;
                break;
            case cherry_grove:
                stats->cherry_cells++;
                break;
            default:
                break;
            }
        }
    }
}

int liyue_flower_search(int64_t seed64, int mc, int min_x, int min_z, int max_x, int max_z,
                        int long_side, int short_side, int square_only, int require_pale,
                        int require_both_bonus, int require_basalt, int max_rows, int32_t *out,
                        int capacity)
{
    const uint64_t seed = (uint64_t) seed64;
    ClimateField field;
    int *sum_swamp = NULL, *sum_flower = NULL, *sum_pale = NULL;
    Candidate *cands = NULL;
    int cand_count = 0, i, j, ori;
    int rows = 0;
    int *accepted = NULL;
    int *checked = NULL;
    int checked_n = 0;
    Generator overworld, nether;
    FlowerNoise flowers;
    const int sides[2][2] = {
        {long_side, short_side},
        {short_side, long_side},
    };
    const int orientations = (square_only || long_side == short_side) ? 1 : 2;

    if (!out || capacity < 16 || max_rows <= 0)
        return 0;
    if (mc < MC_1_18)
        return 0;
    if (max_rows > FLW_MAX_ROWS)
        max_rows = FLW_MAX_ROWS;
    if (long_side < 64 || short_side < 64)
        return 0;
    if (long_side > 1024 || short_side > 1024)
        return 0;

    memset(&field, 0, sizeof(field));
    if (!build_field(&field, seed, mc, min_x, min_z, max_x, max_z))
    {
        free(field.e);
        free(field.h);
        return 0;
    }
    sum_swamp = integral_image(&field, 0);
    sum_flower = integral_image(&field, 1);
    sum_pale = integral_image(&field, 2);
    free(field.e);
    free(field.h);
    field.e = field.h = NULL;
    if (!sum_swamp || !sum_flower || !sum_pale)
    {
        free(sum_swamp);
        free(sum_flower);
        free(sum_pale);
        return 0;
    }

    cands = (Candidate *) calloc(FLW_MAX_CANDIDATES, sizeof(Candidate));
    if (!cands)
    {
        free(sum_swamp);
        free(sum_flower);
        free(sum_pale);
        return 0;
    }

    for (ori = 0; ori < orientations; ori++)
    {
        const int side_x = sides[ori][0];
        const int side_z = sides[ori][1];
        int x, z;
        for (z = min_z; z + side_z <= max_z; z += 32)
        {
            for (x = min_x; x + side_x <= max_x; x += 32)
            {
                const int i0 = (x - field.min_x) / field.step;
                const int j0 = (z - field.min_z) / field.step;
                const int i1 = (x + side_x - field.min_x) / field.step;
                const int j1 = (z + side_z - field.min_z) / field.step;
                int swamp, flower, pale;
                int score;
                if (i1 <= i0 || j1 <= j0 || i1 > field.gw || j1 > field.gh)
                    continue;
                swamp = window_sum(sum_swamp, field.gw + 1, i0, j0, i1, j1);
                if (swamp < 1)
                    continue;
                flower = window_sum(sum_flower, field.gw + 1, i0, j0, i1, j1);
                if (flower < 2)
                    continue;
                if (require_pale)
                {
                    pale = window_sum(sum_pale, field.gw + 1, i0, j0, i1, j1);
                    if (pale < 1)
                        continue;
                }
                else
                {
                    pale = 0;
                }
                score = flower * 2 + swamp + pale * 3;
                if (cand_count < FLW_MAX_CANDIDATES)
                {
                    cands[cand_count].x = x;
                    cands[cand_count].z = z;
                    cands[cand_count].side_x = side_x;
                    cands[cand_count].side_z = side_z;
                    cands[cand_count].score = score;
                    cands[cand_count].accepted = 0;
                    cand_count++;
                }
                else
                {
                    int worst = 0;
                    for (i = 1; i < FLW_MAX_CANDIDATES; i++)
                        if (cands[i].score < cands[worst].score)
                            worst = i;
                    if (score > cands[worst].score)
                    {
                        cands[worst].x = x;
                        cands[worst].z = z;
                        cands[worst].side_x = side_x;
                        cands[worst].side_z = side_z;
                        cands[worst].score = score;
                        cands[worst].accepted = 0;
                    }
                }
            }
        }
    }
    free(sum_swamp);
    free(sum_flower);
    free(sum_pale);

    if (getenv("FLW_DEBUG"))
        fprintf(stderr, "[flw] field=%dx%d step=%d windows-kept=%d\n", field.gw, field.gh,
                field.step, cand_count);
    qsort(cands, (size_t) cand_count, sizeof(Candidate), cmp_candidate);

    setupGenerator(&overworld, mc, 0);
    applySeed(&overworld, DIM_OVERWORLD, seed);
    setupGenerator(&nether, mc, 0);
    applySeed(&nether, DIM_NETHER, seed);
    init_flower_noise(&flowers);

    accepted = (int *) calloc((size_t) max_rows, 4 * sizeof(int));
    checked = (int *) calloc((size_t) FLW_MAX_ROWS * 8, 4 * sizeof(int));
    if (!accepted || !checked)
    {
        free(accepted);
        free(checked);
        free(cands);
        return 0;
    }

    for (i = 0; i < cand_count && rows < max_rows; i++)
    {
        WindowStats coarse, fine;
        int cell, flower_area, pale_area, swamp_area;
        int bonus = 0;
        int rank;
        int sample_x[512], sample_z[512], sample_n = 0;
        int flower_y = 0;
        const int side_x = cands[i].side_x;
        const int side_z = cands[i].side_z;
        const int wx = cands[i].x;
        const int wz = cands[i].z;

        if (in_window(wx, wz, side_x, side_z, accepted, rows) ||
            in_window(wx, wz, side_x, side_z, checked, checked_n))
            continue;
        if (checked_n < FLW_MAX_ROWS * 8)
        {
            checked[checked_n * 4 + 0] = wx;
            checked[checked_n * 4 + 1] = wz;
            checked[checked_n * 4 + 2] = side_x;
            checked[checked_n * 4 + 3] = side_z;
            checked_n++;
        }

        classify_window(&overworld, wx, wz, side_x, side_z, 16, &coarse);
        if (coarse.flower_cells < 2 || coarse.swamp_cells < 1)
            continue;
        if (require_pale && coarse.pale_cells < 1)
            continue;

        cell = side_x > side_z ? side_x : side_z;
        cell = cell / 80;
        if (cell < 4)
            cell = 4;
        classify_window(&overworld, wx, wz, side_x, side_z, cell, &fine);
        flower_area = fine.flower_cells * fine.area_per_cell;
        pale_area = fine.pale_cells * fine.area_per_cell;
        swamp_area = fine.swamp_cells * fine.area_per_cell;
        if (getenv("FLW_DEBUG"))
            fprintf(stderr, "[flw] cand (%d,%d) %dx%d coarse ff=%d sw=%d pg=%d | fine cell=%d ff=%d sw=%d pg=%d\n",
                    wx, wz, side_x, side_z, coarse.flower_cells, coarse.swamp_cells,
                    coarse.pale_cells, cell, fine.flower_cells, fine.swamp_cells,
                    fine.pale_cells);
        if (flower_area <= FLW_FLOWER_AREA_MIN)
            continue;
        if (swamp_area < 64)
            continue;
        if (require_pale && pale_area < 64)
            continue;

        if (fine.birch_cells > 0 || fine.old_cells > 0 || fine.meadow_cells > 0)
            bonus |= FLW_BONUS_WILDFLOWERS;
        if (fine.cherry_cells > 0)
            bonus |= FLW_BONUS_PINK_PETALS;
        if (require_both_bonus &&
            (bonus & (FLW_BONUS_WILDFLOWERS | FLW_BONUS_PINK_PETALS)) !=
                (FLW_BONUS_WILDFLOWERS | FLW_BONUS_PINK_PETALS))
            continue;

        rank = nether_rank(&nether, wx, wz, side_x, side_z);
        if (require_basalt && rank != FLW_NETHER_BASALT)
            continue;

        {
            int gx, gz;
            const int stride = cell;
            for (gz = 0; gz * cell < side_z && sample_n < 512; gz++)
                for (gx = 0; gx * cell < side_x && sample_n < 512; gx++)
                {
                    const int bx = wx + gx * stride;
                    const int bz = wz + gz * stride;
                    if (getBiomeAt(&overworld, 4, bx, FLW_BIOME_Y, bz) == flower_forest)
                    {
                        sample_x[sample_n] = bx;
                        sample_z[sample_n] = bz;
                        sample_n++;
                    }
                }
        }
        if (sample_n > 0)
            flower_extremes(&flowers, sample_x, sample_z, sample_n, &flower_y);

        accepted[rows * 4 + 0] = wx;
        accepted[rows * 4 + 1] = wz;
        accepted[rows * 4 + 2] = side_x;
        accepted[rows * 4 + 3] = side_z;

        {
            int32_t *row = out + (size_t) rows * 16;
            row[0] = wx + side_x / 2;
            row[1] = wz + side_z / 2;
            row[2] = side_x;
            row[3] = side_z;
            row[4] = flower_area;
            row[5] = pale_area;
            row[6] = swamp_area;
            row[7] = fine.flower_x;
            row[8] = flower_y;
            row[9] = fine.flower_z;
            row[10] = fine.pale_x;
            row[11] = fine.pale_z;
            row[12] = fine.swamp_x;
            row[13] = fine.swamp_z;
            row[14] = rank;
            row[15] = bonus;
        }
        rows++;
        if ((size_t) rows * 16 >= (size_t) capacity)
            break;
    }

    free(accepted);
    free(checked);
    free(cands);
    return rows;
}
