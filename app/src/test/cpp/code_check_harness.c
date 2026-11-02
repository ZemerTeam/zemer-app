// Host check of code_check.c: this program's own code is unchanged (0), then one byte is patched in memory (1),
// then restored (0). Prints "skip" when the host refuses to make code writable.
#define _GNU_SOURCE
#include <stdint.h>
#include <stdio.h>
#include <sys/mman.h>
#include <unistd.h>
#include "code_check.h"

__attribute__((noinline)) int target(int x) { return x * 3 + 1; }

int main(void) {
    const void *fn = (const void *)target;
    printf("%d\n", zemer_code_patched_at(fn));
    long page = sysconf(_SC_PAGESIZE);
    uintptr_t start = (uintptr_t)fn & ~(uintptr_t)(page - 1);
    if (mprotect((void *)start, (size_t)page * 2, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        printf("skip\n");
        return 0;
    }
    volatile uint8_t *code = (volatile uint8_t *)fn;
    code[0] ^= 0xff;
    printf("%d\n", zemer_code_patched_at(fn));
    code[0] ^= 0xff;
    printf("%d\n", zemer_code_patched_at(fn));
    return 0;
}
