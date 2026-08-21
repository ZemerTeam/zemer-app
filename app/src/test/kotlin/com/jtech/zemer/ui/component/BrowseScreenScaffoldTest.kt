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

    // --- browseFastScrollProgress ---

    @Test
    fun `progress is zero at the top even inside the header items`() {
        assertEquals(0f, browseFastScrollProgress(0, 8, 2, 100), 0f)
        assertEquals(0f, browseFastScrollProgress(1, 8, 2, 100), 0f)
        assertEquals(0f, browseFastScrollProgress(2, 8, 2, 100), 0f)
    }

    @Test
    fun `progress reaches one at the bottom`() {
        // 100 items, 8 visible (2 of them headers only at the top) -> maxFirst = 100 - 6 = 94.
        assertEquals(1f, browseFastScrollProgress(96, 8, 2, 100), 0f)
    }

    @Test
    fun `progress scales linearly with the first visible content item`() {
        // contentFirst = 49, maxFirst = 94.
        assertEquals(49f / 94f, browseFastScrollProgress(51, 8, 2, 100), 1e-6f)
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
    fun `header sections shift the content offset`() {
        // With 3 header items, index 3 is still the first content row -> progress 0.
        assertEquals(0f, browseFastScrollProgress(3, 9, 3, 100), 0f)
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
