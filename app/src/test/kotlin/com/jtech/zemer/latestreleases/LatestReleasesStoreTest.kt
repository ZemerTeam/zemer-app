package com.jtech.zemer.latestreleases

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pins the resilience contract of [LatestReleasesStore] (the part that must never break the UI):
 * one fetch per launch retried then given up on, a 3-day staleness cap on the disk cache, 304 and
 * failure both keeping the last-good copy. Pure JVM — drives the network/clock/cache through the
 * store's test seams so no server or Android runtime is needed.
 */
class LatestReleasesStoreTest {
    private lateinit var dir: File

    private val sampleBody = """
        {"generatedAt":"2026-06-18T00:00:00Z","whitelistVersion":"9","windowDays":14,"count":2,
         "releases":[
           {"artistId":"UC1","artistName":"A","title":"T1","browseId":"MPRE1","playlistId":"OLAK1","thumbnail":"u1","year":2026,"uploadDate":"2026-06-17T00:00:00-07:00","sampleVideoId":"v1"},
           {"artistId":"UC2","artistName":"B","title":"T2","browseId":"MPRE2","playlistId":"OLAK2","thumbnail":"u2","uploadDate":"2026-06-16T00:00:00-07:00"}
         ]}
    """.trimIndent()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("latest-releases-test").toFile()
        LatestReleasesStore.cacheDirForTest = dir
        LatestReleasesStore.nowProvider = { NOW }
        LatestReleasesStore.retryDelayMs = 0L
        LatestReleasesStore.resetForTest()
        dir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        LatestReleasesStore.cacheDirForTest = null
        LatestReleasesStore.nowProvider = { System.currentTimeMillis() }
        LatestReleasesStore.retryDelayMs = 1500L
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Failure }
        LatestReleasesStore.resetForTest()
        dir.deleteRecursively()
    }

    @Test
    fun `refresh parses, returns newest-first, and caches to disk`() = runBlocking {
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Success(sampleBody, "etag-1") }

        val result = LatestReleasesStore.refresh()

        assertEquals(2, result.size)
        assertEquals("MPRE1", result[0].browseId)
        // A fresh process (cleared memory) still reads it back from disk.
        LatestReleasesStore.resetForTest()
        assertEquals(2, LatestReleasesStore.cachedReleases().size)
    }

    @Test
    fun `repeated failure gives up after MAX_ATTEMPTS and stops refetching until next launch`() = runBlocking {
        var calls = 0
        LatestReleasesStore.fetcher = { calls++; LatestReleasesStore.FetchOutcome.Failure }

        assertTrue(LatestReleasesStore.refresh().isEmpty())
        assertEquals(3, calls)

        // Already gave up: a second call this "launch" must not hit the network again.
        LatestReleasesStore.refresh()
        assertEquals(3, calls)
    }

    @Test
    fun `a failed refresh keeps the last-good cache instead of clearing it`() = runBlocking {
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Success(sampleBody, "etag-1") }
        LatestReleasesStore.refresh()

        LatestReleasesStore.resetForTest() // simulate next launch
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Failure }

        assertEquals(2, LatestReleasesStore.refresh().size)
    }

    @Test
    fun `cache older than 3 days is dropped`() = runBlocking {
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Success(sampleBody, "etag-1") }
        LatestReleasesStore.refresh() // meta stamped at NOW

        LatestReleasesStore.resetForTest()
        LatestReleasesStore.nowProvider = { NOW + 4L * 24 * 60 * 60 * 1000 } // +4 days

        assertTrue(LatestReleasesStore.cachedReleases().isEmpty())
    }

    @Test
    fun `not-modified keeps the cached releases`() = runBlocking {
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Success(sampleBody, "etag-1") }
        LatestReleasesStore.refresh()

        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.NotModified }
        assertEquals(2, LatestReleasesStore.refresh().size)
    }

    @Test
    fun `an unparseable body keeps the previous releases`() = runBlocking {
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Success(sampleBody, "etag-1") }
        LatestReleasesStore.refresh()

        LatestReleasesStore.resetForTest()
        LatestReleasesStore.fetcher = { LatestReleasesStore.FetchOutcome.Success("not json", "etag-2") }

        assertEquals(2, LatestReleasesStore.refresh().size)
    }

    private companion object {
        const val NOW = 1_750_000_000_000L
    }
}
