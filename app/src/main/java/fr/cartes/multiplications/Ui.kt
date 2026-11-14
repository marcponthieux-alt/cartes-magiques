package fr.cartes.multiplications

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.max

private val Fond = Color(0xFFF4F1EA)
private val Ink = Color(0xFF23264A)
private val Sub = Color(0xFF5A5E7A)
private val Head = Color(0xFF282828)
private val Edge = Color(0xFFCFC8B8)
private val Line = Color(0xFFE2DCCD)
private val Grey = Color(0xFFDAD6CC)
private val GreyInk = Color(0xFF9A958A)
private val Faint = Color(0xFF6E6858)
private val Missed = Color(0xFFECE8DF)
private val Sun = Color(0xFFFFD60A)

@Composable
fun App(g: Game) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Fond) {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (g.screen) {
                    Screen.HOME -> Home(g)
                    Screen.MEMO -> Memo(g)
                    Screen.GAME -> GameScreen(g)
                    Screen.END -> EndScreen(g)
                    Screen.REVISION -> RevisionScreen(g)
                }
                // Encouragements
                AnimatedVisibility(
                    visible = g.bannerOn && g.screen == Screen.GAME,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp)
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(Sun)
                            .border(2.dp, Ink, RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(g.bannerText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
                    }
                }
                Celebration(g)
                if (g.gamePaused && g.screen == Screen.GAME) PauseOverlay(g)
            }
            BackHandler(enabled = g.screen != Screen.HOME) { g.goHome() }
        }
    }
}

/* ---------- Éléments communs ---------- */

private fun Modifier.whiteEdge(v: Int, r: Dp, w: Dp = 1.dp): Modifier =
    if (Data.isWhite(v)) this.border(w, Edge, RoundedCornerShape(r)) else this

private fun Modifier.dashedOver(color: Color, r: Dp = 12.dp, w: Dp = 2.dp): Modifier =
    this.drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            style = Stroke(width = w.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            cornerRadius = CornerRadius(r.toPx())
        )
    }

@Composable
private fun HeadCell(t: String, modifier: Modifier, size: TextUnit = 14.sp, r: Dp = 5.dp) {
    Box(modifier.clip(RoundedCornerShape(r)).background(Head), contentAlignment = Alignment.Center) {
        Text(t, color = Color.White, fontSize = size, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    pad: PaddingValues = PaddingValues(14.dp),
    gap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(pad),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}

@Composable
private fun TopBar(title: String, sub: String, onBack: () -> Unit, score: Int?) {
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour à la table", tint = Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
            Text(sub, fontSize = 12.sp, color = Sub, maxLines = 1)
        }
        if (score != null) {
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("⭐ $score/25", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
        }
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(shape)
            .background(if (filled) Ink else Color.White)
            .border(2.dp, Ink, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else Ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmallButton(
    text: String, bg: Color, edge: Color, fg: Color, modifier: Modifier,
    enabled: Boolean = true, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 44.dp).alpha(if (enabled) 1f else 0.4f).clip(shape).background(bg)
            .border(2.dp, edge, shape).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg) }
}

/**
 * Les deux faces d'une carte. rot < 90° : face résultat ; sinon face multiplications,
 * avec la ligne de l'emplacement de la carte (ligne gr × colonne gc) en gras et plus grande.
 */
@Composable
private fun CardFaces(v: Int, gr: Int, gc: Int, rot: Float, fg: Color, icon: String?) {
    if (rot <= 90f) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Text(icon, fontSize = 14.sp)
            Text("$v", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    } else {
        Column(
            Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) Text(icon, fontSize = 12.sp)
            Data.prods(v).forEach { (a, b) ->
                val geo = a == gr && b == gc
                Text(
                    "$a×$b",
                    fontSize = if (geo) 15.sp else 11.sp,
                    lineHeight = if (geo) 17.sp else 13.sp,
                    fontWeight = if (geo) FontWeight.Bold else FontWeight.Medium,
                    color = if (geo) fg else fg.copy(alpha = 0.85f),
                    maxLines = 1, softWrap = false
                )
            }
        }
    }
}

/* ---------- Écran 1 : accueil ---------- */

