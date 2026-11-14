package fr.cartes.multiplications

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { HOME, MEMO, GAME, END, REVISION }

enum class Mode(val label: String, val sub: String) {
    REV("Révision", "Je regarde les cartes"),
    DEB("Débutant", "Je choisis la carte"),
    AV("Avancé", "Le jeu choisit"),
    EXP("Expert", "Une carte à la fois")
}

/** R2V : on part du résultat (recto). V2R : on part de la multiplication (verso). */
enum class Dir(val label: String, val sub: String) {
    R2V("Le résultat", "24 → 3×8"),
    V2R("La multiplication", "3×8 → 24")
}

enum class Phase { PICK, IDLE, ASK, LISTEN, RIGHT, WRONG, PASSED }

/** Résultat d'une carte jouée. */
enum class Res { OK, KO, PASS }

/** Déroulement d'une partie et du mode révision. */
class Game(private val speaker: Speaker, val ear: Ear) {
    private val h = Handler(Looper.getMainLooper())
    private val ui = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(Screen.HOME)
    var quad by mutableIntStateOf(0)
    var mode by mutableStateOf(Mode.DEB)
    var dir by mutableStateOf(Dir.V2R)
    var hint by mutableStateOf(false)
    val done = mutableStateMapOf<Int, Res>()
    var sel by mutableStateOf<Int?>(null)
    var phase by mutableStateOf(Phase.PICK)
    var intro by mutableStateOf(false)
    var said by mutableStateOf<String?>(null)
    var revealed by mutableStateOf(false)
    var lastEarn by mutableStateOf<Int?>(null)

    /** Partie bonus : seules les cartes ratées ou passées sont rejouées. */
    var bonus by mutableStateOf(false)
    var targets by mutableStateOf((0..24).toList())
    var gamePaused by mutableStateOf(false)

    var bannerOn by mutableStateOf(false)
    var bannerText by mutableStateOf("")
    var celebrateOn by mutableStateOf(false)
    var celebrateIcon by mutableStateOf("")

    // Mode révision
    var revOnly by mutableStateOf<Set<Int>?>(null)
    var revForBonus by mutableStateOf(false)
    var revAllMult by mutableStateOf(true)
    val revMult = mutableStateMapOf<Int, Boolean>()
    var revSel by mutableStateOf<Int?>(null)

    private var queue: List<Int> = (0..24).toList()
    private var streak = 0
    private var bgPaused = false

    val score: Int get() = done.values.count { it == Res.OK }
    val missing: List<Int> get() = (0..24).filter { done[it] == Res.KO || done[it] == Res.PASS }
    val speakerMissingVoice: Boolean get() = speaker.missingVoice

    private fun later(ms: Long, f: () -> Unit) { h.postDelayed({ f() }, ms) }
    private fun cancelAll() { h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop() }
    private fun doneCount() = targets.count { done[it] != null }

    private fun showBanner(t: String) {
        bannerText = t; bannerOn = true
        ui.postDelayed({ if (bannerText == t) bannerOn = false }, 2600)
    }

    private fun showCelebrate(icon: String) {
        celebrateIcon = icon; celebrateOn = true
        ui.postDelayed({ celebrateOn = false }, 1900)
    }

    /* ---------- Navigation ---------- */

    fun goHome() {
        cancelAll(); ui.removeCallbacksAndMessages(null)
        bannerOn = false; celebrateOn = false; gamePaused = false
        screen = Screen.HOME; sel = null
    }

    fun openQuad(q: Int) {
        cancelAll()
        quad = q; bonus = false; done.clear()
        if (mode == Mode.REV) { openRevision(null, false); return }
        hint = false; screen = Screen.MEMO
        later(5000) { hint = true }
    }

    fun replay() = openQuad(quad)

    fun flip() { if (screen == Screen.MEMO) startGame() }

    /* ---------- Révision ---------- */

    private fun openRevision(only: Set<Int>?, forBonus: Boolean) {
        cancelAll()
        revOnly = only; revForBonus = forBonus
        revAllMult = true; revMult.clear(); revSel = null
        screen = Screen.REVISION
    }

    fun revShowsMult(i: Int): Boolean = revMult[i] ?: revAllMult

    fun revToggleAll() { revAllMult = !revAllMult; revMult.clear() }

    fun revTap(i: Int) {
        val only = revOnly
        if (only != null && i !in only) return
        revMult[i] = !revShowsMult(i)
        revSel = i
    }

    /** Fin de partie : révision des cartes ratées ou passées, avant la partie bonus. */
    fun startBonusRevision() = openRevision(missing.toSet(), true)

    /* ---------- Partie ---------- */

    private fun startGame() {
        done.clear(); targets = (0..24).toList(); bonus = false
        begin()
    }

    fun startBonus() {
        val m = missing
        if (m.isEmpty()) { screen = Screen.END; return }
        m.forEach { done.remove(it) }
        targets = m; bonus = true
        begin()
    }

    private fun begin() {
        cancelAll()
        queue = if (mode == Mode.DEB) targets else targets.shuffled()
        sel = null; said = null; revealed = false; lastEarn = null; streak = 0; gamePaused = false
        phase = if (mode == Mode.DEB) Phase.PICK else Phase.IDLE
        intro = dir == Dir.V2R && !bonus
        screen = Screen.GAME
        if (intro) later(450) { intro = false }
        if (mode != Mode.DEB) later(1500) { nextInQueue()?.let { ask(it) } }
    }

