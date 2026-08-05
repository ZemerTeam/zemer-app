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
        stationBroadcast: Boolean = false,
        localVideoFile: Boolean = false,
        online: Boolean = true,
        musicVideoType: String? = null,
        counterpartVideoId: String? = null,
        isBlockedRendition: (String) -> Boolean = neverBlocked,
    ) = VideoModeLogic.availability(
        mediaId = "SONG",
        casting = casting,
        blockVideos = blockVideos,
        stationBroadcast = stationBroadcast,
        localVideoFile = localVideoFile,
        online = online,
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

    @Test
    fun `station broadcast ⇒ never available — even a LOCAL file must not offer the toggle`() {
        // A broadcast is play/stop only: the toggle would mutate the synchronized timeline behind
        // every station transport guard.
        assertNull(availability(stationBroadcast = true, musicVideoType = OMV))
        assertNull(availability(stationBroadcast = true, localVideoFile = true))
        assertNull(availability(stationBroadcast = true, counterpartVideoId = "VID"))
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

    // --- offline gating (I8: streaming renditions need internet) ------------

    @Test
    fun `offline ⇒ a streaming self-video is unavailable (no source error)`() {
        assertNull(availability(online = false, musicVideoType = OMV))
    }

    @Test
    fun `offline ⇒ a streaming counterpart is unavailable`() {
        assertNull(availability(online = false, musicVideoType = ATV, counterpartVideoId = "VID"))
    }

    @Test
    fun `offline ⇒ a downloaded LOCAL muxed file is STILL available`() {
        assertEquals(RenditionKind.LOCAL, availability(online = false, localVideoFile = true)?.kind)
    }

    // --- isVideoDownloadItem (Option A: video-capable ⇒ muxed download) -----

    @Test
    fun `video item downloads muxed — by flag or by non-ATV type`() {
        assertEquals(true, VideoModeLogic.isVideoDownloadItem(musicVideoType = null, metadataIsVideo = true))
        assertEquals(true, VideoModeLogic.isVideoDownloadItem(musicVideoType = OMV, metadataIsVideo = false))
    }

    @Test
    fun `plain ATV or unknown song stays an audio download`() {
        assertEquals(false, VideoModeLogic.isVideoDownloadItem(musicVideoType = ATV, metadataIsVideo = false))
        assertEquals(false, VideoModeLogic.isVideoDownloadItem(musicVideoType = null, metadataIsVideo = false))
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

    // --- shouldRestoreDepartedItem (the "tap a new song in video mode" bug) --

    @Test
    fun `restore departed item when our video rendition is still parked at the index`() {
        // Within-queue skip/seek: index still holds SONG as a video rendition → restore it to audio.
        assertEquals(true, VideoModeLogic.shouldRestoreDepartedItem("SONG", "SONG", itemAtIndexIsVideoKey = true))
    }

    @Test
    fun `do NOT restore when a fresh playQueue replaced the index with the tapped song`() {
        // The bug: index now holds a DIFFERENT tapped song (audio, no video key) → must not clobber it.
        assertEquals(false, VideoModeLogic.shouldRestoreDepartedItem("SONG", "TAPPED", itemAtIndexIsVideoKey = false))
    }

    @Test
    fun `do NOT restore when the same id sits at the index but is no longer a video rendition`() {
        // Re-tapping the same song: id matches but the new item is plain audio (not a video key) → skip.
        assertEquals(false, VideoModeLogic.shouldRestoreDepartedItem("SONG", "SONG", itemAtIndexIsVideoKey = false))
    }

    @Test
    fun `do NOT restore when the index holds a different id even if it were a video key`() {
        assertEquals(false, VideoModeLogic.shouldRestoreDepartedItem("SONG", "OTHER", itemAtIndexIsVideoKey = true))
    }

    @Test
    fun `do NOT restore when there is no departed item id`() {
        assertEquals(false, VideoModeLogic.shouldRestoreDepartedItem(null, "SONG", itemAtIndexIsVideoKey = true))
    }
}

private typealias Rendition = VideoModeLogic.Rendition
