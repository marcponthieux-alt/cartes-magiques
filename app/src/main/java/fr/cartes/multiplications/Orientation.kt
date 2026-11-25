package fr.cartes.multiplications

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        "Les rochers",
        "Les définitions de postes"
    )
    val LEVEL_WHY = listOf(
        "La base : chaque couleur a un sens.",
        "Pour ta première course : départ, postes, arrivée, zones interdites et chemins.",
        "Pour choisir où courir : le blanc est rapide, le vert foncé est à éviter.",
        "Des repères faciles à reconnaître sur le terrain.",
        "Plus difficile : lire la forme du terrain avec les courbes marron.",
        "Des repères précis, souvent choisis pour placer les postes.",
        "La feuille de définitions (IOF 2024) : quel élément, et où est la balise."
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
        Sym("affleurement", 6, Fam.NOIR, "Affleurement rocheux (roche nue)", "Du gris : de la roche à nu.") { drawPath(lake(), C_GREY) },

        // 7. Définitions de postes (IOF 2024) — colonne D : l'élément ; colonne G : où est la balise
        Sym("d_colline", 7, Fam.NOIR, "Colline (colonne D)", "Une forme ovale : comme la courbe fermée de la carte.") {
            drawOval(C_BLACK, o(20f, 32f), Size(60f * u(), 36f * u()), style = Stroke(5f * u()))
        },
        Sym("d_butte", 7, Fam.NOIR, "Butte (colonne D)", "Un point plein : une petite bosse.") { dot(50f, 50f, 13f, C_BLACK) },
        Sym("d_depression", 7, Fam.NOIR, "Petite dépression (colonne D)", "Un U : un petit creux.") {
            drawArc(C_BLACK, 0f, 180f, false, o(28f, 22f), Size(44f * u(), 44f * u()), style = Stroke(6f * u()))
        },
        Sym("d_trou", 7, Fam.NOIR, "Trou (colonne D)", "Un V : un trou.") { vee(C_BLACK) },
        Sym("d_rentrant", 7, Fam.NOIR, "Rentrant (colonne D)", "Une forme en arche : un petit vallon qui entre dans la pente.") {
            val pth = Path().apply {
                val a = o(22f, 82f); moveTo(a.x, a.y)
                val c1 = o(22f, 18f); val c2 = o(78f, 18f); val b = o(78f, 82f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
            }
            drawPath(pth, C_BLACK, style = Stroke(6f * u()))
        },
        Sym("d_falaise", 7, Fam.NOIR, "Falaise (colonne D)", "Un trait avec des dents, comme sur la carte.") { toothed(C_BLACK, 5f, 16f) },
        Sym("d_rocher", 7, Fam.NOIR, "Rocher, bloc rocheux (colonne D)", "Un triangle plein.") { tri(50f, 52f, 24f, C_BLACK) },
        Sym("d_clairiere", 7, Fam.NOIR, "Clairière (colonne D)", "Un cercle en pointillés : un trou dans la forêt.") {
            drawCircle(C_BLACK, 28f * u(), o(50f, 50f), style = Stroke(5f * u(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * u(), 6f * u()))))
        },
        Sym("d_souche", 7, Fam.NOIR, "Souche (colonne D)", "Un cercle barré d'une croix.") {
            ring(50f, 50f, 28f, C_BLACK, 5f); stroke(path(31f to 31f, 69f to 69f), C_BLACK, 5f); stroke(path(69f to 31f, 31f to 69f), C_BLACK, 5f)
        },
        Sym("d_ruine", 7, Fam.NOIR, "Ruine (colonne D)", "Un carré en pointillés : un bâtiment écroulé.") {
            stroke(path(25f to 25f, 75f to 25f, 75f to 75f, 25f to 75f, close = true), C_BLACK, 5f, floatArrayOf(9f, 6f))
        },
        Sym("d_batiment", 7, Fam.NOIR, "Bâtiment (colonne D)", "Un carré plein.") { drawRect(C_BLACK, o(27f, 27f), Size(46f * u(), 46f * u())) },
        Sym("d_borne", 7, Fam.NOIR, "Borne (colonne D)", "Un cercle avec un point au milieu.") { ring(50f, 50f, 26f, C_BLACK, 5f); dot(50f, 50f, 7f, C_BLACK) },
        Sym("d_tour", 7, Fam.NOIR, "Tour (colonne D)", "Un T.") { stroke(path(22f to 24f, 78f to 24f), C_BLACK, 7f); stroke(path(50f to 24f, 50f to 82f), C_BLACK, 7f) },
        Sym("d_arbre", 7, Fam.NOIR, "Arbre remarquable (colonne D)", "Un petit arbre dessiné.") {
            stroke(path(50f to 86f, 50f to 52f), C_BLACK, 6f); ring(50f, 36f, 19f, C_BLACK, 5f)
        },
        Sym("g_nord", 7, Fam.NOIR, "Côté nord (colonne G)", "Le point est en haut : la balise est du côté nord de l'élément.") {
            ring(50f, 52f, 26f, C_BLACK, 4f); dot(50f, 26f, 8f, C_BLACK)
        },
        Sym("g_est", 7, Fam.NOIR, "Côté est (colonne G)", "Le point est à droite : côté est.") { ring(50f, 50f, 26f, C_BLACK, 4f); dot(76f, 50f, 8f, C_BLACK) },
        Sym("g_sud", 7, Fam.NOIR, "Côté sud (colonne G)", "Le point est en bas : côté sud.") { ring(50f, 48f, 26f, C_BLACK, 4f); dot(50f, 74f, 8f, C_BLACK) },
        Sym("g_ouest", 7, Fam.NOIR, "Côté ouest (colonne G)", "Le point est à gauche : côté ouest.") { ring(50f, 50f, 26f, C_BLACK, 4f); dot(24f, 50f, 8f, C_BLACK) },
        Sym("g_pied", 7, Fam.NOIR, "Au pied (colonne G)", "Un angle avec un point en bas : la balise est au pied (d'une falaise, d'un rocher…).") {
            stroke(path(30f to 16f, 30f to 80f, 84f to 80f), C_BLACK, 5f); dot(42f, 69f, 7f, C_BLACK)
        },
        Sym("g_sur", 7, Fam.NOIR, "Sur (colonne G)", "Le point est au-dessus : la balise est sur l'élément.") {
            stroke(path(22f to 80f, 22f to 52f, 78f to 52f, 78f to 80f), C_BLACK, 5f); dot(50f, 32f, 8f, C_BLACK)
        },
        Sym("g_sous", 7, Fam.NOIR, "Sous (colonne G)", "Le point est en dessous : la balise est sous l'élément.") {
            stroke(path(22f to 80f, 22f to 30f, 78f to 30f, 78f to 80f), C_BLACK, 5f); dot(50f, 60f, 8f, C_BLACK)
        },
        Sym("g_entre", 7, Fam.NOIR, "Entre (colonne G)", "Un point entre deux traits : la balise est entre deux éléments.") {
            stroke(path(18f to 28f, 82f to 28f), C_BLACK, 5f); stroke(path(18f to 72f, 82f to 72f), C_BLACK, 5f); dot(50f, 50f, 8f, C_BLACK)
        }
    )

    fun level(n: Int): List<Sym> = ALL.filter { it.level == n }
    fun byId(id: String): Sym = ALL.first { it.id == id }
}

