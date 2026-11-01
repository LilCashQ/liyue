#include <stdio.h>
#include <stdlib.h>

#include "biomes.h"
#include "util.h"

/* Emits "id<TAB>name" for every biome id cubiomes knows for the given version. */
int main(int argc, char **argv)
{
    int mc = MC_1_20;
    if (argc > 1)
        mc = atoi(argv[1]);

    for (int id = 0; id < 256; id++) {
        const char *name = biome2str(mc, id);
        if (name != NULL)
            printf("%d\t%s\n", id, name);
    }
    return 0;
}
