package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pure core of the offline-subset sync: the content-addressed diff that decides what to
 * download vs delete, the shard hash the verification depends on, and the shard-name validation that
 * keeps a server-supplied name from escaping the store directory.
 */
class SubsetSyncTest {

    private fun shard(name: String, hash: String) = SubsetShard(name, hash, bytes = 1)
    private fun manifest(v: Int, vararg shards: SubsetShard) = SubsetManifest(v, "2026-07-27T00:00:00Z", shards.toList())

    @Test
    fun `no local manifest downloads every shard and deletes nothing`() {
        val remote = manifest(1, shard("tracks-0", "aaa"), shard("artists", "bbb"))
        val plan = subsetSyncPlan(local = null, remote = remote)
        assertEquals(listOf("tracks-0", "artists"), plan.toDownload.map { it.name })
        assertTrue(plan.toDelete.isEmpty())
        assertFalse(plan.isNoOp)
    }

    @Test
    fun `identical manifests are a no-op`() {
        val m = manifest(2, shard("tracks-0", "aaa"), shard("artists", "bbb"))
        val plan = subsetSyncPlan(local = m, remote = m)
        assertTrue(plan.toDownload.isEmpty())
        assertTrue(plan.toDelete.isEmpty())
        assertTrue(plan.isNoOp)
    }

    @Test
    fun `only the changed shard is re-downloaded`() {
        val local = manifest(1, shard("tracks-0", "aaa"), shard("artists", "bbb"))
        val remote = manifest(2, shard("tracks-0", "aaa"), shard("artists", "ZZZ"))
        val plan = subsetSyncPlan(local, remote)
        assertEquals(listOf("artists"), plan.toDownload.map { it.name })
        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `a shard dropped from the remote manifest is deleted, a new one is downloaded`() {
        val local = manifest(1, shard("tracks-0", "aaa"), shard("legacy", "bbb"))
        val remote = manifest(2, shard("tracks-0", "aaa"), shard("community", "ccc"))
        val plan = subsetSyncPlan(local, remote)
        assertEquals(listOf("community"), plan.toDownload.map { it.name })
        assertEquals(listOf("legacy"), plan.toDelete)
    }

    @Test
    fun `shard hash is the first 16 hex chars of sha256 over the raw bytes`() {
        // Known SHA-256 vectors: sha256("") and sha256("abc").
        assertEquals("e3b0c44298fc1c14", subsetShardHash(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea", subsetShardHash("abc".toByteArray()))
    }

    @Test
    fun `shard names are validated to prevent path traversal`() {
        assertTrue(isValidShardName("tracks-0"))
        assertTrue(isValidShardName("albumtracks-15"))
        assertTrue(isValidShardName("community"))
        assertFalse(isValidShardName("../secrets"))
        assertFalse(isValidShardName("Tracks"))      // uppercase
        assertFalse(isValidShardName("a b"))          // space
        assertFalse(isValidShardName("a_b"))          // underscore
        assertFalse(isValidShardName(""))
    }
}
