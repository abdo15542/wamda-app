package net.wamdalibya.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.MobileAds

/**
 * Native app (no WebView). One codebase, two flavors:
 * - ar: ومضة — https://abdo15542.github.io/wamdalibya/
 * - en: TechPulse — https://abdo15542.github.io/techpulse/
 * Each flavor reads its own JSON feed (feed/index.json + per-article
 * JSON) and renders articles natively. New articles appear the moment
 * they are published — the app shows the cached feed instantly, then
 * refreshes in the background, with a disk cache as the offline fallback.
 * AdMob: one small banner fixed at the bottom only (see Screens.kt).
 *
 * Update notifications: a WorkManager periodic check (every 15 minutes)
 * posts a system notification when a newer build is published; the
 * notification permission is asked once at startup on Android 13+.
 *
 * NOTE 2026-11-24: run 45438057804 failed with exit code 1 and no compiler
 * error in the annotations while GitHub's cache service was erroring
 * ("Our services aren't available right now", incl. "Restore Gradle
 * distribution 8.5 failed") — the same transient pattern as the v1 build.
 * This commit only re-triggers CI; no code changed.
 * UPDATE: re-trigger run 45438796984 failed identically (same cache
 * errors, still no compiler error); githubstatus.com now reports
 * "All Systems Operational", so this is one more re-trigger attempt.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)
        UpdateCheckWorker.enqueue(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: AppViewModel = viewModel()
            WamdaApp(vm)
        }
    }
}
