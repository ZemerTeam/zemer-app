package com.jtech.zemer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.utils.WhitelistFetcher
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.utils.parseCookieString
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
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

        // تهيئة إعدادات التطبيق عند الإقلاع
        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
            fetchAnonymousTokenOnStartup()
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
                "https://yt-token-dispenser.usheraweiss.workers.dev/api/token"
            ).bodyAsText()

            val json = kotlinx.serialization.json.Json.parseToJsonElement(responseText)
            val visitorData = json.jsonObject["visitorData"]?.jsonPrimitive?.content
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
                        dataSyncId?.let { prefs[DataSyncIdKey] = it.substringBefore("||") }
                        accountName?.let { prefs[AccountNameKey] = it }
                        accountEmail?.let { prefs[AccountEmailKey] = it }
                        accountChannelHandle?.let { prefs[AccountChannelHandleKey] = it }
                    }
                    cookie
                        ?.takeIf { parseCookieString(it).containsKey("SAPISID") }
                        ?.let { YouTube.cookie = it }
                    dataSyncId?.let { YouTube.dataSyncId = it.substringBefore("||") }
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

        // Ensure floating mini player defaults to ON if unset
        if (!settings.contains(FloatingMiniPlayerKey)) {
            dataStore.edit { it[FloatingMiniPlayerKey] = true }
        }
        if (!settings.contains(EnableContentFiltersKey)) {
            dataStore.edit { it[EnableContentFiltersKey] = true }
        }
        if (!settings.contains(AllowFemaleSingersKey)) {
            dataStore.edit { it[AllowFemaleSingersKey] = false }
        }
        if (!settings.contains(AllowChasidishKey)) {
            dataStore.edit { it[AllowChasidishKey] = false }
        }
        if (!settings.contains(AllowDjKey)) {
            dataStore.edit { it[AllowDjKey] = false }
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
                    Toast.makeText(this@App, "Failed to parse proxy url.", Toast.LENGTH_SHORT).show()
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
                        promoteChasidish = prefs[AllowChasidishKey] ?: false,
                        hideOldStuff = prefs[AllowDjKey] ?: false,
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
                        promoteChasidish = prefs[AllowChasidishKey] ?: false,
                        hideOldStuff = prefs[AllowDjKey] ?: false,
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
        suspend fun forgetAccount(context: Context) {
            context.dataStore.edit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(VisitorDataKey)
                settings.remove(DataSyncIdKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
            }
        }
    }
}
