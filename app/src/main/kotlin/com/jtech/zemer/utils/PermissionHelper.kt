package com.jtech.zemer.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Helper class for managing storage permissions required for MediaStore downloads.
 *
 * Android storage permissions vary by API level:
 * - Android 13+ (API 33+): READ_MEDIA_AUDIO + READ_MEDIA_IMAGES (covers audio downloads and
 *   generated cover art)
 * - Android 10-12 (API 29-32): READ_EXTERNAL_STORAGE
 * - Android 9 and below (API ≤28): READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
 * - Android 10+ (API 29+): WRITE_EXTERNAL_STORAGE not needed for MediaStore
 */
object PermissionHelper {

    /**
     * Check if storage permissions are granted for MediaStore WRITE operations.
     *
     * On Android 10+ (API 29+) MediaStore inserts to the Music collection do not require the
     * legacy WRITE_EXTERNAL_STORAGE permission, but older devices still need the legacy pair of
     * read/write permissions. Android 13+ requires granular media permissions for reading back
     * downloaded audio and generated cover art.
     */
    fun hasMediaStoreWritePermission(context: Context): Boolean {
        val permissions = getRequiredWritePermissions()
        if (permissions.isEmpty()) return true

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if storage permissions are granted for MediaStore READ operations
     *
     * @param context Application context
     * @return true if all required permissions are granted for reading
     */
    fun hasStoragePermission(context: Context): Boolean {
        return when {
            // Android 13+ (API 33+) - Requires READ_MEDIA_AUDIO and READ_MEDIA_IMAGES
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasAudio = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val hasImages = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
                hasAudio && hasImages
            }

            // Android 10-12 (API 29-32) - Requires READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            // Android 9 and below (API ≤28) - Requires both READ and WRITE
            else -> {
                val hasRead = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                val hasWrite = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                hasRead && hasWrite
            }
        }
    }

    /**
     * Get the required storage permissions for MediaStore WRITE operations
     *
     * @return Array of permission strings needed for writing
     */
    fun getRequiredWritePermissions(): Array<String> {
        return when {
            // Android 13+ (API 33+) - Request READ_MEDIA_AUDIO + READ_MEDIA_IMAGES for cover art
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }

            // Android 10-12 (API 29-32) - Request READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            // Android 9 and below (API ≤28) - Request READ + WRITE
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    /**
     * Get the required storage permissions for MediaStore READ operations
     *
     * @return Array of permission strings needed for reading
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            // Android 13+ (API 33+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }

            // Android 10-12 (API 29-32)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            // Android 9 and below (API ≤28)
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    /**
     * Create a permission launcher for requesting storage permissions
     *
     * Usage in Activity:
     * ```
     * val permissionLauncher = PermissionHelper.createPermissionLauncher(
     *     activity = this,
     *     onGranted = { /* proceed with download */ },
     *     onDenied = { /* show rationale or error */ }
     * )
     *
     * // Later, when needed:
     * PermissionHelper.requestStoragePermission(this, permissionLauncher)
     * ```
     *
     * @param activity The activity to register the launcher with
     * @param onGranted Callback when permission is granted
     * @param onDenied Callback when permission is denied
     * @return ActivityResultLauncher for permissions
     */
    fun createPermissionLauncher(
        activity: ComponentActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Timber.d("Storage permissions granted")
            onGranted()
        } else {
            Timber.w("Storage permissions denied: $permissions")
            onDenied()
        }
    }

    /**
     * Request storage permissions using the provided launcher
     *
     * @param context Application context (for permission check)
     * @param launcher Permission launcher created with createPermissionLauncher
     */
    fun requestStoragePermission(
        context: Context,
        launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        if (hasStoragePermission(context)) {
            Timber.d("Storage permissions already granted")
            return
        }

        val permissions = getRequiredPermissions()
        Timber.d("Requesting storage permissions: ${permissions.contentToString()}")
        launcher.launch(permissions)
    }

    /**
     * Check if we should show rationale for storage permissions
     *
     * @param activity The activity to check
     * @return true if we should show permission rationale
     */
    fun shouldShowRationale(activity: ComponentActivity): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }

    /**
     * Get a user-friendly description of why storage permission is needed
     *
     * @return Explanation string
     */
    fun getPermissionRationale(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                "This app needs access to your audio files and cover art so downloads save correctly to your device's Music and Pictures folders."
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                "This app needs storage permission to download and save music to your device's Music folder."
            }
            else -> {
                "This app needs storage permissions to download and save music to your device."
            }
        }
    }

    /**
     * Request storage permission directly from an Activity context.
     * Returns true if already granted; false if a permission request was started or not possible.
     */
    fun requestMediaStorePermissionIfNeeded(context: Context): Boolean {
        if (hasMediaStoreWritePermission(context)) return true
        val activity = context as? Activity ?: return false
        ActivityCompat.requestPermissions(activity, getRequiredWritePermissions(), 2001)
        return false
    }
}
