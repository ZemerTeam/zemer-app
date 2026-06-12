package com.jtech.zemer.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Submits user content reports (inappropriate song/album/artist/playlist) to the
 * `artistReports` Firestore collection. [subject] carries the item-identifying fields
 * (e.g. "songId"/"songTitle"); the reason, comment, reporter and timestamp fields are
 * appended here.
 */
@Singleton
class ContentReportRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    suspend fun submitReport(subject: Map<String, Any?>, reason: String, comment: String) {
        val payload = buildReportPayload(
            subject = subject,
            reason = reason,
            comment = comment,
            reporterUid = auth.currentUser?.uid ?: "anon",
            createdAt = FieldValue.serverTimestamp(),
        )
        firestore.collection("artistReports").add(payload).await()
    }

    companion object {
        /**
         * Pure payload assembly, separated from the Firestore call so it is unit-testable. The
         * reserved fields are written AFTER the subject is merged in, so a stray subject key can
         * never clobber a repository-controlled field (reason/comment/status/reporterUid/createdAt).
         */
        fun buildReportPayload(
            subject: Map<String, Any?>,
            reason: String,
            comment: String,
            reporterUid: String,
            createdAt: Any,
        ): HashMap<String, Any?> {
            val payload = HashMap<String, Any?>(subject)
            payload["reason"] = reason
            payload["comment"] = comment
            payload["status"] = "pending"
            payload["reporterUid"] = reporterUid
            payload["createdAt"] = createdAt
            return payload
        }
    }
}
