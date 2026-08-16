package com.jtech.zemer.utils

import com.metrolist.innertube.models.YouTubeClient

/**
 * Pure resolution of the effective stream-client chain from the configured table and the user's
 * disabled FAMILIES (Settings → Stream sources). Extracted from [YTPlayerUtils] so the
 * main-promotion / disable-filtering rules are JVM-testable.
 */
object StreamClientChain {
    data class Chain(val main: YouTubeClient, val fallbacks: List<YouTubeClient>)

    /**
     * Returns the (main, fallbacks) chain, or null when every client's family is disabled.
     *
     * Rules:
     * - Fallbacks keep table order, minus entries whose family is disabled.
     * - A disabled main is replaced by the first enabled fallback ("main promotion").
     * - Every fallback sharing the effective main's clientName is dropped from the chain — so a
     *   promoted VISIONOS main also drops the VISIONOS second-chance entry (same clientName),
     *   and the normal main never appears twice.
     */
    fun resolve(
        main: StreamClient,
        fallbacks: List<StreamClient>,
        disabledFamilies: Set<String>,
    ): Chain? {
        val enabledFallbacks = fallbacks.filter { it.family !in disabledFamilies }
        val effectiveMain = if (main.family in disabledFamilies) {
            enabledFallbacks.firstOrNull() ?: return null
        } else {
            main
        }
        return Chain(
            main = effectiveMain.client,
            fallbacks = enabledFallbacks
                .filter { it.client.clientName != effectiveMain.client.clientName }
                .map { it.client },
        )
    }
}
