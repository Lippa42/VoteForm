package it.marcolipparini.sfide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Accent = Color(0xFFFF5A63)
private val Bg = Color(0xFF0F1117)
private val Ink = Color(0xFFECEEF5)
private val InkSoft = Color(0xFFA9AEBF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { HostScreen() }
            }
        }
    }
}

@Composable
private fun HostScreen() {
    val ctx = LocalContext.current
    val info by HostController.state.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("🎯 Sfide · Host", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        if (!info.running) {
            Text(
                "Avvia l'host: il telefono diventa il server. Gli altri si collegano dal " +
                    "browser sulla stessa rete WiFi, senza installare nulla.",
                color = InkSoft,
            )
            Button(onClick = { HostService.start(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Avvia la stanza")
            }
        } else {
            Text("PIN", color = InkSoft, fontSize = 12.sp)
            Text(info.pin, color = Accent, fontSize = 40.sp, fontWeight = FontWeight.Bold)

            val qr = remember(info.ip, info.port, info.running) { QrBitmap.forJoin(info.port) }
            qr?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR per entrare",
                    modifier = Modifier.size(200.dp),
                )
            }

            Text("Visualizzatore (TV)", color = InkSoft, fontSize = 12.sp)
            Text("http://${info.ip}:${info.port}/viewer/index.html", color = Ink, fontFamily = FontFamily.Monospace)
            Text("Spettatore", color = InkSoft, fontSize = 12.sp)
            Text("http://${info.ip}:${info.port}/spectator/index.html", color = Ink, fontFamily = FontFamily.Monospace)

            HorizontalDivider(color = Color(0xFF262A36))

            Text("REGIA", color = InkSoft, fontSize = 12.sp)
            val session = HostController.session
            Button(onClick = { scope.launch { session?.next() } }, modifier = Modifier.fillMaxWidth()) {
                Text("▶ Prossima domanda")
            }
            Button(onClick = { scope.launch { session?.lock() } }, modifier = Modifier.fillMaxWidth()) {
                Text("🔒 Chiudi risposte")
            }
            Button(onClick = { scope.launch { session?.reveal() } }, modifier = Modifier.fillMaxWidth()) {
                Text("🎉 Svela risultati")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { HostService.stop(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ferma la stanza")
            }
        }
    }
}
