package com.jtech.zemer.sync

import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Submits user content reports (inappropriate song/album/artist/playlist) to the content server's
 * `POST /reports`. [subject] carries the item-identifying fields (e.g. "songId"/"songTitle"); the
 * reason, comment and reporter fields are appended here. The server owns status and createdAt.
 */
@Singleton
class ContentReportRepository @Inject constructor(
    private val api: ContentServerApi,
    private val auth: FirebaseAuth,
) {
    suspend fun submitReport(subject: Map<String, Any?>, reason: String, comment: String) {
        api.submitReport(
            buildReportPayload(
                subject = subject,
                reason = reason,
                comment = comment,
                reporterUid = auth.currentUser?.uid ?: "anon",
            )
        )
    }

    companion object {
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
