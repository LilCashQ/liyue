#include <stdio.h>
#include <stdlib.h>

#include "biomes.h"
#include "generator.h"

/*
 * Exact point sampler: queries the biome at the centre of every cell with
 * scale=1 (voronoi-accurate for 1.18+). Slower than genBiomes but matches the
 * vanilla block-level biome.
 *
 * usage: refpoints <seed> <mcEnum> <dim> <x0> <z0> <cells> <step> <y>
 */
int main(int argc, char **argv)
{
    if (argc != 9) {
        fprintf(stderr, "usage: %s <seed> <mcEnum> <dim> <x0> <z0> <cells> <step> <y>\n", argv[0]);
        return 2;
    }

    uint64_t seed = strtoull(argv[1], NULL, 10);
    int mc = atoi(argv[2]);
    int dim = atoi(argv[3]);
    int x0 = atoi(argv[4]);
    int z0 = atoi(argv[5]);
    int cells = atoi(argv[6]);
    int step = atoi(argv[7]);
    int y = atoi(argv[8]);

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, dim, seed);

    for (int iz = 0; iz < cells; iz++) {
        for (int ix = 0; ix < cells; ix++) {
            int x = x0 + ix * step + step / 2;
            int z = z0 + iz * step + step / 2;
            int id = getBiomeAt(&g, 1, x, y, z);
            printf("%d%s", id, ix + 1 == cells ? "\n" : ",");
        }
    }
    return 0;
}
