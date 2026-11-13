package com.jtech.zemer.db

import java.io.File
import java.io.InputStream

/**
 * A backup restore never touches the open database: closing it under the app's running queries crashed
 * ("connection is closed"). The restored file is staged beside it and swapped in at the next process
 * start, before Room opens the database.
 */
internal object StagedRestore {
    private fun staged(dbFile: File) = File(dbFile.path + ".restore")

    /** Copies the backup's database next to [dbFile]; it only becomes the staged file once complete. */
    @Synchronized
    fun stage(dbFile: File, input: InputStream) {
        val part = part(dbFile)
        part.outputStream().use { input.copyTo(it) }
        if (!part.renameTo(staged(dbFile))) error("Unable to stage the restored database")
    }

    /** Drops a staged copy: the restore it belongs to failed. */
    @Synchronized
    fun discard(dbFile: File) {
        part(dbFile).delete()
        staged(dbFile).delete()
    }

    /**
     * Swaps a staged database in. The old database's journal and WAL files must not survive the swap. The
     * rename replaces the old database in one step, so a failed swap leaves it in place.
     */
    fun apply(dbFile: File): Boolean {
        val staged = staged(dbFile)
        if (!staged.exists()) return false
        listOf("-journal", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        return staged.renameTo(dbFile)
    }

    private fun part(dbFile: File) = File(dbFile.path + ".restore.part")
}
