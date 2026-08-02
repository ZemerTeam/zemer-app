package com.jtech.zemer.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.jtech.zemer.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Artists : Screens(
        titleId = R.string.artists,
        iconIdInactive = R.drawable.artist,
        iconIdActive = R.drawable.artist,
        route = "artists"
    )

    // Reached from the drawer, "Browse all podcasts" in Search, and the Library filter - never a
    // default bottom-nav tab (mirrors Artists, per the surface-in-search decision).
    object Podcasts : Screens(
        titleId = R.string.podcasts,
        iconIdInactive = R.drawable.podcast,
        iconIdActive = R.drawable.podcast,
        route = "podcasts"
    )

    object KidZone : Screens(
        titleId = R.string.kid_zone,
        iconIdInactive = R.drawable.kid_zone,
        iconIdActive = R.drawable.kid_zone,
        route = "kid_zone"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search,
        route = "search"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_music_outlined,
        iconIdActive = R.drawable.library_music_filled,
        route = "library"
    )

    companion object {
        val MainScreens = listOf(Home, Artists, Podcasts, KidZone, Search, Library)
    }
}
