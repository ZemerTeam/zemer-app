// The request-signing key, masked: the build generates zemer_sig_key.h (never in the source tree). The key is
// rebuilt on the stack only for the HMAC and wiped right after, so it never exists as a Java/Kotlin object.
// A release key is also bound to the APK's signing certificate: the build XORs in SHA-256 of the release
// certificate, and this library XORs back in the digest of the certificate the running APK is signed with, read
// here from the package manager (bind), so an APK signed with another certificate computes a wrong key.
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include "apk_cert.h"
#include "code_check.h"
#include "hmac_sha256.h"
#include "zemer_sig_key.h"
#include "zemer_sig_unmask.h"

#define GET_SIGNATURES 0x40
#define GET_SIGNING_CERTIFICATES 0x08000000

static uint8_t cert_digest[32];
static int cert_ready;

// SHA-256 of the running APK's current signing certificate. False when it cannot be read (nothing is thrown).
static int read_cert_digest(JNIEnv *env, jobject context, uint8_t out[32]) {
    int ok = 0;
    jclass cls;
    jmethodID method;
    jfieldID field;
    jobject pm, name, info, signing;
    jobjectArray signers = NULL;
    jbyteArray der;
    jbyte *bytes;
    if ((*env)->PushLocalFrame(env, 32) != 0) {
        (*env)->ExceptionClear(env);
        return 0;
    }
#define REQUIRE(x) do { if ((x) == NULL || (*env)->ExceptionCheck(env)) goto done; } while (0)
    REQUIRE(cls = (*env)->GetObjectClass(env, context));
    REQUIRE(method = (*env)->GetMethodID(env, cls, "getPackageManager", "()Landroid/content/pm/PackageManager;"));
    REQUIRE(pm = (*env)->CallObjectMethod(env, context, method));
    REQUIRE(method = (*env)->GetMethodID(env, cls, "getPackageName", "()Ljava/lang/String;"));
    REQUIRE(name = (*env)->CallObjectMethod(env, context, method));
    REQUIRE(cls = (*env)->FindClass(env, "android/os/Build$VERSION"));
    REQUIRE(field = (*env)->GetStaticFieldID(env, cls, "SDK_INT", "I"));
    int modern = (*env)->GetStaticIntField(env, cls, field) >= 28;
    REQUIRE(cls = (*env)->GetObjectClass(env, pm));
    REQUIRE(method = (*env)->GetMethodID(env, cls, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;"));
    REQUIRE(info = (*env)->CallObjectMethod(env, pm, method, name, modern ? GET_SIGNING_CERTIFICATES : GET_SIGNATURES));
    REQUIRE(cls = (*env)->GetObjectClass(env, info));
    if (modern) {
        // the current signer (after a key rotation, the newest certificate)
        REQUIRE(field = (*env)->GetFieldID(env, cls, "signingInfo", "Landroid/content/pm/SigningInfo;"));
        REQUIRE(signing = (*env)->GetObjectField(env, info, field));
        REQUIRE(cls = (*env)->GetObjectClass(env, signing));
        REQUIRE(method = (*env)->GetMethodID(env, cls, "getApkContentsSigners", "()[Landroid/content/pm/Signature;"));
        REQUIRE(signers = (*env)->CallObjectMethod(env, signing, method));
    } else {
        REQUIRE(field = (*env)->GetFieldID(env, cls, "signatures", "[Landroid/content/pm/Signature;"));
        REQUIRE(signers = (*env)->GetObjectField(env, info, field));
    }
    if ((*env)->GetArrayLength(env, signers) < 1) goto done;
    REQUIRE(signing = (*env)->GetObjectArrayElement(env, signers, 0));
    REQUIRE(cls = (*env)->GetObjectClass(env, signing));
    REQUIRE(method = (*env)->GetMethodID(env, cls, "toByteArray", "()[B"));
    REQUIRE(der = (*env)->CallObjectMethod(env, signing, method));
    REQUIRE(bytes = (*env)->GetByteArrayElements(env, der, NULL));
    zemer_sha256((const uint8_t *)bytes, (size_t)(*env)->GetArrayLength(env, der), out);
    (*env)->ReleaseByteArrayElements(env, der, bytes, JNI_ABORT);
    ok = 1;
#undef REQUIRE
done:
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    (*env)->PopLocalFrame(env, NULL);
    return ok;
}

// This process's own APK, found in its memory map (the app's code is mapped from it) by the package name in its
// command line, without asking any Java API: "/data/app/[~~…/]<package>-…/base.apk".
static int own_apk_path(char *out, size_t cap) {
    char pkg[256] = {0}, line[1024], needle[260];
    FILE *f = fopen("/proc/self/cmdline", "r");
    if (f == NULL) return 0;
    size_t n = fread(pkg, 1, sizeof pkg - 1, f);
    fclose(f);
    if (n == 0) return 0;
    char *colon = strchr(pkg, ':'); // a ":service" process
    if (colon != NULL) *colon = 0;
    snprintf(needle, sizeof needle, "/%s-", pkg);
    int found = 0;
    if ((f = fopen("/proc/self/maps", "r")) == NULL) return 0;
    while (!found && fgets(line, sizeof line, f) != NULL) {
        char *path = strchr(line, '/');
        if (path == NULL) continue;
        path[strcspn(path, "\n")] = 0;
        size_t len = strlen(path);
        if (len > 9 && len < cap && strcmp(path + len - 9, "/base.apk") == 0 && strstr(path, needle) != NULL) {
            memcpy(out, path, len + 1);
            found = 1;
        }
    }
    fclose(f);
    return found;
}

static int cert_mismatch;

// Reads the signing certificate once per process, twice and independently: from the package manager and from the
// APK file's own signing block. Both readable but different = a wrong digest (the server sees an invalid
// signature) and a mismatch; one unreadable = the other is used (a read failure never invalidates a request); none
// = a bound key signs with an all-zero digest (invalid), a request is never dropped for it. A release build also
// compares with the certificate it was bound to.
JNIEXPORT void JNICALL
Java_com_jtech_zemer_sig_NativeSig_bind(JNIEnv *env, jclass clazz, jobject context) {
    (void)clazz;
    if (__atomic_load_n(&cert_ready, __ATOMIC_ACQUIRE)) return;
    uint8_t from_pm[32], from_apk[32], digest[32];
    char path[512];
    int has_pm = read_cert_digest(env, context, from_pm);
    int has_apk = own_apk_path(path, sizeof path) && zemer_apk_cert_digest(path, from_apk);
    int disagree = has_pm && has_apk && memcmp(from_pm, from_apk, 32) != 0;
    if (!has_pm && !has_apk) return;
    for (int i = 0; i < 32; i++) {
        digest[i] = has_pm && has_apk ? (disagree ? from_pm[i] ^ from_apk[i] : from_pm[i]) : (has_pm ? from_pm[i] : from_apk[i]);
    }
    cert_mismatch = disagree || (ZEMER_SIG_CERT_BOUND && memcmp(digest, zemer_sig_cert, 32) != 0);
    memcpy(cert_digest, digest, 32);
    __atomic_store_n(&cert_ready, 1, __ATOMIC_RELEASE);
}

// The two certificate reads disagreed, or (release) the APK is not signed with the certificate the key is bound to.
JNIEXPORT jboolean JNICALL
Java_com_jtech_zemer_sig_NativeSig_nativeCertMismatch(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return __atomic_load_n(&cert_ready, __ATOMIC_ACQUIRE) && cert_mismatch ? JNI_TRUE : JNI_FALSE;
}

// This library's own code differs in memory from the APK it is mapped from (hooked or patched): 1, else 0; -1 when
// it cannot be checked.
JNIEXPORT jint JNICALL
Java_com_jtech_zemer_sig_NativeSig_nativeCodePatched(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return zemer_code_patched_at((const void *)Java_com_jtech_zemer_sig_NativeSig_nativeCodePatched);
}

JNIEXPORT jbyteArray JNICALL
Java_com_jtech_zemer_sig_NativeSig_hmac(JNIEnv *env, jclass clazz, jbyteArray message) {
    (void)clazz;
    jsize len = (*env)->GetArrayLength(env, message);
    jbyte *msg = (*env)->GetByteArrayElements(env, message, NULL);
    if (msg == NULL) return NULL;
    uint8_t cert[32] = {0}, key[ZEMER_SIG_KEY_LEN], mac[32];
    if (ZEMER_SIG_CERT_BOUND && __atomic_load_n(&cert_ready, __ATOMIC_ACQUIRE)) memcpy(cert, cert_digest, 32);
    zemer_sig_unmask(zemer_sig_a, zemer_sig_b, zemer_sig_p, cert, key);
    zemer_hmac_sha256(key, ZEMER_SIG_KEY_LEN, (const uint8_t *)msg, (size_t)len, mac);
    volatile uint8_t *v = key;
    for (int i = 0; i < ZEMER_SIG_KEY_LEN; i++) v[i] = 0;
    (*env)->ReleaseByteArrayElements(env, message, msg, JNI_ABORT);
    jbyteArray out = (*env)->NewByteArray(env, 32);
    if (out != NULL) (*env)->SetByteArrayRegion(env, out, 0, 32, (const jbyte *)mac);
    return out;
}
