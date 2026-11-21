package com.example.myappstore.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/** Read-only helpers for inspecting installed packages. */
object PackageUtils {

    data class InstalledApp(
        val versionName: String,
        val versionCode: Long,
        val label: String,
        val icon: Drawable?
    )

    fun getInstalledApp(context: Context, packageName: String): InstalledApp? {
        val packageManager = context.packageManager
        val info = getPackageInfo(packageManager, packageName) ?: return null

        val versionName = info.versionName?.takeIf { it.isNotBlank() } ?: versionCodeOf(info).toString()
        val applicationInfo = info.applicationInfo
        val label = applicationInfo
            ?.let { runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull() }
            ?: packageName
        val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()

        return InstalledApp(
            versionName = versionName,
            versionCode = versionCodeOf(info),
            label = label,
            icon = icon
        )
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        getPackageInfo(context.packageManager, packageName) != null

    /** Null when the app is not installed or has no launcher activity. */
    fun getLaunchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    private fun getPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
}
