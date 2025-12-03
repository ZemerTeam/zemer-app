package com.jtech.zemer.ui.screens

import android.net.Uri

fun videoRoute(videoId: String, title: String? = null, artist: String? = null): String {
    val params = listOfNotNull(
        title?.takeIf { it.isNotBlank() }?.let { "title=${Uri.encode(it)}" },
        artist?.takeIf { it.isNotBlank() }?.let { "artist=${Uri.encode(it)}" },
    )
    val query = if (params.isNotEmpty()) params.joinToString("&", prefix = "?") else ""
    return "video/$videoId$query"
}
