package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shard decoders to the real wire format, using rows sampled from the live
 * `search.zemer.io` subset shards. Guards the positional layouts and packed-flag bits against a
 * drift in `zemer-search/index/build-subset.mjs`.
 */
class SubsetDecoderTest {

    @Test
    fun `artists decode with packed flag bits`() {
        val a = SubsetDecoder.decodeArtists(
            """[["UC-2KKdSj6zy8SZ8jFngR91g","נתנאל זלבסקי","https://yt3/x=w2880",0],
                ["UCx","Fem Chasid Kid","https://t",7]]""",
        )
        assertEquals("UC-2KKdSj6zy8SZ8jFngR91g", a[0].id)
        assertEquals("נתנאל זלבסקי", a[0].name)
        assertFalse(a[0].isFemale); assertFalse(a[0].isChasid); assertFalse(a[0].isKidZone)
        // flags = 7 = 1|2|4
        assertTrue(a[1].isFemale); assertTrue(a[1].isChasid); assertTrue(a[1].isKidZone)
    }

    @Test
    fun `tracks decode positions, video and explicit bits, and nullable playCount and date`() {
        val t = SubsetDecoder.decodeTracks(
            """[["--CBg_MWYAI","Mi Ha'ish","UCPgvMW042hVrcLn73zGyeqg",0,239,7300,"2015-07-02T05:27:44-07:00"],
                ["--sU8olZCYM","Nafshi Acapella","UCu6IT3jTOtAcNfjIQSIyCQQ",1,262,null,null],
                ["x","Ex","UCa",2,100,5,null]]""",
        )
        assertEquals("--CBg_MWYAI", t[0].videoId)
        assertEquals("UCPgvMW042hVrcLn73zGyeqg", t[0].artistId)
        assertFalse(t[0].isVideo); assertFalse(t[0].explicit)
        assertEquals(239, t[0].durationSec); assertEquals(7300L, t[0].playCount)
        assertEquals("2015-07-02T05:27:44-07:00", t[0].uploadDate)
        assertTrue(t[1].isVideo); assertFalse(t[1].explicit)
        assertNull(t[1].playCount); assertNull(t[1].uploadDate)
        // flags = 2 → explicit, not video
        assertFalse(t[2].isVideo); assertTrue(t[2].explicit)
    }

    @Test
    fun `albums and album_tracks decode`() {
        val al = SubsetDecoder.decodeAlbums(
            """[["MPREb_01R54uSBAcI","OLAK5uy_ktyH","Misratzeh","UCchRbwcne","single",2025,"https://t","2026-06-15T10:55:35-07:00"],
                ["MPREb_x",null,"NoPlaylist","UCa","album",null,null,null]]""",
        )
        assertEquals("OLAK5uy_ktyH", al[0].playlistId)
        assertEquals("single", al[0].type); assertEquals(2025, al[0].year)
        assertNull(al[1].playlistId); assertNull(al[1].year); assertEquals("album", al[1].type)

        val at = SubsetDecoder.decodeAlbumTracks("""[["MPREb_01R54uSBAcI","6wA415N97Xs",0],["MPREb_Y","vid2",7]]""")
        assertEquals("MPREb_01R54uSBAcI", at[0].albumId); assertEquals("6wA415N97Xs", at[0].videoId); assertEquals(0, at[0].pos)
        assertEquals(7, at[1].pos)
    }

    @Test
    fun `community and community_tracks decode with nullable resolved artist`() {
        val c = SubsetDecoder.decodeCommunity(
            """[["LRSR4D8","March-May Recap '24","Moris SEVILLA","https://t",48,37,null],
                ["LRb","T","auth","https://t",10,3,999]]""",
        )
        assertEquals(48, c[0].total); assertEquals(37, c[0].whitelisted); assertNull(c[0].viewCount)
        assertEquals(999L, c[1].viewCount)

        val ct = SubsetDecoder.decodeCommunityTracks(
            """[["LRSR4D8","cUMhXx3HssY",0,null],["LRSR4D8","RD0m1pI8uPg",2,"UCzqpZ8XdZsLBGNYKbEZn-TA"]]""",
        )
        assertNull(ct[0].artistId)
        assertEquals("UCzqpZ8XdZsLBGNYKbEZn-TA", ct[1].artistId); assertEquals(2, ct[1].pos)
    }

    @Test
    fun `homerank, zemer and blocked object shards decode`() {
        val hr = SubsetDecoder.decodeHomeRank(
            """[{"row":"top-albums","kind":"album","refId":"MPREb_BSx5FtOl9u3","artistId":"UC4yNv","pos":0,"score":0.95},
                {"row":"top-artists","kind":"artist","refId":"UCa","artistId":null,"pos":1,"score":null}]""",
        )
        assertEquals("top-albums", hr[0].row); assertEquals("MPREb_BSx5FtOl9u3", hr[0].refId); assertEquals(0.95, hr[0].score!!, 1e-9)
        assertNull(hr[1].artistId); assertNull(hr[1].score)

        val (pls, items) = SubsetDecoder.decodeZemer(
            """{"playlists":[{"id":"auto-top-50","title":"Top 50","pos":0,"year":null},{"id":"auto-year-2026","title":"Year of 2026","pos":4,"year":2026}],
                "items":[{"playlistId":"auto-top-50","kind":"track","refId":"MJUjf4hLTlo","pos":0}]}""",
        )
        assertEquals("auto-top-50", pls[0].id); assertNull(pls[0].year); assertEquals(2026, pls[1].year)
        assertEquals("track", items[0].kind); assertEquals("MJUjf4hLTlo", items[0].refId)

        val blocked = SubsetDecoder.decodeBlocked("""{"global":["jvEXWajQUlQ"],"female":["0ynSwHjCOSQ","6V8hM6RbaAI"]}""")
        assertTrue("jvEXWajQUlQ" in blocked.global)
        assertEquals(2, blocked.female.size)
        assertTrue("6V8hM6RbaAI" in blocked.female)
    }
}
