package com.jtech.zemer.ui.component

import com.jtech.zemer.utils.UpdateChecker.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class UpdateDownloadDialogLogicTest {

    @Test
    fun `primary action follows the download and install state`() {
        assertEquals(UpdateDialogAction.DOWNLOAD, updateDialogPrimaryAction(DownloadState.Idle, isInstalling = false))
        assertEquals(UpdateDialogAction.CANCEL, updateDialogPrimaryAction(DownloadState.Downloading(0.4f), isInstalling = false))
        assertEquals(UpdateDialogAction.INSTALL, updateDialogPrimaryAction(DownloadState.Downloaded(File("a.apk")), isInstalling = false))
        assertEquals(UpdateDialogAction.RETRY, updateDialogPrimaryAction(DownloadState.Error("x"), isInstalling = false))
    }

    @Test
    fun `installing offers no action regardless of download state`() {
        assertEquals(UpdateDialogAction.NONE, updateDialogPrimaryAction(DownloadState.Downloaded(File("a.apk")), isInstalling = true))
        assertEquals(UpdateDialogAction.NONE, updateDialogPrimaryAction(DownloadState.Error("x"), isInstalling = true))
    }

    @Test
    fun `nightly value drops the redundant channel prefix, stable value is untouched`() {
        assertEquals("#1215 (0f8e9f8)", updateVersionValue("nightly #1215 (0f8e9f8)", isNightly = true))
        assertEquals("39", updateVersionValue("39", isNightly = false))
    }
}
