package com.jtech.zemer.playback

/**
 * Pure UI-state decisions for the in-player video experience — no Compose, no Android, fully
 * JVM-unit-tested. The Player composables read these instead of hand-rolling the (isVideoMode,
 * isFullscreen, expanded) coupling in two places (inline thumbnail vs fullscreen overlay), so the
 * two placements can never disagree about which surface is live.
 *
 * [VideoModeController] already decides *whether* video is available (blocked/casting/rendition —
 * [VideoModeLogic.availability]); this object only decides *where* the live video surface renders
 * given the mode/fullscreen flags. The two never overlap: at most one of [showInlineVideo] /
 * [showFullscreenVideo] is ever true, so exactly one surface owner exists at a time.
 */
object PlayerVideoUiLogic {

    /**
     * Whether the inline video surface (in the album-art slot) is the live surface owner. True only
     * while in video mode and NOT in fullscreen — fullscreen re-parents the same surface, so the
     * inline placement must yield to avoid two owners fighting over the one ExoPlayer output.
     */
    fun showInlineVideo(isVideoMode: Boolean, isFullscreen: Boolean): Boolean =
        isVideoMode && !isFullscreen

    /**
     * Whether the fullscreen overlay (and its surface) should be shown. Requires the player sheet to
     * be expanded — a collapsed/mini player never hosts fullscreen video.
     */
    fun showFullscreenVideo(expanded: Boolean, isVideoMode: Boolean, isFullscreen: Boolean): Boolean =
        expanded && isVideoMode && isFullscreen

    /**
     * Whether a requested-fullscreen flag must be force-cleared. Fullscreen is a per-play, in-video
     * affordance: the instant video mode ends — a track advance/skip/error revert (I2) or the sheet
     * collapsing — fullscreen must exit back to the expanded player (D4: track end in fullscreen
     * exits + advances as audio). The caller flips its `isFullscreen` state to false on this.
     */
    fun shouldExitFullscreen(isFullscreen: Boolean, isVideoMode: Boolean, expanded: Boolean): Boolean =
        isFullscreen && (!isVideoMode || !expanded)
}
