package net.wamdalibya.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * In-app APK update: downloads the new APK with DownloadManager and opens
 * the Android installer automatically when the download finishes.
 *
 * Android never allows a fully silent install from a sideloaded app, so
 * "automatic" here means: one tap starts the download, the installer
 * opens by itself when it is done, and the user confirms once. If the
 * system blocks installs from unknown sources, the system settings page
 * for this app is opened instead — the user allows it once, then taps
 * the update button again.
 *
 * Every failure falls back to opening the APK URL in the browser.
 * Nothing here may ever crash the app.
 */
object UpdateInstaller {

    private const val PREFS = "wamda_update"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val APK_FILE = "update.apk"

    private fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // A bad link must never crash the app.
        }
    }

    /**
     * Starts the update. Returns true when a download was enqueued
     * (the button can show "downloading"), false when nothing was
     * enqueued (settings opened, or browser fallback — the button
     * stays enabled so the user can tap again).
     */
    fun startUpdate(context: Context, apkUrl: String): Boolean {
        val appContext = context.applicationContext
        try {
            // Unknown-source installs must be allowed once per app.
            if (Build.VERSION.SDK_INT >= 26 &&
                !appContext.packageManager.canRequestPackageInstalls()
            ) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:" + appContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(settingsIntent)
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.update_install_allow),
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(appContext.getString(R.string.update_downloading))
                .setDestinationInExternalFilesDir(
                    appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILE
                )
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setAllowedOverRoaming(false)
            val id = dm.enqueue(request)
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_DOWNLOAD_ID, id).apply()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val doneId =
                        intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    val saved = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getLong(KEY_DOWNLOAD_ID, -1)
                    if (doneId != saved) return
                    try {
                        appContext.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // Already unregistered — harmless.
                    }
                    handleComplete(appContext, doneId, apkUrl)
                }
            }
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            return true
        } catch (e: Exception) {
            openUrl(context, apkUrl)
            return false
        }
    }

    private fun handleComplete(appContext: Context, id: Long, apkUrl: String) {
        try {
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var ok = false
            dm.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    ok = status == DownloadManager.STATUS_SUCCESSFUL
                }
            }
            if (!ok) {
                openUrl(appContext, apkUrl)
                return
            }
            val apk = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE
            )
            if (!apk.exists()) {
                openUrl(appContext, apkUrl)
                return
            }
            val uri = FileProvider.getUriForFile(
                appContext, appContext.packageName + ".fileprovider", apk
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(installIntent)
        } catch (e: Exception) {
            openUrl(appContext, apkUrl)
        }
    }
}
