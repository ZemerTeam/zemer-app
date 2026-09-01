package com.jtech.zemer.utils

import com.metrolist.innertube.models.StreamProtocol
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS_0_1
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.zemer.cipher.StreamClientParser
import com.zemer.cipher.StreamClientStore

/**
 * The stream-client table the resolution chain runs on: the remote/bundled
 * [StreamClientStore] config mapped to [YouTubeClient]s, with the compiled-in constants as the
 * floor below the floor (used only when neither the bundled asset nor a cached remote copy could
 * be loaded — the table must never be empty).
 *
 * A client's toggle identity is its FAMILY (several entries may share one — the VISIONOS
 * second-chance pair), so every entry carries it alongside the mapped client.
 */
data class StreamClient(val client: YouTubeClient, val family: String)

object StreamClientTable {

    data class Table(val main: StreamClient, val fallbacks: List<StreamClient>)

    /** Maps a validated config entry onto the innertube request-builder model. */
    fun StreamClientParser.StreamClientDef.toStreamClient(): StreamClient = StreamClient(
        client = YouTubeClient(
            clientName = clientName,
            clientVersion = clientVersion,
            clientId = clientId,
            userAgent = userAgent,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            loginSupported = loginSupported,
            loginRequired = loginRequired,
            isEmbedded = isEmbedded,
            protocol = when (protocol) {
                StreamClientParser.StreamClientDef.Protocol.WEB_CIPHER_POT -> StreamProtocol.WEB_CIPHER_POT
                StreamClientParser.StreamClientDef.Protocol.DIRECT -> StreamProtocol.DIRECT
            },
            skipHeadValidation = skipHeadValidation,
        ),
        family = family,
    )

    /** Pure mapping from a parsed config (entry 0 = main) — JVM-testable without the store. */
    fun fromConfig(config: StreamClientParser.StreamClientConfig): Table {
        val mapped = config.clients.map { it.toStreamClient() }
        return Table(main = mapped.first(), fallbacks = mapped.drop(1))
    }

    /**
     * The current table: the store's active config (remote/cached/bundled), or the compiled
     * constants when the store has nothing. Snapshot ONCE per resolution — a refresh landing
     * mid-resolution must not switch tables under the loop.
     */
    fun current(): Table {
        val config = StreamClientStore.config() ?: return COMPILED_TABLE
        return fromConfig(config)
    }

    /**
     * The compiled floor — the validated chain (the 2026-08-15 pass, minus the ANDROID_VR 1.65.10
     * and MWEB removals that followed it: see AGENTS.md), kept in lockstep with the bundled
     * `stream_clients.json` (StreamClientsBundledAssetTest pins the asset's order; the app-side
     * StreamClientTableTest pins this list against it conceptually). Order rationale lives in the
     * asset + git history: VISIONOS first (no `spc` gate — the most reliable fallback), the
     * second-chance 0.1 config behind it, then the cipher clients.
     */
    internal val COMPILED_TABLE = Table(
        main = StreamClient(WEB_REMIX, "WEB_REMIX"),
        fallbacks = listOf(
            StreamClient(VISIONOS, "VISIONOS"),
            StreamClient(VISIONOS_0_1, "VISIONOS"),
            StreamClient(WEB_CREATOR, "WEB_CREATOR"),
            StreamClient(TVHTML5_SIMPLY, "TVHTML5"),
        ),
    )
}
