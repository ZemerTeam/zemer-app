package com.zemer.innertube.models

import com.metrolist.innertube.models.Thumbnails
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A thumbnail object without its list (seen in a `next` queue panel) decodes as empty instead of failing the response. */
class ThumbnailsDecodeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `a missing thumbnails list decodes as empty`() {
        assertTrue(json.decodeFromString<Thumbnails>("{}").thumbnails.isEmpty())
    }

    @Test
    fun `a present list still decodes`() {
        val t = json.decodeFromString<Thumbnails>("""{"thumbnails":[{"url":"https://img/a.jpg","width":60,"height":60}]}""")
        assertEquals("https://img/a.jpg", t.thumbnails.single().url)
    }
}
