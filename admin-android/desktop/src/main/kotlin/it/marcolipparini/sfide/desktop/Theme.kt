package it.marcolipparini.sfide.desktop

import androidx.compose.ui.graphics.Color

// Stessa palette dell'app Android host, per continuità visiva.
val Accent = Color(0xFFFF5A63)
val Bg = Color(0xFF0F1117)
val Surface = Color(0xFF16181F)
val SurfaceHi = Color(0xFF1E212B)
val Ink = Color(0xFFECEEF5)
val InkSoft = Color(0xFFA9AEBF)
val Line = Color(0xFF262A36)

/** Converte un colore esadecimale (#RRGGBB) della palette in Color di Compose. */
fun hexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    return Color(("FF$clean").toLong(16))
}
