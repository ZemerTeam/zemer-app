// The request-signing key, masked: the build generates zemer_sig_key.h (never in the source tree). The key is
// rebuilt on the stack only for the HMAC and wiped right after, so it never exists as a Java/Kotlin object.
#include <jni.h>
#include "hmac_sha256.h"
#include "zemer_sig_key.h"

JNIEXPORT jbyteArray JNICALL
Java_com_jtech_zemer_sig_NativeSig_hmac(JNIEnv *env, jclass clazz, jbyteArray message) {
    (void)clazz;
    jsize len = (*env)->GetArrayLength(env, message);
    jbyte *msg = (*env)->GetByteArrayElements(env, message, NULL);
    if (msg == NULL) return NULL;
    uint8_t key[ZEMER_SIG_KEY_LEN], mac[32];
    for (int i = 0; i < ZEMER_SIG_KEY_LEN; i++) key[i] = (uint8_t)(zemer_sig_a[ZEMER_SIG_ORDER(i)] ^ zemer_sig_b[i]);
    zemer_hmac_sha256(key, ZEMER_SIG_KEY_LEN, (const uint8_t *)msg, (size_t)len, mac);
    volatile uint8_t *v = key;
    for (int i = 0; i < ZEMER_SIG_KEY_LEN; i++) v[i] = 0;
    (*env)->ReleaseByteArrayElements(env, message, msg, JNI_ABORT);
    jbyteArray out = (*env)->NewByteArray(env, 32);
    if (out != NULL) (*env)->SetByteArrayRegion(env, out, 0, 32, (const jbyte *)mac);
    return out;
}
