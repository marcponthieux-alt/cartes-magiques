package fr.cartes.multiplications

import androidx.compose.ui.graphics.Color

/** Couleurs, icônes et calculs communs aux tableaux imprimés et à l'application. */
object Data {
    val MINT = Color(0xFFAFE8D3)
    private val WHITE = Color(0xFFFFFFFF)

    /** Une couleur par résultat qui apparaît 3 ou 4 fois dans la table. */
    private val PAL: Map<Int, Long> = mapOf(
        4 to 0xFFDC2832, 6 to 0xFFFF8C00, 8 to 0xFFFFD60A, 9 to 0xFF28A046,
        10 to 0xFF006EC8, 12 to 0xFF8C3CAA, 16 to 0xFFFF69B4, 18 to 0xFF8B5A2B,
        20 to 0xFF00C8DC, 24 to 0xFFAADC28, 30 to 0xFF1E2878, 36 to 0xFF007878,
        40 to 0xFFBEA0FF
    )

    /** Résultats qui n'apparaissent qu'une fois : fond blanc. */
    private val ONCE = setOf(1, 25, 49, 64, 81, 100)

    /** Fonds foncés : texte blanc. */
    private val WHITE_TXT = setOf(4, 9, 10, 12, 18, 30, 36)

    val VALS = listOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 15, 16, 18, 20, 21, 24, 25, 27, 28,
        30, 32, 35, 36, 40, 42, 45, 48, 49, 50, 54, 56, 60, 63, 64, 70, 72, 80, 81, 90, 100
    )

    /** Un autocollant (bon point) par résultat, dans l'ordre de VALS. */
    val ICONS = listOf(
        "🐶", "🐱", "🐭", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵",
        "🐔", "🐧", "🐦", "🦄", "🐝", "🦋", "🐢", "🐙", "🐬", "🐳", "🦀", "🐞",
        "😀", "😎", "🤩", "🥳", "😺",
        "🚀", "🚲", "⚽", "🎈", "🎁", "🌈", "⭐", "🍓", "🍦", "🎸", "🏆"
    )

    fun bg(v: Int): Color = if (v in ONCE) WHITE else PAL[v]?.let { Color(it) } ?: MINT
    fun fg(v: Int): Color = if (v in WHITE_TXT) Color.White else Color(0xFF1A1A1A)
    fun isWhite(v: Int): Boolean = v in ONCE
    fun icon(v: Int): String = ICONS[VALS.indexOf(v)]

    /** Toutes les multiplications (a, b) de 1 à 10 qui donnent v. */
    fun prods(v: Int): List<Pair<Int, Int>> =
        (1..10).flatMap { a -> (1..10).filter { b -> a * b == v }.map { b -> a to b } }

    /** Carré 0 : 1-5 × 1-5, 1 : 1-5 × 6-10, 2 : 6-10 × 1-5, 3 : 6-10 × 6-10. */
    fun rows(q: Int): List<Int> { val s = if (q >= 2) 6 else 1; return (s..s + 4).toList() }
    fun cols(q: Int): List<Int> { val s = if (q % 2 == 1) 6 else 1; return (s..s + 4).toList() }
    fun value(q: Int, i: Int): Int = rows(q)[i / 5] * cols(q)[i % 5]
    fun geo(q: Int, i: Int): Pair<Int, Int> = rows(q)[i / 5] to cols(q)[i % 5]
    fun range(q: Int): String {
        val r = rows(q); val c = cols(q)
        return "Lignes ${r.first()} à ${r.last()} × colonnes ${c.first()} à ${c.last()}"
    }
}

/** Textes lus par le haut-parleur. Un point « . » = un temps de silence. */
object Speech {
    fun question(dir: Dir, v: Int): String =
        if (dir == Dir.R2V) "$v égale ?"
        else Data.prods(v).joinToString(", ") { "${it.first} fois ${it.second}" } + ", égale ?"

    fun answer(v: Int): String =
        Data.prods(v).joinToString(" ") { "${it.first} fois ${it.second}.. $v..." }

    fun right(v: Int): String = "Bravo. " + answer(v)

    fun wrong(dir: Dir, v: Int): String = question(dir, v) + "... " + answer(v)
}
