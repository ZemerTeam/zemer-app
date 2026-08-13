package com.jtech.zemer.di

import com.jtech.zemer.search.ShareCredentialStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Resolves the singleton [ShareCredentialStore] from a plain application
 * [android.content.Context], for the share flow's free functions in
 * [com.jtech.zemer.ui.menu.ShareUserPlaylistDialog]'s file. Mirrors [ZemerSearchRepositoryEntryPoint].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareCredentialStoreEntryPoint {
    fun shareCredentialStore(): ShareCredentialStore
}
