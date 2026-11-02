package com.jtech.zemer.ui.menu

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportMatchTest {

    private fun song(id: String, artistId: String) =
        SongItem(id = id, title = id, artists = listOf(Artist(name = artistId, id = artistId)), thumbnail = "")

    private val whitelisted: suspend (List<YTItem>) -> List<YTItem> = { items ->
        items.filter { (it as SongItem).artists.first().id == "UC_ok" }
    }

    @Test
    fun `a whitelisted top hit is imported`() = runBlocking {
        assertEquals("v1", importMatch(listOf(song("v1", "UC_ok")), whitelisted)?.id)
    }

    @Test
    fun `a top hit the filter drops is not imported`() = runBlocking {
        assertNull(importMatch(listOf(song("v1", "UC_blocked")), whitelisted))
    }

    @Test
    fun `a lower whitelisted hit never stands in for a dropped top hit`() = runBlocking {
        assertNull(importMatch(listOf(song("v1", "UC_blocked"), song("v2", "UC_ok")), whitelisted))
    }

    @Test
    fun `no results imports nothing`() = runBlocking {
        assertNull(importMatch(emptyList(), whitelisted))
    }
}
