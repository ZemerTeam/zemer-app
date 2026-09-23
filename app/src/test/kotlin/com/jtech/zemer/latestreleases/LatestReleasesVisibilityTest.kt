package com.jtech.zemer.latestreleases

import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.WhitelistCache
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Issue #535: the Latest Releases row filtered its feed once, so a feed that landed before the artist
 * whitelist stayed empty for the whole session. [visibleLatestReleases] must re-filter the SAME
 * collection when the whitelist or the content filters change, keep unverified artists hidden while
 * waiting, and not re-run for an identical whitelist reload (the filter itself may refill the cache).
 * Runs against the real [WhitelistCache]; the filter stands in for filterWhitelisted's artist rule
 * (absent from the whitelist = rejected, female gate via [WhitelistCache.isAllowed]).
 */
class LatestReleasesVisibilityTest {
    private fun release(id: String, artistId: String) = LatestRelease(
        artistId = artistId,
        artistName = artistId,
        title = id,
        browseId = id,
        playlistId = "OLAK_$id",
        thumbnail = "thumb",
        uploadDate = "2026-09-01T00:00:00-07:00",
    )

    private val feedOf3 = listOf(release("a", "UC1"), release("b", "UC2"), release("c", "UC3"))
    private val whitelist = listOf(
        ArtistWhitelistEntity(artistId = "UC1", artistName = "One"),
        ArtistWhitelistEntity(artistId = "UC2", artistName = "Two"),
        ArtistWhitelistEntity(artistId = "UC3", artistName = "Three", isFemale = true),
    )

    private val filterCalls = AtomicInteger()
    private val filter: suspend (List<LatestRelease>, ContentFilterConfig) -> List<LatestRelease> = { releases, config ->
        filterCalls.incrementAndGet()
        releases.filter { r -> WhitelistCache.get(r.artistId)?.let { WhitelistCache.isAllowed(it, config) } == true }
    }

    @Before @After
    fun reset() = WhitelistCache.updateAll(emptyList())

    private fun <T> MutableStateFlow<T>.awaitValue(expected: T): T =
        runBlocking { withTimeout(5_000) { first { it == expected } } }

    private fun ids(list: List<LatestRelease>?) = list?.map { it.browseId }

    @Test
    fun `a feed that lands before the whitelist is re-filtered in place when the whitelist arrives`() = runBlocking {
        val feed = MutableStateFlow<List<LatestRelease>?>(null)
        val filters = MutableStateFlow(ContentFilterConfig(allowFemaleSingers = true))
        val shown = MutableStateFlow<List<String>?>(null)
        val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            visibleLatestReleases(feed, WhitelistCache.entries, filters, filter).collect { shown.value = ids(it) }
        }

        feed.value = feedOf3
        assertEquals(emptyList<String>(), shown.awaitValue(emptyList())) // unverified artists stay hidden

        WhitelistCache.updateAll(whitelist) // the whitelist lands later; same collector, no new VM
        assertEquals(listOf("a", "b", "c"), shown.awaitValue(listOf("a", "b", "c")))
        job.cancel()
    }

    @Test
    fun `a content-filter change re-filters the visible releases`() = runBlocking {
        WhitelistCache.updateAll(whitelist)
        val feed = MutableStateFlow<List<LatestRelease>?>(feedOf3)
        val filters = MutableStateFlow(ContentFilterConfig(allowFemaleSingers = true))
        val shown = MutableStateFlow<List<String>?>(null)
        val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            visibleLatestReleases(feed, WhitelistCache.entries, filters, filter).collect { shown.value = ids(it) }
        }

        shown.awaitValue(listOf("a", "b", "c"))
        filters.value = ContentFilterConfig(allowFemaleSingers = false)
        assertEquals(listOf("a", "b"), shown.awaitValue(listOf("a", "b")))
        job.cancel()
    }

    @Test
    fun `nothing is emitted before the feed loads, and an identical whitelist reload does not re-filter`() = runBlocking {
        val feed = MutableStateFlow<List<LatestRelease>?>(null)
        val filters = MutableStateFlow(ContentFilterConfig())
        val shown = MutableStateFlow<List<String>?>(null)
        val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            visibleLatestReleases(feed, WhitelistCache.entries, filters, filter).collect { shown.value = ids(it) }
        }

        WhitelistCache.updateAll(whitelist)
        delay(200)
        assertNull(shown.value)
        assertEquals(0, filterCalls.get())

        feed.value = feedOf3
        shown.awaitValue(listOf("a", "b"))
        val callsAfterLoad = filterCalls.get()

        WhitelistCache.updateAll(whitelist) // same entries, as filterWhitelisted's DB refill produces
        delay(200)
        assertEquals(callsAfterLoad, filterCalls.get())
        job.cancel()
    }
}
