package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.constants.OfflineSubsetEnabledKey
import com.jtech.zemer.constants.OfflineSubsetPromoDismissedKey
import com.jtech.zemer.utils.rememberPreference

/**
 * One-time "search backup exists" promo above the Zemer search results — the pre-failure discovery
 * surface for EXISTING installs (new users get the onboarding step): the user must learn the backup
 * exists BEFORE the first outage, not from a failed search. Renders nothing once the feature is
 * enabled or the promo was dismissed (here, or by declining the onboarding step).
 */
@Composable
fun OfflineBackupPromoCard(
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (enabled, _) = rememberPreference(OfflineSubsetEnabledKey, defaultValue = false)
    val (dismissed, onDismissedChange) = rememberPreference(OfflineSubsetPromoDismissedKey, defaultValue = false)
    if (enabled || dismissed) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp),
        ) {
            Icon(
                painterResource(R.drawable.offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.offline_search_promo_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.offline_search_promo_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextButton(
                onClick = { onDismissedChange(true) },
                modifier = Modifier.focusBorder(),
            ) {
                Text(stringResource(R.string.offline_search_promo_dismiss))
            }
            TextButton(
                onClick = onSetUp,
                modifier = Modifier.focusBorder(),
            ) {
                Text(stringResource(R.string.offline_search_promo_action))
            }
        }
    }
}
