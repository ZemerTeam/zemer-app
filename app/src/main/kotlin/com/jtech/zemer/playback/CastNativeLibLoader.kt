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
    data object Downloading : CastLibState
    data object Ready : CastLibState          // present + verified + override applied; the SDK can load
    data class Failed(val reason: String) : CastLibState
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

    private val _state = MutableStateFlow<CastLibState>(
        if (libFile.exists()) CastLibState.Ready else CastLibState.Idle,
    )
    val state: StateFlow<CastLibState> = _state.asStateFlow()

    init {
        // A copy downloaded in a previous run is already trusted (verified on download, app-private
        // storage). Point uniffi at it now — a system-property write, no native code — so the SDK can
        // load on relaunch without a fetch.
        if (libFile.exists()) applyOverride()
    }

    val isReady: Boolean get() = _state.value is CastLibState.Ready

    private fun applyOverride() {
        System.setProperty(CastNativeLib.OVERRIDE_PROPERTY, libFile.absolutePath)
    }

    /**
     * Ensures the lib is present + verified and the override applied. Blocking network I/O — call off the
     * main thread. Returns true once the SDK can be loaded. Safe to call repeatedly (no-op when ready).
     */
    fun ensure(): Boolean {
        if (libFile.exists()) {
            applyOverride()
            _state.value = CastLibState.Ready
            return true
        }
        val abiLib = CastNativeLib.pickAbi(Build.SUPPORTED_ABIS.toList()) ?: run {
            _state.value = CastLibState.Failed("Unsupported CPU (${Build.SUPPORTED_ABIS.joinToString()})")
            return false
        }
        _state.value = CastLibState.Downloading
        return try {
            libDir.mkdirs()
            val tmp = File(libDir, "${CastNativeLib.LIB_FILE_NAME}.download")
            val sha = downloadTo(abiLib.url, tmp)
            if (!sha.equals(abiLib.sha256, ignoreCase = true)) {
                tmp.delete()
                _state.value = CastLibState.Failed("Checksum mismatch")
                false
            } else {
                if (!tmp.renameTo(libFile)) {
                    tmp.copyTo(libFile, overwrite = true)
                    tmp.delete()
                }
                applyOverride()
                _state.value = CastLibState.Ready
                true
            }
        } catch (e: Exception) {
            reportException(e)
            _state.value = CastLibState.Failed(e.message ?: "Download failed")
            false
        }
    }

    /** Streams [url] to [dest], returning the lowercase-hex SHA-256 of the bytes received. */
    private fun downloadTo(url: String, dest: File): String {
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
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
