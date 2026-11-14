package fr.cartes.multiplications

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.util.Locale

/**
 * Haut-parleur : synthèse vocale d'Android, en français, hors connexion
 * si la voix française est installée sur le téléphone.
 * Chaque point « . » du texte devient un temps de silence.
 */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    companion object { const val PAUSE_MS = 350L }

    private val h = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var initDone = false
    private var ready = false
    private var gen = 0
    private var pending: Pair<String, () -> Unit>? = null
    private val callbacks = HashMap<String, () -> Unit>()

    var missingVoice by mutableStateOf(false)
        private set

    /** Hauteur de la voix : un peu plus aiguë dans l'univers des additions et soustractions. */
    var pitch = 1.0f

    override fun onInit(status: Int) {
        initDone = true
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.FRANCE)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.9f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { fire(utteranceId) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { fire(utteranceId) }
                override fun onError(utteranceId: String?, errorCode: Int) { fire(utteranceId) }
            })
        }
        missingVoice = !ready
        pending?.let { pending = null; speak(it.first, it.second) }
    }

    private fun fire(id: String?) {
        if (id == null) return
        h.post { callbacks.remove(id)?.invoke() }
    }

    fun speak(text: String, onDone: () -> Unit) {
        gen++
        val g = gen
        val cb: () -> Unit = { if (g == gen) onDone() }
        if (!initDone) { pending = text to onDone; return }
        if (!ready) { h.postDelayed({ cb() }, 1200); return }

        // Découpe : morceaux de texte et silences (nombre de points).
        val parts = mutableListOf<Pair<String, Int>>()
        var last = 0
        for (m in Regex("\\.+").findAll(text)) {
            parts += text.substring(last, m.range.first).trim() to m.value.length
            last = m.range.last + 1
        }
        val tail = text.substring(last).trim()
        if (tail.isNotEmpty()) parts += tail to 0

        callbacks.clear()
        tts.setPitch(pitch)
        var first = true
        var lastId: String? = null
        var k = 0
        for ((chunk, dots) in parts) {
            if (chunk.isNotEmpty()) {
                val id = "u$g-${k++}"
                tts.speak(chunk, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
                first = false; lastId = id
            }
            if (dots > 0) {
                val id = "u$g-${k++}"
                tts.playSilentUtterance(dots * PAUSE_MS, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, id)
                first = false; lastId = id
            }
        }
        if (lastId == null) h.post { cb() } else callbacks[lastId] = cb
    }

    fun stop() {
        gen++
        callbacks.clear()
        h.removeCallbacksAndMessages(null)
        if (ready) tts.stop()
    }

    fun release() { stop(); tts.shutdown() }
}

/**
 * Micro : reconnaissance vocale Vosk, entièrement sur le téléphone
 * (modèle français inclus dans l'application, aucune connexion nécessaire).
 */
class Ear(ctx: Context) : RecognitionListener {
    var ready by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var micAllowed by mutableStateOf(false)

    val usable: Boolean get() = ready && micAllowed && !failed

    private var model: Model? = null
    private var service: SpeechService? = null
    private var rec: Recognizer? = null
    private var cb: ((String) -> Unit)? = null

    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(
            ctx.applicationContext, "model-fr", "model",
            { m: Model -> model = m; ready = true },
            { _: java.io.IOException -> failed = true }
        )
    }

    fun listen(onText: (String) -> Unit) {
        val m = model ?: return
        stop()
        try {
            val r = Recognizer(m, 16000f, Answer.GRAMMAR)
            val s = SpeechService(r, 16000f)
            rec = r; service = s; cb = onText
            s.startListening(this)
        } catch (e: Exception) {
            failed = true
        }
    }

    fun stop() {
        cb = null
        service?.let { it.stop(); it.shutdown() }
        service = null
        rec?.close()
        rec = null
    }

    private fun handle(hyp: String?) {
        val c = cb ?: return
        val text = try { JSONObject(hyp ?: "").optString("text") } catch (e: Exception) { "" }
        val clean = text.replace("[unk]", "").trim()
        if (clean.isEmpty()) return
        stop()
        c(clean)
    }

    override fun onResult(hypothesis: String?) = handle(hypothesis)
    override fun onFinalResult(hypothesis: String?) = handle(hypothesis)
    override fun onPartialResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) { failed = true }
    override fun onTimeout() {}
}
