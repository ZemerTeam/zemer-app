package com.jtech.zemer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shared [zemerResponseJson] config the client decodes every response with — specifically
 * `coerceInputValues`. kotlinx applies a field's default only for an ABSENT key, so without coercion an
 * explicit JSON `null` on a non-null defaulted field throws and fails the WHOLE response (the strict-
 * deserialization "No results" trap). These prove a sparse/odd payload degrades to defaults instead.
 */
class ZemerSearchJsonTest {

    @Test
    fun `explicit null on a non-null defaulted field decodes to the default, not an exception`() {
        val payload =
            """{"q":"x","count":0,"categories":{"songs":null,"community":null,""" +
                """"playlists":[{"id":"p1","title":"PL","artist":"A","thumbnail":null,"whitelisted":null}]}}"""

        val resp = zemerResponseJson.decodeFromString(ZemerSearchResponse.serializer(), payload)

        assertTrue(resp.categories.songs.isEmpty())       // null list → default empty, no throw
        assertTrue(resp.categories.community.isEmpty())
        assertEquals(listOf("p1"), resp.categories.playlists.map { it.id })
        assertEquals(null, resp.categories.playlists.single().songCount) // nullable stays null
    }

    @Test
    fun `explicit null categories object decodes to the default empty categories`() {
        val resp = zemerResponseJson.decodeFromString(
            ZemerSearchResponse.serializer(),
            """{"q":"x","categories":null}""",
        )

        assertTrue(resp.categories.songs.isEmpty())
        assertTrue(resp.categories.playlists.isEmpty())
        assertTrue(resp.categories.community.isEmpty())
    }
}
