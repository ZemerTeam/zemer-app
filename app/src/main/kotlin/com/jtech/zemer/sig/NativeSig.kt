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

    /**
     * The package manager and the APK's own signing block named different certificates, or (a release build) not
     * the one the key is bound to. Information only ([com.jtech.zemer.net.EnvSignals]); false without the library.
     */
    fun certMismatch(): Boolean = available && runCatching { nativeCertMismatch() }.getOrDefault(false)

    @JvmStatic
    private external fun nativeCertMismatch(): Boolean

    /**
     * The signer's own code differs in memory from the APK it was loaded from (hooked or patched). Information only
     * ([com.jtech.zemer.net.EnvSignals]); false without the library or when it cannot be checked.
     */
    fun codePatched(): Boolean = available && runCatching { nativeCodePatched() == 1 }.getOrDefault(false)

    @JvmStatic
    private external fun nativeCodePatched(): Int

    @JvmStatic
    external fun hmac(message: ByteArray): ByteArray?
}