/* =====================================================================
 *  Jeux
 * ===================================================================== */

enum class OriScreen { MENU, LEARN, PAIRS, MEMORY, QUIZ, RESULT, TERRAIN, DETECTIVE, BANK, INTRUS, DEFS }
enum class QType { SYM_TO_NAME, NAME_TO_SYM, SYM_TO_FAM }

class OriQ(val sym: Sym, val type: QType, val choices: List<Sym>, val fams: List<Fam>)
class MemCard(val sym: Sym, val isName: Boolean)

/** Une question « photo ↔ symbole ». */
class TerrainQ(val photo: PhotoItem, val sym: Sym, val choices: List<Sym>)

/** L'intrus : 4 symboles, dont un qui n'a pas la propriété des 3 autres. */
class IntrusQ(val prompt: String, val syms: List<Sym>, val odd: Sym, val why: String)

/** Une ligne de la feuille de définitions : élément (D) et position de la balise (G, null = au centre). */
class DefQ(val num: Int, val code: Int, val d: Sym, val g: Sym?, val choices: List<String>, val answer: String)

/** Un petit extrait de carte avec un poste (coordonnées de 0 à 100). */
class Scene(
    val target: Sym,
    val items: List<Triple<Sym, Float, Float>>,
    val start: Pair<Float, Float>,
    val yellow: Pair<Float, Float>,
    val green: Pair<Float, Float>,
    val pathY: Float,
    val choices: List<Sym>
)

class OriGame(private val progress: Progress, private val speaker: Speaker, val photos: PhotoBank) {
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

    // Photo ↔ symbole
    var terrain by mutableStateOf<List<TerrainQ>>(emptyList())
    var tIdx by mutableIntStateOf(0)
    var tPicked by mutableStateOf<Int?>(null)

