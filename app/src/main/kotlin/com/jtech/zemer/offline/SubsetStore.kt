package com.jtech.zemer.offline

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk store for the subset snapshot under `filesDir/subset/`: the gzipped shard files
 * (`<name>.gz`) plus the last successfully-committed `manifest.json`. The committed manifest is the
 * record of what is actually present, so it is written only AFTER every shard it lists is on disk and
 * verified — an interrupted sync leaves the previous manifest (and its shards) intact, never a
 * half-updated snapshot.
 */
class SubsetStore(private val root: File) {

    constructor(context: Context) : this(File(context.filesDir, DIR_NAME))

    private val json = Json { ignoreUnknownKeys = true }

    private val manifestFile: File get() = File(root, MANIFEST_FILE)

    fun shardFile(name: String): File {
        require(isValidShardName(name)) { "invalid shard name: $name" }
        return File(root, "$name$SHARD_EXT")
    }

    /** The last committed local manifest, or null if nothing is stored / it is unreadable. */
    fun localManifest(): SubsetManifest? = runCatching {
        manifestFile.takeIf { it.isFile }?.readText()?.let { json.decodeFromString<SubsetManifest>(it) }
    }.getOrNull()

    fun shardBytes(name: String): ByteArray? = shardFile(name).takeIf { it.isFile }?.readBytes()

    /** Writes one shard atomically (temp + rename) so a partial write is never observed as complete. */
    fun writeShard(name: String, gzippedBytes: ByteArray) {
        root.mkdirs()
        val target = shardFile(name)
        val tmp = File(root, "$name$SHARD_EXT$TMP_EXT")
        tmp.writeBytes(gzippedBytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "failed to commit shard $name" }
        }
    }

    fun deleteShard(name: String) {
        shardFile(name).delete()
    }

    /**
     * Commits [manifest] as the new local record, atomically. Callers MUST have written and verified
     * every shard it lists first — this write is the point at which the new snapshot becomes live.
     */
    fun commitManifest(manifest: SubsetManifest) {
        root.mkdirs()
        val tmp = File(root, "$MANIFEST_FILE$TMP_EXT")
        tmp.writeText(json.encodeToString(manifest))
        if (!tmp.renameTo(manifestFile)) {
            manifestFile.delete()
            check(tmp.renameTo(manifestFile)) { "failed to commit manifest" }
        }
    }

    /** Deletes any `*.gz` shard file whose name is not in [keep] (belt-and-suspenders over the diff). */
    fun pruneOrphans(keep: Set<String>) {
        root.listFiles { f -> f.isFile && f.name.endsWith(SHARD_EXT) }?.forEach { f ->
            if (f.name.removeSuffix(SHARD_EXT) !in keep) f.delete()
        }
    }

    /** Total gzipped bytes currently on disk (for a "downloaded size" readout). */
    fun sizeOnDisk(): Long =
        root.listFiles { f -> f.isFile && f.name.endsWith(SHARD_EXT) }?.sumOf { it.length() } ?: 0L

    /** Wipes the whole store (used when the user turns offline search off). */
    fun clear() {
        root.deleteRecursively()
    }

    companion object {
        const val DIR_NAME = "subset"
        private const val MANIFEST_FILE = "manifest.json"
        private const val SHARD_EXT = ".gz"
        private const val TMP_EXT = ".tmp"
    }
}
