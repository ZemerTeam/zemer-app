package com.jtech.zemer.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the send-always / fail-closed query contract for `/search`. The Zemer server is default-OPEN
 * (an omitted flag = "don't filter that category"), so the content flags must be emitted on EVERY
 * request regardless of value — never omitted when false. Regression for the bug where `blockVideos`
 * was only sent when true, leaving video filtering dependent on the server's default.
 */
class ZemerSearchParametersTest {

    private fun paramMap(allowFemale: Boolean, blockVideos: Boolean) =
        zemerSearchParameters("query", allowFemale, blockVideos, k = 8).toMap()

    @Test
    fun `both content flags are sent even when both are false`() {
        val params = paramMap(allowFemale = false, blockVideos = false)

        // The crux: a false flag is still present (as "0"), not omitted.
        assertEquals("0", params["allowFemale"])
        assertEquals("0", params["blockVideos"])
    }

    @Test
    fun `kidZone is omitted when null, else sent as 1 or 0`() {
        // The KidZone contract: null = the endpoint takes no kidZone param; non-null endpoints send
        // it explicitly on EVERY request (fail-closed, like the other flags) - "1" only from the
        // KidZone navigation context.
        assertEquals(
            listOf("allowFemale" to "1", "blockVideos" to "0"),
            zemerContentFlagParameters(allowFemale = true, blockVideos = false),
        )
        assertEquals(
            listOf("allowFemale" to "1", "blockVideos" to "0", "kidZone" to "0"),
            zemerContentFlagParameters(allowFemale = true, blockVideos = false, kidZone = false),
        )
        assertEquals(
            listOf("allowFemale" to "1", "blockVideos" to "0", "kidZone" to "1"),
            zemerContentFlagParameters(allowFemale = true, blockVideos = false, kidZone = true),
        )
    }

    @Test
    fun `flags encode true as 1 and false as 0`() {
        assertEquals("1", paramMap(allowFemale = true, blockVideos = false)["allowFemale"])
        assertEquals("0", paramMap(allowFemale = false, blockVideos = true)["allowFemale"])
        assertEquals("1", paramMap(allowFemale = false, blockVideos = true)["blockVideos"])
        assertEquals("0", paramMap(allowFemale = true, blockVideos = false)["blockVideos"])
    }

    @Test
    fun `every request carries q, both flags, and k - and nothing is dropped`() {
        val params = zemerSearchParameters("shwekey", allowFemale = false, blockVideos = false, k = 100)

        assertEquals(
            listOf("q", "allowFemale", "blockVideos", "k"),
            params.map { it.first },
        )
        assertEquals("shwekey", params.toMap()["q"])
        assertEquals("100", params.toMap()["k"])
    }
}