    // Détective du poste
    var scenes by mutableStateOf<List<Scene>>(emptyList())
    var dIdx by mutableIntStateOf(0)
    var dPicked by mutableStateOf<Int?>(null)

    // L'intrus
    var intrus by mutableStateOf<List<IntrusQ>>(emptyList())
    var iIdx by mutableIntStateOf(0)
    var iPicked by mutableStateOf<Int?>(null)

    // Lis la définition
    var defs by mutableStateOf<List<DefQ>>(emptyList())
    var fIdx by mutableIntStateOf(0)
    var fPicked by mutableStateOf<Int?>(null)

    // Résultat
    var errors by mutableIntStateOf(0)
    var good by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)

    val atMenu: Boolean get() = screen == OriScreen.MENU

    fun back() { h.removeCallbacksAndMessages(null); speaker.stop(); screen = OriScreen.MENU }

    fun known(n: Int): Int = Ori.level(n).count { progress.oriBox(it.id) >= 3 }
    fun dueList(): List<Sym> = Ori.ALL.filter { progress.oriDue(it.id) }

    /** Le premier niveau pas encore bien su : c'est le niveau conseillé. */
    fun nextLevel(): Int = (1..7).firstOrNull { known(it) < Ori.level(it).size } ?: 7

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

    /** Groupes de symboles qui se mélangent dans les questions : couleurs / symboles de carte / définitions IOF. */
    private fun grp(l: Int): Int = if (l == 1) 0 else if (l == 7) 2 else 1

    fun startQuiz(lv: Int) {
        level = lv; title = titleFor(lv); lastGame = OriScreen.QUIZ
        val set = setFor(lv).shuffled().take(8)
        quiz = set.mapIndexed { k, s ->
            val type = when {
                s.level == 1 || s.level == 7 -> if (k % 2 == 0) QType.NAME_TO_SYM else QType.SYM_TO_NAME
                k % 4 == 3 -> QType.SYM_TO_FAM
                k % 2 == 0 -> QType.NAME_TO_SYM
                else -> QType.SYM_TO_NAME
            }
            // Distracteurs de la même couleur d'abord : on apprend à distinguer ce qui se ressemble.
            val same = Ori.ALL.filter { it.id != s.id && it.fam == s.fam && grp(it.level) == grp(s.level) }.shuffled()
            val other = Ori.ALL.filter { it.id != s.id && it !in same && grp(it.level) == grp(s.level) }.shuffled()
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

    /* ----- Photo ↔ symbole : relier le terrain à la carte ----- */

    fun startTerrain() {
        title = "Photo ↔ symbole"; lastGame = OriScreen.TERRAIN
        val items = photos.all().filter { p -> Ori.ALL.any { it.id == p.symbol } }.shuffled()
        // Une question par symbole au plus, 8 questions
        val picked = items.distinctBy { it.symbol }.take(8)
        terrain = picked.map { p ->
            val s = Ori.byId(p.symbol)
            val pool = Ori.ALL.filter { it.level in 2..6 && it.id != s.id }
            val near = pool.filter { it.level == s.level || it.fam == s.fam }.shuffled()
            val choices = (listOf(s) + (near + pool.shuffled()).distinct().take(3)).shuffled()
            TerrainQ(p, s, choices)
        }
        tIdx = 0; tPicked = null; errors = 0; good = 0; total = terrain.size
        screen = OriScreen.TERRAIN
    }

    fun answerT(k: Int) {
        if (tPicked != null) return
        tPicked = k
        val q = terrain[tIdx]
        val ok = q.choices[k].id == q.sym.id
        if (ok) good++ else errors++
        progress.recordOri(q.sym.id, ok)
        if (ok) h.postDelayed({ nextT() }, 1600)
    }

    fun nextT() {
        h.removeCallbacksAndMessages(null)
        if (tIdx + 1 < terrain.size) { tIdx++; tPicked = null } else finish()
    }

    /* ----- Détective du poste : que trouve-t-on au centre du cercle ? ----- */

    private val pointIds = listOf(
        "butte", "depression", "trou", "bloc", "gros_bloc", "groupe_blocs", "trou_rocheux",
        "trou_eau", "puits", "gros_arbre", "buisson", "batiment"
    )

    /** Symboles de même forme mais de couleur différente : point, V, carré… */
    private fun shape(id: String): String = when (id) {
        "butte", "bloc", "gros_bloc", "buisson" -> "point"
        "trou", "trou_rocheux", "trou_eau" -> "v"
        "puits", "batiment" -> "carre"
        else -> id
    }

    fun startDetective() {
        title = "Détective du poste"; lastGame = OriScreen.DETECTIVE
        val points = pointIds.map { Ori.byId(it) }
        val cells = listOf(
            22f to 22f, 50f to 20f, 78f to 24f, 20f to 50f, 50f to 52f, 80f to 50f, 24f to 80f, 52f to 80f, 80f to 78f
        )
        scenes = points.shuffled().take(8).map { target ->
            val spots = cells.shuffled()
            val decoys = points.filter { it.id != target.id }.shuffled().take(4)
            val items = listOf(Triple(target, spots[0].first, spots[0].second)) +
                decoys.mapIndexed { i, d -> Triple(d, spots[i + 1].first, spots[i + 1].second) }
            // Les mauvaises réponses : des éléments présents sur la carte, et des formes qui se ressemblent.
            val alike = points.filter { it.id != target.id && (it.fam == target.fam || shape(it.id) == shape(target.id)) }
            val choices = (listOf(target) + (decoys.take(2) + alike.shuffled() + decoys).distinct().take(3)).shuffled()
            Scene(
                target, items, spots[5], spots[6], spots[7], (30..70).random().toFloat(), choices
            )
        }
        dIdx = 0; dPicked = null; errors = 0; good = 0; total = scenes.size
        screen = OriScreen.DETECTIVE
    }

    fun answerD(k: Int) {
        if (dPicked != null) return
        dPicked = k
        val sc = scenes[dIdx]
        val ok = sc.choices[k].id == sc.target.id
        if (ok) good++ else errors++
        progress.recordOri(sc.target.id, ok)
        if (ok) h.postDelayed({ nextD() }, 1600)
    }

    fun nextD() {
        h.removeCallbacksAndMessages(null)
        if (dIdx + 1 < scenes.size) { dIdx++; dPicked = null } else finish()
    }

    fun openBank() { title = "Banque de photos"; screen = OriScreen.BANK }

    /* ----- L'intrus : lequel n'est pas de la même famille, ou ne se traverse pas ? ----- */

    private val impassable = listOf("lac", "falaise_inf", "interdit", "batiment")
    private val passable = listOf(
        "route", "carrossable", "chemin", "sentier", "foret", "lente", "decouvert",
        "petit_ruisseau", "ruisseau", "butte", "depression", "bloc", "courbe"
    )

    fun startIntrus() {
        title = "L'intrus"; lastGame = OriScreen.INTRUS
        val map = Ori.ALL.filter { it.level in 2..6 }
        intrus = (0 until 8).map { k ->
            if (k % 3 == 2) {
                val odd = Ori.byId(impassable.random())
                val three = passable.shuffled().take(3).map { Ori.byId(it) }
                IntrusQ(
                    "Lequel ne peut-on PAS traverser ?", (three + odd).shuffled(), odd,
                    "${odd.name} : on ne passe pas. Les trois autres se traversent."
                )
            } else {
                val fam = listOf(Fam.NOIR, Fam.BLEU, Fam.MARRON, Fam.VERT, Fam.JAUNE).random()
                val three = map.filter { it.fam == fam }.shuffled().take(3)
                val odd = map.filter { it.fam != fam && it.fam != Fam.VIOLET }.random()
                IntrusQ(
                    "Lequel n'est pas de la même famille de couleur ?", (three + odd).shuffled(), odd,
                    "${odd.name} est ${odd.fam.label.lowercase()} (${odd.fam.meaning}). Les autres sont ${fam.label.lowercase()} (${fam.meaning})."
                )
            }
        }
        iIdx = 0; iPicked = null; errors = 0; good = 0; total = intrus.size
        screen = OriScreen.INTRUS
    }

    fun answerI(k: Int) {
        if (iPicked != null) return
        iPicked = k
        val q = intrus[iIdx]
        val ok = q.syms[k].id == q.odd.id
        if (ok) good++ else errors++
        progress.recordOri(q.odd.id, ok)
    }

    fun nextI() { if (iIdx + 1 < intrus.size) { iIdx++; iPicked = null } else finish() }

    /* ----- Lis la définition : où est la balise ? ----- */

    /** Élément de la colonne D : (« la colline », « de la colline »). */
    private val dWords = mapOf(
        "d_colline" to ("la colline" to "de la colline"), "d_butte" to ("la butte" to "de la butte"),
        "d_depression" to ("la dépression" to "de la dépression"), "d_trou" to ("le trou" to "du trou"),
        "d_rentrant" to ("le rentrant" to "du rentrant"), "d_falaise" to ("la falaise" to "de la falaise"),
        "d_rocher" to ("le rocher" to "du rocher"), "d_clairiere" to ("la clairière" to "de la clairière"),
        "d_souche" to ("la souche" to "de la souche"), "d_ruine" to ("la ruine" to "de la ruine"),
        "d_batiment" to ("le bâtiment" to "du bâtiment"), "d_borne" to ("la borne" to "de la borne"),
        "d_tour" to ("la tour" to "de la tour"), "d_arbre" to ("l'arbre" to "de l'arbre")
    )
    private val gOk = mapOf(
        "g_pied" to setOf("d_falaise", "d_rocher", "d_tour", "d_batiment", "d_arbre", "d_borne"),
        "g_sur" to setOf("d_colline", "d_butte", "d_rocher", "d_souche"),
        "g_sous" to setOf("d_arbre")
    )

    private fun sentence(d: String, g: String?): String {
        val (le, de) = dWords.getValue(d)
        return "La balise est " + when (g) {
            null -> "au centre $de"
            "g_nord" -> "côté nord $de"
            "g_est" -> "côté est $de"
            "g_sud" -> "côté sud $de"
            "g_ouest" -> "côté ouest $de"
            "g_pied" -> "au pied $de"
            "g_sur" -> "sur $le"
            "g_sous" -> "sous $le"
            else -> "près $de"
        } + "."
    }

    private fun validG(d: String): List<String?> =
        listOf(null, "g_nord", "g_est", "g_sud", "g_ouest") + gOk.filter { d in it.value }.keys

    fun startDefs() {
        title = "Lis la définition"; lastGame = OriScreen.DEFS; level = 7
        defs = (1..8).map { num ->
            val d = dWords.keys.random()
            val g = validG(d).random()
            val answer = sentence(d, g)
            val alts = mutableListOf<String>()
            validG(d).shuffled().forEach { g2 -> if (g2 != g) alts += sentence(d, g2) }
            dWords.keys.shuffled().forEach { d2 -> if (d2 != d && g in validG(d2)) alts += sentence(d2, g) }
            val choices = (listOf(answer) + alts.distinct().shuffled().take(3)).shuffled()
            DefQ(num, (31..99).random(), Ori.byId(d), g?.let { Ori.byId(it) }, choices, answer)
        }
        fIdx = 0; fPicked = null; errors = 0; good = 0; total = defs.size
        screen = OriScreen.DEFS
    }

    fun answerF(k: Int) {
        if (fPicked != null) return
        fPicked = k
        val q = defs[fIdx]
        val ok = q.choices[k] == q.answer
        if (ok) good++ else errors++
        progress.recordOri(q.d.id, ok); q.g?.let { progress.recordOri(it.id, ok) }
        if (ok) h.postDelayed({ nextF() }, 1600)
    }

    fun nextF() {
        h.removeCallbacksAndMessages(null)
        if (fIdx + 1 < defs.size) { fIdx++; fPicked = null } else finish()
    }

    private fun finish() { screen = OriScreen.RESULT }

    fun replay() {
        when (lastGame) {
            OriScreen.PAIRS -> startPairs(level)
            OriScreen.MEMORY -> startMemory(level)
            OriScreen.TERRAIN -> startTerrain()
            OriScreen.DETECTIVE -> startDetective()
            OriScreen.INTRUS -> startIntrus()
            OriScreen.DEFS -> startDefs()
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
            OriScreen.TERRAIN -> OriTerrain(o)
            OriScreen.DETECTIVE -> OriDetective(o)
            OriScreen.BANK -> OriBank(o)
            OriScreen.INTRUS -> OriIntrus(o)
            OriScreen.DEFS -> OriDefs(o)
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
        OriTop("Course d'orientation", "Les symboles des cartes", { g.goMenu() })
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
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF2E7D4F)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🌲  Sur le terrain", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Relier la carte et la réalité.", fontSize = 13.sp, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OButton("Photo ↔ symbole", Color.White, Color(0xFF2E7D4F), Modifier.weight(1f)) { o.startTerrain() }
                OButton("Détective du poste", Color.White, Color(0xFF2E7D4F), Modifier.weight(1f)) { o.startDetective() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OButton("🔎 L'intrus", Color.White, Color(0xFF2E7D4F), Modifier.weight(1f)) { o.startIntrus() }
                OButton("📷 Banque de photos", Color(0x33FFFFFF), Color.White, Modifier.weight(1f)) { o.openBank() }
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
                if (lv == 7) {
                    OButton("📋  Lis la définition : où est la balise ?", O_ACCENT, Color.White, Modifier.fillMaxWidth()) { o.startDefs() }
                }
            }
        }
        Text(
            "Niveau 7 : sans symbole dans la colonne G, la balise est au centre de l'élément (règle IOF).",
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


/* =====================================================================
 *  Sur le terrain : photo ↔ symbole, détective du poste, banque de photos
 * ===================================================================== */

@Composable
private fun PhotoView(bank: PhotoBank, item: PhotoItem, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, item.path) {
        value = withContext(Dispatchers.IO) { bank.load(item) }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(O_LINE), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("📷", fontSize = 28.sp)
    }
}

@Composable
private fun AnswerGrid(n: Int, picked: Int?, isRight: (Int) -> Boolean, onPick: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until n).chunked(2).forEach { row ->
            Row(Modifier.height(112.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val right = isRight(k)
                    val edge = when {
                        picked == null -> O_LINE
                        right -> O_GOOD
                        picked == k -> O_BAD
                        else -> O_LINE
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .border(if (picked != null && (right || picked == k)) 4.dp else 2.dp, edge, RoundedCornerShape(16.dp))
                            .clickable { onPick(k) }.padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) { content(k) }
                }
            }
        }
    }
}

