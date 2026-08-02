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

    // --- episodePassesPodcastWhitelist: podcast whitelist, never the artist whitelist ---

    @Test
    fun `episode passes when filters are off regardless of ids`() {
        assertTrue(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                artistIds = listOf(null, "UCnope"),
                filtersEnabled = false,
                isAllowedPodcastId = { false },
                isAllowedChannelId = { false },
            )
        )
    }

    @Test
    fun `episode passes when a show id (MPSP) is whitelisted`() {
        assertTrue(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                artistIds = listOf("MPSPshow", "UCother"),
                filtersEnabled = true,
                isAllowedPodcastId = { it == "MPSPshow" },
                isAllowedChannelId = { false },
            )
        )
    }

    @Test
    fun `episode passes when a host channel id (UC) is whitelisted`() {
        assertTrue(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                artistIds = listOf("UChost"),
                filtersEnabled = true,
                isAllowedPodcastId = { false },
                isAllowedChannelId = { it == "UChost" },
            )
        )
    }

    @Test
    fun `episode is dropped when no id is podcast-whitelisted and filters on`() {
        assertFalse(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                artistIds = listOf(null, "UCnope", "MPSPnope"),
                filtersEnabled = true,
                isAllowedPodcastId = { false },
                isAllowedChannelId = { false },
            )
        )
    }

    @Test
    fun `episode with no artist ids is dropped when filters on`() {
        assertFalse(
            PodcastSyncLogic.episodePassesPodcastWhitelist(
                artistIds = emptyList(),
                filtersEnabled = true,
                isAllowedPodcastId = { true },
                isAllowedChannelId = { true },
            )
        )
    }
}
