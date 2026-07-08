package com.metrolist.innertube.pages

import com.metrolist.innertube.models.PlaylistPanelRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parse + extraction contract for YouTube Music's song↔video counterpart carrier
 * (`playlistPanelVideoWrapperRenderer`) inside a `next()` response — the source of the video-mode
 * COUNTERPART availability.
 *
 * The fixtures here are SYNTHETIC, hand-built to ytmusicapi's documented wrapper shape (an
 * authenticated cookie was unavailable when this was written, see the unified-video step-3 report).
 * They pin the model shape and the pure extraction logic; replace the wrapper fixture with a captured
 * real authenticated response when one is available (the extraction assertions should hold verbatim).
 */
class NextCounterpartTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** A minimal-but-valid `playlistPanelVideoRenderer` JSON for [videoId] with the given music-video type. */
    private fun rendererJson(videoId: String, musicVideoType: String): String = """
        {
          "title": null, "lengthText": null, "longBylineText": null, "shortBylineText": null,
          "badges": null, "videoId": "$videoId", "playlistSetVideoId": null, "selected": false,
          "thumbnail": { "thumbnails": [] }, "unplayableText": null, "menu": null,
          "navigationEndpoint": {
            "watchEndpoint": {
              "videoId": "$videoId",
              "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": { "musicVideoType": "$musicVideoType" }
              }
            }
          }
        }
    """.trimIndent()

    private fun content(jsonBody: String): PlaylistPanelRenderer.Content =
        json.decodeFromString(jsonBody)

    @Test
    fun `wrapper content parses and yields a song to video counterpart`() {
        val wrapped = content(
            """
            {
              "playlistPanelVideoRenderer": null,
              "automixPreviewVideoRenderer": null,
              "playlistPanelVideoWrapperRenderer": {
                "primaryRenderer": { "playlistPanelVideoRenderer": ${rendererJson("SONG_ID", "MUSIC_VIDEO_TYPE_ATV")} },
                "counterpart": [
                  { "counterpartRenderer": { "playlistPanelVideoRenderer": ${rendererJson("VIDEO_ID", "MUSIC_VIDEO_TYPE_OMV")} } }
                ]
              }
            }
            """.trimIndent()
        )

        // The primary renderer is reachable (fixes the silently-dropped wrapped row).
        assertEquals("SONG_ID", NextPage.primaryRendererOf(wrapped)?.let(NextPage::videoIdOf))
        // The song→video counterpart is extracted.
        assertEquals(mapOf("SONG_ID" to "VIDEO_ID"), NextPage.counterpartsFrom(listOf(wrapped)))
    }

    @Test
    fun `a bare (unwrapped) content is unaffected and contributes no counterpart`() {
        val bare = content(
            """
            {
              "playlistPanelVideoRenderer": ${rendererJson("SONG_ID", "MUSIC_VIDEO_TYPE_ATV")},
              "automixPreviewVideoRenderer": null
            }
            """.trimIndent()
        )
        assertEquals("SONG_ID", NextPage.primaryRendererOf(bare)?.let(NextPage::videoIdOf))
        assertTrue(NextPage.counterpartsFrom(listOf(bare)).isEmpty())
    }

    @Test
    fun `a wrapper whose only counterpart is itself an audio song contributes nothing`() {
        // Primary is the video; its counterpart is the ATV song. A video queue item is its own SELF
        // rendition, so no song→video mapping should be produced here.
        val wrapped = content(
            """
            {
              "playlistPanelVideoRenderer": null,
              "automixPreviewVideoRenderer": null,
              "playlistPanelVideoWrapperRenderer": {
                "primaryRenderer": { "playlistPanelVideoRenderer": ${rendererJson("VIDEO_ID", "MUSIC_VIDEO_TYPE_OMV")} },
                "counterpart": [
                  { "counterpartRenderer": { "playlistPanelVideoRenderer": ${rendererJson("SONG_ID", "MUSIC_VIDEO_TYPE_ATV")} } }
                ]
              }
            }
            """.trimIndent()
        )
        assertTrue(NextPage.counterpartsFrom(listOf(wrapped)).isEmpty())
    }

    @Test
    fun `missing counterpart list yields no mapping`() {
        val wrapped = content(
            """
            {
              "playlistPanelVideoRenderer": null,
              "automixPreviewVideoRenderer": null,
              "playlistPanelVideoWrapperRenderer": {
                "primaryRenderer": { "playlistPanelVideoRenderer": ${rendererJson("SONG_ID", "MUSIC_VIDEO_TYPE_ATV")} },
                "counterpart": null
              }
            }
            """.trimIndent()
        )
        assertEquals("SONG_ID", NextPage.primaryRendererOf(wrapped)?.let(NextPage::videoIdOf))
        assertTrue(NextPage.counterpartsFrom(listOf(wrapped)).isEmpty())
    }

    @Test
    fun `musicVideoTypeOf reads the watch-endpoint config`() {
        val renderer = json.decodeFromString<PlaylistPanelRenderer.Content>(
            """{ "playlistPanelVideoRenderer": ${rendererJson("X", "MUSIC_VIDEO_TYPE_UGC")}, "automixPreviewVideoRenderer": null }"""
        ).playlistPanelVideoRenderer!!
        assertEquals("MUSIC_VIDEO_TYPE_UGC", NextPage.musicVideoTypeOf(renderer))
        assertNull(NextPage.musicVideoTypeOf(renderer.copy(navigationEndpoint = renderer.navigationEndpoint.copy(watchEndpoint = null))))
    }
}
