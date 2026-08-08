package it.marcolipparini.sfide.persistence

import android.content.Context
import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Punto d'accesso alla persistenza locale: espone template e storico come modelli
 * dell'engine, nascondendo Room e la (de)serializzazione JSON. Nessun dato lascia
 * il dispositivo.
 */
class SfideStore(context: Context) {

    private val dao = SfideDatabase.get(context).dao()
    private val json = EngineJson

    val templates: Flow<List<RoomDefinition>> = dao.templates().map { rows ->
        rows.mapNotNull { row -> runCatching { json.decodeFromString(RoomDefinition.serializer(), row.json) }.getOrNull() }
    }

    val history: Flow<List<MatchResult>> = dao.history().map { rows ->
        rows.mapNotNull { row -> runCatching { json.decodeFromString(MatchResult.serializer(), row.json) }.getOrNull() }
    }

    suspend fun saveTemplate(room: RoomDefinition) {
        dao.upsertTemplate(
            RoomTemplateEntity(
                id = templateId(room),
                title = room.meta.title,
                mode = modeName(room),
                updatedAt = System.currentTimeMillis(),
                json = json.encodeToString(RoomDefinition.serializer(), room),
            ),
        )
    }

    suspend fun deleteTemplate(room: RoomDefinition) = dao.deleteTemplate(templateId(room))

    suspend fun saveResult(result: MatchResult) {
        dao.insertResult(
            MatchResultEntity(
                id = result.id,
                roomTitle = result.roomTitle,
                playedAtEpochMs = result.playedAtEpochMs,
                json = json.encodeToString(MatchResult.serializer(), result),
            ),
        )
    }

    private fun templateId(room: RoomDefinition): String =
        room.meta.id.ifEmpty { room.meta.title }

    private fun modeName(room: RoomDefinition): String = when (room.mode) {
        is GameModeConfig.Quiz -> "quiz"
        is GameModeConfig.Voting -> "voting"
        is GameModeConfig.Questionnaire -> "questionnaire"
    }
}
