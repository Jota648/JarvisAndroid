package com.jota.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var core: CoreView
    private lateinit var status: TextView
    private lateinit var reply: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("jarvis", Context.MODE_PRIVATE) }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var speaking = false
    private var busy = false

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                reply.text = "Sem permissão do microfone. Ative em Configurações > Apps > JARVIS > Permissões."
            }
        }

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
            setPadding(dp(24), dp(8), dp(24), dp(8))
            setOnLongClickListener { askVoice(); true }
        }
        root.addView(status, LinearLayout.LayoutParams(match, wrap))

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
            hint = "Digite ou toque no núcleo e fale..."
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

        core.setOnClickListener { onCoreTap() }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = input.text.toString().trim()
                if (text.isNotEmpty() && !busy) {
                    input.text.clear()
                    chat(text)
                }
                true
            } else {
                false
            }
        }

        setContentView(root)
        initSpeech()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun initSpeech() {
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("pt", "BR"))
                ttsReady = true
                applyVoice()
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    speaking = true
                    show(CoreState.DONE, "FALANDO")
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    speaking = false
                    show(CoreState.IDLE, "ONLINE")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    speaking = false
                    show(CoreState.IDLE, "ONLINE")
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                runOnUiThread {
                    speaking = false
                    show(CoreState.IDLE, "ONLINE")
                }
            }
        })
    }

    private fun applyVoice() {
        val engine = tts ?: return
        val name = prefs.getString("voice", "") ?: ""
        if (name.isNotBlank()) {
            val found = engine.voices?.firstOrNull { it.name == name }
            if (found != null) engine.setVoice(found)
        }
        engine.setSpeechRate(prefs.getFloat("rate", 1f))
        engine.setPitch(prefs.getFloat("pitch", 1f))
    }

    private fun askVoice() {
        val engine = tts
        if (engine == null || !ttsReady) {
            reply.text = "A voz ainda está carregando. Tente de novo em instantes."
            return
        }
        val voices: List<Voice> = (engine.voices ?: emptySet<Voice>())
            .filter { it.locale.language == "pt" }
            .sortedWith(compareBy({ it.locale.country }, { it.name }))
        if (voices.isEmpty()) {
            reply.text = "Nenhuma voz em português encontrada. Verifique em Configurações > Texto para fala."
            return
        }

        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        val labels = voices.mapIndexed { i, v ->
            val country = v.locale.displayCountry.ifBlank { v.locale.language }
            val online = if (v.isNetworkConnectionRequired) " · online" else ""
            "Voz ${i + 1} ($country)$online"
        }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        val savedName = prefs.getString("voice", "") ?: ""
        val currentName = engine.voice?.name
        var selected = voices.indexOfFirst { it.name == savedName }
        if (selected < 0) selected = voices.indexOfFirst { it.name == currentName }
        if (selected < 0) selected = 0
        spinner.setSelection(selected)

        fun makeBar(value: Float): SeekBar {
            val bar = SeekBar(this@MainActivity)
            bar.max = 150
            bar.progress = ((value - 0.5f) * 100f).toInt().coerceIn(0, 150)
            return bar
        }
        val rateBar = makeBar(prefs.getFloat("rate", 1f))
        val pitchBar = makeBar(prefs.getFloat("pitch", 1f))

        val rateLabel = TextView(this).apply { text = "Velocidade (esquerda = lenta)" }
        val pitchLabel = TextView(this).apply { text = "Tom (esquerda = grave)" }
        rateLabel.setPadding(0, pad, 0, 0)
        pitchLabel.setPadding(0, pad, 0, 0)

        box.addView(spinner)
        box.addView(rateLabel)
        box.addView(rateBar)
        box.addView(pitchLabel)
        box.addView(pitchBar)

        AlertDialog.Builder(this)
            .setTitle("Voz do JARVIS")
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                val chosen = voices[spinner.selectedItemPosition]
                prefs.edit()
                    .putString("voice", chosen.name)
                    .putFloat("rate", 0.5f + rateBar.progress / 100f)
                    .putFloat("pitch", 0.5f + pitchBar.progress / 100f)
                    .apply()
                applyVoice()
                engine.speak("Olá, eu sou o JARVIS.", TextToSpeech.QUEUE_FLUSH, null, "jarvis")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun show(state: CoreState, label: String) {
        core.state = state
        status.text = label
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun onCoreTap() {
        when {
            busy -> return
            speaking -> {
                tts?.stop()
                speaking = false
                show(CoreState.IDLE, "ONLINE")
            }
            listening -> {
                recognizer?.cancel()
                listening = false
                show(CoreState.IDLE, "ONLINE")
            }
            hasMic() -> startListening()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            reply.text = "Reconhecimento de voz indisponível neste aparelho. Verifique se os Serviços de fala do Google estão instalados e ativados."
            return
        }
        tts?.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { rec ->
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) reply.text = "Você: $partial"
                }

                override fun onError(error: Int) {
                    listening = false
                    reply.text = "Não consegui ouvir (código $error). Toque no núcleo e tente de novo."
                    show(CoreState.IDLE, "ONLINE")
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        show(CoreState.IDLE, "ONLINE")
                    } else {
                        chat(text)
                    }
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        reply.text = ""
        show(CoreState.LISTENING, "ESCUTANDO")
        recognizer?.startListening(intent)
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
            show(CoreState.IDLE, "ONLINE")
            askSettings()
            return
        }
        busy = true
        tts?.stop()
        handler.removeCallbacksAndMessages(null)
        show(CoreState.PROCESSING, "PROCESSANDO")
        reply.text = "Você: $text"

        thread {
            var ok = false
            val result = try {
                val system = JSONObject()
                    .put("role", "system")
                    .put("content", "Você é o JARVIS, um assistente pessoal. Responda em português, de forma curta e direta, em no máximo três frases.")
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
                    ok = true
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
                busy = false
                reply.text = "Você: $text\n\nJARVIS: $result"
                if (ok && ttsReady && result.isNotBlank()) {
                    tts?.speak(result, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
                } else {
                    show(CoreState.DONE, "CONCLUÍDO")
                    handler.postDelayed({ show(CoreState.IDLE, "ONLINE") }, 1500)
                }
            }
        }
    }
}
