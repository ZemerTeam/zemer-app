package com.jtech.zemer.di

import com.jtech.zemer.search.ZemerSearchRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt access to the Zemer search repository from composables without a ViewModel (menus). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZemerSearchEntryPoint {
    fun zemerSearchRepository(): ZemerSearchRepository
}
