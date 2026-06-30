package com.jtech.zemer.playback

/**
 * Pure decision for how a search/browse video item should activate, shared by every surface so the
 * "play videos as audio" behavior can never drift between them.
 *
 * The model has one effective rule: a video plays as **audio** (rendered as a "video song" row, queued
 * in the music player) whenever video imagery is blocked by the content filter (`blockVideos`) OR the
 * user opted into audio-only video playback (`playVideosAsAudio`). Otherwise it opens the full-screen
 * video player. A non-video is always audio.
 *
 * Because `blockVideos` is the existing (lockable, synced) content filter, redefining it here — from
 * "hide videos" to "videos are audio-only" — gives every blocked/locked account the audio experience
 * with no new setting to toggle (which a locked account could not change anyway).
 */
fun videoPlaysAsAudio(
    isVideo: Boolean,
    blockVideos: Boolean,
    playVideosAsAudio: Boolean,
): Boolean = !isVideo || blockVideos || playVideosAsAudio

/**
 * Whether a video item may be opened in the full-screen video player. Watching is allowed only when
 * imagery is not blocked; the audio-only *preference* never forbids an explicit watch (it only changes
 * the default), but a blocked content filter always does.
 */
fun videoWatchAllowed(blockVideos: Boolean): Boolean = !blockVideos
