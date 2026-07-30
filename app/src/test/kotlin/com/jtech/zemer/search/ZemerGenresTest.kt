package com.jtech.zemer.search

import com.jtech.zemer.search.ZemerResultMapper.toGenrePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the `/genres` integration contract (handoff: zemer-app-genres.md):
 * - send-always / fail-closed flags — all three content flags on every request (catalog AND page),
 *   `offset` only when actually paging;
 * - lenient wire decoding — the doc'd payload shapes, unknown fields ignored, null/absent arrays
 *   degrade instead of failing the whole response;
 * - kind handling — non-music and UNKNOWN kinds never reach a music surface (fail-closed), grouping
 *   preserves the server's popularity order, the home chips row is capped;
 * - the page mapper — sparse-row drop, de-dup, hide-explicit, nextOffset passthrough.
 */
class ZemerGenresTest {

    // ---- query-parameter contract ----

    @Test
    fun `catalog request always carries all three content flags, even when all-open`() {
        val params = zemerGenresParameters(id = null, allowFemale = true, blockVideos = false)

        assertEquals(listOf("allowFemale", "blockVideos", "kidZone"), params.map { it.first })
        assertEquals("1", params.toMap()["allowFemale"])
        assertEquals("0", params.toMap()["blockVideos"])
        assertEquals("0", params.toMap()["kidZone"])
    }

    @Test
    fun `page request carries the slug plus the same three flags, no offset on page zero`() {
        val params = zemerGenresParameters(id = "purim", allowFemale = false, blockVideos = true)

        assertEquals(listOf("id", "allowFemale", "blockVideos", "kidZone"), params.map { it.first })
        assertEquals("purim", params.toMap()["id"])
        // The crux: a restricted user's flags are explicit, never left to the server default.
        assertEquals("0", params.toMap()["allowFemale"])
        assertEquals("1", params.toMap()["blockVideos"])
    }

    @Test
    fun `offset is sent only when paging past the first page`() {
        val paged = zemerGenresParameters(id = "purim", allowFemale = true, blockVideos = false, offset = 100)

        assertEquals(listOf("id", "allowFemale", "blockVideos", "kidZone", "offset"), paged.map { it.first })
        assertEquals("100", paged.toMap()["offset"])
    }

    // ---- wire decoding ----

    @Test
    fun `catalog response decodes the doc'd payload in server order`() {
        val payload =
            """{"count":2,"genres":[""" +
                """{"id":"nigunim","title":"Nigunim","trackCount":2708,"kind":"style"},""" +
                """{"id":"acapella","title":"Acapella","trackCount":2261,"kind":"style"}]}"""

        val resp = zemerResponseJson.decodeFromString(ZemerGenresResponse.serializer(), payload)

        assertEquals(listOf("nigunim", "acapella"), resp.genres.map { it.id })
        assertEquals("Nigunim", resp.genres.first().title)
        assertEquals(2708, resp.genres.first().trackCount)
        assertEquals("style", resp.genres.first().kind)
    }

    @Test
    fun `page response decodes the doc'd payload and ignores the extra per-row fields`() {
        val payload =
            """{"genre":{"id":"purim","title":"Purim","kind":"occasion","trackCount":236,"artistCount":11,"albumCount":2,"singleCount":1},""" +
                """"artists":[{"id":"UCa","name":"Benny Friedman","trackCount":59,"thumbnail":"https://t"}],""" +
                """"albums":[{"id":"MPREa","playlistId":"OLAKa","title":"It Sounds Like Purim!","artist":"Benny Friedman","year":2022,"releaseDate":"2022-03-07T00:00:00Z","thumbnail":"https://t","trackCount":59}],""" +
                """"singles":[{"id":"MPREs","playlistId":"OLAKs","title":"Purimmix 2020","artist":"Simcha Leiner","year":2020,"trackCount":6}],""" +
                """"songs":[{"videoId":"QXLWMz4KpJg","title":"Mishenichnas Adar","artist":"Benny Friedman","artistId":"UCa","durationSec":62,"explicit":false,"isVideo":false,"releaseDate":"2022-03-07T00:00:00Z","genres":["dance","purim"],"album":{"id":"MPREa","name":"It Sounds Like Purim!"}}],""" +
                """"videos":[],"offset":0,"nextOffset":100}"""

        val resp = zemerResponseJson.decodeFromString(ZemerGenrePageResponse.serializer(), payload)

        assertEquals("purim", resp.genre.id)
        assertEquals(236, resp.genre.trackCount)
        assertEquals(11, resp.genre.artistCount)
        assertEquals("Benny Friedman", resp.artists.single().name)
        assertEquals("OLAKa", resp.albums.single().playlistId)
        assertEquals("MPREs", resp.singles.single().id)
        assertEquals("QXLWMz4KpJg", resp.songs.single().videoId)
        assertEquals(62, resp.songs.single().durationSec)
        assertEquals(100, resp.nextOffset)
    }

    @Test
    fun `final page's null nextOffset and absent arrays both decode cleanly`() {
        val resp = zemerResponseJson.decodeFromString(
            ZemerGenrePageResponse.serializer(),
            """{"genre":{"id":"calm","title":"Chill","kind":"style","trackCount":1},"songs":null,"offset":200,"nextOffset":null}""",
        )

        assertNull(resp.nextOffset)
        assertTrue(resp.songs.isEmpty())
        assertTrue(resp.artists.isEmpty())
    }

    // ---- kind handling ----

    @Test
    fun `fromSlug maps the three doc'd kinds and fails closed on anything else`() {
        assertEquals(GenreKind.STYLE, GenreKind.fromSlug("style"))
        assertEquals(GenreKind.OCCASION, GenreKind.fromSlug("occasion"))
        assertEquals(GenreKind.NON_MUSIC, GenreKind.fromSlug("non-music"))
        assertNull(GenreKind.fromSlug("spoken-word"))
        assertNull(GenreKind.fromSlug(""))
    }

