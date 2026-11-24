package fr.cartes.multiplications

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* =====================================================================
 *  Course d'orientation : les symboles des cartes (norme IOF ISOM 2017-2)
 *  Dessins simplifiés et agrandis pour l'apprentissage.
 * ===================================================================== */

private val O_INK = Color(0xFF23264A)
private val O_SUB = Color(0xFF5A5E7A)
private val O_LINE = Color(0xFFE2DCCD)
private val O_BG = Color(0xFFF1F5EC)
private val O_GOOD = Color(0xFF2E8B57)
private val O_BAD = Color(0xFFC0392B)
private val O_ACCENT = Color(0xFFE8743B)

private val C_YEL = Color(0xFFFFBA35)
private val C_YEL_L = Color(0xFFFFE2A6)
private val C_G1 = Color(0xFFC6F2BB)
private val C_G2 = Color(0xFF8BD67E)
private val C_G3 = Color(0xFF34A853)
private val C_BLUE = Color(0xFF1C9AD6)
private val C_BLUE_L = Color(0xFF9AD8F5)
private val C_BROWN = Color(0xFFBF6A2A)
private val C_BLACK = Color(0xFF1A1A1A)
private val C_GREY = Color(0xFFA7A7A7)
private val C_PURPLE = Color(0xFFB5179E)

/** Les 7 familles de couleurs de la carte : la première chose à savoir. */
enum class Fam(val label: String, val color: Color, val meaning: String) {
    BLANC("Blanc", Color.White, "forêt facile"),
    JAUNE("Jaune", C_YEL, "terrain découvert"),
    VERT("Vert", C_G3, "végétation qui ralentit"),
    BLEU("Bleu", C_BLUE, "eau"),
    MARRON("Marron", C_BROWN, "relief"),
    NOIR("Noir", C_BLACK, "rochers et constructions"),
    VIOLET("Violet", C_PURPLE, "le parcours")
}

class Sym(val id: String, val level: Int, val fam: Fam, val name: String, val tip: String, val draw: DrawScope.() -> Unit)

/* ---------- Petits outils de dessin (coordonnées de 0 à 100) ---------- */

private fun DrawScope.u(): Float = size.minDimension / 100f
private fun DrawScope.o(x: Float, y: Float) = Offset(x * size.width / 100f, y * size.height / 100f)

private fun DrawScope.path(vararg pts: Pair<Float, Float>, close: Boolean = false): Path = Path().apply {
    pts.forEachIndexed { i, (x, y) -> val q = o(x, y); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
    if (close) close()
}

private fun DrawScope.wave(y: Float, amp: Float = 8f, x0: Float = 8f, x1: Float = 92f): Path = Path().apply {
    val a = o(x0, y); val c1 = o(x0 + (x1 - x0) / 3, y - amp); val c2 = o(x0 + 2 * (x1 - x0) / 3, y + amp); val b = o(x1, y)
    moveTo(a.x, a.y); cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
}

private fun DrawScope.stroke(p: Path, c: Color, w: Float, dash: FloatArray? = null) =
    drawPath(
        p, c,
        style = Stroke(
            width = w * u(), cap = StrokeCap.Butt,
            pathEffect = dash?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { d[it] * u() }) }
        )
    )

private fun DrawScope.fill(c: Color, x0: Float = 10f, y0: Float = 10f, x1: Float = 90f, y1: Float = 90f) =
    drawRect(c, topLeft = o(x0, y0), size = Size((x1 - x0) * size.width / 100f, (y1 - y0) * size.height / 100f))

private fun DrawScope.dot(x: Float, y: Float, r: Float, c: Color) = drawCircle(c, r * u(), o(x, y))

private fun DrawScope.ring(x: Float, y: Float, r: Float, c: Color, w: Float) =
    drawCircle(c, r * u(), o(x, y), style = Stroke(w * u()))

private fun DrawScope.lake(): Path = Path().apply {
    val a = o(15f, 50f); moveTo(a.x, a.y)
    val p1 = listOf(o(15f, 22f), o(55f, 14f), o(76f, 28f))
    cubicTo(p1[0].x, p1[0].y, p1[1].x, p1[1].y, p1[2].x, p1[2].y)
    val p2 = listOf(o(95f, 40f), o(90f, 76f), o(60f, 79f))
    cubicTo(p2[0].x, p2[0].y, p2[1].x, p2[1].y, p2[2].x, p2[2].y)
    val p3 = listOf(o(34f, 82f), o(15f, 72f), o(15f, 50f))
    cubicTo(p3[0].x, p3[0].y, p3[1].x, p3[1].y, p3[2].x, p3[2].y)
    close()
}

/** Trait horizontal avec des dents (falaise, talus, clôture). */
private fun DrawScope.toothed(c: Color, w: Float, tooth: Float, up: Boolean = false) {
    stroke(path(10f to 45f, 90f to 45f), c, w)
    var x = 15f
    while (x < 90f) {
        stroke(path(x to 45f, x to (if (up) 45f - tooth else 45f + tooth)), c, w * 0.7f)
        x += 12f
    }
}

