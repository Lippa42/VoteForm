package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.Serializable

/**
 * Regole di voto/punteggio **componibili**. Ogni blocco è indipendente e
 * combinabile: è ciò che rende il motore riusabile tra tutte le modalità senza
 * riscritture. Vive nel modulo `engine` (Kotlin puro) con test dedicati, perché
 * è il cuore della correttezza.
 */
@Serializable
data class ScoringRules(
    val aggregation: Aggregation = Aggregation.MEAN,
    /** Scarta il voto più alto e più basso prima di aggregare (stile giuria di gara). */
    val trimExtremes: Boolean = false,
    /** Peso relativo di giuria e pubblico (verranno normalizzati). */
    val juryPublicWeight: JuryPublicWeight = JuryPublicWeight(),
    /** Voti speciali con moltiplicatore (es. giudice d'onore ×2, jolly una tantum). */
    val specialVotes: List<SpecialVote> = emptyList(),
    /** Un concorrente non può votare sé stesso. */
    val forbidSelfVote: Boolean = true,
    /** Peso del voto verso l'avversario diretto (0..1, 1 = pieno). */
    val directRivalWeight: Double = 1.0,
    /** Riscala i voti di ogni giudice per compensare severità/generosità. */
    val normalizePerJudge: Boolean = false,
    /** Media pesata sul numero di votanti/sfidanti. */
    val weightByVoterCount: Boolean = false,
    val reveal: RevealStyle = RevealStyle.PROGRESSIVE,
    /** Criteri di spareggio, applicati in ordine finché non si rompe la parità. */
    val tieBreak: List<TieBreak> = listOf(TieBreak.HEAD_TO_HEAD, TieBreak.RANDOM_DRAW),
)

@Serializable
enum class Aggregation { MEAN, MEDIAN, WEIGHTED_MEAN }

@Serializable
data class JuryPublicWeight(
    val jury: Double = 1.0,
    val public: Double = 0.0,
)

@Serializable
data class SpecialVote(
    val id: String,
    val label: String,
    val multiplier: Double,
    /** Se true è utilizzabile una sola volta nell'intera partita. */
    val oneShot: Boolean = false,
)

@Serializable
enum class RevealStyle { INSTANT, PROGRESSIVE }

@Serializable
enum class TieBreak { HEAD_TO_HEAD, FASTEST_ANSWER, JURY_DECISION, RANDOM_DRAW }
