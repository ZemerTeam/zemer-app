package com.jtech.zemer.playback

/**
 * Pure UI-state decisions for the in-player video experience — no Compose, no Android, fully
 * JVM-unit-tested. The Player composables read these instead of hand-rolling the (isVideoMode,
 * isFullscreen, expanded) coupling in two places (inline thumbnail vs fullscreen overlay), so the
 * two placements can never disagree about which surface is live.
 *
 * [VideoModeController] already decides *whether* video is available (blocked/casting/rendition —
 * [VideoModeLogic.availability]); this object only decides *where* the live video surface renders
 * given the mode/fullscreen/PiP flags. They never overlap: at most one of [showInlineVideo] /
 * [showFullscreenVideo] / [showPipVideo] is ever true, so exactly one surface owner exists at a time.
 */
object PlayerVideoUiLogic {

    /**
     * Whether the inline video surface (in the album-art slot) is the live surface owner. True only
     * while in video mode and NOT in fullscreen — fullscreen re-parents the same surface, so the
     * inline placement must yield to avoid two owners fighting over the one ExoPlayer output.
     */
    fun showInlineVideo(isVideoMode: Boolean, isFullscreen: Boolean, inPip: Boolean = false): Boolean =
        isVideoMode && !isFullscreen && !inPip

    /**
     * Whether the fullscreen overlay (and its surface) should be shown. Requires the player sheet to
     * be expanded — a collapsed/mini player never hosts fullscreen video.
     */
    fun showFullscreenVideo(
        expanded: Boolean,
        isVideoMode: Boolean,
        isFullscreen: Boolean,
        inPip: Boolean = false,
    ): Boolean =
        expanded && isVideoMode && isFullscreen && !inPip

    /**
     * Whether the picture-in-picture window's surface is the live owner: in video mode while the
     * Activity is in PiP. The inline and fullscreen placements yield while this is true, so the one
     * ExoPlayer output still has exactly one owner.
     */
    fun showPipVideo(isVideoMode: Boolean, inPip: Boolean): Boolean =
        isVideoMode && inPip

    /**
     * The PiP window's audio-only state: still in the window, but video mode ended inside it (video
     * mode is per-play, so a track change, a player error or a cast connection reverts it). The window
     * then shows the current cover art - the audio IS still playing - never the app's regular UI
     * shrunk into a thumbnail-sized window. Exactly one of this / [showPipVideo] is true while in PiP.
     */
    fun showPipArtwork(isVideoMode: Boolean, inPip: Boolean): Boolean =
        inPip && !isVideoMode

    /**
     * Whether `onUserLeaveHint` should enter PiP: only in video mode, NEVER while an Activity this
     * one launched is opening (the platform delivers the hint for the recognition dialog, a share
     * sheet, a picker or sign-in too, and PiP under our own dialog is exactly the bug), and never
     * where the platform's auto-enter (API 31+) owns the decision - it fires only for Home/recents,
     * and a manual enter beside it would double up.
     */
    fun shouldEnterPipOnLeave(isVideoMode: Boolean, ownLaunchInFlight: Boolean, autoEnterSupported: Boolean): Boolean =
        isVideoMode && !ownLaunchInFlight && !autoEnterSupported

    /**
     * Whether a requested-fullscreen flag must be force-cleared. Fullscreen is a per-play, in-video
     * affordance: the instant video mode ends — a track advance/skip/error revert (I2) or the sheet
     * collapsing — fullscreen must exit back to the expanded player (D4: track end in fullscreen
     * exits + advances as audio). The caller flips its `isFullscreen` state to false on this.
     */
    fun shouldExitFullscreen(isFullscreen: Boolean, isVideoMode: Boolean, expanded: Boolean): Boolean =
        isFullscreen && (!isVideoMode || !expanded)

    /**
     * Whether opening the lyrics sheet should revert video mode to audio. Video plays inline in the
     * album-art slot; the lyrics sheet expands over it, so the video surface keeps decoding — burning
     * bandwidth and CPU invisibly behind the lyrics. The defensible rule: opening lyrics
     * exits video mode (position-continuous revert to audio). Closing lyrics does NOT auto-return to
     * video — video is a per-play opt-in the user re-toggles.
     */
    fun shouldRevertVideoForLyrics(lyricsExpanded: Boolean, isVideoMode: Boolean): Boolean =
        lyricsExpanded && isVideoMode
}
