package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R

/**
 * The anonymous-sync-account warning body shared by the Content settings and Onboarding
 * sign-in dialogs, so the irreversibility wording stays identical in both places.
 */
@Composable
fun SyncAccountWarning(
    delaySeconds: Int,
    showCountdown: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.sync_account_warning_intro))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.sync_account_warning_lock),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.sync_account_warning_permanent),
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
        if (showCountdown) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.sync_account_wait_countdown,
                    delaySeconds,
                    delaySeconds,
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
