package com.jtech.zemer.di

import com.jtech.zemer.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Resolves the singleton [MusicDatabase] from a plain application [android.content.Context], for
 * non-injected helpers that need it - e.g. the share flow's credential reads/writes in
 * [com.jtech.zemer.ui.menu.ShareUserPlaylistDialog]. Mirrors [ZemerSearchRepositoryEntryPoint].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicDatabaseEntryPoint {
    fun musicDatabase(): MusicDatabase
}
