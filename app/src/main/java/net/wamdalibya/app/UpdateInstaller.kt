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
import androidx.core.content.FileProvider
import java.io.File

/**
 * In-app APK update: downloads the new APK with DownloadManager and opens
 * the Android installer automatically when the download finishes.
 *
 * Android never allows a fully silent install from a sideloaded app, so
 * "automatic" here means: one tap starts the download, the installer
 * opens by itself when it is done, and the user confirms once.
 *
 * v3.3 — fix for "tap Update now and the app exits" (reported 2026-11-25):
 * the old code, when the app itself had never been granted the
 * install-unknown-apps permission (the user installed v3.1 from the
 * browser, so the permission belonged to the browser, not to Wamda),
 * jumped straight out of the app into the system Settings page — to the
 * user that looked exactly like the app crashing and exiting. The new
 * flow NEVER leaves the app on its own: startUpdate returns
 * NeedsPermission and the UI shows an in-app dialog explaining the
 * one-time permission; only the dialog's button opens Settings.
 * A failed download also no longer yanks the user into the browser —
 * it reports back so the button can be tapped again.
 *
 * Nothing here may ever crash the app.
 */
object UpdateInstaller {

    private const val PREFS = "wamda_update"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val APK_FILE = "update.apk"

    /** What startUpdate did. The UI decides what to show from this. */
    sealed interface UpdateResult {
        /** Download enqueued; [onDownloadFinished] fires once (true = installer opened). */
        data object Started : UpdateResult
        /** The app needs the one-time install permission first. */
        data object NeedsPermission : UpdateResult
        /** Nothing was started (bad URL, no DownloadManager, ...). */
        data object Failed : UpdateResult
    }

    /** Opens the system "Install unknown apps" page for this app. Call only from a user tap. */
    fun openInstallSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:" + context.applicationContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.applicationContext.startActivity(intent)
        } catch (e: Exception) {
            // No settings page on this device — the dialog stays, user retries later.
        }
    }

    fun startUpdate(
        context: Context,
        apkUrl: String,
        onDownloadFinished: (Boolean) -> Unit = {}
    ): UpdateResult {
        val appContext = context.applicationContext
        try {
            // Only real http(s) links may be downloaded.
            val uri = Uri.parse(apkUrl)
            if (apkUrl.isBlank() || (uri.scheme != "http" && uri.scheme != "https")) {
                return UpdateResult.Failed
            }
            // Unknown-source installs must be allowed once per app. Do NOT
            // jump to Settings here — report it so the UI can explain first.
            if (Build.VERSION.SDK_INT >= 26 &&
                !appContext.packageManager.canRequestPackageInstalls()
            ) {
                return UpdateResult.NeedsPermission
            }
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return UpdateResult.Failed
            val request = DownloadManager.Request(uri)
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
                    onDownloadFinished(handleComplete(appContext, doneId))
                }
            }
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            return UpdateResult.Started
        } catch (e: Exception) {
            return UpdateResult.Failed
        }
    }

    /** Returns true when the installer was opened, false on any failure. */
    private fun handleComplete(appContext: Context, id: Long): Boolean {
        try {
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return false
            var ok = false
            dm.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    ok = status == DownloadManager.STATUS_SUCCESSFUL
                }
            }
            if (!ok) return false
            val apk = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE
            )
            if (!apk.exists()) return false
            val uri = FileProvider.getUriForFile(
                appContext, appContext.packageName + ".fileprovider", apk
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
