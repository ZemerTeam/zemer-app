package com.jtech.zemer.ui.component

import com.jtech.zemer.constants.LibraryViewType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure scroll rules behind the shared whitelist-browse scaffold:
 * - [browseHeaderItemCount] — the fast scroller's item offset must count the optional
 *   header-sections item, or letter jumps land one row off on the Podcasts browse.
 * - [browseFastScrollProgress] — thumb-fraction clamping (header offset, short lists, over-scroll).
 * - [browseLetterBuckets] / [browseLazyItemIndex] — the LIST view's sticky letter sections and the
 *   letter-jump index mapping around them.
 * - [browseShowBackToTop] — the list/grid reveal thresholds.
 */
class BrowseScreenScaffoldTest {

    // --- browseHeaderItemCount ---

    @Test
    fun `header count is the two base items without sections`() {
        assertEquals(2, browseHeaderItemCount(hasHeaderSections = false))
    }

    @Test
    fun `header count adds the sections item`() {
        assertEquals(3, browseHeaderItemCount(hasHeaderSections = true))
    }

    // --- browseFastScrollProgress (takes a CONTENT index, see browseContentIndexOf) ---

    @Test
    fun `progress is zero at the top`() {
        assertEquals(0f, browseFastScrollProgress(0, 8, 2, 100), 0f)
    }

    @Test
    fun `progress reaches one at the bottom`() {
        // 100 items, 8 visible (2 of them headers only at the top) -> maxFirst = 100 - 6 = 94.
        assertEquals(1f, browseFastScrollProgress(94, 8, 2, 100), 0f)
    }

    @Test
    fun `progress scales linearly with the first visible content item`() {
        assertEquals(47f / 94f, browseFastScrollProgress(47, 8, 2, 100), 1e-6f)
    }

    @Test
    fun `progress is clamped when the list over-reports`() {
        assertEquals(1f, browseFastScrollProgress(500, 8, 2, 100), 0f)
    }

    @Test
    fun `short list never divides by zero`() {
        // Everything visible: maxFirst would be <= 0 and is floored to 1.
        assertEquals(0f, browseFastScrollProgress(0, 7, 2, 5), 0f)
        assertEquals(1f, browseFastScrollProgress(3, 7, 2, 5), 0f)
    }

    @Test
    fun `offset is the visible non-content count, not the fixed header count`() {
        // Scrolled to the bottom of a letter-bucketed list: the two fixed headers have scrolled off
        // and only ONE stuck letter header is on screen, so visibleNonContent = 1 (not the fixed 2).
        // maxFirst = 100 - (8 - 1) = 93, and the last reachable content first-index (100 - 7) is 93,
        // so the thumb reaches exactly 1.0 at the true bottom instead of a screenful early.
        assertEquals(1f, browseFastScrollProgress(93, 8, 1, 100), 0f)
        assertEquals(46f / 93f, browseFastScrollProgress(46, 8, 1, 100), 1e-6f)
    }

    // --- browseContentIndexOf ---

    @Test
    fun `content index inverts the lazy index mapping`() {
        val starts = listOf(0, 2, 3)
        for (item in 0..5) {
            assertEquals(item, browseContentIndexOf(browseLazyItemIndex(item, starts, 2), starts, 2))
        }
    }

    @Test
    fun `content index without buckets strips only the fixed headers`() {
        assertEquals(7, browseContentIndexOf(9, emptyList(), 2))
        assertEquals(0, browseContentIndexOf(0, emptyList(), 2))
        assertEquals(0, browseContentIndexOf(2, emptyList(), 2))
    }

    @Test
    fun `a sticky header at the top is the current position, not a preceding one`() {
        // starts = [0, 2, 3], 2 fixed headers. The letter header for bucket 1 sits at lazy index
        // 2 + 2 + 1 = 5; its content run begins at item 2. When that header is the first visible
        // item the resting content index is 2, not 1 - counting the header-at-lazyIndex as preceding
        // (the <= bug) under-reported it by one at every section boundary.
        val starts = listOf(0, 2, 3)
        assertEquals(2, browseContentIndexOf(5, starts, 2))
        // The bucket-0 header immediately after the fixed headers rests at content 0, not -1.
        assertEquals(0, browseContentIndexOf(2, starts, 2))
    }

    @Test
    fun `a mid-list lazy index stays proportional despite many headers`() {
        // 26 single-item buckets: content i sits at lazy 2 + 2i (+1 header each). Without the
        // header-aware mapping, lazy index 42 would read as content 40 of 26 and clamp the thumb
        // to the bottom half-way down - the reported bug.
        val starts = (0 until 26).toList()
        assertEquals(20, browseContentIndexOf(browseLazyItemIndex(20, starts, 2), starts, 2))
    }

    // --- browseLetterBuckets ---

    @Test
    fun `buckets start at each new letter run`() {
        val buckets = browseLetterBuckets(listOf("Abie", "Avraham", "Benny", "Chaim", "Chevra"))
        assertEquals(listOf('A' to 0, 'B' to 2, 'C' to 3), buckets)
    }

    @Test
    fun `a letter recurring in a later run gets its own bucket`() {
        // Bucketing strips leading punctuation while the sort does not: "(Chaim)" sorts under the
        // parenthesis (before A) but buckets under C, so C legitimately appears twice.
        val buckets = browseLetterBuckets(listOf("(Chaim)", "Avraham", "Chevra"))
        assertEquals(listOf('C' to 0, 'A' to 1, 'C' to 2), buckets)
    }

    @Test
    fun `digits bucket under the other bucket`() {
        val buckets = browseLetterBuckets(listOf("8th Day", "Abie"))
        assertEquals(listOf(ALPHABET_OTHER_BUCKET to 0, 'A' to 1), buckets)
    }

    @Test
    fun `empty names produce no buckets`() {
        assertTrue(browseLetterBuckets(emptyList()).isEmpty())
    }

    // --- browseLazyItemIndex ---

    @Test
    fun `no buckets means the plain header offset`() {
        assertEquals(1 + 7, browseLazyItemIndex(7, emptyList(), 1))
    }

    @Test
    fun `every bucket at or before the item shifts its lazy index`() {
        val starts = listOf(0, 2, 3)
        // Item 0: its own bucket header precedes it.
        assertEquals(1 + 0 + 1, browseLazyItemIndex(0, starts, 1))
        // Item 1: still only the first bucket's header.
        assertEquals(1 + 1 + 1, browseLazyItemIndex(1, starts, 1))
        // Item 2: two headers precede.
        assertEquals(1 + 2 + 2, browseLazyItemIndex(2, starts, 1))
        // Item 3: all three headers precede.
        assertEquals(1 + 3 + 3, browseLazyItemIndex(3, starts, 1))
    }

    @Test
    fun `header sections shift the lazy index too`() {
        assertEquals(2 + 4 + 1, browseLazyItemIndex(4, listOf(0), 2))
    }

    // --- browseShowBackToTop ---

    @Test
    fun `list shows back-to-top past two items`() {
        assertFalse(browseShowBackToTop(LibraryViewType.LIST, 2))
        assertTrue(browseShowBackToTop(LibraryViewType.LIST, 3))
    }

    @Test
    fun `grid shows back-to-top past five items`() {
        assertFalse(browseShowBackToTop(LibraryViewType.GRID, 5))
        assertTrue(browseShowBackToTop(LibraryViewType.GRID, 6))
    }
}
