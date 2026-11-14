package fr.cartes.multiplications

import androidx.compose.ui.graphics.Color

/** Deux univers : celui des additions/soustractions et celui des multiplications/divisions. */
enum class Univ { ADD, MUL }

/** Les quatre opérations. Chacune a son symbole, son mot, sa couleur et sa signature sonore. */
enum class Op(val symbol: String, val word: String, val univ: Univ, val accent: Long, val label: String) {
    ADD("+", "plus", Univ.ADD, 0xFF2E8B57, "Additions"),
    SUB("−", "moins", Univ.ADD, 0xFFC4561A, "Soustractions"),
    MUL("×", "fois", Univ.MUL, 0xFF2F6FD6, "Multiplications"),
    DIV("÷", "divisé par", Univ.MUL, 0xFF8A4FD1, "Divisions");

    /** Soustraction et division : on cherche le nombre caché (la ligne du tableau). */
    val inverse: Boolean get() = this == SUB || this == DIV
    val color: Color get() = Color(accent)
    /** Symbole des lignes affichées sur la face « opérations » des cartes. */
    val tableSymbol: String get() = if (univ == Univ.ADD) "+" else "×"
}

/**
 * Un fait numérique : la carte de la colonne a (largeur, axe horizontal) et de la ligne b
 * (hauteur, axe vertical), jouée avec une opération. Le 1 × 1 est en bas à gauche.
 * ADD : a + b = v · SUB : v − a = b · MUL : a × b = v (a de large, b de haut) · DIV : v ÷ a = b
 */
data class Fact(val op: Op, val a: Int, val b: Int) {
    val v: Int get() = Data.combine(op.univ, a, b)
    val key: String get() = "${op.name}_${a}_$b"
}

object Data {
    fun combine(u: Univ, a: Int, b: Int): Int = if (u == Univ.ADD) a + b else a * b

    /* ----- Univers × ÷ : couleurs et autocollants des tableaux imprimés ----- */
    val MINT = Color(0xFFAFE8D3)
    private val WHITE = Color(0xFFFFFFFF)
    private val PAL: Map<Int, Long> = mapOf(
        4 to 0xFFDC2832, 6 to 0xFFFF8C00, 8 to 0xFFFFD60A, 9 to 0xFF28A046,
        10 to 0xFF006EC8, 12 to 0xFF8C3CAA, 16 to 0xFFFF69B4, 18 to 0xFF8B5A2B,
        20 to 0xFF00C8DC, 24 to 0xFFAADC28, 30 to 0xFF1E2878, 36 to 0xFF007878,
        40 to 0xFFBEA0FF
    )
    private val ONCE = setOf(1, 25, 49, 64, 81, 100)
    private val WHITE_TXT = setOf(4, 9, 10, 12, 18, 30, 36)
    val MUL_VALS = listOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 15, 16, 18, 20, 21, 24, 25, 27, 28,
        30, 32, 35, 36, 40, 42, 45, 48, 49, 50, 54, 56, 60, 63, 64, 70, 72, 80, 81, 90, 100
    )
    val MUL_ICONS = listOf(
        "🐶", "🐱", "🐭", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵",
        "🐔", "🐧", "🐦", "🦄", "🐝", "🦋", "🐢", "🐙", "🐬", "🐳", "🦀", "🐞",
        "😀", "😎", "🤩", "🥳", "😺",
        "🚀", "🚲", "⚽", "🎈", "🎁", "🌈", "⭐", "🍓", "🍦", "🎸", "🏆"
    )

    /* ----- Univers + − : magiciens, détectives et objets du quotidien ----- */
    val ADD_ICONS = listOf(
        "🎩", "🔮", "🃏", "🔍", "🔑", "🗝️", "🕯️", "🧭", "🔦", "✨",
        "🎲", "🧩", "⏰", "☂️", "🎒", "✏️", "🧲", "🎨", "👑"
    )

    /**
     * Univers + − : 10 couleurs douces, choisies selon le chiffre des unités
     * (4, 14, 24… ont la même couleur, pour faire voir les régularités).
     */
    private val UNITS_PAL = listOf(
        0xFFD8E2DC, 0xFFFFADAD, 0xFFFFD6A5, 0xFFFDFFB6, 0xFFCAFFBF,
        0xFF9BF6FF, 0xFFA0C4FF, 0xFFBDB2FF, 0xFFFFC6FF, 0xFFE4C1A1
    )

    /** Les deux nombres d'une addition, dans les bâtons de dizaines. */
    val COLOR_A = Color(0xFFE8743B)
    val COLOR_B = Color(0xFF2E86AB)

    fun bg(u: Univ, v: Int): Color =
        if (u == Univ.ADD) Color(UNITS_PAL[((v % 10) + 10) % 10])
        else if (v in ONCE) WHITE else PAL[v]?.let { Color(it) } ?: MINT

    fun fg(u: Univ, v: Int): Color =
        if (u == Univ.MUL && v in WHITE_TXT) Color.White else Color(0xFF1A1A1A)

    fun isWhite(u: Univ, v: Int): Boolean = u == Univ.MUL && v in ONCE

    fun icon(u: Univ, v: Int): String =
        if (u == Univ.ADD) ADD_ICONS[((v - 2) % ADD_ICONS.size + ADD_ICONS.size) % ADD_ICONS.size]
        else MUL_ICONS[MUL_VALS.indexOf(v)]

    fun teaser(u: Univ): String = (if (u == Univ.ADD) ADD_ICONS else MUL_ICONS).take(7).joinToString("")
    fun iconCount(u: Univ): Int = if (u == Univ.ADD) ADD_ICONS.size else MUL_ICONS.size

    /** Toutes les opérations (a, b) de 1 à 10 qui donnent v. */
    fun pairs(u: Univ, v: Int): List<Pair<Int, Int>> =
        (1..10).flatMap { a -> (1..10).filter { b -> combine(u, a, b) == v }.map { b -> a to b } }

    /**
     * Carré 0 : colonnes 1-5, lignes 1-5 (en bas à gauche) · 1 : colonnes 6-10, lignes 1-5
     * 2 : colonnes 1-5, lignes 6-10 · 3 : colonnes 6-10, lignes 6-10 (en haut à droite).
     */
    fun rows(q: Int): List<Int> { val s = if (q >= 2) 6 else 1; return (s..s + 4).toList() }
    fun cols(q: Int): List<Int> { val s = if (q % 2 == 1) 6 else 1; return (s..s + 4).toList() }

    /** Lignes affichées de haut en bas (la plus grande en haut). */
    fun rowsTopDown(q: Int): List<Int> = rows(q).reversed()

    /** Emplacement i (0 à 24) lu de gauche à droite et de haut en bas. */
    fun fact(op: Op, q: Int, i: Int): Fact = Fact(op, cols(q)[i % 5], rowsTopDown(q)[i / 5])
    fun range(q: Int): String {
        val r = rows(q); val c = cols(q)
        return "Colonnes ${c.first()} à ${c.last()} · lignes ${r.first()} à ${r.last()}"
    }
}

