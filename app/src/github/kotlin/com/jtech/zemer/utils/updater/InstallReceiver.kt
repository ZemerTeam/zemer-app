package com.jtech.zemer.utils.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.jtech.zemer.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Receives PackageInstaller session status for Shizuku-based installs. */
class InstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_INSTALL_STATUS = "com.jtech.zemer.INSTALL_STATUS"

        // Shizuku installs finish asynchronously here, not in AppInstaller.install (which
        // returns RequiresUserAction). Re-publish the real outcome so the install UI can show
        // a failure in-dialog instead of only a toast. Buffered so a tryEmit never drops it.
        private val _events = MutableSharedFlow<InstallResult>(extraBufferCapacity = 4)
        val events: SharedFlow<InstallResult> = _events.asSharedFlow()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // Shizuku's silent install replaces our package and the OS kills this
                // process; we can't reliably relaunch from here (the Shizuku remote process
                // is reaped with us), so we just confirm and let the user reopen.
                _events.tryEmit(InstallResult.Success)
                Toast.makeText(context, R.string.install_success, Toast.LENGTH_SHORT).show()
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: context.getString(R.string.install_failed_generic)
                _events.tryEmit(InstallResult.Error(message))
                Toast.makeText(
                    context,
                    context.getString(R.string.install_failed, message),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
