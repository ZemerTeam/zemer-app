package com.jtech.zemer.statuses

/**
 * Pure filter/sort logic for the Status downloads library, kept UI-free so it is JVM-testable and
 * shared by the screen and its ViewModel. The chip filters on the ORIGINAL kind (text-as-image still
 * counts as text); the sort control is a flat chronological order.
 */

/** Kind filter chip. [kind] null = show everything. */
enum class StatusKindFilter(val kind: String?) {
    ALL(null),
    VIDEO("video"),
    IMAGE("image"),
    TEXT("text"),
}

/** Sort mode: a flat chronological grid by save time or by the status's posted time. */
enum class StatusDownloadSort {
    RECENT_SAVED,
    RECENT_POSTED,
}

fun List<StatusDownload>.filterByKind(filter: StatusKindFilter): List<StatusDownload> =
    filter.kind?.let { k -> this.filter { it.kind == k } } ?: this

/** Flat ordering. `postedAt` is ISO-8601, so string order is chronological. */
fun List<StatusDownload>.sortedFlat(sort: StatusDownloadSort): List<StatusDownload> = when (sort) {
    StatusDownloadSort.RECENT_SAVED -> sortedByDescending { it.savedAt }
    StatusDownloadSort.RECENT_POSTED -> sortedByDescending { it.postedAt }
}
