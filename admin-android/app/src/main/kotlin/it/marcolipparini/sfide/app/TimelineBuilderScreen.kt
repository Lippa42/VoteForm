package it.marcolipparini.sfide.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.engine.model.MediaAsset
import it.marcolipparini.sfide.engine.model.MediaKind
import it.marcolipparini.sfide.engine.model.RoomDefinition
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Builder "a presentazione": la serata è un **mazzo di slide** scorrevoli come in
 * PowerPoint. La prima slide è la copertina (impostazioni della stanza), poi una
 * slide per fase. Si naviga col carosello e con la filmstrip in basso; ogni slide
 * si riordina con le frecce, si duplica-per-tipo dall'aggiunta e si elimina.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TimelineBuilderScreen(
    onCancel: () -> Unit,
    onSaveTemplate: (RoomDefinition) -> Unit,
    onStart: (RoomDefinition) -> Unit,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var paletteIndex by remember { mutableStateOf(0) }
    val competitors = remember { mutableStateListOf(CompetitorDraft(), CompetitorDraft()) }
    val assets = remember { mutableStateListOf<MediaAsset>() }
    val segments = remember {
        mutableStateListOf(SegmentDraft(SegmentType.TITLE), SegmentDraft(SegmentType.QUIZ), SegmentDraft(SegmentType.FINAL))
    }
    var showAdd by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    // Pagine del mazzo: copertina (0) + una per fase.
    val pagerState = rememberPagerState(pageCount = { segments.size + 1 })

    fun current(): RoomDefinition =
        buildTimelineRoom(title, pin, palettes[paletteIndex], competitors, segments, assets)

    val onPicked: (SegmentDraft, Uri?) -> Unit = { seg, uri ->
        if (uri != null) {
            val id = UUID.randomUUID().toString()
            val path = copyAssetToInternal(ctx, uri, id)
            if (path != null) {
                assets.add(MediaAsset(id = id, localPath = path))
                seg.assetId = id
            }
        }
    }

    fun goTo(page: Int) = scope.launch { pagerState.animateScrollToPage(page) }

    fun insert(type: SegmentType) {
        val p = pagerState.currentPage
        val at = (if (p == 0) 0 else p).coerceAtMost(segments.size)
        segments.add(at, SegmentDraft(type))
        showAdd = false
        goTo(at + 1)
    }

    fun move(idx: Int, delta: Int) {
        val target = idx + delta
        if (target < 0 || target >= segments.size) return
        segments.add(target, segments.removeAt(idx))
        goTo(target + 1)
    }

    fun removeAt(idx: Int) {
        if (segments.size <= 1) return
        segments.removeAt(idx)
        goTo((idx + 1).coerceAtMost(segments.size))
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Barra superiore -------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel) { Text("←") }
            Column(Modifier.weight(1f)) {
                Text("Costruisci la serata", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val label = if (pagerState.currentPage == 0) "Copertina" else "Fase ${pagerState.currentPage} di ${segments.size}"
                Text(label, color = InkSoft, fontSize = 12.sp)
            }
            Text("${pagerState.currentPage + 1}/${segments.size + 1}", color = InkSoft, fontSize = 13.sp)
        }

        // ---- Il mazzo di slide (carosello) -----------------------------------
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 10.dp,
        ) { page ->
            if (page == 0) {
                CoverSlide(
                    title = title, onTitle = { title = it },
                    pin = pin, onPin = { pin = it },
                    paletteIndex = paletteIndex, onPalette = { paletteIndex = it },
                    competitors = competitors,
                )
            } else {
                val idx = page - 1
                val seg = segments[idx]
                SegmentSlide(
                    seg = seg,
                    number = page,
                    isFirst = idx == 0,
                    isLast = idx == segments.size - 1,
                    canRemove = segments.size > 1,
                    onMoveLeft = { move(idx, -1) },
                    onMoveRight = { move(idx, +1) },
                    onRemove = { removeAt(idx) },
                    onPicked = onPicked,
                )
            }
        }

        // ---- Filmstrip di navigazione ---------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilmChip("🎬", "Cover", selected = pagerState.currentPage == 0) { goTo(0) }
            segments.forEachIndexed { i, s ->
                FilmChip(iconFor(s.type), "${i + 1}", selected = pagerState.currentPage == i + 1) { goTo(i + 1) }
            }
            FilmChip("＋", "Fase", selected = false, accented = true) { showAdd = !showAdd }
        }

        // ---- Scelta del tipo di fase (a comparsa) ---------------------------
        if (showAdd) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Inserisci una fase dopo la slide corrente", color = InkSoft, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentType.entries.forEach { type ->
                        OutlinedButton(onClick = { insert(type) }) { Text("${iconFor(type)} ${type.label}") }
                    }
                }
            }
        }

        // ---- Azioni ----------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = { onSaveTemplate(current()) }, modifier = Modifier.weight(1f)) { Text("💾 Salva") }
            Button(onClick = { onStart(current()) }, modifier = Modifier.weight(1f)) { Text("▶ Avvia ora") }
        }
    }
}

