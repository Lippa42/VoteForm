package it.marcolipparini.sfide.engine.history

import it.marcolipparini.sfide.engine.phase.Standing
import kotlinx.serialization.Serializable

/**
 * Risultato di una partita, salvato in locale (Room DB) sul dispositivo host
 * per lo storico. Nessun dato lascia il dispositivo.
 */
@Serializable
data class MatchResult(
    val id: String,
    val roomTitle: String,
    val playedAtEpochMs: Long,
    val finalStandings: List<Standing>,
)
