package com.jtech.zemer.lyrics.model

/**
 * Thrown when a provider reached the server but the track has no lyrics/transcript available.
 */
object LyricsUnavailableException : IllegalStateException("Lyrics not available for this track")
