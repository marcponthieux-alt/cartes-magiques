package fr.cartes.multiplications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Mémoire des progrès, carte par carte et opération par opération (boîtes de Leitner).
 *
 * Boîte 0 : jamais vue. Une erreur renvoie en boîte 1. Une bonne réponse rapide fait monter
 * d'une boîte ; une bonne réponse lente garde la même boîte (la carte n'est pas encore automatisée).
 * La carte revient ensuite après 0, 1, 3, 7 puis 14 jours : la révision espacée consolide la mémoire.
 */
class Progress(ctx: Context) {
    private val p = ctx.getSharedPreferences("progres", Context.MODE_PRIVATE)

    /** Change à chaque enregistrement, pour rafraîchir l'affichage. */
    var version by mutableIntStateOf(0)
        private set

    private fun today(): Long = System.currentTimeMillis() / 86_400_000L

    fun box(f: Fact): Int = boxKey(f.key)
    private fun due(f: Fact): Long = dueKey(f.key)
    private fun boxKey(k: String): Int = p.getInt("b_$k", 0)
    private fun dueKey(k: String): Long = p.getLong("d_$k", 0L)

    fun record(f: Fact, ok: Boolean, fast: Boolean) {
        if (f.a > 10 || f.b > 10) return // calcul réfléchi (jusqu'à 100) : pas de suivi carte par carte
        recordKey(f.key, ok, fast)
    }

    private fun recordKey(k: String, ok: Boolean, fast: Boolean) {
        val old = boxKey(k)
        val nb = when {
            !ok -> 1
            fast -> min(5, old + 1)
            else -> max(1, old)
        }
        val wait = intArrayOf(0, 0, 1, 3, 7, 14)[nb]
        p.edit().putInt("b_$k", nb).putLong("d_$k", today() + wait).apply()
        version++
    }

    /* ----- Course d'orientation : un suivi par symbole ----- */

    fun oriBox(id: String): Int = boxKey("ORI_$id")
    fun oriDue(id: String): Boolean = boxKey("ORI_$id") > 0 && dueKey("ORI_$id") <= today()
    fun recordOri(id: String, ok: Boolean) = recordKey("ORI_$id", ok, ok)

    /* ----- Dictée : un suivi par mot ----- */

    private fun wkey(w: String) = "DIC_" + w.lowercase().replace(Regex("[^\\p{L}'-]"), "_")
    fun wordBox(w: String): Int = boxKey(wkey(w))
    fun wordDue(w: String): Boolean = boxKey(wkey(w)) == 0 || dueKey(wkey(w)) <= today()
    /** ok = trouvé en 2 essais au plus ; firstTry = trouvé du premier coup (seul cas où la boîte monte). */
    fun recordWord(w: String, ok: Boolean, firstTry: Boolean) = recordKey(wkey(w), ok, firstTry)

    /* ----- Les sommes égales (4 à 18) ----- */

    val SUM_TARGETS = (4..18).toList()

    /** Une manche réussie avec 2 ou 3 étoiles compte comme réussie ; 3 étoiles rapides font monter la boîte. */
    fun recordSum(n: Int, stars: Int, fast: Boolean) {
        recordKey("SUM_$n", stars >= 2, stars == 3 && fast)
        if (stars > bestStars(n)) p.edit().putInt("s_SUM_$n", stars).apply()
        version++
    }

    fun bestStars(n: Int): Int = p.getInt("s_SUM_$n", 0)
    fun sumMastered(n: Int): Boolean = boxKey("SUM_$n") >= 4

    /** Nombre conseillé : d'abord ceux à revoir aujourd'hui, puis le premier jamais joué, dans l'ordre. */
    fun nextSum(): Int {
        val t = today()
        SUM_TARGETS.filter { boxKey("SUM_$it") in 1..5 && dueKey("SUM_$it") <= t }
            .minByOrNull { boxKey("SUM_$it") }?.let { return it }
        SUM_TARGETS.firstOrNull { boxKey("SUM_$it") == 0 }?.let { return it }
        return SUM_TARGETS.minByOrNull { boxKey("SUM_$it") } ?: 9
    }

    fun allFacts(o: Op): List<Fact> = (1..10).flatMap { a -> (1..10).map { b -> Fact(o, a, b) } }

