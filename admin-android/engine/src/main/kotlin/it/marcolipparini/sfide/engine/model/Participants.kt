package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompetitorKind { TEAMS, INDIVIDUALS }

@Serializable
data class ParticipantsConfig(
    val kind: CompetitorKind,
    val competitors: List<Competitor>,
)

/** Un concorrente: una squadra o un singolo, secondo [ParticipantsConfig.kind]. */
@Serializable
data class Competitor(
    val id: String,
    val name: String,
    /** Colore identificativo in formato hex (es. "#E23744"). */
    val color: String? = null,
    val avatarAssetId: String? = null,
    /** Nomi dei componenti, valorizzato per le squadre. */
    val members: List<String> = emptyList(),
)
