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
import com.jtech.zemer.constants.AcceptedPrivacyDocKey
import com.jtech.zemer.constants.AcceptedTermsDocKey
import com.jtech.zemer.constants.OnboardingCompleteKey
import com.jtech.zemer.legal.LegalAgreement
import com.jtech.zemer.legal.LegalRepository
import com.jtech.zemer.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The legal update gate: when the privacy policy or terms ([LegalRepository]) changed since this install agreed
 * ([LegalAgreement]), it shows the changed documents full screen and the caller renders nothing else, so the app
 * cannot be used. Agree is the only way out (back and outside taps do nothing) and stores the versions and time.
 * Returns true while it blocks, including the moment before the stored agreement and the documents have loaded
 * (nothing renders then). A document that changes while the app is open blocks it at once.
 */
@Composable
fun privacyNoticeBlocking(): Boolean {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // only the three values the gate needs (equal values never recompose the caller, which is the whole app)
    val agreed by remember {
        context.dataStore.data.map {
            LegalAgreement.Agreed(it[OnboardingCompleteKey] == true, it[AcceptedPrivacyDocKey] ?: 0, it[AcceptedTermsDocKey] ?: 0)
        }
    }.collectAsState(initial = null)
    val docs by LegalRepository.docs.collectAsState()
    val pending = LegalAgreement.pending(agreed ?: return true, docs ?: return true)
    val current = docs ?: return true
    if (pending.isEmpty()) return false
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
    LegalOverlay(
        title = stringResource(R.string.privacy_update_title),
        markdown = stringResource(R.string.privacy_update_intro) + "\n\n" +
            pending.joinToString("\n\n") { "# ${it.title}\n\n${it.body}" },
        confirmText = stringResource(R.string.privacy_update_agree),
        dismissible = false,
        onConfirm = { scope.launch { context.dataStore.edit { LegalAgreement.record(it, current) } } },
    )
    return true
}
