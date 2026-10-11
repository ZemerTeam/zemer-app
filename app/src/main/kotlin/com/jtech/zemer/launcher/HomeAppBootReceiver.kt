package com.jtech.zemer.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jtech.zemer.utils.reportException

/**
 * Applies the launcher marker at boot, so a preinstalled Zemer on a managed ROM is a home app before it
 * has ever been opened (App.onCreate only runs once something starts the process).
 */
class HomeAppBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { HomeAppAlias.applyManaged(context) }.onFailure { reportException(it) }
    }
}