private val SCATTER = listOf(
    18f to 22f, 40f to 18f, 66f to 24f, 82f to 40f, 28f to 44f, 52f to 40f,
    74f to 60f, 20f to 66f, 44f to 62f, 62f to 80f, 34f to 84f, 84f to 80f
)

private fun DrawScope.tri(x: Float, y: Float, s: Float, c: Color) =
    drawPath(path(x to y - s, x + s to y + s * 0.8f, x - s to y + s * 0.8f, close = true), c)

private fun DrawScope.vee(c: Color) = stroke(path(35f to 35f, 50f to 65f, 65f to 35f), c, 6f)

/* ---------- Le catalogue, dans l'ordre d'apprentissage conseillé ---------- */

object Ori {
    val LEVELS = listOf(
        "Les couleurs de la carte",
        "Le parcours et les chemins",
        "La végétation",
        "L'eau",
        "Le relief",
        "Les rochers"
    )
    val LEVEL_WHY = listOf(
        "La base : chaque couleur a un sens.",
        "Pour ta première course : départ, postes, arrivée, zones interdites et chemins.",
        "Pour choisir où courir : le blanc est rapide, le vert foncé est à éviter.",
        "Des repères faciles à reconnaître sur le terrain.",
        "Plus difficile : lire la forme du terrain avec les courbes marron.",
        "Des repères précis, souvent choisis pour placer les postes."
    )

