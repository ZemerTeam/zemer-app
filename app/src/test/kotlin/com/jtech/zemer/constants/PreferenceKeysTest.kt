package com.jtech.zemer.constants

import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

class PreferenceKeysTest {

    /**
     * Every top-level key in PreferenceKeys.kt must use a UNIQUE storage name. DataStore identifies
     * a preference by its string, not the Kotlin symbol — a copy-pasted string silently makes two
     * settings share one stored value (MixSortDescendingKey once reused "albumSortDescending", so
     * toggling sort direction on the Mix tab flipped the Albums tab too).
     */
    @Test
    fun `no two preference keys share a storage name`() {
        val names = Class.forName("com.jtech.zemer.constants.PreferenceKeysKt")
            .declaredMethods
            .filter { Modifier.isStatic(it.modifiers) && it.parameterCount == 0 }
            .filter { Preferences.Key::class.java.isAssignableFrom(it.returnType) }
            .map { (it.invoke(null) as Preferences.Key<*>).name }
        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals("duplicate DataStore storage names: $duplicates", emptyMap<String, Int>(), duplicates)
    }
}
