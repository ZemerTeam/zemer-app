package com.jtech.zemer.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import timber.log.Timber
import java.io.File

/**
 * The opt-in home-screen role: the manifest's disabled `.HomeLauncher` activity-alias (HOME category) is
 * enabled/disabled at runtime. The component state IS the setting, so there is no preference to drift.
 */
object HomeAppAlias {
    /** Must match the manifest's `.HomeLauncher` alias (resolved against the `com.jtech.zemer` namespace). */
    private const val ALIAS_CLASS = "com.jtech.zemer.HomeLauncher"

    /**
     * Any of these files makes Zemer the launcher: the alias is forced on and the toggle is locked.
     * Only existence is checked, never the contents.
     */
    val MANAGED_MARKERS = listOf(
        File("/system/etc/.zlauncher"),
        File("/data/system/.zlauncher"),
        File("/data/local/tmp/.zlauncher"),
    )

    /** Remembers that a marker was seen; lives in noBackupFilesDir so only "clear data" removes it. */
    private fun latchFile(context: Context) = File(context.noBackupFilesDir, ".zlauncher_managed")

    fun isManaged(context: Context): Boolean = isManaged(MANAGED_MARKERS, latchFile(context))

    /**
     * Managed once any marker has been seen, and it STAYS managed: deleting the marker alone does not
     * unlock the toggle. Only removing the marker AND clearing the app's data (which drops [latch]) does.
     */
    fun isManaged(markers: List<File>, latch: File): Boolean {
        if (latch.exists()) return true
        if (markers.none { it.exists() }) return false
        runCatching { latch.createNewFile() }.onFailure { Timber.w(it, "Could not persist the launcher lock") }
        return true
    }

    /**
     * At app start and boot: on a managed device re-enables the alias (also undoes a manual `pm disable`).
     * The boot receiver ships ENABLED so a ROM-preinstalled Zemer becomes home on its first boot, and is
     * switched off here on unmanaged devices, so a normal install never starts Zemer at boot.
     */
    fun applyManaged(context: Context) {
        val managed = isManaged(context)
        if (managed && !isEnabled(context)) setEnabled(context, true)
        val receiver = ComponentName(context, HomeAppBootReceiver::class.java)
        if (isComponentEnabled(context, receiver, manifestDefault = true) != managed) setComponent(context, receiver, managed)
    }

    private fun component(context: Context) = ComponentName(context, ALIAS_CLASS)

    fun isEnabled(context: Context): Boolean = isComponentEnabled(context, component(context), manifestDefault = false)

    fun setEnabled(context: Context, enabled: Boolean) = setComponent(context, component(context), enabled)

    private fun isComponentEnabled(context: Context, component: ComponentName, manifestDefault: Boolean): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
            else -> false
        }

    private fun setComponent(context: Context, component: ComponentName, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** True when the system's current default home resolves to Zemer. */
    fun isDefaultHome(context: Context): Boolean =
        context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName

    /** Opens the system default-home picker so the user can choose Zemer; some ROMs don't have it. */
    fun openHomePicker(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.w(it, "No home settings screen on this device") }
    }
}
