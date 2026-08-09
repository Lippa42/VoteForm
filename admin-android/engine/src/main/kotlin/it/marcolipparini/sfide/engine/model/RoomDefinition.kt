package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.Serializable

/**
 * Definizione completa e serializzabile di una stanza di sfida.
 *
 * È l'unità che l'amministratore compone, che il motore esegue e che viene
 * salvata/esportata come *template*. L'idea chiave del progetto è che una
 * sfida sia **dati** (questa struttura), non codice separato per ogni tipo.
 */
@Serializable
data class RoomDefinition(
    val meta: RoomMeta,
    val mode: GameModeConfig,
    val participants: ParticipantsConfig,
    val format: FormatConfig,
    val scoring: ScoringRules,
    val theme: Theme = Theme(),
    val media: MediaConfig = MediaConfig(),
    val interaction: SpectatorInteraction = SpectatorInteraction(),
    /**
     * Timeline di fasi componibili. Se non vuota, la stanza è guidata da questa
     * sequenza ([Segment]) invece che dalla singola [mode] (che resta per
     * retrocompatibilità con le stanze a modalità unica).
     */
    val timeline: List<Segment> = emptyList(),
)

@Serializable
data class RoomMeta(
    val title: String,
    val description: String = "",
    /** PIN che gli spettatori inseriscono per entrare nella stanza. */
    val pin: String,
    val maxParticipants: Int = 15,
    /** Identificatore stabile della stanza/template (vuoto per le stanze usa-e-getta). */
    val id: String = "",
)
