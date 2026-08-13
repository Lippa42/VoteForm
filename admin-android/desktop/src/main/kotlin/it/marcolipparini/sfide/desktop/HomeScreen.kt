package it.marcolipparini.sfide.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.samples.SampleRooms
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Example(val label: String, val build: () -> RoomDefinition)

private val examples = listOf(
    Example("Quiz + Torneo") { SampleRooms.quizTournament() },
    Example("Sfida a voti") { SampleRooms.cookingVoting() },
    Example("Torneo a eliminazione") { SampleRooms.cookingKnockout() },
    Example("Questionario") { SampleRooms.quickSurvey() },
    Example("Serata (timeline)") { SampleRooms.showcase() },
    Example("Timeline con Torneo") { SampleRooms.tournamentShow() },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    store: DesktopStore,
    refreshKey: Int,
    onNew: () -> Unit,
    onStart: (RoomDefinition) -> Unit,
    onHistory: () -> Unit,
    onRelayChange: (String) -> Unit,
) {
    val templates = remember(refreshKey) { store.templates() }
    var relay by remember { mutableStateOf(DesktopHost.relayUrl ?: "") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎯 Sfide", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Admin · desktop", color = InkSoft, fontSize = 14.sp)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onHistory, modifier = Modifier.align(Alignment.CenterEnd)) { Text("Storico partite") }
                }
            }

            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("＋  Crea nuova stanza (builder a slide)") }

            SectionCard {
                Label("Avvia una stanza d'esempio")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEach { ex ->
                        OutlinedButton(onClick = { onStart(ex.build()) }) { Text(ex.label) }
                    }
                }
            }

            if (templates.isNotEmpty()) {
                SectionCard {
                    Label("I tuoi template salvati")
                    templates.forEach { room ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(room.meta.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("PIN ${room.meta.pin} · ${room.timeline.size} fasi", color = InkSoft, fontSize = 12.sp)
                            }
                            Button(onClick = { onStart(room) }) { Text("▶ Avvia") }
                        }
                    }
                }
            }

            SectionCard {
                Label("Spettatori da remoto (relay)")
                Text("Incolla l'URL del relay (wss://…) per far entrare gli spettatori da reti diverse. Vuoto = solo LAN.", color = InkSoft, fontSize = 12.sp)
                TField(relay, { relay = it; onRelayChange(it) }, "URL relay (opzionale)")
            }

            Text(
                "Il computer diventa l'host: apri il Visualizzatore sulla TV e gli spettatori entrano col QR sulla stessa rete (o dal relay).",
                color = InkSoft, fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun HistoryScreen(store: DesktopStore, refreshKey: Int, onBack: () -> Unit) {
    val history = remember(refreshKey) { store.history() }
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("← Indietro") }
                Text("Storico partite", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (history.isEmpty()) {
                Text("Ancora nessuna partita giocata.", color = InkSoft)
            } else {
                Text("${history.size} partite giocate", color = InkSoft, fontSize = 12.sp)
                history.forEach { result ->
                    SectionCard {
                        Text(result.roomTitle, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(fmt.format(Date(result.playedAtEpochMs)), color = InkSoft, fontSize = 12.sp)
                        if (result.winnerLabel != null) {
                            Text("🏆 ${result.winnerLabel}", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        } else if (result.finalStandings.isNotEmpty()) {
                            Text("${result.finalStandings.size} in classifica", color = InkSoft, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