    val ALL: List<Sym> = listOf(
        // 1. Les couleurs
        Sym("fam_blanc", 1, Fam.BLANC, "Blanc : la forêt où l'on court bien", "Pas de couleur, pas d'obstacle : la forêt est facile à traverser.") {
            fill(Color.White, 12f, 12f, 88f, 88f); stroke(path(12f to 12f, 88f to 12f, 88f to 88f, 12f to 88f, close = true), C_GREY, 1.2f)
        },
        Sym("fam_jaune", 1, Fam.JAUNE, "Jaune : terrain découvert (prairie, clairière)", "Le jaune, c'est le soleil : pas d'arbres au-dessus de toi.") { fill(C_YEL) },
        Sym("fam_vert", 1, Fam.VERT, "Vert : végétation qui ralentit", "Plus le vert est foncé, plus il est difficile de passer.") {
            fill(C_G1, 10f, 10f, 37f, 90f); fill(C_G2, 37f, 10f, 64f, 90f); fill(C_G3, 64f, 10f, 90f, 90f)
        },
        Sym("fam_bleu", 1, Fam.BLEU, "Bleu : l'eau", "Lac, ruisseau, marais, puits : tout ce qui est bleu est de l'eau.") {
            drawPath(lake(), C_BLUE_L); stroke(lake(), C_BLUE, 3f); stroke(wave(88f, 4f), C_BLUE, 3f)
        },
        Sym("fam_marron", 1, Fam.MARRON, "Marron : le relief (la forme du terrain)", "Le marron, c'est la terre : bosses, creux, talus.") {
            stroke(wave(30f), C_BROWN, 2.5f); stroke(wave(50f), C_BROWN, 4f); stroke(wave(70f), C_BROWN, 2.5f)
        },
        Sym("fam_noir", 1, Fam.NOIR, "Noir : rochers et constructions", "Chemins, bâtiments, clôtures et rochers sont en noir.") {
            stroke(wave(70f, 6f), C_BLACK, 3f, floatArrayOf(10f, 4f)); drawRect(C_BLACK, o(20f, 20f), Size(26f * u(), 18f * u())); dot(72f, 30f, 7f, C_BLACK)
        },
        Sym("fam_violet", 1, Fam.VIOLET, "Violet : ton parcours", "Le violet est ajouté par l'organisateur : départ, postes, arrivée.") {
            stroke(path(18f to 78f, 30f to 58f, 6f to 58f, close = true), C_PURPLE, 3f)
            stroke(path(24f to 58f, 42f to 38f), C_PURPLE, 2f); ring(50f, 30f, 10f, C_PURPLE, 3f)
            stroke(path(59f to 35f, 72f to 58f), C_PURPLE, 2f); ring(78f, 68f, 11f, C_PURPLE, 3f); ring(78f, 68f, 6f, C_PURPLE, 3f)
        },

        // 2. Le parcours et les chemins
        Sym("depart", 2, Fam.VIOLET, "Départ", "Un triangle violet : c'est là que tu commences.") {
            stroke(path(50f to 18f, 84f to 78f, 16f to 78f, close = true), C_PURPLE, 5f)
        },
        Sym("poste", 2, Fam.VIOLET, "Poste (balise)", "Un cercle violet : la balise est au centre du cercle.") { ring(50f, 50f, 30f, C_PURPLE, 5f) },
        Sym("arrivee", 2, Fam.VIOLET, "Arrivée", "Deux cercles l'un dans l'autre : c'est fini !") {
            ring(50f, 50f, 32f, C_PURPLE, 5f); ring(50f, 50f, 19f, C_PURPLE, 5f)
        },
        Sym("ligne", 2, Fam.VIOLET, "Ligne de parcours", "Le trait violet relie les postes dans l'ordre. Ce n'est pas un chemin !") {
            ring(22f, 72f, 12f, C_PURPLE, 3.5f); stroke(path(31f to 63f, 66f to 34f), C_PURPLE, 3f); ring(75f, 26f, 12f, C_PURPLE, 3.5f)
        },
        Sym("interdit", 2, Fam.VIOLET, "Zone interdite", "Des rayures violettes : on n'y entre jamais !") {
            var x = 16f; while (x < 88f) { stroke(path(x to 14f, x to 86f), C_PURPLE, 3.5f); x += 9f }
            stroke(path(12f to 14f, 88f to 14f, 88f to 86f, 12f to 86f, close = true), C_PURPLE, 2.5f)
        },
        Sym("route", 2, Fam.NOIR, "Route", "Un trait noir plein et épais.") { stroke(wave(50f, 10f), C_BLACK, 5f) },
        Sym("carrossable", 2, Fam.NOIR, "Chemin carrossable (large chemin)", "Grands tirets épais : une voiture peut y passer.") {
            stroke(wave(50f, 10f), C_BLACK, 4.5f, floatArrayOf(16f, 4f))
        },
        Sym("chemin", 2, Fam.NOIR, "Chemin", "Tirets moyens, plus fins que le chemin carrossable.") {
            stroke(wave(50f, 10f), C_BLACK, 3f, floatArrayOf(10f, 4.5f))
        },
        Sym("sentier", 2, Fam.NOIR, "Sentier", "Petits tirets fins : un petit chemin pour marcher.") {
            stroke(wave(50f, 10f), C_BLACK, 2f, floatArrayOf(5f, 4f))
        },
        Sym("batiment", 2, Fam.NOIR, "Bâtiment", "Un rectangle noir plein.") { drawRect(C_BLACK, o(26f, 30f), Size(48f * u(), 36f * u())) },
        Sym("cloture", 2, Fam.NOIR, "Clôture", "Un trait noir fin avec de petites dents.") { toothed(C_BLACK, 2.2f, 8f, up = true) },

        // 3. La végétation
        Sym("foret", 3, Fam.BLANC, "Forêt, course facile", "Blanc : on court vite entre les arbres.") {
            fill(Color.White, 12f, 12f, 88f, 88f); stroke(path(12f to 12f, 88f to 12f, 88f to 88f, 12f to 88f, close = true), C_GREY, 1.2f)
        },
        Sym("lente", 3, Fam.VERT, "Végétation, course lente", "Vert clair : ça ralentit un peu.") { fill(C_G1) },
        Sym("marche", 3, Fam.VERT, "Végétation, marche", "Vert moyen : on ne peut que marcher.") { fill(C_G2) },
        Sym("difficile", 3, Fam.VERT, "Végétation, progression difficile", "Vert foncé : presque impossible, fais le tour !") { fill(C_G3) },
        Sym("decouvert", 3, Fam.JAUNE, "Terrain découvert", "Jaune vif : prairie, on voit loin.") { fill(C_YEL) },
        Sym("encombre", 3, Fam.JAUNE, "Terrain découvert encombré", "Jaune pâle : herbes hautes ou fougères.") { fill(C_YEL_L) },
        Sym("arbres_disp", 3, Fam.JAUNE, "Terrain découvert avec arbres dispersés", "Jaune avec des ronds blancs : quelques arbres.") {
            fill(C_YEL); SCATTER.take(8).forEach { (x, y) -> dot(x, y, 5f, Color.White) }
        },
        Sym("limite", 3, Fam.NOIR, "Limite de végétation", "Des pointillés noirs : la végétation change d'un coup.") {
            fill(C_G2, 10f, 10f, 90f, 50f); fill(Color.White, 10f, 50f, 90f, 90f)
            stroke(path(10f to 50f, 90f to 50f), C_BLACK, 2.2f, floatArrayOf(2.2f, 3f))
        },
        Sym("gros_arbre", 3, Fam.VERT, "Gros arbre remarquable", "Un rond vert : un arbre que l'on remarque.") { ring(50f, 50f, 16f, C_G3, 5f) },
        Sym("buisson", 3, Fam.VERT, "Arbre ou buisson remarquable", "Un point vert.") { dot(50f, 50f, 11f, C_G3) },

        // 4. L'eau
        Sym("lac", 4, Fam.BLEU, "Étendue d'eau infranchissable (lac)", "Bleu entouré de noir : on ne peut pas traverser.") {
            drawPath(lake(), C_BLUE); stroke(lake(), C_BLACK, 3f)
        },
        Sym("eau_peu", 4, Fam.BLEU, "Étendue d'eau peu profonde", "Bleu clair avec un bord bleu, sans bord noir.") {
            drawPath(lake(), C_BLUE_L); stroke(lake(), C_BLUE, 2.5f)
        },
        Sym("ruisseau", 4, Fam.BLEU, "Cours d'eau franchissable", "Un trait bleu épais.") { stroke(wave(50f, 12f), C_BLUE, 5f) },
        Sym("petit_ruisseau", 4, Fam.BLEU, "Petit cours d'eau", "Un trait bleu fin.") { stroke(wave(50f, 12f), C_BLUE, 2.2f) },
        Sym("saisonnier", 4, Fam.BLEU, "Cours d'eau saisonnier (parfois à sec)", "Un trait bleu en tirets.") {
            stroke(wave(50f, 12f), C_BLUE, 2.5f, floatArrayOf(9f, 4f))
        },
        Sym("marais", 4, Fam.BLEU, "Marais", "Petits traits bleus horizontaux : le sol est mouillé.") {
            for (k in 0..5) {
                val y = 18f + k * 13f; val sh = if (k % 2 == 0) 0f else 7f
                stroke(path((12f + sh) to y, (88f + sh - 7f) to y), C_BLUE, 2.5f, floatArrayOf(12f, 5f))
            }
        },
        Sym("trou_eau", 4, Fam.BLEU, "Trou d'eau", "Un petit V bleu.") { vee(C_BLUE) },
        Sym("puits", 4, Fam.BLEU, "Puits, fontaine ou réservoir", "Un petit carré bleu.") {
            stroke(path(36f to 36f, 64f to 36f, 64f to 64f, 36f to 64f, close = true), C_BLUE, 5f)
        },

        // 5. Le relief
        Sym("courbe", 5, Fam.MARRON, "Courbe de niveau", "Une ligne marron qui relie les points de même hauteur.") { stroke(wave(50f, 14f), C_BROWN, 2.8f) },
        Sym("maitresse", 5, Fam.MARRON, "Courbe maîtresse", "Plus épaisse, une courbe sur cinq : elle aide à compter.") {
            stroke(wave(32f, 10f), C_BROWN, 2.2f); stroke(wave(56f, 10f), C_BROWN, 5f); stroke(wave(78f, 10f), C_BROWN, 2.2f)
        },
        Sym("tiret", 5, Fam.MARRON, "Tiret de pente", "Le petit trait montre le côté qui descend.") {
            stroke(wave(45f, 10f), C_BROWN, 2.8f); stroke(path(50f to 45f, 50f to 60f), C_BROWN, 2.8f)
        },
        Sym("colline", 5, Fam.MARRON, "Colline (courbe fermée)", "Une courbe qui fait le tour : le sommet est au milieu.") {
            drawOval(C_BROWN, o(18f, 28f), Size(64f * u(), 44f * u()), style = Stroke(3f * u()))
            drawOval(C_BROWN, o(34f, 39f), Size(32f * u(), 22f * u()), style = Stroke(3f * u()))
        },
        Sym("butte", 5, Fam.MARRON, "Petite butte", "Un point marron : une petite bosse.") { dot(50f, 50f, 10f, C_BROWN) },
        Sym("butte_allongee", 5, Fam.MARRON, "Petite butte allongée", "Un petit trait marron épais : une bosse allongée.") {
            drawOval(C_BROWN, o(28f, 42f), Size(44f * u(), 16f * u()))
        },
        Sym("depression", 5, Fam.MARRON, "Petite dépression", "Un U marron : un petit creux.") {
            drawArc(C_BROWN, 0f, 180f, false, o(30f, 25f), Size(40f * u(), 40f * u()), style = Stroke(6f * u()))
        },
        Sym("trou", 5, Fam.MARRON, "Trou", "Un V marron : un trou dans la terre.") { vee(C_BROWN) },
        Sym("talus", 5, Fam.MARRON, "Talus (abrupt de terre)", "Un trait marron avec des dents : les dents descendent la pente.") { toothed(C_BROWN, 3f, 12f) },
        Sym("accidente", 5, Fam.MARRON, "Terrain accidenté", "Des points marron : le sol est plein de bosses.") {
            SCATTER.forEach { (x, y) -> dot(x, y, 3.2f, C_BROWN) }
        },

        // 6. Les rochers
        Sym("falaise_inf", 6, Fam.NOIR, "Falaise infranchissable", "Trait noir épais avec des dents : danger, on ne passe pas !") { toothed(C_BLACK, 5f, 14f) },
        Sym("falaise", 6, Fam.NOIR, "Falaise", "Trait noir fin avec des dents : on peut parfois passer.") { toothed(C_BLACK, 2.2f, 10f) },
        Sym("bloc", 6, Fam.NOIR, "Bloc rocheux", "Un point noir : un gros caillou.") { dot(50f, 50f, 8f, C_BLACK) },
        Sym("gros_bloc", 6, Fam.NOIR, "Gros bloc rocheux", "Un point noir plus gros.") { dot(50f, 50f, 15f, C_BLACK) },
        Sym("groupe_blocs", 6, Fam.NOIR, "Groupe de blocs rocheux", "Un triangle noir plein.") { tri(50f, 50f, 20f, C_BLACK) },
        Sym("zone_blocs", 6, Fam.NOIR, "Zone de blocs rocheux", "Plein de petits triangles noirs.") {
            SCATTER.take(9).forEach { (x, y) -> tri(x, y, 5f, C_BLACK) }
        },
        Sym("pierreux", 6, Fam.NOIR, "Sol pierreux", "De petits points noirs.") { SCATTER.forEach { (x, y) -> dot(x, y, 2.6f, C_BLACK) } },
        Sym("trou_rocheux", 6, Fam.NOIR, "Trou rocheux", "Un V noir.") { vee(C_BLACK) },
        Sym("affleurement", 6, Fam.NOIR, "Affleurement rocheux (roche nue)", "Du gris : de la roche à nu.") { drawPath(lake(), C_GREY) }
    )

