package com.jtech.zemer.ui.menu

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlaylistDialogTest {

    /**
     * The library playlists queries filter `WHERE bookmarkedAt IS NOT NULL`, so an imported copy
     * with a null bookmark saves a fully-populated playlist that never appears anywhere (the
     * "Save a copy saved nothing" bug). The import must construct the entity exactly like the
     * create-playlist flow: bookmarked now, editable.
     */
    @Test
    fun importedPlaylistIsBookmarkedAndEditableSoTheLibraryShowsIt() {
        val entity = importedPlaylistEntity("My Copy")
        assertNotNull(entity.bookmarkedAt)
        assertTrue(entity.isEditable)
    }
}
