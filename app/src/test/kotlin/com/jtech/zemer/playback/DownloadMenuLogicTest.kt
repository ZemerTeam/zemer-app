package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Strict lock on the menu decision so "Download vs Remove vs progress vs retry" can never differ
 * between menus again. The two historical bugs this guards: (1) a downloaded item still offering
 * "Download" because the menu read a live-only source, and (2) a blocked video still showing a
 * download row.
 */
class DownloadMenuLogicTest {

    // ---- single song ----

    @Test fun downloaded_song_offersRemove_evenIfLiveFailed() {
        // Persisted DOWNLOADED must win over a stale FAILED live flag — the file is on disk.
        assertEquals(
            DownloadRowKind.REMOVE,
            DownloadMenuLogic.songRow(DownloadStatus.DOWNLOADED, failed = true, isVideo = false, blockVideos = false),
        )
    }

    @Test fun failed_song_offersRetry() {
        assertEquals(
            DownloadRowKind.FAILED,
            DownloadMenuLogic.songRow(DownloadStatus.NOT_DOWNLOADED, failed = true, isVideo = false, blockVideos = false),
        )
    }

    @Test fun downloading_song_showsProgress() {
        assertEquals(
            DownloadRowKind.DOWNLOADING,
            DownloadMenuLogic.songRow(DownloadStatus.DOWNLOADING, failed = false, isVideo = false, blockVideos = false),
        )
    }

    @Test fun notDownloaded_audio_offersDownload() {
        assertEquals(
            DownloadRowKind.DOWNLOAD,
            DownloadMenuLogic.songRow(DownloadStatus.NOT_DOWNLOADED, failed = false, isVideo = false, blockVideos = false),
        )
    }

    @Test fun notDownloaded_video_offersVideoDownload() {
        assertEquals(
            DownloadRowKind.DOWNLOAD_VIDEO,
            DownloadMenuLogic.songRow(DownloadStatus.NOT_DOWNLOADED, failed = false, isVideo = true, blockVideos = false),
        )
    }

    @Test fun blockedVideo_isHidden() {
        assertEquals(
            DownloadRowKind.HIDDEN,
            DownloadMenuLogic.songRow(DownloadStatus.NOT_DOWNLOADED, failed = false, isVideo = true, blockVideos = true),
        )
    }

    @Test fun blockedVideo_thatIsDownloaded_stillOffersRemove() {
        // A blocked video already on disk must still be removable.
        assertEquals(
            DownloadRowKind.REMOVE,
            DownloadMenuLogic.songRow(DownloadStatus.DOWNLOADED, failed = false, isVideo = true, blockVideos = true),
        )
    }

    // ---- collection ----

    @Test fun collection_allDownloaded_offersRemove() {
        assertEquals(DownloadRowKind.REMOVE, DownloadMenuLogic.collectionRow(DownloadStatus.DOWNLOADED, anyFailed = false))
    }

    @Test fun collection_downloaded_winsOverFailedMember() {
        assertEquals(DownloadRowKind.REMOVE, DownloadMenuLogic.collectionRow(DownloadStatus.DOWNLOADED, anyFailed = true))
    }

    @Test fun collection_failedMember_offersRetry() {
        assertEquals(DownloadRowKind.FAILED, DownloadMenuLogic.collectionRow(DownloadStatus.NOT_DOWNLOADED, anyFailed = true))
    }

    @Test fun collection_inProgress_showsProgress() {
        assertEquals(DownloadRowKind.DOWNLOADING, DownloadMenuLogic.collectionRow(DownloadStatus.DOWNLOADING, anyFailed = false))
    }

    @Test fun collection_none_offersDownload() {
        assertEquals(DownloadRowKind.DOWNLOAD, DownloadMenuLogic.collectionRow(DownloadStatus.NOT_DOWNLOADED, anyFailed = false))
    }

    // ---- exhaustive invariants over the full input space ----

    @Test fun songRow_downloadedAlwaysRemove_regardlessOfFlags() {
        for (failed in listOf(true, false)) for (isVideo in listOf(true, false)) for (block in listOf(true, false)) {
            assertEquals(
                "downloaded must always offer Remove (failed=$failed video=$isVideo block=$block)",
                DownloadRowKind.REMOVE,
                DownloadMenuLogic.songRow(DownloadStatus.DOWNLOADED, failed, isVideo, block),
            )
        }
    }

    @Test fun songRow_blockedVideoNeverOffersAVideoDownload() {
        for (status in DownloadStatus.entries) for (failed in listOf(true, false)) {
            val kind = DownloadMenuLogic.songRow(status, failed, isVideo = true, blockVideos = true)
            assertNotEquals(DownloadRowKind.DOWNLOAD_VIDEO, kind)
            assertNotEquals(DownloadRowKind.DOWNLOAD, kind)
        }
    }

    @Test fun songRow_neverReturnsDownloadVideoForNonVideo() {
        for (status in DownloadStatus.entries) for (failed in listOf(true, false)) for (block in listOf(true, false)) {
            assertNotEquals(
                DownloadRowKind.DOWNLOAD_VIDEO,
                DownloadMenuLogic.songRow(status, failed, isVideo = false, blockVideos = block),
            )
        }
    }

    @Test fun collectionRow_neverHiddenOrVideo() {
        for (status in DownloadStatus.entries) for (failed in listOf(true, false)) {
            val kind = DownloadMenuLogic.collectionRow(status, failed)
            assertNotEquals(DownloadRowKind.HIDDEN, kind)
            assertNotEquals(DownloadRowKind.DOWNLOAD_VIDEO, kind)
        }
    }
}
