package com.zemer.innertube.models

import com.metrolist.innertube.models.Tabs
import com.metrolist.innertube.models.browseEndpointMatching
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression (#6): watch-next Lyrics/Related tabs were read by fixed index (lyrics = 1, related = 2).
 * YouTube inserted a Comments tab, shifting Related to index 3, so the positional read returned the
 * Comments tab and relatedEndpoint was null/wrong for every track (related + autoplay silently dead).
 * Resolving by page type finds the right tab regardless of an inserted one.
 */
class WatchNextTabResolutionTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // Post-insertion layout: up-next(0), lyrics(1), comments(2), related(3).
    private val tabs = json.decodeFromString<List<Tabs.Tab>>(
        """
        [
          { "tabRenderer": { "title": "Up next" } },
          { "tabRenderer": { "title": "Lyrics", "endpoint": { "browseEndpoint": { "browseId": "lyrics-id",
            "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_TRACK_LYRICS" } } } } } },
          { "tabRenderer": { "title": "Comments", "endpoint": { "browseEndpoint": { "browseId": "comments-id" } } } },
          { "tabRenderer": { "title": "Related", "endpoint": { "browseEndpoint": { "browseId": "related-id",
            "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_TRACK_RELATED" } } } } } }
        ]
        """.trimIndent(),
    )

    @Test
    fun `related resolves by page type past the inserted comments tab`() {
        assertEquals("related-id", tabs.browseEndpointMatching { it.isTrackRelatedEndpoint }?.browseId)
    }

    @Test
    fun `lyrics resolves by page type`() {
        assertEquals("lyrics-id", tabs.browseEndpointMatching { it.isTrackLyricsEndpoint }?.browseId)
    }

    @Test
    fun `the positional index that used to be related is now the comments tab`() {
        // Documents exactly why the fixed-index read broke.
        assertEquals("comments-id", tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint?.browseId)
    }
}
