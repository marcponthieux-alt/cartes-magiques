package fr.cartes.multiplications

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { HOME, MEMO, GAME, END, REVISION, DAILY_END }

enum class Mode(val label: String, val sub: String) {
    REV("Révision", "Je regarde les cartes"),
    DEB("Débutant", "Je choisis la carte"),
    AV("Avancé", "Le jeu choisit"),
    EXP("Expert", "Une carte à la fois")
}

/** Pour + et × : R2V on part du résultat (recto), V2R on part de l'opération (verso). */
enum class Dir(val label: String, val sub: String) {
    R2V("Le résultat", "on trouve l'opération"),
    V2R("L'opération", "on trouve le résultat")
}

enum class Phase { PICK, IDLE, ASK, LISTEN, RIGHT, WRONG, PASSED }

/** Résultat d'une carte jouée. */
enum class Res { OK, KO, PASS }

/** Déroulement d'une partie (25 cartes ou entraînement du jour) et du mode révision. */
class Game(private val speaker: Speaker, val ear: Ear, val progress: Progress) {
    private val h = Handler(Looper.getMainLooper())
    private val ui = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(Screen.HOME)
    var op by mutableStateOf(Op.MUL)
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

    /** Entraînement du jour : une liste de cartes choisies par la répétition espacée. */
    var daily by mutableStateOf(false)
    var dailyList by mutableStateOf<List<Fact>>(emptyList())
    /** true : entraînement du jour ; false : série d'additions ou de soustractions par niveau. */
    var trainingDay by mutableStateOf(true)
    var seriesTitle by mutableStateOf("Entraînement du jour")

    /** + et − : niveau de la série (résultats jusqu'à 10, 20 ou 100). */
    var addLevel by mutableIntStateOf(10)

    var bannerOn by mutableStateOf(false)
    var bannerText by mutableStateOf("")
    var celebrateOn by mutableStateOf(false)
    var celebrateIcon by mutableStateOf("")

    // Mode révision
    var revOnly by mutableStateOf<Set<Int>?>(null)
    var revForBonus by mutableStateOf(false)
    var revAllBack by mutableStateOf(true)
    val revBack = mutableStateMapOf<Int, Boolean>()
    var revSel by mutableStateOf<Int?>(null)

    private var queue: List<Int> = (0..24).toList()
    private var streak = 0
    private var bgPaused = false
    private var listenStart = 0L

    val score: Int get() = done.values.count { it == Res.OK }
    val total: Int get() = targetsAll().size
    val missing: List<Int> get() = (0..24).filter { done[it] == Res.KO || done[it] == Res.PASS }
    val speakerMissingVoice: Boolean get() = speaker.missingVoice
    private val effMode: Mode get() = if (daily) Mode.AV else mode

    /** La carte jouée à l'emplacement i (0 à 24 dans un carré, ou rang dans l'entraînement du jour). */
    fun fact(i: Int): Fact = if (daily) dailyList[i] else Data.fact(op, quad, i)

    /** + et − : on part toujours de l'opération. × : au choix. */
    fun dirOf(f: Fact): Dir = if (f.op.univ == Univ.ADD) Dir.V2R else dir

    /** + et × en partant de l'opération : la question est sur la face « opérations » (verso). */
    fun questionIsBack(f: Fact): Boolean = !f.op.inverse && dirOf(f) == Dir.V2R

    private fun targetsAll(): List<Int> = if (daily) dailyList.indices.toList() else (0..24).toList()
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

    fun selectOp(o: Op) { op = o }

    fun goHome() {
        cancelAll(); ui.removeCallbacksAndMessages(null)
        bannerOn = false; celebrateOn = false; gamePaused = false
        daily = false
        screen = Screen.HOME; sel = null
    }

    fun openQuad(q: Int) {
        cancelAll()
        daily = false; quad = q; bonus = false; done.clear()
        // + et − : le tableau sert à la révision des cartes 1 + 1 à 10 + 10.
        if (mode == Mode.REV || op.univ == Univ.ADD) { openRevision(null, false); return }
        hint = false; screen = Screen.MEMO
        later(5000) { hint = true }
    }

    fun replay() = openQuad(quad)

    fun flip() { if (screen == Screen.MEMO) startGame() }

    /* ---------- Révision ---------- */

    private fun openRevision(only: Set<Int>?, forBonus: Boolean) {
        cancelAll()
        revOnly = only; revForBonus = forBonus
        // + et × : face « opérations » d'abord. − et ÷ : face « question » d'abord.
        revAllBack = !op.inverse
        revBack.clear(); revSel = null
        screen = Screen.REVISION
    }

    fun revShowsBack(i: Int): Boolean = revBack[i] ?: revAllBack

    fun revToggleAll() { revAllBack = !revAllBack; revBack.clear() }

    fun revTap(i: Int) {
        val only = revOnly
        if (only != null && i !in only) return
        revBack[i] = !revShowsBack(i)
        revSel = i
    }

    /** Fin de partie : révision des cartes ratées ou passées, avant la partie bonus. */
    fun startBonusRevision() = openRevision(missing.toSet(), true)

    /* ---------- Partie ---------- */

    private fun startGame() {
        done.clear(); targets = (0..24).toList(); bonus = false; daily = false
        begin()
    }

