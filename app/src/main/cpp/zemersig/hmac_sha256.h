#ifndef ZEMER_HMAC_SHA256_H
#define ZEMER_HMAC_SHA256_H
#include <stddef.h>
#include <stdint.h>

void zemer_hmac_sha256(const uint8_t *key, size_t key_len, const uint8_t *msg, size_t msg_len, uint8_t out[32]);

#endif
