#include <stdint.h>
#include <string.h>

#include "cubiomes/finders.h"
#include "cubiomes/quadbase.h"

/*
 * Quad witch huts, the way cubiomes-viewer reports them: enumerate the
 * quad-bases of the seed inside a window of structure regions, then rebuild
 * the four huts of each base at the corresponding absolute position.
 *
 * Results are written as (blockX, blockZ, spawnSpaces) triples, one per quad.
 */
int liyue_quad_huts(
        int64_t seed,
        int mc,
        int minRegionX,
        int minRegionZ,
        int maxRegionX,
        int maxRegionZ,
        int32_t *out,
        int capacity)
{
    if (!out || capacity <= 0) return 0;
    if (maxRegionX < minRegionX || maxRegionZ < minRegionZ) return 0;

    StructureConfig sconf;
    if (!getStructureConfig(Swamp_Hut, mc, &sconf)) return 0;

    enum { MAX_QUADS = 64 };
    Pos quads[MAX_QUADS];
    const int width = maxRegionX - minRegionX + 1;
    const int height = maxRegionZ - minRegionZ + 1;

    int found = scanForQuads(
            sconf, 128, (uint64_t)seed, low20QuadHutNormal, 20, sconf.salt,
            minRegionX, minRegionZ, width, height, quads, MAX_QUADS);
    if (found <= 0) return 0;

    int written = 0;
    for (int i = 0; i < found && written + 1 < capacity; i++)
    {
        Pos quad = quads[i];
        uint64_t shifted = moveStructure((uint64_t)seed, -quad.x, -quad.z);

        Pos p[4];
        p[0] = getFeaturePos(sconf, shifted, 0, 0);
        p[1] = getFeaturePos(sconf, shifted, 0, 1);
        p[2] = getFeaturePos(sconf, shifted, 1, 0);
        p[3] = getFeaturePos(sconf, shifted, 1, 1);

        int spawn = 0;
        Pos afk = getOptimalAfk(p, 7, 7, 9, &spawn);

        /* one structure region spans 512 blocks (spacing 32 chunks) */
        out[written * 3 + 0] = (quad.x << 9) + afk.x;
        out[written * 3 + 1] = (quad.z << 9) + afk.z;
        out[written * 3 + 2] = spawn;
        written++;
        if (written * 3 + 3 > capacity) break;
    }
    return written;
}
