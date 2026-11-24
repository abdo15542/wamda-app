package net.wamdalibya.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background update check — the "instant notification".
 *
 * Every 15 minutes (Android's minimum for periodic work) the worker
 * fetches announcements.json. When a newer build exists, it posts a
 * system notification; tapping it opens the app, where the update card
 * sits at the top of the home screen. No Firebase/FCM, no server.
 *
 * Enqueued once from MainActivity with KEEP, so reinstalling the
 * schedule never duplicates the work.
 */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = FeedRepository(applicationContext, BuildConfig.FEED_BASE_URL)
            val ann = repo.fetchAnnouncements()
            if (ann.latestVersionCode > BuildConfig.VERSION_CODE &&
                ann.apkUrl.isNotBlank()
            ) {
                showUpdateNotification(applicationContext, ann.latestVersionName)
            }
            Result.success()
        } catch (e: Exception) {
            // Never retry-loop: the next periodic run tries again.
            Result.success()
        }
    }

    private fun showUpdateNotification(context: Context, versionName: String) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val channelId = "app_updates"
            val nm =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(channel)
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_text, versionName))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(1001, notification)
        } catch (e: Exception) {
            // A notification must never crash the worker.
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "update-check", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
