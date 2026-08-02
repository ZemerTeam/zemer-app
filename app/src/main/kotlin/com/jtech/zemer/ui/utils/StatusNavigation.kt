package com.jtech.zemer.ui.utils

/**
 * Route to the full-screen story viewer, opened at the given creator index within the Home row's
 * creator list. Pure so it is unit-tested (see StatusNavigationTest). The viewer reads the creators
 * list from the shared session cache, so only the start index needs to travel in the route.
 */
fun storyRoute(index: Int): String = "story/$index"
