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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.persistence.SfideStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(store: SfideStore, onBack: () -> Unit) {
    val history by store.history.collectAsState(initial = emptyList())
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("← Indietro") }
            Text("Storico partite", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (history.isEmpty()) {
            Text("Ancora nessuna partita giocata.", color = InkSoft)
        } else {
            Text("${history.size} partite giocate", color = InkSoft, fontSize = 12.sp)
            history.forEach { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(result.roomTitle, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
