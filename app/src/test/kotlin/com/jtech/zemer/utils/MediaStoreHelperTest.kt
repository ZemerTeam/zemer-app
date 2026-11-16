package com.jtech.zemer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreHelperTest {
    @Test
    fun `a download saves when the primary volume is present`() {
        assertTrue(MediaStoreHelper.canSaveDownload(primaryVolumePresent = true, video = false, customPath = false))
        assertTrue(MediaStoreHelper.canSaveDownload(primaryVolumePresent = true, video = true, customPath = true))
    }

    @Test
    fun `without the primary volume only an audio download to a custom folder can save`() {
        assertFalse(MediaStoreHelper.canSaveDownload(primaryVolumePresent = false, video = false, customPath = false))
        assertFalse(MediaStoreHelper.canSaveDownload(primaryVolumePresent = false, video = true, customPath = false))
        // video always saves through MediaStore, a custom folder or not
        assertFalse(MediaStoreHelper.canSaveDownload(primaryVolumePresent = false, video = true, customPath = true))
        assertTrue(MediaStoreHelper.canSaveDownload(primaryVolumePresent = false, video = false, customPath = true))
    }

    @Test
    fun `only a MediaStore save before Android 10 needs the storage permission`() {
        assertTrue(MediaStoreHelper.needsStoragePermission(sdk = 28, video = false, customPath = false))
        assertTrue(MediaStoreHelper.needsStoragePermission(sdk = 28, video = true, customPath = true))
        assertFalse(MediaStoreHelper.needsStoragePermission(sdk = 28, video = false, customPath = true))
        assertFalse(MediaStoreHelper.needsStoragePermission(sdk = 29, video = false, customPath = false))
        assertFalse(MediaStoreHelper.needsStoragePermission(sdk = 36, video = true, customPath = false))
    }
}
