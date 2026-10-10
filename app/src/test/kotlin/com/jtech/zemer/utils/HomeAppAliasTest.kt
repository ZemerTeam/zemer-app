package com.jtech.zemer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The launcher marker decides managed mode by existence only (its contents are never read). */
class HomeAppAliasTest {
    @Test
    fun managedWhenAnyMarkerExists() {
        val marker = File.createTempFile("zlauncher", null)
        val missing = File(marker.parentFile, "absent-zlauncher")
        assertTrue(HomeAppAlias.isManaged(listOf(missing, marker)))
        marker.delete()
        assertFalse(HomeAppAlias.isManaged(listOf(missing, marker)))
    }
}
