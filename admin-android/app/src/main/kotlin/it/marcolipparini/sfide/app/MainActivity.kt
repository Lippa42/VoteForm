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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.persistence.SfideStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { AppRoot() }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val store = remember { SfideStore(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val info by HostController.state.collectAsState()
    var screen by remember { mutableStateOf("home") }

    when {
        info.running -> HostPanel()
        screen == "builder" -> TimelineBuilderScreen(
            onCancel = { screen = "home" },
            onSaveTemplate = { room -> scope.launch { store.saveTemplate(room) }; screen = "home" },
            onStart = { room -> HostService.startWith(ctx, room) },
        )
        screen == "history" -> HistoryScreen(store = store, onBack = { screen = "home" })
        else -> HomeScreen(
            store = store,
            onNew = { screen = "builder" },
            onStart = { room -> HostService.startWith(ctx, room) },
            onHistory = { screen = "history" },
        )
    }
}

@Composable
private fun HostPanel() {
    val ctx = LocalContext.current
    val info by HostController.state.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("🎯 Sfide · Host", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (info.mode.isNotEmpty()) Text("Modalità: ${info.mode}", color = InkSoft, fontSize = 12.sp)

        Text("PIN", color = InkSoft, fontSize = 12.sp)
        Text(info.pin, color = Accent, fontSize = 40.sp, fontWeight = FontWeight.Bold)

        val qr = remember(info.ip, info.port, info.running) { QrBitmap.forJoin(info.port) }
        qr?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "QR per entrare", modifier = Modifier.size(200.dp))
        }

        Text("Visualizzatore (TV)", color = InkSoft, fontSize = 12.sp)
        Text("http://${info.ip}:${info.port}/viewer/index.html", color = Ink, fontFamily = FontFamily.Monospace)
        Text("Spettatore", color = InkSoft, fontSize = 12.sp)
        Text("http://${info.ip}:${info.port}/spectator/index.html", color = Ink, fontFamily = FontFamily.Monospace)

        HorizontalDivider(color = Line)
        Text("REGIA", color = InkSoft, fontSize = 12.sp)
        val session = HostController.session
        Button(onClick = { scope.launch { session?.next() } }, modifier = Modifier.fillMaxWidth()) { Text("▶ Avanti") }
        Button(onClick = { scope.launch { session?.lock() } }, modifier = Modifier.fillMaxWidth()) { Text("🔒 Chiudi") }
        Button(onClick = { scope.launch { session?.reveal() } }, modifier = Modifier.fillMaxWidth()) { Text("🎉 Svela") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { HostService.stop(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Ferma la stanza") }
    }
}
