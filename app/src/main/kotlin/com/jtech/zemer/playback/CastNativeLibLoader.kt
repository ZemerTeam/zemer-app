package com.jtech.zemer.playback

import android.content.Context
import android.os.Build
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** State of the on-demand FCast native library. */
sealed interface CastLibState {
    data object Idle : CastLibState          // not present, not requested

    /** [progress] is the 0..1 downloaded fraction, or null while the total size is still unknown. */
    data class Downloading(val progress: Float? = null) : CastLibState
    data object Ready : CastLibState          // present + verified + override applied; the SDK can load

    /** A localizable failure reason (the UI maps it to a string resource — never a raw English string). */
    data class Failed(val reason: Reason) : CastLibState {
        enum class Reason { UNSUPPORTED_DEVICE, DOWNLOAD_FAILED }
    }
}

/**
 * Pure metadata for the on-demand FCast native lib — kept Android-free so it is unit-testable.
 * The libs are hosted (CI-built + checksummed) at ZemerTeam/zemer-cast, not bundled in the APK.
 */
object CastNativeLib {
    const val SDK_VERSION = "0.4.0"
    const val LIB_FILE_NAME = "libfcast_sender_sdk.so"

    /** uniffi reads this system property and hands the value straight to JNA's `Native.load`, so we
     *  point it at the absolute path of the downloaded lib. Must be set before the SDK is first used. */
    const val OVERRIDE_PROPERTY = "uniffi.component.fcast_sender_sdk.libraryOverride"

    private const val BASE = "https://github.com/ZemerTeam/zemer-cast/releases/download/sdk-$SDK_VERSION"

    data class AbiLib(val abi: String, val url: String, val sha256: String)

    /** Pinned to the zemer-cast `sdk-0.4.0` release (CI-extracted byte-for-byte from the upstream aar). */
    val ABIS = listOf(
        AbiLib(
            "arm64-v8a",
            "$BASE/libfcast_sender_sdk-arm64-v8a.so",
            "ef198a26239e4fb1dadd2ed76b85f92601b78d829c9556a595976a9a5b40427e",
        ),
        AbiLib(
            "armeabi-v7a",
            "$BASE/libfcast_sender_sdk-armeabi-v7a.so",
            "99cd81196a1fc0783e79c6868404896b290e65dd4da2907cf904e1832259ec63",
        ),
    )

    /** The lib for the device's most-preferred supported ABI, or null if none of ours match. */
    fun pickAbi(supportedAbis: List<String>): AbiLib? =
        supportedAbis.firstNotNullOfOrNull { abi -> ABIS.firstOrNull { it.abi == abi } }

    /**
     * Whether an already-present cached lib can be trusted as-is (skip re-download): it exists AND its
     * recorded sha matches the sha pinned for the device's ABI. A version bump (different expected sha),
     * a missing marker, or a truncated/partial copy (marker absent because it is written only after the
     * file is fully in place) all fail this check and force a fresh, verified download.
     */
    fun cacheIsValid(libExists: Boolean, storedSha: String?, expectedSha: String?): Boolean =
        libExists && expectedSha != null && storedSha != null && storedSha.equals(expectedSha, ignoreCase = true)

