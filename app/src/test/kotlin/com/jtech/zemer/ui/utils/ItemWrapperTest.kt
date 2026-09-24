package com.jtech.zemer.ui.utils

import org.junit.Assert.assertFalse
import org.junit.Test

class ItemWrapperTest {
    // Regression: a freshly built wrapper must NOT be selected. LibrarySongsScreen rebuilds its
    // wrappers with no remember, so a Room re-emit mid-multi-select creates fresh wrappers; a
    // selected-by-default wrapper made every row select at once (and a bulk remove then hit the
    // whole library). Every entry point clears-then-selects, so the honest default is unselected.
    @Test
    fun `new wrapper is not selected by default`() {
        assertFalse(ItemWrapper("song-id").isSelected)
    }
}
