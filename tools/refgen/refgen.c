#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

#include "biomes.h"
#include "generator.h"

/*
 * Reference generator: prints a biome grid straight from cubiomes, without the
 * JNI layer. Used to cross-check the Kotlin/JNI path and to produce fixture
 * values for unit tests.
 *
 * usage: refgen <seed> <mcEnum> <dim> <scale> <x> <z> <w> <h> <y>
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
    if (argc != 10) {
        fprintf(stderr, "usage: %s <seed> <mcEnum> <dim> <scale> <x> <z> <w> <h> <y>\n", argv[0]);
        return 2;
    }

    uint64_t seed = strtoull(argv[1], NULL, 10);
    int mc = atoi(argv[2]);
    int dim = atoi(argv[3]);
    int scale = atoi(argv[4]);
    int x = atoi(argv[5]);
    int z = atoi(argv[6]);
    int w = atoi(argv[7]);
    int h = atoi(argv[8]);
    int y = atoi(argv[9]);

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, dim, seed);

    Range r = {scale, x, z, w, h, y, 1};
    int *ids = allocCache(&g, r);
    if (ids == NULL) {
        fprintf(stderr, "allocCache failed\n");
        return 1;
    }

    int err = genBiomes(&g, ids, r);
    if (err != 0) {
        fprintf(stderr, "genBiomes failed: %d\n", err);
        free(ids);
        return 1;
    }

    printf("FIELDS\tseed=%llu mc=%d dim=%d scale=%d x=%d z=%d w=%d h=%d y=%d\n",
        (unsigned long long)seed, mc, dim, scale, x, z, w, h, y);
    printf("HASH\t%016llx\n", (unsigned long long)fnv1a(ids, (long)w * h));
    for (int iz = 0; iz < h; iz++) {
        for (int ix = 0; ix < w; ix++) {
            printf("%d%s", ids[iz * w + ix], ix + 1 == w ? "\n" : ",");
        }
    }

    free(ids);
    return 0;
}
