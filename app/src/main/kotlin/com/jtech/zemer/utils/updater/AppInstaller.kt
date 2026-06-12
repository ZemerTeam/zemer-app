/**
 * APK install methods for self-update, adapted from APK-MultiUpdate
 * (https://github.com/alltechdev/APK-MultiUpdate, GPL-3.0), itself based on
 * Aurora Store's installer implementations.
 */

package com.jtech.zemer.utils.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstallerHidden
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jtech.zemer.R
import com.jtech.zemer.utils.reportException
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import com.topjohnwu.superuser.Shell
import dev.rikka.tools.refine.Refine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File

sealed class InstallResult {
    /** Install finished silently (root / Shizuku). */
    data object Success : InstallResult()

    /** The system installer UI was launched; the user finishes the install there. */
    data object RequiresUserAction : InstallResult()

    data class Error(val message: String) : InstallResult()
}

object AppInstaller {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    @Volatile
    private var hiddenApiBypassApplied = false

    /**
     * Lift the hidden-API denylist so the Shizuku installer can reach the hidden
     * PackageInstaller constructors (Android 9+). Applied lazily on first Shizuku install
     * instead of at app startup, so users who never use Shizuku don't pay the cost.
     */
    private fun ensureHiddenApiBypass() {
        if (hiddenApiBypassApplied || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching { HiddenApiBypass.addHiddenApiExemptions("I", "L") }
            .onFailure { Timber.w(it, "Hidden API bypass unavailable; Shizuku install may fail") }
        hiddenApiBypassApplied = true
    }

    private fun IBinder.wrap() = ShizukuBinderWrapper(this)

    private fun IInterface.asShizukuBinder() = this.asBinder().wrap()

    /** Extract the session id from `pm install-create` output ("Success: created install session [123]"). */
    fun parseSessionId(output: List<String>): Int? =
        output.firstOrNull()?.let { Regex("(\\d+)").find(it)?.value?.toIntOrNull() }

    /**
     * Whether the app may launch the system package installer (Android 8+
     * "install unknown apps" permission). Always true below O.
     */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Settings intent for granting the "install unknown apps" permission. */
    fun getInstallPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /** Note: opens the root shell, which triggers the Magisk/SuperSU grant prompt. */
    fun hasRootAccess(): Boolean = Shell.getShell().isRoot

    fun hasShizukuOrSui(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun hasShizukuPermission(): Boolean =
        try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

    fun isShizukuAlive(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

    suspend fun install(
        context: Context,
        apkFile: File,
        installerType: InstallerType,
    ): InstallResult = withContext(Dispatchers.IO) {
        when (installerType) {
            InstallerType.NATIVE -> installNative(context, apkFile)
            InstallerType.ROOT -> installRoot(context, apkFile)
            InstallerType.SHIZUKU -> installShizuku(context, apkFile)
        }
    }

    private fun installNative(context: Context, apkFile: File): InstallResult =
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }
            context.startActivity(installIntent)
            InstallResult.RequiresUserAction
        } catch (e: Exception) {
            reportException(e, "Native install")
            InstallResult.Error(e.message ?: context.getString(R.string.installer_launch_failed))
        }

    private fun installRoot(context: Context, apkFile: File): InstallResult {
        if (!Shell.getShell().isRoot) {
            return InstallResult.Error(context.getString(R.string.installer_root_unavailable))
        }

        return try {
            val createResult = Shell.cmd(
                "pm install-create -i ${context.packageName} --user 0 -r -S ${apkFile.length()}",
            ).exec()
            if (!createResult.isSuccess) {
                return InstallResult.Error(createResult.errorOr(context))
            }

            val sessionId = parseSessionId(createResult.out)
                ?: return InstallResult.Error(context.getString(R.string.installer_session_id_failed))

            // Pass the APK path directly — pm reads the file itself, avoiding a full copy of the
            // APK through a shell pipe. The split name is just a session-internal label, so use a
            // fixed safe string rather than interpolating the file name into the shell command.
            val writeResult = Shell.cmd(
                "pm install-write -S ${apkFile.length()} $sessionId base.apk \"${apkFile.absolutePath}\"",
            ).exec()
            if (!writeResult.isSuccess) {
                return InstallResult.Error(writeResult.errorOr(context))
            }

            // Chain the relaunch onto the commit in one root-shell command: install-commit
            // replaces our package and the OS kills this process, but the root shell is a
            // separate process and runs the trailing `am start` (which, as root, is exempt
            // from background-activity-launch limits) — so the app comes back on its own.
            val relaunch = AppRestarter.relaunchCommand(context)
            val commitCommand = "pm install-commit $sessionId" +
                if (relaunch != null) " && sleep 1 && $relaunch" else ""
            val commitResult = Shell.cmd(commitCommand).exec()
            if (commitResult.isSuccess) {
                InstallResult.Success
            } else {
                InstallResult.Error(commitResult.errorOr(context))
            }
        } catch (e: Exception) {
            reportException(e, "Root install")
            InstallResult.Error(e.message ?: context.getString(R.string.install_failed_generic))
        }
    }

    private fun Shell.Result.errorOr(context: Context): String =
        err.joinToString("\n").ifEmpty { context.getString(R.string.install_failed_generic) }

    /** Shizuku install through the hidden PackageInstaller APIs (rikka refine). */
    private fun installShizuku(context: Context, apkFile: File): InstallResult {
        if (!isShizukuAlive()) {
            return InstallResult.Error(context.getString(R.string.shizuku_not_running))
        }
        if (!hasShizukuPermission()) {
            return InstallResult.Error(context.getString(R.string.shizuku_permission_required))
        }
        ensureHiddenApiBypass()

        return try {
            val iPackageManager = IPackageManager.Stub.asInterface(
                SystemServiceHelper.getSystemService("package").wrap(),
            )
            val iPackageInstaller = IPackageInstaller.Stub.asInterface(
                iPackageManager.packageInstaller.asShizukuBinder(),
            )
            val packageInstaller = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Refine.unsafeCast<PackageInstaller>(
                    PackageInstallerHidden(iPackageInstaller, context.packageName, null, 0),
                )
            } else {
                Refine.unsafeCast<PackageInstaller>(
                    PackageInstallerHidden(iPackageInstaller, context.packageName, 0),
                )
            }

            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val paramsHidden = Refine.unsafeCast<PackageInstallerHidden.SessionParamsHidden>(params)
            paramsHidden.installFlags = paramsHidden.installFlags or PackageManagerHidden.INSTALL_REPLACE_EXISTING

            val sessionId = packageInstaller.createSession(params)
            val iSession = IPackageInstallerSession.Stub.asInterface(
                iPackageInstaller.openSession(sessionId).asShizukuBinder(),
            )
            val session = Refine.unsafeCast<PackageInstaller.Session>(
                PackageInstallerHidden.SessionHidden(iSession),
            )

            apkFile.inputStream().use { input ->
                session.openWrite("zemer_update_${System.currentTimeMillis()}", 0, -1).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val callbackIntent = Intent(context, InstallReceiver::class.java).apply {
                action = InstallReceiver.ACTION_INSTALL_STATUS
                setPackage(context.packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            InstallResult.RequiresUserAction
        } catch (e: NoSuchMethodError) {
            // The hidden constructor signatures changed (Android 16+)
            reportException(RuntimeException(e), "Shizuku install: hidden API mismatch")
            InstallResult.Error(context.getString(R.string.shizuku_not_supported_version))
        } catch (e: Exception) {
            reportException(e, "Shizuku install")
            InstallResult.Error(e.message ?: context.getString(R.string.install_failed_generic))
        }
    }
}
