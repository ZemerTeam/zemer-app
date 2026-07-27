package com.jtech.zemer.di

import com.jtech.zemer.search.ZemerSearchRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Resolves the singleton [ZemerSearchRepository] from a plain application [android.content.Context], for
 * the few non-injected constructors that need it — e.g. [com.jtech.zemer.playback.queues.LocalAlbumRadio],
 * built inside leaf composables that have no ViewModel to inject it. Mirrors [LyricsHelperEntryPoint].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZemerSearchRepositoryEntryPoint {
    fun zemerSearchRepository(): ZemerSearchRepository
}
