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
