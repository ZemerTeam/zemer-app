package com.metrolist.music.utils

import com.metrolist.music.db.entities.ArtistWhitelistEntity
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime

object WhitelistFetcher {
    private val client = HttpClient()
    private const val PROJECT_ID = "zemer-7f5f1"
    private const val BASE_URL =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/artistsWhitelist"
    private const val VERSION_DOC =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/databasenumber/latest"

    var lastFetchTime = -1L
        private set

    suspend fun fetchVersion(): Result<Long> =
        runCatching {
            val response = client.get(VERSION_DOC).bodyAsText()
            val json = JSONObject(response)
            json.optJSONObject("error")?.let { err ->
                val message = err.optString("message", "Unknown Firestore error")
                error("Firestore version fetch failed: $message")
            }
            val fields = json.optJSONObject("fields") ?: error("Missing fields in databasenumber/latest")
            // Prefer updatedAt timestamp if present, otherwise use numeric/string update field
            val updatedAt = fields.optJSONObject("updatedAt")?.optString("timestampValue")
            val updateStr = fields.optJSONObject("update")?.optString("stringValue")
                ?: fields.optJSONObject("update")?.optString("integerValue")

            val value = when {
                updatedAt != null -> Instant.parse(updatedAt).toEpochMilli()
                updateStr != null -> updateStr.toLongOrNull()
                else -> null
            } ?: error("Missing or invalid update value in databasenumber/latest")
            value
        }

    suspend fun fetchWhitelist(): Result<List<ArtistWhitelistEntity>> =
        runCatching {
            val now = LocalDateTime.now()
            val whitelistEntities = mutableListOf<ArtistWhitelistEntity>()
            var pageToken: String? = null

            do {
                val pageParam = pageToken?.let { "&pageToken=$it" } ?: ""
                val url = "$BASE_URL?pageSize=1000$pageParam"
                val response = client.get(url).bodyAsText()
                val json = JSONObject(response)
                json.optJSONObject("error")?.let { err ->
                    val message = err.optString("message", "Unknown Firestore error")
                    error("Firestore whitelist fetch failed: $message")
                }
                val documents = json.optJSONArray("documents") ?: JSONArray()

                for (i in 0 until documents.length()) {
                    val doc = documents.getJSONObject(i)
                    val fields = doc.optJSONObject("fields") ?: continue
                    val artistId = fields.optJSONObject("id")?.optString("stringValue")
                        ?: fields.optJSONObject("artistId")?.optString("stringValue")
                        ?: continue
                    val artistName = fields.optJSONObject("name")?.optString("stringValue")
                        ?: fields.optJSONObject("artistName")?.optString("stringValue")
                        ?: continue
                    val isFemale = fields.optJSONObject("isFemale")?.optBoolean("booleanValue") ?: false
                    val isChasid = fields.optJSONObject("isChasid")?.optBoolean("booleanValue") ?: false
                    val isGenZ = fields.optJSONObject("isGenZ")?.optBoolean("booleanValue") ?: false
                    val updatedAt = fields.optJSONObject("updatedAt")?.optString("timestampValue")
                    val addedAt = now

                    whitelistEntities.add(
                        ArtistWhitelistEntity(
                            artistId = artistId,
                            artistName = artistName,
                            addedAt = addedAt,
                            source = "firestore",
                            lastSyncedAt = now,
                            isFemale = isFemale,
                            isChasid = isChasid,
                            isGenZ = isGenZ
                        )
                    )
                }

                pageToken = json.optString("nextPageToken", null)
                if (pageToken != null && pageToken.isBlank()) pageToken = null
            } while (pageToken != null)

            lastFetchTime = System.currentTimeMillis()
            whitelistEntities
        }
}
