package com.jtech.zemer.ui.menu

import com.jtech.zemer.playback.DownloadRowKind
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Locks the unified download menu row builder so every menu wires the SAME action to the SAME row,
 * and so the "menu stays open" contract can't silently regress (the builder must never attach a
 * dismiss). Compose lambdas are stored, not invoked, so this runs as a plain JVM test.
 */
class DownloadMenuItemsTest {

    @Test fun hidden_producesNoRow() {
        assertNull(downloadMenuItem(DownloadRowKind.HIDDEN))
    }

    @Test fun remove_firesOnRemoveOnly() {
        val onDownload = {}; val onCancel = {}; val onRetry = {}; val onRemove = {}
        val item = downloadMenuItem(
            DownloadRowKind.REMOVE,
            onDownload = onDownload, onCancel = onCancel, onRetry = onRetry, onRemove = onRemove,
        )
        assertNotNull(item)
        assertSame(onRemove, item!!.onClick)
    }

    @Test fun downloading_firesOnCancel() {
        val onDownload = {}; val onCancel = {}; val onRetry = {}; val onRemove = {}
        val item = downloadMenuItem(
            DownloadRowKind.DOWNLOADING, progress = 0.5f,
            onDownload = onDownload, onCancel = onCancel, onRetry = onRetry, onRemove = onRemove,
        )
        assertSame(onCancel, item!!.onClick)
    }

    @Test fun failed_firesOnRetry() {
        val onDownload = {}; val onCancel = {}; val onRetry = {}; val onRemove = {}
        val item = downloadMenuItem(
            DownloadRowKind.FAILED,
            onDownload = onDownload, onCancel = onCancel, onRetry = onRetry, onRemove = onRemove,
        )
        assertSame(onRetry, item!!.onClick)
    }

    @Test fun download_firesOnDownload() {
        val onDownload = {}; val onCancel = {}; val onRetry = {}; val onRemove = {}
        val item = downloadMenuItem(
            DownloadRowKind.DOWNLOAD,
            onDownload = onDownload, onCancel = onCancel, onRetry = onRetry, onRemove = onRemove,
        )
        assertSame(onDownload, item!!.onClick)
    }

    @Test fun downloadVideo_firesOnDownload() {
        val onDownload = {}; val onCancel = {}; val onRetry = {}; val onRemove = {}
        val item = downloadMenuItem(
            DownloadRowKind.DOWNLOAD_VIDEO,
            onDownload = onDownload, onCancel = onCancel, onRetry = onRetry, onRemove = onRemove,
        )
        assertSame(onDownload, item!!.onClick)
    }

    @Test fun everyNonHiddenKind_producesARow() {
        DownloadRowKind.entries.filter { it != DownloadRowKind.HIDDEN }.forEach { kind ->
            assertNotNull("expected a row for $kind", downloadMenuItem(kind))
        }
    }
}
