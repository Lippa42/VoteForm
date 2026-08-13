package it.marcolipparini.sfide.desktop

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.RoomDefinition
import java.io.File

/**
 * Persistenza locale su desktop: template di stanza e storico partite salvati come
 * file JSON in `~/.sfide/`. Nessun database, nessun cloud — l'equivalente desktop
 * del Room DB usato sull'app Android. Nessun dato lascia il computer.
 */
class DesktopStore {
    private val root = File(System.getProperty("user.home"), ".sfide")
    private val templatesDir = File(root, "templates").apply { mkdirs() }
    private val historyDir = File(root, "history").apply { mkdirs() }

    /** Cartella dove copiare i media caricati dall'utente (immagini/audio/video). */
    val assetsDir: File = File(root, "assets").apply { mkdirs() }

    private val json = EngineJson

    fun templates(): List<RoomDefinition> =
        templatesDir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { runCatching { json.decodeFromString(RoomDefinition.serializer(), it.readText()) }.getOrNull() }
            ?: emptyList()

    fun history(): List<MatchResult> =
        historyDir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { runCatching { json.decodeFromString(MatchResult.serializer(), it.readText()) }.getOrNull() }
            ?: emptyList()

    fun saveTemplate(room: RoomDefinition) {
        val name = safe(room.meta.id.ifEmpty { room.meta.title })
        File(templatesDir, "$name.json").writeText(json.encodeToString(RoomDefinition.serializer(), room))
    }

    fun deleteTemplate(room: RoomDefinition) {
        val name = safe(room.meta.id.ifEmpty { room.meta.title })
        File(templatesDir, "$name.json").delete()
    }

    fun saveResult(result: MatchResult) {
        File(historyDir, "${safe(result.id)}.json").writeText(json.encodeToString(MatchResult.serializer(), result))
    }

    private fun safe(s: String): String = s.ifBlank { "senza-nome" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
