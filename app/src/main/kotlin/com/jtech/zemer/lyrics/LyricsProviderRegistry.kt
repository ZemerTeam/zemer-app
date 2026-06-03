package com.jtech.zemer.lyrics

object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "BetterLyrics" to BetterLyricsProvider,
        "SimpMusic" to SimpMusicLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "KuGou" to KuGouLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTube" to YouTubeLyricsProvider,
    )

    val providerNames: List<String> = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun deserializeProviderOrder(orderString: String): List<String> =
        if (orderString.isBlank()) {
            getDefaultProviderOrder()
        } else {
            orderString.split(",").map { it.trim() }.filter { it in providerNames }
        }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics",
        "SimpMusic",
        "LrcLib",
        "KuGou",
        "YouTubeSubtitle",
        "YouTube",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }
}
