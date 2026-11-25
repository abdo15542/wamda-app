package net.wamdalibya.app

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash diagnostics (v3.3). The app has no crash reporting, so a real
 * crash on a user's phone was invisible — the fix for "the app exits
 * when I tap Update" could only be guessed at. This records the exact
 * stack trace so the cause can be read, not guessed:
 *
 * - install(): catches any uncaught exception, writes the stack trace
 *   to a small file in the app cache (synchronously — the process is
 *   dying, so nothing async is safe), then hands the crash to the
 *   previous handler so Android still shows its normal crash dialog.
 * - uploadPending(): on the next launch, sends the saved report to
 *   Firestore collection "crash_reports" (any authenticated uid may
 *   create; nobody reads from the app) and deletes the file. If the
 *   user is not signed in yet, the file waits for the following launch.
 *
 * Everything here is best-effort and failure-safe: a diagnostics
 * failure must never crash the app or block startup.
 */
object CrashReporter {

    private const val FILE_NAME = "crash-log.txt"
    private const val MAX_STACK_CHARS = 8000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stamp = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.US
                ).format(Date())
                val writer = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(writer))
                val stack = writer.toString().take(MAX_STACK_CHARS)
                val text = StringBuilder()
                    .append("time=").append(stamp).append("\n")
                    .append("version=")
                    .append(BuildConfig.VERSION_NAME)
                    .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                    .append("brand=")
                    .append(if (BuildConfig.IS_ARABIC) "wamda-ar" else "techpulse-en")
                    .append("\n")
                    .append("android=").append(Build.VERSION.SDK_INT).append("\n")
                    .append("device=").append(Build.MANUFACTURER)
                    .append(" ").append(Build.MODEL).append("\n")
                    .append("thread=").append(thread.name).append("\n")
                    .append(stack)
                    .toString()
                File(appContext.cacheDir, FILE_NAME).writeText(text)
            } catch (e: Exception) {
                // Diagnostics must never interfere with the crash itself.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun uploadPending(context: Context) {
        try {
            val file = File(context.applicationContext.cacheDir, FILE_NAME)
            if (!file.exists()) return
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val text = file.readText()
            val data = hashMapOf<String, Any>(
                "uid" to uid,
                "version" to BuildConfig.VERSION_NAME,
                "versionCode" to BuildConfig.VERSION_CODE,
                "brand" to if (BuildConfig.IS_ARABIC) "wamda-ar" else "techpulse-en",
                "androidSdk" to Build.VERSION.SDK_INT,
                "device" to (Build.MANUFACTURER + " " + Build.MODEL),
                "stack" to text,
                "createdAt" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance().collection("crash_reports")
                .add(data)
                .addOnSuccessListener { file.delete() }
                .addOnFailureListener { /* keep the file; retry next launch */ }
        } catch (e: Exception) {
            // Never block startup over diagnostics.
        }
    }
}
