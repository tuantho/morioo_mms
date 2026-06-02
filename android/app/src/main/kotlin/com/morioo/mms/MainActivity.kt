package com.morioo.mms

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    companion object {
        private const val PI_URL = "http://rasp-boesch:8000"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Plein écran immersif
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(0xFF5B2BE0.toInt(), 0xFF50E3A4.toInt())
        }

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    swipeRefresh.isRefreshing = false
                }
                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        swipeRefresh.isRefreshing = false
                        view.loadData(errorPage(), "text/html", "utf-8")
                    }
                }
            }

            webChromeClient = WebChromeClient()
        }

        swipeRefresh.apply {
            addView(webView)
            setOnRefreshListener { webView.reload() }
        }

        setContentView(swipeRefresh)
        webView.loadUrl(PI_URL)
    }

    // Bouton retour = navigation dans le WebView
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun errorPage() = """
        <html><body style="background:#0d0812;color:#ffeedd;font-family:monospace;
            display:flex;flex-direction:column;align-items:center;justify-content:center;
            height:100vh;margin:0;text-align:center;">
          <div style="font-size:64px">🚤</div>
          <h2 style="color:#5B2BE0">Boesch 510</h2>
          <p>Impossible de joindre le Pi.<br>
             Vérifie que tu es connecté au réseau bateau.</p>
          <p style="color:#888;font-size:12px">$PI_URL</p>
          <button onclick="location.reload()"
            style="margin-top:20px;padding:12px 32px;background:#5B2BE0;color:white;
                   border:none;border-radius:8px;font-size:16px;cursor:pointer">
            Réessayer
          </button>
        </body></html>
    """.trimIndent()
}
