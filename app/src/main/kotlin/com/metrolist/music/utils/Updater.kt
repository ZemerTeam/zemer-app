package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject

object Updater {
    private val client = HttpClient()
    private const val PROJECT_ID = "zemer-7f5f1"
    private const val DOC_URL =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/appUpdates/latest"

    var lastCheckTime = -1L
        private set
    private var cachedDownloadUrl: String? = null

    data class UpdateInfo(
        val versionName: String,
        val downloadUrl: String,
    )

    suspend fun getLatestUpdate(): Result<UpdateInfo> =
        runCatching {
            val response = client.get(DOC_URL).bodyAsText()
            val json = JSONObject(response)
            val fields = json.optJSONObject("fields")
                ?: error("Missing fields in update document")

            val versionName =
                fields.optJSONObject("versionName")?.optString("stringValue")
                    ?: error("Missing versionName")

            val universalUrl = fields.optJSONObject("universalUrl")?.optString("stringValue")
            val arm64Url = fields.optJSONObject("arm64Url")?.optString("stringValue")
            val x86Url = fields.optJSONObject("x86Url")?.optString("stringValue")
            val baseUrl = fields.optJSONObject("baseUrl")?.optString("stringValue")

            val architecture = BuildConfig.ARCHITECTURE
            val downloadUrl = when (architecture) {
                "arm64" -> arm64Url ?: universalUrl ?: baseUrl
                "x86" -> x86Url ?: universalUrl ?: baseUrl
                else -> universalUrl ?: baseUrl
            } ?: error("Missing download URL for architecture $architecture")

            lastCheckTime = System.currentTimeMillis()
            cachedDownloadUrl = downloadUrl
            UpdateInfo(versionName = versionName, downloadUrl = downloadUrl)
        }

    fun getCachedDownloadUrl(): String? = cachedDownloadUrl
}
