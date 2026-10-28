package com.jtech.zemer.ui.screens

import com.jtech.zemer.ui.component.InlineErrorPanel
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingStepTitle
import com.jtech.zemer.viewmodels.restoreRetryDelayMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.jtech.zemer.R

@Composable
fun LoadingScreen(
    onFinished: () -> Unit,
    shouldStartSync: Boolean = true,
) {
    val syncUtils = com.jtech.zemer.LocalSyncUtils.current
    val progress by syncUtils.whitelistSyncProgress.collectAsState()
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_dots_blue))
    val lottieColors = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = PorterDuffColorFilter(MaterialTheme.colorScheme.primary.toArgb(), PorterDuff.Mode.SRC_ATOP),
            keyPath = arrayOf("**")
        )
    )

    val loopingState = animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = true
    )


    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (shouldStartSync) {
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    LaunchedEffect(progress.isComplete, progress.failedEmpty) {
        // Wait for sync to complete before finishing; without any artist list the app would open empty
        if (progress.isComplete && !progress.failedEmpty) {
            onFinished()
        }
    }

    // No artist list yet: block here and keep retrying with backoff (the panel's Retry tries at once). One effect for
    // the screen's lifetime: keyed on the flag, each attempt's own reset of it cancelled that attempt mid-request.
    LaunchedEffect(Unit) {
        var attempt = 0
        while (true) {
            val done = syncUtils.whitelistSyncProgress.first { it.isComplete }
            if (!done.failedEmpty) break
            delay(restoreRetryDelayMs(attempt++))
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    if (progress.failedEmpty) {
        WhitelistLoadFailed(onRetry = { scope.launch { syncUtils.syncArtistWhitelist(forceSync = true) } })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth()
        ) {
            LottieAnimation(
                composition = composition,
                progress = { loopingState.progress },
                dynamicProperties = lottieColors,
                modifier = Modifier
                    .size(320.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            OnboardingStepTitle(
                text = stringResource(R.string.setting_up_library_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WhitelistLoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingStepTitle(text = stringResource(R.string.onboarding_whitelist_load_failed))
        InlineErrorPanel(text = stringResource(R.string.onboarding_whitelist_load_failed_detail))
        OnboardingPrimaryButton(
            text = stringResource(R.string.retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun DisposableLifecycle(
    onEvent: () -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
    LaunchedEffect(lifecycleOwner) {
        onEvent()
    }

    val observer = remember {
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent()
            }
        }
    }

    DisposableEffectWithLifecycle(lifecycleOwner, observer)
}

@Composable
internal fun DisposableEffectWithLifecycle(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    observer: LifecycleEventObserver,
) {
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

