package com.jtech.zemer.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.StreamSourceAndroidVRKey
import com.jtech.zemer.constants.StreamSourceMWEBKey
import com.jtech.zemer.constants.StreamSourcePrefsMigratedKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceVisionOSKey
import com.jtech.zemer.constants.StreamSourceWebCreatorKey
import com.jtech.zemer.constants.StreamSourceWebRemixKey
import kotlinx.coroutines.flow.first

/**
 * The per-FAMILY stream-source toggle scheme. A family (config `family` id, e.g. "VISIONOS")
 * gets one dynamic boolean key; ABSENT = enabled, so a remotely-added family is on for everyone
 * until they toggle it. The pure pieces (key naming, disabled-set derivation, the legacy-key
 * mapping) are JVM-tested in StreamSourcePrefsTest.
 */
object StreamSourcePrefs {
    private const val FAMILY_KEY_PREFIX = "streamSourceFamily_"

    fun familyKey(familyId: String): Preferences.Key<Boolean> =
        booleanPreferencesKey(FAMILY_KEY_PREFIX + familyId)

    /**
     * The families the user has switched off — every per-family key explicitly false. Derived by
     * PREFIX SCAN (not a fixed list) so families added remotely need no app-side registry.
     */
    fun disabledFamilies(prefs: Preferences): Set<String> =
        prefs.asMap().entries
            .filter { (key, value) -> key.name.startsWith(FAMILY_KEY_PREFIX) && value == false }
            .map { (key, _) -> key.name.removePrefix(FAMILY_KEY_PREFIX) }
            .toSet()

    /** Legacy single-client toggle key → the family id it governed. */
    internal val LEGACY_KEY_TO_FAMILY = mapOf(
        StreamSourceWebRemixKey to "WEB_REMIX",
        StreamSourceTVHTML5Key to "TVHTML5",
        StreamSourceAndroidVRKey to "ANDROID_VR",
        StreamSourceWebCreatorKey to "WEB_CREATOR",
        StreamSourceVisionOSKey to "VISIONOS",
        StreamSourceMWEBKey to "MWEB",
    )

    /**
     * The per-family writes a legacy snapshot needs: only explicit `false` values are copied
     * (default-true needs no write). Pure — the suspend wrapper below applies it.
     */
    internal fun migrationWrites(prefs: Preferences): Map<String, Boolean> =
        LEGACY_KEY_TO_FAMILY.entries
            .filter { (legacyKey, _) -> prefs[legacyKey] == false }
            .associate { (_, family) -> family to false }

    /**
     * One-time copy of the legacy toggles onto the per-family keys, stamped by
     * [StreamSourcePrefsMigratedKey]. The legacy keys are left in place but never written again —
     * they are the migration SOURCE, not a mirror (see PreferenceKeys).
     */
    suspend fun migrateLegacyToggles(dataStore: DataStore<Preferences>) {
        val prefs = dataStore.data.first()
        if (prefs[StreamSourcePrefsMigratedKey] == true) return
        val writes = migrationWrites(prefs)
        dataStore.edit { mutable ->
            for ((family, value) in writes) mutable[familyKey(family)] = value
            mutable[StreamSourcePrefsMigratedKey] = true
        }
    }
}
