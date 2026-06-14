package com.jtech.zemer.extensions

import android.content.Context
import com.jtech.zemer.constants.DataSyncIdKey
import com.jtech.zemer.utils.dataStore
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether a **personal** Google account is signed in.
 *
 * The "anonymous" login signs into a shared, pooled account whose cookie DOES carry `SAPISID`, so
 * the cookie-based [isUserLoggedIn]/[isUserLoggedInFlow] return `true` for anonymous sessions and
 * must NOT be used to authorize remote *account* reads or writes — doing so leaks the pooled
 * account's library/likes/subscriptions across every anonymous user. Only a personal login sets a
 * `dataSyncId` (the anonymous flow explicitly clears it; see `App.kt` / `LoginGateScreen`), so that
 * is the correct discriminator for "may this session touch the remote account".
 *
 * This reads the in-memory [YouTube.dataSyncId] (kept in sync with DataStore by `App.kt`) so it is
 * usable from context-free call sites such as entity data classes. For Composables that must
 * recompose on login changes, use [isPersonalAccountFlow].
 */
val isPersonalAccountSignedIn: Boolean
    get() = !YouTube.dataSyncId.isNullOrEmpty()

/** Reactive equivalent of [isPersonalAccountSignedIn] for Compose UI. */
fun Context.isPersonalAccountFlow(): Flow<Boolean> =
    dataStore.data.map { (it[DataSyncIdKey] ?: "").isNotBlank() }
