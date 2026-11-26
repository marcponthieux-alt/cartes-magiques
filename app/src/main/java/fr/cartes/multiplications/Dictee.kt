package fr.cartes.multiplications

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* =====================================================================
 *  Orthographe : la dictée de mots
 * ===================================================================== */

private val D_INK = Color(0xFF23264A)
private val D_SUB = Color(0xFF5A5E7A)
private val D_LINE = Color(0xFFE2DCCD)
private val D_BG = Color(0xFFF3EFFA)
private val D_ACC = Color(0xFF7B4FC9)
private val D_GOOD = Color(0xFF2E8B57)
private val D_BAD = Color(0xFFC0392B)
private val D_GREY = Color(0xFFC9C6D3)

class Word(val w: String, val sentence: String)
class Week(val id: Long, val label: String, val words: List<Word>)

/** Les mots de chaque semaine, saisis par le parent. Gardés dans l'application (fichier dictee.json). */
class WordStore(ctx: Context) {
    private val file = File(ctx.applicationContext.filesDir, "dictee.json")
    var weeks by mutableStateOf(load())
        private set

    private fun load(): List<Week> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ws = o.getJSONArray("words")
            Week(o.getLong("id"), o.getString("label"),
                (0 until ws.length()).map { k -> val x = ws.getJSONObject(k); Word(x.getString("w"), x.optString("s")) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save() {
        val arr = JSONArray()
        weeks.forEach { wk ->
            val ws = JSONArray()
            wk.words.forEach { ws.put(JSONObject().put("w", it.w).put("s", it.sentence)) }
            arr.put(JSONObject().put("id", wk.id).put("label", wk.label).put("words", ws))
        }
        file.writeText(arr.toString())
    }

    /** Un mot par ligne ; une phrase d'exemple facultative après « ; » (utile pour les homophones). */
    fun parse(text: String): List<Word> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split(";", limit = 2)
        Word(parts[0].trim().lowercase(Locale.FRENCH).replace(Regex("\\s+"), " "), parts.getOrNull(1)?.trim() ?: "")
    }.filter { it.w.isNotEmpty() }.distinctBy { it.w }

    fun addWeek(label: String, text: String): Int {
        val ws = parse(text)
        if (ws.isEmpty()) return 0
        val lab = label.ifBlank { "Semaine du " + SimpleDateFormat("d MMMM", Locale.FRENCH).format(Date()) }
        weeks = listOf(Week(System.currentTimeMillis(), lab, ws)) + weeks
        save()
        return ws.size
    }

    fun deleteWeek(id: Long) { weeks = weeks.filter { it.id != id }; save() }

    /** Modifier une liste existante (protégé par mot de passe dans l'écran). */
    fun updateWeek(id: Long, label: String, text: String): Int {
        val ws = parse(text)
        if (ws.isEmpty()) { deleteWeek(id); return 0 }
        weeks = weeks.map { if (it.id == id) Week(id, label.ifBlank { it.label }, ws) else it }
        save()
        return ws.size
    }

    /** Texte d'une liste, tel qu'il se saisit : un mot par ligne, phrase après « ; ». */
    fun asText(wk: Week): String = wk.words.joinToString("\n") { if (it.sentence.isBlank()) it.w else "${it.w} ; ${it.sentence}" }

    fun removeWord(weekId: Long, w: String) {
        weeks = weeks.map { if (it.id == weekId) Week(it.id, it.label, it.words.filter { x -> x.w != w }) else it }
            .filter { it.words.isNotEmpty() }
        save()
    }
}

/** Comparaison lettre à lettre (alignement de Levenshtein) entre ce que l'enfant a tapé et le mot. */
object Align {
    class Result(val typedOk: List<Boolean>, val targetKnown: List<Boolean>)

