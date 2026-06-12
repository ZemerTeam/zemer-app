package com.jtech.zemer.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.FormatEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.PlayerDatesStore

/**
 * Song-details sheet (opened from the player / song menu). Material 3 expressive separated-list:
 * each field is its own card (icon + label + value) and tapping it copies the value. Sections:
 * General, Information (stats + stream/format), Description.
 */
@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank()) return

    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current

    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var song by remember { mutableStateOf<Song?>(null) }
    var currentFormat by remember { mutableStateOf<FormatEntity?>(null) }

    LaunchedEffect(videoId) { info = YouTube.getMediaInfo(videoId).getOrNull() }
    LaunchedEffect(videoId) { database.song(videoId).collect { song = it } }
    LaunchedEffect(videoId) { database.format(videoId).collect { currentFormat = it } }

    fun copy(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        if (info == null) {
            ShimmerHost {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
                ) { TextPlaceholder() }
            }
            return
        }

        val unknown = stringResource(R.string.unknown)
        val notApplicable = stringResource(R.string.not_applicable)
        // Player hash + cipher date apply only to deciphered web clients; direct-URL clients
        // (VISIONOS/ANDROID_VR/IOS) never run the cipher, so show "N/A", not "Unknown".
        val isWebStream = currentFormat?.streamClient in setOf("WEB_REMIX", "WEB_CREATOR", "TVHTML5", "WEB")
        val playerHash = if (isWebStream) CipherDeobfuscator.lastUsedPlayerHash else notApplicable
        val cipherSupportAdded =
            if (isWebStream) PlayerDatesStore.get(CipherDeobfuscator.lastUsedPlayerHash) else notApplicable

        fun field(@DrawableRes icon: Int, label: String, value: String?): Material3MenuItemData {
            val display = value ?: unknown
            return Material3MenuItemData(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                title = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                description = {
                    Text(
                        text = display,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { copy(label, display) },
            )
        }

        SectionTitle(stringResource(R.string.general))
        Material3MenuGroup(
            items = listOf(
                field(R.drawable.music_note, stringResource(R.string.song_title), song?.title ?: info?.title),
                field(R.drawable.person, stringResource(R.string.song_artists), song?.artists?.joinToString { it.name } ?: info?.author),
                field(R.drawable.link, stringResource(R.string.media_id), song?.id ?: videoId),
            ),
        )

        SectionTitle(stringResource(R.string.information))
        Material3MenuGroup(
            items = buildList {
                // Stream/cipher details first (most relevant for diagnosing playback).
                currentFormat?.let { f ->
                    add(field(R.drawable.info, stringResource(R.string.format_stream_client), f.streamClient))
                    add(field(R.drawable.lock, stringResource(R.string.format_player_hash), playerHash))
                    add(field(R.drawable.lock_open, stringResource(R.string.format_cipher_support_added), cipherSupportAdded))
                }
                add(field(R.drawable.stats, stringResource(R.string.views), info?.viewCount?.let(::numberFormatter)))
                add(field(R.drawable.favorite, stringResource(R.string.likes), info?.like?.let(::numberFormatter)))
                add(field(R.drawable.favorite_border, stringResource(R.string.dislikes), info?.dislike?.let(::numberFormatter)))
                add(field(R.drawable.person, stringResource(R.string.subscribers), info?.subscribers))
                currentFormat?.let { f ->
                    add(field(R.drawable.tune, stringResource(R.string.format_itag), f.itag?.toString()))
                    add(field(R.drawable.info, stringResource(R.string.mime_type), f.mimeType))
                    add(field(R.drawable.graphic_eq, stringResource(R.string.codecs), f.codecs))
                    add(field(R.drawable.speed, stringResource(R.string.bitrate), f.bitrate?.let { "${it / 1000} Kbps" }))
                    add(field(R.drawable.equalizer, stringResource(R.string.sample_rate), f.sampleRate?.let { "$it Hz" }))
                    add(field(R.drawable.volume_up, stringResource(R.string.loudness), f.loudnessDb?.let { "$it dB" }))
                    add(field(R.drawable.volume_up, stringResource(R.string.volume), playerConnection?.let { "${(it.player.volume * 100).toInt()}%" }))
                    add(field(R.drawable.storage, stringResource(R.string.file_size), f.contentLength?.let { Formatter.formatShortFileSize(context, it) }))
                }
            },
        )

        if (!info?.description.isNullOrBlank()) {
            SectionTitle(stringResource(R.string.description))
            Material3MenuGroup(
                items = listOf(field(R.drawable.info, stringResource(R.string.description), info?.description)),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
    )
}
