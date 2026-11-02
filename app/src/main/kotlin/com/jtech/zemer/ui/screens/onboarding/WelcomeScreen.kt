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
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import com.jtech.zemer.legal.LegalAgreement
import com.jtech.zemer.legal.LegalRepository
import com.jtech.zemer.utils.dataStore
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.MarkdownText
import com.jtech.zemer.ui.component.OnboardingPrimaryButton

private enum class LegalKind { TOS, PRIVACY }

@Composable
internal fun WelcomeScreen(
    onContinue: () -> Unit,
) {
    var legal by remember { mutableStateOf<LegalKind?>(null) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val (isConnected, isCheckingNetwork) = rememberOnboardingConnectivity()
    val docs by LegalRepository.docs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // the checkbox agrees to the documents this screen offers: record exactly those, at Continue (a newer
    // version that lands later is asked for by the update notice)
    val agreeAndContinue: () -> Unit = {
        val shown = docs
        scope.launch {
            if (shown != null) context.dataStore.edit { LegalAgreement.record(it, shown) }
            onContinue()
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
                        .padding(horizontal = 8.dp)
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
                    onClick = agreeAndContinue,
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
            val doc = docs?.let { if (kind == LegalKind.TOS) it.terms else it.privacy } ?: return@let
            LegalOverlay(title = doc.title, markdown = doc.body, onConfirm = { legal = null })
        }
    }
}


/**
 * A legal text ([markdown], from [LegalRepository]) in a scrolling dialog with one button. [dismissible] = false
 * keeps it up until [onConfirm] (the update notice: back and outside taps do nothing).
 */
@Composable
internal fun LegalOverlay(
    title: String,
    markdown: String,
    onConfirm: () -> Unit,
    confirmText: String = stringResource(R.string.ok),
    dismissible: Boolean = true,
) {
    DefaultDialog(
        onDismiss = { if (dismissible) onConfirm() },
        horizontalAlignment = Alignment.Start,
        title = { Text(title) },
        buttons = {
            OnboardingPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
            )
        },
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            MarkdownText(markdown = markdown, modifier = Modifier.verticalScroll(rememberScrollState()))
        }
    }
}

