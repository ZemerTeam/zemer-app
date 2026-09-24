package com.jtech.zemer.playback

import com.jtech.zemer.models.PersistPlayerState

/**
 * Pure helpers for the queue-persistence hot path (issue #515). The periodic save fires every 10s
 * while playing, and the dominant cost is Java-serializing the full queue metadata list. But on
 * restore the resume index/position come from the small player-state file (see MusicService's load
 * path: it `seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)`), NOT from the
 * queue file. So the heavy queue file only needs rewriting when its CONTENT changes; the tiny
 * player-state file carries position every tick. [signature] captures content identity so a
 * reorder/add/remove triggers a rewrite while plain playback progress does not.
 */
object QueuePersist {
    /**
     * Content identity of a queue: its ordered item ids plus its title. Excludes index/position on
     * purpose (those ride the player-state file), so steady playback of an unchanged queue keeps the
     * same signature and skips the expensive re-serialization. A reorder changes id order, an
     * add/remove changes the length, and switching queues changes the title. NUL as the separator
     * (as the `\u0000` ESCAPE — a raw NUL byte turns this source file binary to git): it cannot
     * appear in a videoId or leak in from a title edit, so joined parts can't collide.
     */
    fun signature(itemIds: List<String>, title: String?): String =
        buildString {
            append(title ?: "")
            append('\u0000')
            itemIds.joinTo(this, separator = "\u0000")
        }

    /**
     * Whether a snapshot file must be rewritten this save: teardown ([blocking]) always writes,
     * otherwise only when the content [signature] moved past the last WRITTEN one — the diff basis
     * is what's on disk, never a computed-but-skipped snapshot.
     */
    fun shouldWrite(blocking: Boolean, signature: String, lastWrittenSignature: String?): Boolean =
        blocking || signature != lastWrittenSignature

    /**
     * Whether the player state MOVED since the last written snapshot (`timestamp` excluded — it
     * changes on every capture by construction). While playing, position advances every tick, so
     * the state file still writes each periodic save; a PAUSED idle service goes fully
     * write-silent — no flash wear / IO wakeups on exactly the low-end devices #515 is about.
     */
    fun playerStateChanged(lastWritten: PersistPlayerState?, next: PersistPlayerState): Boolean =
        lastWritten == null || lastWritten.copy(timestamp = next.timestamp) != next
}
