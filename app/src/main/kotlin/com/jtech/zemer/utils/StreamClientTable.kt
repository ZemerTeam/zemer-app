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
data class StreamClient(
    val client: YouTubeClient,
    val family: String,
    /** The table entry key (stall tracking, telemetry labels); the compiled floor keys by constant name. */
    val key: String = client.clientName,
    /**
     * Present = SABR-usable: the SABR resolvers' roster is the table's sabr entries in table order
     * ([StreamClientTable.Table.sabrRoster]). Its fields override the client's os/device identity
     * for the SABR streamerContext only — the `/player` context is [client]'s own.
     */
    val sabr: StreamClientParser.StreamClientDef.SabrInfo? = null,
)

object StreamClientTable {

    data class Table(val main: StreamClient, val fallbacks: List<StreamClient>) {
        /**
         * The SABR roster: every sabr-capable entry whose capability is not benched
         * (`sabr.enabled: false` — the monitor's SABR-only kill switch), main first, in table order.
         */
        val sabrRoster: List<StreamClient> get() = (listOf(main) + fallbacks).filter { it.sabr?.enabled == true }
    }

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
        key = key,
        sabr = sabr,
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
        // SABR identity: WEB_REMIX announces Windows 10.0 in the SABR streamerContext (its /player
        // context stays OS-less); VISIONOS and TVHTML5_SIMPLY use their own identity. VISIONOS_0_1
        // and WEB_CREATOR are DIRECT-only (WEB_CREATOR is attestation-throttled over SABR).
        main = StreamClient(
            WEB_REMIX, "WEB_REMIX", key = "WEB_REMIX",
            sabr = StreamClientParser.StreamClientDef.SabrInfo(osName = "Windows", osVersion = "10.0"),
        ),
        fallbacks = listOf(
            StreamClient(VISIONOS, "VISIONOS", key = "VISIONOS", sabr = StreamClientParser.StreamClientDef.SabrInfo()),
            StreamClient(VISIONOS_0_1, "VISIONOS", key = "VISIONOS_0_1"),
            StreamClient(WEB_CREATOR, "WEB_CREATOR", key = "WEB_CREATOR"),
            StreamClient(TVHTML5_SIMPLY, "TVHTML5", key = "TVHTML5_SIMPLY", sabr = StreamClientParser.StreamClientDef.SabrInfo()),
        ),
    )
}