/**
 * Textes lus par le haut-parleur. Un point « . » = un temps de silence.
 * Seule l'opération de l'emplacement de la carte est lue.
 */
object Speech {
    fun question(f: Fact, dir: Dir): String = when {
        f.op.inverse -> "${f.v} ${f.op.word} ${f.a}, égale ?"
        dir == Dir.R2V -> "${f.v} égale ?"
        else -> "${f.a} ${f.op.word} ${f.b}, égale ?"
    }

    fun answer(f: Fact): String =
        if (f.op.inverse) "${f.v} ${f.op.word} ${f.a}.. ${f.b}..."
        else "${f.a} ${f.op.word} ${f.b}.. ${f.v}..."

    fun wrong(f: Fact, dir: Dir): String = question(f, dir) + "... " + answer(f)
}

/** Encouragements et félicitations, de temps en temps, pour aller au bout de la partie. */
object Cheers {
    private val NOMBRES = mapOf(
        3 to "Trois", 6 to "Six", 9 to "Neuf", 12 to "Douze", 15 to "Quinze",
        18 to "Dix-huit", 21 to "Vingt et une", 24 to "Vingt-quatre"
    )
    private val GENERIC = listOf(
        "Continue comme ça !", "Tu te débrouilles très bien !", "Super, on continue !",
        "Tu progresses !", "Quelle mémoire !", "Bravo, tu es sur la bonne route !"
    )

    fun after(ok: Boolean, streak: Int, doneCount: Int, total: Int): String? {
        val left = total - doneCount
        if (left <= 0) return null
        if (ok && streak >= 3 && streak % 3 == 0) return "${NOMBRES[streak] ?: streak} bonnes réponses de suite !"
        if (total >= 10 && left == 1) return "Dernière carte !"
        if (total >= 15 && left == 5) return "Plus que cinq cartes, tu y es presque !"
        if (total >= 10 && doneCount == total / 2) return "Tu es à la moitié, continue !"
        if (!ok && (0..2).random() == 0) return "Ce n'est pas grave, on continue !"
        if (doneCount % 4 == 0) return GENERIC.random()
        return null
    }
}

/**
 * Séries d'additions ou de soustractions par niveau.
 * Jusqu'à 10 et jusqu'à 20 : le répertoire additif à mémoriser (cartes les plus fragiles d'abord).
 * Jusqu'à 100 : du calcul réfléchi, par familles, de la plus facile à la plus difficile.
 */
object Series {
    const val SIZE = 15

    fun additive(op: Op, level: Int, progress: Progress): List<Fact> {
        if (level <= 20) {
            val pool = progress.allFacts(op).filter { it.a + it.b <= level }
            return pool.shuffled().sortedBy { progress.box(it) }.take(SIZE).shuffled()
        }
        val out = mutableListOf<Fact>()
        // + : grand + petit ; − : (grand + petit) − petit = grand
        fun add(big: Int, small: Int) {
            out += if (op == Op.ADD) Fact(op, big, small) else Fact(op, small, big)
        }
        // 1. Ajouter des dizaines entières : 34 + 20
        repeat(4) {
            val big = (1..7).random() * 10 + (1..9).random()
            add(big, (1..(9 - big / 10)).random() * 10)
        }
        // 2. Ajouter un petit nombre sans changer de dizaine : 34 + 5
        repeat(4) {
            val u = (1..7).random()
            add((1..8).random() * 10 + u, (1..(9 - u)).random())
        }
        // 3. Ajouter un petit nombre avec passage de la dizaine : 38 + 5
        repeat(4) {
            val u = (2..9).random()
            add((1..8).random() * 10 + u, ((10 - u)..9).random())
        }
        // 4. Deux nombres de deux chiffres : 47 + 38
        repeat(3) {
            val big = (15..60).random()
            add(big, (11..(99 - big)).random())
        }
        return out
    }
}
