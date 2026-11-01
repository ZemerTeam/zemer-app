// Host harness for SigNativeHostTest: each argument is an APK path; prints the signer certificate's SHA-256 (hex),
// or "-" when it cannot be read, one line per path, from the production apk_cert.c.
#include <stdio.h>
#include "apk_cert.h"

int main(int argc, char **argv) {
    for (int i = 1; i < argc; i++) {
        uint8_t digest[32];
        if (zemer_apk_cert_digest(argv[i], digest)) {
            for (int j = 0; j < 32; j++) printf("%02x", digest[j]);
            printf("\n");
        } else {
            printf("-\n");
        }
    }
    return 0;
}
