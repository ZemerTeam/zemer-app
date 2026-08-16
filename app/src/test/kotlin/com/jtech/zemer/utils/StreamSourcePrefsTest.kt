package com.jtech.zemer.utils

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.jtech.zemer.constants.StreamSourceMWEBKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceVisionOSKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourcePrefsTest {

    @Test
    fun `disabledFamilies is a prefix scan of explicit-false keys`() {
        val prefs = preferencesOf(
            StreamSourcePrefs.familyKey("VISIONOS") to false,
            StreamSourcePrefs.familyKey("MWEB") to true,
            // Unrelated keys must not leak in.
            booleanPreferencesKey("streamSourceWebRemix") to false,
            booleanPreferencesKey("somethingElse") to false,
        )
        assertEquals(setOf("VISIONOS"), StreamSourcePrefs.disabledFamilies(prefs))
    }

    @Test
    fun `absent keys mean enabled`() {
        assertTrue(StreamSourcePrefs.disabledFamilies(preferencesOf()).isEmpty())
    }

    @Test
    fun `a remotely-added family works with no registry`() {
        val prefs = preferencesOf(StreamSourcePrefs.familyKey("NEW_CLIENT") to false)
        assertEquals(setOf("NEW_CLIENT"), StreamSourcePrefs.disabledFamilies(prefs))
    }

    @Test
    fun `migration copies only explicit-false legacy toggles`() {
        val prefs = preferencesOf(
            StreamSourceTVHTML5Key to false,
            StreamSourceVisionOSKey to true,
            // MWEB absent = default-true, no write needed.
        )
        assertEquals(mapOf("TVHTML5" to false), StreamSourcePrefs.migrationWrites(prefs))
    }

    @Test
    fun `migration of a default snapshot writes nothing`() {
        assertTrue(StreamSourcePrefs.migrationWrites(preferencesOf()).isEmpty())
    }

    @Test
    fun `every legacy key maps to a distinct family`() {
        val families = StreamSourcePrefs.LEGACY_KEY_TO_FAMILY.values
        assertEquals(6, families.size)
        assertEquals(families.size, families.toSet().size)
        assertTrue("MWEB" in families)
        assertEquals("MWEB", StreamSourcePrefs.LEGACY_KEY_TO_FAMILY[StreamSourceMWEBKey])
    }
}
