package com.jtech.zemer.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.jtech.zemer.R
import com.jtech.zemer.utils.hasNotificationPermission
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class ExoDownloadService : DownloadService(
    NOTIFICATION_ID,
    1000L,
    CHANNEL_ID,
    R.string.downloading,
    0
) {
    @Inject
    lateinit var downloadUtil: DownloadUtil

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasNotificationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == REMOVE_ALL_PENDING_DOWNLOADS) {
            downloadManager.currentDownloads.forEach { download ->
                downloadManager.removeDownload(download.request.id)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun getDownloadManager() = downloadUtil.downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification =
        Notification.Builder.recoverBuilder(
            this, downloadUtil.downloadNotificationHelper.buildProgressNotification(
                this,
                R.drawable.download,
                null,
                if (downloads.size == 1) Util.fromUtf8Bytes(downloads[0].request.data)
                else resources.getQuantityString(R.plurals.n_song, downloads.size, downloads.size),
                downloads,
                notMetRequirements
            )
        ).addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.close),
                getString(android.R.string.cancel),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, ExoDownloadService::class.java).setAction(
                        REMOVE_ALL_PENDING_DOWNLOADS
                    ),
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        ).build()

    companion object {
        const val CHANNEL_ID = "download"
        const val NOTIFICATION_ID = 1
        const val JOB_ID = 1
        const val REMOVE_ALL_PENDING_DOWNLOADS = "REMOVE_ALL_PENDING_DOWNLOADS"
    }
}
