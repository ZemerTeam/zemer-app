package com.jtech.zemer.ui.utils

import androidx.navigation.NavController

/**
 * Null-safe navigation to the id-bearing detail routes.
 *
 * Building these routes by hand from an item's id (`navController.navigate("artist/$id")`) is the
 * crash we keep hitting: a null or blank id produces the route `"artist/"` (or `"album/"`), which
 * matches no registered destination and throws `IllegalArgumentException`. Item ids reach the UI
 * nullable/blank in several places (corpus artists with no channel id, etc.), so instead of guarding
 * at every call site, route every navigation through these helpers — a blank id yields a null route
 * and the navigation is skipped, so the whole class is handled once. Prefer these over a hand-built
 * `navigate("artist/$id")`.
 *
 * The route strings are built by the pure [artistRoute] / [albumRoute] so the blank-id guard is
 * unit-tested without an Android runtime (see AppNavigationTest).
 */
fun artistRoute(artistId: String?, isPodcastChannel: Boolean = false): String? =
    artistId?.takeIf { it.isNotBlank() }?.let {
        // Podcast host channels load via InnerTube (not the corpus) - the flag rides the route.
        if (isPodcastChannel) "artist/$it?isPodcastChannel=true" else "artist/$it"
    }

fun albumRoute(albumId: String?): String? =
    albumId?.takeIf { it.isNotBlank() }?.let { "album/$it" }

fun podcastRoute(podcastId: String?): String? =
    podcastId?.takeIf { it.isNotBlank() }?.let { "online_podcast/$it" }

/**
 * Where a browsed whitelisted podcast opens: the host CHANNEL page when a channelId is known (that is
 * where Subscribe + the host's shows live), otherwise the show's own episode list. Pure so the routing
 * decision is unit-tested (see AppNavigationTest).
 */
fun whitelistedPodcastRoute(podcastId: String?, channelId: String?): String? =
    channelId?.takeIf { it.isNotBlank() }?.let { artistRoute(it, isPodcastChannel = true) }
        ?: podcastRoute(podcastId)

fun NavController.navigateToArtist(artistId: String?, isPodcastChannel: Boolean = false) {
    artistRoute(artistId, isPodcastChannel)?.let(::navigate)
}

fun NavController.navigateToAlbum(albumId: String?) {
    albumRoute(albumId)?.let(::navigate)
}

fun NavController.navigateToPodcast(podcastId: String?) {
    podcastRoute(podcastId)?.let(::navigate)
}
