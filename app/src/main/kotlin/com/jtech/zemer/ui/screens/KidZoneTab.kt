package com.jtech.zemer.ui.screens

/**
 * The KidZone screen's content-type tabs (the Home content-selector pattern): kid ARTISTS (the
 * local whitelist slice, the screen's original content) and kid PODCASTS (the server's kid-flagged
 * shows). Pure + unit-tested (KidZoneTabTest).
 */
enum class KidZoneTab { ARTISTS, PODCASTS }

/**
 * The tabs under the current flags. Block Podcasts removes the PODCASTS tab entirely (the same
 * category gate as the Home selector and the nav surfaces) — with one tab left, the chip row
 * itself is hidden and KidZone renders as the plain artist browse.
 */
fun visibleKidZoneTabs(blockPodcasts: Boolean): List<KidZoneTab> = buildList {
    add(KidZoneTab.ARTISTS)
    if (!blockPodcasts) add(KidZoneTab.PODCASTS)
}

/** A persisted/selected tab that is no longer visible falls back to ARTISTS. */
fun effectiveKidZoneTab(selected: KidZoneTab, visible: List<KidZoneTab>): KidZoneTab =
    if (selected in visible) selected else KidZoneTab.ARTISTS
