@file:Suppress("unused")

package com.jtech.zemer.constants

import androidx.annotation.StringRes
import com.jtech.zemer.R
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val DensityScaleKey = floatPreferencesKey("density_scale_factor")
val CustomDensityScaleKey = floatPreferencesKey("custom_density_scale_value")
val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val BottomNavigationBarEnabledKey = booleanPreferencesKey("bottomNavigationBarEnabled")
val SlimNavBarKey = booleanPreferencesKey("slimNavBar")
val BottomNavigationItemsKey = stringPreferencesKey("bottomNavigationItems")
val RecognizeMusicFabKey = booleanPreferencesKey("recognizeMusicFab")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val SwipeToRemoveSongKey = booleanPreferencesKey("SwipeToRemoveSong")
val UseNewPlayerDesignKey= booleanPreferencesKey("useNewPlayerDesign")
val UseNewMiniPlayerDesignKey = booleanPreferencesKey("useNewMiniPlayerDesign")
val FloatingMiniPlayerKey = booleanPreferencesKey("floatingMiniPlayerEnabled")
val CastEnabledKey = booleanPreferencesKey("castEnabled")
val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val CropAlbumArtKey = booleanPreferencesKey("cropAlbumArt")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val ButtonDpadRightKey = intPreferencesKey("buttonDpadRight")
val ButtonDpadLeftKey = intPreferencesKey("buttonDpadLeft")
val ButtonDpadUpKey = intPreferencesKey("buttonDpadUp")
val ButtonDpadDownKey = intPreferencesKey("buttonDpadDown")
val ButtonDpadCenterKey = intPreferencesKey("buttonDpadCenter")

enum class DensityScale(val value: Float, @StringRes val labelRes: Int) {
    NATIVE(1.0f, R.string.density_label_native),
    COMPACT(0.75f, R.string.density_label_compact),
    VERY_COMPACT(0.65f, R.string.density_label_very_compact),
    ULTRA_COMPACT(0.55f, R.string.density_label_ultra_compact),
    CUSTOM(-1f, R.string.density_label_custom);

    companion object {
        fun fromValue(value: Float): DensityScale = entries.find { it.value == value } ?: CUSTOM
    }
}

enum class SliderStyle {
    DEFAULT,
    SQUIGGLY,
    SLIM,
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val YtmSyncKey = booleanPreferencesKey("ytmSync")
// Persisted snapshot of the server's blockedContentIds list (newline-joined), loaded at startup so the
// blocklist is active before the first sync of the session and survives offline launches.
val BlockedContentIdsKey = stringPreferencesKey("blockedContentIds")
val CheckForUpdatesKey = booleanPreferencesKey("checkForUpdates")
val NightlyUpdatesKey = booleanPreferencesKey("nightlyUpdates") // opt-in nightly update channel
// Last nightly build announced via the startup snackbar, so each nightly is announced only once.
val LastNightlyAnnouncedKey = stringPreferencesKey("lastNightlyAnnounced")
val UpdateNotificationsEnabledKey = booleanPreferencesKey("updateNotifications")
val InstallerTypeKey = intPreferencesKey("installerType") // InstallerType ordinal
val LastWhitelistVersionKey = longPreferencesKey("lastWhitelistVersion")

val AudioQualityKey = stringPreferencesKey("audioQuality")

// Stream source toggles — each key maps to whether that client is enabled
val StreamSourceWebRemixKey   = booleanPreferencesKey("streamSourceWebRemix")
val StreamSourceTVHTML5Key    = booleanPreferencesKey("streamSourceTVHTML5")
val StreamSourceAndroidVRKey  = booleanPreferencesKey("streamSourceAndroidVR")
val StreamSourceIOSKey        = booleanPreferencesKey("streamSourceIOS")
val StreamSourceIPadOSKey     = booleanPreferencesKey("streamSourceIPadOS")
val StreamSourceWebCreatorKey = booleanPreferencesKey("streamSourceWebCreator")
val StreamSourceAndroidCreatorKey = booleanPreferencesKey("streamSourceAndroidCreator")
val StreamSourceVisionOSKey   = booleanPreferencesKey("streamSourceVisionOS")

enum class AudioQuality {
    AUTO,
    HIGH,
    LOW,
}

val AudioOffload = booleanPreferencesKey("enableOffload")

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val DisableLoadMoreWhenRepeatAllKey = booleanPreferencesKey("disableLoadMoreWhenRepeatAll")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val CustomDownloadPathKey = stringPreferencesKey("customDownloadPath")

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val OnboardingCompleteKey = booleanPreferencesKey("onboardingComplete")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

// Home screen cache
val HomeCacheKey = stringPreferencesKey("home_cache_json")

// Artist profiles cache (Firebase whitelist)
val ArtistProfilesCacheKey = stringPreferencesKey("artist_profiles_cache")
val ArtistProfilesCacheTimestampKey = longPreferencesKey("artist_profiles_cache_timestamp")

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")
val AllowFemaleSingersKey = booleanPreferencesKey("allowFemaleSingers")
val FemalePasscodeHashKey = stringPreferencesKey("femalePasscodeHash")
val AllowChasidishKey = booleanPreferencesKey("allowChasidish")
val BlockVideosKey = booleanPreferencesKey("blockVideos")
val EnableContentFiltersKey = booleanPreferencesKey("enableContentFilters")

// Online search engine: SearchProvider.name (ZEMER default, or YOUTUBE)
val SearchProviderKey = stringPreferencesKey("searchProvider")

val ContentFiltersAutoRestoredKey = booleanPreferencesKey("content_filters_auto_restored")
val ContentFiltersRestoredEmailKey = stringPreferencesKey("content_filters_restored_email")
val ContentFiltersLockedKey = booleanPreferencesKey("content_filters_locked")
val HomeRecentArtistsKey = stringPreferencesKey("home_recent_artists")

val ShowLikedPlaylistKey = booleanPreferencesKey("show_liked_playlist")
val ShowDownloadedPlaylistKey = booleanPreferencesKey("show_downloaded_playlist")
val ShowTopPlaylistKey = booleanPreferencesKey("show_top_playlist")
val ShowCachedPlaylistKey = booleanPreferencesKey("show_cached_playlist")
val ShowUploadedPlaylistKey = booleanPreferencesKey("show_uploaded_playlist")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    UPLOADED
}

