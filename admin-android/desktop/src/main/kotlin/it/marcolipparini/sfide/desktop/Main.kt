package it.marcolipparini.sfide.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * App Admin per desktop (Compose for Desktop). Avvia il server embedded e offre
 * builder + regia con mouse e tastiera. Stesse funzioni dell'app Android host.
 */
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1100.dp, 760.dp))
    Window(
        onCloseRequest = {
            DesktopHost.stop()
            exitApplication()
        },
        state = windowState,
        title = "Sfide · Admin",
    ) {
        App()
    }
}
