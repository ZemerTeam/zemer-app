package com.jtech.zemer.crawler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.jtech.zemer.R
import com.jtech.zemer.lyrics.LyricsHelper
import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient
import com.jtech.zemer.models.MediaMetadata
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Debug-only self-driving lyrics CRAWLER. Walks the catalog (a page at a time from the server's shared rotating
 * worklist, so multiple devices never duplicate work), fetches every enabled provider's lyrics for each song via
 * [LyricsHelper] (which fire-and-forget POSTs each provider body to the Zemer server), and paces itself to stay
 * IP-safe to the providers. It runs as a FOREGROUND service holding a PARTIAL wake lock, so it keeps working with
 * the screen off. It deliberately does NOT play audio, so it never emits play events and never skews play-count
 * rankings.
 */
@AndroidEntryPoint
class LyricsCrawlerService : Service() {

    @Inject lateinit var lyricsHelper: LyricsHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    data class Progress(val running: Boolean = false, val done: Int = 0, val current: String = "")

    companion object {
        val progress = MutableStateFlow(Progress())
        private const val CHANNEL_ID = "lyrics_crawler"
        private const val NOTIFICATION_ID = 771
        const val ACTION_STOP = "com.jtech.zemer.CRAWL_STOP"
        private const val BATCH = 100
        private const val PACE_MS = 1500L   // default gap between songs; the server can override via worklist paceMs

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LyricsCrawlerService::class.java)) }
                .onFailure { Timber.e(it, "crawler start failed") }
        }
        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, LyricsCrawlerService::class.java).apply { action = ACTION_STOP }) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        startForegroundNow()
        acquireWakeLock()
        startCrawl()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNow()
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        progress.value = progress.value.copy(running = false, current = "")
        job?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startCrawl() {
        if (job?.isActive == true) return
        job = scope.launch {
            var done = progress.value.done
            progress.value = Progress(running = true, done = done)
            while (isActive) {
                val reply = runCatching { ZemerLyricsClient.scrapeWorklist(BATCH) }.getOrNull()
                val batch = reply?.tracks ?: emptyList()
                // Rate-limit handling: the server sets the pace (throttle the whole fleet centrally if a provider
                // pushes back), we add per-device jitter, and back off longer when the server is unreachable.
                val pace = if (reply != null && reply.paceMs > 0) reply.paceMs.toLong() else PACE_MS
                if (batch.isEmpty()) { delay(if (reply == null) 15000L else 5000L); continue }
                for (t in batch) {
                    if (!isActive) break
                    progress.value = Progress(running = true, done = done, current = t.title)
                    runCatching {
                        lyricsHelper.getLyrics(
                            MediaMetadata(
                                id = t.videoId,
                                title = t.title,
                                artists = listOf(MediaMetadata.Artist(null, t.artist)),
                                duration = t.duration,
                            )
                        )
                    }.onFailure { Timber.d(it, "crawl fetch failed %s", t.videoId) }
                    done++
                    if (done % 10 == 0) updateNotification(done, t.title)
                    delay(pace + (0L..400L).random())
                }
            }
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zemer:lyrics-crawler").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)   // 6 h ceiling; a longer crawl just restarts the service
            }
        }.onFailure { Timber.e(it, "wakelock failed") }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lyrics crawler", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
    }

    private fun startForegroundNow() {
        runCatching {
            val n = buildNotification(progress.value.done, progress.value.current.ifBlank { "Starting" })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION_ID, n)
        }.onFailure { Timber.e(it, "startForeground failed"); stopSelf() }
    }

    private fun updateNotification(done: Int, current: String) {
        runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification(done, current)) }
    }

    private fun buildNotification(done: Int, current: String): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, LyricsCrawlerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lyrics crawler running")
            .setContentText("Fetched $done  ·  $current")
            .setSmallIcon(R.drawable.lyrics)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stop)
            .build()
    }
}