    fun run(typed: String, target: String): Result {
        val n = typed.length; val m = target.length
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val c = if (typed[i - 1] == target[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
        }
        val tOk = MutableList(n) { false }; val known = MutableList(m) { false }
        var i = n; var j = m
        while (i > 0 && j > 0) {
            val c = if (typed[i - 1] == target[j - 1]) 0 else 1
            when {
                d[i][j] == d[i - 1][j - 1] + c -> { if (c == 0) { tOk[i - 1] = true; known[j - 1] = true }; i--; j-- }
                d[i][j] == d[i - 1][j] + 1 -> i--
                else -> j--
            }
        }
        return Result(tOk, known)
    }
}

enum class DScreen { HOME, ADD, EDIT, PLAY, RESULT }
enum class DPhase { TYPE1, SHOW1, TYPE2, OK, FAIL }

class DicteeGame(private val progress: Progress, private val speaker: Speaker, val store: WordStore) {
    private val h = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(DScreen.HOME)
    var list by mutableStateOf<List<Word>>(emptyList())
    var idx by mutableIntStateOf(0)
    var phase by mutableStateOf(DPhase.TYPE1)
    var typed by mutableStateOf("")
    var first by mutableStateOf("")
    var align by mutableStateOf<Align.Result?>(null)
    var firstOk by mutableIntStateOf(0)
    var secondOk by mutableIntStateOf(0)
    val missed = mutableListOf<String>()
    private var newIds: Set<String> = emptySet()

    val atHome: Boolean get() = screen == DScreen.HOME
    val current: Word? get() = list.getOrNull(idx)
    fun isNew(w: Word): Boolean = w.w in newIds

    fun back() { h.removeCallbacksAndMessages(null); speaker.stop(); screen = DScreen.HOME }
    fun openAdd() { screen = DScreen.ADD }
    var editing by mutableStateOf<Week?>(null)
    fun openEdit(wk: Week) { editing = wk; screen = DScreen.EDIT }

    /**
     * Dictée du jour (environ 12 mots) :
     * 1. les mots de la semaine en cours pas encore sus (priorité) ;
     * 2. les mots des semaines précédentes dont la date de révision est arrivée (boîtes de Leitner),
     *    les plus fragiles d'abord ;
     * 3. nouveaux et anciens mots mélangés (2 nouveaux, 1 ancien…).
     */
    fun plan(): List<Word> {
        val weeks = store.weeks
        if (weeks.isEmpty()) return emptyList()
        val current = weeks.first().words.filter { progress.wordBox(it.w) < 3 }
        val older = weeks.drop(1).flatMap { it.words }.distinctBy { it.w }
            .filter { it.w !in current.map { c -> c.w } && progress.wordDue(it.w) }
            .sortedBy { progress.wordBox(it.w) }
        val a = current.shuffled().take(10).toMutableList()
        val b = older.take(12 - minOf(a.size, 8)).toMutableList()
        val out = mutableListOf<Word>()
        while (a.isNotEmpty() || b.isNotEmpty()) {
            repeat(2) { if (a.isNotEmpty()) out += a.removeAt(0) }
            if (b.isNotEmpty()) out += b.removeAt(0)
        }
        return out.ifEmpty { weeks.first().words.shuffled().take(10) }
    }

    fun dueCount(): Int = plan().size

    fun start(words: List<Word> = plan()) {
        if (words.isEmpty()) return
        newIds = store.weeks.firstOrNull()?.words?.map { it.w }?.toSet() ?: emptySet()
        list = words; idx = 0; firstOk = 0; secondOk = 0; missed.clear()
        screen = DScreen.PLAY
        startWord()
    }

    fun replayMissed() { val m = missed.toSet(); start(list.filter { it.w in m }) }

    private fun startWord() {
        typed = ""; first = ""; align = null; phase = DPhase.TYPE1
        h.postDelayed({ sayWord() }, 300)
    }

    /** Le mot, sa phrase d'exemple s'il y en a une, puis le mot à nouveau. */
    fun sayWord() {
        val w = current ?: return
        speaker.pitch = 1.0f
        speaker.speak(if (w.sentence.isNotBlank()) "${w.w}... ${w.sentence}... ${w.w}." else "${w.w}... ${w.w}.") {}
    }

    fun key(c: String) {
        if (phase != DPhase.TYPE1 && phase != DPhase.TYPE2) return
        if (typed.length < 30) typed += c
    }

    fun erase() { if (typed.isNotEmpty() && (phase == DPhase.TYPE1 || phase == DPhase.TYPE2)) typed = typed.dropLast(1) }

