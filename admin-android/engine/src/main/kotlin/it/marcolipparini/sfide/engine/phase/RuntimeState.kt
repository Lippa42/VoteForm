package it.marcolipparini.sfide.engine.phase

import kotlinx.serialization.Serializable

/** Un accoppiamento prodotto dal formato durante il gioco. */
@Serializable
data class Match(
    val id: String,
    val competitorIds: List<String>,
    val round: Int,
)

/** Riga di classifica calcolata dal motore. */
@Serializable
data class Standing(
    val competitorId: String,
    val points: Double,
    val rank: Int,
)
