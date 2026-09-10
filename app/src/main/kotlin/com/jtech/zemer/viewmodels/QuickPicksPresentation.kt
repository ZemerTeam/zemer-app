package com.jtech.zemer.viewmodels

/**
 * How the Quick Picks row is PRESENTED across loads (pure, unit-tested). The row's content is a per-load
 * rotation (recent-artist avoidance + one-per-artist); what must not happen is the row visibly churning on
 * a pull-to-refresh: a user with a handful of songs saw the set flip 3 -> 4 -> 3 and reorder on every pull
 * ("songs jump in"). Three rules keep it calm without touching the rotation on real libraries:
 *  - a refresh keeps the rows already on screen until the FINAL list is ready ([showLocalRowsFirst]);
 *  - items that stay keep their position, newcomers append ([keepDisplayedOrder]);
 *  - a pool too small to rotate is shown whole and stable ([rotates]).
 */
object QuickPicksPresentation {
    /** Below this many allowed songs the recent-artist rotation only flips the row between two subsets. */
    const val MIN_POOL_FOR_ROTATION = 8

    fun rotates(poolSize: Int): Boolean = poolSize >= MIN_POOL_FOR_ROTATION

    /**
     * The local rows are published immediately (before the network wait) only when nothing is on screen
     * yet - a cold start's instant paint. A refresh over displayed rows waits for the final list, else
     * the row reshuffles twice per pull.
     */
    fun showLocalRowsFirst(force: Boolean, hasDisplayedRows: Boolean): Boolean = !force || !hasDisplayedRows

    /**
     * [next] re-ordered so items already displayed keep their relative position and newcomers follow in
     * [next]'s (shuffled) order. Empty [previousIds] (first paint) leaves [next] untouched.
     */
    fun <T> keepDisplayedOrder(previousIds: List<String>, next: List<T>, idOf: (T) -> String): List<T> {
        if (previousIds.isEmpty()) return next
        val rank = HashMap<String, Int>()
        previousIds.forEachIndexed { i, id -> rank.putIfAbsent(id, i) }
        val (kept, fresh) = next.partition { idOf(it) in rank }
        return kept.sortedBy { rank.getValue(idOf(it)) } + fresh
    }
}
