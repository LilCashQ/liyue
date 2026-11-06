#include <stdio.h>
#include <stdlib.h>

#include "biomes.h"
#include "finders.h"
#include "generator.h"

/*
 * Reference structure enumerator: prints the structures of one type inside a
 * block rectangle straight from cubiomes, using the same viability rules the
 * vanilla game uses (biome check everywhere, terrain check on 1.18+).
 *
 * This is deliberately a separate code path from the Kotlin/JNI one so the
 * finder's "nearest structure" answers can be cross-checked.
 *
 * usage: refstruct <seed> <mcEnum> <dim> <type> <x0> <z0> <x1> <z1>
 */
int main(int argc, char **argv)
{
    if (argc != 9) {
        fprintf(stderr, "usage: %s <seed> <mcEnum> <dim> <type> <x0> <z0> <x1> <z1>\n", argv[0]);
        return 2;
    }

    uint64_t seed = strtoull(argv[1], NULL, 10);
    int mc = atoi(argv[2]);
    int dim = atoi(argv[3]);
    int type = atoi(argv[4]);
    int x0 = atoi(argv[5]);
    int z0 = atoi(argv[6]);
    int x1 = atoi(argv[7]);
    int z1 = atoi(argv[8]);

    StructureConfig conf;
    if (!getStructureConfig(type, mc, &conf)) {
        fprintf(stderr, "structure %d unsupported for mc %d\n", type, mc);
        return 1;
    }
    if (conf.dim != dim) {
        return 0;
    }

    Generator g;
    setupGenerator(&g, mc, 0);
    applySeed(&g, dim, seed);

    int span = (conf.regionSize > 0 ? conf.regionSize : 1) * 16;
    int rx0 = x0 >= 0 ? x0 / span : (x0 - span + 1) / span;
    int rz0 = z0 >= 0 ? z0 / span : (z0 - span + 1) / span;
    int rx1 = x1 >= 0 ? x1 / span : (x1 - span + 1) / span;
    int rz1 = z1 >= 0 ? z1 / span : (z1 - span + 1) / span;

    for (int rz = rz0; rz <= rz1; rz++) {
        for (int rx = rx0; rx <= rx1; rx++) {
            Pos pos;
            if (!getStructurePos(type, mc, seed, rx, rz, &pos))
                continue;
            if (pos.x < x0 || pos.x > x1 || pos.z < z0 || pos.z > z1)
                continue;
            if (!isViableStructurePos(type, &g, pos.x, pos.z, 0))
                continue;
            if (mc >= MC_1_18 && dim == DIM_OVERWORLD && !isViableStructureTerrain(type, &g, pos.x, pos.z))
                continue;
            printf("%d,%d\n", pos.x, pos.z);
        }
    }
    return 0;
}