// ---- Slide di copertina (impostazioni della stanza) -------------------------

@Composable
private fun CoverSlide(
    title: String, onTitle: (String) -> Unit,
    pin: String, onPin: (String) -> Unit,
    paletteIndex: Int, onPalette: (Int) -> Unit,
    competitors: MutableList<CompetitorDraft>,
) {
    SlideFrame(accent = Accent) {
        SlideHeader(icon = "🎬", kicker = "COPERTINA", name = "Impostazioni della serata")
        TField(title, onTitle, "Titolo della stanza")
        TField(pin, onPin, "PIN d'ingresso")

        Label("Tema")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palettes.forEachIndexed { i, p ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(p.primary)))
                        .border(if (paletteIndex == i) 3.dp else 1.dp, if (paletteIndex == i) Ink else Line, CircleShape)
                        .clickable { onPalette(i) },
                )
            }
        }

        Label("Concorrenti / squadre")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            competitors.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TField(c.name, { c.name = it }, "Concorrente ${i + 1}", Modifier.weight(1f))
                    if (competitors.size > 1) OutlinedButton(onClick = { competitors.removeAt(i) }) { Text("×") }
                }
            }
            OutlinedButton(onClick = { competitors.add(CompetitorDraft()) }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Concorrente")
            }
        }
        Text(
            "Suggerimento: lascia i concorrenti vuoti per una serata basata solo sul pubblico (quiz/questionario).",
            color = InkSoft, fontSize = 12.sp,
        )
    }
}

// ---- Slide di una fase ------------------------------------------------------