@Composable
private fun Feedback(ok: Boolean, sym: Sym, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (ok) Color(0xFFE6F6EC) else Color(0xFFFBE7E9)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(if (ok) "Bravo !" else "C'était : ${sym.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = O_INK)
        Text("💡 ${sym.tip}", fontSize = 14.sp, lineHeight = 18.sp, color = O_INK)
        if (!ok) OButton("Compris ▶", O_INK, Color.White, Modifier.fillMaxWidth()) { onNext() }
    }
}

@Composable
private fun OriTerrain(o: OriGame) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (o.terrain.isEmpty()) {
            OriTop(o.title, "Pas encore de photos", { o.back() })
            Text(
                "Ajoute des photos dans la banque de photos pour jouer.",
                fontSize = 16.sp, color = O_SUB, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            OButton("📷  Banque de photos", O_INK, Color.White, Modifier.fillMaxWidth()) { o.openBank() }
            return@Column
        }
        val q = o.terrain[o.tIdx]
        val picked = o.tPicked
        OriTop(o.title, "Photo ${o.tIdx + 1} sur ${o.terrain.size} · ✓ ${o.good}", { o.back() })
        Text("Quel symbole de la carte montre cette photo ?", fontSize = 15.sp, color = O_SUB, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        PhotoView(o.photos, q.photo, Modifier.fillMaxWidth().height(230.dp))
        Text(
            q.photo.credit ?: "📷 Photo de la famille", fontSize = 10.sp, color = O_SUB, maxLines = 2,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        AnswerGrid(4, picked, { k -> q.choices[k].id == q.sym.id }, { o.answerT(it) }) { k ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SymbolTile(q.choices[k], 64.dp)
                if (picked != null) Text(q.choices[k].name, fontSize = 11.sp, lineHeight = 13.sp, color = O_INK, modifier = Modifier.weight(1f))
            }
        }
        if (picked != null) Feedback(q.choices[picked].id == q.sym.id, q.sym) { o.nextT() }
    }
}