    fun validate() {
        val w = current ?: return
        typed = typed.trim()
        if (typed.isEmpty()) return
        val target = w.w
        when (phase) {
            DPhase.TYPE1 -> {
                if (typed == target) {
                    firstOk++; progress.recordWord(target, true, true); phase = DPhase.OK
                    speaker.speak("Bravo !") {}
                } else {
                    first = typed; align = Align.run(typed, target); phase = DPhase.SHOW1
                    speaker.speak("Presque. Essaie de corriger.") {}
                }
            }
            DPhase.TYPE2 -> {
                if (typed == target) {
                    secondOk++; progress.recordWord(target, true, false); phase = DPhase.OK
                    speaker.speak("Bravo, tu as corrigé !") {}
                } else {
                    missed += target; progress.recordWord(target, false, false); phase = DPhase.FAIL
                    speaker.speak("Pas tout à fait. Voici la bonne réponse.") {}
                }
            }
            else -> {}
        }
    }

    /** Les mauvaises lettres sont effacées : il reste le mot avec des trous, l'enfant réécrit tout le mot. */
    fun retry() { if (phase == DPhase.SHOW1) { typed = ""; phase = DPhase.TYPE2 } }

    fun next() {
        speaker.stop()
        if (idx + 1 < list.size) { idx++; startWord() } else screen = DScreen.RESULT
    }
}

/* ---------- Écrans ---------- */

@Composable
private fun DTop(title: String, sub: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = D_INK) }
        Text("✏️", fontSize = 24.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = D_INK, maxLines = 1)
            Text(sub, fontSize = 12.sp, color = D_SUB, maxLines = 1)
        }
    }
}

@Composable
private fun DBtn(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) bg else D_LINE)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = fg, textAlign = TextAlign.Center) }
}

@Composable
fun DicteeApp(g: Game) {
    val d = g.dictee
    Box(Modifier.fillMaxSize().background(D_BG)) {
        when (d.screen) {
            DScreen.HOME -> DHome(g)
            DScreen.ADD -> DAdd(d, null)
            DScreen.EDIT -> DAdd(d, d.editing)
            DScreen.PLAY -> DPlay(d)
            DScreen.RESULT -> DResult(d)
        }
    }
}

/** Demande le mot de passe parent (« papa ») avant une modification. */
@Composable
fun AskPassword(onDismiss: () -> Unit, onOk: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mot de passe parent") },
        text = {
            Column {
                OutlinedTextField(pw, { pw = it; bad = false }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Mot de passe") })
                if (bad) Text("Ce n'est pas le bon mot de passe.", color = D_BAD, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton({ if (pw.trim().lowercase() == "papa") onOk() else bad = true }) { Text("Valider") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun DHome(g: Game) {
    val d = g.dictee
    var ask by remember { mutableStateOf<Pair<String, Week>?>(null) }
    ask?.let { (what, wk) ->
        AskPassword({ ask = null }) {
            ask = null
            if (what == "edit") d.openEdit(wk) else d.store.deleteWeek(wk.id)
        }
    }
    val weeks = d.store.weeks
    val ver = g.progress.version
    val n = remember(ver, weeks) { d.dueCount() }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DTop("Orthographe", "La dictée de mots", { g.goMenu() })
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(D_ACC).clickable(enabled = n > 0) { d.start() }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("🎧  Dictée du jour", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (weeks.isEmpty()) "Ajoute d'abord les mots de la semaine." else "$n mots : ceux de la semaine d'abord, puis des révisions.",
                fontSize = 14.sp, color = Color.White
            )
        }
        DBtn("➕  Ajout des mots de ma semaine", Color.White, D_ACC, Modifier.fillMaxWidth()) { d.openAdd() }
        Text("Mes listes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = D_INK)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(weeks, key = { it.id }) { wk ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            wk.label + if (wk.id == weeks.first().id) "  · en cours" else "", fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, color = D_INK, modifier = Modifier.weight(1f)
                        )
                        Text("Modifier", fontSize = 12.sp, color = D_ACC, modifier = Modifier.clickable { ask = "edit" to wk }.padding(4.dp))
                        Text("Supprimer", fontSize = 12.sp, color = D_BAD, modifier = Modifier.clickable { ask = "del" to wk }.padding(4.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        wk.words.forEach { w ->
                            val b = remember(ver, w.w) { g.progress.wordBox(w.w) }
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(if (b >= 3) Color(0xFFE6F6EC) else D_BG)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text(w.w + if (b >= 3) " ✓" else "", fontSize = 14.sp, color = D_INK) }
                        }
                    }
                    DBtn("▶  Réviser cette liste", D_BG, D_INK, Modifier.fillMaxWidth()) { d.start(wk.words.shuffled()) }
                }
            }
        }
    }
}

@Composable
private fun DAdd(d: DicteeGame, wk: Week?) {
    var label by remember(wk?.id) { mutableStateOf(wk?.label ?: "") }
    var text by remember(wk?.id) { mutableStateOf(wk?.let { d.store.asText(it) } ?: "") }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DTop(if (wk == null) "Mots de ma semaine" else "Modifier la liste", "Pour le parent", { d.back() })
        Text(
            "Un mot par ligne, avec son article si tu veux (le livre). Pour un mot qui se prononce comme un autre, ajoute une phrase après « ; ».\nExemple :\nle livre\nverre ; Je bois dans un verre.",
            fontSize = 13.sp, lineHeight = 17.sp, color = D_SUB
        )
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Nom de la liste (facultatif)") }, singleLine = true)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Les mots") })
        if (msg.isNotEmpty()) Text(msg, fontSize = 14.sp, color = D_GOOD, fontWeight = FontWeight.SemiBold)
        DBtn("Enregistrer", D_ACC, Color.White, Modifier.fillMaxWidth()) {
            if (wk != null) {
                d.store.updateWeek(wk.id, label, text); d.back()
            } else {
                val k = d.store.addWeek(label, text)
                if (k > 0) { msg = "$k mots enregistrés ✓"; text = ""; label = ""; d.back() } else msg = "Écris au moins un mot."
            }
        }
    }
}

