package com.jtech.zemer.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.StreamSabrPrefsMigratedKey
import com.jtech.zemer.constants.StreamSabrTVHTML5Key
import com.jtech.zemer.constants.StreamSabrVisionOSKey
import com.jtech.zemer.constants.StreamSabrWebRemixKey
import com.jtech.zemer.constants.StreamSourcePrefsMigratedKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceVisionOSKey
import com.jtech.zemer.constants.StreamSourceWebCreatorKey
import com.jtech.zemer.constants.StreamSourceWebRemixKey
import kotlinx.coroutines.flow.first

/**
 * The per-FAMILY stream-source toggle scheme, for BOTH transports. A family (config `family` id,
 * e.g. "VISIONOS") gets one dynamic boolean key per transport; ABSENT = enabled, so a remotely-added
 * family is on for everyone until they toggle it. DIRECT keys govern the resolution chain, SABR
 * keys the SABR roster (the table's sabr-capable entries). The pure pieces (key naming,
 * disabled-set derivation, the legacy-key mappings) are JVM-tested in StreamSourcePrefsTest.
 */
object StreamSourcePrefs {
    private const val FAMILY_KEY_PREFIX = "streamSourceFamily_"
    private const val SABR_FAMILY_KEY_PREFIX = "streamSabrFamily_"

    fun familyKey(familyId: String): Preferences.Key<Boolean> =
        booleanPreferencesKey(FAMILY_KEY_PREFIX + familyId)

    fun sabrFamilyKey(familyId: String): Preferences.Key<Boolean> =
        booleanPreferencesKey(SABR_FAMILY_KEY_PREFIX + familyId)

    /**
     * The families the user has switched off for DIRECT — every per-family key explicitly false.
     * Derived by PREFIX SCAN (not a fixed list) so families added remotely need no app-side registry.
     */
    fun disabledFamilies(prefs: Preferences): Set<String> = disabledWithPrefix(prefs, FAMILY_KEY_PREFIX)

    /** The families the user has switched off for SABR (same scheme, its own key prefix). */
    fun disabledSabrFamilies(prefs: Preferences): Set<String> = disabledWithPrefix(prefs, SABR_FAMILY_KEY_PREFIX)

    /**
     * The family ids the SABR resolvers may use: the table's SABR roster minus the off switches.
     * Empty when the roster is empty or every roster family is off (SABR mode then has no client).
     */
    fun enabledSabrFamilies(prefs: Preferences, table: StreamClientTable.Table): Set<String> =
        table.sabrRoster.map { it.family }.toSet() - disabledSabrFamilies(prefs)

    private fun disabledWithPrefix(prefs: Preferences, prefix: String): Set<String> =
        prefs.asMap().entries
            .filter { (key, value) -> key.name.startsWith(prefix) && value == false }
            .map { (key, _) -> key.name.removePrefix(prefix) }
            .toSet()

    /**
     * Legacy single-client toggle key → the family id it governed. Only the families that still
     * exist: the retired ANDROID_VR / MWEB toggles have no family to migrate onto.
     */
    internal val LEGACY_KEY_TO_FAMILY = mapOf(
        StreamSourceWebRemixKey to "WEB_REMIX",
        StreamSourceTVHTML5Key to "TVHTML5",
        StreamSourceWebCreatorKey to "WEB_CREATOR",
        StreamSourceVisionOSKey to "VISIONOS",
    )

    /** Legacy per-client SABR toggle key → the family id it governed. */
    internal val LEGACY_SABR_KEY_TO_FAMILY = mapOf(
        StreamSabrWebRemixKey to "WEB_REMIX",
        StreamSabrVisionOSKey to "VISIONOS",
        StreamSabrTVHTML5Key to "TVHTML5",
    )

    /**
     * The per-family writes a legacy snapshot needs: only explicit `false` values are copied
     * (default-true needs no write). Pure — the suspend wrapper below applies it.
     */
    internal fun migrationWrites(prefs: Preferences): Map<String, Boolean> =
        writesFor(prefs, LEGACY_KEY_TO_FAMILY)

    internal fun sabrMigrationWrites(prefs: Preferences): Map<String, Boolean> =
        writesFor(prefs, LEGACY_SABR_KEY_TO_FAMILY)

    private fun writesFor(prefs: Preferences, legacy: Map<Preferences.Key<Boolean>, String>): Map<String, Boolean> =
        legacy.entries
            .filter { (legacyKey, _) -> prefs[legacyKey] == false }
            .associate { (_, family) -> family to false }

    /**
     * One-time copy of the legacy toggles onto the per-family keys, stamped per transport by
     * [StreamSourcePrefsMigratedKey] / [StreamSabrPrefsMigratedKey] (separate stamps: the SABR
     * scheme landed later, so an install already past the DIRECT migration still gets its SABR
     * choices carried over). The legacy keys are left in place but never written again — they
     * are the migration SOURCE, not a mirror (see PreferenceKeys).
     */
    suspend fun migrateLegacyToggles(dataStore: DataStore<Preferences>) {
        val prefs = dataStore.data.first()
        val migrateDirect = prefs[StreamSourcePrefsMigratedKey] != true
        val migrateSabr = prefs[StreamSabrPrefsMigratedKey] != true
        if (!migrateDirect && !migrateSabr) return
        val directWrites = migrationWrites(prefs)
        val sabrWrites = sabrMigrationWrites(prefs)
        dataStore.edit { mutable ->
            if (migrateDirect) {
                for ((family, value) in directWrites) mutable[familyKey(family)] = value
                mutable[StreamSourcePrefsMigratedKey] = true
            }
            if (migrateSabr) {
                for ((family, value) in sabrWrites) mutable[sabrFamilyKey(family)] = value
                mutable[StreamSabrPrefsMigratedKey] = true
            }
        }
    }
}
