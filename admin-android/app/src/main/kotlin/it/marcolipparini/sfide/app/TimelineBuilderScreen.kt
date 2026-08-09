package it.marcolipparini.sfide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.engine.model.MediaKind
import it.marcolipparini.sfide.engine.model.RoomDefinition
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineBuilderScreen(
    onCancel: () -> Unit,
    onSaveTemplate: (RoomDefinition) -> Unit,
    onStart: (RoomDefinition) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var paletteIndex by remember { mutableStateOf(0) }
    val competitors = remember { mutableStateListOf(CompetitorDraft(), CompetitorDraft()) }
    val segments = remember {
        mutableStateListOf(SegmentDraft(SegmentType.TITLE), SegmentDraft(SegmentType.QUIZ), SegmentDraft(SegmentType.FINAL))
    }

    fun current(): RoomDefinition =
        buildTimelineRoom(title, pin, palettes[paletteIndex], competitors, segments)

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val f = segments.indexOfFirst { it.id == from.key }
        val t = segments.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) segments.add(t, segments.removeAt(f))
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) { Text("← Indietro") }
                Text("Timeline della serata", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { TField(title, { title = it }, "Titolo della stanza") }
        item { TField(pin, { pin = it }, "PIN") }
        item {
            Label("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                palettes.forEachIndexed { i, p ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(p.primary)))
                            .border(if (paletteIndex == i) 3.dp else 1.dp, if (paletteIndex == i) Ink else Line, CircleShape)
                            .clickable { paletteIndex = i },
                    )
                }
            }
        }
        item {
            Label("Concorrenti")
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
        }
        item { Label("Fasi — trascina l'impugnatura ⠿ per riordinare") }

        items(segments, key = { it.id }) { seg ->
            ReorderableItem(reorderState, key = seg.id) { _ ->
                SegmentCard(
                    seg = seg,
                    handle = Modifier.draggableHandle(),
                    onRemove = { if (segments.size > 1) segments.remove(seg) },
                )
            }
        }

        item {
            Label("Aggiungi una fase")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentType.entries.forEach { type ->
                    OutlinedButton(onClick = { segments.add(SegmentDraft(type)) }) { Text("+ ${type.label}") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onSaveTemplate(current()) }, modifier = Modifier.fillMaxWidth()) { Text("💾 Salva come template") }
                Button(onClick = { onStart(current()) }, modifier = Modifier.fillMaxWidth()) { Text("▶ Avvia ora") }
            }
        }
    }
}

@Composable
private fun SegmentCard(seg: SegmentDraft, handle: Modifier, onRemove: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("⠿", color = InkSoft, fontSize = 22.sp, modifier = handle)
            Text(seg.type.label, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRemove) { Text("×") }
        }
        TField(seg.title, { seg.title = it }, "Titolo della fase")

        when (seg.type) {
            SegmentType.TITLE -> TField(seg.subtitle, { seg.subtitle = it }, "Sottotitolo")
            SegmentType.MEDIA -> {
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
            }
            SegmentType.QUIZ, SegmentType.QUESTIONNAIRE -> QuestionsEditor(seg, quiz = seg.type == SegmentType.QUIZ)
            SegmentType.VOTING, SegmentType.TOURNAMENT -> PromptsEditor(seg)
            SegmentType.STANDINGS, SegmentType.FINAL -> Unit
        }
    }
}

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
