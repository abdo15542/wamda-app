package net.wamdalibya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.MobileAds

/**
 * Native app (no WebView). One codebase, two flavors:
 * - ar: ومضة — https://abdo15542.github.io/wamdalibya/
 * - en: TechPulse — https://abdo15542.github.io/techpulse/
 * Each flavor reads its own JSON feed (feed/index.json + per-article
 * JSON) and renders articles natively. New articles appear the moment
 * they are published — the app always fetches the live feed, with a
 * disk cache as the offline fallback.
 * AdMob: one small banner fixed at the bottom only (see Screens.kt).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)
        setContent {
            val vm: AppViewModel = viewModel()
            WamdaApp(vm)
        }
    }
}