    fun level(n: Int): List<Sym> = ALL.filter { it.level == n }
    fun byId(id: String): Sym = ALL.first { it.id == id }
}

/* =====================================================================
 *  Jeux
 * ===================================================================== */

enum class OriScreen { MENU, LEARN, PAIRS, MEMORY, QUIZ, RESULT }
enum class QType { SYM_TO_NAME, NAME_TO_SYM, SYM_TO_FAM }

class OriQ(val sym: Sym, val type: QType, val choices: List<Sym>, val fams: List<Fam>)
class MemCard(val sym: Sym, val isName: Boolean)

class OriGame(private val progress: Progress, private val speaker: Speaker) {
    private val h = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(OriScreen.MENU)
    var level by mutableIntStateOf(1) // 0 = révision du jour
    var title by mutableStateOf("")
    private var lastGame = OriScreen.QUIZ

    // Apprendre
    var learnList by mutableStateOf<List<Sym>>(emptyList())
    var learnIdx by mutableIntStateOf(0)
    var learnShown by mutableStateOf(false)

    // Paires
    private var pairsRounds: List<List<Sym>> = emptyList()
    var pairsRound by mutableIntStateOf(0)
    var pairsTotalRounds by mutableIntStateOf(0)
    var left by mutableStateOf<List<Sym>>(emptyList())
    var right by mutableStateOf<List<Sym>>(emptyList())
    val matched = mutableStateListOf<String>()
    var selLeft by mutableStateOf<String?>(null)
    var selRight by mutableStateOf<String?>(null)
    var wrongL by mutableStateOf<String?>(null)
    var wrongR by mutableStateOf<String?>(null)
    private val erred = mutableSetOf<String>()

