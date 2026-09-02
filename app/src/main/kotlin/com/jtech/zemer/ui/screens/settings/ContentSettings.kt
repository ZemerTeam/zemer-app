package com.jtech.zemer.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.ui.component.SyncAccountWarning
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.auth.AuthState
import com.jtech.zemer.auth.UserAuthManager
import com.jtech.zemer.constants.AllowFemaleSingersKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.BlockPodcastsKey
import com.jtech.zemer.constants.AppLanguageKey
import com.jtech.zemer.constants.EnableContentFiltersKey
import com.jtech.zemer.constants.EnableLrcLibKey
import com.jtech.zemer.constants.EnableSimpMusicKey
import com.jtech.zemer.constants.EnableYouTubeLyricsKey
import com.jtech.zemer.constants.EnableZemerLyricsKey
import com.jtech.zemer.constants.LyricsProviderOrderKey
import com.jtech.zemer.constants.EnableMusixmatchKey
import com.jtech.zemer.constants.LanguageCodeToName
import com.jtech.zemer.constants.QuickPicks
import com.jtech.zemer.constants.QuickPicksKey
import com.jtech.zemer.constants.SYSTEM_DEFAULT
import com.jtech.zemer.constants.TopSize
import com.jtech.zemer.sync.SyncState
import com.jtech.zemer.sync.SyncStatus
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.EditTextPreference
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SettingsScreenTopSpacing
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.setAppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for content settings screen with sync functionality
 */
