package it.marcolipparini.sfide.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Pannello di **Regia**: PIN, QR di join, link per Visualizzatore/Spettatore, e i
 * controlli della partita. Ottimizzato per mouse e tastiera: Spazio o → per
 * avanzare, L chiude, R svela, P mette in pausa/riprende la musica, N salta,
 * Esc ferma la stanza.
 */
@Composable
fun HostScreen(onStop: () -> Unit) {
    val info by DesktopHost.state.collectAsState()
    val scope = rememberCoroutineScope()
    var musicPaused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun next() = scope.launch { DesktopHost.session?.next() }
    fun lock() = scope.launch { DesktopHost.session?.lock() }
    fun reveal() = scope.launch { DesktopHost.session?.reveal() }
    fun music(cmd: String) = scope.launch { DesktopHost.session?.music(cmd) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Spacebar, Key.DirectionRight, Key.PageDown, Key.Enter -> { next(); true }
                    Key.L -> { lock(); true }
                    Key.R -> { reveal(); true }
                    Key.P -> { musicPaused = !musicPaused; music(if (musicPaused) "pause" else "resume"); true }
                    Key.N -> { music("skip"); true }
                    Key.Escape -> { onStop(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 940.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ---- Colonna sinistra: identità della stanza + QR ----------------
            Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎯 Sfide · Host", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (info.title.isNotBlank()) Text(info.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (info.mode.isNotEmpty()) Text("Modalità: ${info.mode}", color = InkSoft, fontSize = 12.sp)

                Text("PIN", color = InkSoft, fontSize = 12.sp)
                Text(info.pin, color = Accent, fontSize = 46.sp, fontWeight = FontWeight.Bold)

                val joinUrl = "http://${info.ip}:${info.port}/spectator/index.html"
                val qr = remember(info.ip, info.port) { runCatching { qrBitmap(joinUrl) }.getOrNull() }
                qr?.let {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White).padding(8.dp)) {
                        Image(bitmap = it, contentDescription = "QR per entrare", modifier = Modifier.size(220.dp))
                    }
                }
            }

            // ---- Colonna destra: link + regia --------------------------------
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinkRow("Visualizzatore (TV)", "http://${info.ip}:${info.port}/viewer/index.html")
                LinkRow("Spettatore (join)", "http://${info.ip}:${info.port}/spectator/index.html")
                LinkRow("Regia web (backup)", "http://${info.ip}:${info.port}/admin")

                HorizontalDivider(color = Line)

                Text("REGIA", color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { next() }, modifier = Modifier.weight(1f)) { Text("▶ Avanti  (Spazio)") }
                    Button(onClick = { lock() }, modifier = Modifier.weight(1f)) { Text("🔒 Chiudi  (L)") }
                    Button(onClick = { reveal() }, modifier = Modifier.weight(1f)) { Text("🎉 Svela  (R)") }
                }

                Text("MUSICA", color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { musicPaused = true; music("pause") }, modifier = Modifier.weight(1f)) { Text("⏸ Pausa") }
                    OutlinedButton(onClick = { musicPaused = false; music("resume") }, modifier = Modifier.weight(1f)) { Text("▶ Riprendi") }
                    OutlinedButton(onClick = { music("skip") }, modifier = Modifier.weight(1f)) { Text("⏭ Salta  (N)") }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHi, contentColor = Ink),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("■ Ferma la stanza  (Esc)") }

                Text(
                    "Scorciatoie: Spazio/→ avanti · L chiudi · R svela · P pausa/riprendi musica · N salta · Esc ferma.",
                    color = InkSoft, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = InkSoft, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SurfaceHi).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            ) { Text(url, color = Ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            OutlinedButton(onClick = { copyToClipboard(url) }) { Text("Copia") }
        }
    }
}

private fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
