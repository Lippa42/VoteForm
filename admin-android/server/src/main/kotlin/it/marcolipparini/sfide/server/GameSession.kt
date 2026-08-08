package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.phase.RoomPhase

/**
 * Contratto comune a tutte le modalità (Quiz, Voti, …). Il server (Ktor) parla
 * solo con questa interfaccia: aggiungere una modalità significa fornire una nuova
 * implementazione, senza toccare le rotte.
 */
interface GameSession {
    val room: RoomDefinition
    val phase: RoomPhase

    suspend fun addConnection(conn: Connection)
    fun removeConnection(id: String)
    suspend fun onIntent(conn: Connection, text: String)

    /** Controlli di regia (guidano la macchina a stati). */
    suspend fun next()
    suspend fun lock()
    suspend fun reveal()
}

/** Sceglie l'implementazione di sessione in base alla modalità della stanza. */
fun sessionFor(room: RoomDefinition): GameSession = when (room.mode) {
    is GameModeConfig.Quiz -> QuizSession(room)
    is GameModeConfig.Voting -> VotingSession(room)
    is GameModeConfig.Questionnaire -> QuizSession(room) // TODO: QuestionnaireSession
}