/** Dessine un symbole ponctuel à la position (x, y) d'un extrait de carte. */
private fun DrawScope.placeSym(s: Sym, x: Float, y: Float, k: Float = 0.2f) {
    val w = size.width; val hh = size.height
    withTransform({
        translate(x * w / 100f - 0.5f * k * w, y * hh / 100f - 0.5f * k * hh)
        scale(k, k, pivot = Offset.Zero)
    }) { s.draw(this) }
}

@Composable
private fun SceneMap(sc: Scene, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, O_LINE, RoundedCornerShape(14.dp))) {
        val w = size.width; val hh = size.height
        fun at(x: Float, y: Float) = Offset(x * w / 100f, y * hh / 100f)
        // Fond : une clairière jaune, une zone verte, des courbes et un chemin
        drawOval(C_YEL, at(sc.yellow.first - 16f, sc.yellow.second - 11f), Size(32f * w / 100f, 22f * hh / 100f))
        drawOval(C_G2, at(sc.green.first - 15f, sc.green.second - 12f), Size(30f * w / 100f, 24f * hh / 100f))
        for (k in 0..1) {
            val y = 12f + k * 80f
            drawPath(Path().apply {
                val a = at(0f, y); moveTo(a.x, a.y)
                val c1 = at(35f, y - 8f); val c2 = at(65f, y + 8f); val b = at(100f, y)
                cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
            }, C_BROWN, style = Stroke(0.6f * w / 100f))
        }
        drawPath(Path().apply {
            val a = at(0f, sc.pathY); moveTo(a.x, a.y)
            val c1 = at(40f, sc.pathY - 14f); val c2 = at(60f, sc.pathY + 14f); val b = at(100f, sc.pathY + 4f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
        }, C_BLACK, style = Stroke(0.8f * w / 100f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f * w / 100f, 1.2f * w / 100f))))
        // Les éléments
        sc.items.forEach { (s, x, y) -> placeSym(s, x, y) }
        // Le départ et le poste
        val t = sc.items.first()
        val st = sc.start
        val r = 8f * w / 100f
        val tri = Path().apply {
            val p1 = at(st.first, st.second - 5f); val p2 = at(st.first + 5f, st.second + 4f); val p3 = at(st.first - 5f, st.second + 4f)
            moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
        }
        drawPath(tri, C_PURPLE, style = Stroke(0.9f * w / 100f))
        val a = at(st.first, st.second); val b = at(t.second, t.third)
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val s0 = Offset(a.x + dx / len * 6f * w / 100f, a.y + dy / len * 6f * w / 100f)
        val s1 = Offset(b.x - dx / len * r, b.y - dy / len * r)
        drawLine(C_PURPLE, s0, s1, strokeWidth = 0.7f * w / 100f)
        drawCircle(C_PURPLE, r, b, style = Stroke(0.9f * w / 100f))
    }
}

