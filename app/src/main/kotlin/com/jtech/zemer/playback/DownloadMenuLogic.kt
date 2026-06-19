package com.jtech.zemer.playback

/**
 * Which download row a menu should show for an item or collection. Decoupled from Compose so the
 * decision is unit-tested without an Android runtime — the row's look/behaviour live in one builder
 * ([com.jtech.zemer.ui.menu.DownloadMenuItem]) and the decision lives here, so neither can drift.
 */
enum class DownloadRowKind {
    /** Offer "Download to device". */
    DOWNLOAD,

    /** Offer "Download video to device" (single video item, videos not blocked). */
    DOWNLOAD_VIDEO,

    /** Show progress + "%", tapping cancels. */
    DOWNLOADING,

    /** Show "Download failed", tapping retries. */
    FAILED,

    /** Show "Remove download", tapping removes. */
    REMOVE,

    /** Show no download row at all (e.g. a video item while videos are blocked). */
    HIDDEN,
}

/**
 * The single rule for what a download menu row should do, given the unified [DownloadStatus]
 * (from [DownloadStateResolver]) plus whether the live download is in a FAILED state. Used by every
 * item/collection menu so "Download vs Remove vs progress vs retry" is decided identically.
 */
object DownloadMenuLogic {

    /**
     * Row for a single song. DOWNLOADED wins over a stale FAILED (the file is on disk), then FAILED,
     * then in-progress, then the download offer — hidden only for a blocked video.
     */
    fun songRow(
        status: DownloadStatus,
        failed: Boolean,
        isVideo: Boolean,
        blockVideos: Boolean,
    ): DownloadRowKind = when {
        status == DownloadStatus.DOWNLOADED -> DownloadRowKind.REMOVE
        failed -> DownloadRowKind.FAILED
        status == DownloadStatus.DOWNLOADING -> DownloadRowKind.DOWNLOADING
        isVideo && blockVideos -> DownloadRowKind.HIDDEN
        isVideo -> DownloadRowKind.DOWNLOAD_VIDEO
        else -> DownloadRowKind.DOWNLOAD
    }

    /**
     * Row for a collection (album / playlist / multi-select). No video variant — collections download
     * as audio. A failed member only surfaces "retry" while the collection isn't already fully on disk.
     */
    fun collectionRow(
        status: DownloadStatus,
        anyFailed: Boolean,
    ): DownloadRowKind = when {
        status == DownloadStatus.DOWNLOADED -> DownloadRowKind.REMOVE
        anyFailed -> DownloadRowKind.FAILED
        status == DownloadStatus.DOWNLOADING -> DownloadRowKind.DOWNLOADING
        else -> DownloadRowKind.DOWNLOAD
    }
}
