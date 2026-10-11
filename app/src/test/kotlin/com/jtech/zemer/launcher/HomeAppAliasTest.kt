package com.jtech.zemer.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Launcher lock: a marker locks it, and only "marker gone + app data cleared" unlocks it. */
class HomeAppAliasTest {
    private val dir = Files.createTempDirectory("zlauncher").toFile()
    private val marker = File(dir, ".zlauncher")
    private val missing = File(dir, "absent")
    private val latch = File(dir, "latch") // stands in for the file in noBackupFilesDir

    @Test
    fun noMarkerIsUnmanaged() {
        assertFalse(HomeAppAlias.isManaged(listOf(missing), latch))
        assertFalse(latch.exists())
    }

    @Test
    fun anyMarkerLocksAndStaysLockedAfterItIsRemoved() {
        marker.createNewFile()
        assertTrue(HomeAppAlias.isManaged(listOf(missing, marker), latch))
        marker.delete()
        assertTrue(HomeAppAlias.isManaged(listOf(missing, marker), latch))
    }

    @Test
    fun clearingDataWithTheMarkerGoneUnlocks() {
        marker.createNewFile()
        HomeAppAlias.isManaged(listOf(marker), latch)
        marker.delete()
        latch.delete() // "clear data"
        assertFalse(HomeAppAlias.isManaged(listOf(marker), latch))
    }

    @Test
    fun clearingDataWithTheMarkerStillThereStaysLocked() {
        marker.createNewFile()
        HomeAppAlias.isManaged(listOf(marker), latch)
        latch.delete() // "clear data" only
        assertTrue(HomeAppAlias.isManaged(listOf(marker), latch))
    }

    @Test
    fun everyMarkerIsAnAbsoluteDotFileNamedZlauncher() {
        HomeAppAlias.MANAGED_MARKERS.forEach {
            assertTrue(it.isAbsolute)
            assertTrue(it.name == ".zlauncher")
        }
    }
}
