package com.jtech.zemer.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastSyncLogicTest {

    // --- upsertAction: server-first, respect local un-bookmark ---

    @Test
    fun `absent locally inserts`() {
        assertEquals(
            PodcastSyncLogic.UpsertAction.INSERT,
            PodcastSyncLogic.upsertAction(existsLocally = false, bookmarkedLocally = false),
        )
    }

    @Test
    fun `present and bookmarked updates metadata`() {
        assertEquals(
            PodcastSyncLogic.UpsertAction.UPDATE_METADATA,
            PodcastSyncLogic.upsertAction(existsLocally = true, bookmarkedLocally = true),
        )
    }

    @Test
    fun `present but unbookmarked is skipped so local removal is respected`() {
        assertEquals(
            PodcastSyncLogic.UpsertAction.SKIP_UNBOOKMARKED,
            PodcastSyncLogic.upsertAction(existsLocally = true, bookmarkedLocally = false),
        )
    }

    // --- localOnly: cleanup set = locals absent from the (whitelisted) remote ---

    @Test
    fun `localOnly returns locals not present remotely`() {
        val local = listOf("a", "b", "c")
        val result = PodcastSyncLogic.localOnly(local, remoteIds = setOf("b"), id = { it })
        assertEquals(listOf("a", "c"), result)
    }

    @Test
    fun `localOnly is empty when remote covers every local`() {
        val local = listOf("a", "b")
        assertTrue(PodcastSyncLogic.localOnly(local, setOf("a", "b", "z"), id = { it }).isEmpty())
    }

    // --- episodePassesPodcastWhitelist: channel-keyed podcast whitelist, never the artist whitelist.
    // The caller resolves show ids (MPSP) to their host channel BEFORE calling, so this receives the
    // item's effective host-channel ids. ---

    @Test
    fun `episode passes when filters are off regardless of ids`() {
        assertTrue(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                channelIds = listOf(null, "UCnope"),
                filtersEnabled = false,
                isWhitelistedChannel = { false },
            )
        )
    }

    @Test
    fun `episode passes when a show resolved to its whitelisted host channel`() {
        // The caller resolved MPSPshow -> UChost and passes the host channel in.
        assertTrue(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                channelIds = listOf("UChost", "UCother"),
                filtersEnabled = true,
                isWhitelistedChannel = { it == "UChost" },
            )
        )
    }

    @Test
    fun `episode passes when a host channel id (UC) is whitelisted`() {
        assertTrue(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                channelIds = listOf("UChost"),
                filtersEnabled = true,
                isWhitelistedChannel = { it == "UChost" },
            )
        )
    }

    @Test
    fun `episode is dropped when no channel is whitelisted and filters on`() {
        // An unresolved show id (MPSPnope, no local row) never matches the channel-keyed whitelist.
        assertFalse(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                channelIds = listOf(null, "UCnope", "MPSPnope"),
                filtersEnabled = true,
                isWhitelistedChannel = { false },
            )
        )
    }

    @Test
    fun `episode with no channel ids is dropped when filters on`() {
        assertFalse(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                channelIds = emptyList(),
                filtersEnabled = true,
                isWhitelistedChannel = { true },
            )
        )
    }

    // --- episodeSyncPlan: whitelist gates import, never deletion ---

    private data class Ep(val id: String, val showId: String?)

    @Test
    fun `unresolvable or unconfirmed show gates import but never enters the removal set`() {
        val remote = listOf(
            Ep("e1", "MPSP_ok"),      // whitelisted show -> imported
            Ep("e2", "MPSP_404"),     // show 404s under current flags -> not imported, still saved remotely
            Ep("e3", null),           // no MPSP id resolvable -> not imported, still saved remotely
        )
        val plan = PodcastSyncLogic.episodeSyncPlan(
            rawRemote = remote,
            id = { it.id },
            showIdOf = { it.showId },
            showAllowed = mapOf("MPSP_ok" to true, "MPSP_404" to false),
        )
        assertEquals(listOf("e1"), plan.imported.map { it.id })
        assertEquals(setOf("e1", "e2", "e3"), plan.cleanupReference)
        // cleanup against the RAW reference removes nothing that is still in VLSE
        assertTrue(PodcastSyncLogic.localOnly(listOf("e2", "e3"), plan.cleanupReference) { it }.isEmpty())
        // a genuinely-removed remote episode is still cleaned up
        assertEquals(listOf("gone"), PodcastSyncLogic.localOnly(listOf("gone"), plan.cleanupReference) { it })
    }

    // --- unsaveAction: a failed lookup must fail loud, not fake success ---

    @Test
    fun `unsave removes when a setVideoId was found`() {
        assertEquals(PodcastSyncLogic.UnsaveAction.REMOVE, PodcastSyncLogic.unsaveAction(lookupSucceeded = true, setVideoId = "svid"))
    }

    @Test
    fun `unsave is a clean no-op when the lookup succeeded but found no entry`() {
        assertEquals(PodcastSyncLogic.UnsaveAction.NOTHING_TO_REMOVE, PodcastSyncLogic.unsaveAction(lookupSucceeded = true, setVideoId = null))
    }

    @Test
    fun `unsave fails when the lookup itself failed`() {
        assertEquals(PodcastSyncLogic.UnsaveAction.FAIL, PodcastSyncLogic.unsaveAction(lookupSucceeded = false, setVideoId = null))
    }
}
