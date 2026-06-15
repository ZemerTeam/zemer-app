package com.jtech.zemer.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.BitmapImageProvider
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.jtech.zemer.MainActivity
import com.jtech.zemer.R
import com.jtech.zemer.playback.MusicService
import com.jtech.zemer.ui.screens.recognition.RecognizeMusicDialogActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MusicWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Exact: lay out for the real widget size so content always fits (no bucket-vs-actual clipping).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // After a process restart the in-memory bitmap is gone but the host may re-inflate the
        // widget with no MusicService update in sight — reload the last art from disk so it persists.
        if (cachedAlbumArtBitmap == null) {
            cachedAlbumArtBitmap = withContext(Dispatchers.IO) { loadPersistedArt(context) }
        }
        val albumArt = cachedAlbumArtBitmap
        provideContent {
            GlanceTheme {
                MusicWidgetContent(context, albumArt)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun MusicWidgetContent(context: Context, albumArt: Bitmap?) {
        val prefs = currentState<Preferences>()
        val title = prefs[PREF_TITLE] ?: context.getString(R.string.app_name)
        val artist = prefs[PREF_ARTIST] ?: ""
        val isPlaying = prefs[PREF_IS_PLAYING] ?: false
        // SizeMode.Exact has no compact bucket, so drop the seek row when the widget is too short to
        // hold it (legacy/odd placements below the resize minimum) instead of clipping the controls.
        val showSeek = WidgetLayout.showSeekRow(LocalSize.current.height.value)

        Box(GlanceModifier.fillMaxSize().padding(3.dp)) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(R.color.widget_background))
                    .cornerRadius(24.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Main row — fills the remaining height; album art fills that height so there's no
                // whitespace, and the info column is weighted so nothing is ever cut off.
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(56.dp)
                            .cornerRadius(12.dp)
                            .background(ColorProvider(R.color.widget_album_bg))
                            .clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (albumArt != null) {
                            Image(
                                provider = BitmapImageProvider(albumArt),
                                contentDescription = null,
                                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Image(
                                provider = ImageProvider(R.mipmap.ic_launcher),
                                contentDescription = null,
                                modifier = GlanceModifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(GlanceModifier.width(10.dp))

                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = TextStyle(
                                color = ColorProvider(R.color.widget_text_primary),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                        )
                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_secondary),
                                    fontSize = 11.sp,
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                            )
                        }
                    }

                    Spacer(GlanceModifier.width(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ControlButton(context, R.drawable.skip_previous, ACTION_PREV, 30)
                        Spacer(GlanceModifier.width(2.dp))
                        Box(
                            modifier = GlanceModifier
                                .size(38.dp)
                                .cornerRadius(19.dp)
                                .background(ColorProvider(R.color.widget_accent))
                                .clickable(getServiceAction(context, ACTION_PLAY_PAUSE)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                provider = ImageProvider(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = context.getString(R.string.play),
                                modifier = GlanceModifier.size(20.dp),
                                colorFilter = ColorFilter.tint(ColorProvider(R.color.widget_text_primary)),
                            )
                        }
                        Spacer(GlanceModifier.width(2.dp))
                        ControlButton(context, R.drawable.skip_next, ACTION_NEXT, 30)
                        Spacer(GlanceModifier.width(2.dp))
                        RecognizeButton(context, 30)
                    }
                }

                if (showSeek) {
                    Spacer(GlanceModifier.height(6.dp))
                    SeekRow(prefs)
                }
            }
        }
    }

    /** Elapsed time · progress bar · total time. */
    @androidx.compose.runtime.Composable
    private fun SeekRow(prefs: Preferences) {
        val position = prefs[PREF_POSITION] ?: 0L
        val duration = prefs[PREF_DURATION] ?: 0L
        val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(position),
                style = TextStyle(color = ColorProvider(R.color.widget_text_secondary), fontSize = 10.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.defaultWeight().height(4.dp),
                color = ColorProvider(R.color.widget_accent),
                backgroundColor = ColorProvider(R.color.widget_text_secondary),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = formatTime(duration),
                style = TextStyle(color = ColorProvider(R.color.widget_text_secondary), fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun ControlButton(context: Context, iconRes: Int, action: String, size: Int) {
        Box(
            modifier = GlanceModifier
                .size(size.dp)
                .cornerRadius((size / 2).dp)
                .clickable(getServiceAction(context, action)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size((size - 8).dp),
                colorFilter = ColorFilter.tint(ColorProvider(R.color.widget_text_primary)),
            )
        }
    }

    /** Mic button that opens the Zemer-branded "Recognize music" popup over the home screen. */
    @androidx.compose.runtime.Composable
    private fun RecognizeButton(context: Context, size: Int) {
        Box(
            modifier = GlanceModifier
                .size(size.dp)
                .cornerRadius((size / 2).dp)
                .clickable(
                    actionStartActivityIntent(
                        Intent(context, RecognizeMusicDialogActivity::class.java)
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.mic),
                contentDescription = context.getString(R.string.recognize_music),
                modifier = GlanceModifier.size((size - 8).dp),
                colorFilter = ColorFilter.tint(ColorProvider(R.color.widget_text_primary)),
            )
        }
    }

    private fun getServiceAction(context: Context, action: String): Action {
        val intent = Intent(context, MusicService::class.java).apply {
            this.action = action
        }
        return actionStartService(intent)
    }

    companion object {
        private val PREF_TITLE = stringPreferencesKey("title")
        private val PREF_ARTIST = stringPreferencesKey("artist")
        private val PREF_IS_PLAYING = booleanPreferencesKey("is_playing")
        private val PREF_POSITION = longPreferencesKey("position_ms")
        private val PREF_DURATION = longPreferencesKey("duration_ms")

        // Last decoded album art, cached in-process for the hot path and mirrored to [ART_FILE_NAME]
        // so it survives process death. @Volatile: written from MusicService, read on the Glance
        // composition thread.
        @Volatile
        private var cachedAlbumArtBitmap: Bitmap? = null

        @Volatile
        private var cachedAlbumArtUrl: String? = null

        private const val ART_FILE_NAME = "widget_album_art.png"

        const val ACTION_PLAY_PAUSE = "com.jtech.zemer.ACTION_PLAY"
        const val ACTION_NEXT = "com.jtech.zemer.ACTION_NEXT"
        const val ACTION_PREV = "com.jtech.zemer.ACTION_PREV"

        /** True iff at least one instance of this widget is currently placed on a home screen. */
        suspend fun hasPlacedWidget(context: Context): Boolean =
            GlanceAppWidgetManager(context).getGlanceIds(MusicWidget::class.java).isNotEmpty()

        private fun artFile(context: Context): File = File(context.filesDir, ART_FILE_NAME)

        private fun loadPersistedArt(context: Context): Bitmap? = runCatching {
            val file = artFile(context)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }.getOrNull()

        private fun formatTime(ms: Long): String {
            if (ms <= 0L) return "0:00"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

        suspend fun updateWidget(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            albumArtUrl: String? = null,
            positionMs: Long = 0L,
            durationMs: Long = 0L,
        ) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(MusicWidget::class.java)
                if (glanceIds.isEmpty()) return

                // Load (and persist) album art only when the URL changes — not on every seek tick.
                if (albumArtUrl != null && albumArtUrl != cachedAlbumArtUrl) {
                    val bitmap = withContext(Dispatchers.IO) {
                        runCatching {
                            val loader = SingletonImageLoader.get(context)
                            val request = ImageRequest.Builder(context)
                                .data(albumArtUrl)
                                .size(300, 300)
                                .build()
                            val result = loader.execute(request)
                            if (result is SuccessResult) {
                                val original = result.image.toBitmap()
                                val safe = if (original.config == Bitmap.Config.HARDWARE) {
                                    original.copy(Bitmap.Config.ARGB_8888, false)
                                } else {
                                    original
                                }
                                runCatching {
                                    artFile(context).outputStream().use {
                                        safe.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                }
                                safe
                            } else {
                                null
                            }
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        cachedAlbumArtBitmap = bitmap
                        cachedAlbumArtUrl = albumArtUrl
                    }
                }

                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[PREF_TITLE] = title
                            this[PREF_ARTIST] = artist
                            this[PREF_IS_PLAYING] = isPlaying
                            this[PREF_POSITION] = positionMs
                            this[PREF_DURATION] = durationMs
                        }
                    }
                    MusicWidget().update(context, glanceId)
                }
            } catch (_: Exception) {
                // Widget may not be added
            }
        }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