@Composable
private fun OriDetective(o: OriGame) {
    if (o.scenes.isEmpty()) { Text("…"); return }
    val sc = o.scenes[o.dIdx]
    val picked = o.dPicked
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OriTop(o.title, "Poste ${o.dIdx + 1} sur ${o.scenes.size} · ✓ ${o.good}", { o.back() })
        Text(
            "Qu'y a-t-il au centre du cercle violet ?", fontSize = 15.sp, color = O_SUB,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        SceneMap(sc, Modifier.fillMaxWidth().height(260.dp))
        AnswerGrid(4, picked, { k -> sc.choices[k].id == sc.target.id }, { o.answerD(it) }) { k ->
            Text(sc.choices[k].name, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = O_INK, textAlign = TextAlign.Center)
        }
        if (picked != null) Feedback(sc.choices[picked].id == sc.target.id, sc.target) { o.nextD() }
    }
}

@Composable
private fun OriBank(o: OriGame) {
    val bank = o.photos
    val ver = bank.version
    var target by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val t = target
        if (uri != null && t != null) msg = if (bank.add(uri, t)) "Photo ajoutée ✓" else "Impossible de lire cette photo."
    }
    val all = remember(ver) { bank.all() }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OriTop("Banque de photos", "${all.count { it.user }} photo(s) de la famille · ${all.count { !it.user }} photos libres", { o.back() })
        Text(
            "Touche « + Photo » pour ajouter une photo prise en forêt. Le ✕ retire une photo de la famille.",
            fontSize = 13.sp, color = O_SUB
        )
        if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = O_GOOD)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "credits_top") {
                Text(
                    "Les photos libres viennent de Wikimedia Commons (licences Creative Commons). Crédits en bas de la liste.",
                    fontSize = 11.sp, color = O_SUB
                )
            }
            items(Ori.ALL.filter { it.level in 2..6 }, key = { it.id }) { s ->
                val mine = all.filter { it.symbol == s.id }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SymbolTile(s, 40.dp)
                        Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = O_INK, modifier = Modifier.weight(1f))
                        OButton("+ Photo", O_INK, Color.White) {
                            target = s.id
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                    if (mine.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(mine, key = { it.path }) { p ->
                                Box {
                                    PhotoView(bank, p, Modifier.size(76.dp))
                                    if (p.user) {
                                        Box(
                                            Modifier.align(Alignment.TopEnd).size(24.dp).clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xCC000000)).clickable { bank.delete(p) },
                                            contentAlignment = Alignment.Center
                                        ) { Text("✕", fontSize = 13.sp, color = Color.White) }
                                    } else {
                                        Text(
                                            "libre", fontSize = 9.sp, color = Color.White,
                                            modifier = Modifier.align(Alignment.BottomStart).background(Color(0x99000000)).padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "credits") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Crédits des photos libres", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = O_INK)
                    all.filter { !it.user }.forEach { p ->
                        Text("• ${Ori.byId(p.symbol).name} — ${p.credit}\n  ${p.page ?: ""}", fontSize = 10.sp, lineHeight = 12.sp, color = O_SUB)
                    }
                }
            }
        }
    }
}


