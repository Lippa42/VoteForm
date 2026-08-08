package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.Serializable

/**
 * Quali input dello spettatore sono abilitati. Tutto configurabile dall'admin:
 * la stessa infrastruttura serve un quiz interattivo, una votazione o un
 * questionario semplicemente accendendo i canali giusti.
 */
@Serializable
data class SpectatorInteraction(
    val canAnswer: Boolean = true,
    val canVote: Boolean = false,
    val canBuzz: Boolean = false,
    val canReact: Boolean = true,
    val requireName: Boolean = false,
    /** Vincola lo spettatore a una squadra/concorrente. */
    val bindToTeam: Boolean = false,
    val oneVotePerDevice: Boolean = true,
)
