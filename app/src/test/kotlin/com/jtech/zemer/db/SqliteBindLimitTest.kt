package com.jtech.zemer.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: `getSongsByIds` over 1000+ cached ids threw "too many SQL variables" on Android 8 (999-variable limit). */
class SqliteBindLimitTest {
    @Test
    fun `every chunk stays under the SQLite variable limit and nothing is lost or reordered`() {
        val ids = (1..2345).map { "id$it" }
        val chunks = SqliteBindLimit.chunks(ids)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.size <= SqliteBindLimit.MAX_IN_VARIABLES && it.size < 999 })
        assertEquals(ids, chunks.flatten())
    }

    @Test
    fun `short lists are one chunk and an empty list runs no query`() {
        assertEquals(listOf(listOf("a", "b")), SqliteBindLimit.chunks(listOf("a", "b")))
        assertTrue(SqliteBindLimit.chunks(emptyList<String>()).isEmpty())
    }
}