    // Memory
    var mem by mutableStateOf<List<MemCard>>(emptyList())
    val memUp = mutableStateListOf<Int>()
    val memDone = mutableStateListOf<String>()
    var memTries by mutableIntStateOf(0)
    private var memBusy = false

    // Quiz
    var quiz by mutableStateOf<List<OriQ>>(emptyList())
    var qIdx by mutableIntStateOf(0)
    var qPicked by mutableStateOf<Int?>(null)

    // Résultat
    var errors by mutableIntStateOf(0)
    var good by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)

    val atMenu: Boolean get() = screen == OriScreen.MENU

    fun back() { h.removeCallbacksAndMessages(null); speaker.stop(); screen = OriScreen.MENU }

    fun known(n: Int): Int = Ori.level(n).count { progress.oriBox(it.id) >= 3 }
    fun dueList(): List<Sym> = Ori.ALL.filter { progress.oriDue(it.id) }

    /** Le premier niveau pas encore bien su : c'est le niveau conseillé. */
    fun nextLevel(): Int = (1..6).firstOrNull { known(it) < Ori.level(it).size } ?: 6

    /** Révision du jour : les symboles à revoir, plus 3 nouveaux du niveau conseillé (pratique espacée et mélangée). */
    private fun dailySet(): List<Sym> {
        val due = dueList().sortedBy { progress.oriBox(it.id) }.take(9)
        val fresh = Ori.level(nextLevel()).filter { progress.oriBox(it.id) == 0 }.take(3)
        var set = (due + fresh).distinct()
        if (set.size < 8) set = set + Ori.ALL.filter { it.level <= nextLevel() && it !in set }
            .sortedBy { progress.oriBox(it.id) }.take(8 - set.size)
        return set.shuffled()
    }

    private fun setFor(lv: Int): List<Sym> = if (lv == 0) dailySet() else Ori.level(lv)
    private fun titleFor(lv: Int): String = if (lv == 0) "Révision du jour" else Ori.LEVELS[lv - 1]

    /* ----- Apprendre : on devine d'abord, puis on retourne la carte ----- */

    fun startLearn(lv: Int) {
        level = lv; title = titleFor(lv)
        learnList = setFor(lv); learnIdx = 0; learnShown = false
        screen = OriScreen.LEARN
    }

    fun learnFlip() { learnShown = !learnShown }
    fun learnMove(d: Int) {
        learnIdx = (learnIdx + d).coerceIn(0, learnList.size - 1); learnShown = false; speaker.stop()
    }
    fun say(text: String) { speaker.pitch = 1.0f; speaker.speak(text) {} }

    /* ----- Paires : 5 symboles à relier à 5 noms ----- */

    fun startPairs(lv: Int) {
        level = lv; title = titleFor(lv); lastGame = OriScreen.PAIRS
        val set = setFor(lv).shuffled()
        pairsRounds = set.chunked(5).filter { it.size >= 3 }.ifEmpty { listOf(set) }
        pairsTotalRounds = pairsRounds.size; pairsRound = 0
        errors = 0; good = 0; total = set.size; erred.clear()
        loadPairs()
        screen = OriScreen.PAIRS
    }

    private fun loadPairs() {
        val r = pairsRounds[pairsRound]
        left = r.shuffled(); right = r.shuffled()
        matched.clear(); selLeft = null; selRight = null; wrongL = null; wrongR = null
    }

    fun pickLeft(id: String) { if (id in matched) return; selLeft = id; tryMatch() }
    fun pickRight(id: String) { if (id in matched) return; selRight = id; tryMatch() }

    private fun tryMatch() {
        val l = selLeft ?: return; val r = selRight ?: return
        if (l == r) {
            matched.add(l)
            if (l !in erred) good++
            progress.recordOri(l, l !in erred)
            selLeft = null; selRight = null
            if (matched.size == left.size) h.postDelayed({ nextPairs() }, 700)
        } else {
            errors++; erred.add(l); erred.add(r)
            wrongL = l; wrongR = r
            h.postDelayed({ wrongL = null; wrongR = null; selLeft = null; selRight = null }, 700)
        }
    }

    private fun nextPairs() {
        if (pairsRound + 1 < pairsRounds.size) { pairsRound++; loadPairs() } else finish()
    }

    /* ----- Memory : retrouver les paires symbole / nom face cachée ----- */

    fun startMemory(lv: Int) {
        level = lv; title = titleFor(lv); lastGame = OriScreen.MEMORY
        val six = setFor(lv).shuffled().take(6)
        mem = (six.map { MemCard(it, false) } + six.map { MemCard(it, true) }).shuffled()
        memUp.clear(); memDone.clear(); memTries = 0; memBusy = false
        errors = 0; good = 0; total = six.size
        screen = OriScreen.MEMORY
    }

    fun memTap(i: Int) {
        if (memBusy || i in memUp || mem[i].sym.id in memDone) return
        memUp.add(i)
        if (memUp.size == 2) {
            memTries++
            val a = mem[memUp[0]]; val b = mem[memUp[1]]
            if (a.sym.id == b.sym.id && a.isName != b.isName) {
                memDone.add(a.sym.id); good++; memUp.clear()
                if (memDone.size == total) h.postDelayed({ errors = memTries - total; finish() }, 800)
            } else {
                memBusy = true
                h.postDelayed({ memUp.clear(); memBusy = false }, 1100)
            }
        }
    }

    /* ----- Quiz : trouver le bon symbole, le bon nom ou la bonne couleur ----- */

    fun startQuiz(lv: Int) {
        level = lv; title = titleFor(lv); lastGame = OriScreen.QUIZ
        val set = setFor(lv).shuffled().take(8)
        quiz = set.mapIndexed { k, s ->
            val type = when {
                s.level == 1 -> if (k % 2 == 0) QType.NAME_TO_SYM else QType.SYM_TO_NAME
                k % 4 == 3 -> QType.SYM_TO_FAM
                k % 2 == 0 -> QType.NAME_TO_SYM
                else -> QType.SYM_TO_NAME
            }
            // Distracteurs de la même couleur d'abord : on apprend à distinguer ce qui se ressemble.
            val same = Ori.ALL.filter { it.id != s.id && it.fam == s.fam && it.level > 1 == s.level > 1 }.shuffled()
            val other = Ori.ALL.filter { it.id != s.id && it !in same && it.level > 1 == s.level > 1 }.shuffled()
            val choices = (listOf(s) + (same + other).take(3)).shuffled()
            val fams = (listOf(s.fam) + Fam.entries.filter { it != s.fam }.shuffled().take(3)).shuffled()
            OriQ(s, type, choices, fams)
        }
        qIdx = 0; qPicked = null; errors = 0; good = 0; total = quiz.size
        screen = OriScreen.QUIZ
    }

    fun answer(k: Int) {
        if (qPicked != null) return
        qPicked = k
        val q = quiz[qIdx]
        val ok = if (q.type == QType.SYM_TO_FAM) q.fams[k] == q.sym.fam else q.choices[k].id == q.sym.id
        if (ok) good++ else errors++
        progress.recordOri(q.sym.id, ok)
        if (ok) h.postDelayed({ nextQ() }, 1400)
    }

    fun nextQ() {
        h.removeCallbacksAndMessages(null)
        if (qIdx + 1 < quiz.size) { qIdx++; qPicked = null } else finish()
    }

    private fun finish() { screen = OriScreen.RESULT }

    fun replay() {
        when (lastGame) {
            OriScreen.PAIRS -> startPairs(level)
            OriScreen.MEMORY -> startMemory(level)
            else -> startQuiz(level)
        }
    }

    val stars: Int get() = when {
        lastGame == OriScreen.MEMORY -> if (errors <= 2) 3 else if (errors <= 6) 2 else 1
        errors == 0 -> 3
        errors <= 2 -> 2
        else -> 1
    }
}

