package cz.kachnicesta

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher

/**
 * Celá 3D hra je webová stránka v assets/index.html (Three.js je přibalený,
 * takže hra běží i bez internetu). Aktivita ji jen zobrazí a předá jí
 * tlačítko Zpět, které hru pozastaví.
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
            loadUrl("file:///android_asset/index.html")
        }
        // Od Androidu 15 kreslí aplikace i pod systémové lišty; rámeček je odsadí.
        val root = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(getColor(R.color.bg))
            addView(web)
        }
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }
    }

    @Deprecated("Používá se jen na Androidu 12 a starším")
    override fun onBackPressed() = handleBack()

    /** Zpět během hry ji pozastaví, jinak aplikaci zavře. */
    private fun handleBack() {
        web.evaluateJavascript("(window.onAndroidBack ? window.onAndroidBack() : false)") { handled ->
            if (handled != "true") finish()
        }
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }
}
