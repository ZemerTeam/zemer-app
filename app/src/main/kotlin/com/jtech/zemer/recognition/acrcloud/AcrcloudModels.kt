package com.jtech.zemer.recognition.acrcloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AcrcloudResponseJson(
    @SerialName("status")
    val status: AcrcloudStatus,
    @SerialName("metadata")
    val metadata: AcrcloudMetadata? = null,
    @SerialName("cost_time")
    val costTime: Double? = null,
)

@Serializable
data class AcrcloudStatus(
    @SerialName("msg")
    val msg: String,
    @SerialName("code")
    val code: Int,
    @SerialName("version")
    val version: String? = null,
)

@Serializable
data class AcrcloudMetadata(
    @SerialName("humming")
    val humming: List<AcrcloudTrack>? = null,
    @SerialName("music")
    val music: List<AcrcloudTrack>? = null,
)

@Serializable
data class AcrcloudTrack(
    @SerialName("title")
    val title: String? = null,
    @SerialName("artists")
    val artists: List<AcrcloudArtist>? = null,
    @SerialName("album")
    val album: AcrcloudAlbum? = null,
    @SerialName("score")
    val score: Double? = null,
    @SerialName("label")
    val label: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("acrid")
    val acrid: String? = null,
    @SerialName("duration_ms")
    val durationMs: String? = null,
    @SerialName("play_offset_ms")
    val playOffsetMs: Int? = null,
    @SerialName("external_ids")
    val externalIds: AcrcloudExternalIds? = null,
)

@Serializable
data class AcrcloudArtist(
    @SerialName("name")
    val name: String,
)

@Serializable
data class AcrcloudAlbum(
    @SerialName("name")
    val name: String? = null,
)

@Serializable
data class AcrcloudExternalIds(
    @SerialName("isrc")
    val isrc: String? = null,
)

