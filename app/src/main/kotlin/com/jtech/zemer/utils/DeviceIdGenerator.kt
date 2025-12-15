package com.jtech.zemer.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import com.jtech.zemer.di.SyncDataStore
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and manages unique device identifiers for app installations.
 * Uses Android ID + installation UUID to create a persistent device ID
 * that survives app reinstalls on the same device.
 */
@Singleton
class DeviceIdGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    @SyncDataStore private val dataStore: DataStore<Preferences>
) {
    private val deviceIdKey = stringPreferencesKey("device_id")

    /**
     * Get or generate a unique device identifier
     *
     * The device ID is generated using:
     * 1. Android ID (device-specific, survives factory resets on some devices)
     * 2. Installation UUID (stored in DataStore, survives app updates)
     * 3. App signature hash (prevents ID reuse after app reinstall from different source)
     */
    suspend fun getDeviceId(): String {
        val storedDeviceId = dataStore.data.map { preferences ->
            preferences[deviceIdKey]
        }.first()

        return if (storedDeviceId != null) {
            Log.d("ZemerDeviceId", "Using stored device ID: $storedDeviceId")
            storedDeviceId
        } else {
            Log.d("ZemerDeviceId", "No stored device ID, generating new one")
            val newDeviceId = generateAndStoreDeviceId()
            Log.d("ZemerDeviceId", "Generated new device ID: $newDeviceId")
            newDeviceId
        }
    }

    /**
     * Generate a new device ID and store it in DataStore
     */
    private suspend fun generateAndStoreDeviceId(): String {
        val androidId = getAndroidId()
        val appSignatureHash = getAppSignatureHash()

        println("DEBUG: Android ID: $androidId")
        println("DEBUG: App signature hash: $appSignatureHash")

        // Use Android ID + app signature for device identification
        // Android ID survives app data clearing and app reinstalls
        val deviceId = "${androidId}_${appSignatureHash}"

        println("DEBUG: Storing device ID: $deviceId")

        // Store the generated device ID
        dataStore.edit { preferences ->
            preferences[deviceIdKey] = deviceId
        }

        return deviceId
    }

    /**
     * Get the Android ID for the device
     */
    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_android_id"
        } catch (e: Exception) {
            "unknown_android_id"
        }
    }

    /**
     * Get a hash of the app's signature for additional uniqueness
     */
    private fun getAppSignatureHash(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            // Create a simple hash from the first signature
            signatures?.firstOrNull()?.hashCode()?.toString() ?: "no_signature"
        } catch (e: Exception) {
            "signature_error"
        }
    }

    /**
     * Get a user-friendly device name for display purposes
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        return if (model.startsWith(manufacturer)) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        }
    }

    /**
     * Get device information for metadata
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceName = getDeviceName(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            appVersion = getAppVersion()
        )
    }

    /**
     * Get the current app version
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Force regenerate the device ID (useful for testing or migration)
     */
    suspend fun regenerateDeviceId(): String {
        dataStore.edit { preferences ->
            preferences.remove(deviceIdKey)
        }
        return generateAndStoreDeviceId()
    }

    /**
     * Check if the device ID has been generated and stored
     */
    suspend fun hasDeviceId(): Boolean {
        return dataStore.data.map { preferences ->
            preferences[deviceIdKey] != null
        }.first()
    }
}

/**
 * Data class representing device information
 */
data class DeviceInfo(
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val appVersion: String
)