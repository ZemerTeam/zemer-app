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
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import timber.log.Timber
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

    init {
        if (BuildConfig.DEBUG) {
            Timber.d("App.init block called - Hilt injection about to happen")
        }
    }

    override fun onCreate() {
        val startTime = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("App.onCreate() started - before super.onCreate() - ${System.currentTimeMillis() - startTime}ms")
        } else {
            Timber.uprootAll()
        }
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.d("super.onCreate() completed - ${System.currentTimeMillis() - startTime}ms")
        }

        // تهيئة إعدادات التطبيق عند الإقلاع
        // TEMPORARILY DISABLED FOR ANR DEBUGGING
        if (BuildConfig.DEBUG) {
            Timber.d("About to launch app initialization coroutine - ${System.currentTimeMillis() - startTime}ms")
        }
        applicationScope.launch {
            if (BuildConfig.DEBUG) {
                Timber.d("App initialization starting in coroutine - ${System.currentTimeMillis() - startTime}ms from process start")
            }
            initializeSettings()
            observeSettingsChanges()
            if (BuildConfig.DEBUG) {
                Timber.d("App initialization complete - ${System.currentTimeMillis() - startTime}ms from process start")
            }
        }

        if (BuildConfig.DEBUG) {
            Timber.d("App.onCreate() completed - ${System.currentTimeMillis() - startTime}ms from process start")
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
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
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
                        Timber.e(e, "[Auth] Could not parse cookie - invalid format or corrupted data - clearing account - thread: ${Thread.currentThread().name}")
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
