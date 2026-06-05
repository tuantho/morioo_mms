package com.morioo.mms

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    private val BG     = Color.parseColor("#0d0812")
    private val CARD   = Color.parseColor("#1a1025")
    private val PURPLE = Color.parseColor("#5B2BE0")
    private val GREEN  = Color.parseColor("#50E3A4")
    private val RED    = Color.parseColor("#FF5555")
    private val TEXT   = Color.parseColor("#ffeedd")
    private val SUBTLE = Color.parseColor("#888888")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(28), dp(48), dp(28), dp(32))
        }

        // ── Titre ────────────────────────────────────────────────────────────
        root.addView(TextView(this).apply {
            setText("⚙️  Paramètres")
            setTextColor(PURPLE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(separator(), lpMatch(top = dp(12), bottom = dp(28)))

        // ── Champ URL Pi ─────────────────────────────────────────────────────
        root.addView(label("Adresse du Raspberry Pi"))
        root.addView(TextView(this).apply {
            setText("Format : http://192.168.43.100:8000  ou  http://rasp-boesch.local:8000")
            setTextColor(SUBTLE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(6))
        })

        val urlField = EditText(this).apply {
            setText(AppPreferences.piUrl)
            setTextColor(TEXT)
            setHintTextColor(SUBTLE)
            hint = "http://..."
            setBackgroundColor(CARD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(urlField, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(4); bottomMargin = dp(10)
        })

        // ── Statut test ───────────────────────────────────────────────────────
        val statusView = TextView(this).apply {
            setText("")
            setTextColor(SUBTLE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(statusView)

        // ── Boutons ───────────────────────────────────────────────────────────
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val testBtn = styledButton("Tester la connexion", CARD, TEXT)
        val saveBtn = styledButton("Enregistrer", PURPLE, Color.WHITE)

        btnRow.addView(testBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            marginEnd = dp(12)
        })
        btnRow.addView(saveBtn)
        root.addView(btnRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ── Infos réseau ──────────────────────────────────────────────────────
        root.addView(separator(), lpMatch(top = dp(32), bottom = dp(20)))
        root.addView(label("Conseil réseau"))
        root.addView(TextView(this).apply {
            setText(
                "Si rasp-boesch.local ne répond pas (DNS_PROBE_FINISHED_NXDOMAIN), " +
                "utilise l'adresse IP directe.\n\n" +
                "Sur hotspot Pixel → le Pi prend généralement une IP en 192.168.43.x.\n" +
                "Appuie sur « Tester » pour vérifier avant d'enregistrer."
            )
            setTextColor(SUBTLE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(0f, 1.4f)
        })

        // ── Listeners ─────────────────────────────────────────────────────────
        testBtn.setOnClickListener {
            val url = urlField.text.toString().trim().trimEnd('/')
            if (url.isEmpty()) {
                statusView.setText("⚠️ Saisis une adresse d'abord")
                statusView.setTextColor(RED)
                return@setOnClickListener
            }
            statusView.setText("⏳ Test en cours…")
            statusView.setTextColor(SUBTLE)

            CoroutineScope(Dispatchers.IO).launch {
                val result = testUrl(url)
                withContext(Dispatchers.Main) {
                    when (result) {
                        TestResult.OK -> {
                            statusView.setText("✅ Pi joignable — connexion réussie !")
                            statusView.setTextColor(GREEN)
                        }
                        TestResult.HTTP_ERROR -> {
                            statusView.setText("⚠️ Serveur trouvé mais réponse inattendue")
                            statusView.setTextColor(RED)
                        }
                        TestResult.UNREACHABLE -> {
                            statusView.setText("❌ Pi injoignable — vérifie l'adresse et le réseau")
                            statusView.setTextColor(RED)
                        }
                    }
                }
            }
        }

        saveBtn.setOnClickListener {
            val url = urlField.text.toString().trim().trimEnd('/')
            if (url.isEmpty()) {
                Toast.makeText(this, "Adresse vide — non enregistrée", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPreferences.piUrl = url
            Toast.makeText(this, "✅ Adresse enregistrée", Toast.LENGTH_SHORT).show()
            finish()
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(root)
        })

        // Focus + clavier automatique sur le champ URL
        urlField.requestFocus()
        urlField.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(urlField, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    // ── Helpers UI ────────────────────────────────────────────────────────────

    private fun label(text: String) = TextView(this).apply {
        setText(text)
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun styledButton(text: String, bg: Int, fg: Int) = Button(this).apply {
        setText(text)
        setBackgroundColor(bg)
        setTextColor(fg)
        setPadding(dp(20), dp(10), dp(20), dp(10))
    }

    private fun separator() = View(this).apply {
        setBackgroundColor(PURPLE)
        alpha = 0.3f
    }

    private fun lpMatch(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
            topMargin = top; bottomMargin = bottom
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // ── Test réseau ───────────────────────────────────────────────────────────

    private enum class TestResult { OK, HTTP_ERROR, UNREACHABLE }

    private fun testUrl(base: String): TestResult = try {
        val conn = URL("$base/api/status").openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout    = 4000
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299) TestResult.OK else TestResult.HTTP_ERROR
    } catch (e: Exception) {
        TestResult.UNREACHABLE
    }
}
