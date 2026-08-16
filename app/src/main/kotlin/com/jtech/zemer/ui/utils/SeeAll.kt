package com.jtech.zemer.ui.utils

/** Minimum items a shelf must hold before its "See all" arrow shows — a shorter row has nothing more to reveal. */
const val SEE_ALL_MIN_ITEMS = 4

/**
 * Whether a section/shelf holding [itemCount] items should show its "See all" arrow. The single source of
 * this threshold, shared by every list surface that offers a see-all so the rule can't drift — the artist
 * page (+ the podcast CHANNEL page that reuses it), genres, new releases, history, stats, statuses
 * and search. The Home tab is the one deliberate exception (every Home content row always shows a see-all
 * by design), so it does NOT call this. Pure + unit-tested.
 */
fun shouldShowSeeAll(itemCount: Int): Boolean = itemCount >= SEE_ALL_MIN_ITEMS

/**
 * The gated `onClick` for a shelf's "See all" arrow: returns [action] when the shelf is long enough
 * ([shouldShowSeeAll]), else null. Passed straight to [com.jtech.zemer.ui.component.NavigationTitle],
 * which hides the arrow and disables the row tap on a null onClick. Componentizes the gate so no call
 * site re-inlines the threshold check — reach for THIS, not a hand-written `if (shouldShowSeeAll(n))`.
 */
fun seeAllOnClick(itemCount: Int, action: () -> Unit): (() -> Unit)? =
    action.takeIf { shouldShowSeeAll(itemCount) }
