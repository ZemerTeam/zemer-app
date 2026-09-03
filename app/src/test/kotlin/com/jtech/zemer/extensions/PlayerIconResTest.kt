package com.jtech.zemer.extensions

import androidx.media3.common.Player
import com.jtech.zemer.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one repeat/shuffle icon mapping (#400): active modes must render the distinct filled-badge
 * glyphs — OFF may never share ON's icon (the "only a few shades lighter" complaint), and every
 * surface (player, queue sheet, lyrics, notification) reads these helpers so the mapping can't
 * drift per site.
 */
class PlayerIconResTest {
    @Test
    fun `repeat off is the plain glyph`() {
        assertEquals(R.drawable.repeat, repeatModeIconRes(Player.REPEAT_MODE_OFF))
    }

    @Test
    fun `repeat all is the filled badge glyph`() {
        assertEquals(R.drawable.repeat_on, repeatModeIconRes(Player.REPEAT_MODE_ALL))
    }

    @Test
    fun `repeat one is the filled badge one glyph`() {
        assertEquals(R.drawable.repeat_one_on, repeatModeIconRes(Player.REPEAT_MODE_ONE))
    }

    /** The accessibility label names the CURRENT mode, never a fixed "off" over an icon that shows on. */
    @Test
    fun `repeat content description follows the mode`() {
        assertEquals(R.string.repeat_mode_off, repeatModeContentDescriptionRes(Player.REPEAT_MODE_OFF))
        assertEquals(R.string.repeat_mode_all, repeatModeContentDescriptionRes(Player.REPEAT_MODE_ALL))
        assertEquals(R.string.repeat_mode_one, repeatModeContentDescriptionRes(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `off icon differs from every active icon`() {
        val off = repeatModeIconRes(Player.REPEAT_MODE_OFF)
        assert(off != repeatModeIconRes(Player.REPEAT_MODE_ALL))
        assert(off != repeatModeIconRes(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `shuffle maps plain when off and filled badge when on`() {
        assertEquals(R.drawable.shuffle, shuffleIconRes(false))
        assertEquals(R.drawable.shuffle_on, shuffleIconRes(true))
    }
}
