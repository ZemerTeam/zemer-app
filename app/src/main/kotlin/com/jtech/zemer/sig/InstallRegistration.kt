package com.jtech.zemer.sig

import android.content.Context
import com.jtech.zemer.utils.ZemerContentClient
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Gives this install its own signing key ([ZemerSig.install]): the stored one when it was issued for this build,
 * else one registration with the content server, at most once per app start. Until then, and whenever any step
 * fails (no network, 403 / 429 / 503, an unreadable store), requests keep the version key. Never throws.
 */
object InstallRegistration {
    @Volatile
    private var attempted = false

    suspend fun ensure(context: Context) {
        if (ZemerSig.signer == null || attempted) return // a build without a version key cannot register
        attempted = true
        val store = InstallKeyStore(context)
        store.load(ZemerSig.keyId)?.let {
            ZemerSig.install = it
            return
        }
        store.clear() // missing, unreadable or issued for another build
        try {
            val credential = InstallCredential.parse(ZemerContentClient.registerInstall(), ZemerSig.keyId)
                ?: return Timber.w("InstallRegistration: unusable answer")
            ZemerSig.install = credential
            if (!store.save(credential)) Timber.w("InstallRegistration: could not store the install key")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("InstallRegistration: registration failed (%s); keeping the version key", e.javaClass.simpleName)
        }
    }
}
