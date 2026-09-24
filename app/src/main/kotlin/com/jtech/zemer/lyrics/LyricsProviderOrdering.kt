package com.jtech.zemer.lyrics

/**
 * The priority dialog's order math, pure: the Content settings show one row per TOGGLE (YouTube's two registry
 * entries collapse into one), only for ENABLED toggles, and a drag rewrites the preference so the disabled
 * providers keep their relative places after the enabled ones (re-enabling one lands where it was).
 */
object LyricsProviderOrdering {
    /** Enabled id groups (one per toggle) in the user's current order; a group sits where its first-listed id sits. */
    fun enabledGroups(order: String, groups: List<List<String>>, enabledIds: Set<String>): List<List<String>> {
        val byId = groups.flatMap { g -> g.map { it to g } }.toMap()
        return LyricsProviderRegistry.deserializeProviderOrder(order).filter { it in enabledIds }.mapNotNull { byId[it] }.distinct()
    }

    /** The serialised preference after a drag: the enabled groups in their new order, then the disabled ids in their old order. */
    fun reordered(order: String, enabledGroupsInOrder: List<List<String>>, enabledIds: Set<String>): String {
        val disabled = LyricsProviderRegistry.deserializeProviderOrder(order).filter { it !in enabledIds }
        return LyricsProviderRegistry.serializeProviderOrder(enabledGroupsInOrder.flatten() + disabled)
    }
}
