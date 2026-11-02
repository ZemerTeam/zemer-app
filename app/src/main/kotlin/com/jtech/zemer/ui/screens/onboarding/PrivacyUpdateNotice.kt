package com.jtech.zemer.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.R
import com.jtech.zemer.constants.AcceptedPrivacyVersionKey
import com.jtech.zemer.constants.OnboardingCompleteKey
import com.jtech.zemer.tracking.PrivacyNotice
import com.jtech.zemer.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one-time "privacy policy updated" gate for an install that finished onboarding under an older policy
 * ([PrivacyNotice]). While it shows, the caller renders nothing else, so the app cannot be used; Agree is the
 * only way out (back and outside taps do nothing) and stores the version and time agreed. Returns true while
 * it blocks.
 */
@Composable
fun privacyNoticeBlocking(): Boolean {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val show by remember {
        context.dataStore.data.map {
            PrivacyNotice.shouldShow(it[OnboardingCompleteKey] == true, it[AcceptedPrivacyVersionKey] ?: 0)
        }
    }.collectAsState(initial = false)
    if (!show) return false
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
    LegalOverlay(
        title = stringResource(R.string.privacy_update_title),
        body = stringResource(R.string.privacy_update_intro) + "\n\n" +
            stringResource(R.string.onboarding_privacy_body_telemetry),
        confirmText = stringResource(R.string.privacy_update_agree),
        dismissible = false,
        onConfirm = { scope.launch { context.dataStore.edit(PrivacyNotice::recordAgreement) } },
    )
    return true
}
