package com.jtech.zemer.lyrics

import com.jtech.zemer.lyrics.zemer.ZemerLyricsProvider

/**
 * The lyrics providers by stable name, the default chain order, and the (de)serialisation of the user's
 * order preference (`LyricsProviderOrderKey`, comma-separated names). Same shape as Metrolist's registry so
 * the Content settings dialogs (provider selection, drag-to-reorder priority) port over unchanged.
 *
 * Default order is the accuracy order: the Zemer resolver (curated, cross-verified) first; the videoId-keyed
 * and identity-gated providers next; Musixmatch on-device; YouTube (lower trust) last.
 */
object LyricsProviderRegistry {
    private val providerMap = linkedMapOf<String, LyricsProvider>(
        // Accuracy first, then coverage on THIS catalog: Zemer is the only cross-verified source; SimpMusic
        // matches the exact YouTube videoId (can't mismatch); Musixmatch outranks LrcLib because on the Jewish
        // catalog Musixmatch matched ~50% of recognised tracks (most synced) while LrcLib hit ~1/120; YouTube's
        // tab is lower trust, tried last. The pick rule (synced-first among trusted providers, low-trust YouTube only
        // when nothing else answered) is SyncedFirstPicker.
        "Zemer" to ZemerLyricsProvider,
        "SimpMusic" to SimpMusicLyricsProvider,
        "Musixmatch" to MusixmatchLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTube" to YouTubeLyricsProvider,
    )

    val providerNames: List<String> = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun getDefaultProviderOrder(): List<String> = providerNames

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        val given = orderString.split(",").map { it.trim() }.filter { it in providerNames }
        // Unknown/new providers keep their default slot at the end so an old preference never drops one.
        return given + getDefaultProviderOrder().filter { it !in given }
    }

    fun serializeProviderOrder(providers: List<String>): String = providers.filter { it in providerNames }.distinct().joinToString(",")

    fun getOrderedProviders(orderString: String): List<LyricsProvider> = deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }
}
