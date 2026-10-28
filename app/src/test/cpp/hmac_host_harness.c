// Host harness for SigNativeHostTest: line 1 = key (hex); every further line = a message (hex);
// prints one HMAC-SHA256 (hex) per message, computed by the production hmac_sha256.c.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "hmac_sha256.h"

static size_t unhex(const char *s, uint8_t *out) {
    size_t n = 0;
    while (s[0] && s[1] && s[0] != '\n') { unsigned v; sscanf(s, "%2x", &v); out[n++] = (uint8_t)v; s += 2; }
    return n;
}

int main(void) {
    static char line[1 << 20];
    static uint8_t buf[1 << 19];
    uint8_t key[256], mac[32];
    if (!fgets(line, sizeof line, stdin)) return 1;
    size_t key_len = unhex(line, key);
    while (fgets(line, sizeof line, stdin)) {
        size_t n = unhex(line, buf);
        zemer_hmac_sha256(key, key_len, buf, n, mac);
        for (int i = 0; i < 32; i++) printf("%02x", mac[i]);
        printf("\n");
    }
    return 0;
}