/* ---------- L'intrus ---------- */

@Composable
private fun OriIntrus(o: OriGame) {
    if (o.intrus.isEmpty()) { Text("…"); return }
    val q = o.intrus[o.iIdx]
    val picked = o.iPicked
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OriTop(o.title, "Question ${o.iIdx + 1} sur ${o.intrus.size} · ✓ ${o.good}", { o.back() })
        Text(q.prompt, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = O_INK, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 2).forEach { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in r..r + 1) {
                        val s = q.syms[k]
                        val right = s.id == q.odd.id
                        val edge = when { picked == null -> O_LINE; right -> O_GOOD; picked == k -> O_BAD; else -> O_LINE }
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White)
                                .border(if (picked != null && (right || picked == k)) 4.dp else 2.dp, edge, RoundedCornerShape(16.dp))
                                .clickable { o.answerI(k) }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                        ) {
                            SymbolTile(s, 96.dp)
                            if (picked != null) Text(s.name, fontSize = 12.sp, lineHeight = 14.sp, color = O_INK, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (picked != null) {
            val ok = q.syms[picked].id == q.odd.id
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (ok) Color(0xFFE6F6EC) else Color(0xFFFBE7E9)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (ok) "Bravo, tu as trouvé l'intrus !" else "L'intrus était : ${q.odd.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = O_INK)
                Text("💡 ${q.why}", fontSize = 14.sp, lineHeight = 18.sp, color = O_INK)
                OButton("Suivant ▶", O_INK, Color.White, Modifier.fillMaxWidth()) { o.nextI() }
            }
        }
    }
}

