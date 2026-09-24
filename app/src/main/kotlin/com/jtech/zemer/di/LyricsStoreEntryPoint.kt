package com.jtech.zemer.di

import com.jtech.zemer.lyrics.LyricsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Reaches the singleton [LyricsStore] from a leaf composable that has no ViewModel to inject it (the lyrics screen). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LyricsStoreEntryPoint {
    fun lyricsStore(): LyricsStore
}
