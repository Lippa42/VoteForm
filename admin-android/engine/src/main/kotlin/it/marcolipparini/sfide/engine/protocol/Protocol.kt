package it.marcolipparini.sfide.engine.protocol

import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.model.SelectionType
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
        /** Vista pubblica della domanda/prova (senza le risposte corrette). */
        val prompt: PublicPrompt? = null,
        val index: Int? = null,
        val total: Int? = null,
        val timerSeconds: Int? = null,
        val locked: Boolean = false,
    ) : ServerState

    /** Avanzamento della raccolta input (per il "quanti hanno risposto" sulla TV). */
    @Serializable
    @SerialName("progress")
    data class Progress(
        val turnId: String,
        val answered: Int,
        val total: Int,
    ) : ServerState

    /** Elenco dei partecipanti connessi con il punteggio corrente. */
    @Serializable
    @SerialName("players")
    data class Players(
        val players: List<PlayerInfo>,
    ) : ServerState

    @Serializable
    @SerialName("reveal")
    data class Reveal(
        val turnId: String,
        val standings: List<Standing>,
        val correctOptionIds: List<String> = emptyList(),
    ) : ServerState

    @Serializable
    @SerialName("bracket")
    data class Bracket(
        val matches: List<Match>,
        val nextMatchId: String? = null,
    ) : ServerState
}

/** Domanda esposta ai client: nessuna informazione su quale opzione sia corretta. */
@Serializable
data class PublicPrompt(
    val title: String,
    val imageAssetId: String? = null,
    val options: List<PublicOption> = emptyList(),
    val selection: SelectionType = SelectionType.SINGLE,
)

@Serializable
data class PublicOption(
    val id: String,
    val text: String,
)

@Serializable
data class PlayerInfo(
    val id: String,
    val name: String,
    val score: Double = 0.0,
)
