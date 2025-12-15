package com.jtech.zemer.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDataStore