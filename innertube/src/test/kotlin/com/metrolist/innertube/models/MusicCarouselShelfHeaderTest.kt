package com.metrolist.innertube.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression: a carousel shelf header can arrive with no title Runs. When title was a non-null
 * required field, decoding a single titleless shelf threw MissingFieldException and failed the
 * WHOLE browse response - the related shelf, the Android Auto browse root and artist search all
 * went empty together, silently. title must be optional.
 */
class MusicCarouselShelfHeaderTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String) =
        json.decodeFromString<MusicCarouselShelfRenderer.Header.MusicCarouselShelfBasicHeaderRenderer>(body)

    @Test
    fun `header decodes when title is absent`() {
        val header = decode("""{ "strapline": null, "thumbnail": null, "moreContentButton": null }""")
        assertNull(header.title)
    }

    @Test
    fun `header still reads the title when present`() {
        val header = decode(
            """{ "strapline": null, "title": { "runs": [ { "text": "Featured", "navigationEndpoint": null } ] }, "thumbnail": null, "moreContentButton": null }"""
        )
        assertEquals("Featured", header.title?.runs?.firstOrNull()?.text)
    }
}
