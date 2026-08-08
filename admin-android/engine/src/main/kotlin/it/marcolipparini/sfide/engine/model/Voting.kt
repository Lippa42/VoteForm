package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.Serializable

/**
 * Chi vota e con quale peso. I gruppi sono completamente personalizzabili
 * dall'admin: "giuria/pubblico" sono solo un default, se ne possono definire
 * quanti se ne vuole (es. "giuria tecnica", "giuria popolare", "chef ospite").
 */
@Serializable
data class Electorate(
    val groups: List<VoterGroup> = listOf(
        VoterGroup(id = "jury", label = "Giuria", weight = 1.0),
        VoterGroup(id = "public", label = "Pubblico", weight = 0.0),
    ),
    /** Come uno spettatore finisce in un gruppo. */
    val assignment: VoterAssignment = VoterAssignment.ADMIN_ASSIGNED,
)

@Serializable
data class VoterGroup(
    val id: String,
    val label: String,
    /** Peso relativo del gruppo nel risultato finale (verrà normalizzato). */
    val weight: Double = 1.0,
    /** True se i votanti di questo gruppo sono i concorrenti stessi (si votano tra loro). */
    val isContestants: Boolean = false,
)

@Serializable
enum class VoterAssignment {
    /** L'admin assegna manualmente ogni spettatore a un gruppo. */
    ADMIN_ASSIGNED,

    /** Lo spettatore sceglie il proprio gruppo all'ingresso. */
    SELF_SELECT,

    /** Tutti gli spettatori confluiscono nel gruppo "pubblico". */
    ALL_PUBLIC,
}

/**
 * Un criterio di voto all'interno di una prova. Permette il voto su più assi
 * (es. Gusto, Presentazione, Originalità) con pesi diversi.
 */
@Serializable
data class VoteCriterion(
    val id: String,
    val label: String,
    val weight: Double = 1.0,
    /** Scala specifica del criterio; se null si usa quella di default della modalità. */
    val scale: VoteScale? = null,
)

/**
 * Regole di eleggibilità: chi può votare chi, e con quale peso. Consente di
 * esprimere qualsiasi vincolo, dal semplice "no auto-voto" fino a
 * "il gruppo A non vota affatto il gruppo B".
 */
@Serializable
data class EligibilityRules(
    val selfVote: SelfVoteRule = SelfVoteRule(),
    /**
     * Override di peso votante→bersaglio, valutati in ordine: vince il primo che
     * combacia. weight = 0.0 significa "voto vietato"; 1.0 pieno; valori intermedi
     * riducono il peso. I campi null fungono da jolly (qualsiasi gruppo/concorrente).
     */
    val overrides: List<VoteWeightRule> = emptyList(),
)

@Serializable
data class SelfVoteRule(
    val allowed: Boolean = false,
    /** Peso del voto verso sé stessi, se consentito (0..1). */
    val weight: Double = 0.0,
)

@Serializable
data class VoteWeightRule(
    val fromGroupId: String? = null,
    val fromCompetitorId: String? = null,
    val toGroupId: String? = null,
    val toCompetitorId: String? = null,
    /** 0.0 = non può votare il bersaglio; 1.0 = peso pieno; intermedio = ridotto. */
    val weight: Double,
)
