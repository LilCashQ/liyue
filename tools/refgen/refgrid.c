#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

#include "biomes.h"
#include "generator.h"

/*
 * Reference implementation of the exact 4-block-cell grid used by the app:
 * generate the block-level biome map with the voronoi path (scale=1) and
 * downsample at cell centres. Pre-1.18 overworld uses the 1:4 layer directly.
 *
 * usage: refgrid <seed> <mcEnum> <dim> <blockX> <blockZ> <cells> <y>
 */
static uint64_t fnv1a(const int *ids, long count)
{
    uint64_t hash = 1469598103934665603ULL;
    for (long i = 0; i < count; i++) {
        uint32_t v = (uint32_t)ids[i];
        for (int b = 0; b < 4; b++) {
            hash ^= (v >> (b * 8)) & 0xff;
            hash *= 1099511628211ULL;
        }
    }
    return hash;
}

int main(int argc, char **argv)
{
    if (argc != 8) {
        fprintf(stderr, "usage: %s <seed> <mcEnum> <dim> <blockX> <blockZ> <cells> <y>\n", argv[0]);
        return 2;
    }

    uint64_t seed = strtoull(argv[1], NULL, 10);
    int mc = atoi(argv[2]);
    int dim = atoi(argv[3]);
    int blockX = atoi(argv[4]);
    int blockZ = atoi(argv[5]);
    int cells = atoi(argv[6]);
    int y = atoi(argv[7]);

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, dim, seed);

    int *ids = (int *)malloc(sizeof(int) * cells * cells);
    if (ids == NULL)
        return 1;

    if (mc <= MC_1_17 && dim == DIM_OVERWORLD) {
        Range r = {4, blockX / 4, blockZ / 4, cells, cells, 63, 1};
        int *cache = allocCache(&g, r);
        if (cache == NULL || genBiomes(&g, cache, r) != 0) {
            fprintf(stderr, "layer generation failed\n");
            return 1;
        }
        for (int i = 0; i < cells * cells; i++)
            ids[i] = cache[i];
        free(cache);
    } else {
        Range r = {1, blockX, blockZ, cells * 4, cells * 4, y, 1};
        int *cache = allocCache(&g, r);
        if (cache == NULL || genBiomes(&g, cache, r) != 0) {
            fprintf(stderr, "block generation failed\n");
            return 1;
        }
        for (int z = 0; z < cells; z++)
            for (int x = 0; x < cells; x++)
                ids[z * cells + x] = cache[(z * 4 + 2) * cells * 4 + (x * 4 + 2)];
        free(cache);
    }

    printf("FIELDS\tseed=%llu mc=%d dim=%d blockX=%d blockZ=%d cells=%d y=%d\n",
        (unsigned long long)seed, mc, dim, blockX, blockZ, cells, y);
    printf("HASH\t%016llx\n", (unsigned long long)fnv1a(ids, (long)cells * cells));
    for (int z = 0; z < cells; z++) {
        for (int x = 0; x < cells; x++)
            printf("%d%s", ids[z * cells + x], x + 1 == cells ? "\n" : ",");
    }

    free(ids);
    return 0;
}
