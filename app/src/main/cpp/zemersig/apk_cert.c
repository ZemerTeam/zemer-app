// The signing certificate read straight from an APK file's signing block (APK Signature Scheme v3, else v2), an
// independent check of what the package manager reports. Dependency-free so it is unit-tested on the host
// (SigNativeHostTest) against a signed fixture.
#include "apk_cert.h"
#include "hmac_sha256.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define EOCD_MAGIC 0x06054b50u
#define EOCD_MIN 22
#define MAX_COMMENT 0xffff
#define V2_ID 0x7109871au
#define V3_ID 0xf05368c0u
#define MAX_BLOCK (16u * 1024 * 1024)

static uint32_t le32(const uint8_t *p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24); }
static uint64_t le64(const uint8_t *p) { return le32(p) | ((uint64_t)le32(p + 4) << 32); }

static int read_at(FILE *f, long off, void *buf, size_t len) {
    return fseek(f, off, SEEK_SET) == 0 && fread(buf, 1, len, f) == len;
}

// A length-prefixed (uint32) slice inside [*p, end): its start in *out and length in *len; advances *p.
static int lp(const uint8_t **p, const uint8_t *end, const uint8_t **out, uint32_t *len) {
    if (end - *p < 4) return 0;
    uint32_t n = le32(*p);
    if ((size_t)(end - *p - 4) < n) return 0;
    *out = *p + 4;
    *len = n;
    *p += 4 + n;
    return 1;
}

// The first certificate of the first signer in a v2/v3 scheme block: signers -> signer -> signed data ->
// (digests, certificates -> certificate).
static int first_cert(const uint8_t *block, uint32_t len, const uint8_t **cert, uint32_t *cert_len) {
    const uint8_t *p = block, *end = block + len, *signers, *signer, *signed_data, *digests, *certs;
    uint32_t n;
    if (!lp(&p, end, &signers, &n)) return 0;
    p = signers; end = signers + n;
    if (!lp(&p, end, &signer, &n)) return 0;
    p = signer; end = signer + n;
    if (!lp(&p, end, &signed_data, &n)) return 0;
    p = signed_data; end = signed_data + n;
    if (!lp(&p, end, &digests, &n) || !lp(&p, end, &certs, &n)) return 0;
    p = certs; end = certs + n;
    return lp(&p, end, cert, cert_len) && *cert_len > 0;
}

int zemer_apk_cert_digest(const char *path, uint8_t out[32]) {
    int ok = 0;
    uint8_t *tail = NULL, *block = NULL;
    FILE *f = fopen(path, "rb");
    if (f == NULL) return 0;
    if (fseek(f, 0, SEEK_END) != 0) goto done;
    long size = ftell(f);
    if (size < EOCD_MIN) goto done;
    // the end of central directory record: the last EOCD magic within the final 22 + 65535 bytes
    long tail_len = size < EOCD_MIN + MAX_COMMENT ? size : EOCD_MIN + MAX_COMMENT;
    tail = malloc((size_t)tail_len);
    if (tail == NULL || !read_at(f, size - tail_len, tail, (size_t)tail_len)) goto done;
    long eocd = -1;
    for (long i = tail_len - EOCD_MIN; i >= 0; i--) {
        if (le32(tail + i) == EOCD_MAGIC) { eocd = i; break; }
    }
    if (eocd < 0) goto done;
    long cd_offset = (long)le32(tail + eocd + 16);
    // the signing block ends right before the central directory: ... size (uint64) "APK Sig Block 42"
    uint8_t footer[24];
    if (cd_offset < 32 || !read_at(f, cd_offset - 24, footer, 24) || memcmp(footer + 8, "APK Sig Block 42", 16) != 0) goto done;
    uint64_t block_size = le64(footer);
    if (block_size < 24 || block_size > MAX_BLOCK || (uint64_t)cd_offset < block_size + 8) goto done;
    // the id-value pairs sit between the leading size field and the trailing size + magic
    uint32_t pairs_len = (uint32_t)(block_size - 24);
    block = malloc(pairs_len);
    if (block == NULL || !read_at(f, cd_offset - (long)block_size + 0, block, pairs_len)) goto done;
    const uint8_t *v2 = NULL, *v3 = NULL;
    uint32_t v2_len = 0, v3_len = 0;
    for (const uint8_t *p = block, *end = block + pairs_len; end - p >= 12;) {
        uint64_t n = le64(p);
        if (n < 4 || n > (uint64_t)(end - p - 8)) break;
        uint32_t id = le32(p + 8);
        if (id == V3_ID) { v3 = p + 12; v3_len = (uint32_t)n - 4; }
        if (id == V2_ID) { v2 = p + 12; v2_len = (uint32_t)n - 4; }
        p += 8 + n;
    }
    const uint8_t *cert;
    uint32_t cert_len;
    // v3 names the current signer after a key rotation; v2 otherwise
    if ((v3 != NULL && first_cert(v3, v3_len, &cert, &cert_len)) || (v2 != NULL && first_cert(v2, v2_len, &cert, &cert_len))) {
        zemer_sha256(cert, cert_len, out);
        ok = 1;
    }
done:
    free(tail);
    free(block);
    fclose(f);
    return ok;
}