@HiltViewModel
class ContentSettingsViewModel @Inject constructor(
    val authManager: UserAuthManager,
    val webAuthManager: com.jtech.zemer.auth.WebViewGoogleAuthManager,
    private val syncService: com.jtech.zemer.sync.ContentFilterSyncService,
    private val userPreferencesRepository: com.jtech.zemer.sync.UserPreferencesRepository
) : androidx.lifecycle.ViewModel() {

    val authState = authManager.authStateFlow
    val syncState = syncService.syncState
    val syncStatus = syncService.getSyncStatusFlow()

    suspend fun signInWithGoogle(idToken: String) = authManager.signInWithGoogle(idToken)
    suspend fun signInAnonymously() = webAuthManager.signInAnonymously()
    suspend fun signOut() = authManager.signOut()
    suspend fun performManualSync() = syncService.performManualSync()
    suspend fun setSyncEnabled(enabled: Boolean) = syncService.setSyncEnabled(enabled)
    suspend fun getUserDevices() = syncService.getUserDevices()

    // Auto-restore and lock management
    suspend fun isAutoRestored() = userPreferencesRepository.isAutoRestored()
    suspend fun getRestoredEmail() = userPreferencesRepository.getRestoredEmail()
    suspend fun isLocked() = userPreferencesRepository.isLocked()
    suspend fun setLocked(locked: Boolean) = userPreferencesRepository.setLocked(locked)

    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ContentSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Auth and sync state
    val authState by viewModel.authState.collectAsState(initial = AuthState.Loading)
    val syncState by viewModel.syncState.collectAsState(initial = SyncState.IDLE)
    val syncStatus by viewModel.syncStatus.collectAsState(initial = SyncStatus.NEVER_SYNCED)

    // Auto-restore and lock state
    var isAutoRestored by remember { mutableStateOf(false) }
    var restoredEmail by remember { mutableStateOf<String?>(null) }
    var isLocked by remember { mutableStateOf(false) }

    // Load auto-restore and lock state
    LaunchedEffect(Unit) {
        isAutoRestored = viewModel.isAutoRestored()
        restoredEmail = viewModel.getRestoredEmail()
        isLocked = viewModel.isLocked()
    }

    var showSignInDialog by remember { mutableStateOf(false) }
    var signInDelaySeconds by remember { mutableStateOf(0) }

    // Determine if toggles should be disabled
    val togglesEnabled = !isLocked && !authState.isSignedIn

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Handle Google Sign-In result
            val data = result.data
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    coroutineScope.launch {
                        viewModel.signInWithGoogle(idToken)
                        showSignInDialog = false
                    }
                }
            } catch (e: ApiException) {
                showSignInDialog = false
                // Handle sign-in failure
            }
        } else {
            showSignInDialog = false
        }
    }

    // Used only before Android 13
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)

    val (enableZemerLyrics, onEnableZemerLyricsChange) = rememberPreference(key = EnableZemerLyricsKey, defaultValue = true)
    val (enableSimpMusic, onEnableSimpMusicChange) = rememberPreference(key = EnableSimpMusicKey, defaultValue = true)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableYouTubeLyrics, onEnableYouTubeLyricsChange) = rememberPreference(key = EnableYouTubeLyricsKey, defaultValue = true)
    val (enableMusixmatch, onEnableMusixmatchChange) = rememberPreference(key = EnableMusixmatchKey, defaultValue = true)
    val (lyricsProviderOrder, onLyricsProviderOrderChange) = rememberPreference(key = LyricsProviderOrderKey, defaultValue = "")
    var showProviderSelectionDialog by rememberSaveable { mutableStateOf(false) }
    var showProviderPriorityDialog by rememberSaveable { mutableStateOf(false) }
    val lyricsProviderToggles = listOf(
        LyricsProviderToggle(listOf("Zemer"), R.string.enable_zemer_lyrics, R.string.enable_zemer_lyrics_desc, enableZemerLyrics, onEnableZemerLyricsChange),
        LyricsProviderToggle(listOf("SimpMusic"), R.string.enable_simpmusic, R.string.enable_simpmusic_desc, enableSimpMusic, onEnableSimpMusicChange),
        LyricsProviderToggle(listOf("LrcLib"), R.string.enable_lrclib, R.string.enable_lrclib_desc, enableLrclib, onEnableLrclibChange),
        LyricsProviderToggle(listOf("Musixmatch"), R.string.enable_musixmatch, R.string.enable_musixmatch_desc, enableMusixmatch, onEnableMusixmatchChange),
        LyricsProviderToggle(listOf("YouTubeSubtitle", "YouTube"), R.string.enable_youtube_lyrics, R.string.enable_youtube_lyrics_desc, enableYouTubeLyrics, onEnableYouTubeLyricsChange),
    )
    if (showProviderSelectionDialog) LyricsProviderSelectionDialog(lyricsProviderToggles) { showProviderSelectionDialog = false }
    if (showProviderPriorityDialog) LyricsProviderPriorityDialog(lyricsProviderToggles, lyricsProviderOrder, onLyricsProviderOrderChange) { showProviderPriorityDialog = false }
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)
    val (enableContentFilters, onEnableContentFiltersChange) = rememberPreference(key = EnableContentFiltersKey, defaultValue = true)
    val (allowFemaleSingers, onAllowFemaleSingersChange) = rememberPreference(key = AllowFemaleSingersKey, defaultValue = false)
    val (blockVideos, onBlockVideosChange) = rememberPreference(key = BlockVideosKey, defaultValue = false)
    val (blockPodcasts, onBlockPodcastsChange) = rememberPreference(key = BlockPodcastsKey, defaultValue = false)

    // Update ContentFilterState when preferences change
    LaunchedEffect(enableContentFilters, allowFemaleSingers, blockVideos, blockPodcasts) {
        ContentFilterState.updateContentFilters(
            filtersEnabled = enableContentFilters,
            allowFemaleSingers = allowFemaleSingers,
            blockVideos = blockVideos,
            blockPodcasts = blockPodcasts
        )
    }

    // Handle countdown for sign-in delay
    LaunchedEffect(showSignInDialog) {
        if (showSignInDialog) {
            signInDelaySeconds = 5
            for (i in 5 downTo 1) {
                delay(1000)
                signInDelaySeconds = i - 1
            }
        } else {
            signInDelaySeconds = 0
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(SettingsScreenTopSpacing))
        SettingsCardGroup(
            title = stringResource(R.string.app_language),
            rows = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.app_language)) },
                            icon = { Icon(painterResource(R.drawable.language), null) },
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APP_LOCALE_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                )
                            }
                        )
                    }
                }
                // Support for Android versions before Android 13
                else {
                    add {
                        ListPreference(
                            title = { Text(stringResource(R.string.app_language)) },
                            icon = { Icon(painterResource(R.drawable.language), null) },
                            selectedValue = appLanguage,
                            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                            valueText = {
                                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                            },
                            onValueSelected = { langTag ->
                                val newLocale = langTag
                                    .takeUnless { it == SYSTEM_DEFAULT }
                                    ?.let { Locale.forLanguageTag(it) }
                                    ?: Locale.getDefault()

                                onAppLanguageChange(langTag)
                                setAppLocale(context, newLocale)

                            }
                        )
                    }
                }
            },
        )

        SettingsCardGroup(
            title = stringResource(R.string.lyrics),
            rows = listOf(
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.lyrics_provider_selection)) },
                        description = stringResource(R.string.lyrics_provider_selection_desc),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        onClick = { showProviderSelectionDialog = true },
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.lyrics_provider_priority)) },
                        description = stringResource(R.string.lyrics_provider_priority_desc),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        onClick = { showProviderPriorityDialog = true },
                    )
                },
            ),
        )

        PreferenceGroupTitle(title = stringResource(R.string.content_filters))

        // Sync status card
        SyncStatusCard(
            authState = authState,
            syncState = syncState,
            syncStatus = syncStatus,
            isAutoRestored = isAutoRestored,
            restoredEmail = restoredEmail,
            onSignInClick = { showSignInDialog = true },
            onLockClick = { },
            onUnlockClick = { },
            isLocked = isLocked
        )

        SettingsCardGroup(
            rows = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_personal_filters)) },
                        icon = { Icon(painterResource(R.drawable.settings), null) },
                        checked = true, // Always shown as ON
                        onCheckedChange = { }, // No-op, cannot be changed
                        isEnabled = false // Always greyed out
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.allow_female_singers)) },
                        icon = { Icon(painterResource(R.drawable.person), null) },
                        checked = allowFemaleSingers,
                        onCheckedChange = onAllowFemaleSingersChange,
                        isEnabled = enableContentFilters && togglesEnabled
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.block_videos)) },
                        icon = { Icon(painterResource(R.drawable.ic_video_hd), null) },
                        checked = blockVideos,
                        onCheckedChange = onBlockVideosChange,
                        isEnabled = enableContentFilters && togglesEnabled
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.block_podcasts)) },
                        icon = { Icon(painterResource(R.drawable.podcast), null) },
                        checked = blockPodcasts,
                        onCheckedChange = onBlockPodcastsChange,
                        isEnabled = enableContentFilters && togglesEnabled
                    )
                },
            ),
        )

        SettingsCardGroup(
            title = stringResource(R.string.misc),
            rows = listOf(
                {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.top_length)) },
                        icon = { Icon(painterResource(R.drawable.trending_up), null) },
                        value = lengthTop,
                        isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                        onValueChange = onLengthTopChange,
                    )
                },
                {
                    ListPreference(
                        title = { Text(stringResource(R.string.set_quick_picks)) },
                        icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                        selectedValue = quickPicks,
                        values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN),
                        valueText = {
                            when (it) {
                                QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                                QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                            }
                        },
                        onValueSelected = onQuickPicksChange,)
                },
            ),
        )
    }

    // Create account dialog
    if (showSignInDialog) {
        var isLoading by remember { mutableStateOf(false) }

        DefaultDialog(
            onDismiss = { if (!isLoading) showSignInDialog = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.sync_account_create_title)) },
            content = {
                if (isLoading) {
                    Text(stringResource(R.string.sync_account_creating))
                } else {
                    SyncAccountWarning(
                        delaySeconds = signInDelaySeconds,
                        showCountdown = signInDelaySeconds > 0,
                    )
                }
            },
            buttons = {
                if (!isLoading) {
                    TextButton(
                        onClick = {
                            showSignInDialog = false
                            signInDelaySeconds = 0
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }

                Button(
                    onClick = {
                        if (signInDelaySeconds == 0) {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val result = viewModel.webAuthManager.signInAnonymously()
                                    if (result.isSuccess) {
                                        // Lock settings after successful authentication
                                        viewModel.setLocked(true)
                                        isLocked = true
                                        // Enable sync and perform initial sync
                                        viewModel.setSyncEnabled(true)
                                        viewModel.performManualSync()
                                    }
                                } catch (e: Exception) {
                                    reportException(e, "ContentSettings sync sign-in")
                                } finally {
                                    isLoading = false
                                    showSignInDialog = false
                                    signInDelaySeconds = 0
                                }
                            }
                        }
                    },
                    enabled = !isLoading && signInDelaySeconds == 0
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            stringResource(
                                if (signInDelaySeconds == 0) R.string.sync_account_create_and_lock
                                else R.string.sync_account_please_wait
                            )
                        )
                    }
                }
            }
        )
    }

    BackTopAppBar(
        title = { AppBarTitle(stringResource(R.string.content)) },
        navController = navController,
    )
}

