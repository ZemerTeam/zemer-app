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

/** The selector's tabs under the current flags — only Block Podcasts removes one (see class doc). */
fun visibleHomeTabs(blockPodcasts: Boolean): List<HomeContentTab> = buildList {
    add(HomeContentTab.MUSIC)
    add(HomeContentTab.RADIO)
    if (!blockPodcasts) add(HomeContentTab.PODCASTS)
    add(HomeContentTab.VIDEO)
}

/** The tab Home may land on: a persisted PODCASTS selection falls back to MUSIC when blocked. */
fun effectiveHomeTab(persisted: HomeContentTab, blockPodcasts: Boolean): HomeContentTab =
    if (blockPodcasts && persisted == HomeContentTab.PODCASTS) HomeContentTab.MUSIC else persisted
