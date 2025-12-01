@file:OptIn(FlowPreview::class)

package com.jtech.zemer.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jtech.zemer.R
import com.jtech.zemer.utils.hasNotificationPermission
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for MediaStore downloads that shows persistent notifications
 * with download progress and allows user to cancel downloads.
 */
@AndroidEntryPoint
class MediaStoreDownloadService : Service() {

    @Inject
    lateinit var downloadManager: MediaStoreDownloadManager

    @Inject
    lateinit var database: com.jtech.zemer.db.MusicDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private var hasStartedForeground = false
    private var lastCompletedCount = 0

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "mediastore_download"
        private const val NOTIFICATION_CHANNEL_NAME = "Music Downloads"
        private const val NOTIFICATION_ID = 100

        const val ACTION_CANCEL_DOWNLOAD = "com.jtech.zemer.CANCEL_DOWNLOAD"
        const val EXTRA_SONG_ID = "song_id"

        fun start(context: Context) {
            val intent = Intent(context, MediaStoreDownloadService::class.java)
            kotlin.runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaStoreDownloadService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (!hasNotificationPermission(this)) {
            stopSelf()
            return
        }

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for downloads
        createNotificationChannel()

        // CRITICAL: Start foreground immediately to avoid ANR
        ensureForegroundService()

        // Observe download states and update notifications
        // Debounce to avoid notification rate limiting (max ~5/sec allowed by Android)
        scope.launch {
            downloadManager.downloadStates
                .debounce(250) // Update at most 4 times per second
                .onEach { states ->
                    updateNotification(states)

                    // Stop service if no active downloads
                    if (states.values.none { it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING ||
                                it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED }) {
                        stopSelf()
                    }
                }
                .collect()
        }
    }

    /**
     * Ensures the service is running in foreground mode.
     * MUST be called immediately in onCreate and onStartCommand to avoid ANR.
     */
    private fun ensureForegroundService() {
        if (hasStartedForeground) return
        startForeground(NOTIFICATION_ID, createInitialNotification())
        hasStartedForeground = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: Must call startForeground immediately to avoid ANR
        ensureForegroundService()

        // Handle cancel action from notification
        when (intent?.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                if (songId != null) {
                    downloadManager.cancelDownload(songId)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for music download progress"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Preparing downloads...")
            .setSmallIcon(R.drawable.download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun updateNotification(states: Map<String, MediaStoreDownloadManager.DownloadState>) {
        val activeDownloads = states.values.filter {
            it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING ||
            it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED
        }

        val completedCount = states.values.count { it.status == MediaStoreDownloadManager.DownloadState.Status.COMPLETED }

        if (activeDownloads.isEmpty()) {
            if (completedCount > lastCompletedCount) {
                notificationManager.notify(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(getString(R.string.downloaded_songs))
                        .setContentText(getString(R.string.download_complete))
                        .setSmallIcon(R.drawable.download)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
                )
            }
            lastCompletedCount = completedCount
            stopSelf()
            return
        }

        val notification = if (activeDownloads.size == 1) {
            // Single download - show detailed progress
            createSingleDownloadNotification(activeDownloads.first())
        } else {
            // Multiple downloads - show summary
            createMultipleDownloadsNotification(activeDownloads)
        }

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun createSingleDownloadNotification(
        state: MediaStoreDownloadManager.DownloadState
    ): Notification {
        // Get song info from database
        val song = database.song(state.songId).first()
        val title = song?.song?.title ?: "Unknown"
        val artist = song?.artists?.firstOrNull()?.name ?: "Unknown Artist"

        val progress = (state.progress * 100).toInt()

        // Create cancel intent
        val cancelIntent = Intent(this, MediaStoreDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_SONG_ID, state.songId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            state.songId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (state.status) {
            MediaStoreDownloadManager.DownloadState.Status.QUEUED -> "Queued"
            MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING -> "Downloading"
            else -> ""
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$artist • $statusText")
            .setSmallIcon(R.drawable.download)
            .setProgress(100, progress, state.progress == 0f && state.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.close,
                "Cancel",
                cancelPendingIntent
            )
            .build()
    }

    private fun createMultipleDownloadsNotification(
        downloads: List<MediaStoreDownloadManager.DownloadState>
    ): Notification {
        val downloadingCount = downloads.count { it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING }
        val queuedCount = downloads.count { it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED }

        val title = when {
            downloadingCount > 0 && queuedCount > 0 ->
                "Downloading $downloadingCount, $queuedCount queued"
            downloadingCount > 0 ->
                "Downloading $downloadingCount ${if (downloadingCount == 1) "song" else "songs"}"
            else ->
                "$queuedCount ${if (queuedCount == 1) "song" else "songs"} queued"
        }

        // Calculate average progress
        val avgProgress = downloads
            .filter { it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING }
            .map { it.progress }
            .average()
            .let { if (it.isNaN()) 0.0 else it }
            .toFloat()

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Music downloads in progress")
            .setSmallIcon(R.drawable.download)
            .setProgress(100, (avgProgress * 100).toInt(), avgProgress == 0f)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
