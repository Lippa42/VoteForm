package it.marcolipparini.sfide.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Router dell'app Admin desktop. Tre schermate (Home / Builder / Storico) più il
 * pannello di **Regia** che prende il controllo quando l'host è in esecuzione.
 */
@Composable
fun App() {
    val store = remember { DesktopStore() }
    val info by DesktopHost.state.collectAsState()
    var screen by remember { mutableStateOf("home") }
    var refresh by remember { mutableStateOf(0) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Surface,
            onPrimary = Bg,
            onBackground = Ink,
            onSurface = Ink,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            when {
                info.running -> HostScreen(onStop = { DesktopHost.stop() })
                screen == "builder" -> BuilderScreen(
                    store = store,
                    onCancel = { screen = "home" },
                    onSaved = { refresh++; screen = "home" },
                    onStart = { room -> DesktopHost.start(store, room) },
                )
                screen == "history" -> HistoryScreen(store = store, refreshKey = refresh, onBack = { screen = "home" })
                else -> HomeScreen(
                    store = store,
                    refreshKey = refresh,
                    onNew = { screen = "builder" },
                    onStart = { room -> DesktopHost.start(store, room) },
                    onHistory = { refresh++; screen = "history" },
                    onRelayChange = { DesktopHost.relayUrl = it },
                )
            }
        }
    }
}
