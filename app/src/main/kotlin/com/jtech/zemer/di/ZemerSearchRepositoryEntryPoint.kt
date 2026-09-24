package com.jtech.zemer.di

import android.content.Context
import com.jtech.zemer.search.ZemerSearchRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Resolves the singleton [ZemerSearchRepository] from a plain application [android.content.Context], for
 * the few non-injected constructors that need it — e.g. [com.jtech.zemer.playback.queues.LocalAlbumRadio],
 * built inside leaf composables that have no ViewModel to inject it. Mirrors [LyricsStoreEntryPoint].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZemerSearchRepositoryEntryPoint {
    fun zemerSearchRepository(): ZemerSearchRepository
}

/**
 * The one-liner for the boilerplate above: resolve the singleton [ZemerSearchRepository] from any
 * [Context]. Always goes through the application context, so it is safe to call with an Activity/
 * leaf-composable context. Prefer this over hand-writing the [EntryPointAccessors] call.
 */
fun Context.zemerSearchRepository(): ZemerSearchRepository =
    EntryPointAccessors
        .fromApplication(applicationContext, ZemerSearchRepositoryEntryPoint::class.java)
        .zemerSearchRepository()
