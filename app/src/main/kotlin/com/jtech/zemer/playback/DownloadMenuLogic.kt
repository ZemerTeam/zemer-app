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
     * as audio, and there is deliberately NO collection-level FAILED row: a failed member just leaves
     * the aggregate NOT_DOWNLOADED, so the collection offers DOWNLOAD again (which re-enqueues only the
     * not-yet-downloaded members — i.e. retries the failed one) and stays removable once everything is
     * on disk. A dedicated "retry" row here was a dead end — it hid Download/Remove and re-failed the
     * dead track forever with no escape (single songs still get their own FAILED row via [songRow]).
     */
    fun collectionRow(
        status: DownloadStatus,
    ): DownloadRowKind = when (status) {
        DownloadStatus.DOWNLOADED -> DownloadRowKind.REMOVE
        DownloadStatus.DOWNLOADING -> DownloadRowKind.DOWNLOADING
        DownloadStatus.NOT_DOWNLOADED -> DownloadRowKind.DOWNLOAD
    }
}