@Composable
private fun SegmentSlide(
    seg: SegmentDraft,
    number: Int,
    isFirst: Boolean,
    isLast: Boolean,
    canRemove: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    onPicked: (SegmentDraft, Uri?) -> Unit,
) {
    SlideFrame(accent = Accent) {
        // Intestazione della slide: tipo + strumenti (riordina / elimina).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Bg),
                contentAlignment = Alignment.Center,
            ) { Text(iconFor(seg.type), fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text("FASE $number", color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(seg.type.label, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            IconChip("◀", enabled = !isFirst, onClick = onMoveLeft)
            IconChip("▶", enabled = !isLast, onClick = onMoveRight)
            IconChip("🗑", enabled = canRemove, onClick = onRemove)
        }

        TField(seg.title, { seg.title = it }, "Titolo della fase")

        when (seg.type) {
            SegmentType.TITLE -> TField(seg.subtitle, { seg.subtitle = it }, "Sottotitolo")
            SegmentType.MEDIA -> MediaBody(seg, onPicked)
            SegmentType.QUIZ, SegmentType.QUESTIONNAIRE -> QuestionsEditor(seg, quiz = seg.type == SegmentType.QUIZ)
            SegmentType.VOTING, SegmentType.TOURNAMENT -> PromptsEditor(seg)
            SegmentType.STANDINGS -> HintBody("Mostra la classifica intermedia dei concorrenti.")
            SegmentType.FINAL -> HintBody("Chiude la serata con la classifica finale e il vincitore.")
        }
    }
}

@Composable
private fun MediaBody(seg: SegmentDraft, onPicked: (SegmentDraft, Uri?) -> Unit) {
    TField(seg.subtitle, { seg.subtitle = it }, "Didascalia")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaKind.entries.forEach { k ->
            if (seg.mediaKind == k) {
                Button(onClick = { seg.mediaKind = k }) { Text(k.name) }
            } else {
                OutlinedButton(onClick = { seg.mediaKind = k }) { Text(k.name) }
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> onPicked(seg, uri) }
    OutlinedButton(onClick = { picker.launch(mimeForKind(seg.mediaKind)) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (seg.assetId != null) "File caricato ✓ — cambia" else "Scegli file dal dispositivo")
    }
}

@Composable
private fun HintBody(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bg).padding(16.dp),
    ) { Text(text, color = InkSoft, fontSize = 13.sp) }
}

// ---- Cornice comune "slide" -------------------------------------------------

@Composable
private fun SlideFrame(accent: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Line, RoundedCornerShape(18.dp)),
    ) {
        // Striscia accent in alto: dà il "colore" della slide come in una presentazione.
        Box(Modifier.fillMaxWidth().height(6.dp).background(accent))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun SlideHeader(icon: String, kicker: String, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Bg),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 20.sp) }
        Column {
            Text(kicker, color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(name, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---- Filmstrip & pulsanti ---------------------------------------------------

@Composable
private fun FilmChip(icon: String, label: String, selected: Boolean, accented: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent else Surface)
            .border(1.dp, if (selected || accented) Accent else Line, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 18.sp)
        Text(label, color = if (selected) Bg else InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconChip(icon: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Bg)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 16.sp) }
}

private fun iconFor(type: SegmentType): String = when (type) {
    SegmentType.TITLE -> "🏷️"
    SegmentType.MEDIA -> "🖼️"
    SegmentType.STANDINGS -> "📊"
    SegmentType.QUIZ -> "❓"
    SegmentType.VOTING -> "⭐"
    SegmentType.TOURNAMENT -> "🏆"
    SegmentType.QUESTIONNAIRE -> "🗳️"
    SegmentType.FINAL -> "🎉"
}

// ---- Editor riusati ---------------------------------------------------------

@Composable
private fun QuestionsEditor(seg: SegmentDraft, quiz: Boolean) {
    seg.questions.forEachIndexed { qi, q ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TField(q.text, { q.text = it }, "Domanda ${qi + 1}")
            if (quiz) Text("Spunta la risposta corretta", color = InkSoft, fontSize = 12.sp)
            q.options.forEachIndexed { oi, o ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (quiz) Checkbox(checked = o.correct, onCheckedChange = { o.correct = it })
                    TField(o.text, { o.text = it }, "Opzione ${oi + 1}", Modifier.weight(1f))
                    if (q.options.size > 1) OutlinedButton(onClick = { q.options.removeAt(oi) }) { Text("×") }
                }
            }
            OutlinedButton(onClick = { q.options.add(OptionDraft()) }) { Text("+ Opzione") }
            if (seg.questions.size > 1) OutlinedButton(onClick = { seg.questions.removeAt(qi) }) { Text("× Rimuovi domanda") }
        }
    }
    OutlinedButton(onClick = { seg.questions.add(QuestionDraft()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Domanda") }
}

@Composable
private fun PromptsEditor(seg: SegmentDraft) {
    seg.prompts.forEachIndexed { pi, p ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TField(p.title, { p.title = it }, "Prova ${pi + 1}")
            p.criteria.forEachIndexed { ci, c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TField(c.label, { c.label = it }, "Criterio", Modifier.weight(1f))
                    TField(c.weight, { c.weight = it }, "Peso", Modifier.width(80.dp))
                    if (p.criteria.size > 1) OutlinedButton(onClick = { p.criteria.removeAt(ci) }) { Text("×") }
                }
            }
            OutlinedButton(onClick = { p.criteria.add(CriterionDraft()) }) { Text("+ Criterio") }
            if (seg.prompts.size > 1) OutlinedButton(onClick = { seg.prompts.removeAt(pi) }) { Text("× Rimuovi prova") }
        }
    }
    OutlinedButton(onClick = { seg.prompts.add(PromptDraft()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Prova") }
}

@Composable
private fun TField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier)
}

@Composable
private fun Label(text: String) {
    Text(text, color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

private fun mimeForKind(kind: MediaKind): String = when (kind) {
    MediaKind.IMAGE -> "image/*"
    MediaKind.MUSIC -> "audio/*"
    MediaKind.VIDEO -> "video/*"
}

/** Copia il file scelto nella storage interna dell'app e ne ritorna il percorso. */
private fun copyAssetToInternal(ctx: Context, uri: Uri, id: String): String? = runCatching {
    val dir = File(ctx.filesDir, "assets").apply { mkdirs() }
    val ext = extForMime(ctx.contentResolver.getType(uri))
    val file = File(dir, "$id$ext")
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file.absolutePath
}.getOrNull()

private fun extForMime(mime: String?): String = when (mime) {
    "image/png" -> ".png"
    "image/jpeg" -> ".jpg"
    "image/webp" -> ".webp"
    "image/gif" -> ".gif"
    "audio/mpeg" -> ".mp3"
    "audio/mp4", "audio/aac" -> ".m4a"
    "audio/ogg" -> ".ogg"
    "audio/wav", "audio/x-wav" -> ".wav"
    "video/mp4" -> ".mp4"
    "video/webm" -> ".webm"
    else -> ""
}
