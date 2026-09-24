package com.jtech.zemer.utils

import android.content.Context
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.utils.updater.NightlyUpdates
import com.jtech.zemer.utils.updater.UpdateDownloadFailure
import com.jtech.zemer.utils.updater.classifyUpdateDownloadFailure
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object UpdateChecker {
    private const val API_URL = "https://ghtrack.zemer.io/api"
    private const val CHANGELOG_URL = "https://ghtrack.zemer.io/changelog"
    private const val DOWNLOAD_URL = "https://ghtrack.zemer.io/download"
    private const val APK_FILENAME = "zemer-update.apk"
    private const val USER_AGENT = "Zemer-Updater"

    /** Inactivity bound on the download: a connection that sends nothing for this long fails. */
    internal const val DOWNLOAD_IDLE_TIMEOUT_MS = 60_000L

    /**
     * The client for the APK/artifact body download. CIO's default caps a WHOLE request at 15 s,
     * which a ~10 MB download over a slow mobile or filtered link cannot meet ("Request timeout
     * has expired" on the update dialog), so the request timeout is disabled. The bound that
     * remains is per-packet inactivity ([idleTimeoutMs], CIO's `socketTimeout`, infinite by
     * default): a slow transfer that keeps delivering bytes runs to completion, while a server
     * that accepts the connection and then goes silent fails as a timeout instead of hanging.
     */
    internal fun downloadHttpClient(idleTimeoutMs: Long = DOWNLOAD_IDLE_TIMEOUT_MS): HttpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 0
            endpoint.socketTimeout = idleTimeoutMs
        }
    }

    sealed class UpdateResult {
        data class UpdateAvailable(
            val latestVersion: String,
            val currentVersion: String,
            val notes: String? = null,
            // Tags the result with its channel so the download uses the source this result came
            // from, even if the nightly preference is toggled between check and download.
            val isNightly: Boolean = false,
            // The nightly user's forced way back to stable: the version may equal the installed
            // one (nightlies share the stable versionName), so the dialog labels the channel.
            val isReturnToStable: Boolean = false,
        ) : UpdateResult()
        data class UpToDate(val currentVersion: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    sealed class DownloadState {
        data object Idle : DownloadState()
        /** [progress] is -1 when the total is unknown; byte counts feed the "x of y" label. */
        data class Downloading(
            val progress: Float,
            val downloadedBytes: Long = 0L,
            val totalBytes: Long = -1L,
        ) : DownloadState()
        data class Downloaded(val apkFile: File) : DownloadState()
        /** [message] is the raw detail for logs; the dialog renders [failure]. */
        data class Error(
            val message: String,
            val failure: UpdateDownloadFailure = UpdateDownloadFailure.UNKNOWN,
        ) : DownloadState()
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

    /**
     * The nightly channel reads ONE document, the mirror's `/api` (the current build with its
     * commit, CI run number, version, size, SHA-256 and SHA-pinned download URL). It is the only
     * input to "is there an update"; the download re-reads it and verifies the bytes against it.
     */
    private suspend fun checkForNightlyUpdate(): UpdateResult {
        return try {
            HttpClient().use { httpClient ->
                val response = httpClient.get(NightlyUpdates.API_URL) { header(HttpHeaders.UserAgent, USER_AGENT) }
                // A 503 before the mirror's first ingest, or any other non-2xx, is "could not
                // check" - never "up to date", an empty answer must not look like being current.
                if (!response.status.isSuccess()) {
                    return@use UpdateResult.Error("Nightly channel unavailable: HTTP ${response.status.value}")
                }
                val build = NightlyUpdates.parseBuild(response.bodyAsText())
                    ?: return@use UpdateResult.Error("Invalid API response")
                val currentVersion =
                    NightlyUpdates.currentVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH)
                if (NightlyUpdates.isUpdateAvailable(BuildConfig.COMMIT_HASH, BuildConfig.RUN_NUMBER, build)) {
                    UpdateResult.UpdateAvailable(
                        latestVersion = NightlyUpdates.versionLabel(build),
                        currentVersion = currentVersion,
                        notes = nightlyNotes(httpClient, build),
                        isNightly = true,
                    )
                } else {
                    UpdateResult.UpToDate(currentVersion)
                }
            }
        } catch (e: CancellationException) {
            throw e // a cancelled check is not an error result
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    /**
     * Release notes: every build between the installed one and [build] (the mirror's `/changelog`,
     * newest first), falling back to [build]'s own commit message when the endpoint is absent,
     * fails, or has nothing - a failed notes fetch must never block the update. A build without a
     * baked-in SHA has no gap to ask about.
     */
    private suspend fun nightlyNotes(httpClient: HttpClient, build: NightlyUpdates.NightlyBuild): String? {
        val installedSha = BuildConfig.COMMIT_HASH
        val changelog = if (installedSha.isBlank()) {
            null
        } else {
            try {
                val response = httpClient.get(NightlyUpdates.changelogUrl(installedSha)) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (response.status.isSuccess()) {
                    NightlyUpdates.parseChangelog(response.bodyAsText())
                        ?.let { NightlyUpdates.changelogMarkdown(it, BuildConfig.RUN_NUMBER) }
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return changelog ?: build.commitMessage?.let(NightlyUpdates::commitMessageMarkdown)
    }

    /**
     * The mirror's current build, read with the download client so it shares the idle bound, with
     * the update rule applied AGAIN: the mirror may have moved on since the check (a newer build is
     * still an upgrade), but it must never hand the download a build that is not one.
     */
    private suspend fun resolveNightlyBuild(httpClient: HttpClient): NightlyUpdates.NightlyBuild {
        val response = httpClient.get(NightlyUpdates.API_URL) { header(HttpHeaders.UserAgent, USER_AGENT) }
        if (!response.status.isSuccess()) {
            throw IOException("Nightly channel unavailable: HTTP ${response.status.value}")
        }
        val build = NightlyUpdates.parseBuild(response.bodyAsText())
            ?: throw IOException("Nightly channel returned an invalid build record")
        return NightlyUpdates.requireUpdate(BuildConfig.COMMIT_HASH, BuildConfig.RUN_NUMBER, build)
    }

    private suspend fun checkForStableUpdate(force: Boolean = false): UpdateResult {
        return try {
            // `use` closes the client on every exit path (early return, throw), fixing the leak
            // the old manual close() calls left when an exception was thrown mid-check.
            HttpClient().use { httpClient ->
                val responseText = httpClient.get(API_URL).bodyAsText()

                val json = Json.parseToJsonElement(responseText)
                val latestVersionRaw = json.jsonObject["latestVersion"]?.jsonPrimitive?.content
                    ?: return UpdateResult.Error("Invalid API response")

                // Strip "v" prefix if present (API returns "v4", app version is "4")
                val latestVersion = latestVersionRaw.removePrefix("v").removePrefix("V")
                val currentVersion = BuildConfig.VERSION_NAME

                if (force || isNewerVersion(latestVersion, currentVersion)) {
                    // Fetch changelog notes; a failed fetch must not block the update.
                    val notes = try {
                        val changelogText = httpClient.get(CHANGELOG_URL).bodyAsText()
                        Json.parseToJsonElement(changelogText).jsonObject["notes"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        null
                    }
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
                        isReturnToStable = force,
                    )
                } else {
                    UpdateResult.UpToDate(currentVersion)
                }
            }
        } catch (e: CancellationException) {
            throw e // a cancelled check is not an error result
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    fun downloadUpdate(context: Context, nightly: Boolean = false): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        val apkFile = File(context.cacheDir, APK_FILENAME)
        // Each run streams into its OWN atomically-created, guaranteed-unique part file, never a
        // shared fixed path. So a cancelled download (whose cleanup deletes only its part file) can
        // never unlink the file a concurrent retry is writing, and a failed run never leaves the
        // final apk behind - the finalized apkFile appears only on success. (The cancel-then-retry
        // corruption fix.) The nullable ref lets the finally clean up even if creation threw.
        var partFile: File? = null
        try {
            val part = File.createTempFile(APK_FILENAME, ".part", context.cacheDir).also { partFile = it }
            downloadHttpClient().use { httpClient ->
                // A nightly is downloaded from the SHA-pinned URL the mirror's `/api` names, and
                // the bytes are verified below against that same document's size + SHA-256: the
                // announced build and the installed build are one object. The record is re-read
                // here rather than carried over from the check so it can never be stale, and the
                // check's run-number rule already guarantees the mirror's current build is never
                // older than the installed one.
                val nightlyBuild = if (nightly) resolveNightlyBuild(httpClient) else null
                val digest = if (nightlyBuild != null) MessageDigest.getInstance("SHA-256") else null
                // The block form STREAMS the body. The no-block `execute()` loads the whole body
                // into memory before returning, which made the progress loop run after the real
                // download had already finished - the bar jumped 0 -> 100 with nothing between.
                httpClient
                    .prepareGet(nightlyBuild?.downloadUrl ?: DOWNLOAD_URL)
                    .execute { response ->
                        // The client does not validate status (expectSuccess is default-false), so a
                        // 4xx/5xx would otherwise be written and offered for install as an error
                        // document. Reject it as a network failure instead.
                        if (!response.status.isSuccess()) {
                            throw IOException("Update download failed: HTTP ${response.status.value}")
                        }
                        // Size of the actual body we'll read, taken after redirects. A separate HEAD
                        // is unreliable here: /download redirects through a worker + CDN, so a HEAD can
                        // be answered by a different hop (e.g. a challenge page) whose Content-Length is
                        // not the APK's, which would make the progress bar scale to the wrong total.
                        // If the body is gzip-encoded the header is the compressed size and won't match
                        // the decoded bytes we count, so treat that as unknown. A nightly's total is
                        // the mirror's declared size (the download is content-addressed), exact.
                        val isEncoded = response.headers[HttpHeaders.ContentEncoding]?.isNotBlank() == true
                        val contentLength = nightlyBuild?.size
                            ?: (if (isEncoded) -1L else response.contentLength() ?: -1L)

                        val channel = response.bodyAsChannel()
                        var downloadedBytes = 0L
                        var lastEmittedBytes = 0L

                        part.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (!channel.isClosedForRead) {
                                val bytesRead = channel.readAvailable(buffer)
                                if (bytesRead > 0) {
                                    output.write(buffer, 0, bytesRead)
                                    digest?.update(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    // Emit per ~128 KB, not per 8 KB chunk, so a 10 MB download
                                    // doesn't drive a thousand recompositions.
                                    if (downloadedBytes - lastEmittedBytes >= PROGRESS_EMIT_BYTES) {
                                        lastEmittedBytes = downloadedBytes
                                        emit(downloadingState(downloadedBytes, contentLength))
                                    }
                                }
                            }
                        }
                        emit(downloadingState(downloadedBytes, contentLength))
                        // A nightly that is not byte-for-byte the announced build is never
                        // installed: this throws CorruptUpdateArtifactException, the part file is
                        // dropped in the finally below and apkFile is never touched.
                        if (nightlyBuild != null && digest != null) {
                            NightlyUpdates.verifyDownload(downloadedBytes, NightlyUpdates.hex(digest.digest()), nightlyBuild)
                        }
                    }
            }
            // Finalize into apkFile only after a fully-read (and, for a nightly, verified) body,
            // and only via an atomic move so a failure never leaves a partial apk behind.
            Files.move(part.toPath(), apkFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            emit(DownloadState.Downloaded(apkFile))
        } catch (e: CancellationException) {
            throw e // cleanup runs in finally; propagate the cancel
        } catch (e: Exception) {
            Timber.w(e, "Update download failed")
            emit(DownloadState.Error(e.message ?: "Download failed", classifyUpdateDownloadFailure(e)))
        } finally {
            // Always drop this run's part file (a no-op after a successful stable move). Never
            // touches apkFile, so a cancelled run cannot delete a concurrent retry's result.
            partFile?.delete()
        }
    }.flowOn(Dispatchers.IO)

    private const val PROGRESS_EMIT_BYTES = 128L * 1024

    private fun downloadingState(downloadedBytes: Long, contentLength: Long) = DownloadState.Downloading(
        progress = if (contentLength > 0) {
            (downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
        } else {
            -1f // Indeterminate
        },
        downloadedBytes = downloadedBytes,
        totalBytes = contentLength,
    )

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

}