    /**
     * Downloaded fraction for the progress bar: null when the server sent no usable Content-Length
     * (→ indeterminate bar), otherwise clamped to 0..1 so a lying length can never push past 100%.
     */
    fun downloadProgress(bytesReceived: Long, totalBytes: Long): Float? =
        if (totalBytes <= 0L) null else (bytesReceived.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/**
 * Downloads + verifies the FCast sender-SDK native lib on demand (it is not bundled in the APK, saving
 * ~5.3 MB), then points uniffi at the downloaded file via [CastNativeLib.OVERRIDE_PROPERTY] so JNA loads
 * it directly. No cast SDK type may be touched until [ensure] reports `Ready` — see [FCastDiscoveryHandler]
 * (lazy `castContext`) and [MusicService.startDiscovery].
 */
class CastNativeLibLoader(context: Context) {
    private val appContext = context.applicationContext
    private val libDir = File(appContext.filesDir, "castlib")
    private val libFile = File(libDir, CastNativeLib.LIB_FILE_NAME)
    // Records the sha256 of the lib actually on disk; written only after a verified download lands, and
    // compared against the sha pinned for this device's ABI so a stale (post-upgrade) or partial copy is
    // never trusted. Keyed by content, not filename, so the cache file name can stay version-less.
    private val markerFile = File(libDir, "${CastNativeLib.LIB_FILE_NAME}.sha")
    private val expectedSha: String? = CastNativeLib.pickAbi(Build.SUPPORTED_ABIS.toList())?.sha256

    private fun storedSha(): String? = runCatching { markerFile.readText().trim() }.getOrNull()
    private fun cachedLibValid(): Boolean = CastNativeLib.cacheIsValid(libFile.exists(), storedSha(), expectedSha)

    private val _state = MutableStateFlow<CastLibState>(
        if (cachedLibValid()) CastLibState.Ready else CastLibState.Idle,
    )
    val state: StateFlow<CastLibState> = _state.asStateFlow()

    init {
        // A copy downloaded in a previous run is trusted only if its recorded sha still matches the sha
        // pinned for this ABI (guards against a stale lib after an SDK_VERSION bump or a truncated copy).
        // Point uniffi at it now — a system-property write, no native code — so the SDK can load without a fetch.
        if (cachedLibValid()) applyOverride()
    }

    val isReady: Boolean get() = _state.value is CastLibState.Ready

    private fun applyOverride() {
        System.setProperty(CastNativeLib.OVERRIDE_PROPERTY, libFile.absolutePath)
    }

    /**
     * Ensures the lib is present + verified and the override applied. Blocking network I/O — call off the
     * main thread. Returns true once the SDK can be loaded. Safe to call repeatedly (no-op when ready).
     *
     * `@Synchronized`: the call-site guard in [MusicService.downloadCastLib] (`isReady || Downloading`)
     * is not atomic with the `_state = Downloading` flip below, so two fast download/retry taps can both
     * pass it and enter here. Serialising means the second caller blocks until the first finishes, then
     * sees `cachedLibValid()` and returns immediately — never a second concurrent write into the shared
     * `*.download` temp file (which could leave a corrupt [libFile] validated by a correct marker).
     */
    @Synchronized
    fun ensure(): Boolean {
        if (cachedLibValid()) {
            applyOverride()
            _state.value = CastLibState.Ready
            return true
        }
        // Missing, stale (version bump), or partial/unverifiable copy — drop it and re-fetch.
        libFile.delete()
        markerFile.delete()
        val abiLib = CastNativeLib.pickAbi(Build.SUPPORTED_ABIS.toList()) ?: run {
            _state.value = CastLibState.Failed(CastLibState.Failed.Reason.UNSUPPORTED_DEVICE)
            return false
        }
        _state.value = CastLibState.Downloading()
        return try {
            libDir.mkdirs()
            val tmp = File(libDir, "${CastNativeLib.LIB_FILE_NAME}.download")
            val sha = downloadTo(abiLib.url, tmp) { progress ->
                _state.value = CastLibState.Downloading(progress)
            }
            if (!sha.equals(abiLib.sha256, ignoreCase = true)) {
                tmp.delete()
                reportException(IllegalStateException("FCast lib checksum mismatch (got $sha, want ${abiLib.sha256})"))
                _state.value = CastLibState.Failed(CastLibState.Failed.Reason.DOWNLOAD_FAILED)
                false
            } else {
                if (!tmp.renameTo(libFile)) {
                    tmp.copyTo(libFile, overwrite = true)
                    tmp.delete()
                }
                // Write the marker only AFTER the lib is fully in place, so a crash mid-copy leaves no
                // marker and the partial file is re-downloaded next launch instead of being trusted.
                markerFile.writeText(abiLib.sha256)
                applyOverride()
                _state.value = CastLibState.Ready
                true
            }
        } catch (e: Exception) {
            reportException(e)
            _state.value = CastLibState.Failed(CastLibState.Failed.Reason.DOWNLOAD_FAILED)
            false
        }
    }

    /**
     * Streams [url] to [dest], returning the lowercase-hex SHA-256 of the bytes received. Reports the
     * downloaded fraction (null while unknown) via [onProgress] as chunks arrive.
     */
    private fun downloadTo(url: String, dest: File, onProgress: (Float?) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true // GitHub release assets 302 to githubusercontent (https->https)
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode} for $url")
            }
            val totalBytes = conn.contentLengthLong
            var received = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        received += n
                        onProgress(CastNativeLib.downloadProgress(received, totalBytes))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
