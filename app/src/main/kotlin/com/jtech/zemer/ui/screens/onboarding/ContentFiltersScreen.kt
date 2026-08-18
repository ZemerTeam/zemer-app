package com.jtech.zemer.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jtech.zemer.viewmodels.OnboardingViewModel
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.SyncAccountWarning
import com.jtech.zemer.ui.component.ZemerLoadingIndicator
import com.jtech.zemer.ui.component.DefaultDialog
import androidx.datastore.core.DataStore
import com.jtech.zemer.constants.EnableContentFiltersKey
import com.jtech.zemer.constants.AllowFemaleSingersKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.BlockPodcastsKey
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.launch
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.ColumnScope
import com.jtech.zemer.ui.component.onboardingCardColors
import com.jtech.zemer.ui.component.OnboardingInfoCard
import com.jtech.zemer.ui.component.OnboardingStatusPill
import com.jtech.zemer.ui.component.OnboardingStepHeader
import com.jtech.zemer.ui.component.OnboardingStepTitle
import com.jtech.zemer.ui.component.OnboardingActionButton
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingTextButton

@Composable
internal fun ContentFiltersScreen(
    onBack: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
    contentFiltersAlreadySet: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val authState by viewModel.authState.collectAsState(initial = com.jtech.zemer.auth.AuthState.SignedOut)
    val (isConnected, isCheckingNetwork) = rememberOnboardingConnectivity()

    // Content filter states (using rememberPreference to auto-save to DataStore)
    val (enableContentFilters, onEnableContentFiltersChange) = rememberPreference(key = EnableContentFiltersKey, defaultValue = true)
    val (allowFemaleSingers, onAllowFemaleSingersChange) = rememberPreference(key = AllowFemaleSingersKey, defaultValue = false)
    val (blockVideos, onBlockVideosChange) = rememberPreference(key = BlockVideosKey, defaultValue = false)
    val (blockPodcasts, onBlockPodcastsChange) = rememberPreference(key = BlockPodcastsKey, defaultValue = false)
    var signInDelaySeconds by remember { mutableStateOf(0) }
    var showSignInDialog by remember { mutableStateOf(false) }

    // Google Sign-In launcher (matching ContentSettings implementation)
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Handle Google Sign-In result
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    scope.launch {
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

    // Check for auto-restore and skip logic
    LaunchedEffect(Unit) {
        // Check if we should skip (already configured)
        if (contentFiltersAlreadySet) {
            onSkip()
            return@LaunchedEffect
        }

        // Attempt auto-restore only if user not signed in
        if (authState !is com.jtech.zemer.auth.AuthState.SignedIn) {
            viewModel.attemptAutoRestore()
        }
    }

    // Update UI state when server preferences are found
    LaunchedEffect(uiState.restoredConfig) {
        uiState.restoredConfig?.let { config ->
            // Note: rememberPreference will automatically load the restored values
            // No need to manually set them here

            // Auto-proceed after showing restore UI for 3 seconds
            kotlinx.coroutines.delay(3000)
            onSkip()
        }
    }

    // Handle countdown for sign-in delay
    LaunchedEffect(showSignInDialog) {
        if (showSignInDialog) {
            signInDelaySeconds = 5
            for (i in 5 downTo 1) {
                kotlinx.coroutines.delay(1000)
                signInDelaySeconds = i - 1
            }
        } else {
            signInDelaySeconds = 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            if (uiState.isCheckingAutoRestore) {
                // Loading state while checking for auto-restore
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OnboardingStepTitle(
                        text = stringResource(R.string.onboarding_restoring_filters),
                    )
                    ZemerLoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_checking_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (uiState.hasServerPreferences && uiState.restoredConfig != null) {
                // Auto-restore screen showing what's being restored
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OnboardingStepTitle(
                        text = stringResource(R.string.onboarding_restoring_filters),
                    )

                    Text(
                        text = stringResource(R.string.onboarding_found_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Show what's being restored. Neutral surfaceContainer fill (active = false) so it
                    // matches the other onboarding cards (permissions, sign-in) instead of the brand-tinted
                    // secondaryContainer, which reads as off-theme against the AMOLED background.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = onboardingCardColors(active = false),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val config = uiState.restoredConfig
                            Text(
                                text = stringResource(R.string.onboarding_restored_settings),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = stringResource(R.string.onboarding_restored_filters_enabled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = stringResource(R.string.onboarding_restored_female, stringResource(if (config?.allowFemaleSingers == true) R.string.allowed else R.string.blocked)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = stringResource(R.string.onboarding_restored_videos, stringResource(if (config?.blockVideos == true) R.string.blocked else R.string.allowed)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = stringResource(R.string.onboarding_restored_podcasts, stringResource(if (config?.blockPodcasts == true) R.string.blocked else R.string.allowed)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.onboarding_continuing_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Normal content filter setup screen
                OnboardingStepHeader(
                    title = stringResource(R.string.content_filters),
                    subtitle = stringResource(R.string.onboarding_filters_subtitle),
                )

                // Allow Female Singers toggle
                FilterOptionCard(
                    title = stringResource(R.string.onboarding_allow_female_title),
                    description = stringResource(R.string.onboarding_allow_female_desc),
                    isEnabled = allowFemaleSingers,
                    onToggle = { onAllowFemaleSingersChange(it) },
                    icon = R.drawable.person
                )

                // Block Videos toggle
                FilterOptionCard(
                    title = stringResource(R.string.onboarding_block_videos_title),
                    description = stringResource(R.string.onboarding_block_videos_desc),
                    isEnabled = blockVideos,
                    onToggle = { onBlockVideosChange(it) },
                    icon = R.drawable.ic_video_hd
                )

                // Block Podcasts toggle
                FilterOptionCard(
                    title = stringResource(R.string.onboarding_block_podcasts_title),
                    description = stringResource(R.string.onboarding_block_podcasts_desc),
                    isEnabled = blockPodcasts,
                    onToggle = { onBlockPodcastsChange(it) },
                    icon = R.drawable.podcast
                )

                // Sign-in status card
                val signedIn = authState is com.jtech.zemer.auth.AuthState.SignedIn
                val createAction: (@Composable ColumnScope.() -> Unit)? = if (signedIn) {
                    null
                } else {
                    {
                        OnboardingActionButton(
                            text = stringResource(R.string.sync_account_create_title),
                            onClick = { showSignInDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                OnboardingInfoCard(
                    active = signedIn,
                    title = if (signedIn) stringResource(R.string.sync_account_created)
                    else stringResource(R.string.sync_account_optional_create),
                    description = if (signedIn) stringResource(R.string.sync_account_locked_backed_up)
                    else stringResource(R.string.sync_account_connect_to_lock),
                    trailing = {
                        OnboardingStatusPill(
                            text = if (signedIn) stringResource(R.string.sync_account_active)
                            else stringResource(R.string.sync_account_optional),
                            active = signedIn,
                        )
                    },
                    action = createAction,
                )

                // Action buttons (vertical stack, matching the other onboarding steps)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OnboardingPrimaryButton(
                        text = if (isCheckingNetwork) stringResource(R.string.network_checking)
                             else if (!isConnected) stringResource(R.string.network_internet_required)
                             else stringResource(R.string.onboarding_continue),
                        onClick = { onSkip() },
                        enabled = isConnected && !isCheckingNetwork,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OnboardingTextButton(
                        text = stringResource(R.string.onboarding_back),
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Sign-in dialog (matching ContentSettings dialog)
        if (showSignInDialog) {
            DefaultDialog(
                onDismiss = {
                    showSignInDialog = false
                    signInDelaySeconds = 0
                },
                horizontalAlignment = Alignment.Start,
                title = { Text(stringResource(R.string.sync_account_important_title)) },
                content = {
                    SyncAccountWarning(
                        delaySeconds = signInDelaySeconds,
                        showCountdown = signInDelaySeconds > 0,
                    )
                },
                buttons = {
                    OnboardingTextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = {
                            showSignInDialog = false
                            signInDelaySeconds = 0
                        },
                    )

                    OnboardingPrimaryButton(
                        text = stringResource(
                            if (signInDelaySeconds == 0) R.string.sync_account_create
                            else R.string.sync_account_please_wait
                        ),
                        onClick = {
                            if (signInDelaySeconds == 0) {
                                // Create anonymous account directly
                                scope.launch {
                                    val result = viewModel.webAuthManager.signInAnonymously()
                                    if (result.isSuccess) {
                                        // Sync preferences after successful authentication
                                        viewModel.signInAnonymously()
                                    }
                                }
                                showSignInDialog = false
                                signInDelaySeconds = 0
                            }
                        },
                        enabled = signInDelaySeconds == 0,
                    )
                }
            )
        }
    }
}

@Composable
private fun FilterOptionCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: Int
) {
    OnboardingInfoCard(
        active = isEnabled,
        title = title,
        description = description,
        leadingIcon = icon,
        trailing = {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        },
    )
}

