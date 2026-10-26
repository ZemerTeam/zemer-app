package com.jtech.zemer.pass

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.constants.ZemerPassExpiresAtKey
import com.jtech.zemer.constants.ZemerPassKey
import com.jtech.zemer.constants.ZemerPassKeyIdKey
import com.jtech.zemer.constants.ZemerPassRetryAfterKey
import com.jtech.zemer.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Obtains and keeps the genuine-app pass ([ZemerPass]): attest a fresh hardware key once, then renew the 24 h
 * pass by signing a server nonce with that key (no new attestation), re-attesting only when a renewal is
 * refused. Every failure is non-fatal: the app simply sends no pass. A refusal (emulator / software-only
 * attestation, a non-release signature) waits [REFUSED_BACKOFF_MS] before trying again, a network failure
 * [FAILURE_BACKOFF_MS], so a device that cannot get a pass does not retry on every launch.
 */
object PassManager {
    const val REFRESH_MARGIN_MS = 2 * 3_600_000L
    const val FAILURE_BACKOFF_MS = 3_600_000L
    const val REFUSED_BACKOFF_MS = 24 * 3_600_000L
    private const val CHECK_INTERVAL_MS = 3_600_000L

    enum class Step { KEEP, RENEW, ATTEST, WAIT }

    /** What to do now, from the stored state (pure; see PassManagerTest). */
    fun nextStep(now: Long, pass: String?, expiresAt: Long, keyId: String?, retryAfter: Long): Step = when {
        pass != null && expiresAt - now > REFRESH_MARGIN_MS -> Step.KEEP
        now < retryAfter -> Step.WAIT
        keyId != null -> Step.RENEW
        else -> Step.ATTEST
    }

    /** How long to wait after [failure] before the next attempt. */
    fun backoffFor(failure: Throwable): Long =
        if (failure is PassRefusedException && failure.status in 400..499 && failure.status != 429) REFUSED_BACKOFF_MS
        else if (failure is PassRefusedException || failure is java.io.IOException) FAILURE_BACKOFF_MS
        else REFUSED_BACKOFF_MS // the device cannot attest at all (no keystore attestation support)

    private val mutex = Mutex()

    /** Loads the stored pass, then keeps it fresh for the life of the process. Never throws. */
    suspend fun run(context: Context) {
        runCatching {
            val prefs = context.dataStore.data.first()
            ZemerPass.current = prefs.validPass(System.currentTimeMillis())
        }
        while (true) {
            ensureFresh(context)
            delay(CHECK_INTERVAL_MS)
        }
    }

    internal suspend fun ensureFresh(context: Context, client: PassClient = PassClient()) = mutex.withLock {
        val prefs = context.dataStore.data.first()
        val now = System.currentTimeMillis()
        ZemerPass.current = prefs.validPass(now)
        val keyId = prefs[ZemerPassKeyIdKey]
        when (nextStep(now, ZemerPass.current, prefs[ZemerPassExpiresAtKey] ?: 0L, keyId, prefs[ZemerPassRetryAfterKey] ?: 0L)) {
            Step.KEEP, Step.WAIT -> return@withLock
            Step.RENEW -> {
                val renewed = runCatching { renew(client, keyId!!) }
                    .onFailure { if (it is CancellationException) throw it }
                val failure = renewed.exceptionOrNull()
                when {
                    failure == null -> store(context, renewed.getOrThrow())
                    // the server no longer accepts this key (or it is gone): start over with a new one
                    failure is PassRefusedException && failure.status == 403 || failure is NoPassKeyException -> attest(context, client)
                    else -> fail(context, failure, "renew")
                }
            }
            Step.ATTEST -> attest(context, client)
        }
    }

    private suspend fun renew(client: PassClient, keyId: String): PassGrant {
        val nonce = client.challenge()
        val sig = PassKeystore.sign(nonce.bytes) ?: throw NoPassKeyException()
        return client.renew(keyId, nonce, sig)
    }

    private suspend fun attest(context: Context, client: PassClient) {
        runCatching {
            val nonce = client.challenge()
            val chain = PassKeystore.createAttested(nonce.bytes)
            // the chain is public (certificates only); debug builds log it so the server's parser can be checked against real hardware output
            if (BuildConfig.DEBUG) Timber.d("ZemerPass attestation chain (%d certs): %s", chain.size, chain.joinToString(","))
            client.attest(nonce, chain)
        }.onFailure { if (it is CancellationException) throw it }
            .onSuccess { store(context, it) }
            .onFailure { fail(context, it, "attest") }
    }

    private suspend fun store(context: Context, grant: PassGrant) {
        context.dataStore.edit {
            it[ZemerPassKey] = grant.pass
            it[ZemerPassKeyIdKey] = grant.keyId
            it[ZemerPassExpiresAtKey] = grant.expiresAt
            it.remove(ZemerPassRetryAfterKey)
        }
        ZemerPass.current = grant.pass
        Timber.d("ZemerPass: pass granted, expires %d", grant.expiresAt)
    }

    private suspend fun fail(context: Context, failure: Throwable, stage: String) {
        val retryAfter = System.currentTimeMillis() + backoffFor(failure)
        context.dataStore.edit {
            it[ZemerPassRetryAfterKey] = retryAfter
            // a refused attestation leaves no usable key; forget it so the next try attests afresh
            if (stage == "attest") it.remove(ZemerPassKeyIdKey)
        }
        Timber.w(failure, "ZemerPass: %s failed, no pass until a later retry", stage)
    }

    private fun Preferences.validPass(now: Long): String? =
        this[ZemerPassKey]?.takeIf { (this[ZemerPassExpiresAtKey] ?: 0L) > now }

    private class NoPassKeyException : IllegalStateException("no attested key on this device")
}
