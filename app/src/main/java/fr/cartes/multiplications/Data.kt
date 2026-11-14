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
 * Un fait numérique : la carte (ligne a, colonne b) jouée avec une opération.
 * ADD : a + b = v · SUB : v − b = a · MUL : a × b = v · DIV : v ÷ b = a
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

    /* ----- Univers + − : magiciens, détectives et objets du quotidien (sommes 2 à 20) ----- */
    val ADD_ICONS = listOf(
        "🎩", "🔮", "🃏", "🔍", "🔑", "🗝️", "🕯️", "🧭", "🔦", "✨",
        "🎲", "🧩", "⏰", "☂️", "🎒", "✏️", "🧲", "🎨", "👑"
    )

    /**
     * Univers + − : un dégradé de couleurs douces, du jaune (2) au rose (20).
     * Les résultats égaux forment des diagonales de même couleur dans le tableau.
     */
    fun bg(u: Univ, v: Int): Color =
        if (u == Univ.ADD) Color.hsv(50f + (v - 2) * (320f - 50f) / 18f, 0.40f, 0.97f)
        else if (v in ONCE) WHITE else PAL[v]?.let { Color(it) } ?: MINT

    fun fg(u: Univ, v: Int): Color =
        if (u == Univ.MUL && v in WHITE_TXT) Color.White else Color(0xFF1A1A1A)

    fun isWhite(u: Univ, v: Int): Boolean = u == Univ.MUL && v in ONCE

    fun icon(u: Univ, v: Int): String =
        if (u == Univ.ADD) ADD_ICONS[v - 2] else MUL_ICONS[MUL_VALS.indexOf(v)]

    fun teaser(u: Univ): String = (if (u == Univ.ADD) ADD_ICONS else MUL_ICONS).take(7).joinToString("")
    fun iconCount(u: Univ): Int = if (u == Univ.ADD) ADD_ICONS.size else MUL_ICONS.size

    /** Toutes les opérations (a, b) de 1 à 10 qui donnent v. */
    fun pairs(u: Univ, v: Int): List<Pair<Int, Int>> =
        (1..10).flatMap { a -> (1..10).filter { b -> combine(u, a, b) == v }.map { b -> a to b } }

    /** Carré 0 : 1-5 × 1-5, 1 : 1-5 × 6-10, 2 : 6-10 × 1-5, 3 : 6-10 × 6-10. */
    fun rows(q: Int): List<Int> { val s = if (q >= 2) 6 else 1; return (s..s + 4).toList() }
    fun cols(q: Int): List<Int> { val s = if (q % 2 == 1) 6 else 1; return (s..s + 4).toList() }
    fun fact(op: Op, q: Int, i: Int): Fact = Fact(op, rows(q)[i / 5], cols(q)[i % 5])
    fun range(q: Int): String {
        val r = rows(q); val c = cols(q)
        return "Lignes ${r.first()} à ${r.last()} · colonnes ${c.first()} à ${c.last()}"
    }
}

/**
 * Textes lus par le haut-parleur. Un point « . » = un temps de silence.
 * Seule l'opération de l'emplacement de la carte est lue.
 */
object Speech {
    fun question(f: Fact, dir: Dir): String = when {
        f.op.inverse -> "${f.v} ${f.op.word} ${f.b}, égale ?"
        dir == Dir.R2V -> "${f.v} égale ?"
        else -> "${f.a} ${f.op.word} ${f.b}, égale ?"
    }

    fun answer(f: Fact): String =
        if (f.op.inverse) "${f.v} ${f.op.word} ${f.b}.. ${f.a}..."
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
