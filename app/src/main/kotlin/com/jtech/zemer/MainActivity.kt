package com.jtech.zemer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.google.firebase.auth.FirebaseAuth
import com.jtech.zemer.constants.AppBarHeight
import com.jtech.zemer.constants.AppLanguageKey
import com.jtech.zemer.constants.CheckForUpdatesKey
import com.jtech.zemer.constants.DarkModeKey
import com.jtech.zemer.constants.DefaultOpenTabKey
import com.jtech.zemer.constants.DisableScreenshotKey
import com.jtech.zemer.constants.DynamicThemeKey
import com.jtech.zemer.constants.FloatingMiniPlayerKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.InstallerTypeKey
import com.jtech.zemer.constants.LastWhitelistVersionKey
import com.jtech.zemer.constants.MiniPlayerBottomSpacing
import com.jtech.zemer.constants.MiniPlayerHeight
import com.jtech.zemer.constants.NavigationBarHeight
import com.jtech.zemer.constants.RecognizeMusicFabKey
import com.jtech.zemer.constants.SlimNavBarHeight
import com.jtech.zemer.constants.OnboardingCompleteKey
import com.jtech.zemer.constants.PauseListenHistoryKey
import com.jtech.zemer.constants.PauseSearchHistoryKey
import com.jtech.zemer.constants.PureBlackKey
import com.jtech.zemer.constants.SYSTEM_DEFAULT
import com.jtech.zemer.constants.SlimNavBarKey
import com.jtech.zemer.constants.BottomNavigationBarEnabledKey
import com.jtech.zemer.constants.BottomNavigationItemsKey
import com.jtech.zemer.constants.StopMusicOnTaskClearKey
import com.jtech.zemer.constants.UpdateNotificationsEnabledKey
import com.jtech.zemer.constants.UseNewMiniPlayerDesignKey
import com.jtech.zemer.constants.VisitorDataKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SearchHistory
import com.jtech.zemer.models.DpadDirection
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.DownloadUtil
import com.jtech.zemer.playback.MusicService
import com.jtech.zemer.playback.MusicService.MusicBinder
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.AccountSettingsDialog
import com.jtech.zemer.ui.component.BottomSheetMenu
import com.jtech.zemer.ui.component.BottomSheetPage
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.RecognizeMusicFab
import com.jtech.zemer.ui.screens.recognition.RecognizeMusicDialogActivity
import com.jtech.zemer.ui.component.TopSearch
import com.jtech.zemer.ui.component.rememberBottomSheetState
import com.jtech.zemer.ui.component.shimmer.ShimmerTheme
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.player.BottomSheetPlayer
import com.jtech.zemer.ui.screens.LoginGateScreen
import com.jtech.zemer.ui.screens.OnboardingFlow
import com.jtech.zemer.ui.screens.Screens
import com.jtech.zemer.ui.screens.SplashScreen
import com.jtech.zemer.ui.screens.navigationBuilder
import com.jtech.zemer.ui.screens.search.OnlineSearchScreen
import com.jtech.zemer.ui.screens.videoRoute
import com.jtech.zemer.ui.screens.settings.DarkMode
import com.jtech.zemer.ui.screens.settings.NavigationTab
import com.jtech.zemer.ui.theme.ColorSaver
import com.jtech.zemer.ui.theme.DefaultThemeColor
import com.jtech.zemer.ui.theme.ZemerTheme
import com.jtech.zemer.ui.theme.extractThemeColor
import com.jtech.zemer.ui.theme.rememberPureBlack
import com.jtech.zemer.ui.utils.appBarScrollBehavior
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.ui.utils.resetHeightOffset
import com.jtech.zemer.utils.ButtonInputCapture
import com.jtech.zemer.utils.ButtonMapperBridge
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.Updater
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.hasNotificationPermission
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.updater.InstallResult
import com.jtech.zemer.utils.updater.InstallerType
import com.jtech.zemer.utils.updater.rememberApkInstallController
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.utils.setAppLocale
import com.jtech.zemer.utils.tryStartForegroundService
import com.jtech.zemer.viewmodels.HomeViewModel
import com.jtech.zemer.viewmodels.KidZoneViewModel
import com.jtech.zemer.viewmodels.WhitelistedArtistsViewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.utils.parseCookieString
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@OptIn(ExperimentalFoundationApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var contentFilterSyncService: com.jtech.zemer.sync.ContentFilterSyncService

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private var dpadKeyMap: Map<Int, Int> by mutableStateOf(emptyMap())
    private val hatTracker = HatInputTracker()
    private var pendingServiceStart: Boolean = false

    /**
     * Request storage permissions at startup if not already granted.
     * Required for MediaStore downloads to Music/Zemer folder.
     */
    private fun requestStoragePermissionsIfNeeded() {
        // Check if permissions are already granted
        if (com.jtech.zemer.utils.PermissionHelper.hasMediaStoreWritePermission(this)) {
            return
        }

        // Get required permissions for current Android version
        val permissions = com.jtech.zemer.utils.PermissionHelper.getRequiredWritePermissions()
        if (permissions.isEmpty()) {
            // Android 10+ with no permissions needed (shouldn't happen with our fixed code)
            return
        }

        // Request permissions
        ActivityCompat.requestPermissions(this, permissions, 2000)
    }

    override fun onStart() {
        super.onStart()
        // Use startService() - Media3's MediaLibraryService handles foreground notification
        // automatically when playback begins
        val serviceIntent = Intent(this, MusicService::class.java)
        try {
            startService(serviceIntent)
            bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        } catch (e: IllegalStateException) {
            // In case the system still thinks we're background, retry once on resume
            pendingServiceStart = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingServiceStart) {
            val serviceIntent = Intent(this, MusicService::class.java)
            try {
                startService(serviceIntent)
                bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
                pendingServiceStart = false
            } catch (e: IllegalStateException) {
            }
        }
        ButtonMapperBridge.register(this)
    }

    override fun onStop() {
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onPause() {
        ButtonMapperBridge.unregister(this)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (dataStore.get(
                    StopMusicOnTaskClearKey,
                    false
                ) && playerConnection?.isPlaying?.value == true && isFinishing
            ) {
                stopService(Intent(this, MusicService::class.java))
            }
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        } finally {
            playerConnection = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.data
                    .map { prefs ->
                        val mapping = mutableMapOf<Int, Int>()
                        DpadDirection.entries.forEach { direction ->
                            val keyCode = prefs[direction.prefKey]
                            if (keyCode != null) {
                                mapping[keyCode] = direction.keyCode
                            }
                        }
                        mapping.toMap()
                    }
                    .collectLatest { dpadKeyMap = it }
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch {
                val locale = dataStore.data.first()[AppLanguageKey]
                    ?.takeUnless { it == SYSTEM_DEFAULT }
                    ?.let { Locale.forLanguageTag(it) }
                    ?: Locale.getDefault()
                setAppLocale(this@MainActivity, locale)
            }
        }

        // Request storage permissions at startup for MediaStore downloads
        // NOTE: Files permission is now handled in the onboarding flow
        // requestStoragePermissionsIfNeeded()

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Initialize content filter sync service
        contentFilterSyncService.initialize()

        setContent {
            val checkForUpdates by rememberPreference(CheckForUpdatesKey, defaultValue = false)

            LaunchedEffect(checkForUpdates) {
                if (checkForUpdates) {
                    withContext(Dispatchers.IO) {
                        if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                            val updatesEnabled = dataStore.get(CheckForUpdatesKey, false)
                            val notifEnabled = dataStore.get(UpdateNotificationsEnabledKey, false)
                            if (!updatesEnabled || !hasNotificationPermission(this@MainActivity)) return@withContext
                            Updater.getLatestUpdate().onSuccess { info ->
                                latestVersionName = info.versionName
                                if (info.versionName != BuildConfig.VERSION_NAME && notifEnabled) {
                                    if (!hasNotificationPermission(this@MainActivity)) return@onSuccess
                                    val intent = Intent(Intent.ACTION_VIEW, info.downloadUrl.toUri())

                                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                                        (PendingIntent.FLAG_IMMUTABLE)
                                    val pending = PendingIntent.getActivity(this@MainActivity, 1001, intent, flags)

                                    @SuppressLint("MissingPermission")
                                    run {
                                        val notif = NotificationCompat.Builder(this@MainActivity, "updates")
                                            .setSmallIcon(R.drawable.update)
                                            .setContentTitle(getString(R.string.update_available_title))
                                            .setContentText(info.versionName)
                                            .setContentIntent(pending)
                                            .setAutoCancel(true)
                                            .build()
                                        runCatching {
                                            NotificationManagerCompat.from(this@MainActivity).notify(1001, notif)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // when the user disables updates, reset to the current version
                    // to trick the app into thinking it's on the latest version
                    latestVersionName = BuildConfig.VERSION_NAME
                }
            }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }

            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }

            val pureBlack = rememberPureBlack()

            val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
            val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
            val showHistoryButton = remember(pauseListenHistory, eventCount) {
                !(pauseListenHistory && eventCount == 0)
            }

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }
            val themeColorCache = remember { mutableStateMapOf<String, Color>() }

            LaunchedEffect(playerConnection, enableDynamicTheme) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColorCache.clear()
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }

                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    val thumbnailUrl = song?.thumbnailUrl
                    if (thumbnailUrl != null) {
                        val cachedColor = themeColorCache[thumbnailUrl]
                        if (cachedColor != null) {
                            themeColor = cachedColor
                        } else {
                            val resolvedColor = withContext(Dispatchers.IO) {
                                runCatching {
                                    val result = withTimeoutOrNull(5000) {
                                        imageLoader.execute(
                                            ImageRequest.Builder(this@MainActivity)
                                                .data(thumbnailUrl)
                                                .allowHardware(false)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .networkCachePolicy(CachePolicy.ENABLED)
                                                .crossfade(false)
                                                .size(512)
                                                .build()
                                        )
                                    }
                                    result?.image?.toBitmap()?.extractThemeColor()
                                }.getOrNull()
                            } ?: DefaultThemeColor

                            themeColorCache[thumbnailUrl] = resolvedColor
                            themeColor = resolvedColor
                        }
                    } else {
                        themeColor = DefaultThemeColor
                    }
                }
            }

            ZemerTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
            ) {
                CompositionLocalProvider(
                    LocalDatabase provides database,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                ) {
                    BoxWithConstraints(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
                            )
                    ) {
                        val focusManager = LocalFocusManager.current
                        val density = LocalDensity.current
                        LocalConfiguration.current
                        val cutoutInsets = WindowInsets.displayCutout
                        val windowsInsets = WindowInsets.systemBars
                        val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                        val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                        // Check onboarding status first, then whitelist sync
                        val onboardingComplete by dataStore.data.map { it[OnboardingCompleteKey] ?: false }.collectAsState(initial = false)
                        val onboardingScope = rememberCoroutineScope()
                        val syncScope = rememberCoroutineScope()
                        val lifecycleOwner = LocalLifecycleOwner.current

                        
                        // Show onboarding first (before splash screen)
                        if (!onboardingComplete) {
                            OnboardingFlow(
                                onFinished = {
                                    onboardingScope.launch {
                                        dataStore.edit { it[OnboardingCompleteKey] = true }
                                    }
                                }
                            )
                            return@BoxWithConstraints
                        }

                        // After onboarding, show splash screen while syncing
                        val syncProgress by syncUtils.whitelistSyncProgress.collectAsState()
                        val (skipSplash, setSkipSplash) = remember { mutableStateOf(false) }
                        val (initialSyncHandled, setInitialSyncHandled) = rememberSaveable { mutableStateOf(false) }
                        val (launchSyncOnce, setLaunchSyncOnce) = rememberSaveable { mutableStateOf(false) }
                        val isWhitelistSyncing by syncUtils.isWhitelistSyncing.collectAsState(initial = false)
                        val (localWhitelistVersion) = rememberPreference(LastWhitelistVersionKey, 0L)
                        val alreadySyncedLocally = localWhitelistVersion > 0L && !isWhitelistSyncing && syncProgress.total == 0 && syncProgress.current == 0 && !syncProgress.isComplete

                        DisposableEffect(lifecycleOwner, true) {

                            syncScope.launch { syncUtils.syncArtistWhitelist() }

                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_START) {
                                    syncScope.launch { syncUtils.syncArtistWhitelist() }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)

                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        LaunchedEffect(syncProgress.isComplete, isWhitelistSyncing) {
                            if (!syncProgress.isComplete && !isWhitelistSyncing && !launchSyncOnce) {
                                setLaunchSyncOnce(true)
                                syncScope.launch { syncUtils.syncArtistWhitelist() }
                            }
                            if (alreadySyncedLocally && !initialSyncHandled) {
                                setInitialSyncHandled(true)
                            }
                        }

                        if (syncProgress.isComplete && !initialSyncHandled) {
                            setInitialSyncHandled(true)
                        }

                        if (!initialSyncHandled && !syncProgress.isComplete && !skipSplash) {
                            SplashScreen(
                                syncProgress = syncProgress,
                                onSkip = {
                                    setSkipSplash(true)
                                    setInitialSyncHandled(true)
                                }
                            )
                            return@BoxWithConstraints
                        }

                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }
                        val drawerState = rememberDrawerState(DrawerValue.Closed)

                        // Login gate - redirect to login_gate if not logged in
                        // Use a state that tracks whether preferences have been loaded
                        val context = LocalContext.current
                        var preferencesLoaded by remember { mutableStateOf(false) }
                        var loginGateCookie by rememberPreference(InnerTubeCookieKey, defaultValue = "")

                        // Mark preferences as loaded after first emission from DataStore
                        LaunchedEffect(Unit) {
                            context.dataStore.data.first()
                            preferencesLoaded = true
                        }

                        val isYouTubeLoggedIn = remember(loginGateCookie) {
                            parseCookieString(loginGateCookie).containsKey("SAPISID")
                        }
                        val currentRoute = navBackStackEntry?.destination?.route
                        LaunchedEffect(preferencesLoaded, isYouTubeLoggedIn, currentRoute) {
                            // Only redirect after preferences are loaded
                            if (preferencesLoaded && !isYouTubeLoggedIn && currentRoute != "login_gate" && currentRoute != "login") {
                                navController.navigate("login_gate") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        val isVideoScreen = remember(navBackStackEntry) {
                            navBackStackEntry?.destination?.route?.startsWith("video/") == true
                        }
                        val homeViewModel: HomeViewModel = hiltViewModel()
                        val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

                        // Contribution auth state
                        val firebaseAuth = remember { FirebaseAuth.getInstance() }
                        var isContributorSignedIn by rememberSaveable { mutableStateOf(firebaseAuth.currentUser != null) }
                        DisposableEffect(firebaseAuth) {
                            val listener = FirebaseAuth.AuthStateListener { auth ->
                                isContributorSignedIn = auth.currentUser != null
                            }
                            firebaseAuth.addAuthStateListener(listener)
                            onDispose { firebaseAuth.removeAuthStateListener(listener) }
                        }

                        val navigationItems = remember { Screens.MainScreens }
                        // Check SharedPreferences first for onboarding values, then fallback to DataStore
                        val sharedPreferences = remember { getSharedPreferences("metrolist_settings", MODE_PRIVATE) }
                        val prefBottomNavEnabled = remember(sharedPreferences) {
                            sharedPreferences.getBoolean("bottomNavigationBarEnabled", false)
                        }
                        val prefBottomNavItems = remember(sharedPreferences) {
                            sharedPreferences.getString("bottomNavigationItems", null)
                        }
                        val (bottomNavEnabled) = rememberPreference(BottomNavigationBarEnabledKey, defaultValue = prefBottomNavEnabled)
                        val (bottomNavItemsString) = rememberPreference(BottomNavigationItemsKey, defaultValue = prefBottomNavItems ?: "home,artists,search,library")

                        // Create bottom navigation items dynamically from preferences
                        val bottomNavigationItems = remember(bottomNavItemsString) {
                            val items = mutableListOf<Screens>()
                            bottomNavItemsString.split(",").forEach { itemKey ->
                                when (itemKey.trim()) {
                                    "home" -> items.add(Screens.Home)
                                    "artists" -> items.add(Screens.Artists)
                                    "kid_zone" -> items.add(Screens.KidZone)
                                    "search" -> items.add(Screens.Search)
                                    "library" -> items.add(Screens.Library)
                                }
                            }
                            items
                        }
                        val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                        val (floatingMiniPlayerEnabled) = rememberPreference(FloatingMiniPlayerKey, defaultValue = true)
                        val (recognizeMusicFab) = rememberPreference(RecognizeMusicFabKey, defaultValue = true)
                        val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
                        val (storedVisitorData) = rememberPreference(VisitorDataKey, defaultValue = "")
                        val isLoggedIn = remember(innerTubeCookie) {
                            parseCookieString(innerTubeCookie).containsKey("SAPISID")
                        }
                        val hasVisitorToken = remember(storedVisitorData) {
                            storedVisitorData.startsWith("Cg")
                        }

                        // Update notification dialog
                        var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
                        var pendingUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
                        var pendingUpdateNotes by rememberSaveable { mutableStateOf<String?>(null) }
                        var downloadState by remember { mutableStateOf<com.jtech.zemer.utils.UpdateChecker.DownloadState>(com.jtech.zemer.utils.UpdateChecker.DownloadState.Idle) }
                        var installError by remember { mutableStateOf<String?>(null) }
                        val updateScope = rememberCoroutineScope()

                        LaunchedEffect(Unit) {
                            App.pendingUpdateVersion?.let { version ->
                                pendingUpdateVersion = version
                                pendingUpdateNotes = App.pendingUpdateNotes
                                showUpdateDialog = true
                                App.clearPendingUpdate()
                            }
                        }

                        // Auto-install when download completes, honoring the chosen install method.
                        // Shared controller gates the Standard installer's permission and restarts
                        // the app after a silent update — same behaviour as the Updater screen.
                        val (installerTypeOrdinal) = rememberPreference(InstallerTypeKey, defaultValue = InstallerType.NATIVE.ordinal)
                        val installController = rememberApkInstallController(InstallerType.fromOrdinal(installerTypeOrdinal)) { result ->
                            when (result) {
                                is InstallResult.Success -> {
                                    downloadState = com.jtech.zemer.utils.UpdateChecker.DownloadState.Idle
                                    installError = null
                                }
                                is InstallResult.RequiresUserAction -> Unit // system installer UI takes over
                                is InstallResult.Error -> installError = result.message
                            }
                        }
                        LaunchedEffect(downloadState) {
                            val downloaded = downloadState as? com.jtech.zemer.utils.UpdateChecker.DownloadState.Downloaded ?: return@LaunchedEffect
                            installError = null
                            installController.install(downloaded.apkFile)
                        }

                        if (showUpdateDialog && pendingUpdateVersion != null) {
                            com.jtech.zemer.ui.component.UpdateDownloadDialog(
                                currentVersion = BuildConfig.VERSION_NAME,
                                latestVersion = pendingUpdateVersion!!,
                                notes = pendingUpdateNotes,
                                downloadState = downloadState,
                                isInstalling = installController.isInstalling,
                                installError = installError,
                                installerType = InstallerType.fromOrdinal(installerTypeOrdinal),
                                onDownload = {
                                    downloadState = com.jtech.zemer.utils.UpdateChecker.DownloadState.Downloading(0f)
                                    installError = null
                                    updateScope.launch {
                                        com.jtech.zemer.utils.UpdateChecker.downloadUpdate(this@MainActivity).collect { state ->
                                            downloadState = state
                                        }
                                    }
                                },
                                onInstall = { apk -> installController.install(apk) },
                                onDismiss = {
                                    showUpdateDialog = false
                                    downloadState = com.jtech.zemer.utils.UpdateChecker.DownloadState.Idle
                                    installError = null
                                },
                            )
                        }

                        val (defaultOpenTab) = rememberEnumPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME)
                        val tabOpenedFromShortcut = remember {
                            when (intent?.action) {
                                ACTION_LIBRARY -> NavigationTab.LIBRARY
                                ACTION_SEARCH -> NavigationTab.SEARCH
                                else -> null
                            }
                        }

                        val topLevelScreens = remember {
                            listOf(
                                Screens.Home.route,
                                Screens.Artists.route,
                                Screens.KidZone.route,
                                Screens.Search.route,
                                Screens.Library.route,
                                "settings",
                            )
                        }

                    val (query, onQueryChange) =
                        rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue())
                        }

                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }


                    val searchBarFocusRequester = remember { FocusRequester() }
                    val searchResultsFocusRequester = remember { FocusRequester() }

                    val onSearch: (String) -> Unit = remember {
                        { searchQuery ->
                            if (searchQuery.isNotEmpty()) {
                                onActiveChange(false)
                                navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")

                                if (dataStore[PauseSearchHistoryKey] != true) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        database.query {
                                            insert(SearchHistory(query = searchQuery))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    val inSearchScreen = remember(navBackStackEntry) {
                        navBackStackEntry?.destination?.route?.startsWith("search/") == true
                    }

                    val shouldShowSearchBar = remember(active, navBackStackEntry) {
                        active ||
                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                inSearchScreen
                    }

                    val shouldShowNavigationBar = remember(bottomNavEnabled, active, navBackStackEntry, inSearchScreen) {
                        bottomNavEnabled &&
                        !active &&
                        (navBackStackEntry?.destination?.route == null ||
                         bottomNavigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                         inSearchScreen ||
                         // Show bottom nav on any main screen if bottom nav is enabled, even if current screen's tab was removed
                         navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route })
                    }

                    val showRail = false

                    val navigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar) NavigationBarHeight else 0.dp,
                        animationSpec = tween(durationMillis = 200),
                        label = "navigationBarHeight"
                    )

                    val floatingMiniPlayerAllowed = floatingMiniPlayerEnabled && !isVideoScreen

                    val collapsedBound = remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        showRail,
                        useNewMiniPlayerDesign,
                        floatingMiniPlayerAllowed
                    ) {
                        if (floatingMiniPlayerAllowed) {
                            bottomInset +
                                (if (!showRail && shouldShowNavigationBar) NavigationBarHeight + 1.dp else 0.dp) +
                                (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                                MiniPlayerHeight
                        } else {
                            0.dp
                        }
                    }

                    val playerBottomSheetState =
                        rememberBottomSheetState(
                            dismissedBound = 0.dp,
                            collapsedBound = collapsedBound,
                            expandedBound = maxHeight,
                        )

                    val playerAwareWindowInsets = remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        playerBottomSheetState.isDismissed,
                        showRail,
                        floatingMiniPlayerAllowed
                    ) {
                        var bottom = bottomInset
                        if (floatingMiniPlayerAllowed && !playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                        if (shouldShowNavigationBar) bottom += NavigationBarHeight
                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                    }

                    appBarScrollBehavior(
                        canScroll = {
                            !inSearchScreen &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    val searchBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                !inSearchScreen &&
                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )
                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                !inSearchScreen &&
                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )

                    // Navigation tracking
                    LaunchedEffect(navBackStackEntry) {
                        if (inSearchScreen) {
                            val searchQuery =
                                withContext(Dispatchers.IO) {
                                    if (navBackStackEntry
                                            ?.arguments
                                            ?.getString(
                                                "query",
                                            )!!
                                            .contains(
                                                "%",
                                            )
                                    ) {
                                        navBackStackEntry?.arguments?.getString(
                                            "query",
                                        )!!
                                    } else {
                                        URLDecoder.decode(
                                            navBackStackEntry?.arguments?.getString("query")!!,
                                            "UTF-8"
                                        )
                                    }
                                }
                            onQueryChange(
                                TextFieldValue(
                                    searchQuery,
                                    TextRange(searchQuery.length)
                                )
                            )
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            onQueryChange(TextFieldValue())
                        }

                        // Reset scroll behavior for main navigation items
                        if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            if (navigationItems.fastAny { it.route == previousTab }) {
                                searchBarScrollBehavior.state.resetHeightOffset()
                            }
                        }

                        searchBarScrollBehavior.state.resetHeightOffset()
                        topAppBarScrollBehavior.state.resetHeightOffset()

                        // Track previous tab for animations
                        navController.currentBackStackEntry?.destination?.route?.let {
                            setPreviousTab(it)
                        }
                    }

                    LaunchedEffect(active) {
                        if (active) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(playerConnection, floatingMiniPlayerAllowed) {
                        val player = playerConnection?.player ?: return@LaunchedEffect
                        if (floatingMiniPlayerAllowed) {
                            if (player.currentMediaItem != null && playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }

                    DisposableEffect(playerConnection, playerBottomSheetState, floatingMiniPlayerAllowed) {
                        val player =
                            playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener =
                            object : Player.Listener {
                                override fun onMediaItemTransition(
                                    mediaItem: MediaItem?,
                                    reason: Int,
                                ) {
                                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                        mediaItem != null &&
                                        floatingMiniPlayerAllowed &&
                                        playerBottomSheetState.isDismissed
                                    ) {
                                        playerBottomSheetState.collapseSoft()
                                    }
                                }
                            }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    var showAccountDialog by remember { mutableStateOf(false) }
                    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(navBackStackEntry) {
                        shouldShowTopBar =
                            !active && navBackStackEntry?.destination?.route in topLevelScreens && navBackStackEntry?.destination?.route != "settings"
                    }

                    val coroutineScope = rememberCoroutineScope()
                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleDeepLinkIntent(pendingIntent!!, navController)
                            pendingIntent = null
                        } else {
                            handleDeepLinkIntent(intent, navController)
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            handleDeepLinkIntent(intent, navController)
                        }

                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    val currentTitleRes = remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.home
                            Screens.Artists.route -> R.string.artists
                            Screens.KidZone.route -> R.string.kid_zone
                            Screens.Search.route -> R.string.search
                            Screens.Library.route -> R.string.filter_library
                            else -> null
                        }
                    }

                    val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                    val insetBg = if (playerBottomSheetState.progress > 0f) Color.Transparent else baseBg
                    val drawerFocusRequester = remember { FocusRequester() }
                    val topPlayFocusRequester = remember { FocusRequester() }
                    val miniPlayFocusRequester = remember { FocusRequester() }
                    val miniHeartFocusRequester = remember { FocusRequester() }
                    val burgerFocusRequester = remember { FocusRequester() }
                    val contentFocusRequester = remember { FocusRequester() }
                    val drawerScrollState = rememberScrollState()
                    val snackbarHostState = remember { SnackbarHostState() }

                    // Observe playback errors and show snackbar
                    LaunchedEffect(playerConnection) {
                        playerConnection?.error?.collect { error ->
                            error?.let {
                                snackbarHostState.showSnackbar(
                                    message = it.message ?: "Playback error occurred",
                                    withDismissAction = true
                                )
                            }
                        }
                    }

                        CompositionLocalProvider(
                            LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        ) {
                            ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                                    drawerContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.focusProperties {
                                        canFocus = drawerState.isOpen
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(drawerScrollState)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.version_short, BuildConfig.VERSION_NAME),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    val statusText = stringResource(R.string.account_status_logged_in)
                                    val statusColor = when {
                                        isLoggedIn -> MaterialTheme.colorScheme.primary
                                        hasVisitorToken -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    val accountItemBringIntoViewRequester = remember { BringIntoViewRequester() }
                                    NavigationDrawerItem(
                                        label = {
                                            Column(verticalArrangement = Arrangement.Center) {
                                                Text(stringResource(R.string.account))
                                                if (isLoggedIn || hasVisitorToken) {
                                                    Text(
                                                        text = statusText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = statusColor
                                                    )
                                                }
                                            }
                                        },
                                        icon = {
                                            when {
                                                isLoggedIn && accountImageUrl != null -> {
                                                    Surface(
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        AsyncImage(
                                                            model = accountImageUrl,
                                                            contentDescription = stringResource(R.string.account),
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }

                                                hasVisitorToken -> {
                                                    Icon(
                                                        painter = painterResource(R.drawable.incognito),
                                                        contentDescription = stringResource(R.string.account)
                                                    )
                                                }

                                                else -> {
                                                    Icon(
                                                        painter = painterResource(R.drawable.account),
                                                        contentDescription = stringResource(R.string.account)
                                                    )
                                                }
                                            }
                                        },
                                        selected = false,
                                        onClick = {
                                            coroutineScope.launch { drawerState.close() }
                                            showAccountDialog = true
                                        },
                                        modifier = Modifier
                                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                                            .focusProperties { canFocus = drawerState.isOpen }
                                            .bringIntoViewRequester(accountItemBringIntoViewRequester)
                                            .onFocusEvent { event ->
                                                if (event.isFocused) {
                                                    coroutineScope.launch {
                                                        accountItemBringIntoViewRequester.bringIntoView()
                                                    }
                                                }
                                            }
                                    )
                                    navigationItems.fastForEachIndexed { index, screen ->
                                        val isSelected =
                                            navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                        val itemBringIntoViewRequester = remember { BringIntoViewRequester() }
                                        NavigationDrawerItem(
                                            label = {
                                                Text(
                                                    text = stringResource(screen.titleId),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            icon = {
                                                Icon(
                                                    painter = painterResource(
                                                        id = if (isSelected) screen.iconIdActive else screen.iconIdInactive
                                                    ),
                                                    contentDescription = null,
                                                )
                                            },
                                            selected = isSelected,
                                            onClick = {
                                                coroutineScope.launch { drawerState.close() }
                                                if (screen.route == Screens.Search.route) {
                                                    onActiveChange(true)
                                                } else if (isSelected) {
                                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                    coroutineScope.launch {
                                                        searchBarScrollBehavior.state.resetHeightOffset()
                                                    }
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                                .focusProperties { canFocus = drawerState.isOpen }
                                                .bringIntoViewRequester(itemBringIntoViewRequester)
                                                .onFocusEvent { event ->
                                                    if (event.isFocused) {
                                                        coroutineScope.launch {
                                                            itemBringIntoViewRequester.bringIntoView()
                                                        }
                                                    }
                                                }
                                                .then(
                                                    if (index == 0) Modifier.focusRequester(drawerFocusRequester) else Modifier
                                                )
                                    )
                                }
                                val radioBringIntoViewRequester = remember { BringIntoViewRequester() }
                                NavigationDrawerItem(
                                    label = { Text(stringResource(R.string.radio_mode)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null
                                        )
                                    },
                                    selected = false,
                                    onClick = {
                                        coroutineScope.launch { drawerState.close() }
                                        navController.navigate(Screens.Home.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        navController.getBackStackEntry(Screens.Home.route)
                                            .savedStateHandle["shuffleNow"] = true
                                    },
                                    modifier = Modifier
                                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                                        .focusProperties { canFocus = drawerState.isOpen }
                                        .bringIntoViewRequester(radioBringIntoViewRequester)
                                        .onFocusEvent { event ->
                                            if (event.isFocused) {
                                                coroutineScope.launch {
                                                    radioBringIntoViewRequester.bringIntoView()
                                                }
                                            }
                                        }
                                )
                                                                val settingsBringIntoViewRequester = remember { BringIntoViewRequester() }
                                NavigationDrawerItem(
                                    label = { Text(stringResource(R.string.settings)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.settings),
                                            contentDescription = null
                                            )
                                        },
                                        selected = navBackStackEntry?.destination?.route == "settings",
                                        onClick = {
                                            coroutineScope.launch { drawerState.close() }
                                            navController.navigate("settings") {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                                            .focusProperties { canFocus = drawerState.isOpen }
                                            .bringIntoViewRequester(settingsBringIntoViewRequester)
                                            .onFocusEvent { event ->
                                                if (event.isFocused) {
                                                    coroutineScope.launch {
                                                        settingsBringIntoViewRequester.bringIntoView()
                                                    }
                                                }
                                            }
                                    )
                                }
                            }
                        ) {
                            LaunchedEffect(drawerState.isOpen) {
                                if (drawerState.isOpen) {
                                    drawerFocusRequester.requestFocus()
                                }
                            }

                            Scaffold(
                            snackbarHost = {
                                SnackbarHost(hostState = snackbarHostState)
                            },
                            topBar = {
                                AnimatedVisibility(
                                    visible = shouldShowTopBar,
                                    enter = slideInHorizontally(
                                        initialOffsetX = { -it / 4 },
                                        animationSpec = tween(durationMillis = 100)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 100)),
                                    exit = slideOutHorizontally(
                                        targetOffsetX = { -it / 4 },
                                        animationSpec = tween(durationMillis = 100)
                                    ) + fadeOut(animationSpec = tween(durationMillis = 100))
                                ) {
                                    Row {
                                        TopAppBar(
                                            title = {
                                                Text(
                                                    text = currentTitleRes?.let { stringResource(it) } ?: "",
                                                    style = MaterialTheme.typography.titleLarge,
                                                )
                                            },
                                            navigationIcon = {
                                                val isMainScreen =
                                                    navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
                                                IconButton(
                                                    onClick = {
                                                        when {
                                                            active -> onActiveChange(false)
                                                            !isMainScreen -> navController.navigateUp()
                                                            else -> coroutineScope.launch { drawerState.open() }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        when {
                                                            active -> {}
                                                            !isMainScreen -> navController.backToMain()
                                                            else -> {}
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        painterResource(
                                                            if (active || !isMainScreen) {
                                                                R.drawable.arrow_back
                                                            } else {
                                                                R.drawable.menu
                                                            },
                                                        ),
                                                        contentDescription = null,
                                                    modifier = Modifier
                                                        .focusRequester(burgerFocusRequester)
                                                        .focusProperties {
                                                            next = topPlayFocusRequester
                                                            down = contentFocusRequester
                                                            previous = miniHeartFocusRequester
                                                        }
                                                    )
                                                }
                                            },
                                            actions = {
                                                val currentRoute = navBackStackEntry?.destination?.route
                                                if (currentRoute == Screens.Home.route) {
                                                    if (showHistoryButton) {
                                                        IconButton(
                                                            onClick = { navController.navigate("history") },
                                                            colors = IconButtonDefaults.iconButtonColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                            ),
                                                            modifier = Modifier.clip(CircleShape)
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.history),
                                                                contentDescription = stringResource(R.string.history)
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            onActiveChange(true)
                                                        },
                                                        colors = IconButtonDefaults.iconButtonColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                        ),
                                                        modifier = Modifier.clip(CircleShape)
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.search),
                                                            contentDescription = stringResource(R.string.search)
                                                        )
                                                    }
                                                }

                                                if (currentRoute == Screens.Artists.route && navBackStackEntry != null) {
                                                    val whitelistedArtistsViewModel: WhitelistedArtistsViewModel =
                                                        hiltViewModel(navBackStackEntry!!)
                                                    IconButton(
                                                        onClick = { whitelistedArtistsViewModel.sync() },
                                                        colors = IconButtonDefaults.iconButtonColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                        ),
                                                        modifier = Modifier.clip(CircleShape)
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.sync),
                                                            contentDescription = stringResource(R.string.refresh_artists)
                                                        )
                                                    }
                                                }

                                                if (currentRoute == Screens.KidZone.route && navBackStackEntry != null) {
                                                    val kidZoneViewModel: KidZoneViewModel =
                                                        hiltViewModel(navBackStackEntry!!)
                                                    IconButton(
                                                        onClick = { kidZoneViewModel.sync() },
                                                        colors = IconButtonDefaults.iconButtonColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                        ),
                                                        modifier = Modifier.clip(CircleShape)
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.sync),
                                                            contentDescription = stringResource(R.string.refresh_artists)
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            playerBottomSheetState.expandSoft()
                                                        }
                                                },
                                                    colors = IconButtonDefaults.iconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                    ),
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .focusRequester(topPlayFocusRequester)
                                                        .focusProperties {
                                                            next = miniPlayFocusRequester
                                                            previous = burgerFocusRequester
                                                            down = contentFocusRequester
                                                        }
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.play),
                                                        contentDescription = stringResource(R.string.now_playing)
                                                    )
                                                }
                                            },
                                            scrollBehavior = searchBarScrollBehavior,
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.windowInsetsPadding(
                                                cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                                            )
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = active || inSearchScreen,
                                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(150)),
                                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(100))
                                ) {
                                    TopSearch(
                                        query = query,
                                        onQueryChange = onQueryChange,
                                        onSearch = onSearch,
                                        active = active,
                                        onActiveChange = onActiveChange,
                                        downFocusRequester = searchResultsFocusRequester,
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.search_yt_music),
                                            )
                                        },
                                        leadingIcon = {
                                            val isMainScreen =
                                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
                                            IconButton(
                                                onClick = {
                                                    when {
                                                        active -> onActiveChange(false)
                                                        !isMainScreen -> {
                                                            navController.navigateUp()
                                                        }

                                                        else -> coroutineScope.launch { drawerState.open() }
                                                    }
                                                },
                                                onLongClick = {
                                                    when {
                                                        active -> {}
                                                        !isMainScreen -> {
                                                            navController.backToMain()
                                                        }
                                                        else -> {}
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    painterResource(
                                                        if (active || !isMainScreen) {
                                                            R.drawable.arrow_back
                                                        } else {
                                                            R.drawable.menu
                                                        },
                                                    ),
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Row {
                                                if (active) {
                                                    if (query.text.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = {
                                                                onQueryChange(
                                                                    TextFieldValue(
                                                                        ""
                                                                    )
                                                               )
                                                            },
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.close),
                                                                contentDescription = null,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        focusRequester = searchBarFocusRequester,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .windowInsetsPadding(WindowInsets(0.dp)),
                                        colors = if (pureBlack && active) {
                                            SearchBarDefaults.colors(
                                                containerColor = Color.Black,
                                                dividerColor = Color.DarkGray,
                                                inputFieldColors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.Gray,
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    cursorColor = Color.White,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                )
                                            )
                                        } else {
                                            SearchBarDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            )
                                        }
                                    ) {
                                        OnlineSearchScreen(
                                            query = query.text,
                                            onQueryChange = onQueryChange,
                                            navController = navController,
                                            onSearch = { searchQuery ->
                                                navController.navigate(
                                                    "search/${URLEncoder.encode(searchQuery, "UTF-8")}"
                                                )
                                                if (dataStore[PauseSearchHistoryKey] != true) {
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        database.query {
                                                            insert(SearchHistory(query = searchQuery))
                                                        }
                                                    }
                                                }
                                            },
                                            onDismiss = { onActiveChange(false) },
                                            pureBlack = pureBlack,
                                            firstResultFocusRequester = searchResultsFocusRequester,
                                            searchFocusRequester = searchBarFocusRequester
                                        )
                                    }
                                }
                            },
                            bottomBar = {
                                Box(
                                    modifier = Modifier
                                        .focusable(false)
                                        .focusProperties { canFocus = false }
                                ) {
                                    BottomSheetPlayer(
                                        state = playerBottomSheetState,
                                        navController = navController,
                                        pureBlack = pureBlack,
                                        floatingMiniPlayerEnabledOverride = floatingMiniPlayerAllowed,
                                        miniPlayerFocusTargets = null
                                    )

                                    Box(
                                        modifier = Modifier
                                            .background(insetBg)
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                    )

                                    // Bottom Navigation Bar
                                val density = LocalDensity.current
                                NavigationBar(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .height(bottomInset + NavigationBarHeight)
                                        .offset {
                                            if (!shouldShowNavigationBar || playerBottomSheetState.isExpanded) {
                                                IntOffset(
                                                    x = 0,
                                                    y = with(density) { (bottomInset + NavigationBarHeight).roundToPx() },
                                                )
                                            } else {
                                                val slideOffset =
                                                    (bottomInset + NavigationBarHeight) *
                                                            playerBottomSheetState.progress.coerceIn(
                                                                0f,
                                                                1f,
                                                            )
                                                val hideOffset =
                                                    (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                IntOffset(
                                                    x = 0,
                                                    y = with(density) { (slideOffset + hideOffset).roundToPx() },
                                                )
                                            }
                                        },
                                    containerColor = if (pureBlack) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    bottomNavigationItems.forEach { screen ->
                                        val isSelected = navBackStackEntry?.destination?.hierarchy?.any {
                                destination ->
                                    destination.route == screen.route ||
                                    (screen.route == Screens.Search.route && destination.route?.startsWith("search/") == true)
                            } == true

                                        NavigationBarItem(
                                            selected = isSelected,
                                            icon = {
                                                Icon(
                                                    painter = painterResource(
                                                        if (isSelected) screen.iconIdActive else screen.iconIdInactive
                                                    ),
                                                    contentDescription = null
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(screen.titleId),
                                                    maxLines = 1
                                                )
                                            },
                                            onClick = {
                                                if (screen.route == Screens.Search.route) {
                                                    onActiveChange(true)
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                        }
                                                    }
                                                }
                                        )
                                    }
                                }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(contentFocusRequester)
                                        .focusProperties { up = topPlayFocusRequester }
                                        .focusable()
                                ) {
                                    // NavHost with animations
                                    NavHost(
                                        navController = navController,
                                        startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                            NavigationTab.HOME -> Screens.Home
                                            NavigationTab.LIBRARY -> Screens.Library
                                            else -> Screens.Home
                                        }.route,
                                        // Enter Transition
                                        enterTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }
                                            val previousRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }

                                            if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                                                slideInHorizontally { it / 4 } + fadeIn(tween(150))
                                            else
                                                slideInHorizontally { -it / 4 } + fadeIn(tween(150))
                                        },
                                        // Exit Transition
                                        exitTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }
                                            val targetRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }

                                            if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                                                slideOutHorizontally { -it / 4 } + fadeOut(tween(100))
                                            else
                                                slideOutHorizontally { it / 4 } + fadeOut(tween(100))
                                        },
                                        // Pop Enter Transition
                                        popEnterTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }
                                            val previousRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }

                                            if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                                                slideInHorizontally { it / 4 } + fadeIn(tween(150))
                                            else
                                                slideInHorizontally { -it / 4 } + fadeIn(tween(150))
                                        },
                                        // Pop Exit Transition
                                        popExitTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }
                                            val targetRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }

                                            if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                                                slideOutHorizontally { -it / 4 } + fadeOut(tween(100))
                                            else
                                                slideOutHorizontally { it / 4 } + fadeOut(tween(100))
                                        },
                                        modifier = Modifier.nestedScroll(
                                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                                inSearchScreen
                                            ) {
                                                searchBarScrollBehavior.nestedScrollConnection
                                            } else {
                                                topAppBarScrollBehavior.nestedScrollConnection
                                            }
                                        )
                                    ) {
                                        navigationBuilder(
                                            navController,
                                            topAppBarScrollBehavior,
                                            searchBarScrollBehavior,
                                            latestVersionName,
                                            homeViewModel
                                        )
                                    }
                                }
                            }
                        }

                        }

                        // "Recognize music" FAB — floats above the bottom nav bar (and mini player)
                        // on main screens; toggleable via Settings → Appearance (default on).
                        if (recognizeMusicFab &&
                            !active &&
                            !playerBottomSheetState.isExpanded &&
                            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
                        ) {
                            RecognizeMusicFab(
                                onClick = {
                                    context.startActivity(
                                        Intent(context, RecognizeMusicDialogActivity::class.java),
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = 16.dp,
                                        bottom = playerAwareWindowInsets.asPaddingValues()
                                            .calculateBottomPadding() + 16.dp,
                                    ),
                            )
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        if (showAccountDialog) {
                            AccountSettingsDialog(
                                navController = navController,
                                onDismiss = {
                                    showAccountDialog = false
                                    homeViewModel.refresh()
                                },
                                latestVersionName = latestVersionName
                            )
                        }

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false),
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                            LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                                if (shouldShowSearchBar && openSearchImmediately) {
                                    onActiveChange(true)
                                    searchBarFocusRequester.requestFocus()
                                    openSearchImmediately = false
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

        // Check if it's a video.zemer.io link
        val isVideoLink = uri.host == "video.zemer.io"

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                // Check if album is whitelisted before navigating
                                val album = database.album(browseId).first()
                                if (album != null) {
                                    withContext(Dispatchers.Main) {
                                        navController.navigate("album/$browseId")
                                    }
                                }
                                // Silently ignore if not whitelisted
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    coroutineScope.launch(Dispatchers.IO) {
                        // Fetch playlist and check if it has any whitelisted songs
                        YouTube.playlist(playlistId).onSuccess { playlistPage ->
                            val whitelistedSongs = playlistPage.songs
                                .filterWhitelisted(database)
                                .filterIsInstance<SongItem>()

                            if (whitelistedSongs.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    navController.navigate("online_playlist/$playlistId")
                                }
                            }
                            // Silently ignore if no whitelisted songs
                        }.onFailure { reportException(it) }
                    }
                }
            }

            "recognition_history" -> navController.navigate("recognition_history")

            "browse" -> uri.lastPathSegment?.let { browseId ->
                coroutineScope.launch(Dispatchers.IO) {
                    // Check if album exists and is whitelisted before navigating
                    val album = database.album(browseId).first()
                    if (album != null) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("album/$browseId")
                        }
                    }
                    // Silently ignore if album doesn't exist or isn't whitelisted
                }
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                coroutineScope.launch(Dispatchers.IO) {
                    // Check if artist is whitelisted before navigating
                    val isWhitelisted = database.isArtistWhitelisted(artistId)
                    if (isWhitelisted) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("artist/$artistId")
                        }
                    }
                    // Silently ignore if not whitelisted
                }
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")

                videoId?.let {
                    if (isVideoLink) {
                        // For video.zemer.io links, navigate directly to video player
                        navController.navigate(videoRoute(it))
                    } else {
                        // For other links, use audio player
                        coroutineScope.launch(Dispatchers.IO) {
                            YouTube.queue(listOf(it), playlistId).onSuccess { queue ->
                                // Filter by whitelist
                                val filteredQueue = queue.filterWhitelisted(database).filterIsInstance<SongItem>()

                                // Silently ignore if no whitelisted songs
                                if (filteredQueue.isEmpty()) {
                                    return@onSuccess
                                }

                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = filteredQueue.firstOrNull()?.id, playlistId = playlistId),
                                            filteredQueue.firstOrNull()?.toMediaMetadata(),
                                            database
                                        )
                                    )
                                }
                            }.onFailure { ex ->
                                reportException(ex)
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    fun handleAccessibilityKey(event: KeyEvent): Boolean {
        if (isProtectedKey(event.keyCode)) {
            return false
        }
        return handleMappedKeyEvent(event)
    }

    @SuppressLint("RestrictedApi")
    private fun handleMappedKeyEvent(event: KeyEvent): Boolean {
        val mapped = dpadKeyMap[event.keyCode] ?: return false
        val target = if (mapped == event.keyCode) {
            event
        } else {
            KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                mapped,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source
            )
        }
        super.dispatchKeyEvent(target)
        return true
    }

    private fun isProtectedKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_POWER
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ButtonInputCapture.isCapturing() && hatTracker.handle(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    companion object {
        const val ACTION_SEARCH = "com.jtech.zemer.action.SEARCH"
        const val ACTION_LIBRARY = "com.jtech.zemer.action.LIBRARY"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }

private class HatInputTracker {
    private var lastKeyCode: Int? = null

    fun handle(event: MotionEvent): Boolean {
        val source = event.source
        val isGamepad = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val isJoystick = (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!isGamepad && !isJoystick) {
            return false
        }
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val keyCode = when {
            x > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
            x < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
            y < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
            y > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> null
        }
        if (keyCode == null) {
            lastKeyCode = null
            return false
        }
        if (keyCode == lastKeyCode) {
            return true
        }
        lastKeyCode = keyCode
        val time = SystemClock.uptimeMillis()
        val synthetic = KeyEvent(
            time,
            time,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            0,
            event.deviceId,
            0,
            0,
            event.source
        )
        ButtonInputCapture.notify(synthetic)
        return true
    }
}
