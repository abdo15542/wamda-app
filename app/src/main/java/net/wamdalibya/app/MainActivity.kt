package net.wamdalibya.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

/**
 * One site per app — the site comes from the build flavor (BuildConfig.SITE_URL):
 * - ar flavor: Wamda Libya (Arabic) — https://abdo15542.github.io/wamdalibya/
 * - en flavor: TechPulse (English) — https://abdo15542.github.io/techpulse/
 * Each flavor is its own installable app (own package, own name, own icon
 * label). New articles appear automatically — the app always loads the
 * live site.
 * The "<token>/1.0 vc<N>" user-agent suffix (WamdaApp for the AR flavor,
 * TechPulseApp for the EN flavor) lets the sites hide AdSense ads
 * inside the app (AdSense is for the web; apps must not show it) and offer
 * app updates — N is this flavor's versionCode (BuildConfig.VERSION_CODE).
 * The Netlify "Powered by" badge is hidden inside the app with injected
 * CSS/JS (it still shows on the website in a browser — that is Netlify's
 * free-tier mark and can only be removed from the site itself by moving
 * the site off Netlify's free hosting).
 * AdMob: ONE small adaptive banner fixed at the bottom of the screen only
 * (Abdoo's real IDs, per flavor via BuildConfig.ADMOB_BANNER_ID — AR and
 * EN have different banner units). No interstitials, no app-open ads, no
 * popups — nothing ever covers the articles or interrupts reading.
 * NOTE: the AdMob account is still under Google review (~24h); the banner
 * units stay inactive until it passes, so the apps may show limited or no
 * real ads until then — that is expected, not a build failure.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var splash: View
    private lateinit var offlineView: View
    private lateinit var adView: AdView

    companion object {
        // Hides the Netlify "Powered by" badge (and anything Netlify-branded)
        // inside the app. Runs on every page load and watches for late
        // injections, so the badge never flashes on screen.
        const val HIDE_BADGE_JS = "(function(){var css=document.createElement('style');css.textContent='#nl-badge-frame,iframe[title*=\"Netlify\" i],[id*=\"netlify\" i],[class*=\"netlify\" i],iframe[src*=\"netlify\"]{display:none!important}';document.head.appendChild(css);function hide(){document.querySelectorAll('#nl-badge-frame,iframe[title*=\"Netlify\" i],iframe[src*=\"netlify\"],[id*=\"netlify\" i],[class*=\"netlify\" i]').forEach(function(e){e.style.display='none';});document.querySelectorAll('a,div,span').forEach(function(e){if(e.children.length===0&&e.textContent&&e.textContent.indexOf('Powered by Netlify')!==-1){e.style.display='none';}});}hide();new MutationObserver(hide).observe(document.documentElement,{childList:true,subtree:true});})();"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        splash = findViewById(R.id.splash)
        offlineView = findViewById(R.id.offline_view)

        // AdMob: one small adaptive banner at the bottom only.
        // Created programmatically: an AdView inflated from XML without
        // app:adSize fails to load ("Required XML attribute adSize was
        // missing" — seen on Abdoo's phone 2026-11-23). setAdSize must
        // run before loadAd on a programmatic AdView.
        MobileAds.initialize(this)
        adView = AdView(this)
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, getAdWidthDp()))
        // Real banner unit ID for this flavor (BuildConfig.ADMOB_BANNER_ID).
        adView.adUnitId = BuildConfig.ADMOB_BANNER_ID
        findViewById<FrameLayout>(R.id.ad_container).addView(adView)
        adView.loadAd(AdRequest.Builder().build())

        swipeRefresh.setColorSchemeColors(getColor(R.color.brand_accent))

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        // Lets the sites hide AdSense ads inside the app, and lets them
        // offer app updates: the "vc<N>" token carries this flavor's
        // versionCode, so the site can tell an old install a newer
        // build is available (see assets/site.js on both sites).
        // The token name is per-flavor (BuildConfig.UA_TOKEN): WamdaApp
        // for AR, TechPulseApp for EN.
        settings.userAgentString = settings.userAgentString + " " + BuildConfig.UA_TOKEN + "/1.0 vc" + BuildConfig.VERSION_CODE

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                offlineView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
                splash.visibility = View.GONE
                // Hide the Netlify badge inside the app.
                view?.evaluateJavascript(HIDE_BADGE_JS, null)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    swipeRefresh.isRefreshing = false
                    splash.visibility = View.GONE
                    offlineView.visibility = View.VISIBLE
                }
            }

            // Keep every link inside the app.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }

        swipeRefresh.setOnRefreshListener { webView.reload() }

        findViewById<Button>(R.id.retry_button).setOnClickListener {
            offlineView.visibility = View.GONE
            webView.reload()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        webView.loadUrl(BuildConfig.SITE_URL)
    }

    // Screen width in dp, for the anchored adaptive banner size.
    private fun getAdWidthDp(): Int {
        val metrics = resources.displayMetrics
        return (metrics.widthPixels / metrics.density).toInt()
    }
}