enum class ArtistFilter {
    LIBRARY,
    LIKED
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    UPLOADED
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY ->
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            WEEK ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL_TIME -> 0
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

enum class PlayerButtonsStyle {
    DEFAULT,
    SECONDARY,
}

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    BLUR,
}

val TopSize = stringPreferencesKey("topSize")
val HistoryDuration = floatPreferencesKey("historyDuration")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")

// Anonymous telemetry install id (a random UUID, the ONLY identity the tracking server ever sees).
val TrackingDeviceIdKey = stringPreferencesKey("trackingDeviceId")
// One-shot listen-history backfill: resume cursor (row id of the last acked event), the upper
// bound (max event id when the backfill first ran — later rows were already reported live), and
// the done flag.
val TrackingBackfillCursorKey = longPreferencesKey("trackingBackfillCursor")
val TrackingBackfillBoundKey = longPreferencesKey("trackingBackfillBound")
val TrackingBackfillDoneKey = booleanPreferencesKey("trackingBackfillDone")
val TrackingActionBackfillDoneKey = booleanPreferencesKey("trackingActionBackfillDone")
val TrackingActionBackfillSentKey = longPreferencesKey("trackingActionBackfillSent")
val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val AndroidAutoYouTubePlaylistsKey = booleanPreferencesKey("androidAutoYoutubePlaylists")
val AndroidAutoSectionsOrderKey = stringPreferencesKey("androidAutoSectionsOrder")
val AndroidAutoTargetPlaylistKey = stringPreferencesKey("androidAutoTargetPlaylist")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")

val LanguageCodeToName =
    mapOf(
        "af" to "Afrikaans",
        "az" to "Azərbaycan",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Malaysia",
        "ca" to "Català",
        "cs" to "Čeština",
        "da" to "Dansk",
        "de" to "Deutsch",
        "et" to "Eesti",
        "en-GB" to "English (UK)",
        "en" to "English (US)",
        "es" to "Español (España)",
        "es-419" to "Español (Latinoamérica)",
        "eu" to "Euskara",
        "fil" to "Filipino",
        "fr" to "Français",
        "fr-CA" to "Français (Canada)",
        "gl" to "Galego",
        "hr" to "Hrvatski",
        "zu" to "IsiZulu",
        "is" to "Íslenska",
        "it" to "Italiano",
        "sw" to "Kiswahili",
        "lt" to "Lietuvių",
        "hu" to "Magyar",
        "nl" to "Nederlands",
        "no" to "Norsk",
        "or" to "Odia",
        "uz" to "O‘zbe",
        "pl" to "Polski",
        "pt-PT" to "Português",
        "pt" to "Português (Brasil)",
        "ro" to "Română",
        "sq" to "Shqip",
        "sk" to "Slovenčina",
        "sl" to "Slovenščina",
        "fi" to "Suomi",
        "sv" to "Svenska",
        "bo" to "Tibetan བོད་སྐད།",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "bg" to "Български",
        "ky" to "Кыргызча",
        "kk" to "Қазақ Тілі",
        "mk" to "Македонски",
        "mn" to "Монгол",
        "ru" to "Русский",
        "sr" to "Српски",
        "uk" to "Українська",
        "el" to "Ελληνικά",
        "hy" to "Հայերեն",
        "iw" to "עברית",
        "ur" to "اردو",
        "ar" to "العربية",
        "fa" to "فارسی",
        "ne" to "नेपाली",
        "mr" to "मराठी",
        "hi" to "हिन्दी",
        "bn" to "বাংলা",
        "pa" to "ਪੰਜਾਬੀ",
        "gu" to "ગુજરાતી",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം",
        "si" to "සිංහල",
        "th" to "ภาษาไทย",
        "lo" to "ລາວ",
        "my" to "ဗမာ",
        "ka" to "ქართული",
        "am" to "አማርኛ",
        "km" to "ខ្មែរ",
        "zh-CN" to "中文 (简体)",
        "zh-TW" to "中文 (繁體)",
        "zh-HK" to "中文 (香港)",
        "ja" to "日本語",
        "ko" to "한국어",
    )

