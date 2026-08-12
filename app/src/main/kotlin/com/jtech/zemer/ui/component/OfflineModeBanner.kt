package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.constants.OfflineModeKey
import com.jtech.zemer.utils.rememberPreference

/**
 * The manual offline-mode indicator (#366): a compact banner explaining why every surface shrank to
 * downloaded content, with the one-tap exit. Self-contained (reads/writes [OfflineModeKey] itself)
 * and renders NOTHING when the mode is off, so a call site may compose it unconditionally — but
 * lazy-list hosts should still gate the item on the mode to avoid an empty list slot.
 */
@Composable
fun OfflineModeBanner(modifier: Modifier = Modifier) {
    val (offlineMode, setOfflineMode) = rememberPreference(OfflineModeKey, defaultValue = false)
    if (!offlineMode) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.offline_mode_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
            TextButton(
                onClick = { setOfflineMode(false) },
                modifier = Modifier.focusBorder(),
            ) {
                Text(stringResource(R.string.offline_mode_turn_off))
            }
        }
    }
}
