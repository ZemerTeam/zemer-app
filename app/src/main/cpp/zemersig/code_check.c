// Compares a library's executable segment in memory with the same bytes in the file it is mapped from. Code
// segments carry no relocations (position-independent, no text relocations), so any difference is a write into
// the code: an inline hook trampoline, a patched instruction or a software breakpoint. Information only (a bit of
// X-Zemer-Env); nothing in the app changes on it.
#define _GNU_SOURCE
#include "code_check.h"
#include <fcntl.h>
#include <link.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

struct segment {
    uintptr_t addr; // the address to find
    uintptr_t start;
    size_t len;
};

static int find_segment(struct dl_phdr_info *info, size_t size, void *data) {
    (void)size;
    struct segment *s = data;
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr) *ph = &info->dlpi_phdr[i];
        if (ph->p_type != PT_LOAD || !(ph->p_flags & PF_X)) continue;
        uintptr_t start = info->dlpi_addr + ph->p_vaddr;
        if (s->addr >= start && s->addr < start + ph->p_filesz) {
            s->start = start;
            s->len = ph->p_filesz;
            return 1;
        }
    }
    return 0;
}

// The file backing [addr] and the file offset of [addr], from this process's memory map.
static int backing_file(uintptr_t addr, char *path, size_t cap, long *offset, uintptr_t *map_end) {
    char line[1024];
    FILE *f = fopen("/proc/self/maps", "r");
    if (f == NULL) return 0;
    int found = 0;
    while (!found && fgets(line, sizeof line, f) != NULL) {
        unsigned long start, end, off;
        char perms[8];
        int consumed = 0;
        if (sscanf(line, "%lx-%lx %7s %lx %*s %*s%n", &start, &end, perms, &off, &consumed) < 4) continue;
        if (addr < start || addr >= end) continue;
        char *p = strchr(line + consumed, '/');
        if (p == NULL) break;
        p[strcspn(p, "\n")] = 0;
        if (strlen(p) >= cap) break;
        strcpy(path, p);
        *offset = (long)(off + (addr - start));
        *map_end = end;
        found = 1;
    }
    fclose(f);
    return found;
}

int zemer_code_patched_at(const void *addr) {
    struct segment s = {(uintptr_t)addr, 0, 0};
    if (!dl_iterate_phdr(find_segment, &s) || s.len == 0) return -1;
    char path[512];
    long offset;
    uintptr_t map_end;
    if (!backing_file(s.start, path, sizeof path, &offset, &map_end)) return -1;
    size_t len = s.len;
    if (s.start + len > map_end) len = map_end - s.start; // compare what this mapping holds
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    uint8_t buf[16384];
    int result = 0;
    for (size_t done = 0; done < len && result == 0;) {
        size_t want = len - done < sizeof buf ? len - done : sizeof buf;
        ssize_t got = pread(fd, buf, want, offset + (long)done);
        if (got <= 0) {
            result = -1;
            break;
        }
        if (memcmp((const void *)(s.start + done), buf, (size_t)got) != 0) result = 1;
        done += (size_t)got;
    }
    close(fd);
    return result;
}
