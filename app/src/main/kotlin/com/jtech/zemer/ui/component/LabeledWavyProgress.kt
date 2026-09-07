package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R

/**
 * The ONE labelled expressive progress block: a label row (with the percent at the trailing edge
 * when [progress] is known), the wavy bar (determinate when [progress] is non-null, indeterminate
 * otherwise), and an optional [detail] caption ("3.2 MB of 9.9 MB", an installer note). Used by
 * the update dialog's downloading + installing states; any new "work in progress" row should
 * render through it rather than hand-rolling the label + bar + caption stack.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LabeledWavyProgress(
    label: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            if (progress != null) {
                Text(
                    text = stringResource(R.string.percent_value, (progress * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (progress != null) {
            LinearWavyProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
