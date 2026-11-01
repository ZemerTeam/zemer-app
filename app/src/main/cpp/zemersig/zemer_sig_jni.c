// The request-signing key, masked: the build generates zemer_sig_key.h (never in the source tree). The key is
// rebuilt on the stack only for the HMAC and wiped right after, so it never exists as a Java/Kotlin object.
// A release key is also bound to the APK's signing certificate: the build XORs in SHA-256 of the release
// certificate, and this library XORs back in the digest of the certificate the running APK is signed with, read
// here from the package manager (bind), so an APK signed with another certificate computes a wrong key.
#include <jni.h>
#include <string.h>
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

// Reads the signing certificate once per process. Until it succeeds a bound key signs with an all-zero digest,
// which the server rejects as an invalid signature; a request is never dropped for it.
JNIEXPORT void JNICALL
Java_com_jtech_zemer_sig_NativeSig_bind(JNIEnv *env, jclass clazz, jobject context) {
    (void)clazz;
    uint8_t digest[32];
    if (__atomic_load_n(&cert_ready, __ATOMIC_ACQUIRE) || !read_cert_digest(env, context, digest)) return;
    memcpy(cert_digest, digest, 32);
    __atomic_store_n(&cert_ready, 1, __ATOMIC_RELEASE);
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
