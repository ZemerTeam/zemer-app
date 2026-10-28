package com.jtech.zemer.sig

/**
 * The native request signer (`libzemersig`, built only when the build has a signing key). The key stays in the
 * library: it returns only the MAC.
 */
internal object NativeSig {
    val available: Boolean = runCatching { System.loadLibrary("zemersig") }.isSuccess

    @JvmStatic
    external fun hmac(message: ByteArray): ByteArray?
}
