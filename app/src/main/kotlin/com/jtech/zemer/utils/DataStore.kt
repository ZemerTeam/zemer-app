package com.jtech.zemer.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.jtech.zemer.extensions.toEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Synchronously reads a preference value from DataStore.
 *
 * WARNING: This uses runBlocking which can block the calling thread. Use this ONLY in:
 * - Service initialization (onCreate, onStartCommand)
 * - One-time app initialization
 * - Background coroutine contexts
 *
 * AVOID using in:
 * - UI/Main thread without careful consideration
 * - Composable functions (use rememberPreference instead)
 * - Hot paths or frequently called code
 *
 * Consider using Flow-based access (dataStore.data.map { it[key] }) for reactive updates.
 */
operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    runBlocking(Dispatchers.IO) {
        data.first()[key]
    }

/**
 * Synchronously reads a preference value from DataStore with a default value.
 *
 * WARNING: This uses runBlocking which can block the calling thread. Use this ONLY in:
 * - Service initialization (onCreate, onStartCommand)
 * - One-time app initialization
 * - Background coroutine contexts
 *
 * AVOID using in:
 * - UI/Main thread without careful consideration
 * - Composable functions (use rememberPreference instead)
 * - Hot paths or frequently called code
 *
 * Consider using Flow-based access (dataStore.data.map { it[key] ?: defaultValue }) for reactive updates.
 */
fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    runBlocking(Dispatchers.IO) {
        data.first()[key] ?: defaultValue
    }

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

/**
 * Non-blocking Flow-based access to enum preferences.
 * Use this instead of enumPreference in hot paths or initialization code.
 */
inline fun <reified T : Enum<T>> enumPreferenceFlow(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
): Flow<T> =
    context.dataStore.data
        .map { it[key].toEnum(defaultValue = defaultValue) }
        .distinctUntilChanged()

/**
 * Non-blocking Flow-based access to preferences.
 * Use this instead of preference in hot paths or initialization code.
 */
fun <T> preferenceFlow(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
): Flow<T> =
    context.dataStore.data
        .map { it[key] ?: defaultValue }
        .distinctUntilChanged()

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
