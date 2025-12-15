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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.auth.AuthState
import com.jtech.zemer.auth.UserAuthManager
import com.jtech.zemer.constants.AllowChasidishKey
import com.jtech.zemer.constants.AllowFemaleSingersKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.AppLanguageKey
import com.jtech.zemer.constants.ContentCountryKey
import com.jtech.zemer.constants.ContentLanguageKey
import com.jtech.zemer.constants.CountryCodeToName
import com.jtech.zemer.constants.EnableContentFiltersKey
import com.jtech.zemer.constants.EnableLrcLibKey
import com.jtech.zemer.constants.LanguageCodeToName
import com.jtech.zemer.constants.QuickPicks
import com.jtech.zemer.constants.QuickPicksKey
import com.jtech.zemer.constants.SYSTEM_DEFAULT
import com.jtech.zemer.constants.TopSize
import com.jtech.zemer.sync.SyncState
import com.jtech.zemer.sync.SyncStatus
import com.jtech.zemer.ui.component.EditTextPreference
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.ListPreference
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.setAppLocale
import com.metrolist.innertube.YouTube
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
    private val syncService: com.jtech.zemer.sync.ContentFilterSyncService,
    private val userPreferencesRepository: com.jtech.zemer.sync.UserPreferencesRepository
) : androidx.lifecycle.ViewModel() {

    val authState = authManager.authStateFlow
    val syncState = syncService.syncState
    val syncStatus = syncService.getSyncStatusFlow()

    suspend fun signInWithGoogle(idToken: String) = authManager.signInWithGoogle(idToken)
    suspend fun signOut() = authManager.signOut()
    suspend fun performManualSync() = syncService.performManualSync()
    suspend fun setSyncEnabled(enabled: Boolean) = syncService.setSyncEnabled(enabled)
    suspend fun getUserDevices() = syncService.getUserDevices()

    // Auto-restore and lock management
    suspend fun isAutoRestored() = userPreferencesRepository.isAutoRestored()
    suspend fun getRestoredEmail() = userPreferencesRepository.getRestoredEmail()
    suspend fun isLocked() = userPreferencesRepository.isLocked()
    suspend fun setLocked(locked: Boolean) = userPreferencesRepository.setLocked(locked)

    fun formatLastSyncTime(timestamp: Long): String {
        return if (timestamp > 0) {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } else {
            "Never"
        }
    }
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
    var showSyncErrorDialog by remember { mutableStateOf(false) }

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

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)
    val (enableContentFilters, onEnableContentFiltersChange) = rememberPreference(key = EnableContentFiltersKey, defaultValue = true)
    val (allowFemaleSingers, onAllowFemaleSingersChange) = rememberPreference(key = AllowFemaleSingersKey, defaultValue = false)
    val (allowChasidish, onAllowChasidishChange) = rememberPreference(key = AllowChasidishKey, defaultValue = false)
    val (blockVideos, onBlockVideosChange) = rememberPreference(key = BlockVideosKey, defaultValue = false)

    // Update ContentFilterState when preferences change (excluding chasidish since it's for recommendations only)
    LaunchedEffect(enableContentFilters, allowFemaleSingers, blockVideos) {
        ContentFilterState.updateContentFilters(
            filtersEnabled = enableContentFilters,
            allowFemaleSingers = allowFemaleSingers,
            blockVideos = blockVideos
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(title = stringResource(R.string.general))
        ListPreference(
            title = { Text(stringResource(R.string.content_language)) },
            icon = { Icon(painterResource(R.drawable.language), null) },
            selectedValue = contentLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()
                val languageTag = locale.toLanguageTag().replace("-Hant", "")
 
                YouTube.locale = YouTube.locale.copy(
                    hl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.language.takeIf { it in LanguageCodeToName }
                        ?: languageTag.takeIf { it in LanguageCodeToName }
                        ?: "en"
                )
 
                onContentLanguageChange(newValue)
            }
        )
        ListPreference(
            title = { Text(stringResource(R.string.content_country)) },
            icon = { Icon(painterResource(R.drawable.location_on), null) },
            selectedValue = contentCountry,
            values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()
 
                YouTube.locale = YouTube.locale.copy(
                    gl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.country.takeIf { it in CountryCodeToName }
                        ?: "US"
                )
 
                onContentCountryChange(newValue)
           }
        )

        PreferenceGroupTitle(title = stringResource(R.string.app_language))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        // Support for Android versions before Android 13
        else {
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

        PreferenceGroupTitle(title = stringResource(R.string.lyrics))
        SwitchPreference(
            title = { Text(stringResource(R.string.enable_lrclib)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = enableLrclib,
            onCheckedChange = onEnableLrclibChange,
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
            onSyncClick = {
                coroutineScope.launch {
                    try {
                        viewModel.performManualSync()
                    } catch (e: Exception) {
                        showSyncErrorDialog = true
                    }
                }
            },
            onLockClick = {
                if (authState.isSignedIn) {
                    coroutineScope.launch {
                        viewModel.setLocked(true)
                        isLocked = true
                    }
                } else {
                    // Show sign-in dialog if user tries to lock without being signed in
                    showSignInDialog = true
                }
            },
            onUnlockClick = {
                // No unlock functionality - once locked, always locked
            },
            isLocked = isLocked
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_personal_filters)) },
            icon = { Icon(painterResource(R.drawable.settings), null) },
            checked = enableContentFilters,
            onCheckedChange = onEnableContentFiltersChange,
            isEnabled = togglesEnabled
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.allow_female_singers)) },
            icon = { Icon(painterResource(R.drawable.person), null) },
            checked = allowFemaleSingers,
            onCheckedChange = onAllowFemaleSingersChange,
            isEnabled = enableContentFilters && togglesEnabled
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.block_videos)) },
            icon = { Icon(painterResource(R.drawable.ic_video_hd), null) },
            checked = blockVideos,
            onCheckedChange = onBlockVideosChange,
            isEnabled = enableContentFilters && togglesEnabled
        )

        PreferenceGroupTitle(title = "Recommendations")
        SwitchPreference(
            title = { Text(stringResource(R.string.i_am_chasidish)) },
            icon = { Icon(painterResource(R.drawable.person), null) },
            checked = allowChasidish,
            onCheckedChange = onAllowChasidishChange,
            isEnabled = true // Chasidish is not locked, it's for recommendations only
        )
        PreferenceGroupTitle(title = stringResource(R.string.misc))
        EditTextPreference(
            title = { Text(stringResource(R.string.top_length)) },
            icon = { Icon(painterResource(R.drawable.trending_up), null) },
            value = lengthTop,
            isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
            onValueChange = onLengthTopChange,
        )
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
    }

    // Sign-in dialog
    if (showSignInDialog) {
        AlertDialog(
            onDismissRequest = { showSignInDialog = false },
            title = { Text("Sign In Required") },
            text = { Text("Sign in with your Google account to save your locked content filter settings. This ensures your preferences are backed up and can be restored after app reinstallation.") },
            confirmButton = {
                Button(
                    onClick = {
                        val signInIntent = viewModel.authManager.googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                        showSignInDialog = false
                    }
                ) {
                    Text("Sign In with Google")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignInDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sync error dialog
    if (showSyncErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSyncErrorDialog = false },
            title = { Text("Sync Error") },
            text = {
                Text(
                    "Failed to sync preferences. Please check your internet connection and try again.\n\n" +
                    "Error: ${syncState.errorMessage ?: "Unknown error"}"
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSyncErrorDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
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
    onSyncClick: () -> Unit,
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
                    text = "Content Filter Sync",
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
                            contentDescription = "Signed in",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = "Not signed in",
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
                        text = "Content filter preferences have been automatically restored from your account.",
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
                            text = "Restored from: $restoredEmail",
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
                            Text("Lock Settings")
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

                    // Show logged-in account
                    val userEmail = when (authState) {
                        is AuthState.SignedIn -> authState.email
                        else -> null
                    }

                    if (userEmail != null) {
                        Spacer(modifier = Modifier.height(8.dp))
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
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSyncClick,
                            enabled = syncState.isIdle && !syncState.isError,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (syncState.isError) {
                                Text("Retry")
                            } else {
                                Text("Sync Now")
                            }
                        }
                    }
                }
                else -> {
                    // User is not signed in and not auto-restored
                    Text(
                        text = "Sign in to sync your content filter preferences and restore them after app reinstallation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // If not locked, show sign in and lock buttons
                    if (!isLocked) {
                        Button(
                            onClick = onSignInClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with Google")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                            Text("Lock Settings")
                        }
                    }
                    // If locked, show no buttons - settings are permanently locked
                }
            }
        }
    }
}
