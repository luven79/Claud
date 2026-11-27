package cz.prolomkod

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher

/**
 * Celá hra je webová stránka v assets/index.html (hra pro jednoho, dva hráči
 * na jednom telefonu a online hra přes veřejný MQTT server). Aktivita ji jen
 * zobrazí, předá jí tlačítko Zpět a nabídne sdílení kódu hry.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/index.html")
        }
        // Od Androidu 15 kreslí aplikace i pod stavovou lištu; rámeček
        // s fitsSystemWindows odsadí hru od systémových lišt.
        val root = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(getColor(R.color.bg))
            addView(web)
        }
        setContentView(root)
        useDarkStatusBarIcons()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }
    }

    @Deprecated("Používá se jen na Androidu 12 a starším")
    override fun onBackPressed() = handleBack()

    /** Stránka sama rozhodne, jestli Zpět vrátí do menu, nebo se aplikace zavře. */
    private fun handleBack() {
        web.evaluateJavascript("(window.onAndroidBack ? window.onAndroidBack() : false)") { handled ->
            if (handled != "true") finish()
        }
    }

    private fun useDarkStatusBarIcons() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (night) return
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun share(text: String) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_title)))
        }
    }
}
