package com.jtech.zemer.offline

import kotlinx.serialization.Serializable

/**
 * The on-device outage-fallback snapshot ("subset") lets the app search and browse the whole Zemer
 * corpus (everything except audio playback) with no InnerTube/YouTube when `search.zemer.io` is
 * unreachable, and serve those reads locally even while online when the local copy is in sync.
 *
 * The `GET /subset/manifest` document is the authoritative set of content-addressed shards making up
 * the current snapshot. Syncing is incremental and driven purely by this manifest: a shard whose
 * [SubsetShard.hash] differs from the local copy is re-downloaded **wholesale** (never merged), and a
 * shard absent from a newer manifest is **deleted** locally — so both content additions and removals
 * propagate without any server-side changelog. See [subsetSyncPlan].
 */
@Serializable
data class SubsetManifest(
    /** Manifest schema/version counter, bumped on every rebuild. Local == remote ⇒ in sync. */
    val v: Int,
    /** ISO-8601 build timestamp (informational; the shard hashes are what drive the diff). */
    val builtAt: String,
    val shards: List<SubsetShard>,
)

@Serializable
data class SubsetShard(
    /** Shard id, also its filename stem; always matches [SHARD_NAME_REGEX]. */
    val name: String,
    /** The content address: first 16 hex chars of sha256(raw gzipped shard bytes). See [subsetShardHash]. */
    val hash: String,
    /** Gzipped size in bytes (informational, e.g. for a download-size estimate). */
    val bytes: Long,
)

/** Shard names are used as filenames, so they are strictly validated to prevent path traversal. */
val SHARD_NAME_REGEX = Regex("^[a-z0-9-]+$")

fun isValidShardName(name: String): Boolean = SHARD_NAME_REGEX.matches(name)

/**
 * What a sync must do to bring the local shard set up to [remote]: which shards to (re)download and
 * which stale local shards to delete.
 */
data class SubsetSyncPlan(
    val toDownload: List<SubsetShard>,
    val toDelete: List<String>,
) {
    val isNoOp: Boolean get() = toDownload.isEmpty() && toDelete.isEmpty()
}

/**
 * Pure diff (unit-tested) of the [local] manifest (null when nothing is downloaded yet) against the
 * [remote] one. A shard is downloaded when it is new or its content hash changed; a local shard that
 * the remote manifest no longer lists is deleted. Shard identity is [SubsetShard.name]; [SubsetShard.hash]
 * is the content address that decides staleness.
 */
fun subsetSyncPlan(local: SubsetManifest?, remote: SubsetManifest): SubsetSyncPlan {
    val localByName = local?.shards?.associateBy { it.name }.orEmpty()
    val remoteNames = remote.shards.mapTo(HashSet()) { it.name }
    val toDownload = remote.shards.filter { localByName[it.name]?.hash != it.hash }
    val toDelete = localByName.keys.filter { it !in remoteNames }
    return SubsetSyncPlan(toDownload, toDelete)
}
