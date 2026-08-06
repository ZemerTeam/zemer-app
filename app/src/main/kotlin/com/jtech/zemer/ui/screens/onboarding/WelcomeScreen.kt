package com.jtech.zemer.ui.screens.onboarding

import com.jtech.zemer.ui.component.AppNameTitle
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.jtech.zemer.R
import com.jtech.zemer.extensions.isInternetConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.OnboardingPrimaryButton

private enum class LegalKind { TOS, PRIVACY }

@Composable
internal fun WelcomeScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var legal by remember { mutableStateOf<LegalKind?>(null) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var isCheckingNetwork by remember { mutableStateOf(true) }

    // Initial and periodic network checks
    LaunchedEffect(Unit) {
        // Initial check
        isConnected = withContext(Dispatchers.IO) {
            context.isInternetConnected()
        }
        isCheckingNetwork = false

        // Continue checking every 2 seconds
        while (true) {
            delay(2000)
            val newConnectionState = withContext(Dispatchers.IO) {
                context.isInternetConnected()
            }
            if (newConnectionState != isConnected) {
                isConnected = newConnectionState
            }
        }
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.welcome))
    val animationState = animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        clipSpec = LottieClipSpec.Progress(0f, 0.5f),
        restartOnPlay = false
    )
    val lottieColors = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = PorterDuffColorFilter(MaterialTheme.colorScheme.primary.toArgb(), PorterDuff.Mode.SRC_ATOP),
            keyPath = arrayOf("**")
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight()
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { animationState.progress },
                    dynamicProperties = lottieColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
                AppNameTitle(textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreed = !agreed }
                ) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_agree_label),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_view_tos),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { legal = LegalKind.TOS }
                            )
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = stringResource(R.string.onboarding_view_privacy),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { legal = LegalKind.PRIVACY }
                            )
                        }
                    }
                }

                OnboardingPrimaryButton(
                    text = if (!isConnected) stringResource(R.string.network_internet_required)
                          else stringResource(R.string.onboarding_continue),
                    onClick = onContinue,
                    enabled = agreed && isConnected && !isCheckingNetwork,
                    loading = isCheckingNetwork,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isConnected || isCheckingNetwork) {
                    Text(
                        text = when {
                            isCheckingNetwork -> stringResource(R.string.network_checking_internet)
                            !isConnected -> stringResource(R.string.network_required_setup)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        legal?.let { kind ->
            LegalOverlay(
                title = stringResource(
                    if (kind == LegalKind.TOS) R.string.onboarding_tos_title else R.string.onboarding_privacy_title
                ),
                body = stringResource(
                    // The telemetry variant additionally discloses the anonymous usage tracking —
                    // the older strings falsely claim "no usage analytics" once feat/track ships.
                    if (kind == LegalKind.TOS) R.string.onboarding_tos_body else R.string.onboarding_privacy_body_telemetry
                ),
                onDismiss = { legal = null }
            )
        }
    }
}


@Composable
private fun LegalOverlay(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = { Text(title) },
        buttons = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.ok),
                onClick = onDismiss,
            )
        },
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

