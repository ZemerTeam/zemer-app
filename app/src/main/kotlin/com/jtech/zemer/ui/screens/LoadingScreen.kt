package com.jtech.zemer.ui.screens

import com.jtech.zemer.ui.component.OnboardingStepTitle

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
    // Override the progress source (defaults to the artist whitelist); the podcast browse passes its
    // own podcast-whitelist progress so the same overlay serves both.
    progressFlow: kotlinx.coroutines.flow.StateFlow<com.jtech.zemer.utils.WhitelistSyncProgress>? = null,
) {
    val syncUtils = com.jtech.zemer.LocalSyncUtils.current
    val progress by (progressFlow ?: syncUtils.whitelistSyncProgress).collectAsState()
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


    LaunchedEffect(Unit) {
        if (shouldStartSync) {
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    LaunchedEffect(progress.isComplete) {
        // Wait for sync to complete before finishing
        if (progress.isComplete) {
            onFinished()
        }
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

