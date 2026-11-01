#ifndef ZEMER_SIG_UNMASK_H
#define ZEMER_SIG_UNMASK_H
#include <stdint.h>

// The inverse of the build's masking (generateZemerSigKey*): key[i] = masked[order[i]] ^ mask[i] ^ cert[i], where
// cert is SHA-256 of the signing certificate for a bound key and all zeros for an unbound one.
static inline void zemer_sig_unmask(const uint8_t masked[32], const uint8_t mask[32], const uint8_t order[32],
                                    const uint8_t cert[32], uint8_t key[32]) {
    for (int i = 0; i < 32; i++) key[i] = (uint8_t)(masked[order[i]] ^ mask[i] ^ cert[i]);
}

#endif
