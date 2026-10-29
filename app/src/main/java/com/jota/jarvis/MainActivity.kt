package com.jota.jarvis

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var core: CoreView
    private lateinit var status: TextView
    private lateinit var reply: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("jarvis", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val cBg = Color.parseColor("#080B10")
        val cPanel = Color.parseColor("#111923")
        val cCyan = Color.parseColor("#48DFF0")
        val cText = Color.parseColor("#EAF2FA")
        val cGray = Color.parseColor("#8291A3")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 16f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.3f
            setTextColor(cCyan)
            gravity = Gravity.CENTER
            setOnLongClickListener { askSettings(); true }
        }
        root.addView(title, LinearLayout.LayoutParams(match, wrap).apply {
            topMargin = dp(24)
        })

        status = TextView(this).apply {
            text = "ONLINE"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
            setTextColor(cGray)
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(match, wrap).apply {
            topMargin = dp(6)
        })

        core = CoreView(this)
        root.addView(core, LinearLayout.LayoutParams(match, 0, 1f))

        reply = TextView(this).apply {
            textSize = 15f
            setTextColor(cText)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val scroll = ScrollView(this).apply {
            addView(reply)
            background = GradientDrawable().apply {
                setColor(cPanel)
                cornerRadius = dp(16).toFloat()
            }
        }
        root.addView(scroll, LinearLayout.LayoutParams(match, dp(170)).apply {
            setMargins(dp(20), dp(4), dp(20), dp(4))
        })

        val input = EditText(this).apply {
            hint = "Digite um comando..."
            setHintTextColor(cGray)
            setTextColor(cText)
            textSize = 16f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = GradientDrawable().apply {
                setColor(cPanel)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.argb(120, 72, 223, 240))
            }
        }
        root.addView(input, LinearLayout.LayoutParams(match, wrap).apply {
            setMargins(dp(20), dp(8), dp(20), dp(20))
        })

        core.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            show(CoreState.LISTENING, "ESCUTANDO")
            handler.postDelayed({ show(CoreState.IDLE, "ONLINE") }, 3000)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    input.text.clear()
                    chat(text)
                }
                true
            } else {
                false
            }
        }

        setContentView(root)
    }

    private fun show(state: CoreState, label: String) {
        core.state = state
        status.text = label
    }

    private fun askSettings() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val keyIn = EditText(this).apply {
            hint = "Chave da API (OpenRouter)"
            setText(prefs.getString("key", ""))
            setSingleLine()
        }
        val modelIn = EditText(this).apply {
            hint = "Nome do modelo"
            setText(prefs.getString("model", ""))
            setSingleLine()
        }
        box.addView(keyIn)
        box.addView(modelIn)
        AlertDialog.Builder(this)
            .setTitle("Configurar IA")
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.edit()
                    .putString("key", keyIn.text.toString().trim())
                    .putString("model", modelIn.text.toString().trim())
                    .apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun chat(text: String) {
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "") ?: ""
        if (key.isBlank() || model.isBlank()) {
            askSettings()
            return
        }
        handler.removeCallbacksAndMessages(null)
        show(CoreState.PROCESSING, "PROCESSANDO")
        reply.text = ""

        thread {
            val result = try {
                val system = JSONObject()
                    .put("role", "system")
                    .put("content", "Você é o JARVIS, um assistente pessoal. Responda em português, de forma curta e direta.")
                val user = JSONObject()
                    .put("role", "user")
                    .put("content", text)
                val messages = JSONArray().put(system).put(user)
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messages)

                val conn = URL("https://openrouter.ai/api/v1/chat/completions")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code in 200..299) {
                    JSONObject(raw)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                } else {
                    "Erro $code: ${raw.take(300)}"
                }
            } catch (e: Exception) {
                "Falha: ${e.message}"
            }

            runOnUiThread {
                reply.text = result
                show(CoreState.DONE, "CONCLUÍDO")
                handler.postDelayed({ show(CoreState.IDLE, "ONLINE") }, 1500)
            }
        }
    }
}
