package com.jtech.zemer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The podcast catalog's grouping contract (`zemer-app-podcast-genre-kinds-request.md`): sections
 * follow the server-owned `kinds` order and titles; unknown/blank kinds fall to a trailing
 * headerless section (never dropped — unlike music's fail-closed kind gate); no `kinds` = flat.
 */
class PodcastGenreSectionsTest {

    private fun genre(id: String, kind: String? = null) =
        ZemerPodcastGenreSummary(id = id, title = id.replaceFirstChar(Char::uppercase), kind = kind)

    private val kinds = listOf(
        ZemerPodcastGenreKind("torah", "Torah"),
        ZemerPodcastGenreKind("life", "Life"),
        ZemerPodcastGenreKind("talk", "Talk"),
    )

    @Test
    fun `sections follow the server kinds order with server titles and within-kind order`() {
        val catalog = PodcastGenreCatalog(
            kinds = kinds,
            genres = listOf(genre("family", "life"), genre("gemara", "torah"), genre("parsha", "torah")),
        )
        val sections = podcastGenreSections(catalog)
        assertEquals(listOf("Torah", "Life"), sections.map { it.title })
        assertEquals(listOf("gemara", "parsha"), sections[0].genres.map { it.id })
        assertEquals(listOf("family"), sections[1].genres.map { it.id })
        // "talk" has no members -> no header rendered for it.
        assertTrue(sections.none { it.title == "Talk" })
    }

    @Test
    fun `unknown or blank kind falls to a trailing headerless section, never dropped`() {
        val catalog = PodcastGenreCatalog(
            kinds = kinds,
            genres = listOf(genre("gemara", "torah"), genre("newthing", "brand-new-kind"), genre("orphan", null)),
        )
        val sections = podcastGenreSections(catalog)
        assertEquals(2, sections.size)
        assertNull(sections.last().title)
        assertEquals(listOf("newthing", "orphan"), sections.last().genres.map { it.id })
    }

    @Test
    fun `no kinds catalog means one headerless flat section - the pre-kinds and offline render`() {
        val catalog = PodcastGenreCatalog(genres = listOf(genre("gemara"), genre("family")))
        val sections = podcastGenreSections(catalog)
        assertEquals(1, sections.size)
        assertNull(sections.single().title)
        assertEquals(listOf("gemara", "family"), sections.single().genres.map { it.id })
    }

    @Test
    fun `empty catalog renders nothing`() {
        assertTrue(podcastGenreSections(PodcastGenreCatalog()).isEmpty())
        assertTrue(podcastGenreSections(PodcastGenreCatalog(kinds = kinds)).isEmpty())
    }
}
