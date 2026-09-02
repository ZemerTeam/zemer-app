package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.lyrics.LyricsProviderRegistry
import com.jtech.zemer.ui.component.DraggableLyricsProviderItem
import com.jtech.zemer.ui.component.DraggableLyricsProviderList

/** One lyrics provider as the settings dialogs see it: registry name(s), display strings, and its enable preference. */
class LyricsProviderToggle(
    val ids: List<String>,            // registry names this toggle governs (YouTube covers subtitles + the lyrics tab)
    val title: Int,
    val description: Int,
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    val status: String? = null,      // last outcome line (Musixmatch), shown under the description
)

/** Content settings → Provider selection: one switch per provider, with what each source is. */
@Composable
fun LyricsProviderSelectionDialog(providers: List<LyricsProviderToggle>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_provider_selection)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                providers.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(p.title))
                            Text(stringResource(p.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            p.status?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                        }
                        Switch(
                            checked = p.enabled,
                            onCheckedChange = p.onEnabledChange,
                            thumbContent = { Icon(painterResource(if (p.enabled) R.drawable.check else R.drawable.close), null, Modifier.size(SwitchDefaults.IconSize)) },
                        )
                    }
                }
                Text(stringResource(R.string.lyrics_provider_youtube_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/**
 * Content settings → Lyrics provider priority: the ENABLED providers in the user's order, drag to reorder.
 * [order] is the serialised preference; the callback receives the new serialised order (disabled providers
 * keep their relative places after the enabled ones so re-enabling one is predictable).
 */
@Composable
fun LyricsProviderPriorityDialog(providers: List<LyricsProviderToggle>, order: String, onOrderChange: (String) -> Unit, onDismiss: () -> Unit) {
    val normalized = LyricsProviderRegistry.deserializeProviderOrder(order)
    val byId = providers.flatMap { p -> p.ids.map { it to p } }.toMap()
    val enabledIds = providers.filter { it.enabled }.flatMap { it.ids }.toSet()
    // One row per TOGGLE (YouTube's two registry entries collapse into one row), in the current order.
    val rows = normalized.filter { it in enabledIds }.map { byId.getValue(it) }.distinct()
    val items = remember { mutableStateListOf<DraggableLyricsProviderItem>() }
    val titles = rows.associate { it.ids.first() to stringResource(it.title) }
    LaunchedEffect(rows) { items.clear(); items.addAll(rows.map { DraggableLyricsProviderItem(it.ids.first(), titles.getValue(it.ids.first())) }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_provider_priority)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                Text(stringResource(R.string.lyrics_provider_priority_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                DraggableLyricsProviderList(
                    items = items,
                    onItemsReordered = { reordered ->
                        val enabledOrder = reordered.flatMap { byId.getValue(it.id).ids }
                        val disabledOrder = normalized.filter { it !in enabledIds }
                        onOrderChange(LyricsProviderRegistry.serializeProviderOrder(enabledOrder + disabledOrder))
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
