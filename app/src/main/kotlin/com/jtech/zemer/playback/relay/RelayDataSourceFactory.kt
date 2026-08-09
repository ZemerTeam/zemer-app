package com.jtech.zemer.playback.relay

import android.content.Context
import androidx.core.net.toUri
import com.jtech.zemer.BuildConfig
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.jtech.zemer.playback.VideoRendition
import com.metrolist.innertube.utils.ResilientDns
import okhttp3.OkHttpClient

/**
 * The isolated RELAY playback data source (see [RelayStream] / the handoff doc), extracted out of
 * MusicService so that giant does not keep growing. It streams each open from the whitelisted relay host,
 * EXCEPT that a downloaded local file plays from disk: the [resolveLocalFile] callback
 * (MusicService.resolveDownloadedFileUri) makes that decision the SAME way the DIRECT resolver does (decide
 * once at position 0, self-repair, recoverSong, video-mode nudge). A `video:` rendition key never has a
 * local file in relay (audio-only downloads), so it always streams. Cache-free and fully separate from the
 * DIRECT factory, so it can never touch a normal user's cached bytes.
 */
object RelayDataSourceFactory {
    fun create(
        context: Context,
        resolveLocalFile: (mediaId: String, position: Long) -> String?,
    ): DataSource.Factory {
        val http = OkHttpDataSource.Factory(OkHttpClient.Builder().dns(ResilientDns()).build())
        // DEBUG builds mark relay media requests so the relay serves but does not count them (see
        // RelayStream.DEBUG_HEADER). Every request from this factory goes to the relay, so it rides
        // relay hosts only; release builds add nothing.
        if (BuildConfig.DEBUG) http.setDefaultRequestProperties(mapOf(RelayStream.DEBUG_HEADER to "1"))
        val upstream = DefaultDataSource.Factory(context, http)
        return ResolvingDataSource.Factory(upstream) { dataSpec ->
            val mediaId = dataSpec.key ?: dataSpec.uri.toString()
            if (!VideoRendition.isVideoKey(mediaId)) {
                resolveLocalFile(mediaId, dataSpec.position)?.let {
                    return@Factory dataSpec.withUri(it.toUri())
                }
            }
            // Audio for a plain id, 360p muxed video for a `video:` rendition key.
            dataSpec.withUri(RelayStream.playbackUrl(mediaId).toUri())
        }
    }
}
