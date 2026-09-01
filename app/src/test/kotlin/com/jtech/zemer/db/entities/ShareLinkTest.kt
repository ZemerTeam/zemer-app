package com.jtech.zemer.db.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression: the playlist and album Share rows built the link by interpolating a nullable id, so a
 * playlist with no browseId or an album with no playlistId produced a dead "?list=null" link. The
 * shareLink helpers return null in that case, and the menus share only when it is non-null.
 */
class ShareLinkTest {
    @Test
    fun `playlist share link is null without a browseId`() {
        assertNull(PlaylistEntity(name = "x", browseId = null).shareLink)
    }

    @Test
    fun `playlist share link uses the browseId`() {
        assertEquals(
            "https://music.zemer.io/playlist?list=VLxyz",
            PlaylistEntity(name = "x", browseId = "VLxyz").shareLink,
        )
    }

    @Test
    fun `album share link is null without a playlistId`() {
        assertNull(albumWith(playlistId = null).shareLink)
    }

    @Test
    fun `album share link uses the playlistId`() {
        assertEquals(
            "https://music.zemer.io/playlist?list=OLAK5uy_x",
            albumWith(playlistId = "OLAK5uy_x").shareLink,
        )
    }

    private fun albumWith(playlistId: String?) =
        AlbumEntity(id = "MPREb_x", playlistId = playlistId, title = "x", songCount = 1, duration = 0)
}
