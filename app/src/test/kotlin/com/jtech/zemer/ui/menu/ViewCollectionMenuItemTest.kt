package com.jtech.zemer.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shared "view collection" row's routing decision, used by all three song menus (player / local /
 * online). An episode's collection is its podcast SHOW, a song's is its ALBUM — the mislabel/mis-route
 * this guards is an episode opening the album screen with a show id.
 */
class ViewCollectionMenuItemTest {

    @Test
    fun `an episode opens its podcast show`() {
        assertEquals("online_podcast/MPSPshow", viewCollectionRoute(isEpisode = true, collectionId = "MPSPshow"))
    }

    @Test
    fun `a song opens its album`() {
        assertEquals("album/MPREb_abc", viewCollectionRoute(isEpisode = false, collectionId = "MPREb_abc"))
    }

    @Test
    fun `a blank or null id yields no route (no dead navigate)`() {
        assertNull(viewCollectionRoute(isEpisode = true, collectionId = null))
        assertNull(viewCollectionRoute(isEpisode = false, collectionId = null))
        assertNull(viewCollectionRoute(isEpisode = true, collectionId = ""))
        assertNull(viewCollectionRoute(isEpisode = false, collectionId = "   "))
    }
}
