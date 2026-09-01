package com.jtech.zemer.playback.sabr

import com.jtech.zemer.utils.StreamClient
import com.jtech.zemer.utils.StreamClientTable
import com.metrolist.innertube.models.StreamProtocol
import com.metrolist.innertube.models.YouTubeClient

/**
 * One SABR-usable client as the resolvers consume it: the innertube client for `/player`, whether
 * it is a web client (ciphered serverAbrStreamingUrl: n-transform + videoId url-pot + web pot/sts
 * in `/player`), and the identity announced in the SABR streamerContext.clientInfo.
 */
internal class SabrClientSpec(
    /** The table entry key — stall tracking and telemetry are keyed by it. */
    val key: String,
    /** The toggle family (the per-family SABR switch in Stream Sources). */
    val family: String,
    val client: YouTubeClient,
    val label: String,
    val web: Boolean,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdk: Int? = null,
)

/**
 * The SABR roster is TABLE data, not a compiled list: the stream-client table's sabr-capable entries
 * ([StreamClientTable.Table.sabrRoster]), in table order — so a client kill or a newly validated
 * SABR client is a config push (`stream_clients.json`, see AGENTS.md §Remote stream-client config),
 * not an APK release. Only entries validated to deliver a WHOLE song over SABR with the app's pot
 * (`tests/sabr-clients.mjs`) carry a `sabr` object.
 */
internal object SabrRoster {

    /** The current roster, snapshotted from the current table (one snapshot per resolution). */
    fun current(): List<SabrClientSpec> = StreamClientTable.current().sabrRoster.map { spec(it) }

    /**
     * The clients one resolution tries, in order: the roster's ENABLED families, non-stalled first;
     * a fully-stalled roster still retries the stalled ones (last hope). [stalledKeys] are entry
     * keys ([SabrPlayerResolver.stalledFor]); [enabledFamilies] are family ids
     * (StreamSourcePrefs.enabledSabrFamilies).
     */
    fun order(enabledFamilies: Set<String>, stalledKeys: Set<String>): List<SabrClientSpec> =
        order(current(), enabledFamilies, stalledKeys)

    /** Pure ordering over a given [roster] — JVM-tested. */
    fun order(roster: List<SabrClientSpec>, enabledFamilies: Set<String>, stalledKeys: Set<String>): List<SabrClientSpec> {
        val enabled = roster.filter { it.family in enabledFamilies }
        return enabled.filter { it.key !in stalledKeys } + enabled.filter { it.key in stalledKeys }
    }

    /**
     * A table entry → its SABR spec. The `sabr` object's fields override the client's own
     * os/device identity for the streamerContext (null = inherit); the `/player` request still
     * carries the client's own context. Requires a sabr-capable entry.
     */
    fun spec(entry: StreamClient): SabrClientSpec {
        val sabr = requireNotNull(entry.sabr) { "${entry.key} is not SABR-capable" }
        val client = entry.client
        return SabrClientSpec(
            key = entry.key,
            family = entry.family,
            client = client,
            label = "${entry.key} (SABR)",
            web = client.protocol == StreamProtocol.WEB_CIPHER_POT,
            osName = sabr.osName ?: client.osName,
            osVersion = sabr.osVersion ?: client.osVersion,
            deviceMake = sabr.deviceMake ?: client.deviceMake,
            deviceModel = sabr.deviceModel ?: client.deviceModel,
            androidSdk = (sabr.androidSdkVersion ?: client.androidSdkVersion)?.toIntOrNull(),
        )
    }
}
