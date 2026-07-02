package com.jtech.zemer.search

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.pages.AlbumPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Locks the source-selection contract of [resolveAlbumPage], which the album long-press menu uses:
 * a Zemer result loads server-first and only falls back to InnerTube on failure; a YouTube result
 * must never touch the Zemer server.
 */
class AlbumPageResolverTest {

    private fun page(id: String) = AlbumPage(
        album = AlbumItem(browseId = id, playlistId = "OL$id", title = "T", artists = null, thumbnail = ""),
        songs = emptyList(),
    )

    @Test
    fun `youtube result uses innertube only and never invokes the zemer source`() = runBlocking {
        var zemerCalled = false
        val expected = page("yt")

        val result = resolveAlbumPage(
            zemer = false,
            fromZemer = { zemerCalled = true; page("zemer") },
            fromInnerTube = { Result.success(expected) },
        )

        assertSame(expected, result.getOrNull())
        assertFalse(zemerCalled)
    }

    @Test
    fun `zemer result uses the server and never invokes innertube on success`() = runBlocking {
        var innerTubeCalled = false
        val expected = page("zemer")

        val result = resolveAlbumPage(
            zemer = true,
            fromZemer = { expected },
            fromInnerTube = { innerTubeCalled = true; Result.success(page("yt")) },
        )

        assertSame(expected, result.getOrNull())
        assertFalse(innerTubeCalled)
    }

    @Test
    fun `zemer failure falls back to innertube`() = runBlocking {
        val expected = page("yt")

        val result = resolveAlbumPage(
            zemer = true,
            fromZemer = { throw IOException("Zemer album returned HTTP 503") },
            fromInnerTube = { Result.success(expected) },
        )

        assertSame(expected, result.getOrNull())
    }

    @Test
    fun `both sources failing propagates the innertube failure`() = runBlocking {
        val innerTubeError = IOException("innertube down")

        val result = resolveAlbumPage(
            zemer = true,
            fromZemer = { throw IOException("zemer down") },
            fromInnerTube = { Result.failure(innerTubeError) },
        )

        assertTrue(result.isFailure)
        assertEquals("innertube down", result.exceptionOrNull()?.message)
    }
}