    @Test
    fun `musicGenres drops non-music, unknown kinds, sparse and duplicate rows`() {
        val cleaned = musicGenres(
            listOf(
                ZemerGenreSummary("nigunim", "Nigunim", 2708, "style"),
                ZemerGenreSummary("shiur", "Shiurim", 900, "non-music"),
                ZemerGenreSummary("purim", "Purim", 236, "occasion"),
                ZemerGenreSummary("mystery", "Mystery", 5, "brand-new-kind"),
                ZemerGenreSummary("", "Sparse", 1, "style"),
                ZemerGenreSummary("nigunim", "Nigunim dup", 1, "style"),
            ),
        )

        assertEquals(listOf("nigunim", "purim"), cleaned.map { it.id })
    }

    @Test
    fun `editorially hidden genres never surface on browse`() {
        val cleaned = musicGenres(
            listOf(
                ZemerGenreSummary("lullaby", "Bedtime", 100, "style"),
                ZemerGenreSummary("carlebach", "Carlebach", 200, "style"),
                ZemerGenreSummary("workout", "On the Move", 300, "style"),
                ZemerGenreSummary("kids", "Kids", 350, "style"),
                ZemerGenreSummary("calm", "Chill", 400, "style"),
            ),
        )

        assertEquals(listOf("calm"), cleaned.map { it.id })
        assertEquals(setOf("lullaby", "carlebach", "workout", "kids"), HIDDEN_GENRE_SLUGS)
    }

    @Test
    fun `acapella pins to the end of its surface without being evicted by the home cap`() {
        val catalog = listOf(
            ZemerGenreSummary("nigunim", "Nigunim", 2708, "style"),
            ZemerGenreSummary("acapella", "Acapella", 2261, "style"),
            ZemerGenreSummary("calm", "Chill", 800, "style"),
        )

        // Catalog grouping: acapella closes its Styles bucket.
        assertEquals(
            listOf("nigunim", "calm", "acapella"),
            genresByKind(catalog)[GenreKind.STYLE]?.map { it.id },
        )
        // Home strip: the cap selects by popularity FIRST (acapella qualifies), then pins it last.
        assertEquals(listOf("nigunim", "acapella"), homeGenreChips(catalog, max = 2).map { it.id })
    }

    @Test
    fun `genresByKind buckets styles and occasions preserving server popularity order`() {
        val groups = genresByKind(
            listOf(
                ZemerGenreSummary("nigunim", "Nigunim", 2708, "style"),
                ZemerGenreSummary("purim", "Purim", 236, "occasion"),
                ZemerGenreSummary("calm", "Chill", 800, "style"),
                ZemerGenreSummary("shiur", "Shiurim", 900, "non-music"),
            ),
        )

        assertEquals(listOf("nigunim", "calm"), groups[GenreKind.STYLE]?.map { it.id })
        assertEquals(listOf("purim"), groups[GenreKind.OCCASION]?.map { it.id })
        assertNull(groups[GenreKind.NON_MUSIC])
    }

    @Test
    fun `home chips are the most-populated music genres, capped`() {
        val many = (1..20).map { ZemerGenreSummary("g$it", "G$it", 100 - it, "style") } +
            ZemerGenreSummary("shiur", "Shiurim", 999, "non-music")

        val chips = homeGenreChips(many)

        assertEquals(HOME_GENRE_CHIPS_MAX, chips.size)
        assertEquals((1..HOME_GENRE_CHIPS_MAX).map { "g$it" }, chips.map { it.id })
    }

    // ---- page mapper ----

    @Test
    fun `toGenrePage maps every section, dropping sparse rows and duplicates`() {
        val page = ZemerGenrePageResponse(
            genre = ZemerGenreHeader(id = "purim", title = "Purim", kind = "occasion", trackCount = 3),
            artists = listOf(ZemerArtist("UCa", "A"), ZemerArtist("", "sparse"), ZemerArtist("UCa", "dup")),
            albums = listOf(ZemerAlbum(id = "MPREa", title = "Album", artist = "A")),
            singles = listOf(ZemerAlbum(id = "MPREs", title = "Single", artist = "B")),
            songs = listOf(ZemerTrack("v1", "S1", "A"), ZemerTrack("", "sparse", "A"), ZemerTrack("v1", "dup", "A")),
            videos = listOf(ZemerTrack("v2", "V1", "A")),
            nextOffset = 100,
        )

        val mapped = page.toGenrePage(hideExplicit = false)

        assertEquals("purim", mapped.header.id)
        assertEquals(listOf("UCa"), mapped.artists.map { it.id })
        assertEquals(listOf("MPREa"), mapped.albums.map { it.browseId })
        assertEquals(listOf("MPREs"), mapped.singles.map { it.browseId })
        assertEquals(listOf("v1"), mapped.songs.map { it.id })
        assertEquals(listOf("v2"), mapped.videos.map { it.id })
        assertEquals(100, mapped.nextOffset)
    }

    @Test
    fun `toGenrePage honors hideExplicit on both track lists and passes null nextOffset through`() {
        val page = ZemerGenrePageResponse(
            songs = listOf(ZemerTrack("clean", "C", "A"), ZemerTrack("dirty", "D", "A", explicit = true)),
            videos = listOf(ZemerTrack("vdirty", "VD", "A", explicit = true)),
            nextOffset = null,
        )

        val mapped = page.toGenrePage(hideExplicit = true)

        assertEquals(listOf("clean"), mapped.songs.map { it.id })
        assertTrue(mapped.videos.isEmpty())
        assertNull(mapped.nextOffset)
    }
}
