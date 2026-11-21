package com.example.myappstore.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads APKs into the app cache and installs/updates them through a
 * [PackageInstaller] session, so no file manager or storage permission is involved.
 */
class ApkInstallerManager(private val context: Context) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Whether the user has allowed this store to install unknown apps. */
    fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants "Allow from this source" for this store. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * Downloads [url] to `cacheDir/apks/<packageName>.apk`, streaming progress.
     *
     * @param onProgress called with (downloadedBytes, totalBytes, percent). [percent] is null
     *   when the server does not report a content length.
     */
    suspend fun downloadApk(
        packageName: String,
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long, percent: Int?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val apkDirectory = File(context.cacheDir, "apks").apply { mkdirs() }
        val target = File(apkDirectory, "$packageName.apk")
        val partial = File(apkDirectory, "$packageName.apk.part")

        if (target.exists()) target.delete()
        if (partial.exists()) partial.delete()

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed (HTTP ${response.code} ${response.message}).")
                }
                val body = response.body ?: throw IOException("Download failed: empty response body.")
                val totalBytes = body.contentLength().coerceAtLeast(0L)

                var downloadedBytes = 0L
                var lastPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val percent = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                null
                            }
                            if (percent != lastPercent) {
                                lastPercent = percent ?: -1
                                onProgress(downloadedBytes, totalBytes, percent)
                            }
                        }
                        output.flush()
                    }
                }

                if (totalBytes > 0 && downloadedBytes != totalBytes) {
                    throw IOException("Download was incomplete ($downloadedBytes of $totalBytes bytes).")
                }
            }

            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }

            onProgress(target.length(), target.length(), 100)
            target
        } catch (error: Throwable) {
            partial.delete()
            target.delete()
            throw error
        }
    }

    /**
     * Commits a previously downloaded APK in a full-install session.
     *
     * On Android 12+ user action is not required, which makes updates to apps that this
     * store installed happen silently. The system still shows a confirmation for the first
     * install of an app (delivered as [PackageInstaller.STATUS_PENDING_USER_ACTION]).
     */
    suspend fun installApk(apkFile: File, packageName: String) = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            throw IOException("The downloaded APK is missing or empty.")
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            session.openWrite(SESSION_NAME, 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    input.copyTo(output, BUFFER_SIZE)
                }
                session.fsync(output)
            }

            val callback = Intent(context, InstallResultReceiver::class.java).apply {
                putExtra(InstallResultReceiver.EXTRA_PACKAGE_NAME, packageName)
                putExtra(InstallResultReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
        } catch (error: Throwable) {
            runCatching { session.abandon() }
            apkFile.delete()
            throw error
        } finally {
            runCatching { session.close() }
        }
    }

    private companion object {
        const val SESSION_NAME = "base.apk"
        const val BUFFER_SIZE = 64 * 1024
    }
}