/* ---------- Lis la définition ---------- */

@Composable
private fun DefCell(w: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(Modifier.width(w).height(42.dp).border(1.dp, C_BLACK), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun OriDefs(o: OriGame) {
    if (o.defs.isEmpty()) { Text("…"); return }
    val q = o.defs[o.fIdx]
    val picked = o.fPicked
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OriTop(o.title, "Poste ${o.fIdx + 1} sur ${o.defs.size} · ✓ ${o.good}", { o.back() })
        Text("Lis la ligne de la feuille de définitions. Où est la balise ?", fontSize = 15.sp, color = O_SUB, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        // La ligne telle qu'elle est imprimée : colonnes A à H
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                "ABCDEFGH".forEach { c ->
                    Box(Modifier.width(40.dp).height(20.dp), contentAlignment = Alignment.Center) {
                        Text("$c", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (c == 'D' || c == 'G') O_ACCENT else O_SUB)
                    }
                }
            }
            Row {
                DefCell(40.dp) { Text("${q.num}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C_BLACK) }
                DefCell(40.dp) { Text("${q.code}", fontSize = 14.sp, color = C_BLACK) }
                DefCell(40.dp) {}
                DefCell(40.dp) { Canvas(Modifier.size(36.dp)) { q.d.draw(this) } }
                DefCell(40.dp) {}
                DefCell(40.dp) {}
                DefCell(40.dp) { q.g?.let { g -> Canvas(Modifier.size(36.dp)) { g.draw(this) } } }
                DefCell(40.dp) {}
            }
            Text("A : numéro · B : code de la balise · D : élément · G : position de la balise", fontSize = 10.sp, color = O_SUB, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            q.choices.forEachIndexed { k, c ->
                val right = c == q.answer
                val edge = when { picked == null -> O_LINE; right -> O_GOOD; picked == k -> O_BAD; else -> O_LINE }
                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White)
                        .border(if (picked != null && (right || picked == k)) 4.dp else 2.dp, edge, RoundedCornerShape(14.dp))
                        .clickable { o.answerF(k) }.padding(10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(c, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = O_INK, textAlign = TextAlign.Center) }
            }
        }
        if (picked != null && q.choices[picked] != q.answer) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFBE7E9)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(q.answer, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = O_INK)
                Text(
                    "💡 Colonne D : ${q.d.name.substringBefore(" (")}. " +
                        (q.g?.let { "Colonne G : ${it.name.substringBefore(" (")}." } ?: "Colonne G vide : la balise est au centre."),
                    fontSize = 14.sp, lineHeight = 18.sp, color = O_INK
                )
                OButton("Compris ▶", O_INK, Color.White, Modifier.fillMaxWidth()) { o.nextF() }
            }
        }
    }
}
