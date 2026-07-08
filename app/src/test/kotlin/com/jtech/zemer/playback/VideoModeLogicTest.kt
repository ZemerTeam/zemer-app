package com.jtech.zemer.playback

import com.jtech.zemer.playback.VideoModeLogic.RenditionKind
import com.jtech.zemer.playback.VideoModeLogic.TransitionClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ATV = "MUSIC_VIDEO_TYPE_ATV"
private const val OMV = "MUSIC_VIDEO_TYPE_OMV"

class VideoModeLogicTest {
    private val neverBlocked: (String) -> Boolean = { false }

    private fun availability(
        casting: Boolean = false,
        blockVideos: Boolean = false,
        localVideoFile: Boolean = false,
        musicVideoType: String? = null,
        counterpartVideoId: String? = null,
        isBlockedRendition: (String) -> Boolean = neverBlocked,
    ) = VideoModeLogic.availability(
        mediaId = "SONG",
        casting = casting,
        blockVideos = blockVideos,
        localVideoFile = localVideoFile,
        musicVideoType = musicVideoType,
        counterpartVideoId = counterpartVideoId,
        isBlockedRendition = isBlockedRendition,
    )

    // --- I1 / I5: hard gates ------------------------------------------------

    @Test
    fun `blocked videos ⇒ never available even for a self-video`() {
        assertNull(availability(blockVideos = true, musicVideoType = OMV))
    }

    @Test
    fun `casting ⇒ never available even with a counterpart`() {
        assertNull(availability(casting = true, counterpartVideoId = "VID"))
    }

    // --- rendition selection ------------------------------------------------

    @Test
    fun `local video file wins as LOCAL with no swap id`() {
        val r = availability(localVideoFile = true, musicVideoType = OMV)
        assertEquals(RenditionKind.LOCAL, r?.kind)
        assertNull(r?.renditionVideoId)
    }

    @Test
    fun `self video ⇒ SELF with own id`() {
        assertEquals(Rendition(RenditionKind.SELF, "SONG"), availability(musicVideoType = OMV))
    }

    @Test
    fun `atv song with counterpart ⇒ COUNTERPART with the counterpart id`() {
        assertEquals(
            Rendition(RenditionKind.COUNTERPART, "VID"),
            availability(musicVideoType = ATV, counterpartVideoId = "VID"),
        )
    }

    @Test
    fun `unknown type with counterpart ⇒ COUNTERPART`() {
        assertEquals(
            Rendition(RenditionKind.COUNTERPART, "VID"),
            availability(musicVideoType = null, counterpartVideoId = "VID"),
        )
    }

    @Test
    fun `atv song with no counterpart ⇒ unavailable`() {
        assertNull(availability(musicVideoType = ATV))
    }

    @Test
    fun `unknown type, no counterpart ⇒ unavailable (never guess self-video on unknown)`() {
        assertNull(availability(musicVideoType = null))
    }

    // --- content-filter id gate (BlockedIdsCache) ---------------------------

    @Test
    fun `blocked self rendition id ⇒ unavailable`() {
        assertNull(availability(musicVideoType = OMV, isBlockedRendition = { it == "SONG" }))
    }

    @Test
    fun `blocked counterpart id ⇒ unavailable (audio side still fine)`() {
        assertNull(
            availability(musicVideoType = ATV, counterpartVideoId = "VID", isBlockedRendition = { it == "VID" }),
        )
    }

    @Test
    fun `blocked local rendition id ⇒ unavailable`() {
        assertNull(availability(localVideoFile = true, isBlockedRendition = { it == "SONG" }))
    }

    // --- shouldRequestAvailability -----------------------------------------

    @Test
    fun `request availability for an unresolved atv or unknown item`() {
        assertEquals(true, VideoModeLogic.shouldRequestAvailability(false, false, ATV, false))
        assertEquals(true, VideoModeLogic.shouldRequestAvailability(false, false, null, false))
    }

    @Test
    fun `do not request when already resolved, self-video, casting, or blocked`() {
        assertEquals(false, VideoModeLogic.shouldRequestAvailability(false, false, ATV, true))
        assertEquals(false, VideoModeLogic.shouldRequestAvailability(false, false, OMV, false))
        assertEquals(false, VideoModeLogic.shouldRequestAvailability(true, false, ATV, false))
        assertEquals(false, VideoModeLogic.shouldRequestAvailability(false, true, ATV, false))
    }

    // --- transition classifier ---------------------------------------------

    @Test
    fun `pending swap on the same item ⇒ OWN_SWAP`() {
        assertEquals(TransitionClass.OWN_SWAP, VideoModeLogic.classifyTransition(true, "SONG", "SONG"))
    }

    @Test
    fun `advance to a different id ⇒ TRACK_CHANGE (even if pending)`() {
        assertEquals(TransitionClass.TRACK_CHANGE, VideoModeLogic.classifyTransition(true, "NEXT", "SONG"))
    }

    @Test
    fun `repeat restart of same id without a pending swap ⇒ TRACK_CHANGE`() {
        assertEquals(TransitionClass.TRACK_CHANGE, VideoModeLogic.classifyTransition(false, "SONG", "SONG"))
    }

    @Test
    fun `transition while not in video mode ⇒ TRACK_CHANGE`() {
        assertEquals(TransitionClass.TRACK_CHANGE, VideoModeLogic.classifyTransition(false, "SONG", null))
    }
}

private typealias Rendition = VideoModeLogic.Rendition
