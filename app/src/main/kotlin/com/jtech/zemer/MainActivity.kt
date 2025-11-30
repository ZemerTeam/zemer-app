package com.jtech.zemer

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import com.google.firebase.auth.FirebaseAuth
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.datastore.preferences.core.edit
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
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.jtech.zemer.constants.AppBarHeight
import com.jtech.zemer.constants.AppLanguageKey
import com.jtech.zemer.constants.CheckForUpdatesKey
import com.jtech.zemer.constants.DarkModeKey
import com.jtech.zemer.constants.DefaultOpenTabKey
import com.jtech.zemer.constants.DisableScreenshotKey
import com.jtech.zemer.constants.DynamicThemeKey
import com.jtech.zemer.constants.OnboardingCompleteKey
import com.jtech.zemer.constants.MiniPlayerHeight
import com.jtech.zemer.constants.MiniPlayerBottomSpacing
import com.jtech.zemer.constants.UpdateNotificationsEnabledKey
import com.jtech.zemer.constants.UseNewMiniPlayerDesignKey
import com.jtech.zemer.constants.FloatingMiniPlayerKey
import com.jtech.zemer.constants.PauseSearchHistoryKey
import com.jtech.zemer.constants.PureBlackKey
import com.jtech.zemer.constants.LastWhitelistVersionKey
import com.jtech.zemer.constants.SYSTEM_DEFAULT
import com.jtech.zemer.constants.SlimNavBarHeight
import com.jtech.zemer.constants.SlimNavBarKey
import com.jtech.zemer.constants.StopMusicOnTaskClearKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SearchHistory
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.models.DpadDirection
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.DownloadUtil
import com.jtech.zemer.playback.MusicService
import com.jtech.zemer.playback.MusicService.MusicBinder
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.BottomSheetMenu
import com.jtech.zemer.ui.component.BottomSheetPage
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalBottomSheetPageState
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.TopSearch
import com.jtech.zemer.ui.component.rememberBottomSheetState
import com.jtech.zemer.ui.component.shimmer.ShimmerTheme
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.player.BottomSheetPlayer
import com.jtech.zemer.ui.player.MiniPlayerFocusTargets
import com.jtech.zemer.ui.screens.OnboardingFlow
import com.jtech.zemer.ui.screens.Screens
import com.jtech.zemer.ui.screens.navigationBuilder
import com.jtech.zemer.ui.screens.search.OnlineSearchScreen
import com.jtech.zemer.ui.screens.settings.DarkMode
import com.jtech.zemer.ui.screens.settings.NavigationTab
import com.jtech.zemer.ui.theme.ColorSaver
import com.jtech.zemer.ui.theme.DefaultThemeColor
import com.jtech.zemer.ui.theme.ZemerTheme
import com.jtech.zemer.ui.screens.SplashScreen
import com.jtech.zemer.ui.theme.extractThemeColor
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
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.utils.setAppLocale
import com.jtech.zemer.viewmodels.HomeViewModel
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

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
            timber.log.Timber.d("Storage permissions already granted")
            return
        }

        // Get required permissions for current Android version
        val permissions = com.jtech.zemer.utils.PermissionHelper.getRequiredWritePermissions()
        if (permissions.isEmpty()) {
            // Android 10+ with no permissions needed (shouldn't happen with our fixed code)
            timber.log.Timber.d("No storage permissions required")
            return
        }

        // Request permissions
        timber.log.Timber.d("Requesting storage permissions at startup: ${permissions.joinToString()}")
        ActivityCompat.requestPermissions(this, permissions, 2000)
    }

    override fun onStart() {
        super.onStart()
        // NOTE: Notification permission is now handled in the onboarding flow
        val serviceIntent = Intent(this, MusicService::class.java)
        try {
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: IllegalStateException) {
            // In case the system still thinks we're background, retry once on resume
            timber.log.Timber.w(e, "[ServiceLifecycle] MusicService start blocked (app may be in background); will retry on resume - thread: ${Thread.currentThread().name}")
            pendingServiceStart = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingServiceStart) {
            val serviceIntent = Intent(this, MusicService::class.java)
            try {
                androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                pendingServiceStart = false
            } catch (e: IllegalStateException) {
                timber.log.Timber.e(e, "[ServiceLifecycle] MusicService start still blocked on resume - background restrictions may be active - thread: ${Thread.currentThread().name}")
            }
        }
        ButtonMapperBridge.register(this)
    }

    override fun onStop() {
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            timber.log.Timber.w(e, "[ServiceLifecycle] Service was not bound during onStop - service may have crashed or not started - thread: ${Thread.currentThread().name}")
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
            timber.log.Timber.w(e, "[ServiceLifecycle] Service cleanup error during onDestroy - service may have already been unbound - isFinishing: $isFinishing - thread: ${Thread.currentThread().name}")
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

        setContent {
            val checkForUpdates by rememberPreference(CheckForUpdatesKey, defaultValue = false)

            LaunchedEffect(checkForUpdates) {
                if (checkForUpdates) {
                    withContext(Dispatchers.IO) {
                        if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                            val updatesEnabled = dataStore.get(CheckForUpdatesKey, false)
                            val notifEnabled = dataStore.get(UpdateNotificationsEnabledKey, false)
                            if (!updatesEnabled) return@withContext
                            Updater.getLatestUpdate().onSuccess { info ->
                                latestVersionName = info.versionName
                                if (info.versionName != BuildConfig.VERSION_NAME && notifEnabled) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))

                                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                                    val pending = PendingIntent.getActivity(this@MainActivity, 1001, intent, flags)

                                    val notif = NotificationCompat.Builder(this@MainActivity, "updates")
                                        .setSmallIcon(R.drawable.update)
                                        .setContentTitle(getString(R.string.update_available_title))
                                        .setContentText(info.versionName)
                                        .setContentIntent(pending)
                                        .setAutoCancel(true)
                                        .build()
                                    NotificationManagerCompat.from(this@MainActivity).notify(1001, notif)
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

            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme 
            }

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(playerConnection, enableDynamicTheme) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }

                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song?.thumbnailUrl != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                val result = withTimeoutOrNull(5000) {
                                    imageLoader.execute(
                                        ImageRequest.Builder(this@MainActivity)
                                            .data(song.thumbnailUrl)
                                            .allowHardware(false)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .networkCachePolicy(CachePolicy.ENABLED)
                                            .crossfade(false)
                                            .build()
                                    )
                                }
                                themeColor = result?.image?.toBitmap()?.extractThemeColor()
                                    ?: DefaultThemeColor
                            } catch (e: Exception) {
                                // Fallback to default on error
                                themeColor = DefaultThemeColor
                            }
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
                    val configuration = LocalConfiguration.current
                    val cutoutInsets = WindowInsets.displayCutout
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                    // Check onboarding status first, then whitelist sync
                    val onboardingComplete by dataStore.data.map { it[OnboardingCompleteKey] ?: false }.collectAsState(initial = false)
                    val onboardingScope = rememberCoroutineScope()
                    val syncScope = rememberCoroutineScope()

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
                            onSkip = { setSkipSplash(true) }
                        )
                        return@BoxWithConstraints
                    }

                    val navController = rememberNavController()
                    val homeViewModel: HomeViewModel = hiltViewModel()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }
                    val drawerState = rememberDrawerState(DrawerValue.Closed)

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
                    val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                    val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                    val (floatingMiniPlayerEnabled, _) = rememberPreference(FloatingMiniPlayerKey, defaultValue = true)
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

                    val shouldShowNavigationBar = false

                    val showRail = false

                    val getNavPadding: () -> Dp = remember { { 0.dp } }

                    val navigationBarHeight = 0.dp

                    val collapsedBound = remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        showRail,
                        useNewMiniPlayerDesign,
                        floatingMiniPlayerEnabled
                    ) {
                        if (floatingMiniPlayerEnabled) {
                            bottomInset +
                                (if (!showRail && shouldShowNavigationBar) getNavPadding() else 0.dp) +
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
                        floatingMiniPlayerEnabled
                    ) {
                        var bottom = bottomInset
                        if (floatingMiniPlayerEnabled && !playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
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

                    LaunchedEffect(playerConnection, floatingMiniPlayerEnabled) {
                        val player = playerConnection?.player ?: return@LaunchedEffect
                        if (floatingMiniPlayerEnabled) {
                            if (player.currentMediaItem != null && playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }

                    DisposableEffect(playerConnection, playerBottomSheetState, floatingMiniPlayerEnabled) {
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
                                        floatingMiniPlayerEnabled &&
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
                    val miniAccountFocusRequester = remember { FocusRequester() }
                    val miniHeartFocusRequester = remember { FocusRequester() }
                    val burgerFocusRequester = remember { FocusRequester() }
                    val contentFocusRequester = remember { FocusRequester() }
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
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
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
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                                    )
                                    navigationItems.fastForEachIndexed { index, screen ->
                                        val isSelected =
                                            navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
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
                                                .then(
                                                    if (index == 0) Modifier.focusRequester(drawerFocusRequester) else Modifier
                                                )
                                    )
                                }
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
                                )
                                if (isContributorSignedIn) {
                                    NavigationDrawerItem(
                                        label = { Text(stringResource(R.string.contribute)) },
                                        icon = { Icon(painterResource(R.drawable.person), null) },
                                        selected = navBackStackEntry?.destination?.route == "settings/contribute",
                                        onClick = {
                                            coroutineScope.launch { drawerState.close() }
                                            navController.navigate("settings/contribute") {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                                            .focusProperties { canFocus = drawerState.isOpen }
                                    )
                                }
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
                                        miniPlayerFocusTargets = null
                                    )

                                    Box(
                                        modifier = Modifier
                                            .background(insetBg)
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                    )
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
                                            latestVersionName
                                        )
                                    }
                                }
                            }
                        }

                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

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

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

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
                        }.onFailure {
                            reportException(it)
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
