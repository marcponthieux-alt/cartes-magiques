package fr.cartes.multiplications

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { HOME, MEMO, GAME, END }

enum class Mode(val label: String, val sub: String) {
    DEB("Débutant", "Je choisis la carte"),
    AV("Avancé", "Le jeu choisit"),
    EXP("Expert", "Une carte à la fois")
}

/** R2V : on part du résultat (recto). V2R : on part de la multiplication (verso). */
enum class Dir(val label: String, val sub: String) {
    R2V("Le résultat", "24 → 3×8"),
    V2R("La multiplication", "3×8 → 24")
}

enum class Phase { PICK, IDLE, ASK, LISTEN, RIGHT, WRONG }

/** Déroulement d'une partie. */
class Game(private val speaker: Speaker, val ear: Ear) {
    private val h = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(Screen.HOME)
    var quad by mutableIntStateOf(0)
    var mode by mutableStateOf(Mode.DEB)
    var dir by mutableStateOf(Dir.R2V)
    var hint by mutableStateOf(false)
    /** Carte -> true (trouvée) / false (ratée). Absente = pas encore jouée. */
    val done = mutableStateMapOf<Int, Boolean>()
    var sel by mutableStateOf<Int?>(null)
    var phase by mutableStateOf(Phase.PICK)
    var intro by mutableStateOf(false)
    var said by mutableStateOf<String?>(null)
    var revealed by mutableStateOf(false)
    var lastEarn by mutableStateOf<Int?>(null)

    private var queue: List<Int> = (0..24).toList()
    private var paused = false

    val score: Int get() = done.values.count { it }
    val speakerMissingVoice: Boolean get() = speaker.missingVoice

    private fun later(ms: Long, f: () -> Unit) { h.postDelayed({ f() }, ms) }
    private fun cancelAll() { h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop() }

    fun goHome() { cancelAll(); screen = Screen.HOME; sel = null }

    fun openQuad(q: Int) {
        cancelAll()
        quad = q; hint = false; screen = Screen.MEMO
        later(5000) { hint = true }
    }

    fun flip() { if (screen == Screen.MEMO) startGame() }

    private fun startGame() {
        cancelAll()
        queue = if (mode == Mode.DEB) (0..24).toList() else (0..24).shuffled()
        done.clear(); sel = null; said = null; revealed = false; lastEarn = null
        phase = if (mode == Mode.DEB) Phase.PICK else Phase.IDLE
        intro = dir == Dir.V2R
        screen = Screen.GAME
        if (intro) later(450) { intro = false }
        if (mode != Mode.DEB) later(1500) { ask(queue.first()) }
    }

    fun pick(i: Int) {
        if (screen == Screen.GAME && mode == Mode.DEB && phase == Phase.PICK && done[i] == null) ask(i)
    }

    private fun ask(i: Int) {
        h.removeCallbacksAndMessages(null); ear.stop()
        sel = i; phase = Phase.ASK; said = null; revealed = false
        speaker.speak(Speech.question(dir, quad, i)) { listen() }
    }

    fun repeatQuestion() { val i = sel ?: return; if (phase == Phase.LISTEN) ask(i) }

    private fun listen() {
        if (screen != Screen.GAME || phase != Phase.ASK) return
        phase = Phase.LISTEN
        if (!ear.usable) return // les boutons de secours s'affichent
        ear.listen { text -> onHeard(text) }
        // Pas de réponse : on repose la question.
        later(12000) { if (phase == Phase.LISTEN) { ear.stop(); sel?.let { ask(it) } } }
    }

    private fun onHeard(text: String) {
        val i = sel ?: return
        if (phase != Phase.LISTEN) return
        h.removeCallbacksAndMessages(null)
        val r = Answer.check(text, Data.value(quad, i), dir)
        reply(r.ok, r.said)
    }

    /** Boutons de secours quand le micro n'est pas disponible. */
    fun manual(ok: Boolean) {
        if (phase != Phase.LISTEN) return
        h.removeCallbacksAndMessages(null); ear.stop()
        reply(ok, null)
    }

    private fun reply(ok: Boolean, saidText: String?) {
        val i = sel ?: return
        val v = Data.value(quad, i)
        if (ok) {
            done[i] = true; lastEarn = i; phase = Phase.RIGHT
            speaker.speak(Speech.right(v)) { later(500) { next() } }
        } else {
            done[i] = false; said = saidText; revealed = true; phase = Phase.WRONG
            speaker.speak(Speech.wrong(dir, quad, i)) {
                later(900) { revealed = false; later(700) { next() } }
            }
        }
    }

    private fun next() {
        if (screen != Screen.GAME) return
        if (done.size >= 25) { sel = null; screen = Screen.END; return }
        if (mode == Mode.DEB) { sel = null; phase = Phase.PICK; revealed = false; return }
        queue.firstOrNull { done[it] == null }?.let { ask(it) }
    }

    /** L'application passe en arrière-plan. */
    fun pause() {
        paused = true
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
    }

    /** Retour dans l'application : on reprend là où on en était. */
    fun resume() {
        if (!paused) return
        paused = false
        when (screen) {
            Screen.MEMO -> hint = true
            Screen.GAME -> when (phase) {
                Phase.ASK, Phase.LISTEN -> sel?.let { ask(it) }
                Phase.RIGHT, Phase.WRONG -> { revealed = false; next() }
                Phase.IDLE -> queue.firstOrNull { done[it] == null }?.let { ask(it) }
                Phase.PICK -> {}
            }
            else -> {}
        }
    }

    fun release() { cancelAll(); speaker.release() }
}
