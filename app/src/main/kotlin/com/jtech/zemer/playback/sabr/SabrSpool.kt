package com.jtech.zemer.playback.sabr

import timber.log.Timber
import java.io.File
import java.util.Properties

/**
 * The SABR spool directory: where [SabrBuffer]s put their disk backing, and the persistent REPLAY
 * CACHE (DIRECT's playerCache parity for SABR) — a COMPLETE audio drain is promoted to a `.done`
 * file + a small `.meta` sidecar, and a later play of the same id is served straight from disk with
 * ZERO network (no /player, no poToken, no re-drain). LRU-pruned by total bytes; `.part` strays from
 * a killed process are cleaned at [init]. Video tracks spool here too (files, not heap) but are never
 * retained (large, and the video resolve cache already skips the /player round-trip).
 */
internal object SabrSpool {
    private const val TAG = "SabrSpool"
    // Replay-cache budget. Comfortably dozens of songs; a multi-hour episode fits but ages out fast.
    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

    @Volatile private var dir: File? = null

    /** Idempotent per target; call from every SABR entry point that owns a Context (service, downloads). */
    fun init(cacheDir: File) {
        val target = File(cacheDir, "sabr-spool")
        if (dir == target) return
        synchronized(this) {
            if (dir == target) return
            target.mkdirs()
            // A `.part` spool is a dead in-flight drain from a killed process — never reusable.
            target.listFiles { f -> f.name.endsWith(".part") }?.forEach { runCatching { it.delete() } }
            dir = target
        }
    }

    private fun requireDir(): File = dir ?: throw IllegalStateException("SabrSpool not initialized")

    // Every part file is UNIQUE PER STREAM INSTANCE: two streams for the same id+itag can be alive at
    // once (a quality switch's audio track while the old rung still plays, an error-refresh recreate,
    // the abandoned side of a swap race), and a shared name meant one stream's destroy unlinked the
    // file under the other's handle — playback survived (POSIX), but a completed drain's promotion
    // silently failed on the unlinked path, so the replay cache never populated after a replacement.
    private val partSeq = java.util.concurrent.atomic.AtomicLong(0)

    /** A fresh in-flight spool file for a PLAYBACK stream ([mediaId] is a videoId — filename-safe). */
    fun partFile(mediaId: String, itag: Int, suffix: String = ""): File =
        File(requireDir(), "$mediaId.$itag$suffix-${partSeq.incrementAndGet()}.part")

    /** A throwaway spool for a DOWNLOAD drain ([tag] disambiguates the video/audio tracks). */
    fun downloadPart(mediaId: String, tag: String): File =
        File(requireDir(), "$mediaId.$tag.dl-${partSeq.incrementAndGet()}.part")

    class Entry(
        val file: File,
        val itag: Int,
        val contentLength: Long,
        val mimeType: String,
        val bitrate: Int,
        val audioSampleRate: Int?,
        val streamClientLabel: String,
        val durationMs: Long,
    )

    /**
     * Promote a COMPLETE drain's spool to the replay cache: rename `.part` -> `.done`, write the meta
     * sidecar, prune LRU. The buffer must already be released (file handle closed) by the caller.
     */
    fun promote(mediaId: String, config: SabrConfig, part: File) {
        try {
            val done = File(requireDir(), "$mediaId.${config.format.itag}.done")
            val meta = File(requireDir(), "$mediaId.meta")
            if (!part.renameTo(done)) return
            // Drop any stale `.done` from an earlier itag for this id: the meta below now points at
            // THIS itag, so an older-itag sibling is unreachable dead weight — and prune() aging it out
            // would otherwise delete this id's LIVE meta and strand the newer file (finding).
            requireDir().listFiles { file -> file.name.startsWith("$mediaId.") && file.name.endsWith(".done") && file != done }
                ?.forEach { runCatching { it.delete() } }
            val p = Properties()
            p["itag"] = config.format.itag.toString()
            p["contentLength"] = config.format.contentLength.toString()
            p["mimeType"] = config.mimeType
            p["bitrate"] = config.bitrate.toString()
            config.audioSampleRate?.let { p["audioSampleRate"] = it.toString() }
            p["streamClientLabel"] = config.streamClientLabel
            p["durationMs"] = config.durationMs.toString()
            meta.outputStream().use { p.store(it, null) }
            prune()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "spool promote failed for %s", mediaId)
        }
    }

    /** The replay-cache entry for [mediaId], or null. Validates the file length against the meta. */
    fun lookup(mediaId: String): Entry? {
        val d = dir ?: return null
        return try {
            val meta = File(d, "$mediaId.meta")
            if (!meta.exists()) return null
            val p = Properties().apply { meta.inputStream().use { load(it) } }
            val itag = p.getProperty("itag")?.toIntOrNull() ?: return null
            val len = p.getProperty("contentLength")?.toLongOrNull() ?: return null
            val done = File(d, "$mediaId.$itag.done")
            if (!done.exists() || done.length() != len) return null
            // LRU touch: reuse keeps an entry alive.
            done.setLastModified(System.currentTimeMillis())
            Entry(
                file = done,
                itag = itag,
                contentLength = len,
                mimeType = p.getProperty("mimeType") ?: "",
                bitrate = p.getProperty("bitrate")?.toIntOrNull() ?: 0,
                audioSampleRate = p.getProperty("audioSampleRate")?.toIntOrNull(),
                streamClientLabel = p.getProperty("streamClientLabel") ?: "SABR",
                durationMs = p.getProperty("durationMs")?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Drop [mediaId]'s cached replay (a poisoned/stale entry — e.g. after a playback error on it). */
    fun evict(mediaId: String) {
        val d = dir ?: return
        d.listFiles { f -> f.name.startsWith("$mediaId.") && (f.name.endsWith(".done") || f.name.endsWith(".meta")) }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun prune() {
        val d = dir ?: return
        val done = d.listFiles { f -> f.name.endsWith(".done") }?.sortedBy { it.lastModified() } ?: return
        var total = done.sumOf { it.length() }
        for (f in done) {
            if (total <= MAX_CACHE_BYTES) break
            total -= f.length()
            val base = f.name.removeSuffix(".done") // "<id>.<itag>"
            val id = base.substringBeforeLast('.')
            val itag = base.substringAfterLast('.')
            runCatching { f.delete() }
            // Delete the meta ONLY when it still references THIS file's itag; if it points at a newer
            // itag's live .done, evicting this orphan must not orphan the live replay entry.
            val meta = File(d, "$id.meta")
            val metaItag = runCatching { Properties().apply { meta.inputStream().use { load(it) } }.getProperty("itag") }.getOrNull()
            if (metaItag == itag) runCatching { meta.delete() }
        }
    }
}
