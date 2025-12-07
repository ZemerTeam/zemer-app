package com.jtech.zemer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.jtech.zemer.BuildConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

object UpdateChecker {
    private const val API_URL = "https://ghtrack.zemer.io/api"
    private const val CHANGELOG_URL = "https://ghtrack.zemer.io/changelog"
    private const val DOWNLOAD_URL = "https://ghtrack.zemer.io/download"
    private const val APK_FILENAME = "zemer-update.apk"

    sealed class UpdateResult {
        data class UpdateAvailable(val latestVersion: String, val currentVersion: String, val notes: String? = null) : UpdateResult()
        data class UpToDate(val currentVersion: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        data class Downloaded(val apkFile: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val httpClient = HttpClient()
            val response = httpClient.get(API_URL)
            val responseText = response.bodyAsText()

            val json = Json.parseToJsonElement(responseText)
            val latestVersionRaw = json.jsonObject["latestVersion"]?.jsonPrimitive?.content
                ?: run {
                    httpClient.close()
                    return@withContext UpdateResult.Error("Invalid API response")
                }

            // Strip "v" prefix if present (API returns "v4", app version is "4")
            val latestVersion = latestVersionRaw.removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME

            if (isNewerVersion(latestVersion, currentVersion)) {
                // Fetch changelog notes
                val notes = try {
                    val changelogResponse = httpClient.get(CHANGELOG_URL)
                    val changelogText = changelogResponse.bodyAsText()
                    val changelogJson = Json.parseToJsonElement(changelogText)
                    changelogJson.jsonObject["notes"]?.jsonPrimitive?.content
                } catch (e: Exception) {
                    null
                }
                httpClient.close()
                UpdateResult.UpdateAvailable(latestVersion, currentVersion, notes)
            } else {
                httpClient.close()
                UpdateResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    fun downloadUpdate(context: Context): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        try {
            val httpClient = HttpClient()
            val response = httpClient.prepareGet(DOWNLOAD_URL).execute()

            val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            val apkFile = File(context.cacheDir, APK_FILENAME)

            // Delete existing file if present
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val channel = response.bodyAsChannel()
            var downloadedBytes = 0L

            apkFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (contentLength > 0) {
                            (downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f // Indeterminate
                        }
                        emit(DownloadState.Downloading(progress))
                    }
                }
            }

            httpClient.close()
            emit(DownloadState.Downloaded(apkFile))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
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
