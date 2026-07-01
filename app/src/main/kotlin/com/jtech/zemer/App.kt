package com.jtech.zemer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import com.jtech.zemer.constants.*
import com.jtech.zemer.di.ApplicationScope
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.extensions.toInetSocketAddress
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.CrashReportingTree
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.IsraeliArtistRegistry
import com.jtech.zemer.utils.SyncUtils
import com.zemer.cipher.ZemerCipher
import timber.log.Timber
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.YTPlayerUtils
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.utils.WhitelistFetcher
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.utils.parseCookieString
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var syncUtils: SyncUtils

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging. The Crashlytics tree runs in all builds:
        // every log becomes a crash-report breadcrumb, ERROR throwables become
        // non-fatal issues.
        Timber.plant(
            CrashReportingTree(
                logBreadcrumb = { FirebaseCrashlytics.getInstance().log(it) },
                recordNonFatal = { FirebaseCrashlytics.getInstance().recordException(it) },
            )
        )
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Hidden-API exemptions for the Shizuku installer are applied lazily on first use
        // (AppInstaller.ensureHiddenApiBypass) so non-Shizuku users don't pay for it at startup.

        // Initialize cipher library for WEB_REMIX streaming
        ZemerCipher.initialize(
            context = this,
            proxy = YouTube.proxy,
            debugLogging = BuildConfig.DEBUG
        )

        // Warm the cipher + PoToken/BotGuard WebViews off the first-play critical path so the first
        // stream resolves fast. Best-effort and delayed so it never competes with app startup. The
        // cipher warm-up needs no session, so it starts immediately; only the PoToken warm-up waits
        // for visitorData (it's a no-op without one). Each half swallows its own failures.
        applicationScope.launch(Dispatchers.IO) {
            delay(2500)
            YTPlayerUtils.prewarmCipher()
            var waitedMs = 0
            while (YouTube.visitorData == null && waitedMs < 12_000) {
                delay(500)
                waitedMs += 500
            }
            YTPlayerUtils.prewarmPoToken()
        }

        // Load the persisted id-block overrides immediately (cached like the whitelist) so the blocklist
        // is active at startup / offline, before the first whitelist sync of the session refreshes it.
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val persisted = BlockedIdsCache.parse(dataStore.get(BlockedContentIdsKey, ""))
                if (persisted.isNotEmpty()) BlockedIdsCache.updateAll(persisted)
            }
        }

        // تهيئة إعدادات التطبيق عند الإقلاع
        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
            checkForUpdatesOnStartup()
            // Pre-load IsraeliArtistRegistry in background for faster HomeViewModel init
            launch(Dispatchers.IO) {
                IsraeliArtistRegistry.ensureLoaded()
                Timber.d("App: IsraeliArtistRegistry pre-loaded")
            }
            // Removed auto-fetch of anonymous token; user must trigger login manually.
        }
    }

    private suspend fun checkForUpdatesOnStartup() {
        val settings = dataStore.data.first()
        if (settings[CheckForUpdatesKey] != true) return

        when (val result = UpdateChecker.checkForUpdates()) {
            is UpdateChecker.UpdateResult.UpdateAvailable -> {
                pendingUpdateVersion = result.latestVersion
                pendingUpdateNotes = result.notes
            }
            else -> { /* No action needed */ }
        }
    }

    private suspend fun fetchAnonymousTokenOnStartup() {
        fun sanitizeCookie(raw: String?): String? {
            val trimmed = raw?.trim() ?: return null
            return if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))
            ) {
                trimmed.drop(1).dropLast(1)
            } else {
                trimmed
            }
        }

        try {
            val httpClient = HttpClient()
            val responseText = httpClient.get(
                "https://mc.alltech.dev/credentials"
            ).bodyAsText()

            val json = kotlinx.serialization.json.Json.parseToJsonElement(responseText)
            val visitorData = json.jsonObject["visitorData"]?.jsonPrimitive?.content
                ?.let { android.net.Uri.decode(it) }
            val clientVersion = json.jsonObject["clientVersion"]?.jsonPrimitive?.content
            val timestamp = json.jsonObject["timestamp"]?.jsonPrimitive?.content?.toLongOrNull()
            val expiresAt = json.jsonObject["expiresAt"]?.jsonPrimitive?.content?.toLongOrNull()
            val cookie = sanitizeCookie(
                json.jsonObject["cookie"]?.jsonPrimitive?.content
                    ?: json.jsonObject["innerTubeCookie"]?.jsonPrimitive?.content
            )
            val dataSyncId = json.jsonObject["dataSyncId"]?.jsonPrimitive?.content
            val accountName = json.jsonObject["accountName"]?.jsonPrimitive?.content
            val accountEmail = json.jsonObject["accountEmail"]?.jsonPrimitive?.content
            val accountChannelHandle = json.jsonObject["accountChannelHandle"]?.jsonPrimitive?.content

            if (!visitorData.isNullOrEmpty()) {
                // Validate token format
                val isValidToken = visitorData.startsWith("Cg") && visitorData.length > 20

                if (isValidToken) {
                    dataStore.edit { prefs ->
                        prefs[VisitorDataKey] = visitorData
                        cookie
                            ?.takeIf { parseCookieString(it).containsKey("SAPISID") }
                            ?.let { prefs[InnerTubeCookieKey] = it }
                        // Anonymous login must not set dataSyncId (onBehalfOfUser breaks playback).
                        prefs[DataSyncIdKey] = ""
                        accountName?.let { prefs[AccountNameKey] = it }
                        accountEmail?.let { prefs[AccountEmailKey] = it }
                        accountChannelHandle?.let { prefs[AccountChannelHandleKey] = it }
                    }
                    cookie
                        ?.takeIf { parseCookieString(it).containsKey("SAPISID") }
                        ?.let { YouTube.cookie = it }
                    YouTube.dataSyncId = null
                    YouTube.visitorData = visitorData
                    val expiresIn = if (expiresAt != null) {
                        val minutesLeft = (expiresAt - (timestamp ?: System.currentTimeMillis())) / 60000
                        "$minutesLeft minutes"
                    } else {
                        "~24 hours"
                    }
                    android.util.Log.i("AnonymousToken", "✓ Token fetched successfully")
                    android.util.Log.i("AnonymousToken", "  Data: ${visitorData.take(20)}...")
                    android.util.Log.i("AnonymousToken", "  Version: $clientVersion")
                    android.util.Log.i("AnonymousToken", "  Expires in: $expiresIn")
                } else {
                    android.util.Log.w("AnonymousToken", "✗ Invalid token format: $visitorData")
                }
            } else {
                android.util.Log.w("AnonymousToken", "✗ No visitorData in response")
            }
            httpClient.close()
        } catch (e: Exception) {
            android.util.Log.w("AnonymousToken", "✗ Failed to fetch token: ${e.message}", e)
        }
    }

    private suspend fun initializeSettings() {
        val settings = dataStore.data.first()
        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")

        // IMPORTANT: Initialize YouTube authentication data FIRST before anything else
        YouTube.cookie = settings[InnerTubeCookieKey]
        YouTube.visitorData = settings[VisitorDataKey]?.takeIf { it != "null" }
            ?.let { android.net.Uri.decode(it) }
        YouTube.dataSyncId = settings[DataSyncIdKey]?.takeIf { it.isNotBlank() }?.let {
            it.takeIf { !it.contains("||") }
                ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                ?: it.substringAfter("||")
        }

        // Ensure floating mini player defaults to ON if unset
        if (!settings.contains(FloatingMiniPlayerKey)) {
            dataStore.edit { it[FloatingMiniPlayerKey] = true }
        }
        // Force content filters to always be enabled - cannot be disabled
        if (settings[EnableContentFiltersKey] != true) {
            dataStore.edit { it[EnableContentFiltersKey] = true }
        }
        if (!settings.contains(AllowFemaleSingersKey)) {
            dataStore.edit { it[AllowFemaleSingersKey] = false }
        }
        if (!settings.contains(AllowChasidishKey)) {
            dataStore.edit { it[AllowChasidishKey] = false }
        }
        // Auto-enable update checks on fresh install
        if (!settings.contains(CheckForUpdatesKey)) {
            dataStore.edit { it[CheckForUpdatesKey] = true }
        }

        YouTube.locale = YouTubeLocale(
            gl = settings[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = settings[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )

        if (settings[ProxyEnabledKey] == true) {
            val username = settings[ProxyUsernameKey].orEmpty()
            val password = settings[ProxyPasswordKey].orEmpty()
            val type = settings[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP)

            if (username.isNotEmpty() || password.isNotEmpty()) {
                if (type == Proxy.Type.HTTP) {
                    YouTube.proxyAuth = Credentials.basic(username, password)
                } else {
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(username, password.toCharArray())
                    })
                }
            }
            try {
                settings[ProxyUrlKey]?.let {
                    YouTube.proxy = Proxy(type, it.toInetSocketAddress())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@App, getString(R.string.proxy_url_parse_failed), Toast.LENGTH_SHORT).show()
                }
                reportException(e)
            }
        }

        YouTube.useLoginForBrowse = settings[UseLoginForBrowse] ?: true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "updates",
                getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.update_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun observeSettingsChanges() {
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData?.takeIf { it != "null" }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { prefs ->
                    ContentFilterConfig(
                        filtersEnabled = prefs[EnableContentFiltersKey] ?: true,
                        allowFemaleSingers = prefs[AllowFemaleSingersKey] ?: false,
                        blockVideos = prefs[BlockVideosKey] ?: false,
                    )
                }
                .distinctUntilChanged()
                .collect { filters ->
                    ContentFilterState.current = filters
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { prefs ->
                    ContentFilterConfig(
                        filtersEnabled = prefs[EnableContentFiltersKey] ?: true,
                        allowFemaleSingers = prefs[AllowFemaleSingersKey] ?: false,
                        blockVideos = prefs[BlockVideosKey] ?: false,
                    )
                }
                .distinctUntilChanged()
                .collect { filters ->
                    ContentFilterState.current = filters
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        forgetAccount(this@App)
                    }
                }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheSize = dataStore.get(MaxImageCacheSizeKey, 256).coerceIn(0, 512)
        val okHttpClient = OkHttpClient.Builder()
            // Coil shares one host for many thumbnails (yt3.googleusercontent.com etc.). OkHttp's default
            // is 5 concurrent per host, so a screenful of same-host avatars loads in staggered waves.
            // Raise it so a full grid of avatars fetches in one wave.
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 12 })
            .dns(ResilientDns())
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .build()

        return ImageLoader.Builder(this).apply {
            crossfade(false)
            allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
            if (cacheSize == 0) {
                diskCachePolicy(CachePolicy.DISABLED)
            } else {
                diskCache(
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(cacheSize * 1024 * 1024L)
                        .build()
                )
            }
        }.build()
    }

    companion object {
        // Holds pending update version and notes detected on startup
        var pendingUpdateVersion: String? = null
        var pendingUpdateNotes: String? = null

        fun clearPendingUpdate() {
            pendingUpdateVersion = null
            pendingUpdateNotes = null
        }

        suspend fun forgetAccount(context: Context) {
            // CRITICAL: Clear cookies to allow new account login
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (e: Exception) {
                Log.w("App", "Error clearing cookies during logout: ${e.message}")
            }

            // Clear authentication data from DataStore
            context.dataStore.edit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(VisitorDataKey)
                settings.remove(DataSyncIdKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
            }

            // Clear YouTube object to prevent stale authentication state
            YouTube.cookie = null
            YouTube.visitorData = null
            YouTube.dataSyncId = null

            Log.d("App", "Account forgotten - cookies cleared for new account login")
        }
    }
}
