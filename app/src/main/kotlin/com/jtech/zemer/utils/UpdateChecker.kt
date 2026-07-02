package com.jtech.zemer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.utils.updater.NightlyUpdates
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    private const val NIGHTLY_ZIP_FILENAME = "zemer-nightly.zip"

    sealed class UpdateResult {
        data class UpdateAvailable(
            val latestVersion: String,
            val currentVersion: String,
            val notes: String? = null,
            // Tags the result with its channel so the download uses the source this result came
            // from, even if the nightly preference is toggled between check and download.
            val isNightly: Boolean = false,
        ) : UpdateResult()
        data class UpToDate(val currentVersion: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
        // Nightly-only: the latest nightly is a higher version than this build, i.e. a release is
        // being prepared. Nightly users are told to wait for the stable release instead.
        data class ReleaseComingSoon(val currentVersion: String) : UpdateResult()
    }

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        data class Downloaded(val apkFile: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    suspend fun checkForUpdates(nightly: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        if (nightly) checkForNightlyUpdate() else checkForStableUpdate()
    }

    /**
     * The nightly channel's way back to stable: always offers the current stable release, even
     * when the version comparison says up to date — nightlies share the stable versionName, so a
     * regular check can never offer the stable build a nightly user wants to return to.
     */
    suspend fun forceStableUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        checkForStableUpdate(force = true)
    }

    private suspend fun checkForNightlyUpdate(): UpdateResult {
        val httpClient = HttpClient()
        return try {
            val responseText = httpClient.get(NightlyUpdates.RUNS_URL) {
                // GitHub's API rejects requests without a User-Agent.
                header(HttpHeaders.UserAgent, "Zemer-Updater")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }.bodyAsText()

            val run = NightlyUpdates.parseLatestRun(responseText)
                ?: return UpdateResult.Error("Invalid API response")
            val currentVersion =
                NightlyUpdates.currentVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH)
            if (NightlyUpdates.isUpdateAvailable(BuildConfig.COMMIT_HASH, run.headSha)) {
                // A higher-versioned nightly means a release is being prepared — tell nightly users
                // to wait for stable rather than hand them the release candidate. The version lives
                // in build.gradle.kts at that commit; a failed fetch must not block the update, so
                // fall through to offering the nightly.
                val nightlyVersion = runCatching {
                    val gradle = httpClient.get(NightlyUpdates.buildGradleUrl(run.headSha)) {
                        header(HttpHeaders.UserAgent, "Zemer-Updater")
                    }.bodyAsText()
                    NightlyUpdates.parseBuildVersion(gradle)
                }.getOrNull()

                if (nightlyVersion != null &&
                    NightlyUpdates.isReleaseComingSoon(
                        BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, nightlyVersion,
                    )
                ) {
                    UpdateResult.ReleaseComingSoon(currentVersion)
                } else {
                    UpdateResult.UpdateAvailable(
                        latestVersion = NightlyUpdates.versionLabel(run),
                        currentVersion = currentVersion,
                        notes = run.commitTitle,
                        isNightly = true,
                    )
                }
            } else {
                UpdateResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to check for updates")
        } finally {
            httpClient.close()
        }
    }

    private suspend fun checkForStableUpdate(force: Boolean = false): UpdateResult {
        return try {
            val httpClient = HttpClient()
            val response = httpClient.get(API_URL)
            val responseText = response.bodyAsText()

            val json = Json.parseToJsonElement(responseText)
            val latestVersionRaw = json.jsonObject["latestVersion"]?.jsonPrimitive?.content
                ?: run {
                    httpClient.close()
                    return UpdateResult.Error("Invalid API response")
                }

            // Strip "v" prefix if present (API returns "v4", app version is "4")
            val latestVersion = latestVersionRaw.removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME

            if (force || isNewerVersion(latestVersion, currentVersion)) {
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
                UpdateResult.UpdateAvailable(
                    latestVersion = latestVersion,
                    // A forced download is the nightly user's return path — show which build
                    // they are leaving by labelling the current version with its commit.
                    currentVersion = if (force) {
                        NightlyUpdates.currentVersionLabel(currentVersion, BuildConfig.COMMIT_HASH)
                    } else {
                        currentVersion
                    },
                    notes = notes,
                )
            } else {
                httpClient.close()
                UpdateResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    fun downloadUpdate(context: Context, nightly: Boolean = false): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        try {
            val httpClient = HttpClient()

            val response = httpClient
                .prepareGet(if (nightly) NightlyUpdates.DOWNLOAD_URL else DOWNLOAD_URL)
                .execute()

            // Size of the actual body we'll read, taken after redirects. A separate HEAD
            // is unreliable here: /download redirects through a worker + CDN, so a HEAD can
            // be answered by a different hop (e.g. a challenge page) whose Content-Length is
            // not the APK's, which would make the progress bar scale to the wrong total.
            // If the body is gzip-encoded the header is the compressed size and won't match
            // the decoded bytes we count, so treat that as unknown.
            val isEncoded = response.headers[HttpHeaders.ContentEncoding]?.isNotBlank() == true
            val contentLength = if (isEncoded) -1L else response.contentLength() ?: -1L

            val apkFile = File(context.cacheDir, APK_FILENAME)
            // A nightly arrives as the artifact zip; the APK is extracted from it afterwards.
            val targetFile = if (nightly) File(context.cacheDir, NIGHTLY_ZIP_FILENAME) else apkFile

            // Delete existing files if present
            if (apkFile.exists()) {
                apkFile.delete()
            }
            if (nightly && targetFile.exists()) {
                targetFile.delete()
            }

            val channel = response.bodyAsChannel()
            var downloadedBytes = 0L

            targetFile.outputStream().use { output ->
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
            if (nightly) {
                NightlyUpdates.extractApk(targetFile, apkFile)
                targetFile.delete()
            }
            emit(DownloadState.Downloaded(apkFile))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

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
