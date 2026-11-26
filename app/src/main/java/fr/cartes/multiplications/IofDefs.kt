package fr.cartes.multiplications

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * Définitions de postes IOF 2024 (colonnes C, D et G).
 * Les tracés viennent de IofData (Purple Pen, licence BSD) et ont été comparés
 * page par page à « International Specification for Control Descriptions 2024 ».
 */

private val IOF_INK = Color(0xFF1A1A1A)

/** Dessine un symbole IOF (repère 200 × 200 centré, y vers le haut) dans une case de 0 à 100. */
fun DrawScope.iof(key: String, c: Color = IOF_INK) {
    val data = IofData.SHAPES[key] ?: return
    val k = 0.42f
    val unit = size.minDimension / 100f
    val dx = (size.width - size.minDimension) / 2f
    val dy = (size.height - size.minDimension) / 2f
    fun at(x: Float, y: Float) = Offset(dx + (50f + x * k) * unit, dy + (50f - y * k) * unit)
    fun pts(t: List<String>, from: Int): List<Offset> =
        (from until t.size - 1 step 2).map { at(t[it].toFloat(), t[it + 1].toFloat()) }
    fun line(p: List<Offset>, close: Boolean) = Path().apply {
        p.forEachIndexed { i, q -> if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
        if (close) close()
    }
    fun bez(p: List<Offset>) = Path().apply {
        moveTo(p[0].x, p[0].y)
        var i = 1
        while (i + 2 < p.size) { cubicTo(p[i].x, p[i].y, p[i + 1].x, p[i + 1].y, p[i + 2].x, p[i + 2].y); i += 3 }
    }
    data.split(';').forEach { el ->
        val t = el.trim().split(' ').filter { it.isNotEmpty() }
        when (t[0]) {
            "L" -> drawPath(
                line(pts(t, 4), false), c,
                style = Stroke(
                    t[1].toFloat() * k * unit,
                    cap = if (t[2] == "f") StrokeCap.Butt else StrokeCap.Round,
                    join = if (t[3] == "s") StrokeJoin.Miter else StrokeJoin.Round
                )
            )
            "B" -> drawPath(bez(pts(t, 2)), c, style = Stroke(t[1].toFloat() * k * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            "P" -> drawPath(
                line(pts(t, 3), true), c,
                style = Stroke(t[1].toFloat() * k * unit, join = if (t[2] == "s") StrokeJoin.Miter else StrokeJoin.Round)
            )
            "Q" -> drawPath(line(pts(t, 1), true), c)
            "R" -> drawPath(bez(pts(t, 1)).apply { close() }, c)
            "C" -> drawCircle(c, t[2].toFloat() * k * unit, at(t[3].toFloat(), t[4].toFloat()), style = Stroke(t[1].toFloat() * k * unit))
            "F" -> drawCircle(c, t[1].toFloat() * k * unit, at(t[2].toFloat(), t[3].toFloat()))
        }
    }
}

/** Un élément de la colonne D. [cat] dit quelles positions (colonne G) ont du sens pour lui. */
class DEl(
    val ref: String, val pp: String, val nom: String, val fem: Boolean, val plur: String,
    val cat: String, val tip: String
) {
    val id: String get() = "iof_D$pp"
    private val vowel: Boolean get() = nom.first().lowercaseChar() in "aeiouéèêâîôûh"
    val le: String get() = (if (vowel) "l'" else if (fem) "la " else "le ") + nom
}

/** Une famille de la colonne G : même dessin, tourné dans 8 directions (ou sans direction). */
class GFam(val pp: String, val ref: String, val dirs: Boolean, val name: String, val tip: String)

object IofDefs {
    val DIRS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val DIR_FR = mapOf(
        "N" to "nord", "NE" to "nord-est", "E" to "est", "SE" to "sud-est",
        "S" to "sud", "SW" to "sud-ouest", "W" to "ouest", "NW" to "nord-ouest"
    )

    val D = listOf(
        DEl("1.3", "1.3", "rentrant", false, "rentrants", "vallon", "Un petit vallon qui entre dans la pente : le contraire d'un éperon."),
        DEl("1.4", "1.4", "talus", false, "talus", "relief_lin", "Une marche de terre : le sol change brusquement de hauteur."),
        DEl("1.7", "1.7", "ravine", true, "ravines", "vallon", "Un fossé creusé par l'eau, en général sec."),
        DEl("1.9", "1.9", "colline", true, "collines", "colline", "Un point haut, dessiné sur la carte par des courbes fermées."),
        DEl("1.10", "1.10", "butte", true, "buttes", "pt_haut", "Une petite bosse bien visible (un point plein)."),
        DEl("1.12", "1.12", "dépression", true, "dépressions", "creux", "Un creux entouré de pentes de tous les côtés."),
        DEl("1.13", "1.13", "petite dépression", true, "petites dépressions", "pt_creux", "Un petit creux peu profond : un U."),
        DEl("1.14", "1.14", "trou", false, "trous", "pt_creux", "Un trou aux bords raides, souvent creusé par l'homme : un V."),
        DEl("1.16", "1.16", "fourmilière", true, "fourmilières", "pt_haut", "Le dôme d'une fourmilière : une étoile."),
        DEl("2.1", "2.1", "falaise", true, "falaises", "relief_lin", "Une paroi de rocher. Sans symbole en G, la balise est au pied de la falaise, au milieu."),
        DEl("2.3", "2.3", "grotte", true, "grottes", "aucun", "Un trou dans la roche ou dans la colline."),
        DEl("2.4", "2.4", "rocher", false, "rochers", "pt_haut", "Un gros bloc de pierre posé sur le sol : un triangle plein."),
        DEl("2.5", "2.5", "zone de blocs rocheux", true, "zones de blocs rocheux", "zone", "Tant de rochers qu'on ne peut pas les dessiner un par un : plusieurs triangles."),
        DEl("2.6", "2.6", "groupe de rochers", false, "groupes de rochers", "pt_haut", "Quelques rochers serrés les uns contre les autres : deux triangles collés."),
        DEl("2.7", "2.7", "sol pierreux", false, "sols pierreux", "zone", "Un sol couvert de petites pierres : plein de points."),
        DEl("3.1", "3.1", "lac", false, "lacs", "zone", "Une grande étendue d'eau, qu'on ne traverse pas."),
        DEl("3.2", "3.2", "mare", true, "mares", "pt_creux", "Une petite étendue d'eau : des vagues au-dessus d'un U."),
        DEl("3.3", "3.3", "trou d'eau", false, "trous d'eau", "pt_creux", "Un trou rempli d'eau : des vagues au-dessus d'un V."),
        DEl("3.4", "3.4", "ruisseau", false, "ruisseaux", "aucun", "Un cours d'eau : une ligne en vagues."),
        DEl("3.7", "3.7", "marais", false, "marais", "zone", "Un terrain toujours mouillé : des traits horizontaux."),
        DEl("3.9", "3.9", "puits", false, "puits", "pt_haut", "Un puits ou une source captée : un rond au-dessus de vagues."),
        DEl("4.1", "4.1", "terrain découvert", false, "terrains découverts", "zone", "Une prairie ou un champ, sans arbres : un losange."),
        DEl("4.3", "4.3", "corne de forêt", true, "cornes de forêt", "aucun", "La pointe d'une forêt qui avance dans un terrain découvert."),
        DEl("4.4", "4.4", "clairière", true, "clairières", "zone", "Un espace sans arbres au milieu de la forêt : un rond en pointillés."),
        DEl("4.5", "4.5", "fourré", false, "fourrés", "zone", "Un petit bois si serré qu'on a du mal à passer : un quadrillage."),
        DEl("4.7", "4.7", "limite de végétation", true, "limites de végétation", "aucun", "La frontière entre deux sortes de végétation."),
        DEl("4.9", "4.9", "arbre remarquable", false, "arbres remarquables", "pt_haut", "Un arbre qu'on remarque : un petit sapin."),
        DEl("4.10", "4.10", "souche", true, "souches", "pt_haut", "Une souche ou les racines d'un arbre tombé : un rond barré d'une croix."),
        DEl("5.1", "5.1", "route", true, "routes", "chemin", "Une route goudronnée : un trait plein."),
        DEl("5.2", "5.2", "chemin", false, "chemins", "chemin", "Un chemin ou un sentier : un trait en tirets."),
        DEl("5.4", "5.4", "pont", false, "ponts", "pont", "Un passage au-dessus d'un ruisseau."),
        DEl("5.8", "5.8", "mur", false, "murs", "ligne", "Un mur de pierre : un trait avec des ronds."),
        DEl("5.9", "5.9", "clôture", true, "clôtures", "ligne", "Une clôture en fil de fer ou en bois : un trait avec des dents."),
        DEl("5.11", "5.11", "bâtiment", false, "bâtiments", "bati", "Une construction (maison, cabane) : un carré plein."),
        DEl("5.12", "5.12", "zone pavée", true, "zones pavées", "zone", "Un parking ou une place goudronnée : un carré hachuré."),
        DEl("5.13", "5.13", "ruine", true, "ruines", "bati", "Les restes d'un bâtiment écroulé : un carré en morceaux."),
        DEl("5.15", "5.15", "tour", true, "tours", "pt_haut", "Une tour ou un pylône : un T."),
        DEl("5.17", "5.17", "borne", true, "bornes", "pt_haut", "Une borne ou un cairn : un rond avec un point au centre."),
        DEl("5.18", "5.18", "mangeoire", true, "mangeoires", "pt_haut", "Une mangeoire pour les animaux : une flèche vers le haut."),
        DEl("5.22", "5.24", "escalier", false, "escaliers", "escalier", "Un escalier d'au moins deux marches."),
    )
    private val byD = D.associateBy { it.id }
    fun dEl(id: String): DEl = byD.getValue(id)

    // Colonne G (numéros 2024 entre parenthèses ; les dessins portent l'ancien numéro de Purple Pen)
    val COTE = GFam("11.1", "12.1", true, "Côté", "Le point est dehors, collé au rond : la balise est contre l'élément, de ce côté.")
    val BORD = GFam("11.2", "12.2", true, "Bord", "Un petit trait coupe le rond : la balise est sur le bord (d'un creux ou d'une zone).")
    val PARTIE = GFam("11.3", "12.3", true, "Partie", "Le point est dans le rond : la balise est à l'intérieur, dans cette partie.")
    val ANGLE_IN = GFam("11.4", "12.4", true, "Angle intérieur", "Le point est dans le coin : la balise est à l'intérieur de l'angle.")
    val ANGLE_OUT = GFam("11.5", "12.5", true, "Angle extérieur", "Le point est hors du coin : la balise est à l'extérieur de l'angle.")
    val POINTE = GFam("11.6", "12.6", true, "Pointe", "Un angle très fin : la balise est au bout de la pointe.")
    val EXTREMITE = GFam("11.8", "12.7", true, "Extrémité", "Le trait s'arrête sur une barre : la balise est au bout de l'élément (mur, chemin…).")
    val SUP = GFam("11.9", "12.8", false, "Partie supérieure", "Le point est en haut, entre deux traits : la balise est dans la partie haute.")
    val INF = GFam("11.10", "12.9", false, "Partie inférieure", "Le point est en bas, entre deux traits : la balise est dans la partie basse.")
    val SOMMET = GFam("11.11", "12.10", false, "Sommet", "Le point est posé dessus : la balise est en haut (de la falaise, de l'escalier…).")
    val SOUS = GFam("11.12", "12.13", false, "Sous", "Le point est sous le toit : la balise est en dessous (sous un pont…).")
    val PIED = GFam("11.13", "12.11", false, "Pied", "Un angle avec le point en bas : la balise est au pied de la pente.")
    val PIED_DIR = GFam("11.14", "12.12", true, "Pied", "Le petit angle est à côté du rond : la balise est au pied, de ce côté (grande colline).")
    val ENTRE = GFam("11.15", "12.14", false, "Entre", "Un point entre deux traits : la balise est entre deux éléments (colonnes D et E).")
    val G = listOf(COTE, BORD, PARTIE, ANGLE_IN, ANGLE_OUT, POINTE, EXTREMITE, SUP, INF, SOMMET, SOUS, PIED, PIED_DIR, ENTRE)

    fun gId(f: GFam, dir: String?) = "iof_G${f.pp}${dir ?: ""}"
    fun cId(c: String) = when (c) {
        "SUP" -> "iof_C0.3"; "INF" -> "iof_C0.4"; "MIL" -> "iof_C0.5"
        else -> "iof_C0.${if (c.length == 1) 1 else 2}$c"
    }

    /** Ce qu'on montre au niveau 8 ; les autres directions servent dans les jeux (niveau 9, caché). */
    private val LEARN8 = setOf(
        "iof_C0.1N", "iof_C0.2SE", "iof_C0.3", "iof_C0.4", "iof_C0.5",
        "iof_G11.1N", "iof_G11.1E", "iof_G11.1S", "iof_G11.1W", "iof_G11.2SE", "iof_G11.3W", "iof_G11.4E",
        "iof_G11.5S", "iof_G11.6SW", "iof_G11.8NW", "iof_G11.9", "iof_G11.10", "iof_G11.11", "iof_G11.12",
        "iof_G11.13", "iof_G11.14NE", "iof_G11.15"
    )

    private fun alaDir(d: String): String { val w = DIR_FR.getValue(d); return if (w[0] in "eo") "à l'$w" else "au $w" }

    fun syms(): List<Sym> {
        val out = mutableListOf<Sym>()
        D.forEach { e ->
            out += Sym(e.id, 7, Fam.NOIR, "${e.nom.replaceFirstChar { it.uppercase() }} (D ${e.ref})", e.tip) { iof("D${e.pp}") }
        }
        DIRS.forEach { d ->
            val key = "C0.${if (d.length == 1) 1 else 2}$d"
            val id = "iof_$key"
            out += Sym(
                id, if (id in LEARN8) 8 else 9, Fam.NOIR, "Le plus ${alaDir(d)} (C ${key.drop(1).dropLast(d.length)})",
                "Une flèche en colonne C : parmi plusieurs éléments pareils, c'est celui qui est le plus ${alaDir(d)}."
            ) { iof(key) }
        }
        out += Sym("iof_C0.3", 8, Fam.NOIR, "Supérieur (C 0.3)", "Deux traits, le point en haut : c'est l'élément du dessus quand deux pareils sont l'un au-dessus de l'autre.") { iof("C0.3") }
        out += Sym("iof_C0.4", 8, Fam.NOIR, "Inférieur (C 0.4)", "Deux traits, le point en bas : c'est l'élément du dessous quand deux pareils sont l'un au-dessus de l'autre.") { iof("C0.4") }
        out += Sym("iof_C0.5", 8, Fam.NOIR, "Du milieu (C 0.5)", "Trois traits, le point au milieu : c'est l'élément du milieu parmi plusieurs pareils.") { iof("C0.5") }
        G.forEach { f ->
            val dirs: List<String?> = if (f.dirs) DIRS else listOf(null)
            dirs.forEach { d ->
                val id = gId(f, d)
                val label = when {
                    d == null -> f.name
                    f == ANGLE_IN -> "Angle ${DIR_FR[d]} (intérieur)"
                    f == ANGLE_OUT -> "Angle ${DIR_FR[d]} (extérieur)"
                    else -> "${f.name} ${DIR_FR[d]}"
                }
                out += Sym(id, if (id in LEARN8) 8 else 9, Fam.NOIR, "$label (G ${f.ref})", f.tip) { iof("G${f.pp}${d ?: ""}") }
            }
        }
        return out
    }

    /* ---------- Phrases en français ---------- */

    /** Le groupe nominal avec la colonne C : « le rocher le plus au nord », « la falaise supérieure ». */
    fun np(e: DEl, c: String?): String = when (c) {
        null -> e.le
        "SUP" -> e.le + if (e.fem) " supérieure" else " supérieur"
        "INF" -> e.le + if (e.fem) " inférieure" else " inférieur"
        "MIL" -> e.le + " du milieu"
        else -> e.le + (if (e.fem) " la plus " else " le plus ") + alaDir(c)
    }

    private fun de(np: String): String = when {
        np.startsWith("le ") -> "du " + np.drop(3)
        np.startsWith("la ") -> "de la " + np.drop(3)
        else -> "de $np"
    }

    fun sentence(e: DEl, c: String?, g: GFam?, d: String?): String {
        val n = np(e, c); val dn = de(n); val dir = d?.let { DIR_FR[it] } ?: ""
        val s = when (g) {
            null -> when (e.cat) {
                "relief_lin" -> "au pied $dn, au milieu"
                "chemin" -> "sur $n"
                "pont" -> "sur $n"
                "escalier" -> "dans $n"
                "aucun" -> if (e.nom == "ruisseau" || e.nom == "limite de végétation") "sur $n" else "à $n".replace("à le ", "au ")
                else -> "au centre $dn"
            }
            COTE -> "du côté $dir $dn"
            BORD -> "sur le bord $dir $dn"
            PARTIE -> "dans la partie $dir $dn"
            ANGLE_IN -> "dans l'angle $dir $dn, à l'intérieur"
            ANGLE_OUT -> "à l'angle $dir $dn, à l'extérieur"
            POINTE -> "à la pointe $dir $dn"
            EXTREMITE -> "à l'extrémité $dir $dn"
            SUP -> "dans la partie supérieure $dn"
            INF -> "dans la partie inférieure $dn"
            SOMMET -> "au sommet $dn"
            SOUS -> "sous $n"
            PIED -> "au pied $dn"
            PIED_DIR -> "au pied $dir $dn"
            ENTRE -> "entre deux ${e.plur}"
            else -> "près $dn"
        }
        return "La balise est $s."
    }

    /** Positions G qui ont du sens pour chaque sorte d'élément (null = au centre / position par défaut). */
    fun gFor(e: DEl): List<GFam?> = when (e.cat) {
        "pt_haut" -> listOf(null, COTE, COTE, ENTRE)
        "pt_creux" -> listOf(null, BORD, BORD, ENTRE)
        "colline" -> listOf(null, PARTIE, PIED_DIR)
        "creux" -> listOf(null, BORD, PARTIE)
        "relief_lin" -> listOf(null, SOMMET, EXTREMITE)
        "vallon" -> listOf(null, SUP, INF)
        "zone" -> listOf(null, BORD, PARTIE, ANGLE_IN, ANGLE_OUT, POINTE)
        "chemin" -> listOf(null, EXTREMITE)
        "ligne" -> listOf(COTE, ANGLE_IN, ANGLE_OUT, EXTREMITE)
        "bati" -> listOf(COTE, ANGLE_IN, ANGLE_OUT)
        "pont" -> listOf(null, SOUS, EXTREMITE)
        "escalier" -> listOf(null, SOMMET, PIED)
        else -> listOf(null)
    }

    /** Colonne C possible : directions, supérieur/inférieur, du milieu. */
    fun cFor(e: DEl): List<String> = when (e.cat) {
        "pt_haut", "pt_creux" -> DIRS + "MIL"
        "zone", "bati", "colline", "creux", "aucun" -> DIRS
        "relief_lin", "chemin" -> listOf("SUP", "INF")
        else -> emptyList()
    }
}
