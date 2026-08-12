package com.jtech.zemer.ui.screens

/**
 * The Home content-type selector tabs (the top [com.jtech.zemer.ui.component.ChipsRow]). Each tab
 * renders only its own shelves: MUSIC the music feed, PODCASTS the podcast surfaces, RADIO the Zemer
 * Radio stations, VIDEO the featured video-songs. VIDEO is ALWAYS shown — blocked-video users get it
 * relabeled "Video songs" with audio-first rows (never hidden; a `!blockVideos` visibility gate here
 * is a regression). PODCASTS is the one tab a content filter removes (Block Podcasts hides the whole
 * content type). MUSIC is the default view.
 */
enum class HomeContentTab { MUSIC, PODCASTS, RADIO, VIDEO }

/**
 * The selector's tabs under the current flags — Block Podcasts removes PODCASTS (see class doc),
 * and manual offline mode removes RADIO (a synchronized live broadcast cannot be served from
 * downloads; every other tab has a downloaded-only rendering). VIDEO stays always visible in both.
 */
fun visibleHomeTabs(blockPodcasts: Boolean, offlineMode: Boolean = false): List<HomeContentTab> = buildList {
    add(HomeContentTab.MUSIC)
    if (!offlineMode) add(HomeContentTab.RADIO)
    if (!blockPodcasts) add(HomeContentTab.PODCASTS)
    add(HomeContentTab.VIDEO)
}

/**
 * The tab Home may land on: a persisted PODCASTS selection falls back to MUSIC when blocked, and a
 * persisted RADIO selection falls back to MUSIC in offline mode (same hidden-tab rule).
 */
fun effectiveHomeTab(
    persisted: HomeContentTab,
    blockPodcasts: Boolean,
    offlineMode: Boolean = false,
): HomeContentTab = when {
    blockPodcasts && persisted == HomeContentTab.PODCASTS -> HomeContentTab.MUSIC
    offlineMode && persisted == HomeContentTab.RADIO -> HomeContentTab.MUSIC
    else -> persisted
}
