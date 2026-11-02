package com.jtech.zemer.legal

import android.content.Context
import android.util.AtomicFile
import com.jtech.zemer.R
import com.jtech.zemer.utils.ZemerContentClient
import com.jtech.zemer.utils.contentRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * The legal documents the app shows: the last signed copy from content.zemer.io `/legal` (cached in `filesDir`),
 * else the built-in copy (`res/raw/legal_builtin.json`, the same file zemer.io serves). [refresh] runs at app start
 * and on every return to the foreground (throttled); a failed or unsigned read keeps what is there, and a copy is
 * never replaced by an older version.
 */
object LegalRepository {
    private val _docs = MutableStateFlow<LegalDocs?>(null)

    /** Null only until [refresh] has loaded the local copy (a moment after the app starts). */
    val docs: StateFlow<LegalDocs?> = _docs.asStateFlow()

    private var lastFetchAt: Long? = null

    /**
     * Loads the local copy (first call), then reads `/legal` unless it was read in the last [MIN_FETCH_INTERVAL_MS]:
     * called at app start and whenever the app comes to the foreground, so a change reaches a long-running app too.
     */
    suspend fun refresh(context: Context, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val file = AtomicFile(File(context.filesDir, CACHE_FILE))
        if (_docs.value == null) {
            val cached = runCatching { LegalDocs.parse(file.readFully().decodeToString()) }.getOrNull()
            val builtIn = builtIn(context)
            // a later app version may ship a newer built-in copy than the cache
            _docs.value = cached?.takeIf { newerOrSame(it, builtIn) } ?: builtIn
        }
        val due = synchronized(this@LegalRepository) {
            fetchDue(lastFetchAt, now).also { if (it) lastFetchAt = now }
        }
        if (due) fetch(file)
    }

    /** The first read of a run always goes; later ones at most once per [MIN_FETCH_INTERVAL_MS]. */
    internal fun fetchDue(lastFetchAt: Long?, now: Long): Boolean =
        lastFetchAt == null || now - lastFetchAt >= MIN_FETCH_INTERVAL_MS

    private suspend fun fetch(file: AtomicFile) {
        val remote = contentRead("legal") { ZemerContentClient.legal() }.getOrNull() ?: return
        val docs = LegalDocs.parse(remote) ?: return Timber.w("LegalRepository: unusable /legal answer")
        val current = _docs.value
        if (current != null && !newerOrSame(docs, current)) return
        runCatching {
            val out = file.startWrite()
            try {
                out.write(remote.encodeToByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }.onFailure { Timber.w(it, "LegalRepository: could not cache /legal") }
        _docs.value = docs
    }

    /** Never trade a copy for an older one (a stale mirror or cache must not undo an update users agreed to). */
    internal fun newerOrSame(candidate: LegalDocs, current: LegalDocs): Boolean =
        candidate.privacy.version >= current.privacy.version && candidate.terms.version >= current.terms.version

    private fun builtIn(context: Context): LegalDocs =
        context.resources.openRawResource(R.raw.legal_builtin).use { LegalDocs.parse(it.readBytes().decodeToString()) }
            ?: error("built-in legal copy is invalid") // pinned by LegalDocsTest

    private const val CACHE_FILE = "legal.json"
    private const val MIN_FETCH_INTERVAL_MS = 30L * 60 * 1000
}
