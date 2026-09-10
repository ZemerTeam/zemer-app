package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.LyricsEntity
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.constants.LyricsLineExtrasKey
import com.jtech.zemer.lyrics.LineExtrasLanguage
import com.jtech.zemer.ui.component.ConfirmDialog
import com.jtech.zemer.ui.component.ListPickerDialog
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.lyrics.lineExtrasLanguageText
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.viewmodels.LyricsMenuViewModel
import com.jtech.zemer.extensions.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = { edited ->
                val id = mediaMetadataProvider().id
                database.query {
                    upsert(
                        LyricsEntity(
                            id = id,
                            lyrics = edited,
                            provider = "manual",
                        ),
                    )
                }
                // A saved edit is also a submission to the Zemer queue: served to others only once a second
                // device agrees or the recording confirms it (server-side gate), never on this edit alone.
                viewModel.feedback.submitEdit(id, edited) { context.toast(R.string.lyrics_submitted) }
            },
        )
    }

    var showReportDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showReportDialog) {
        ConfirmDialog(
            text = stringResource(R.string.lyrics_report_confirm),
            onDismiss = { showReportDialog = false },
            onConfirm = {
                showReportDialog = false
                // Launched on the ViewModel's scope BEFORE dismissing: the sheet's scope dies with it.
                viewModel.feedback.reportWrong(mediaMetadataProvider().id) { context.toast(R.string.lyrics_reported) }
                onDismiss()
            },
        )
    }

    val (lineExtrasLanguage, onLineExtrasLanguageChange) = rememberEnumPreference(LyricsLineExtrasKey, defaultValue = LineExtrasLanguage.OFF)
    var showLineExtrasDialog by rememberSaveable { mutableStateOf(false) }
    if (showLineExtrasDialog) {
        ListPickerDialog(
            selectedValue = lineExtrasLanguage,
            values = LineExtrasLanguage.entries,
            valueText = { lineExtrasLanguageText(it) },
            onValueSelected = onLineExtrasLanguageChange,
            onDismiss = { showLineExtrasDialog = false },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOf(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.edit),
                        onClick = {
                            showEditDialog = true
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.cached),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.refetch),
                        onClick = {
                            onDismiss()
                            viewModel.refetchLyrics(mediaMetadataProvider())
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.warning),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.lyrics_report_wrong),
                        onClick = {
                            showReportDialog = true
                        }
                    )
                ),
                columns = 3, // three actions on one balanced row (Edit · Refetch · Report)
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }
        item {
            // The per-line translation / transliteration pick, also in Appearance settings; here so the
            // language can be flipped without leaving the pane. The picker is the settings rows' own dialog.
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        icon = { Icon(painterResource(R.drawable.language), contentDescription = null) },
                        title = { Text(stringResource(R.string.lyrics_line_extras)) },
                        description = { Text(lineExtrasLanguageText(lineExtrasLanguage)) },
                        onClick = { showLineExtrasDialog = true },
                    ),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
