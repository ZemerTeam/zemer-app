package com.jtech.zemer.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jtech.zemer.utils.reportException

/**
 * Applies the launcher marker at boot, so a ROM-preinstalled Zemer is a home app on its first boot before it
 * has ever been opened (the platform's FallbackHome keeps polling for a real home and picks it up).
 * System apps are never in the "stopped" state, so they get BOOT_COMPLETED; a user-installed, never-opened
 * app does not (it becomes home on first open instead). [HomeAppAlias.applyManaged] disables this receiver
 * on unmanaged devices, so normal installs are not started at boot.
 */
class HomeAppBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { HomeAppAlias.applyManaged(context) }.onFailure { reportException(it) }
    }
}
