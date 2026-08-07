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
}
