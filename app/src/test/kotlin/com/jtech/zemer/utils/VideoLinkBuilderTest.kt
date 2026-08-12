package com.jtech.zemer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoLinkBuilderTest {

    @Test
    fun `watch and video links carry the id on the right host`() {
        assertEquals("https://music.zemer.io/watch?v=abc123", VideoLinkBuilder.watchLink("abc123"))
        assertEquals("https://video.zemer.io/watch?v=abc123", VideoLinkBuilder.videoLink("abc123"))
    }

    @Test
    fun `episode link appends the owning show id`() {
        assertEquals(
            "https://music.zemer.io/watch?v=abc123&podcast=MPSPxyz",
            VideoLinkBuilder.episodeLink("abc123", "MPSPxyz"),
        )
    }

    @Test
    fun `episode link without a show id degrades to the plain watch link`() {
        assertEquals("https://music.zemer.io/watch?v=abc123", VideoLinkBuilder.episodeLink("abc123", null))
        assertEquals("https://music.zemer.io/watch?v=abc123", VideoLinkBuilder.episodeLink("abc123", ""))
    }

    @Test
    fun `channel link parses back through the channel deep link path`() {
        assertEquals("https://music.zemer.io/channel/UCabc", VideoLinkBuilder.channelLink("UCabc"))
    }

    @Test
    fun `shareLink routes episodes to the show-tagged link and songs to the plain watch link`() {
        assertEquals(
            "https://music.zemer.io/watch?v=abc123&podcast=MPSPxyz",
            VideoLinkBuilder.shareLink("abc123", isEpisode = true, collectionId = "MPSPxyz"),
        )
        assertEquals(
            "https://music.zemer.io/watch?v=abc123",
            VideoLinkBuilder.shareLink("abc123", isEpisode = true, collectionId = null),
        )
        // A song's collection id is a music album id and must not ride the link.
        assertEquals(
            "https://music.zemer.io/watch?v=abc123",
            VideoLinkBuilder.shareLink("abc123", isEpisode = false, collectionId = "MPREalbum"),
        )
    }
}