    fun startBonus() {
        val m = missing
        if (m.isEmpty()) { screen = Screen.END; return }
        m.forEach { done.remove(it) }
        targets = m; bonus = true
        begin()
    }

    /** Entraînement du jour : quelques cartes choisies par la répétition espacée. */
    fun startDaily() {
        trainingDay = true; seriesTitle = "Entraînement du jour"
        startSeries(progress.dailyPlan(op))
    }

    /** + et − : série de 15 calculs du niveau choisi. */
    fun startLevel() {
        trainingDay = false; seriesTitle = "${op.label} jusqu'à $addLevel"
        startSeries(Series.additive(op, addLevel, progress))
    }

    private fun startSeries(list: List<Fact>) {
        cancelAll()
        dailyList = list
        daily = true; bonus = false
        done.clear(); targets = dailyList.indices.toList()
        begin()
    }

    private fun begin() {
        cancelAll()
        queue = when {
            daily -> targets
            effMode == Mode.DEB -> targets
            // Les cartes les plus fragiles d'abord, quand l'attention est la meilleure.
            else -> targets.shuffled().sortedBy { progress.box(fact(it)) }
        }
        sel = null; said = null; revealed = false; lastEarn = null; streak = 0; gamePaused = false
        phase = if (effMode == Mode.DEB) Phase.PICK else Phase.IDLE
        intro = !daily && !bonus && questionIsBack(fact(0))
        screen = Screen.GAME
        if (intro) later(450) { intro = false }
        if (effMode != Mode.DEB) later(1300) { nextInQueue()?.let { ask(it) } }
    }

    private fun nextInQueue(): Int? = queue.firstOrNull { done[it] == null }

    fun pick(i: Int) {
        if (screen == Screen.GAME && effMode == Mode.DEB && phase == Phase.PICK && !gamePaused &&
            i in targets && done[i] == null
        ) ask(i)
    }

    private fun ask(i: Int) {
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
        sel = i; phase = Phase.ASK; said = null; revealed = false
        val f = fact(i)
        speaker.pitch = if (f.op.univ == Univ.ADD) 1.12f else 1.0f
        Earcon.play(f.op)
        later(Earcon.durationMs(f.op)) {
            speaker.speak(Speech.question(f, dirOf(f))) { listen() }
        }
    }

    fun repeatQuestion() { val i = sel ?: return; if (phase == Phase.LISTEN && !gamePaused) ask(i) }

    private fun listen() {
        if (screen != Screen.GAME || phase != Phase.ASK || gamePaused) return
        phase = Phase.LISTEN
        listenStart = SystemClock.elapsedRealtime()
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
        val r = Answer.check(text, fact(i), dirOf(fact(i)))
        reply(r.ok, r.said, SystemClock.elapsedRealtime() - listenStart < 6000)
    }

    /** Boutons de secours quand le micro n'est pas disponible. */
    fun manual(ok: Boolean) {
        if (phase != Phase.LISTEN || gamePaused) return
        h.removeCallbacksAndMessages(null); ear.stop()
        reply(ok, null, true)
    }

    private fun reply(ok: Boolean, saidText: String?, fast: Boolean) {
        val i = sel ?: return
        val f = fact(i)
        progress.record(f, ok, fast)
        if (ok) {
            done[i] = Res.OK; lastEarn = i; phase = Phase.RIGHT; streak++
            val cheer = if (bonus) null else Cheers.after(true, streak, doneCount(), targets.size)
            val head = if (bonus) "Bravo. Erreur corrigée !. " else "Bravo. "
            if (bonus) showCelebrate(Data.icon(f.op.univ, f.v))
            cheer?.let { showBanner(it) }
            speaker.speak(head + Speech.answer(f) + (cheer?.let { " $it" } ?: "")) {
                later(500) { next() }
            }
        } else {
            done[i] = Res.KO; said = saidText; revealed = true; phase = Phase.WRONG; streak = 0
            val cheer = Cheers.after(false, streak, doneCount(), targets.size)
            cheer?.let { showBanner(it) }
            speaker.speak(Speech.wrong(f, dirOf(f)) + (cheer?.let { " $it" } ?: "")) {
                later(900) { revealed = false; later(700) { next() } }
            }
        }
    }

    /** « Je passe » : la bonne réponse est montrée et lue, puis on continue. */
    fun pass() {
        val i = sel ?: return
        if (screen != Screen.GAME || gamePaused || (phase != Phase.ASK && phase != Phase.LISTEN)) return
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
        val f = fact(i)
        progress.record(f, false, false)
        done[i] = Res.PASS; streak = 0; said = null; revealed = true; phase = Phase.PASSED
        speaker.speak(Speech.answer(f)) {
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
        if (effMode == Mode.DEB) { sel = null; phase = Phase.PICK; revealed = false; return }
        nextInQueue()?.let { ask(it) }
    }

    private fun finish() {
        sel = null
        val s = score
        speaker.pitch = 1.0f
        if (daily) {
            screen = Screen.DAILY_END
            speaker.speak(
                if (trainingDay) "Entraînement terminé !. Tu as trouvé $s cartes sur ${dailyList.size}.. Reviens demain, ton cerveau range tes cartes pendant la nuit."
                else "Série terminée !. Tu as trouvé $s calculs sur ${dailyList.size}."
            ) {}
            return
        }
        screen = Screen.END
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
