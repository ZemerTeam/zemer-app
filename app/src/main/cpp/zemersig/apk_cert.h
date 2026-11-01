#ifndef ZEMER_APK_CERT_H
#define ZEMER_APK_CERT_H
#include <stdint.h>

// SHA-256 of the first signer's certificate in the APK at [path] (v3 block, else v2). 0 when it cannot be read.
int zemer_apk_cert_digest(const char *path, uint8_t out[32]);

#endif
