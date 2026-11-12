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
    fun stage(dbFile: File, input: InputStream) {
        val part = File(dbFile.path + ".restore.part")
        part.outputStream().use { input.copyTo(it) }
        if (!part.renameTo(staged(dbFile))) error("Unable to stage the restored database")
    }

    /** Swaps a staged database in. The old database's journal and WAL files must not survive the swap. */
    fun apply(dbFile: File): Boolean {
        val staged = staged(dbFile)
        if (!staged.exists()) return false
        listOf("-journal", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        dbFile.delete()
        return staged.renameTo(dbFile)
    }
}
