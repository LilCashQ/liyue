#include <stdio.h>

#include "biomes.h"
#include "finders.h"
#include "generator.h"

/*
 * Emits the measured capability matrix that backs docs/capability-matrix.md.
 * Every cell is derived from a real cubiomes probe, never from assumptions.
 *
 * usage: capmatrix > capability.csv
 */

typedef struct {
    int mc;
    const char *label;
} Version;

static const Version VERSIONS[] = {
    {MC_1_7_10, "1.7.10"},
    {MC_1_8_9, "1.8.9"},
    {MC_1_9_4, "1.9.4"},
    {MC_1_10_2, "1.10.2"},
    {MC_1_11_2, "1.11.2"},
    {MC_1_12_2, "1.12.2"},
    {MC_1_13_2, "1.13.2"},
    {MC_1_14_4, "1.14.4"},
    {MC_1_15_2, "1.15.2"},
    {MC_1_16_1, "1.16.1"},
    {MC_1_16_5, "1.16.5"},
    {MC_1_17_1, "1.17.1"},
    {MC_1_18_2, "1.18.2"},
    {MC_1_19_2, "1.19.2"},
    {MC_1_19_4, "1.19.4"},
    {MC_1_20_6, "1.20.6"},
    {MC_1_21_1, "1.21.1"},
    {MC_1_21_3, "1.21.3"},
};

static const int DIMS[] = {0, -1, 1};

static const int STRUCTURES[] = {
    Village, Monument, Mansion, Outpost, Ruined_Portal, Ancient_City,
    Trial_Chambers, Fortress, Bastion, End_City, Desert_Pyramid,
    Jungle_Temple, Swamp_Hut, Igloo, Ocean_Ruin, Shipwreck, Treasure,
    Mineshaft, Geode, Trail_Ruins, End_Gateway, End_Island,
};

static const char *STRUCTURE_NAMES[] = {
    "village", "monument", "mansion", "outpost", "ruined_portal", "ancient_city",
    "trial_chambers", "fortress", "bastion", "end_city", "desert_pyramid",
    "jungle_temple", "swamp_hut", "igloo", "ocean_ruin", "shipwreck", "treasure",
    "mineshaft", "geode", "trail_ruins", "end_gateway", "end_island",
};

int main(void)
{
    const int versionCount = (int)(sizeof(VERSIONS) / sizeof(VERSIONS[0]));
    const int structureCount = (int)(sizeof(STRUCTURES) / sizeof(STRUCTURES[0]));

    printf("version");
    for (int d = 0; d < 3; d++)
        printf(",dim%d", DIMS[d]);
    for (int s = 0; s < structureCount; s++)
        printf(",%s", STRUCTURE_NAMES[s]);
    printf(",stronghold\n");

    for (int v = 0; v < versionCount; v++) {
        printf("%s", VERSIONS[v].label);

        for (int d = 0; d < 3; d++) {
            Generator g;
            setupGenerator(&g, VERSIONS[v].mc, 0);
            applySeed(&g, DIMS[d], 0);
            int id = getBiomeAt(&g, 4, 0, 63, 0);
            printf(",%d", id >= 0 ? 1 : 0);
        }

        for (int s = 0; s < structureCount; s++) {
            StructureConfig conf;
            int ok = getStructureConfig(STRUCTURES[s], VERSIONS[v].mc, &conf);
            printf(",%d", ok ? 1 : 0);
        }

        Generator g;
        setupGenerator(&g, VERSIONS[v].mc, 0);
        applySeed(&g, 0, 0);
        StrongholdIter sh;
        initFirstStronghold(&sh, VERSIONS[v].mc, 0);
        printf(",%d\n", nextStronghold(&sh, &g) > 0 ? 1 : 0);
    }

    (void)structureCount;
    return 0;
}
