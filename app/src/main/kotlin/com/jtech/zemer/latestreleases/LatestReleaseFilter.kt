package com.jtech.zemer.latestreleases

/**
 * The "See all" Latest Releases screen filter. A release is a SONG when it's a playable single
 * (exactly one track, see [isPlayableSingle]) and an ALBUM otherwise — the same single/album split
 * the cards already use for tap behaviour, so the filter matches what the user sees on each row.
 */
enum class LatestReleaseFilter { ALL, ALBUMS, SONGS }

/** Keeps only the releases matching [filter], preserving feed order. Pure, so it's unit-testable. */
fun List<LatestRelease>.applyFilter(filter: LatestReleaseFilter): List<LatestRelease> = when (filter) {
    LatestReleaseFilter.ALL -> this
    LatestReleaseFilter.ALBUMS -> filter { !it.isPlayableSingle() }
    LatestReleaseFilter.SONGS -> filter { it.isPlayableSingle() }
}
