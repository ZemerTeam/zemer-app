package com.jtech.zemer.ui.menu

import com.jtech.zemer.playback.DownloadRowKind
import org.junit.Assert.assertEquals
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
    private val ungated: (() -> Unit) -> Unit = { it() }

    /** Taps the row's action through a gate that records itself; returns (action that ran, gated?). */
    private fun tap(kind: DownloadRowKind): Pair<String, Boolean> {
        var ran = ""
        var gated = false
        downloadMenuItem(
            kind,
            storageGate = { action -> gated = true; action() },
            onDownload = { ran = "download" }, onCancel = { ran = "cancel" },
            onRetry = { ran = "retry" }, onRemove = { ran = "remove" },
        )!!.onClick!!()
        return ran to gated
    }

    @Test fun hidden_producesNoRow() {
        assertNull(downloadMenuItem(DownloadRowKind.HIDDEN, storageGate = ungated))
    }

    @Test fun remove_firesOnRemoveOnly() {
        val onDownload = {}; val onCancel = {}; val onRetry = {}; val onRemove = {}
        val item = downloadMenuItem(
            DownloadRowKind.REMOVE, storageGate = ungated,
            onDownload = onDownload, onCancel = onCancel, onRetry = onRetry, onRemove = onRemove,
        )
        assertNotNull(item)
        assertSame(onRemove, item!!.onClick)
    }

    @Test fun downloading_firesOnCancel() {
        val onDownload = {}; val onCancel = {}; val onRetry = {}; val onRemove = {}
        val item = downloadMenuItem(
            DownloadRowKind.DOWNLOADING, storageGate = ungated, progress = 0.5f,
            onDownload = onDownload, onCancel = onCancel, onRetry = onRetry, onRemove = onRemove,
        )
        assertSame(onCancel, item!!.onClick)
    }

    // Download and Retry ask for the storage permission first (a failed row asks again); Cancel and Remove do not.
    @Test fun failed_firesOnRetry_throughTheStorageGate() {
        assertEquals("retry" to true, tap(DownloadRowKind.FAILED))
    }

    @Test fun download_firesOnDownload_throughTheStorageGate() {
        assertEquals("download" to true, tap(DownloadRowKind.DOWNLOAD))
    }

    @Test fun downloadVideo_firesOnDownload_throughTheStorageGate() {
        assertEquals("download" to true, tap(DownloadRowKind.DOWNLOAD_VIDEO))
    }

    @Test fun cancelAndRemove_skipTheStorageGate() {
        assertEquals("cancel" to false, tap(DownloadRowKind.DOWNLOADING))
        assertEquals("remove" to false, tap(DownloadRowKind.REMOVE))
    }

    @Test fun everyNonHiddenKind_producesARow() {
        DownloadRowKind.entries.filter { it != DownloadRowKind.HIDDEN }.forEach { kind ->
            assertNotNull("expected a row for $kind", downloadMenuItem(kind, storageGate = ungated))
        }
    }
}
