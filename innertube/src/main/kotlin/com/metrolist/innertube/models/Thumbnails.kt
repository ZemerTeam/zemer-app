package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnails(
    // YouTube sometimes sends a thumbnail object without the list; that must not fail the whole response.
    val thumbnails: List<Thumbnail> = emptyList(),
)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int?,
    val height: Int?,
)