/* =====================================================================
 *  Écrans
 * ===================================================================== */

@Composable
fun SymbolTile(sym: Sym, size: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFAFAF7))
            .border(1.dp, O_LINE, RoundedCornerShape(10.dp))
    ) { sym.draw(this) }
}

@Composable
private fun OriTop(title: String, sub: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = O_INK) }
        Text("🧭", fontSize = 26.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = O_INK, maxLines = 1)
            Text(sub, fontSize = 12.sp, color = O_SUB, maxLines = 1)
        }
    }
}

@Composable
private fun OButton(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) bg else O_LINE)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg, textAlign = TextAlign.Center) }
}

@Composable
fun OriApp(g: Game) {
    val o = g.ori
    Box(Modifier.fillMaxSize().background(O_BG)) {
        when (o.screen) {
            OriScreen.MENU -> OriMenu(g)
            OriScreen.LEARN -> OriLearn(o)
            OriScreen.PAIRS -> OriPairs(o)
            OriScreen.MEMORY -> OriMemory(o)
            OriScreen.QUIZ -> OriQuiz(o)
            OriScreen.RESULT -> OriResult(o)
        }
    }
}

@Composable
private fun OriMenu(g: Game) {
    val o = g.ori
    val ver = g.progress.version
    val due = remember(ver) { o.dueList().size }
    val next = remember(ver) { o.nextLevel() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OriTop("Course d'orientation", "Les symboles des cartes", { g.goHome() })
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(O_ACCENT).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🧠  Révision du jour", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (due > 0) "$due symbole${if (due > 1) "s" else ""} à revoir + quelques nouveaux" else "Quelques symboles choisis pour toi",
                fontSize = 13.sp, color = Color.White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OButton("Paires", Color.White, O_ACCENT, Modifier.weight(1f)) { o.startPairs(0) }
                OButton("Quiz", Color.White, O_ACCENT, Modifier.weight(1f)) { o.startQuiz(0) }
            }
        }
        Ori.LEVELS.forEachIndexed { k, name ->
            val lv = k + 1
            val n = Ori.level(lv).size
            val kn = remember(ver, lv) { o.known(lv) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .border(2.dp, if (lv == next) O_ACCENT else Color.White, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SymbolTile(Ori.level(lv).first(), 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text("$lv. $name" + if (lv == next) "  · conseillé" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = O_INK)
                        Text(Ori.LEVEL_WHY[k], fontSize = 12.sp, lineHeight = 14.sp, color = O_SUB)
                    }
                    Text("$kn/$n", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (kn == n) O_GOOD else O_SUB)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OButton("Apprendre", O_INK, Color.White, Modifier.weight(1f)) { o.startLearn(lv) }
                    OButton("Paires", O_BG, O_INK, Modifier.weight(1f)) { o.startPairs(lv) }
                    OButton("Memory", O_BG, O_INK, Modifier.weight(1f)) { o.startMemory(lv) }
                    OButton("Quiz", O_BG, O_INK, Modifier.weight(1f)) { o.startQuiz(lv) }
                }
            }
        }
        Text(
            "Bientôt : niveau 7, les définitions de postes (symboles IOF).",
            fontSize = 12.sp, color = O_SUB, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OriLearn(o: OriGame) {
    val list = o.learnList
    if (list.isEmpty()) { o.back(); return }
    val s = list[o.learnIdx]
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OriTop(o.title, "Symbole ${o.learnIdx + 1} sur ${list.size}", { o.back() })
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                .clickable { o.learnFlip() }.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            SymbolTile(s, 200.dp)
            if (!o.learnShown) {
                Text("Qu'est-ce que c'est ? Devine, puis touche la carte.", fontSize = 16.sp, color = O_SUB, textAlign = TextAlign.Center)
            } else {
                Text(s.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = O_INK, textAlign = TextAlign.Center)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(s.fam.color).border(1.dp, O_LINE, RoundedCornerShape(4.dp)))
                    Text("Famille ${s.fam.label.lowercase()} : ${s.fam.meaning}", fontSize = 14.sp, color = O_SUB)
                }
                Text("💡 ${s.tip}", fontSize = 16.sp, lineHeight = 21.sp, color = O_INK, textAlign = TextAlign.Center)
                OButton("🔊 Écouter", O_BG, O_INK) { o.say(s.name + ". " + s.tip) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OButton("◀ Précédent", Color.White, O_INK, Modifier.weight(1f), enabled = o.learnIdx > 0) { o.learnMove(-1) }
            OButton("Suivant ▶", O_INK, Color.White, Modifier.weight(1f), enabled = o.learnIdx < list.size - 1) { o.learnMove(1) }
        }
    }
}

