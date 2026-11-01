// Host harness for SigNativeHostTest: one line = masked, mask, order and certificate DER (hex, space-separated);
// prints the key the production signer rebuilds (zemer_sig_unmask over SHA-256 of the certificate, or an all-zero
// digest when the certificate field is "-", as for an unbound key).
#include <stdio.h>
#include <string.h>
#include "hmac_sha256.h"
#include "zemer_sig_unmask.h"

static size_t unhex(const char *s, uint8_t *out) {
    size_t n = 0;
    while (s[0] && s[1] && s[0] != ' ' && s[0] != '\n') { unsigned v; sscanf(s, "%2x", &v); out[n++] = (uint8_t)v; s += 2; }
    return n;
}

int main(void) {
    static char line[1 << 16];
    static uint8_t der[1 << 14];
    uint8_t masked[32], mask[32], order[32], cert[32] = {0}, key[32];
    while (fgets(line, sizeof line, stdin)) {
        char *p = line;
        unhex(p, masked); p = strchr(p, ' ') + 1;
        unhex(p, mask); p = strchr(p, ' ') + 1;
        unhex(p, order); p = strchr(p, ' ') + 1;
        if (*p == '-') memset(cert, 0, 32);
        else zemer_sha256(der, unhex(p, der), cert);
        zemer_sig_unmask(masked, mask, order, cert, key);
        for (int i = 0; i < 32; i++) printf("%02x", key[i]);
        printf("\n");
    }
    return 0;
}
