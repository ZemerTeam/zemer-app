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
        deviceId: String?,
        resolveLocalFile: (mediaId: String, position: Long) -> String?,
    ): DataSource.Factory {
        val http = OkHttpDataSource.Factory(OkHttpClient.Builder().dns(ResilientDns()).build())
        // Default headers on the relay OkHttp factory ride relay hosts only (local files bypass OkHttp) and
        // never leak elsewhere. DEBUG marks the request so the relay serves it but does not count it; a
        // release build instead carries the relay-only [deviceId] (null in debug) so the relay can count
        // distinct devices per filter. The two are mutually exclusive by build type.
        val headers = buildMap {
            if (BuildConfig.DEBUG) put(RelayStream.DEBUG_HEADER, "1")
            deviceId?.let { put(RelayDeviceId.HEADER, it) }
        }
        if (headers.isNotEmpty()) http.setDefaultRequestProperties(headers)
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
