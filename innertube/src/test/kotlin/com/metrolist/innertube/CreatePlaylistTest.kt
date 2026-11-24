package com.metrolist.innertube

import org.junit.Assert.assertTrue
import org.junit.Test

/** A rejected login (401) or no network fails the Result instead of throwing into the caller's coroutine. */
class CreatePlaylistTest {
    @Test
    fun createWithoutALoginFailsWithoutThrowing() {
        YouTube.cookie = null
        assertTrue(YouTube.createPlaylist("test").isFailure)
    }
}
