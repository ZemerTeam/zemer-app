package com.jtech.zemer.tracking

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.telephony.TelephonyManager
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.db.DatabaseDao
import com.jtech.zemer.net.EnvSignals
import com.jtech.zemer.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

/**
 * The batch envelope's `info` object: the device, its state and the app's settings. Collected per upload
 * (network, carrier and free storage change); any field that cannot be read is left out.
 */
internal object DeviceInfo {

    suspend fun collect(context: Context, dao: DatabaseDao?): JsonObject = buildJsonObject {
        put("manufacturer", Build.MANUFACTURER)
        put("brand", Build.BRAND)
        put("model", Build.MODEL)
        put("device", Build.DEVICE)
        put("product", Build.PRODUCT)
        put("hardware", Build.HARDWARE)
        put("fingerprint", Build.FINGERPRINT)
        put("osRelease", Build.VERSION.RELEASE)
        put("securityPatch", Build.VERSION.SECURITY_PATCH)
        put("sdk", Build.VERSION.SDK_INT)
        put("abi", Build.SUPPORTED_ABIS.firstOrNull())
        put("locale", Locale.getDefault().toLanguageTag())
        put("tz", TimeZone.getDefault().id)
        put("versionCode", BuildConfig.VERSION_CODE)
        put("buildType", BuildConfig.BUILD_TYPE)
        runCatching { installer(context) }.getOrNull()?.let { put("installer", it) }
        context.resources.displayMetrics.let { put("screen", "${it.widthPixels}x${it.heightPixels}@${it.densityDpi}") }
        runCatching { network(context) }.getOrNull()?.let { put("network", it) }
        runCatching { context.getSystemService(TelephonyManager::class.java)?.networkOperatorName }
            .getOrNull()?.takeIf { it.isNotBlank() }?.let { put("carrier", it) }
        runCatching {
            ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem
        }.getOrNull()?.let { put("ramMb", it / MIB) }
        runCatching { StatFs(context.filesDir.path).availableBytes }.getOrNull()?.let { put("storageFreeMb", it / MIB) }
        val prefs = try {
            context.dataStore.data.first().asMap()
        } catch (e: IOException) {
            null
        }
        val named = prefs?.mapKeys { (key, _) -> key.name }.orEmpty()
        if (prefs != null) put("settings", settingsSnapshot(named))
        val counts = try {
            dao?.libraryCounts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        counts?.let {
            put("librarySongs", it.librarySongs)
            put("downloads", it.downloads)
            put("playlists", it.playlists)
            put("liked", it.liked)
        }
        context.getSystemService(BatteryManager::class.java)?.let { battery ->
            battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }?.let { put("batteryPct", it) }
            put("charging", if (battery.isCharging) 1 else 0)
        }
        put("playServices", if (hasPackage(context, PLAY_SERVICES)) 1 else 0)
        put("shizuku", if (hasPackage(context, SHIZUKU)) 1 else 0)
        put("rooted", if (SU_PATHS.any { File(it).exists() }) 1 else 0)
        put("envBits", EnvSignals.mask)
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()?.let {
            put("firstInstallAt", it.firstInstallTime)
            put("lastUpdateAt", it.lastUpdateTime)
        }
    }

    private fun hasPackage(context: Context, name: String): Boolean =
        try {
            context.packageManager.getPackageInfo(name, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    private fun installer(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }

    private fun network(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private const val MIB = 1024L * 1024L
    private const val PLAY_SERVICES = "com.google.android.gms"
    private const val SHIZUKU = "moe.shizuku.privileged.api"

    // a passive file check: never runs su (that would raise the Magisk grant prompt)
    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sd/xbin/su",
        "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su", "/debug_ramdisk/su",
    )
}

/** Settings keys never sent: credentials, the account's identity (owner decision) and the other install ids. */
private val EXCLUDED_SETTINGS = setOf(
    "innerTubeCookie", "visitorData", "dataSyncId", "accountEmail", "accountName", "accountChannelHandle",
    "content_filters_restored_email", "trackingDeviceId", "relayDeviceId", "current_device_id", "device_id",
    "content_filter_upload_pending_uid",
)
private val CREDENTIAL_NAME = Regex("(?i)cookie|token|secret|password|sig")
private const val MAX_SETTING_CHARS = 64
private const val MAX_SETTINGS_BYTES = 4_000

/** The legal agreement ([com.jtech.zemer.legal.LegalAgreement]): placed first so a full snapshot never drops it. */
private val FIRST_SETTINGS = setOf("acceptedPrivacyDoc", "acceptedTermsDoc", "acceptedPrivacyAt")

/**
 * The app's settings as a flat JSON object under [maxBytes] encoded: [FIRST_SETTINGS] (never cut by the cap),
 * then booleans, then strings (short ones only, which skips the cached JSON blobs), then numbers, each group by
 * key. Credentials and ids ([EXCLUDED_SETTINGS], [CREDENTIAL_NAME]) and string sets are never included.
 */
internal fun settingsSnapshot(prefs: Map<String, Any?>, maxBytes: Int = MAX_SETTINGS_BYTES): JsonObject {
    val rank = { k: String, v: Any? ->
        when {
            k in FIRST_SETTINGS -> -1
            v is Boolean -> 0
            v is String -> 1
            else -> 2
        }
    }
    val entries = prefs
        .filterKeys { it !in EXCLUDED_SETTINGS && !CREDENTIAL_NAME.containsMatchIn(it) }
        .mapNotNull { (k, v) ->
            when (v) {
                is Boolean, is Int, is Long, is Float, is Double -> k to v
                is String -> if (v.length <= MAX_SETTING_CHARS) k to v else null
                else -> null
            }
        }
        .sortedWith(compareBy({ rank(it.first, it.second) }, { it.first }))
    val out = LinkedHashMap<String, JsonPrimitive>()
    var bytes = 2
    for ((k, v) in entries) {
        val value = when (v) {
            is Boolean -> JsonPrimitive(v)
            is String -> JsonPrimitive(v)
            else -> JsonPrimitive(v as Number)
        }
        val size = "${JsonPrimitive(k)}:$value,".encodeToByteArray().size
        if (bytes + size > maxBytes) break
        out[k] = value
        bytes += size
    }
    return JsonObject(out)
}
