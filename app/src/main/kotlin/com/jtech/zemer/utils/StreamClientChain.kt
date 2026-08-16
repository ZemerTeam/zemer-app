package com.jtech.zemer.utils

import com.metrolist.innertube.models.YouTubeClient

/**
 * Pure resolution of the effective stream-client chain from the configured order and the user's
 * disabled client names (Settings → Stream sources). Extracted from [YTPlayerUtils] so the
 * main-promotion / disable-filtering rules are JVM-testable.
 */
object StreamClientChain {
    data class Chain(val main: YouTubeClient, val fallbacks: List<YouTubeClient>)

    /**
     * Returns the (main, fallbacks) chain, or null when every client is disabled.
     *
     * Rules (behavior-preserving from the pre-extraction inline logic):
     * - Fallbacks keep [fallbacks] order, minus disabled names.
     * - A disabled main is replaced by the first enabled fallback ("main promotion").
     * - Every fallback sharing the effective main's clientName is dropped from the chain — so a
     *   promoted VISIONOS main also drops the VISIONOS second-chance entry (same clientName),
     *   and the normal main never appears twice.
     */
    fun resolve(
        main: YouTubeClient,
        fallbacks: List<YouTubeClient>,
        disabledNames: Set<String>,
    ): Chain? {
        val enabledFallbacks = fallbacks.filter { it.clientName !in disabledNames }
        val effectiveMain = if (main.clientName in disabledNames) {
            enabledFallbacks.firstOrNull() ?: return null
        } else {
            main
        }
        return Chain(
            main = effectiveMain,
            fallbacks = enabledFallbacks.filter { it.clientName != effectiveMain.clientName },
        )
    }
}
