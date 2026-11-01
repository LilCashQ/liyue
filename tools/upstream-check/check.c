#include <stdio.h>

/* Provided by cubiomes' own tests.c (compiled with -Dmain=cubiomes_unused_main). */
int testGeneration(void);

int main(void)
{
    int result = testGeneration();
    printf("cubiomes testGeneration -> %d\n", result);
    return result == 0 ? 0 : 1;
}
