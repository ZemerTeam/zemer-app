package com.jtech.zemer.ui.utils

/**
 * Whether tapping a list row for the CURRENTLY-PLAYING song should toggle play/pause (the
 * app-wide convention) or fall through to the row's normal "play this" path.
 *
 * During a station broadcast the toggle arm is a dead end (issue #324): the listener joined the
 * song mid-broadcast, and every restart affordance - seek, skip, previous - is deliberately
 * masked, so a liked song heard on the radio could never be replayed from the beginning. Falling
 * through starts the song as a fresh play from 0, which also exits broadcast mode through the
 * same machinery as any queue swap. Off-station behavior is unchanged.
 *
 * Every active-row tap site in `ui/screens/` routes its condition through this ONE predicate;
 * never inline the `&& !isStationBroadcast` per screen (drift between surfaces is how conventions
 * rot). The queue sheet (`ui/player/Queue.kt`) is deliberately NOT converted: its fall-through is
 * a seek to the tapped window - a broadcast-forbidden mid-schedule jump - not a fresh play, its
 * taps are already station-gated at the site, and pause is the one legitimate broadcast transport
 * there.
 */
fun activeRowTapTogglesPlayPause(isActiveSong: Boolean, isStationBroadcast: Boolean): Boolean =
    isActiveSong && !isStationBroadcast
