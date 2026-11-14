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

/**
 * Textes lus par le haut-parleur. Un point « . » = un temps de silence.
 * Face multiplication : seule la ligne de l'emplacement de la carte (en gras) est lue,
 * en question comme en réponse. Les autres lignes restent seulement affichées.
 */
object Speech {
    fun question(dir: Dir, q: Int, i: Int): String {
        if (dir == Dir.R2V) return "${Data.value(q, i)} égale ?"
        val (a, b) = Data.geo(q, i)
        return "$a fois $b, égale ?"
    }

    fun answer(q: Int, i: Int): String {
        val (a, b) = Data.geo(q, i)
        return "$a fois $b.. ${a * b}..."
    }

    fun wrong(dir: Dir, q: Int, i: Int): String = question(dir, q, i) + "... " + answer(q, i)
}

/** Encouragements et félicitations, de temps en temps, pour aller au bout des 25 cartes. */
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
        if (total >= 10 && left == 5) return "Plus que cinq cartes, tu y es presque !"
        if (total >= 10 && doneCount == total / 2) return "Tu es à la moitié, continue !"
        if (!ok && (0..2).random() == 0) return "Ce n'est pas grave, on continue !"
        if (doneCount % 4 == 0) return GENERIC.random()
        return null
    }
}
