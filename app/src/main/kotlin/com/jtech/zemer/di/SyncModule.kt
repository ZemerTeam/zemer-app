package com.jtech.zemer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jtech.zemer.utils.dataStore
import com.google.firebase.firestore.FirebaseFirestore
import com.jtech.zemer.auth.UserAuthManager
import com.jtech.zemer.sync.ContentFilterSyncService
import com.jtech.zemer.sync.UserPreferencesRepository
import com.jtech.zemer.utils.DeviceIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_preferences"
)

/**
 * Hilt module for providing sync-related dependencies.
 * Includes Firebase services, authentication, and preference sync components.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    /**
     * Provides the DataStore instance for sync-related preferences
     */
    @Provides
    @Singleton
    @SyncDataStore
    fun provideSyncDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.syncDataStore
    }

    /**
     * Provides FirebaseFirestore instance for user preferences storage
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    /**
     * Provides UserAuthManager for Firebase authentication operations
     */
    @Provides
    @Singleton
    fun provideUserAuthManager(
        @ApplicationContext context: Context
    ): UserAuthManager {
        return UserAuthManager(context)
    }

    /**
     * Provides DeviceIdGenerator for unique device identification
     */
    @Provides
    @Singleton
    fun provideDeviceIdGenerator(
        @ApplicationContext context: Context,
        @SyncDataStore dataStore: DataStore<Preferences>
    ): DeviceIdGenerator {
        return DeviceIdGenerator(context, dataStore)
    }

    /**
     * Provides the main DataStore instance for content filter preferences
     */
    @Provides
    @Singleton
    @MainDataStore
    fun provideMainDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    /**
     * Provides UserPreferencesRepository for managing preference sync
     */
    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        @SyncDataStore syncDataStore: DataStore<Preferences>,
        @MainDataStore mainDataStore: DataStore<Preferences>,
        authManager: UserAuthManager,
        deviceIdGenerator: DeviceIdGenerator
    ): UserPreferencesRepository {
        return UserPreferencesRepository(
            context = context,
            firestore = firestore,
            syncDataStore = syncDataStore,
            mainDataStore = mainDataStore,
            authManager = authManager,
            deviceIdGenerator = deviceIdGenerator
        )
    }

    /**
     * Provides ContentFilterSyncService for orchestrating sync operations
     */
    @Provides
    @Singleton
    fun provideContentFilterSyncService(
        userPreferencesRepository: UserPreferencesRepository,
        authManager: UserAuthManager
    ): ContentFilterSyncService {
        return ContentFilterSyncService(
            userPreferencesRepository = userPreferencesRepository,
            authManager = authManager
        )
    }
}