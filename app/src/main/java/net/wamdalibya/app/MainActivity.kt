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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * One site per app — the site comes from the build flavor (BuildConfig.SITE_URL):
 * - ar flavor: Wamda Libya (Arabic) — https://wamdalibya.netlify.app/
 * - en flavor: Libya Pulse (English) — https://abdo15542.github.io/libyapulse/
 * Each flavor is its own installable app (own package, own name, own icon
 * label). New articles appear automatically — the app always loads the
 * live site.
 * The "WamdaApp/1.0" user-agent suffix lets the sites hide AdSense ads
 * inside the app (AdSense is for the web; apps must not show it).
 * The Netlify "Powered by" badge is hidden inside the app with injected
 * CSS/JS (it still shows on the website in a browser — that is Netlify's
 * free-tier mark and can only be removed from the site itself by moving
 * the site off Netlify's free hosting).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var splash: View
    private lateinit var offlineView: View

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

        swipeRefresh.setColorSchemeColors(getColor(R.color.gold))

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        // Lets the sites hide AdSense ads inside the app.
        settings.userAgentString = settings.userAgentString + " WamdaApp/1.0"

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
}
