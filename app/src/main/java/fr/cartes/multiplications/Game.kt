package fr.cartes.multiplications

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { MAIN, HOME, MEMO, GAME, END, REVISION, DAILY_END, SUMS, ORI, DICTEE }

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
class Game(private val speaker: Speaker, val ear: Ear, val progress: Progress, val photos: PhotoBank, words: WordStore) {
    private val h = Handler(Looper.getMainLooper())
    private val ui = Handler(Looper.getMainLooper())

    /** Module course d'orientation (symboles des cartes). */
    val ori = OriGame(progress, speaker, photos)

    fun openOri() { cancelAll(); ori.back(); screen = Screen.ORI }

    /** Module orthographe (dictée de mots). */
    val dictee = DicteeGame(progress, speaker, words)

    fun openDictee() { cancelAll(); dictee.back(); screen = Screen.DICTEE }
    fun openMath() { goHome() }

    /** Menu principal : mathématiques, course d'orientation, orthographe. */
    fun goMenu() {
        cancelAll(); ui.removeCallbacksAndMessages(null)
        bannerOn = false; celebrateOn = false; gamePaused = false; daily = false
        screen = Screen.MAIN; sel = null
    }

    var screen by mutableStateOf(Screen.MAIN)
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
    var celebrateTitle by mutableStateOf("Erreur corrigée !")
    var celebrateSub by mutableStateOf("+1 bon point récupéré")

    /* ----- Les sommes égales ----- */
    var sumTarget by mutableIntStateOf(9)
    /** (a, b) -> true : trouvée par l'enfant ; false : révélée (indice refusé ou « je passe »). */
    val sumFound = mutableStateMapOf<Pair<Int, Int>, Boolean>()
    var sumHint by mutableStateOf<Pair<Int, Int>?>(null)
    var sumFlash by mutableStateOf<Pair<Int, Int>?>(null)
    var sumMsg by mutableStateOf("")
    var sumListening by mutableStateOf(false)
    var sumDone by mutableStateOf(false)
    var sumStars by mutableIntStateOf(0)
    private var sumHints = 0
    private var sumErrors = 0
    private var sumStart = 0L

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

    private fun showCelebrate(icon: String, title: String = "Erreur corrigée !", sub: String = "+1 bon point récupéré") {
        celebrateIcon = icon; celebrateTitle = title; celebrateSub = sub; celebrateOn = true
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
            Answer.Command.HINT -> { ask(i); return }
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
        if (screen != Screen.GAME && screen != Screen.SUMS) return
        gamePaused = true
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
    }

    fun resumeGame() {
        if (!gamePaused) return
        gamePaused = false
        if (screen == Screen.SUMS) { sumListening = false; sumListen(); return }
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
        if (screen == Screen.GAME || screen == Screen.SUMS) pauseGame()
        else { h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop() }
    }

    /** Retour dans l'application : la partie reste en pause jusqu'à « Reprendre ». */
    fun resume() {
        if (!bgPaused) return
        bgPaused = false
        if (screen == Screen.MEMO) hint = true
    }

    /* ---------- Les sommes égales ---------- */

    /** Toutes les additions a + b = n avec a et b de 0 à 9. */
    fun sumPairs(n: Int): List<Pair<Int, Int>> =
        (0..9).flatMap { a -> (0..9).filter { b -> a + b == n }.map { b -> a to b } }

    /** Lignes de la fiche : (a, b) avec a ≥ b, du plus grand a au plus petit ; le jumeau (b, a) est à droite. */
    fun sumRows(n: Int): List<Pair<Int, Int>> =
        sumPairs(n).filter { it.first >= it.second }.sortedByDescending { it.first }

    val sumTotal: Int get() = sumPairs(sumTarget).size
    val sumCount: Int get() = sumFound.size

    fun openSums() = startSums(progress.nextSum())

    fun startSums(n: Int) {
        cancelAll(); ui.removeCallbacksAndMessages(null)
        bannerOn = false; celebrateOn = false; gamePaused = false; daily = false
        sumTarget = n; sumFound.clear(); sumHint = null; sumFlash = null
        sumDone = false; sumStars = 0; sumHints = 0; sumErrors = 0; sumListening = false
        sumStart = SystemClock.elapsedRealtime()
        sumMsg = "Trouve toutes les additions qui font $n."
        screen = Screen.SUMS
        speaker.pitch = 1.12f
        Earcon.play(Op.ADD)
        later(Earcon.durationMs(Op.ADD)) {
            speaker.speak("Les sommes égales à $n ?") { sumListen() }
        }
    }

    private fun sumListen() {
        if (screen != Screen.SUMS || sumDone || gamePaused) return
        sumListening = true
        if (!ear.usable) return // les cases se touchent pour être révélées (adulte)
        ear.listen { t -> sumHeard(t) }
        // Pas de réponse pendant 15 secondes : un indice, dans l'ordre de la fiche.
        later(15000) { if (sumListening && !sumDone && !gamePaused) { ear.stop(); sumGiveHint() } }
    }

