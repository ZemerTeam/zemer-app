package com.jtech.zemer.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.jtech.zemer.search.isZemerServerUnreachable
import com.jtech.zemer.tracking.TrackingQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Submits user content reports (inappropriate song/album/artist/playlist) to the content server's
 * `POST /reports`. [subject] carries the item-identifying fields (e.g. "songId"/"songTitle"); the
 * reason, comment and reporter fields are appended here. The server owns status and createdAt.
 *
 * A report the server could not take right now (unreachable, 5xx, 429) is queued on disk and sent
 * later with backoff, also after a restart ([drainPending] at launch). A rejected report (other 4xx)
 * fails to the caller, as before.
 */
@Singleton
class ContentReportRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: ContentServerApi,
    private val auth: FirebaseAuth,
) {
    private val pending = TrackingQueue(File(context.filesDir, PENDING_FILE), maxSize = MAX_PENDING)
    private val pendingLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var drainJob: Job? = null

    suspend fun submitReport(subject: Map<String, Any?>, reason: String, comment: String) {
        val payload = buildReportPayload(
            subject = subject,
            reason = reason,
            comment = comment,
            reporterUid = auth.currentUser?.uid ?: "anon",
        )
        try {
            api.submitReport(payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isRetryableReportFailure()) throw e
            Timber.w(e, "ContentReportRepository: server unavailable, report queued")
            pendingLock.withLock { pending.append(payload.toString()) }
            drainPending()
        }
    }

    /** Sends queued reports oldest first, backing off while the server stays unavailable. */
    fun drainPending() {
        if (drainJob?.isActive == true) return
        drainJob = scope.launch {
            var attempt = 0
            while (true) {
                val line = pendingLock.withLock { pending.peekBatch(1).firstOrNull() } ?: return@launch
                val failure = runCatching {
                    api.submitReport(Json.parseToJsonElement(line) as JsonObject)
                }.exceptionOrNull()
                if (failure == null || !failure.isRetryableReportFailure()) {
                    // Sent, or rejected for good (a bad line can never succeed): drop it either way.
                    if (failure != null) Timber.w(failure, "ContentReportRepository: queued report rejected")
                    pendingLock.withLock { pending.removeBatch(listOf(line)) }
                    attempt = 0
                } else {
                    delay(retryDelayMs(writeRetryDelayMs(attempt++), failure))
                }
            }
        }
    }

    companion object {
        private const val PENDING_FILE = "pending_reports.jsonl"
        private const val MAX_PENDING = 50

        /**
         * Pure payload assembly, separated from the network call so it is unit-testable. The reserved
         * fields are written AFTER the subject is merged in, so a stray subject key can never clobber a
         * repository-controlled field (reason/comment/reporterUid).
         */
        fun buildReportPayload(
            subject: Map<String, Any?>,
            reason: String,
            comment: String,
            reporterUid: String,
        ): JsonObject {
            val payload = LinkedHashMap<String, Any?>(subject)
            payload["reason"] = reason
            payload["comment"] = comment
            payload["reporterUid"] = reporterUid
            return JsonObject(payload.mapValues { (_, value) -> value.toJson() })
        }

        private fun Any?.toJson() = when (this) {
            null -> JsonNull
            is String -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            else -> JsonPrimitive(toString())
        }
    }
}

/**
 * Worth retrying later: the server was unreachable, or answered 429 / 5xx. Any other status is a
 * rejection of this report, and a non-network error (e.g. an unreadable 201 body, so the report may
 * already be stored) is never re-sent.
 */
internal fun Throwable.isRetryableReportFailure(): Boolean = when (this) {
    is ContentServerException -> status == 429 || status >= 500
    else -> isZemerServerUnreachable()
}
