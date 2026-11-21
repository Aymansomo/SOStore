package com.example.myappstore.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import java.io.File

/**
 * Receives the result of a [PackageInstaller] session and forwards it to [InstallEventBus].
 *
 * The PendingIntent created in [ApkInstallerManager.installApk] points here. The system
 * strips the extras for the pending-user-action step, so the package name and APK path are
 * re-added by the system only for the final callback; both are treated as optional.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (packageName.isBlank()) return

        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java
                )
                if (confirmation == null) {
                    deleteCachedApk(apkPath)
                    InstallEventBus.emit(
                        InstallOutcome(
                            packageName = packageName,
                            status = InstallStatus.FAILURE,
                            message = "The system installer did not provide a confirmation dialog."
                        )
                    )
                    return
                }

                InstallEventBus.emit(
                    InstallOutcome(
                        packageName = packageName,
                        status = InstallStatus.PENDING,
                        message = "Confirm the installation in the system dialog."
                    )
                )

                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmation) }
                    .onFailure { error ->
                        Log.w(TAG, "Could not launch install confirmation", error)
                        deleteCachedApk(apkPath)
                        InstallEventBus.emit(
                            InstallOutcome(
                                packageName = packageName,
                                status = InstallStatus.FAILURE,
                                message = "Couldn't open the system installer: " +
                                    (error.message ?: "unknown error") + "."
                            )
                        )
                    }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                deleteCachedApk(apkPath)
                InstallEventBus.emit(
                    InstallOutcome(packageName = packageName, status = InstallStatus.SUCCESS)
                )
            }

            else -> {
                deleteCachedApk(apkPath)
                InstallEventBus.emit(
                    InstallOutcome(
                        packageName = packageName,
                        status = InstallStatus.FAILURE,
                        message = describeFailure(status, statusMessage)
                    )
                )
            }
        }
    }

    private fun describeFailure(status: Int, rawMessage: String?): String {
        val raw = rawMessage.orEmpty()
        return when {
            raw.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                raw.contains("signatures do not match", ignoreCase = true) ||
                raw.contains("INSTALL_FAILED_DUPLICATE_PERMISSION", ignoreCase = true) ->
                "Signature mismatch: the installed app was signed with a different key. " +
                    "Uninstall the existing app, then install this release again."

            raw.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) ->
                "The installed version is newer than this APK, so the downgrade was blocked."

            raw.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true) ||
                status == PackageInstaller.STATUS_FAILURE_STORAGE ->
                "Not enough free storage to install the app."

            raw.contains("INSTALL_FAILED_NO_MATCHING_ABIS", ignoreCase = true) ->
                "This APK does not contain native libraries for this device's CPU."

            status == PackageInstaller.STATUS_FAILURE_BLOCKED ->
                "The install was blocked by the system or by Play Protect."

            status == PackageInstaller.STATUS_FAILURE_ABORTED ->
                "The installation was cancelled."

            status == PackageInstaller.STATUS_FAILURE_INVALID ->
                "The APK is invalid or corrupt. Try refreshing and downloading again."

            status == PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "The APK conflicts with the version already installed on this device."

            status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "This APK is incompatible with the current Android version."

            raw.isNotBlank() -> raw
            else -> "The installation failed (status $status)."
        }
    }

    private fun deleteCachedApk(apkPath: String?) {
        if (apkPath.isNullOrBlank()) return
        runCatching { File(apkPath).delete() }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "com.example.myappstore.extra.PACKAGE_NAME"
        const val EXTRA_APK_PATH = "com.example.myappstore.extra.APK_PATH"
        private const val TAG = "InstallResultReceiver"
    }
}
