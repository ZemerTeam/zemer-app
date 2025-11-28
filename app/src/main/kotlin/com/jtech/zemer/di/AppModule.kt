package com.jtech.zemer.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.jtech.zemer.constants.MaxSongCacheSizeKey
import com.jtech.zemer.db.InternalDatabase
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase {
        // Delay database initialization to avoid blocking on app startup
        // Database will be created on first access instead
        return InternalDatabase.newInstance(context)
    }

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: dagger.Lazy<DatabaseProvider>,
    ): SimpleCache {
        Timber.d("PlayerCache being created")
        val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
        return SimpleCache(
            context.filesDir.resolve("exoplayer"),
            when (cacheSize) {
                -1 -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
            },
            databaseProvider.get(),
        )
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: dagger.Lazy<DatabaseProvider>,
    ): SimpleCache {
        Timber.d("DownloadCache being created")
        return SimpleCache(
            context.filesDir.resolve("download"),
            NoOpCacheEvictor(),
            databaseProvider.get()
        )
    }
}