/** Écriture cursive scolaire (Playwrite France Moderne, licence SIL OFL). */
private val CURSIVE = FontFamily(Font(R.font.playwrite_fr_moderne))

@Composable
private fun DPlay(d: DicteeGame) {
    val w = d.current ?: return
    val target = w.w
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DTop("Dictée", "Mot ${d.idx + 1} sur ${d.list.size}" + if (d.isNew(w)) " · nouveau" else " · révision", { d.back() })
        DBtn("🔊  Écouter le mot", Color.White, D_ACC, Modifier.fillMaxWidth()) { d.sayWord() }
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            val al = d.align
            when (d.phase) {
                DPhase.TYPE1 -> {
                    Text("Écris le mot que tu entends.", fontSize = 15.sp, color = D_SUB)
                    LetterRow(d.typed.map { it.toString() }.ifEmpty { listOf("") }) { _, c -> Triple(c, D_INK, D_LINE) }
                }
                DPhase.SHOW1 -> {
                    Text("Les lettres en gris ne sont pas les bonnes.", fontSize = 15.sp, color = D_SUB)
                    LetterRow(d.first.map { it.toString() }) { i, c ->
                        if (al != null && al.typedOk[i]) Triple(c, D_INK, D_GOOD) else Triple(c, D_GREY, D_LINE)
                    }
                    DBtn("✏️  Essaie de corriger", D_ACC, Color.White, Modifier.fillMaxWidth()) { d.retry() }
                }
                DPhase.TYPE2 -> {
                    Text("Réécris tout le mot. Les trous sont à remplir.", fontSize = 15.sp, color = D_SUB)
                    // Le mot avec des trous : les bonnes lettres restent, les autres sont effacées.
                    val n = maxOf(target.length, d.typed.length)
                    LetterRow((0 until n).map { k ->
                        when {
                            k < d.typed.length -> d.typed[k].toString()
                            k < target.length && target[k] == ' ' -> " "
                            k < target.length && al != null && al.targetKnown[k] -> target[k].toString()
                            else -> ""
                        }
                    }) { k, c ->
                        if (k < d.typed.length) Triple(c, D_INK, D_ACC)
                        else if (c != "") Triple(c, D_GREY, D_LINE) else Triple(c, D_INK, D_LINE)
                    }
                }
                DPhase.OK -> {
                    // L'encouragement reste discret : c'est le mot qu'on doit regarder.
                    Text("Bravo ! ⭐", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = D_SUB)
                    Spacer(Modifier.height(40.dp))
                    LetterRow(target.map { it.toString() }) { _, c -> Triple(c, D_GOOD, D_GOOD) }
                }
                DPhase.FAIL -> {
                    Text("Pas tout à fait… Voici la bonne réponse :", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = D_SUB, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(40.dp))
                    LetterRow(target.map { it.toString() }) { _, c -> Triple(c, D_INK, D_LINE) }
                    Text("Regarde bien le mot pour le retenir.", fontSize = 13.sp, color = D_SUB)
                }
            }
        }
        if (d.phase == DPhase.OK || d.phase == DPhase.FAIL) {
            DBtn(if (d.idx + 1 < d.list.size) "Mot suivant ▶" else "Terminer ▶", D_ACC, Color.White, Modifier.fillMaxWidth()) { d.next() }
        } else if (d.phase != DPhase.SHOW1) {
            Keyboard(d)
        }
    }
}

