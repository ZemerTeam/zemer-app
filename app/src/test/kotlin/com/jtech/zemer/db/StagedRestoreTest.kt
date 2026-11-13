package com.jtech.zemer.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StagedRestoreTest {
    @get:Rule
    val dir = TemporaryFolder()

    @Test
    fun `a staged database replaces the old one and its journal files at the next start`() {
        val db = dir.newFile("song.db").apply { writeText("old") }
        val sidecars = listOf("-journal", "-wal", "-shm").map { File(db.path + it).apply { writeText("x") } }

        StagedRestore.stage(db, "restored".byteInputStream())
        assertEquals("the open database is untouched until the restart", "old", db.readText())

        assertTrue(StagedRestore.apply(db))
        assertEquals("restored", db.readText())
        sidecars.forEach { assertFalse(it.exists()) }
        assertFalse("applied once", StagedRestore.apply(db))
    }

    @Test
    fun `nothing staged or a half-written copy changes nothing`() {
        val db = dir.newFile("song.db").apply { writeText("old") }
        assertFalse(StagedRestore.apply(db))
        File(db.path + ".restore.part").writeText("half")
        assertFalse(StagedRestore.apply(db))
        assertEquals("old", db.readText())
    }

    @Test
    fun `a discarded staged copy is never swapped in`() {
        val db = dir.newFile("song.db").apply { writeText("old") }
        StagedRestore.stage(db, "restored".byteInputStream())
        StagedRestore.discard(db)
        assertFalse(StagedRestore.apply(db))
        assertEquals("old", db.readText())
    }
}