@Composable
private fun Home(g: Game) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Les cartes magiques", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Touche la table pour choisir tes 25 cartes", fontSize = 15.sp, color = Sub)
        }
        BigTable(g)
        Panel {
            Text("Mon niveau", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Mode.entries.forEach { m ->
                    Chip(m.label, m.sub, g.mode == m, Modifier.weight(1f)) { g.mode = m }
                }
            }
            if (g.mode != Mode.REV) {
                Text("Je commence par", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dir.entries.forEach { d ->
                        Chip(d.label, d.sub, g.dir == d, Modifier.weight(1f)) { g.dir = d }
                    }
                }
            } else {
                Text("Révision : pas de voix ni de micro. Touche une carte pour la retourner.", fontSize = 13.sp, color = Sub)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("42 autocollants à gagner", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Sub)
            Text(Data.ICONS.take(7).joinToString(""), fontSize = 20.sp)
        }
        val status = when {
            g.ear.failed -> "Micro indisponible : un adulte validera les réponses."
            !g.ear.micAllowed -> "Autorise le micro pour répondre à voix haute."
            !g.ear.ready -> "Préparation du micro…"
            else -> "Micro prêt 🎤  Tu peux aussi dire « pause » ou « je passe »."
        }
        Text(status, fontSize = 12.sp, color = Sub, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        if (g.speakerMissingVoice) {
            Text(
                "Voix française absente : installe-la dans les réglages Android (Synthèse vocale).",
                fontSize = 12.sp, color = Color(0xFF8E1F2B), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Chip(label: String, sub: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.heightIn(min = 58.dp).clip(shape)
            .background(if (on) Ink else Fond)
            .border(2.dp, if (on) Ink else Line, shape)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val c = if (on) Color.White else Ink
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c, textAlign = TextAlign.Center, maxLines = 1)
        Text(sub, fontSize = 11.sp, lineHeight = 13.sp, color = c, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BigTable(g: Game) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0..10) {
            if (r == 6) Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0..10) {
                    if (c == 6) Spacer(Modifier.width(2.dp))
                    val mod = if (c == 0) Modifier.width(32.dp).height(30.dp) else Modifier.weight(1f).height(30.dp)
                    if (r == 0 || c == 0) {
                        HeadCell(if (r == 0 && c == 0) "×" else "${max(r, c)}", mod)
                    } else {
                        val v = r * c
                        val q = (if (r >= 6) 2 else 0) + (if (c >= 6) 1 else 0)
                        Box(
                            mod.clip(RoundedCornerShape(5.dp)).background(Data.bg(v)).whiteEdge(v, 5.dp)
                                .clickable { g.openQuad(q) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$v", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Data.fg(v))
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Écran 2 : mémorisation ---------- */

@Composable
private fun Memo(g: Game) {
    val rows = Data.rows(g.quad)
    val cols = Data.cols(g.quad)
    val hintA by animateFloatAsState(if (g.hint) 1f else 0f, tween(500), label = "hint")
    Column(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { g.flip() }) }
            .pointerInput(Unit) {
                var d = 0f
                detectDragGestures(onDragStart = { d = 0f }) { change, amount ->
                    change.consume()
                    d += abs(amount.x) + abs(amount.y)
                    if (d > 900f) { d = -1e9f; g.flip() }
                }
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopBar("Carré ${g.quad + 1} – regarde bien !", Data.range(g.quad), { g.goHome() }, null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HeadCell("×", Modifier.width(44.dp).fillMaxHeight(), 20.sp, 10.dp)
                cols.forEach { HeadCell("$it", Modifier.weight(1f).fillMaxHeight(), 20.sp, 10.dp) }
            }
            rows.forEach { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HeadCell("$r", Modifier.width(44.dp).fillMaxHeight(), 20.sp, 10.dp)
                    cols.forEach { c ->
                        val v = r * c
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                .background(Data.bg(v)).whiteEdge(v, 12.dp, 1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$v", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Data.fg(v))
                                Text(Data.icon(v), fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().alpha(hintA).clip(RoundedCornerShape(22.dp)).background(Ink)
                .clickable(enabled = g.hint) { g.flip() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("👆", fontSize = 30.sp)
            Column {
                Text("Quand tu es prêt, retourne les cartes !", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Touche deux fois l'écran ou frotte-le", fontSize = 13.sp, color = Color(0xFFD9DBEA))
            }
        }
    }
}

/* ---------- Révision (et révision du super bonus) ---------- */

@Composable
private fun RevisionScreen(g: Game) {
    val rows = Data.rows(g.quad)
    val cols = Data.cols(g.quad)
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TopBar(
            if (g.revForBonus) "Super bonus · je révise" else "Révision · carré ${g.quad + 1}",
            Data.range(g.quad), { g.goHome() }, null
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.height(36.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HeadCell("×", Modifier.width(36.dp).fillMaxHeight(), 17.sp, 9.dp)
                cols.forEach { HeadCell("$it", Modifier.weight(1f).fillMaxHeight(), 17.sp, 9.dp) }
            }
            rows.forEachIndexed { ri, r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HeadCell("$r", Modifier.width(36.dp).fillMaxHeight(), 17.sp, 9.dp)
                    for (ci in 0..4) RevCard(g, ri * 5 + ci, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        Text(
            if (g.revForBonus) "Regarde bien les cartes à revoir. Touche une carte pour la retourner."
            else "Touche une carte pour la retourner.",
            fontSize = 14.sp, color = Sub, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        BigButton(
            if (g.revAllMult) "🔄 Voir tous les résultats" else "🔄 Voir toutes les multiplications",
            !g.revForBonus
        ) { g.revToggleAll() }
        if (g.revForBonus) BigButton("⭐ Je suis prêt : partie bonus !", true) { g.startBonus() }
    }
}

@Composable
private fun RevCard(g: Game, i: Int, modifier: Modifier) {
    val v = Data.value(g.quad, i)
    val (gr, gc) = Data.geo(g.quad, i)
    val only = g.revOnly
    if (only != null && i !in only) {
        Box(modifier.dashedOver(Edge))
        return
    }
    val colored = g.revSel == i
    val rot by animateFloatAsState(if (g.revShowsMult(i)) 180f else 0f, tween(500), label = "rev")
    val bg by animateColorAsState(if (colored) Data.bg(v) else Grey, label = "rbg")
    val fg by animateColorAsState(if (colored) Data.fg(v) else GreyInk, label = "rfg")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.zIndex(if (colored) 2f else 0f)
            .graphicsLayer { rotationY = rot; cameraDistance = 12f * density }
            .clip(shape).background(bg)
            .then(if (colored) Modifier.border(3.dp, Ink, shape) else Modifier)
            .clickable { g.revTap(i) },
        contentAlignment = Alignment.Center
    ) {
        CardFaces(v, gr, gc, rot, fg, null)
    }
}

/* ---------- Écrans 3 à 5 : la partie ---------- */

@Composable
private fun GameScreen(g: Game) {
    val active = g.sel != null && g.phase in setOf(Phase.ASK, Phase.LISTEN, Phase.RIGHT, Phase.WRONG, Phase.PASSED)
    Column(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TopBar(
            "Carré ${g.quad + 1} · ${g.mode.label}" + if (g.bonus) " · Bonus ⭐" else "",
            Data.range(g.quad), { g.goHome() }, g.score
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..4) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..4) CardView(g, r * 5 + c, active, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        Rewards(g)
        VoicePanel(g)
    }
}

@Composable
private fun CardView(g: Game, i: Int, active: Boolean, modifier: Modifier) {
    val v = Data.value(g.quad, i)
    val (gr, gc) = Data.geo(g.quad, i)
    val st = g.done[i]
    val ok = st == Res.OK
    val isSel = active && g.sel == i
    val reveal = g.phase == Phase.RIGHT || ((g.phase == Phase.WRONG || g.phase == Phase.PASSED) && g.revealed)
    val showAnswer = ok || (g.sel == i && reveal)
    val showBack = if (g.intro) false else if (g.dir == Dir.R2V) showAnswer else !showAnswer
    val hidden = g.mode == Mode.EXP && !isSel
    val greyed = active && !isSel

    val rot by animateFloatAsState(if (showBack) 180f else 0f, tween(600), label = "rot")
    val scale by animateFloatAsState(if (isSel) 1.08f else 1f, label = "scale")
    val bg by animateColorAsState(if (greyed) Grey else Data.bg(v), label = "bg")
    val fg by animateColorAsState(if (greyed) GreyInk else Data.fg(v), label = "fg")
    val shape = RoundedCornerShape(12.dp)

    when {
        hidden && ok -> {
            // Expert : une carte trouvée garde son autocollant, pour voir l'avancée.
            Box(
                modifier.clip(shape).background(bg).then(if (!greyed) Modifier.whiteEdge(v, 12.dp) else Modifier),
                contentAlignment = Alignment.Center
            ) { Text(Data.icon(v), fontSize = 26.sp) }
        }
        hidden -> Box(modifier.dashedOver(Edge))
        else -> {
            val missed = (st == Res.KO || st == Res.PASS) && !isSel
            val outer = if (missed) modifier.dashedOver(Faint) else modifier
            Box(
                outer.zIndex(if (isSel) 2f else 0f)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        rotationY = rot
                        cameraDistance = 12f * density
                    }
                    .clip(shape)
                    .background(bg)
                    .then(
                        if (isSel) Modifier.border(3.dp, Ink, shape)
                        else if (Data.isWhite(v) && !greyed) Modifier.border(1.5.dp, Edge, shape)
                        else Modifier
                    )
                    .clickable(enabled = g.mode == Mode.DEB && g.phase == Phase.PICK && st == null && i in g.targets) { g.pick(i) },
                contentAlignment = Alignment.Center
            ) {
                CardFaces(v, gr, gc, rot, fg, if (ok) Data.icon(v) else null)
            }
        }
    }
}

@Composable
private fun Rewards(g: Game) {
    val order = (0..24).sortedWith(compareBy({ Data.value(g.quad, it) }, { it }))
    Panel(pad = PaddingValues(horizontal = 12.dp, vertical = 10.dp), gap = 6.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mes bons points", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("${g.score} / 25", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        order.chunked(13).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                line.forEach { i -> Slot(g, i, Modifier.weight(1f).height(24.dp)) }
                repeat(13 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Slot(g: Game, i: Int, modifier: Modifier) {
    val v = Data.value(g.quad, i)
    val ok = g.done[i] == Res.OK
    val isNew = g.lastEarn == i && g.phase == Phase.RIGHT
    val sc by animateFloatAsState(if (isNew) 1.35f else 1f, label = "slot")
    Box(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(7.dp))
            .background(if (ok) Data.bg(v) else Fond)
            .then(if (ok) Modifier.whiteEdge(v, 7.dp) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (ok) Text(Data.icon(v), fontSize = 14.sp)
        else Text("$v", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Faint)
    }
}

@Composable
private fun VoicePanel(g: Game) {
    val i = g.sel
    val v = i?.let { Data.value(g.quad, it) }
    val q = if (i == null || v == null) "" else {
        if (g.dir == Dir.R2V) "$v = ?" else Data.geo(g.quad, i).let { "${it.first}×${it.second} = ?" }
    }
    val a = v?.let { x -> Data.prods(x).joinToString("\n") { "${it.first}×${it.second} = $x" } } ?: ""
    val (icon, title, text) = when (g.phase) {
        Phase.PICK -> Triple("👆", "À toi de choisir !", "Touche la carte à laquelle tu veux répondre.")
        Phase.IDLE -> Triple("👆", "Prêt ?", "Le jeu choisit une carte…")
        Phase.ASK -> Triple("🔊", "Écoute la question…", q)
        Phase.LISTEN -> Triple("🎤", "Je t'écoute !", q)
        Phase.RIGHT -> Triple("⭐", "Bravo ! +1 bon point", a)
        Phase.WRONG -> Triple("💡", "Regarde bien…", (g.said?.let { "Tu as dit : $it\n" } ?: "") + a)
        Phase.PASSED -> Triple("⏭", "Tu passes. La réponse :", a)
    }
    val circle = when (g.phase) {
        Phase.ASK -> Color(0xFFFFE9A8)
        Phase.LISTEN -> Ink
        Phase.RIGHT -> Color(0xFFE6F6EC)
        Phase.WRONG -> Color(0xFFFBE7E9)
        else -> Fond
    }
    val listening = g.phase == Phase.LISTEN
    val canPass = g.phase == Phase.ASK || g.phase == Phase.LISTEN
    val inf = rememberInfiniteTransition(label = "pulse")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p")

    Panel(Modifier.heightIn(min = 130.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(52.dp)
                    .drawBehind {
                        if (listening) drawCircle(Ink.copy(alpha = 0.18f), radius = size.minDimension / 2 + p * 10.dp.toPx())
                    }
                    .clip(CircleShape).background(circle),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = 24.sp) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (text.isNotEmpty()) Text(text, fontSize = 14.sp, lineHeight = 19.sp, color = Color(0xFF4A4E6A))
            }
        }
        if (listening) {
            if (g.ear.usable) {
                Text(
                    "Réécouter la question",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { g.repeatQuestion() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text("Micro indisponible : un adulte valide la réponse", fontSize = 11.sp, color = Sub)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Bonne réponse", Color(0xFFE6F6EC), Color(0xFF2E8B57), Color(0xFF1D5E3A), Modifier.weight(1f)) { g.manual(true) }
                    SmallButton("Mauvaise réponse", Color(0xFFFBE7E9), Color(0xFFB4323F), Color(0xFF8E1F2B), Modifier.weight(1f)) { g.manual(false) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("⏸  Pause", Fond, Line, Ink, Modifier.weight(1f)) { g.pauseGame() }
            SmallButton("⏭  Je passe", Fond, Line, Ink, Modifier.weight(1f), enabled = canPass) { g.pass() }
        }
    }
}

@Composable
private fun PauseOverlay(g: Game) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE623264A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("⏸", fontSize = 64.sp)
            Text("Pause", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Prends ton temps, la partie t'attend.", fontSize = 16.sp, color = Color(0xFFD9DBEA))
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Sun).clickable { g.resumeGame() }
                    .padding(horizontal = 36.dp, vertical = 16.dp)
            ) { Text("▶  Reprendre", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink) }
            Text(
                "Revenir à la table",
                fontSize = 15.sp, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { g.goHome() }.padding(8.dp)
            )
        }
    }
}

/** Animation pour chaque erreur corrigée pendant la partie bonus. */
@Composable
private fun Celebration(g: Game) {
    AnimatedVisibility(
        visible = g.celebrateOn,
        enter = scaleIn(initialScale = 0.2f) + fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        val inf = rememberInfiniteTransition(label = "cel")
        val s by inf.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "s")
        Box(Modifier.fillMaxSize().background(Color(0x5523264A)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.scale(s).clip(RoundedCornerShape(28.dp)).background(Color.White)
                    .border(4.dp, Sun, RoundedCornerShape(28.dp)).padding(horizontal = 30.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⭐ 🌟 ✨ 🌟 ⭐", fontSize = 26.sp)
                Text(g.celebrateIcon, fontSize = 84.sp)
                Text("Erreur corrigée !", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("+1 bon point récupéré", fontSize = 16.sp, color = Sub)
                Text("✨ 🎉 ✨", fontSize = 26.sp)
            }
        }
    }
}

/* ---------- Écran 6 : fin de partie ---------- */

@Composable
private fun EndScreen(g: Game) {
    val rows = Data.rows(g.quad)
    val cols = Data.cols(g.quad)
    val s = g.score
    val miss = g.missing.size
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Carré ${g.quad + 1} terminé · ${g.mode.label}", fontSize = 15.sp, color = Sub)
        Text(
            if (s == 25) "Parfait !" else if (s >= 18) "Bravo !" else "Bien joué !",
            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Ink
        )
        Text("⭐ $s bons points sur 25", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Panel(pad = PaddingValues(12.dp), gap = 6.dp) {
            Row(Modifier.height(32.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeadCell("×", Modifier.width(32.dp).fillMaxHeight(), 15.sp, 8.dp)
                cols.forEach { HeadCell("$it", Modifier.weight(1f).fillMaxHeight(), 15.sp, 8.dp) }
            }
            rows.forEachIndexed { ri, r ->
                Row(Modifier.height(76.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeadCell("$r", Modifier.width(32.dp).fillMaxHeight(), 15.sp, 8.dp)
                    cols.forEachIndexed { ci, c ->
                        val i = ri * 5 + ci
                        val v = r * c
                        val ok = g.done[i] == Res.OK
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                                .background(if (ok) Data.bg(v) else Missed)
                                .then(if (ok) Modifier.whiteEdge(v, 14.dp) else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(if (ok) Data.icon(v) else "?", fontSize = 28.sp, color = Faint)
                            Text("$v", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (ok) Data.fg(v) else Faint)
                        }
                    }
                }
            }
        }
        if (miss > 0) {
            Text(
                "$miss carte${if (miss > 1) "s" else ""} à revoir pour gagner tous les bons points",
                fontSize = 14.sp, color = Sub, textAlign = TextAlign.Center
            )
            BigButton("⭐ Super bonus : je progresse un peu plus", true) { g.startBonusRevision() }
        }
        BigButton("Rejouer ce carré", miss == 0) { g.replay() }
        BigButton("Choisir un autre carré", false) { g.goHome() }
    }
}
