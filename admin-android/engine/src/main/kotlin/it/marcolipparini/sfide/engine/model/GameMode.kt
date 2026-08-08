package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configurazione della modalità di gioco. Ogni variante è un "plugin": definisce
 * cosa accade nelle fasi Setup / Input / Calcolo di ciascun turno. Aggiungere una
 * modalità significa aggiungere una variante qui e la relativa logica nel motore,
 * senza toccare il resto del sistema.
 */
@Serializable
sealed interface GameModeConfig {

    /** Quiz: risposta singola o multipla, immagine opzionale, punteggi a tempo. */
    @Serializable
    @SerialName("quiz")
    data class Quiz(
        val questions: List<Question>,
        val answerTimeSeconds: Int = 30,
        val speedBonus: Boolean = true,
    ) : GameModeConfig

    /** Sfida a voti (culinaria / talent): i concorrenti si esibiscono e vengono votati. */
    @Serializable
    @SerialName("voting")
    data class Voting(
        val prompts: List<VotingPrompt>,
        val voteScale: VoteScale = VoteScale(min = 1.0, max = 10.0, step = 0.5),
    ) : GameModeConfig

    /** Questionario: nessuna risposta "giusta", si aggregano gli esiti del pubblico. */
    @Serializable
    @SerialName("questionnaire")
    data class Questionnaire(
        val questions: List<Question>,
        val showAggregateLive: Boolean = true,
    ) : GameModeConfig
}

@Serializable
data class Question(
    val id: String,
    val text: String,
    val imageAssetId: String? = null,
    val options: List<AnswerOption>,
    val selection: SelectionType = SelectionType.SINGLE,
    /** Punti per risposta corretta. Ignorato dalle modalità senza risposta esatta. */
    val points: Int = 100,
)

@Serializable
enum class SelectionType { SINGLE, MULTIPLE }

@Serializable
data class AnswerOption(
    val id: String,
    val text: String,
    val imageAssetId: String? = null,
    val correct: Boolean = false,
)

@Serializable
data class VotingPrompt(
    val id: String,
    val title: String,
    val description: String = "",
    val imageAssetId: String? = null,
    /** Criteri di voto pesati (es. Gusto ×2, Presentazione, Originalità). */
    val criteria: List<VoteCriterion> = listOf(
        VoteCriterion(id = "overall", label = "Voto complessivo"),
    ),
)

/** Scala di voto per le modalità a punteggio (es. da 1 a 10 con passo 0.5). */
@Serializable
data class VoteScale(
    val min: Double,
    val max: Double,
    val step: Double,
)
