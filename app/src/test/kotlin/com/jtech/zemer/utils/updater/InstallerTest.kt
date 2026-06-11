package com.jtech.zemer.utils.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallerTest {

    @Test
    fun `fromOrdinal resolves each installer type`() {
        InstallerType.entries.forEach { type ->
            assertEquals(type, InstallerType.fromOrdinal(type.ordinal))
        }
    }

    @Test
    fun `fromOrdinal falls back to NATIVE for unknown ordinals`() {
        assertEquals(InstallerType.NATIVE, InstallerType.fromOrdinal(-1))
        assertEquals(InstallerType.NATIVE, InstallerType.fromOrdinal(InstallerType.entries.size))
        assertEquals(InstallerType.NATIVE, InstallerType.fromOrdinal(Int.MAX_VALUE))
    }

    @Test
    fun `ordinals are stable`() {
        // Persisted in DataStore (InstallerTypeKey) — reordering the enum corrupts saved settings
        assertEquals(0, InstallerType.NATIVE.ordinal)
        assertEquals(1, InstallerType.ROOT.ordinal)
        assertEquals(2, InstallerType.SHIZUKU.ordinal)
    }

    @Test
    fun `parseSessionId extracts id from pm install-create output`() {
        assertEquals(123, AppInstaller.parseSessionId(listOf("Success: created install session [123]")))
        assertEquals(7, AppInstaller.parseSessionId(listOf("Success: created install session [7]", "ignored")))
    }

    @Test
    fun `parseSessionId returns null when no id present`() {
        assertNull(AppInstaller.parseSessionId(emptyList()))
        assertNull(AppInstaller.parseSessionId(listOf("")))
        assertNull(AppInstaller.parseSessionId(listOf("Failure: no session")))
    }
}
