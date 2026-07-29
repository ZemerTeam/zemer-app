package com.jtech.zemer.offline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The staged-commit contract: a sync stages every downloaded shard without touching the live copy and
 * promotes only after ALL downloads verified, so a failure partway can never leave a mixed-version
 * corpus on disk — plus the read-time hash check that guards the residual window.
 */
class SubsetStoreStagingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = SubsetStore(tmp.root)

    @Test
    fun `staging leaves the live shard untouched until promotion`() {
        val s = store()
        s.writeShard("tracks-0", byteArrayOf(1))
        s.stageShard("tracks-0", byteArrayOf(2))

        assertArrayEquals(byteArrayOf(1), s.shardBytes("tracks-0"))

        s.promoteStagedShard("tracks-0")
        assertArrayEquals(byteArrayOf(2), s.shardBytes("tracks-0"))
    }

    @Test
    fun `clearStaged removes leftovers and keeps live shards`() {
        val s = store()
        s.writeShard("tracks-0", byteArrayOf(1))
        s.stageShard("tracks-0", byteArrayOf(2))
        s.stageShard("albums-0", byteArrayOf(3))

        s.clearStaged()

        assertArrayEquals(byteArrayOf(1), s.shardBytes("tracks-0"))
        assertNull("staged-only shard must not become live", s.shardBytes("albums-0"))
    }

    @Test
    fun `staged files are invisible to sizeOnDisk and pruneOrphans`() {
        val s = store()
        s.writeShard("tracks-0", byteArrayOf(1, 2, 3))
        s.stageShard("albums-0", ByteArray(100))

        assertEquals(3L, s.sizeOnDisk())
        s.pruneOrphans(keep = setOf("tracks-0"))
        s.promoteStagedShard("albums-0")
        assertArrayEquals("prune must not delete a pending staged file", ByteArray(100), s.shardBytes("albums-0"))
    }

    @Test
    fun `loadCorpus refuses a shard whose bytes do not match the committed manifest`() {
        val s = store()
        val bytes = byteArrayOf(9, 9, 9)
        s.writeShard("artists", bytes)
        s.commitManifest(
            SubsetManifest(
                v = 1,
                builtAt = "2026-07-29T00:00:00Z",
                shards = listOf(SubsetShard("artists", hash = "0000000000000000", bytes = bytes.size.toLong())),
            ),
        )

        assertNull("mixed/corrupt shard must read as no-snapshot", SubsetDecoder.loadCorpus(s))
    }
}