/**
 * Le mot en deux écritures, sans cadre autour des lettres :
 * en capitales, puis en cursive posée sur des lignes de cahier.
 * Une case vide ("") est un trou à remplir ; la taille s'adapte à la longueur du mot.
 */
@Composable
private fun LetterRow(cells: List<String>, style: (Int, String) -> Triple<String, Color, Color>) {
    val styled = cells.mapIndexed { i, c -> val (t, col, _) = style(i, c); t to col }
    fun build(upper: Boolean): AnnotatedString = buildAnnotatedString {
        styled.forEach { (t, col) ->
            when (t) {
                "" -> withStyle(SpanStyle(color = D_GREY)) { append("_") }
                " " -> append(" ")
                else -> withStyle(SpanStyle(color = col)) { append(if (upper) t.uppercase() else t) }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val n = styled.size.coerceAtLeast(3)
        val fsCap = minOf(32f, maxWidth.value / (n * 0.78f))
        val fsCur = minOf(40f, maxWidth.value / (n * 0.62f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(build(true), fontSize = fsCap.sp, fontWeight = FontWeight.Bold, letterSpacing = (fsCap * 0.08f).sp, maxLines = 1, softWrap = false)
            SeyesCursive(build(false), fsCur)
        }
    }
}

/** La cursive sur 4 lignes de cahier, la ligne d'écriture (plus foncée) sous les lettres. */
@Composable
private fun SeyesCursive(text: AnnotatedString, fs: Float) {
    var base by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxWidth().drawBehind {
            if (base > 0f) {
                val step = with(density) { fs.sp.toPx() } * 0.26f
                for (k in -1..3) {
                    val y = base - k * step
                    drawLine(
                        if (k == 0) Color(0xFF8C86A6) else Color(0xFFD9D5E6),
                        Offset(0f, y), Offset(size.width, y), strokeWidth = if (k == 0) 2.5f else 1.4f
                    )
                }
            }
        },
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text, fontFamily = CURSIVE, fontSize = fs.sp, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(vertical = 4.dp),
            onTextLayout = { base = it.firstBaseline + with(density) { 4.dp.toPx() } }
        )
    }
}

private val ROWS = listOf("azertyuiop", "qsdfghjklm", "wxcvbn'-")
private const val ACCENTS = "éèêëàâçùûîïôœ"

/**
 * Clavier adaptatif : les lettres utiles pour écrire le mot, ou qu'on pourrait confondre en l'écrivant,
 * sont mises en valeur ; les autres passent en retrait (elles restent utilisables).
 */
object Kb {
    const val VOWELS = "aeiouyéèêëàâùûîïôœ"

    /** Confusions fréquentes à l'écrit en français (sons proches, lettres muettes, doubles écritures). */
    private val CONF: Map<Char, String> = mapOf(
        'a' to "àâe", 'à' to "aâ", 'â' to "aà",
        'e' to "éèêëaiou", 'é' to "eèêai", 'è' to "eéêai", 'ê' to "eéèai", 'ë' to "eï",
        'i' to "îïye", 'î' to "iï", 'ï' to "iî", 'y' to "i",
        'o' to "ôaue", 'ô' to "oau", 'u' to "ùûo", 'ù' to "uû", 'û' to "uù", 'œ' to "oeu",
        'c' to "kqsçgh", 'ç' to "cs", 'k' to "cq", 'q' to "cku", 's' to "cçzxt", 'z' to "s",
        'x' to "skc", 'g' to "jcu", 'j' to "g", 'f' to "pvh", 'p' to "bh", 'b' to "pd",
        'd' to "tb", 't' to "dh", 'v' to "fw", 'w' to "v", 'm' to "n", 'n' to "m", 'h' to "",
        'l' to "", 'r' to ""
    )

    fun relevant(word: String): Set<Char> {
        val out = mutableSetOf<Char>()
        for (c in word) { out += c; CONF[c]?.let { out.addAll(it.toList()) } }
        // Lettres muettes fréquentes en fin de mot, et le « h » muet.
        out.addAll("est".toList())
        if (' ' in word) out += ' '
        if (word.any { it in "cpts" }) out += 'h'
        if ("eu" in word || "oe" in word) out += 'œ'
        return out
    }
}

@Composable
private fun Keyboard(d: DicteeGame) {
    val target = d.current?.w ?: ""
    val rel = remember(target) { Kb.relevant(target) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(VOW))
            Text("voyelles", fontSize = 12.sp, color = D_SUB)
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(CONS))
            Text("consonnes", fontSize = 12.sp, color = D_SUB)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ACCENTS.forEach { c -> Key(c, c in rel, Modifier.weight(1f)) { d.key(c.toString()) } }
        }
        ROWS.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c -> Key(c, c in rel || (c in "'-" && c in target), Modifier.weight(1f)) { d.key(c.toString()) } }
                if (r == 2) {
                    Box(
                        Modifier.weight(2f).height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFE3E0))
                            .border(1.dp, D_LINE, RoundedCornerShape(8.dp)).clickable { d.erase() },
                        contentAlignment = Alignment.Center
                    ) { Text("⌫", fontSize = 21.sp, color = D_INK) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val sp = ' ' in rel
            Box(
                Modifier.weight(3f).height(46.dp).clip(RoundedCornerShape(8.dp)).background(if (sp) Color.White else Color(0xFFF1F0F4))
                    .border(if (sp) 1.5.dp else 1.dp, if (sp) Color(0xFF9C94B8) else D_LINE, RoundedCornerShape(8.dp))
                    .clickable { if (d.typed.isNotEmpty() && !d.typed.endsWith(" ")) d.key(" ") },
                contentAlignment = Alignment.Center
            ) { Text("espace", fontSize = 15.sp, color = if (sp) D_INK else Color(0xFFBDBAC8)) }
            DBtn("✓  J'ai fini", D_GOOD, Color.White, Modifier.weight(2f), enabled = d.typed.isNotEmpty()) { d.validate() }
        }
    }
}

