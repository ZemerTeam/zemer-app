package com.jtech.zemer.sync

import com.jtech.zemer.sync.models.DeviceContentFilters
import com.jtech.zemer.sync.models.DevicePreferencesEntity
import com.jtech.zemer.sync.models.UserDeviceData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFiltersChangedTest {
    private val stored = DeviceContentFilters(blockVideos = true, blockPodcasts = true)
    private val doc = DevicePreferencesEntity(
        contentFilters = DeviceContentFilters(), // the document-level copy is not this device's
        devices = listOf(UserDeviceData(deviceId = "other"), UserDeviceData(deviceId = "me", contentFilters = stored)),
    )

    @Test
    fun `the same filters for this device are not re-sent`() {
        assertFalse(deviceFiltersChanged(doc, "me", stored.copy()))
    }

    @Test
    fun `any changed field, or an unset podcast flag made explicit, is sent`() {
        assertTrue(deviceFiltersChanged(doc, "me", stored.copy(allowFemaleSingers = true)))
        assertTrue(deviceFiltersChanged(doc, "me", stored.copy(enableContentFilters = false)))
        assertTrue(deviceFiltersChanged(doc.copy(devices = listOf(UserDeviceData(deviceId = "me", contentFilters = stored.copy(blockPodcasts = null)))), "me", stored))
    }

    @Test
    fun `a device the document does not list counts as changed`() {
        assertTrue(deviceFiltersChanged(doc, "new", stored))
    }
}
