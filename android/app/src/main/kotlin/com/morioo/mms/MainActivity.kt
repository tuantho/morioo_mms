package com.morioo.mms

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.webkit.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Écran toujours allumé (dashboard bateau)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Permet à la WebView d'occuper toute la fenêtre y compris sous les barres système
        WindowCompat.setDecorFitsSystemWindows(window, false)

        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(0xFF5B2BE0.toInt(), 0xFF50E3A4.toInt())
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0d0812"))

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = false
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                allowContentAccess = true
                allowFileAccess = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    swipeRefresh.isRefreshing = false
                    // Force le recalcul du layout position:fixed après chargement
                    view.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null)
                    Log.d("Morioo", "Page chargée : $url")
                }
                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError
                ) {
                    Log.e("Morioo", "Erreur WebView : ${error.description} → ${request.url}")
                    if (request.isForMainFrame) {
                        swipeRefresh.isRefreshing = false
                        view.loadData(errorPage(), "text/html", "utf-8")
                    }
                }
                override fun onReceivedHttpError(
                    view: WebView, request: WebResourceRequest, response: WebResourceResponse
                ) {
                    Log.e("Morioo", "HTTP ${response.statusCode} → ${request.url}")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    Log.d("Morioo/JS", "${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                    return true
                }
            }
        }

        swipeRefresh.addView(webView)
        swipeRefresh.setOnRefreshListener { webView.loadUrl(AppPreferences.piUrl) }

        // ── Bouton ⚙️ flottant (coin haut-droit) ────────────────────────────
        val settingsBtn = TextView(this).apply {
            text = "⚙"
            setTextColor(Color.parseColor("#5B2BE0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setBackgroundColor(Color.parseColor("#CC0d0812"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            alpha = 0.80f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        val frame = FrameLayout(this).apply {
            addView(swipeRefresh)
            addView(settingsBtn, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { topMargin = dp(12); marginEnd = dp(12) })
        }

        setContentView(frame)

        // Masquer barres système après attachement de la vue (WindowCompat)
        WindowInsetsControllerCompat(window, frame).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView.loadUrl(AppPreferences.piUrl.trimEnd('/') + "/android")

        // Bridge média local
        startService(Intent(this, MediaBridgeService::class.java))
    }

    // Après retour des Settings : recharger avec la nouvelle URL si elle a changé
    override fun onResume() {
        super.onResume()
        val target = AppPreferences.piUrl.trimEnd('/') + "/android"
        val current = webView.url ?: ""
        if (!current.startsWith(target)) {
            webView.loadUrl(target)
        }
    }

    // Bouton retour = navigation dans le WebView
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun errorPage(): String {
        val url = AppPreferences.piUrl
        return """
            <html><body style="background:#0d0812;color:#ffeedd;font-family:monospace;
                display:flex;flex-direction:column;align-items:center;justify-content:center;
                height:100vh;margin:0;text-align:center;">
              <div style="font-size:64px">🚤</div>
              <h2 style="color:#5B2BE0">Boesch 510</h2>
              <p>Impossible de joindre le Pi.<br>
                 Vérifie que tu es connecté au réseau bateau.</p>
              <p style="color:#888;font-size:12px">$url</p>
              <button onclick="location.reload()"
                style="margin-top:20px;padding:12px 32px;background:#5B2BE0;color:white;
                       border:none;border-radius:8px;font-size:16px;cursor:pointer">
                Réessayer
              </button>
            </body></html>
        """.trimIndent()
    }
}