@Composable
private fun SyncStatusCard(
    authState: AuthState,
    syncState: SyncState,
    syncStatus: SyncStatus,
    isAutoRestored: Boolean,
    restoredEmail: String?,
    onSignInClick: () -> Unit,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    isLocked: Boolean
) {
      Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        syncState.isSyncing -> MaterialTheme.colorScheme.primary
                        syncState.isError -> MaterialTheme.colorScheme.error
                        authState.isSignedIn -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.content_filter_sync),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                when {
                    syncState.isSyncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    authState.isSignedIn -> {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.signed_in),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = stringResource(R.string.not_signed_in),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isAutoRestored && restoredEmail != null -> {
                    // Auto-restored from server without sign-in
                    Text(
                        text = stringResource(R.string.content_filters_restored),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Show restored account email
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.restored_from, restoredEmail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Show lock button only if not locked
                    if (!isLocked) {
                        OutlinedButton(
                            onClick = onLockClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.lock_open),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.lock_settings))
                        }
                    }
                }
                authState.isSignedIn -> {
                    // User is signed in
                    val statusText = when (syncStatus) {
                        is SyncStatus.SYNCED -> {
                            "Last synced: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(syncStatus.lastSyncTime))}"
                        }
                        SyncStatus.NEVER_SYNCED -> "Never synced"
                        SyncStatus.DISABLED -> "Sync disabled"
                        else -> "Ready to sync"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Show logged-in account info
                    val userInfo = when (authState) {
                        is AuthState.SignedIn -> Pair(
                            authState.email,
                            authState.userId
                        )
                        else -> null
                    }

                    if (userInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Show user ID
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.security),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.user_id_format, userInfo.second),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                else -> {
                    // User is not signed in and not auto-restored
                    Text(
                        text = stringResource(R.string.sync_account_create_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // If not locked, show create account button
                    if (!isLocked) {
                        Button(
                            onClick = onSignInClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.lock),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.create_account_to_lock))
                        }
                    }
                    // If locked, show no buttons - settings are permanently locked
                }
            }
        }
    }
}
