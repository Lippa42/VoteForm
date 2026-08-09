package it.marcolipparini.sfide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.samples.SampleRooms
import it.marcolipparini.sfide.persistence.SfideStore
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    store: SfideStore,
    onNew: () -> Unit,
    onStart: (RoomDefinition) -> Unit,
    onHistory: () -> Unit,
) {
    val templates by store.templates.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("🎯 Sfide · Host", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("➕ Crea nuova stanza") }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("📊 Storico partite") }

        Text("Esempi rapidi", color = InkSoft, fontSize = 12.sp)
        OutlinedButton(onClick = { onStart(SampleRooms.quizTournament()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Avvia Quiz d'esempio")
        }
        OutlinedButton(onClick = { onStart(SampleRooms.cookingVoting()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Avvia Sfida a voti d'esempio")
        }
        OutlinedButton(onClick = { onStart(SampleRooms.cookingKnockout()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Avvia Torneo d'esempio")
        }

        HorizontalDivider(color = Line)
        Text("Le tue stanze salvate", color = InkSoft, fontSize = 12.sp)

        if (templates.isEmpty()) {
            Text("Nessuna stanza salvata. Creane una con il pulsante qui sopra.", color = InkSoft)
        } else {
            templates.forEach { room ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(room.meta.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("${roomModeLabel(room)} · PIN ${room.meta.pin}", color = InkSoft, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onStart(room) }, modifier = Modifier.weight(1f)) { Text("Avvia") }
                        OutlinedButton(
                            onClick = { scope.launch { store.deleteTemplate(room) } },
                            modifier = Modifier.weight(1f),
                        ) { Text("Elimina") }
                    }
                }
            }
        }
    }
}

internal fun roomModeLabel(room: RoomDefinition): String = when (room.mode) {
    is GameModeConfig.Quiz -> "Quiz"
    is GameModeConfig.Voting -> "Sfida a voti"
    is GameModeConfig.Questionnaire -> "Questionario"
}
