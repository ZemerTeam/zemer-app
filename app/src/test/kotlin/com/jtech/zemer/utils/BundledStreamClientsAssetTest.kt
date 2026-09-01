package com.jtech.zemer.utils

import com.zemer.cipher.StreamClientParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled `stream_clients.json` — not the compiled constants — is what every device actually
 * sends: [StreamClientTable.current] falls back to [StreamClientTable.COMPILED_TABLE] only when the
 * asset fails to parse, which never happens once it does. So a one-digit typo in the asset
 * (clientId "6", a mangled clientVersion, a dropped loginSupported) would ship fleet-wide with
 * every other test still green.
 *
 * This pins the asset AGAINST the compiled floor, making that floor the golden reference its own
 * doc comment claims it is. A deliberate chain change updates both in the same commit.
 */
class BundledStreamClientsAssetTest {

    private fun assetFile(): File {
        val candidates = listOf(
            File("../cipher/library/src/main/assets/stream_clients.json"),
            File("cipher/library/src/main/assets/stream_clients.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("stream_clients.json not found from ${File(".").absolutePath}")
    }

    @Test
    fun `bundled asset maps exactly onto the compiled floor`() {
        val result = StreamClientParser.parse(assetFile().readText())
        assertTrue("bundled asset must parse: $result", result is StreamClientParser.ParseResult.Success)
        val config = (result as StreamClientParser.ParseResult.Success).config
        assertTrue("bundled asset must skip nothing", result.skippedEntries.isEmpty())

        val fromAsset = StreamClientTable.fromConfig(config)
        val compiled = StreamClientTable.COMPILED_TABLE
        // Structural equality over every client AND the family ids, in order — minus what the
        // cipher client-monitor is allowed to change unattended: a BENCHED entry (`enabled: false`)
        // drops out of the parsed chain, and an entry's mirrored IDENTITY (clientVersion, userAgent,
        // os/device fields) follows yt-dlp master once a candidate drained whole songs. Everything
        // else - key, clientName, clientId, protocol, family, flags, sabr - must match the floor;
        // never a different, reordered or extra entry, never a benched main.
        assertEquals(compiled.main.structural(), fromAsset.main.structural())
        val floor = compiled.fallbacks
        val live = fromAsset.fallbacks
        assertEquals(
            "asset fallbacks must be the compiled floor in order, minus benched entries: $live",
            floor.filter { f -> live.any { it.key == f.key } }.map { it.structural() },
            live.map { it.structural() },
        )
    }

    /** The entry with its bumpable identity blanked - what a version/UA bump may NOT change. */
    private fun StreamClient.structural() = copy(
        client = client.copy(
            clientVersion = "", userAgent = "", osName = null, osVersion = null,
            deviceMake = null, deviceModel = null, androidSdkVersion = null,
        ),
    )
}
