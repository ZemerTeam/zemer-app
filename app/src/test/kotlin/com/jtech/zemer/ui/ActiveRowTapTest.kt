package com.jtech.zemer.ui

import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The active-row tap rule (issue #324): tapping the currently-playing song in a list toggles
 * play/pause EXCEPT during a station broadcast, where it must fall through to a fresh play from 0
 * - the broadcast masks seek/skip, so the row tap is the only way to replay a song heard
 * mid-broadcast (e.g. like it on the radio, open Liked songs, tap it).
 */
class ActiveRowTapTest {

    @Test
    fun `active song toggles play-pause in normal playback`() {
        assertTrue(activeRowTapTogglesPlayPause(isActiveSong = true, isStationBroadcast = false))
    }

    @Test
    fun `active song falls through to a fresh play during a station broadcast`() {
        assertFalse(activeRowTapTogglesPlayPause(isActiveSong = true, isStationBroadcast = true))
    }

    @Test
    fun `a different song never toggles, station or not`() {
        assertFalse(activeRowTapTogglesPlayPause(isActiveSong = false, isStationBroadcast = false))
        assertFalse(activeRowTapTogglesPlayPause(isActiveSong = false, isStationBroadcast = true))
    }
}
