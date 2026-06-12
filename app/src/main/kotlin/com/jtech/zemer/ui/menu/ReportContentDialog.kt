package com.jtech.zemer.ui.menu

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.viewmodels.ReportContentViewModel
import kotlinx.coroutines.launch

/**
 * Shared "report content" dialog used by every menu (song, album, artist, playlist).
 * [subject] carries the item-identifying fields (e.g. "songId"/"songTitle"); submission
 * goes through [ReportContentViewModel] -> ContentReportRepository.
 */
@Composable
fun ReportContentDialog(
    subject: Map<String, Any?>,
    onDismiss: () -> Unit,
    viewModel: ReportContentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    val reasons = listOf(
        "female" to stringResource(R.string.report_reason_female),
        "gentile" to stringResource(R.string.report_reason_gentile),
        "bad_playlists" to stringResource(R.string.report_reason_bad_playlists),
        "bad_images" to stringResource(R.string.report_reason_bad_images),
        "other" to stringResource(R.string.report_reason_other),
    )

    DefaultDialog(
        onDismiss = { if (!isSubmitting) onDismiss() },
        horizontalAlignment = Alignment.Start,
        title = { Text(stringResource(R.string.report_artist)) },
        content = {
            reasons.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = value }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedReason == value,
                        onClick = { selectedReason = value },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.report_optional_comment)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text(stringResource(R.string.report_cancel))
            }

            Button(
                onClick = {
                    if (selectedReason.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.report_choose_reason), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        isSubmitting = true
                        val success = viewModel.submit(subject, selectedReason, comment)
                        isSubmitting = false
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.report_success), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, context.getString(R.string.report_failure), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSubmitting,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.report_submit))
                }
            }
        },
    )
}
