package com.jtech.zemer.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.jtech.zemer.MainActivity
import timber.log.Timber
import java.io.File

/**
 * The opt-in home-screen role: the manifest's disabled `.HomeLauncher` activity-alias (HOME category) is
 * enabled/disabled at runtime. The component state IS the setting, so there is no preference to drift.
 */
object HomeAppAlias {
    /** A ROM that ships either file makes Zemer its launcher: the alias is forced on and the toggle is locked. */
    private val MANAGED_MARKERS = listOf(File("/system/etc/.zlauncher"), File("/data/system/.zlauncher"))

    fun isManaged(markers: List<File> = MANAGED_MARKERS): Boolean = markers.any { it.exists() }

    /** Called at app start: re-enables the alias on a managed ROM (also undoes a manual `pm disable`). */
    fun applyManaged(context: Context) {
        if (isManaged() && !isEnabled(context)) setEnabled(context, true)
    }

    private fun component(context: Context) =
        ComponentName(context, MainActivity::class.java.name.substringBeforeLast('.') + ".HomeLauncher")

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Opens the system default-home picker so the user can choose Zemer; some ROMs don't have it. */
    fun openHomePicker(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.w(it, "No home settings screen on this device") }
    }
}