private val PAIR_COLORS = listOf(Color(0xFFFFD6A5), Color(0xFFCAFFBF), Color(0xFF9BF6FF), Color(0xFFBDB2FF), Color(0xFFFFC6FF))

@Composable
private fun OriPairs(o: OriGame) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OriTop(o.title, "Paires · manche ${o.pairsRound + 1} sur ${o.pairsTotalRounds}", { o.back() })
        Text(
            "Touche un symbole, puis son nom.", fontSize = 15.sp, color = O_SUB,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(96.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                o.left.forEach { s ->
                    val mi = o.matched.indexOf(s.id)
                    val edge = when {
                        o.wrongL == s.id -> O_BAD
                        mi >= 0 -> O_GOOD
                        o.selLeft == s.id -> O_ACCENT
                        else -> O_LINE
                    }
                    Box(
                        Modifier.size(88.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (mi >= 0) PAIR_COLORS[mi % 5] else Color.White)
                            .border(3.dp, edge, RoundedCornerShape(14.dp))
                            .clickable { o.pickLeft(s.id) },
                        contentAlignment = Alignment.Center
                    ) { SymbolTile(s, 72.dp) }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                o.right.forEach { s ->
                    val mi = o.matched.indexOf(s.id)
                    val edge = when {
                        o.wrongR == s.id -> O_BAD
                        mi >= 0 -> O_GOOD
                        o.selRight == s.id -> O_ACCENT
                        else -> O_LINE
                    }
                    Box(
                        Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (mi >= 0) PAIR_COLORS[mi % 5] else Color.White)
                            .border(3.dp, edge, RoundedCornerShape(14.dp))
                            .clickable { o.pickRight(s.id) }.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(s.name, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = O_INK, textAlign = TextAlign.Center) }
                }
            }
        }
        Text("Erreurs : ${o.errors}", fontSize = 13.sp, color = O_SUB, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun OriMemory(o: OriGame) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OriTop(o.title, "Memory · paires trouvées ${o.memDone.size} sur ${o.total} · essais ${o.memTries}", { o.back() })
        Text(
            "Retourne deux cartes : un symbole et son nom.", fontSize = 15.sp, color = O_SUB,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            o.mem.indices.chunked(3).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i ->
                        val c = o.mem[i]
                        val done = c.sym.id in o.memDone
                        val up = i in o.memUp || done
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(14.dp))
                                .background(if (!up) O_INK else if (done) Color(0xFFE6F6EC) else Color.White)
                                .border(2.dp, if (done) O_GOOD else O_LINE, RoundedCornerShape(14.dp))
                                .clickable { o.memTap(i) }.padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                !up -> Text("🧭", fontSize = 30.sp)
                                c.isName -> Text(c.sym.name, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = O_INK, textAlign = TextAlign.Center)
                                else -> SymbolTile(c.sym, 70.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OriQuiz(o: OriGame) {
    if (o.quiz.isEmpty()) { o.back(); return }
    val q = o.quiz[o.qIdx]
    val picked = o.qPicked
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OriTop(o.title, "Question ${o.qIdx + 1} sur ${o.quiz.size} · ✓ ${o.good}", { o.back() })
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (q.type) {
                QType.NAME_TO_SYM -> {
                    Text("Quel est le symbole de :", fontSize = 15.sp, color = O_SUB)
                    Text(q.sym.name, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = O_INK, textAlign = TextAlign.Center)
                }
                QType.SYM_TO_NAME -> {
                    Text("Que veut dire ce symbole ?", fontSize = 15.sp, color = O_SUB)
                    SymbolTile(q.sym, 130.dp)
                }
                QType.SYM_TO_FAM -> {
                    Text("De quelle famille de couleur est ce symbole ?", fontSize = 15.sp, color = O_SUB, textAlign = TextAlign.Center)
                    SymbolTile(q.sym, 130.dp)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 2).forEach { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in r..r + 1) {
                        val right = if (q.type == QType.SYM_TO_FAM) q.fams[k] == q.sym.fam else q.choices[k].id == q.sym.id
                        val edge = when {
                            picked == null -> O_LINE
                            right -> O_GOOD
                            picked == k -> O_BAD
                            else -> O_LINE
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White)
                                .border(if (picked != null && (right || picked == k)) 4.dp else 2.dp, edge, RoundedCornerShape(16.dp))
                                .clickable { o.answer(k) }.padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (q.type) {
                                QType.NAME_TO_SYM -> SymbolTile(q.choices[k], 100.dp)
                                QType.SYM_TO_NAME -> Text(
                                    q.choices[k].name, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                                    color = O_INK, textAlign = TextAlign.Center
                                )
                                QType.SYM_TO_FAM -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(q.fams[k].color).border(1.dp, O_LINE, RoundedCornerShape(10.dp)))
                                    Text(q.fams[k].label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = O_INK)
                                    Text(q.fams[k].meaning, fontSize = 11.sp, color = O_SUB, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (picked != null) {
            val ok = if (q.type == QType.SYM_TO_FAM) q.fams[picked] == q.sym.fam else q.choices[picked].id == q.sym.id
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (ok) Color(0xFFE6F6EC) else Color(0xFFFBE7E9)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (ok) "Bravo !" else "C'était : ${q.sym.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = O_INK)
                Text("💡 ${q.sym.tip}", fontSize = 14.sp, lineHeight = 18.sp, color = O_INK)
                if (!ok) OButton("Compris ▶", O_INK, Color.White, Modifier.fillMaxWidth()) { o.nextQ() }
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun OriResult(o: OriGame) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(o.title, fontSize = 16.sp, color = O_SUB)
        Text("★".repeat(o.stars) + "☆".repeat(3 - o.stars), fontSize = 54.sp, color = Color(0xFFE0A100))
        Text(
            when (o.stars) { 3 -> "Parfait !"; 2 -> "Bravo !"; else -> "Bien joué, continue !" },
            fontSize = 36.sp, fontWeight = FontWeight.Bold, color = O_INK
        )
        Text("${o.good} sur ${o.total} trouvés du premier coup", fontSize = 17.sp, color = O_INK)
        Text(
            "Les symboles ratés reviendront bientôt dans la révision du jour.",
            fontSize = 14.sp, color = O_SUB, textAlign = TextAlign.Center
        )
        OButton("🔁  Rejouer", O_ACCENT, Color.White, Modifier.fillMaxWidth()) { o.replay() }
        OButton("Menu des symboles", Color.White, O_INK, Modifier.fillMaxWidth()) { o.back() }
    }
}
