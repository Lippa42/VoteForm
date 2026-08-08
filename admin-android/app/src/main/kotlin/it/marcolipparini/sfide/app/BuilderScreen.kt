package it.marcolipparini.sfide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.engine.model.RoomDefinition

@Composable
fun BuilderScreen(
    onCancel: () -> Unit,
    onSaveTemplate: (RoomDefinition) -> Unit,
    onStart: (RoomDefinition) -> Unit,
) {
    var mode by remember { mutableStateOf(BuildMode.QUIZ) }
    var title by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val competitors = remember { mutableStateListOf(CompetitorDraft(), CompetitorDraft()) }
    val questions = remember { mutableStateListOf(QuestionDraft()) }
    val prompts = remember { mutableStateListOf(PromptDraft()) }
    var paletteIndex by remember { mutableStateOf(0) }

    fun current(): RoomDefinition =
        buildRoom(mode, title, pin, competitors, questions, prompts, palettes[paletteIndex])

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("← Indietro") }
            Text("Nuova stanza", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        SectionTitle("Modalità")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeButton("Quiz", mode == BuildMode.QUIZ, Modifier.weight(1f)) { mode = BuildMode.QUIZ }
            ModeButton("Sfida a voti", mode == BuildMode.VOTING, Modifier.weight(1f)) { mode = BuildMode.VOTING }
        }

        Field(title, { title = it }, "Titolo")
        Field(pin, { pin = it }, "PIN")

        SectionTitle("Tema")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palettes.forEachIndexed { i, p ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(p.primary)))
                        .border(
                            width = if (paletteIndex == i) 3.dp else 1.dp,
                            color = if (paletteIndex == i) Ink else Line,
                            shape = CircleShape,
                        )
                        .clickable { paletteIndex = i },
                )
            }
        }

        SectionTitle("Concorrenti")
        competitors.forEachIndexed { i, c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(c.name, { c.name = it }, "Concorrente ${i + 1}", Modifier.weight(1f))
                if (competitors.size > 1) RemoveButton { competitors.removeAt(i) }
            }
        }
        AddButton("+ Aggiungi concorrente") { competitors.add(CompetitorDraft()) }

        if (mode == BuildMode.QUIZ) {
            SectionTitle("Domande")
            questions.forEachIndexed { qi, q ->
                CardBox {
                    Field(q.text, { q.text = it }, "Domanda ${qi + 1}")
                    Field(q.points, { q.points = it }, "Punti", Modifier.width(140.dp))
                    Text("Risposte (spunta la corretta)", color = InkSoft, fontSize = 12.sp)
                    q.options.forEachIndexed { oi, o ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = o.correct, onCheckedChange = { o.correct = it })
                            Field(o.text, { o.text = it }, "Risposta ${oi + 1}", Modifier.weight(1f))
                            if (q.options.size > 1) RemoveButton { q.options.removeAt(oi) }
                        }
                    }
                    AddButton("+ Risposta") { q.options.add(OptionDraft()) }
                    if (questions.size > 1) OutlinedButton(onClick = { questions.removeAt(qi) }) { Text("× Rimuovi domanda") }
                }
            }
            AddButton("+ Aggiungi domanda") { questions.add(QuestionDraft()) }
        } else {
            SectionTitle("Prove e criteri")
            prompts.forEachIndexed { pi, p ->
                CardBox {
                    Field(p.title, { p.title = it }, "Prova ${pi + 1}")
                    Text("Criteri (etichetta e peso)", color = InkSoft, fontSize = 12.sp)
                    p.criteria.forEachIndexed { ci, c ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(c.label, { c.label = it }, "Criterio", Modifier.weight(1f))
                            Field(c.weight, { c.weight = it }, "Peso", Modifier.width(90.dp))
                            if (p.criteria.size > 1) RemoveButton { p.criteria.removeAt(ci) }
                        }
                    }
                    AddButton("+ Criterio") { p.criteria.add(CriterionDraft()) }
                    if (prompts.size > 1) OutlinedButton(onClick = { prompts.removeAt(pi) }) { Text("× Rimuovi prova") }
                }
            }
            AddButton("+ Aggiungi prova") { prompts.add(PromptDraft()) }
        }

        HorizontalDivider(color = Line)
        Button(onClick = { onSaveTemplate(current()) }, modifier = Modifier.fillMaxWidth()) { Text("💾 Salva come template") }
        Button(onClick = { onStart(current()) }, modifier = Modifier.fillMaxWidth()) { Text("▶ Avvia ora") }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ModeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text("×") }
}

@Composable
private fun CardBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