    private fun sumHeard(text: String) {
        if (screen != Screen.SUMS || sumDone || gamePaused) return
        h.removeCallbacksAndMessages(null)
        sumListening = false
        when (Answer.command(text)) {
            Answer.Command.PAUSE -> { pauseGame(); return }
            Answer.Command.PASS -> { sumGiveUp(); return }
            Answer.Command.HINT -> { sumGiveHint(); return }
            null -> {}
        }
        val n = sumTarget
        val pairs = Answer.additions(text)
        if (pairs.isEmpty()) {
            sumMsg = "Dis une addition, par exemple « 4 plus 5 »."
            speaker.speak("Dis une addition.") { sumListen() }
            return
        }
        val talk = StringBuilder()
        var anyNew = false
        for ((a, b) in pairs) {
            when {
                a + b != n || a !in 0..9 || b !in 0..9 -> {
                    sumErrors++
                    sumMsg = "$a + $b = ${a + b}, ce n'est pas $n."
                    talk.append("$a plus $b.. égale ${a + b}... ")
                }
                sumFound.containsKey(a to b) -> {
                    sumFlash = a to b
                    sumMsg = "$a + $b : déjà trouvée !"
                    talk.append("Déjà trouvée... ")
                }
                else -> {
                    anyNew = true
                    sumFound[a to b] = true
                    sumFlash = a to b
                    if (a != b && !sumFound.containsKey(b to a)) {
                        sumFound[b to a] = true
                        talk.append("Oui !. $a plus $b.. et son jumeau, $b plus $a... ")
                    } else {
                        talk.append("Oui !. $a plus $b... ")
                    }
                    if (sumHint == (a to b) || sumHint == (b to a)) sumHint = null
                }
            }
        }
        val left = sumTotal - sumCount
        if (anyNew) sumMsg = if (left > 0) "Bravo ! Encore $left à trouver." else "Bravo !"
        if (left == 0) { speaker.speak(talk.toString()) { sumFinish() }; return }
        speaker.speak(talk.toString()) { sumListen() }
    }

    /** Indice : la première somme manquante dans l'ordre de la fiche (9 + …, puis 8 + …). */
    fun sumGiveHint() {
        if (screen != Screen.SUMS || sumDone) return
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
        val next = sumRows(sumTarget).firstOrNull { !sumFound.containsKey(it) } ?: return
        sumHints++
        sumHint = next
        sumMsg = "Indice : ${next.first} + ? = $sumTarget"
        sumListening = false
        speaker.speak("${next.first} plus combien ?") { sumListen() }
    }

    /** « Je passe » ou « Fini » : les sommes manquantes s'affichent en gris. */
    fun sumGiveUp() {
        if (screen != Screen.SUMS || sumDone) return
        h.removeCallbacksAndMessages(null); ear.stop(); speaker.stop()
        sumPairs(sumTarget).forEach { if (!sumFound.containsKey(it)) sumFound[it] = false }
        sumHint = null
        sumFinish()
    }

    /** Sans micro : un adulte touche une case pour la révéler. */
    fun sumReveal(pair: Pair<Int, Int>) {
        if (screen != Screen.SUMS || sumDone || ear.usable || sumFound.containsKey(pair)) return
        sumFound[pair] = true
        val twin = pair.second to pair.first
        if (!sumFound.containsKey(twin)) sumFound[twin] = true
        if (sumCount == sumTotal) sumFinish()
    }

    private fun sumFinish() {
        sumDone = true; sumListening = false
        val n = sumTarget
        val revealed = sumFound.values.count { !it }
        sumStars = when {
            revealed > 0 -> if (sumCount - revealed > 0) 1 else 0
            sumHints == 0 && sumErrors <= 1 -> 3
            sumHints <= 1 -> 2
            else -> 1
        }
        val elapsed = SystemClock.elapsedRealtime() - sumStart
        progress.recordSum(n, sumStars, elapsed < sumTotal * 9000L)
        sumMsg = if (revealed > 0) "Les sommes en gris sont à retenir. On rejoue bientôt !"
        else "Tu as trouvé les $sumTotal sommes égales à $n !"
        if (revealed == 0) {
            showCelebrate("⭐".repeat(maxOf(1, sumStars)), "Les $sumTotal sommes égales à $n !",
                if (sumStars == 3) "Sans aide, bravo !" else "Bravo, tu les as toutes trouvées !")
            speaker.speak("Bravo !. Tu as trouvé les $sumTotal sommes égales à $n !") {}
        } else {
            speaker.speak("Voici toutes les sommes égales à $n.") {}
        }
    }

    fun sumNext() = startSums(progress.nextSum().let { if (it == sumTarget && sumStars >= 2) (it % 18) + 1 else it }.coerceIn(4, 18))

    fun release() { cancelAll(); ui.removeCallbacksAndMessages(null); speaker.release() }
}
