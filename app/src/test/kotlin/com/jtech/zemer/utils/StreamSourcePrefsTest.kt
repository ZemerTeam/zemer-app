package com.jtech.zemer.utils

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.jtech.zemer.constants.StreamSourceWebCreatorKey
import com.jtech.zemer.constants.StreamSabrTVHTML5Key
import com.jtech.zemer.constants.StreamSabrVisionOSKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.metrolist.innertube.models.YouTubeClient
import com.zemer.cipher.StreamClientParser
import com.jtech.zemer.constants.StreamSourceVisionOSKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourcePrefsTest {

    @Test
    fun `disabledFamilies is a prefix scan of explicit-false keys`() {
        val prefs = preferencesOf(
            StreamSourcePrefs.familyKey("VISIONOS") to false,
            StreamSourcePrefs.familyKey("WEB_CREATOR") to true,
            // Unrelated keys must not leak in.
            booleanPreferencesKey("streamSourceWebRemix") to false,
            booleanPreferencesKey("somethingElse") to false,
        )
        assertEquals(setOf("VISIONOS"), StreamSourcePrefs.disabledFamilies(prefs))
    }

    @Test
    fun `DIRECT and SABR family keys are separate namespaces`() {
        val prefs = preferencesOf(
            StreamSourcePrefs.familyKey("VISIONOS") to false,
            StreamSourcePrefs.sabrFamilyKey("TVHTML5") to false,
        )
        assertEquals(setOf("VISIONOS"), StreamSourcePrefs.disabledFamilies(prefs))
        assertEquals(setOf("TVHTML5"), StreamSourcePrefs.disabledSabrFamilies(prefs))
    }

    @Test
    fun `enabledSabrFamilies is the table's SABR roster minus the off switches`() {
        fun sc(key: String, family: String, sabr: Boolean) = StreamClient(
            YouTubeClient(clientName = key, clientVersion = "1.0", clientId = "1", userAgent = "ua"),
            family, key = key,
            sabr = if (sabr) StreamClientParser.StreamClientDef.SabrInfo() else null,
        )
        val table = StreamClientTable.Table(
            main = sc("WEB_REMIX", "WEB_REMIX", sabr = true),
            fallbacks = listOf(
                sc("VISIONOS", "VISIONOS", sabr = true),
                sc("VISIONOS_0_1", "VISIONOS", sabr = false),
                sc("WEB_CREATOR", "WEB_CREATOR", sabr = false),
                sc("TVHTML5_SIMPLY", "TVHTML5", sabr = true),
            ),
        )
        assertEquals(
            setOf("WEB_REMIX", "VISIONOS", "TVHTML5"),
            StreamSourcePrefs.enabledSabrFamilies(preferencesOf(), table),
        )
        assertEquals(
            setOf("WEB_REMIX", "TVHTML5"),
            StreamSourcePrefs.enabledSabrFamilies(
                preferencesOf(
                    StreamSourcePrefs.sabrFamilyKey("VISIONOS") to false,
                    // A DIRECT off switch never touches the SABR roster.
                    StreamSourcePrefs.familyKey("TVHTML5") to false,
                    // An off switch for a family not in the roster is inert.
                    StreamSourcePrefs.sabrFamilyKey("GONE") to false,
                ),
                table,
            ),
        )
    }

    @Test
    fun `SABR migration copies only explicit-false legacy SABR toggles`() {
        val prefs = preferencesOf(
            StreamSabrVisionOSKey to false,
            StreamSabrTVHTML5Key to true,
        )
        assertEquals(mapOf("VISIONOS" to false), StreamSourcePrefs.sabrMigrationWrites(prefs))
        assertEquals(
            setOf("WEB_REMIX", "VISIONOS", "TVHTML5"),
            StreamSourcePrefs.LEGACY_SABR_KEY_TO_FAMILY.values.toSet(),
        )
        // The two transports' legacy keys never cross-migrate.
        assertTrue(StreamSourcePrefs.migrationWrites(prefs).isEmpty())
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
            // WEB_CREATOR absent = default-true, no write needed.
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
        assertEquals(4, families.size)
        assertEquals(families.size, families.toSet().size)
        assertTrue("WEB_CREATOR" in families)
        assertEquals("WEB_CREATOR", StreamSourcePrefs.LEGACY_KEY_TO_FAMILY[StreamSourceWebCreatorKey])
        // The retired ANDROID_VR / MWEB toggles have no family: nothing to migrate onto.
        assertTrue("ANDROID_VR" !in families && "MWEB" !in families)
    }
}