    /** Cartes bien connues : réussies vite au moins 3 fois à quelques jours d'écart. */
    fun mastered(o: Op): Int = allFacts(o).count { box(it) >= 4 }
    fun started(o: Op): Boolean = allFacts(o).any { box(it) > 0 }
    fun dueCount(): Int {
        val t = today()
        return Op.entries.sumOf { o -> allFacts(o).count { box(it) > 0 && due(it) <= t } }
    }

    /**
     * Ordre d'apprentissage des nouvelles cartes, du plus facile au plus difficile
     * (tables de 1, 10, 2 et 5 d'abord ; doubles, carrés et compléments à 10 favorisés).
     */
    fun difficulty(f: Fact): Int {
        val order = if (f.op.univ == Univ.MUL) listOf(1, 10, 2, 5, 3, 4, 9, 6, 8, 7)
        else listOf(1, 2, 10, 3, 4, 5, 9, 6, 7, 8)
        val ra = order.indexOf(f.a); val rb = order.indexOf(f.b)
        var d = max(ra, rb) * 10 + min(ra, rb)
        if (f.a == f.b) d -= 5
        if (f.op.univ == Univ.ADD && f.v == 10) d -= 8
        if (f.op.inverse) d += 3
        return d
    }

    /**
     * Entraînement du jour, environ 12 cartes :
     * 1. les cartes à revoir aujourd'hui, des plus fragiles aux plus solides ;
     * 2. au plus 3 nouvelles cartes de l'opération choisie, les plus faciles d'abord ;
     * 3. si besoin, des cartes déjà vues pour compléter.
     * Les opérations déjà travaillées sont mélangées (pratique entrelacée).
     */
    fun dailyPlan(current: Op): List<Fact> {
        val t = today()
        val ops = (Op.entries.filter { started(it) } + current).distinct()
        val pool = ops.flatMap { allFacts(it) }
        val dueNow = pool.filter { box(it) > 0 && due(it) <= t }
            .sortedWith(compareBy({ box(it) }, { due(it) })).take(9)
        val fresh = allFacts(current).filter { box(it) == 0 }.sortedBy { difficulty(it) }.take(3)
        var plan = dueNow + fresh
        if (plan.size < 12) {
            plan = plan + pool.filter { it !in plan && box(it) in 1..4 }
                .sortedBy { box(it) }.take(12 - plan.size)
        }
        if (plan.size < 12) {
            plan = plan + allFacts(current).filter { it !in plan }
                .sortedBy { difficulty(it) }.take(12 - plan.size)
        }
        return plan.shuffled()
    }
}

/**
 * Signature sonore de chaque opération, jouée avant la question :
 * + deux notes qui montent, − deux notes qui descendent,
 * × trois notes qui montent, ÷ trois notes qui descendent.
 * Les opérations « sœurs » (+/− et ×/÷) ont le même nombre de notes, en sens inverse.
 */
object Earcon {
    private const val SR = 22050
    private const val NOTE_MS = 110

    private fun notes(op: Op): DoubleArray = when (op) {
        Op.ADD -> doubleArrayOf(523.25, 783.99)
        Op.SUB -> doubleArrayOf(783.99, 523.25)
        Op.MUL -> doubleArrayOf(523.25, 659.25, 783.99)
        Op.DIV -> doubleArrayOf(783.99, 659.25, 523.25)
    }

    fun durationMs(op: Op): Long = notes(op).size * NOTE_MS + 180L

    fun play(op: Op) {
        try {
            val ns = notes(op)
            val n = SR * NOTE_MS / 1000
            val buf = ShortArray(n * ns.size)
            for (k in ns.indices) {
                for (j in 0 until n) {
                    val t = j.toDouble() / SR
                    val env = min(1.0, j / (0.008 * SR)) * (1.0 - j.toDouble() / n)
                    buf[k * n + j] = (sin(2 * PI * ns[k] * t) * env * 0.3 * Short.MAX_VALUE).toInt().toShort()
                }
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buf.size * 2)
                .build()
            track.write(buf, 0, buf.size)
            track.play()
            Handler(Looper.getMainLooper()).postDelayed({
                try { track.release() } catch (e: Exception) { }
            }, durationMs(op) + 300)
        } catch (e: Exception) {
        }
    }
}
