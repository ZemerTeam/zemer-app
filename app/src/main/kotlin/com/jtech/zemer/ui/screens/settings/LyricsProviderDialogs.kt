package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.lyrics.LyricsProviderOrdering
import com.jtech.zemer.lyrics.musixmatch.MusixmatchStatus
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.PreferenceEntryDefaults
import com.jtech.zemer.ui.component.ReorderableEntry
import com.jtech.zemer.ui.component.ReorderableList
import com.jtech.zemer.ui.component.SwitchPreference

/** One lyrics provider as the settings dialogs see it: registry name(s), display strings, and its enable preference. */
class LyricsProviderToggle(
    val ids: List<String>,            // registry names this toggle governs (YouTube covers subtitles + the lyrics tab)
    val title: Int,
    val description: Int,
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    val statusCode: String? = null,   // last outcome code (Musixmatch, `MusixmatchStatus`), localised under the description
)

/** The stored Musixmatch outcome code as user text; null for no/unknown code (older installs stored free text). */
@Composable
fun musixmatchStatusText(code: String?): String? = when (val s = MusixmatchStatus.parse(code)) {
    null -> null
    MusixmatchStatus.Hit -> stringResource(R.string.musixmatch_status_hit)
    MusixmatchStatus.HitSynced -> stringResource(R.string.musixmatch_status_hit_synced)
    MusixmatchStatus.Unauthorized -> stringResource(R.string.musixmatch_status_unauthorized)
    MusixmatchStatus.Network -> stringResource(R.string.musixmatch_status_network)
    MusixmatchStatus.NoMatch -> stringResource(R.string.musixmatch_status_no_match)
    MusixmatchStatus.NoLyrics -> stringResource(R.string.musixmatch_status_no_lyrics)
    MusixmatchStatus.NoToken -> stringResource(R.string.musixmatch_status_no_token)
    is MusixmatchStatus.Rejected -> when (s.reason) {
        MusixmatchStatus.REASON_ARTIST -> stringResource(R.string.musixmatch_status_rejected_artist, s.detail)
        MusixmatchStatus.REASON_TITLE -> stringResource(R.string.musixmatch_status_rejected_title, s.detail)
        MusixmatchStatus.REASON_LENGTH -> stringResource(R.string.musixmatch_status_rejected_length, s.detail)
        else -> stringResource(R.string.musixmatch_status_rejected_unusable, s.detail)
    }
}

/** Content settings → Provider selection: one switch row per provider (the shared `SwitchPreference`, D-pad focusable), with what each source is. */
@Composable
fun LyricsProviderSelectionDialog(providers: List<LyricsProviderToggle>, onDismiss: () -> Unit) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.lyrics_provider_selection)) },
        horizontalAlignment = Alignment.Start,
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    ) {
        // weight(fill = false): the list takes only what it needs but never pushes the buttons off a small screen;
        // past the dialog's height cap it scrolls (the nav-drawer behaviour).
        Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            providers.forEach { p ->
                val status = musixmatchStatusText(p.statusCode)
                SwitchPreference(
                    title = { Text(stringResource(p.title)) },
                    description = listOfNotNull(stringResource(p.description), status).joinToString("\n"),
                    checked = p.enabled,
                    onCheckedChange = p.onEnabledChange,
                    contentPadding = PreferenceEntryDefaults.compactContentPadding,
                )
            }
            Text(stringResource(R.string.lyrics_provider_youtube_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
        }
    }
}

/**
 * Content settings → Lyrics provider priority: the ENABLED providers in the user's order, drag to reorder.
 * [order] is the serialised preference; the callback receives the new serialised order. The order math is the
 * pure, tested `LyricsProviderOrdering`; the rows are the shared `ReorderableList`.
 */
@Composable
fun LyricsProviderPriorityDialog(providers: List<LyricsProviderToggle>, order: String, onOrderChange: (String) -> Unit, onDismiss: () -> Unit) {
    val enabledIds = providers.filter { it.enabled }.flatMap { it.ids }.toSet()
    val byFirstId = providers.associateBy { it.ids.first() }
    val rows = LyricsProviderOrdering.enabledGroups(order, providers.map { it.ids }, enabledIds)
    val items = remember { mutableStateListOf<ReorderableEntry>() }
    val titles = rows.associate { it.first() to stringResource(byFirstId.getValue(it.first()).title) }
    LaunchedEffect(rows) { items.clear(); items.addAll(rows.map { ReorderableEntry(it.first(), titles.getValue(it.first())) }) }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.lyrics_provider_priority)) },
        horizontalAlignment = Alignment.Start,
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Text(stringResource(R.string.lyrics_provider_priority_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            // The lazy list is bounded by the dialog's height cap and scrolls past it (the nav-drawer behaviour).
            ReorderableList(
                items = items,
                onItemsReordered = { reordered -> onOrderChange(LyricsProviderOrdering.reordered(order, reordered.map { byFirstId.getValue(it.id).ids }, enabledIds)) },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            )
        }
    }
}
