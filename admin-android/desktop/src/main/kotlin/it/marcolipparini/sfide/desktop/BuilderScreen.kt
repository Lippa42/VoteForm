package it.marcolipparini.sfide.desktop

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.engine.model.MediaAsset
import it.marcolipparini.sfide.engine.model.MediaKind
import it.marcolipparini.sfide.engine.model.RoomDefinition
import kotlinx.coroutines.launch

/**
 * Builder a **presentazione**: la serata è un mazzo di slide (copertina + una per
 * fase) sfogliabili col carosello. Pensato per mouse e tastiera: PagSù/PagGiù per
 * navigare, Ctrl+←/→ per riordinare, Ctrl+I per aggiungere, Ctrl+Canc per eliminare,
 * Ctrl+S per salvare, Ctrl+Invio per avviare.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BuilderScreen(
    store: DesktopStore,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onStart: (RoomDefinition) -> Unit,
) {
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
    val pagerState = rememberPagerState(pageCount = { segments.size + 1 })

    fun build(): RoomDefinition = buildTimelineRoom(title, pin, palettes[paletteIndex], competitors, segments, assets)
    fun goTo(page: Int) { scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, segments.size)) } }

    fun insert(type: SegmentType) {
        val p = pagerState.currentPage
        val at = (if (p == 0) 0 else p).coerceAtMost(segments.size)
        segments.add(at, SegmentDraft(type))
        showAdd = false
        goTo(at + 1)
    }

    fun move(idx: Int, delta: Int) {
        val target = idx + delta
        if (idx < 0 || target < 0 || target >= segments.size) return
        segments.add(target, segments.removeAt(idx))
        goTo(target + 1)
    }

    fun removeAt(idx: Int) {
        if (idx < 0 || segments.size <= 1) return
        segments.removeAt(idx)
        goTo((idx + 1).coerceAtMost(segments.size))
    }

    val onPicked: (SegmentDraft) -> Unit = { seg ->
        pickAndCopyAsset(seg.mediaKind, store.assetsDir)?.let { asset ->
            assets.add(asset)
            seg.assetId = asset.id
        }
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val cur = pagerState.currentPage
            val seg = cur - 1
            when {
                e.key == Key.PageDown -> { goTo(cur + 1); true }
                e.key == Key.PageUp -> { goTo(cur - 1); true }
                e.isCtrlPressed && e.key == Key.DirectionRight -> { move(seg, +1); true }
                e.isCtrlPressed && e.key == Key.DirectionLeft -> { move(seg, -1); true }
                e.isCtrlPressed && e.key == Key.I -> { showAdd = !showAdd; true }
                e.isCtrlPressed && (e.key == Key.Delete || e.key == Key.Backspace) -> { removeAt(seg); true }
                e.isCtrlPressed && e.key == Key.S -> { store.saveTemplate(build()); onSaved(); true }
                e.isCtrlPressed && e.key == Key.Enter -> { onStart(build()); true }
                else -> false
            }
        },
    ) {
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
            Text(
                "PagSù/PagGiù naviga · Ctrl+←/→ sposta · Ctrl+I aggiungi · Ctrl+Canc elimina · Ctrl+S salva · Ctrl+Invio avvia",
                color = InkSoft, fontSize = 11.sp,
            )
        }

        // ---- Mazzo di slide (carosello) --------------------------------------
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 12.dp,
        ) { page ->
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
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
            }
        }

        // ---- Filmstrip -------------------------------------------------------
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

        // ---- Scelta tipo fase ------------------------------------------------
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
            OutlinedButton(onClick = { store.saveTemplate(build()); onSaved() }, modifier = Modifier.weight(1f)) { Text("💾 Salva template") }
            Button(onClick = { onStart(build()) }, modifier = Modifier.weight(1f)) { Text("▶ Avvia ora") }
        }
    }
}

// ---- Slide di copertina -----------------------------------------------------

@Composable
private fun CoverSlide(
    title: String, onTitle: (String) -> Unit,
    pin: String, onPin: (String) -> Unit,
    paletteIndex: Int, onPalette: (Int) -> Unit,
    competitors: MutableList<CompetitorDraft>,
) {
    SlideFrame {
        SlideHeader("🎬", "COPERTINA", "Impostazioni della serata")
        TField(title, onTitle, "Titolo della stanza")
        TField(pin, onPin, "PIN d'ingresso")

        Label("Tema")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palettes.forEachIndexed { i, p ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(hexColor(p.primary))
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
            OutlinedButton(onClick = { competitors.add(CompetitorDraft()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Concorrente") }
        }
        Text("Lascia i concorrenti vuoti per una serata basata solo sul pubblico (quiz/questionario).", color = InkSoft, fontSize = 12.sp)
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
    onPicked: (SegmentDraft) -> Unit,
) {
    SlideFrame {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Bg), contentAlignment = Alignment.Center) {
                Text(iconFor(seg.type), fontSize = 20.sp)
            }
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
private fun MediaBody(seg: SegmentDraft, onPicked: (SegmentDraft) -> Unit) {
    TField(seg.subtitle, { seg.subtitle = it }, "Didascalia")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaKind.entries.forEach { k ->
            if (seg.mediaKind == k) Button(onClick = { seg.mediaKind = k }) { Text(k.name) }
            else OutlinedButton(onClick = { seg.mediaKind = k }) { Text(k.name) }
        }
    }
    OutlinedButton(onClick = { onPicked(seg) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (seg.assetId != null) "File caricato ✓ — cambia" else "Scegli file dal computer")
    }
}

@Composable
private fun HintBody(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bg).padding(16.dp)) {
        Text(text, color = InkSoft, fontSize = 13.sp)
    }
}

// ---- Cornice slide ----------------------------------------------------------

@Composable
private fun SlideFrame(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Line, RoundedCornerShape(18.dp))) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(Accent))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun SlideHeader(icon: String, kicker: String, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Bg), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 20.sp)
        }
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
            .size(38.dp).clip(RoundedCornerShape(10.dp)).background(Bg).border(1.dp, Line, RoundedCornerShape(10.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 16.sp) }
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
                    TField(c.weight, { c.weight = it }, "Peso", Modifier.width(90.dp))
                    if (p.criteria.size > 1) OutlinedButton(onClick = { p.criteria.removeAt(ci) }) { Text("×") }
                }
            }
            OutlinedButton(onClick = { p.criteria.add(CriterionDraft()) }) { Text("+ Criterio") }
            if (seg.prompts.size > 1) OutlinedButton(onClick = { seg.prompts.removeAt(pi) }) { Text("× Rimuovi prova") }
        }
    }
    OutlinedButton(onClick = { seg.prompts.add(PromptDraft()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Prova") }
}
