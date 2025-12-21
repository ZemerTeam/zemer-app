package com.jtech.zemer.ui.menu

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.ArtistSongSortType
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.ListItem
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val artistState = database.artist(originalArtist.id).collectAsState(initial = originalArtist)
    val artist = artistState.value ?: originalArtist

    if (showReportDialog) {
        val reasons = listOf(
            "female" to stringResource(R.string.report_reason_female),
            "gentile" to stringResource(R.string.report_reason_gentile),
            "bad_playlists" to stringResource(R.string.report_reason_bad_playlists),
            "bad_images" to stringResource(R.string.report_reason_bad_images),
            "other" to stringResource(R.string.report_reason_other),
        )
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showReportDialog = false },
            title = { Text(stringResource(R.string.report_artist)) },
            text = {
                Column {
                    reasons.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = value }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == value,
                                onClick = { selectedReason = value }
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
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedReason.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.report_choose_reason), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            isSubmitting = true
                            try {
                                val uid = auth.currentUser?.uid ?: "anon"
                                val payload = hashMapOf(
                                    "artistId" to artist.artist.id,
                                    "artistName" to artist.artist.name,
                                    "reason" to selectedReason,
                                    "comment" to comment,
                                    "status" to "pending",
                                    "reporterUid" to uid,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                                firestore.collection("artistReports").add(payload).await()
                                Toast.makeText(context, context.getString(R.string.report_success), Toast.LENGTH_SHORT).show()
                                showReportDialog = false
                                selectedReason = ""
                                comment = ""
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.report_failure), Toast.LENGTH_SHORT).show()
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.report_submit))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isSubmitting) showReportDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text(stringResource(R.string.report_cancel))
                }
            }
        )
    }

    // Artist menu header without song count
    ListItem(
        title = artist.artist.name,
        subtitle = null, // Hide song count in menu
        badges = {
            if (artist.artist.bookmarkedAt != null) {
                Icon(
                    painter = painterResource(R.drawable.favorite),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
                )
            }
        },
        thumbnailContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.artist.thumbnailUrl)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(CircleShape),
            )
        },
        trailingContent = {},
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        userScrollEnabled = !isPortrait,
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    if (artist.songCount > 0) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )

                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.shuffle),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                                .shuffled()
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }

                    if (artist.artist.isYouTubeArtist) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.share),
                                onClick = {
                                    onDismiss()
                                    val intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.zemer.io/channel/${artist.id}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }
                            )
                        )
                }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            androidx.compose.material3.ListItem(
                headlineContent = {
                    Text(text = if (artist.artist.bookmarkedAt != null) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(if (artist.artist.bookmarkedAt != null) R.drawable.subscribed else R.drawable.subscribe),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    database.transaction {
                        update(artist.artist.toggleLike())
                    }
                }
            )
        }

        item {
            androidx.compose.material3.ListItem(
                headlineContent = { Text(stringResource(R.string.report_artist)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.warning),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    showReportDialog = true
                }
            )
        }
    }
}
