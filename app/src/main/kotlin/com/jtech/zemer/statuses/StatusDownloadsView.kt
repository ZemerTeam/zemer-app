package com.jtech.zemer.statuses

/**
 * Pure filter/sort/group logic for the Status downloads library, kept UI-free so it is JVM-testable and
 * shared by the screen and its ViewModel. The chip filters on the ORIGINAL kind (text-as-image still
 * counts as text); the sort control offers creator grouping or a flat chronological view.
 */

/** Kind filter chip. [kind] null = show everything. */
enum class StatusKindFilter(val kind: String?) {
    ALL(null),
    VIDEO("video"),
    IMAGE("image"),
    TEXT("text"),
}

/** Sort mode: creator grouping (A-Z, newest-within) or a flat chronological grid. */
enum class StatusDownloadSort {
    CREATOR,
    RECENT_SAVED,
    RECENT_POSTED,
}

/** A creator's saved statuses, newest-saved first, for the grouped-by-creator layout. */
data class StatusCreatorGroup(
    val creatorId: String,
    val creatorName: String,
    val items: List<StatusDownload>,
)

fun List<StatusDownload>.filterByKind(filter: StatusKindFilter): List<StatusDownload> =
    filter.kind?.let { k -> this.filter { it.kind == k } } ?: this

/** Flat ordering for the non-grouped sorts. `postedAt` is ISO-8601, so string order is chronological. */
fun List<StatusDownload>.sortedFlat(sort: StatusDownloadSort): List<StatusDownload> = when (sort) {
    StatusDownloadSort.CREATOR ->
        sortedWith(compareBy({ it.creatorName.lowercase() }, { -it.savedAt }))
    StatusDownloadSort.RECENT_SAVED -> sortedByDescending { it.savedAt }
    StatusDownloadSort.RECENT_POSTED -> sortedByDescending { it.postedAt }
}

/** Group into per-creator sections (creators A-Z, each section newest-saved first). */
fun List<StatusDownload>.groupByCreator(): List<StatusCreatorGroup> =
    groupBy { it.creatorId }
        .map { (id, items) ->
            val sorted = items.sortedByDescending { it.savedAt }
            StatusCreatorGroup(id, sorted.first().creatorName, sorted)
        }
        .sortedBy { it.creatorName.lowercase() }
