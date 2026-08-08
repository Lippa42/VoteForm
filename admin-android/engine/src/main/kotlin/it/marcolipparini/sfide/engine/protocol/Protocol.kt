package it.marcolipparini.sfide.engine.protocol

import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.phase.Match
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.phase.Standing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Protocollo WebSocket. Due direzioni:
 *  - [ClientIntent]  Client → Host: intenzioni, sempre validate dall'autorità.
 *  - [ServerState]   Host → Client: lo stato è l'unica verità.
 *
 * I client web (viewer/spectator) condividono queste stesse forme in `web/shared`.
 */
@Serializable
sealed interface ClientIntent {

    @Serializable
    @SerialName("join")
    data class Join(val pin: String, val name: String? = null, val role: ClientRole) : ClientIntent

    @Serializable
    @SerialName("submit_answer")
    data class SubmitAnswer(val turnId: String, val optionIds: List<String>) : ClientIntent

    @Serializable
    @SerialName("cast_vote")
    data class CastVote(
        val turnId: String,
        val targetId: String,
        /** Voto per ciascun criterio: criterionId → valore. */
        val values: Map<String, Double>,
        val specialVoteId: String? = null,
    ) : ClientIntent

    @Serializable
    @SerialName("buzz")
    data class Buzz(val turnId: String) : ClientIntent

    @Serializable
    @SerialName("reaction")
    data class Reaction(val emoji: String) : ClientIntent
}

@Serializable
enum class ClientRole { VIEWER, SPECTATOR }

@Serializable
sealed interface ServerState {

    /** Inviato a ogni connessione: allinea in un colpo un client nuovo o riconnesso. */
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val phase: RoomPhase,
        val room: RoomDefinition,
        val standings: List<Standing>,
    ) : ServerState

    @Serializable
    @SerialName("turn")
    data class Turn(
        val turnId: String,
        val phase: RoomPhase,
        val timerSeconds: Int? = null,
        val locked: Boolean = false,
    ) : ServerState

    @Serializable
    @SerialName("reveal")
    data class Reveal(
        val turnId: String,
        val standings: List<Standing>,
    ) : ServerState

    @Serializable
    @SerialName("bracket")
    data class Bracket(
        val matches: List<Match>,
        val nextMatchId: String? = null,
    ) : ServerState
}
