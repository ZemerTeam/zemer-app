package com.jtech.zemer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVideoUiLogicTest {

    // --- inline vs fullscreen surface ownership (never both) ----------------

    @Test
    fun `inline video shows only in video mode and not fullscreen`() {
        assertTrue(PlayerVideoUiLogic.showInlineVideo(isVideoMode = true, isFullscreen = false))
        assertFalse(PlayerVideoUiLogic.showInlineVideo(isVideoMode = true, isFullscreen = true))
        assertFalse(PlayerVideoUiLogic.showInlineVideo(isVideoMode = false, isFullscreen = false))
    }

    @Test
    fun `fullscreen video needs expanded + video mode + fullscreen`() {
        assertTrue(PlayerVideoUiLogic.showFullscreenVideo(expanded = true, isVideoMode = true, isFullscreen = true))
        assertFalse(PlayerVideoUiLogic.showFullscreenVideo(expanded = false, isVideoMode = true, isFullscreen = true))
        assertFalse(PlayerVideoUiLogic.showFullscreenVideo(expanded = true, isVideoMode = false, isFullscreen = true))
        assertFalse(PlayerVideoUiLogic.showFullscreenVideo(expanded = true, isVideoMode = true, isFullscreen = false))
    }

    @Test
    fun `inline and fullscreen are mutually exclusive for every input`() {
        for (video in listOf(false, true)) {
            for (full in listOf(false, true)) {
                for (expanded in listOf(false, true)) {
                    val inline = PlayerVideoUiLogic.showInlineVideo(video, full)
                    val fs = PlayerVideoUiLogic.showFullscreenVideo(expanded, video, full)
                    assertFalse("inline+fs both true for video=$video full=$full exp=$expanded", inline && fs)
                }
            }
        }
    }

    // --- force-exit fullscreen (D4 / I2) ------------------------------------

    @Test
    fun `exit fullscreen when video mode ends`() {
        assertTrue(PlayerVideoUiLogic.shouldExitFullscreen(isFullscreen = true, isVideoMode = false, expanded = true))
    }

    @Test
    fun `exit fullscreen when the sheet collapses`() {
        assertTrue(PlayerVideoUiLogic.shouldExitFullscreen(isFullscreen = true, isVideoMode = true, expanded = false))
    }

    @Test
    fun `stay fullscreen while expanded + in video mode`() {
        assertFalse(PlayerVideoUiLogic.shouldExitFullscreen(isFullscreen = true, isVideoMode = true, expanded = true))
    }

    @Test
    fun `never exit when not fullscreen`() {
        assertFalse(PlayerVideoUiLogic.shouldExitFullscreen(isFullscreen = false, isVideoMode = false, expanded = false))
    }

    // --- lyrics-over-video reverts to audio (DESIGN §4) ---------------------

    @Test
    fun `opening lyrics while in video mode reverts to audio`() {
        assertTrue(PlayerVideoUiLogic.shouldRevertVideoForLyrics(lyricsExpanded = true, isVideoMode = true))
    }

    @Test
    fun `lyrics closed or not in video mode does not revert`() {
        assertFalse(PlayerVideoUiLogic.shouldRevertVideoForLyrics(lyricsExpanded = false, isVideoMode = true))
        assertFalse(PlayerVideoUiLogic.shouldRevertVideoForLyrics(lyricsExpanded = true, isVideoMode = false))
        assertFalse(PlayerVideoUiLogic.shouldRevertVideoForLyrics(lyricsExpanded = false, isVideoMode = false))
    }
}
