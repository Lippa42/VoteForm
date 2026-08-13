package it.marcolipparini.sfide.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marcolipparini.sfide.engine.model.MediaAsset
import it.marcolipparini.sfide.engine.model.MediaKind
import java.awt.FileDialog
import java.io.File
import java.util.UUID

@Composable
fun TField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier)
}

@Composable
fun Label(text: String) {
    Text(text, color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
fun Chip(text: String, selected: Boolean = false, accent: Color = Accent, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent else SurfaceHi)
            .border(1.dp, if (selected) accent else Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = if (selected) Bg else Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

fun iconFor(type: SegmentType): String = when (type) {
    SegmentType.TITLE -> "🏷"
    SegmentType.MEDIA -> "🖼"
    SegmentType.STANDINGS -> "📊"
    SegmentType.QUIZ -> "❓"
    SegmentType.VOTING -> "⭐"
    SegmentType.TOURNAMENT -> "🏆"
    SegmentType.QUESTIONNAIRE -> "🗳"
    SegmentType.FINAL -> "🎉"
}

/** Apre il file dialog nativo (mouse) e copia il file scelto tra gli asset locali. */
fun pickAndCopyAsset(kind: MediaKind, assetsDir: File): MediaAsset? {
    val dialog = FileDialog(null as java.awt.Frame?, "Scegli un file ${kind.name.lowercase()}", FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    val src = File(dir, name)
    if (!src.isFile) return null
    val id = UUID.randomUUID().toString()
    val ext = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
    val dest = File(assetsDir, "$id$ext")
    runCatching { src.copyTo(dest, overwrite = true) }.getOrNull() ?: return null
    return MediaAsset(id = id, localPath = dest.absolutePath)
}
