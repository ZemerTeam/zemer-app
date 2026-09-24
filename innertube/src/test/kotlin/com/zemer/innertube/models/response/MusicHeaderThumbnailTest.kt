package com.zemer.innertube.models.response

import com.metrolist.innertube.models.response.BrowseResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression: MusicHeaderRenderer.thumbnail was a self-recursive, non-nullable MusicThumbnailRenderer,
 * so any browse response carrying that header failed to decode wholesale (the failure swallowed by
 * runCatching), which could silently stop liked-songs sync from ever running. It is now the shared
 * ThumbnailRenderer, and the reader reaches one level deeper (musicThumbnailRenderer.thumbnail.thumbnails).
 */
class MusicHeaderThumbnailTest {
    // Match InnerTube's production decoder: explicitNulls = false lets a real partial thumbnail (only
    // musicThumbnailRenderer present) decode, which is exactly what a browse header sends.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun decode(body: String) =
        json.decodeFromString<BrowseResponse.Header.MusicHeaderRenderer>(body)

    @Test
    fun `header with a thumbnail decodes and exposes the url`() {
        val header = decode(
            """
            {
              "title": { "runs": [ { "text": "Liked Music" } ] },
              "thumbnail": {
                "musicThumbnailRenderer": {
                  "thumbnail": { "thumbnails": [ { "url": "https://img/last.jpg", "width": 60, "height": 60 } ] }
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(
            "https://img/last.jpg",
            header.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url,
        )
    }

    @Test
    fun `header without a thumbnail still decodes`() {
        val header = decode("""{ "title": { "runs": [ { "text": "X" } ] } }""")
        assertNull(header.thumbnail)
    }
}
