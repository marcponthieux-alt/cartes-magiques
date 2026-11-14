package fr.cartes.multiplications

import org.json.JSONArray

/** Lecture des nombres prononcés en français et vérification de la réponse. */
object Answer {

    private val UNITS = listOf(
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize"
    )
    private val TENS = listOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante")

    /** 24 -> "vingt-quatre", 71 -> "soixante-et-onze", 80 -> "quatre-vingts"… */
    fun fr(n: Int): String = when {
        n <= 16 -> UNITS[n]
        n < 20 -> "dix-" + UNITS[n - 10]
        n == 100 -> "cent"
        n < 70 -> {
            val t = TENS[n / 10]; val u = n % 10
            when (u) { 0 -> t; 1 -> "$t-et-un"; else -> "$t-${UNITS[u]}" }
        }
        n < 80 -> if (n == 71) "soixante-et-onze" else "soixante-" + fr(n - 60)
        n == 80 -> "quatre-vingts"
        else -> "quatre-vingt-" + fr(n - 80)
    }

    private val WORDS: Map<String, Int> = buildMap {
        UNITS.forEachIndexed { i, w -> put(w, i) }
        put("zero", 0); put("une", 1)
        put("vingt", 20); put("vingts", 20); put("trente", 30); put("quarante", 40)
        put("cinquante", 50); put("soixante", 60); put("cent", 100); put("cents", 100)
    }
    private val FOIS = setOf("fois", "foi", "foie", "x")
    private val PLUS = setOf("plus")
    private val SEPARATORS = setOf(
        "égale", "égal", "égalent", "font", "fait", "ça", "moins", "divisé", "divise", "divisés", "par"
    )

    /**
     * Vocabulaire imposé au moteur de reconnaissance : seulement les nombres, les opérations
     * du jeu et les commandes. Cela réduit beaucoup les erreurs.
     */
    val GRAMMAR: String by lazy {
        val phrases = LinkedHashSet<String>()
        for (n in 0..100) { val w = fr(n); phrases += w; phrases += w.replace('-', ' ') }
        for (a in 1..10) for (b in 1..10) {
            phrases += "${fr(a)} fois ${fr(b)}"
            phrases += "${fr(a)} plus ${fr(b)}"
        }
        phrases += "égale"; phrases += "moins"; phrases += "divisé par"
        phrases += "pause"; phrases += "je passe"; phrases += "passe"
        val arr = JSONArray()
        phrases.forEach { arr.put(it) }
        arr.put("[unk]")
        arr.toString()
    }

    sealed interface Tok {
        data class N(val n: Int) : Tok
        data object F : Tok
        data object P : Tok
    }

    fun parse(text: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var cur: Int? = null
        fun flush() { cur?.let { out.add(Tok.N(it)) }; cur = null }
        val words = text.lowercase().replace('-', ' ').replace('×', ' ').replace('+', ' ')
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        for (w in words) {
            if (w in FOIS) { flush(); out.add(Tok.F); continue }
            if (w in PLUS) { flush(); out.add(Tok.P); continue }
            if (w == "et") continue
            if (w in SEPARATORS) { flush(); continue }
            val t = w.toIntOrNull() ?: WORDS[w]
            if (t == null) { flush(); continue }
            val c = cur
            cur = when {
                c == null -> t
                c in setOf(20, 30, 40, 50, 60, 70, 80, 90) && t in 1..9 -> c + t
                (c == 60 || c == 80) && t in 10..19 -> c + t
                c == 4 && t == 20 -> 80
                c == 10 && t in 7..9 -> c + t
                else -> { flush(); t }
            }
        }
        flush()
        return out
    }

    enum class Command { PAUSE, PASS }

    /** Mots de commande reconnus au micro : « pause » et « je passe ». */
    fun command(text: String): Command? {
        val t = text.lowercase()
        if (Regex("\\bpause\\b").containsMatchIn(t)) return Command.PAUSE
        if (Regex("\\bpass(e|er|es)?\\b").containsMatchIn(t)) return Command.PASS
        return null
    }

    data class Result(val ok: Boolean, val said: String)

    /**
     * + et × en partant du résultat : l'enfant dit une opération qui donne ce résultat.
     * Sinon (en partant de l'opération, ou − et ÷) : l'enfant dit un nombre ;
     * on retient le dernier nombre prononcé (« 24 divisé par 6, 4 » → 4).
     */
    fun check(text: String, f: Fact, dir: Dir): Result {
        val toks = parse(text)
        val opTok: Tok = if (f.op.univ == Univ.ADD) Tok.P else Tok.F
        val pairs = mutableListOf<Pair<Int, Int>>()
        val used = mutableSetOf<Int>()
        for (k in toks.indices) {
            if ((toks[k] == Tok.F || toks[k] == Tok.P) && k > 0 && k < toks.lastIndex) {
                val a = toks[k - 1]; val b = toks[k + 1]
                if (a is Tok.N && b is Tok.N) {
                    if (toks[k] == opTok) pairs += a.n to b.n
                    used += k - 1; used += k + 1
                }
            }
        }
        val singles = toks.indices.filter { it !in used }.mapNotNull { (toks[it] as? Tok.N)?.n }
        val ok = if (!f.op.inverse && dir == Dir.R2V) {
            pairs.any { Data.combine(f.op.univ, it.first, it.second) == f.v && it.first in 1..10 && it.second in 1..10 }
        } else {
            val expected = if (f.op.inverse) f.b else f.v
            singles.lastOrNull() == expected
        }
        val said = toks.joinToString(" ") {
            when (it) { is Tok.N -> it.n.toString(); Tok.F -> "×"; Tok.P -> "+" }
        }.replace(" × ", "×").replace(" + ", "+").ifBlank { text }
        return Result(ok, said)
    }
}
