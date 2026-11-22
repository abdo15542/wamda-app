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
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Wamda Libya news app: a WebView shell around the two live sites.
 * - Arabic tab: https://wamdalibya.netlify.app/
 * - English tab: https://abdo15542.github.io/libyapulse/
 * New articles appear automatically — the app always loads the live sites.
 * The "WamdaApp/1.0" user-agent suffix lets the sites hide AdSense ads
 * inside the app (AdSense is for the web; apps must not show it).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var splash: View
    private lateinit var offlineView: View
    private lateinit var bottomNav: BottomNavigationView

    private var currentUrl = AR_URL

    companion object {
        const val AR_URL = "https://wamdalibya.netlify.app/"
        const val EN_URL = "https://abdo15542.github.io/libyapulse/"
        const val PREFS = "wamda_prefs"
        const val KEY_TAB = "last_tab"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        splash = findViewById(R.id.splash)
        offlineView = findViewById(R.id.offline_view)
        bottomNav = findViewById(R.id.bottom_nav)

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

        // Restore the last tab (Arabic by default).
        val savedTab =
            getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TAB, AR_URL) ?: AR_URL
        currentUrl = savedTab
        bottomNav.selectedItemId = if (savedTab == EN_URL) R.id.nav_en else R.id.nav_ar

        bottomNav.setOnItemSelectedListener { item ->
            val url = when (item.itemId) {
                R.id.nav_en -> EN_URL
                else -> AR_URL
            }
            if (url != currentUrl) {
                currentUrl = url
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putString(KEY_TAB, url).apply()
                webView.loadUrl(url)
            }
            true
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

        webView.loadUrl(savedTab)
    }
}