private val VOW = Color(0xFFFFD9B0)
private val CONS = Color(0xFFCFE0FF)

@Composable
private fun Key(c: Char, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val vowel = c in Kb.VOWELS
    val bg = when {
        !active -> Color(0xFFF1F0F4)
        c == '\'' || c == '-' -> Color.White
        vowel -> VOW
        else -> CONS
    }
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .border(if (active) 1.5.dp else 1.dp, if (active) Color(0xFF9C94B8) else D_LINE, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            c.toString(), fontSize = if (active) 22.sp else 17.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) D_INK else Color(0xFFBDBAC8)
        )
    }
}

@Composable
private fun DResult(d: DicteeGame) {
    val total = d.list.size
    val ok = d.firstOk
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        val stars = if (d.missed.isEmpty() && d.secondOk == 0) 3 else if (d.missed.size <= 1) 2 else 1
        Text("★".repeat(stars) + "☆".repeat(3 - stars), fontSize = 54.sp, color = Color(0xFFE0A100))
        Text(if (stars == 3) "Parfait !" else if (stars == 2) "Bravo !" else "Bien joué !", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = D_INK)
        Text("$ok du premier coup · ${d.secondOk} corrigés · ${d.missed.size} à revoir (sur $total)", fontSize = 16.sp, color = D_INK, textAlign = TextAlign.Center)
        if (d.missed.isNotEmpty()) {
            Text("À revoir : " + d.missed.joinToString(", "), fontSize = 15.sp, color = D_BAD, textAlign = TextAlign.Center)
            DBtn("🔁  Réessayer ces mots", D_ACC, Color.White, Modifier.fillMaxWidth()) { d.replayMissed() }
        }
        Text("Les mots ratés reviendront dès demain ; les mots sus, de plus en plus tard.", fontSize = 13.sp, color = D_SUB, textAlign = TextAlign.Center)
        DBtn("Retour", Color.White, D_INK, Modifier.fillMaxWidth()) { d.back() }
    }
}
