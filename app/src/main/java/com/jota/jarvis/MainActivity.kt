package com.jota.jarvis

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var core: CoreView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

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
            runDemo(CoreState.LISTENING, "ESCUTANDO", 3000)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                if (input.text.toString().isNotBlank()) {
                    input.text.clear()
                    runDemo(CoreState.PROCESSING, "PROCESSANDO", 1800)
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

    private fun runDemo(first: CoreState, firstLabel: String, ms: Long) {
        handler.removeCallbacksAndMessages(null)
        show(first, firstLabel)
        handler.postDelayed({
            show(CoreState.DONE, "CONCLUÍDO")
            handler.postDelayed({ show(CoreState.IDLE, "ONLINE") }, 1500)
        }, ms)
    }
}