    private fun nextInQueue(): Int? = queue.firstOrNull { done[it] == null }

    fun pick(i: Int) {
        if (screen == Screen.GAME && mode == Mode.DEB && phase == Phase.PICK && !gamePaused &&
            i in targets && done[i] == null
        ) ask(i)
    }

    private fun ask(i: Int) {
        h.removeCallbacksAndMessages(null); ear.stop()
        sel = i; phase = Phase.ASK; said = null; revealed = false
        speaker.speak(Speech.question(dir, quad, i)) { listen() }
    }

    fun repeatQuestion() { val i = sel ?: return; if (phase == Phase.LISTEN && !gamePaused) ask(i) }

    private fun listen() {
        if (screen != Screen.GAME || phase != Phase.ASK || gamePaused) return
        phase = Phase.LISTEN
        if (!ear.usable) return // les boutons de secours s'affichent
        ear.listen { text -> onHeard(text) }
        // Pas de réponse : on repose la question.
        later(12000) { if (phase == Phase.LISTEN) { ear.stop(); sel?.let { ask(it) } } }
    }

    private fun onHeard(text: String) {
        val i = sel ?: return
        if (phase != Phase.LISTEN || gamePaused) return
        h.removeCallbacksAndMessages(null)
        when (Answer.command(text)) {
            Answer.Command.PAUSE -> { pauseGame(); return }
            Answer.Command.PASS -> { pass(); return }
            null -> {}
        }
        val r = Answer.check(text, Data.value(quad, i), dir)
        reply(r.ok, r.said)
    }

    /** Boutons de secours quand le micro n'est pas disponible. */
    fun manual(ok: Boolean) {
        if (phase != Phase.LISTEN || gamePaused) return
        h.removeCallbacksAndMessages(null); ear.stop()
        reply(ok, null)
    }

    private fun reply(ok: Boolean, saidText: String?) {
        val i = sel ?: return
        val v = Data.value(quad, i)
        if (ok) {
            done[i] = Res.OK; lastEarn = i; phase = Phase.RIGHT; streak++
            val cheer = if (bonus) null else Cheers.after(true, streak, doneCount(), targets.size)
            val head = if (bonus) "Bravo. Erreur corrigée !. " else "Bravo. "
            if (bonus) showCelebrate(Data.icon(v))
            cheer?.let { showBanner(it) }
            speaker.speak(head + Speech.answer(quad, i) + (cheer?.let { " $it" } ?: "")) {
                later(500) { next() }
            }
        } else {
            done[i] = Res.KO; said = saidText; revealed = true; phase = Phase.WRONG; streak = 0
            val cheer = Cheers.after(false, streak, doneCount(), targets.size)
            cheer?.let { showBanner(it) }
            speaker.speak(Speech.wrong(dir, quad, i) + (cheer?.let { " $it" } ?: "")) {
                later(900) { revealed = false; later(700) { next() } }
            }
        }
    }

    /** « Je passe » : la bonne réponse est montrée et lue, puis on continue. */
    fun pass() {
        val i = sel ?: return
        if (screen != Screen.GAME || gamePaused || (phase != Phase.ASK && phase != Phase.LISTEN)) return
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
        done[i] = Res.PASS; streak = 0; said = null; revealed = true; phase = Phase.PASSED
        speaker.speak(Speech.answer(quad, i)) {
            later(900) { revealed = false; later(700) { next() } }
        }
    }

    fun pauseGame() {
        if (screen != Screen.GAME) return
        gamePaused = true
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
    }

    fun resumeGame() {
        if (!gamePaused) return
        gamePaused = false
        when (phase) {
            Phase.ASK, Phase.LISTEN -> sel?.let { ask(it) }
            Phase.RIGHT, Phase.WRONG, Phase.PASSED -> { revealed = false; next() }
            Phase.IDLE -> nextInQueue()?.let { ask(it) }
            Phase.PICK -> {}
        }
    }

    private fun next() {
        if (screen != Screen.GAME || gamePaused) return
        if (targets.all { done[it] != null }) { finish(); return }
        if (mode == Mode.DEB) { sel = null; phase = Phase.PICK; revealed = false; return }
        nextInQueue()?.let { ask(it) }
    }

    private fun finish() {
        sel = null; screen = Screen.END
        val s = score
        val text = when {
            s == 25 && bonus -> "Bravo !. Tu as récupéré tous tes bons points !"
            s == 25 -> "Parfait !. Vingt-cinq bons points sur vingt-cinq !"
            else -> "Partie terminée.. Tu as gagné $s bons points.. Avec le super bonus, tu peux gagner les bons points qui manquent !"
        }
        speaker.speak(text) {}
    }

    /* ---------- Vie de l'application ---------- */

    /** L'application passe en arrière-plan. */
    fun pause() {
        bgPaused = true
        if (screen == Screen.GAME) pauseGame()
        else { h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop() }
    }

    /** Retour dans l'application : la partie reste en pause jusqu'à « Reprendre ». */
    fun resume() {
        if (!bgPaused) return
        bgPaused = false
        if (screen == Screen.MEMO) hint = true
    }

    fun release() { cancelAll(); ui.removeCallbacksAndMessages(null); speaker.release() }
}
