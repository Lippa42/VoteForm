package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.FormatConfig
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

    /** Invocato una sola volta al termine della partita, con il risultato finale. */
    var onFinish: ((MatchResult) -> Unit)?

    /**
     * Risultato "così com'è ora", per salvare lo storico anche se l'host ferma la
     * stanza senza arrivare in fondo (caso comune: si chiude dopo la classifica).
     * Ritorna `null` se non c'è ancora nulla da salvare (partita non iniziata).
     */
    fun snapshotResult(): MatchResult? = null

    suspend fun addConnection(conn: Connection)
    fun removeConnection(id: String)
    suspend fun onIntent(conn: Connection, text: String)

    /** Controlli di regia (guidano la macchina a stati). */
    suspend fun next()
    suspend fun lock()
    suspend fun reveal()

    /** Comando musica di sottofondo (pause/resume/skip). Default: nessuna musica. */
    suspend fun music(command: String) {}
}

/** Sceglie l'implementazione di sessione in base alla modalità della stanza. */
fun sessionFor(room: RoomDefinition): GameSession {
    if (room.timeline.isNotEmpty()) return ShowSession(room)
    return when (room.mode) {
        is GameModeConfig.Quiz -> QuizSession(room)
        is GameModeConfig.Voting ->
            if (room.format is FormatConfig.Knockout) KnockoutSession(room) else VotingSession(room)
        is GameModeConfig.Questionnaire -> QuestionnaireSession(room)
    }
}
