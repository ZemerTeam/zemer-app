package com.jtech.zemer.repositories

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A format row with no known length is never asked of the cache (SimpleCache throws on a negative one). */
class CachedSongsRepositoryTest {
    private val throwsOnBadLength: (Long) -> Boolean = { require(it >= 0); true }

    @Test
    fun unknownLengthIsNotCachedAndNeverQueried() {
        assertFalse(CachedSongsRepository.fullyCached(-1L, throwsOnBadLength))
        assertFalse(CachedSongsRepository.fullyCached(0L, throwsOnBadLength))
        assertFalse(CachedSongsRepository.fullyCached(null, throwsOnBadLength))
    }

    @Test
    fun knownLengthAsksTheCache() {
        assertTrue(CachedSongsRepository.fullyCached(1000L) { it == 1000L })
        assertFalse(CachedSongsRepository.fullyCached(1000L) { false })
    }
}
