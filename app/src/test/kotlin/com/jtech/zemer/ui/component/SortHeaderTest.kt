package com.jtech.zemer.ui.component

import com.jtech.zemer.constants.SongSortType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the extracted songSortTypeLabel when-mapping against copy-paste collisions/gaps. */
class SortHeaderTest {
    @Test
    fun `songSortTypeLabel maps every SongSortType to a distinct non-zero string res`() {
        val labels = SongSortType.entries.map { songSortTypeLabel(it) }
        labels.forEach { assertTrue("every sort type must resolve to a real string res", it != 0) }
        assertEquals(
            "each SongSortType must map to a distinct label (no accidental reuse)",
            SongSortType.entries.size,
            labels.toSet().size,
        )
    }
}
