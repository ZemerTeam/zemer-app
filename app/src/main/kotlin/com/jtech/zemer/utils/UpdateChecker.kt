package com.jtech.zemer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jtech.zemer.BuildConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object UpdateChecker {
    private const val API_URL = "https://ghtrack.zemer.io/api"
    private const val DOWNLOAD_URL = "https://ghtrack.zemer.io/download"

    sealed class UpdateResult {
        data class UpdateAvailable(val latestVersion: String, val currentVersion: String) : UpdateResult()
        data class UpToDate(val currentVersion: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val httpClient = HttpClient()
            val response = httpClient.get(API_URL)
            val responseText = response.bodyAsText()
            httpClient.close()

            val json = Json.parseToJsonElement(responseText)
            val latestVersionRaw = json.jsonObject["latestVersion"]?.jsonPrimitive?.content
                ?: return@withContext UpdateResult.Error("Invalid API response")

            // Strip "v" prefix if present (API returns "v4", app version is "4")
            val latestVersion = latestVersionRaw.removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME

            if (isNewerVersion(latestVersion, currentVersion)) {
                UpdateResult.UpdateAvailable(latestVersion, currentVersion)
            } else {
                UpdateResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val latestPart = latestParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            // If parsing fails, try simple string comparison
            return latest != current && latest > current
        }
    }

    fun openDownloadPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