val CountryCodeToName =
    mapOf(
        "DZ" to "Algeria",
        "AR" to "Argentina",
        "AU" to "Australia",
        "AT" to "Austria",
        "AZ" to "Azerbaijan",
        "BH" to "Bahrain",
        "BD" to "Bangladesh",
        "BY" to "Belarus",
        "BE" to "Belgium",
        "BO" to "Bolivia",
        "BA" to "Bosnia and Herzegovina",
        "BR" to "Brazil",
        "BG" to "Bulgaria",
        "KH" to "Cambodia",
        "CA" to "Canada",
        "CL" to "Chile",
        "HK" to "Hong Kong",
        "CO" to "Colombia",
        "CR" to "Costa Rica",
        "HR" to "Croatia",
        "CY" to "Cyprus",
        "CZ" to "Czech Republic",
        "DK" to "Denmark",
        "DO" to "Dominican Republic",
        "EC" to "Ecuador",
        "EG" to "Egypt",
        "SV" to "El Salvador",
        "EE" to "Estonia",
        "FI" to "Finland",
        "FR" to "France",
        "GE" to "Georgia",
        "DE" to "Germany",
        "GH" to "Ghana",
        "GR" to "Greece",
        "GT" to "Guatemala",
        "HN" to "Honduras",
        "HU" to "Hungary",
        "IS" to "Iceland",
        "IN" to "India",
        "ID" to "Indonesia",
        "IQ" to "Iraq",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IT" to "Italy",
        "JM" to "Jamaica",
        "JP" to "Japan",
        "JO" to "Jordan",
        "KZ" to "Kazakhstan",
        "KE" to "Kenya",
        "KR" to "South Korea",
        "KW" to "Kuwait",
        "LA" to "Lao",
        "LV" to "Latvia",
        "LB" to "Lebanon",
        "LY" to "Libya",
        "LI" to "Liechtenstein",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "MK" to "Macedonia",
        "MY" to "Malaysia",
        "MT" to "Malta",
        "MX" to "Mexico",
        "ME" to "Montenegro",
        "MA" to "Morocco",
        "NP" to "Nepal",
        "NL" to "Netherlands",
        "NZ" to "New Zealand",
        "NI" to "Nicaragua",
        "NG" to "Nigeria",
        "NO" to "Norway",
        "OM" to "Oman",
        "PK" to "Pakistan",
        "PA" to "Panama",
        "PG" to "Papua New Guinea",
        "PY" to "Paraguay",
        "PE" to "Peru",
        "PH" to "Philippines",
        "PL" to "Poland",
        "PT" to "Portugal",
        "PR" to "Puerto Rico",
        "QA" to "Qatar",
        "RO" to "Romania",
        "RU" to "Russian Federation",
        "SA" to "Saudi Arabia",
        "SN" to "Senegal",
        "RS" to "Serbia",
        "SG" to "Singapore",
        "SK" to "Slovakia",
        "SI" to "Slovenia",
        "ZA" to "South Africa",
        "ES" to "Spain",
        "LK" to "Sri Lanka",
        "SE" to "Sweden",
        "CH" to "Switzerland",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        "TH" to "Thailand",
        "TN" to "Tunisia",
        "TR" to "Turkey",
        "UG" to "Uganda",
        "UA" to "Ukraine",
        "AE" to "United Arab Emirates",
        "GB" to "United Kingdom",
        "US" to "United States",
        "UY" to "Uruguay",
        "VE" to "Venezuela (Bolivarian Republic)",
        "VN" to "Vietnam",
        "YE" to "Yemen",
        "ZW" to "Zimbabwe",
    )
