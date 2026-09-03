package com.jtech.zemer.ui.component.lyrics

import com.jtech.zemer.db.entities.LyricsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSourceHeaderTest {
    @Test
    fun `zemer labels collapse to Zemer, others unchanged, legacy hidden`() {
        assertEquals("Zemer", displayProviderName("Zemer · jkaraoke"))
        assertEquals("Zemer", displayProviderName("Zemer · shironet ✓"))
        assertEquals("Zemer", displayProviderName("Zemer"))
        assertEquals("Musixmatch", displayProviderName("Musixmatch"))
        assertEquals("SimpMusic", displayProviderName("SimpMusic ✓"))
        assertNull(displayProviderName(LyricsEntity.PROVIDER_LEGACY))
        assertNull(displayProviderName(null))
    }
}
