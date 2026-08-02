package com.jtech.zemer.statuses

import org.json.JSONArray
import org.json.JSONObject

/**
 * One saved-to-device status: the local gallery copy plus the metadata the Status library needs to
 * list, group, filter and re-open it WITHOUT the live feed. Persisted as JSON in [StatusDownloadsStore]
 * (DataStore, no Room migration). [mediaUri] is the MediaStore `content://` uri of the saved file; for a
 * text status that file is the rendered PNG, while [kind] stays `"text"` so the chip filter and badge
 * still classify it correctly (and [textBody] keeps the original words for a future selectable view).
 */
data class StatusDownload(
    val id: String,
    val kind: String,               // "video" | "image" | "text" (the ORIGINAL kind, even for text->png)
    val creatorId: String,
    val creatorName: String,
    val creatorAvatar: String?,     // avatar path/url, so the saved viewer's header matches the live one
    val postedAt: String,           // ISO-8601, the status's posted time (drives the filename + date sort)
    val caption: String?,
    val textBody: String?,          // original body for a text status (kept for a future selectable view)
    val mediaUri: String,           // MediaStore content:// uri of the saved gallery file
    val savedAt: Long,              // epoch millis when saved (drives the "recently saved" sort)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind)
        put("creatorId", creatorId)
        put("creatorName", creatorName)
        put("creatorAvatar", creatorAvatar)
        put("postedAt", postedAt)
        put("caption", caption)
        put("textBody", textBody)
        put("mediaUri", mediaUri)
        put("savedAt", savedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): StatusDownload = StatusDownload(
            id = o.getString("id"),
            kind = o.getString("kind"),
            creatorId = o.optStringOrNull("creatorId") ?: "",
            creatorName = o.optStringOrNull("creatorName") ?: "",
            creatorAvatar = o.optStringOrNull("creatorAvatar"),
            postedAt = o.optStringOrNull("postedAt") ?: "",
            caption = o.optStringOrNull("caption"),
            textBody = o.optStringOrNull("textBody"),
            mediaUri = o.getString("mediaUri"),
            savedAt = o.optLong("savedAt", 0L),
        )

        /** Serialize the whole set for one DataStore write (newest-first is imposed by the store). */
        fun encodeList(items: List<StatusDownload>): String =
            JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

        /** Parse the stored array; a malformed blob yields an empty list (fail-soft, never crashes). */
        fun decodeList(raw: String?): List<StatusDownload> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}
