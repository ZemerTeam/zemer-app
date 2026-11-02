package com.jtech.zemer.sig

import android.content.Context
import com.jtech.zemer.utils.ZemerContentClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Gives this install its own signing key ([ZemerSig.install]): the stored one when it was issued for this build,
 * else one registration with the content server, at most once per app start. Until then, and whenever any step
 * fails (no network, 403 / 429 / 503, an unreadable store), requests keep the version key. Never throws.
 *
 * Self-heals a retired key ([onRefused]): a request signed with the install key and refused `update_required`
 * drops the key (requests go back to the version key at once) and deletes the stored copy, then registers again at
 * most once per [RETRY_MS]. A refusal of the version key itself is a real retirement and is left as it is.
 */
object InstallRegistration {
    const val KEY_REFUSED = "update_required"
    private const val RETRY_MS = 60L * 60 * 1000

    @Volatile
    private var attempted = false

    @Volatile
    private var appContext: Context? = null
    private var lastRetryAt: Long? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun ensure(context: Context) {
        if (ZemerSig.signer == null || attempted) return // a build without a version key cannot register
        attempted = true
        appContext = context.applicationContext
        val store = InstallKeyStore(context)
        store.load(ZemerSig.keyId)?.let {
            ZemerSig.install = it
            return
        }
        store.clear() // missing, unreadable or issued for another build
        register(store)
    }

    private suspend fun register(store: InstallKeyStore) {
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

    /** A Zemer server refused a request with [error]; [sentSignature] is the `X-Zemer-Sig` that request carried. */
    fun onRefused(sentSignature: String?, error: String, now: Long = System.currentTimeMillis()) {
        val install = ZemerSig.install ?: return
        if (!dropsInstallKey(install.id, sentSignature, error)) return
        ZemerSig.install = null
        Timber.w("InstallRegistration: install key refused; back on the version key")
        val context = appContext ?: return
        val retry = synchronized(this) { retryAllowed(lastRetryAt, now).also { if (it) lastRetryAt = now } }
        scope.launch {
            val store = InstallKeyStore(context)
            store.clear() // never loaded again, by this run or the next start
            if (retry) register(store)
        }
    }

    /** The first re-registration of a run always goes; later ones at most once per [RETRY_MS]. */
    internal fun retryAllowed(lastRetryAt: Long?, now: Long): Boolean = lastRetryAt == null || now - lastRetryAt >= RETRY_MS

    /** The refused request was signed with the install key [installId] and refused as a retired key. */
    internal fun dropsInstallKey(installId: String, sentSignature: String?, error: String): Boolean =
        error == KEY_REFUSED && sentKeyId(sentSignature) == installId

    /** The `k=` of an `X-Zemer-Sig` value (version 1 or 2), else null. */
    internal fun sentKeyId(signature: String?): String? =
        signature?.split(';')?.firstOrNull { it.startsWith("k=") }?.substring(2)?.takeIf { it.isNotEmpty() }
}
