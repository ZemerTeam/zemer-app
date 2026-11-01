package com.jtech.zemer.sig

import android.content.Context

/**
 * The native request signer (`libzemersig`, built only when the build has a signing key). The key stays in the
 * library: it returns only the MAC.
 */
internal object NativeSig {
    val available: Boolean = runCatching { System.loadLibrary("zemersig") }.isSuccess

    /**
     * Reads the app's signing certificate once, which a release key is bound to (the library reads it itself from
     * the package manager). Call before the first request; nothing is thrown.
     */
    fun bindTo(context: Context) {
        if (available) runCatching { bind(context.applicationContext) }
    }

    @JvmStatic
    private external fun bind(context: Context)

    @JvmStatic
    external fun hmac(message: ByteArray): ByteArray?
}
