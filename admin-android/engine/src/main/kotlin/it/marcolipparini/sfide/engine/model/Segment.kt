package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una fase della **timeline** di una stanza. La stanza non è più legata a una sola
 * modalità: è una sequenza componibile di segmenti (titolo, media, classifica,
 * quiz, votazione, questionario, finale…), riordinabili dall'amministratore.
 *
 * Ogni segmento porta il proprio contenuto, così la timeline è autosufficiente.
 */
@Serializable
sealed interface Segment {
    val id: String
    val title: String

    /** Schermata di titolo (apertura, stacco, intertitolo). */
    @Serializable
    @SerialName("title")
    data class Title(
        override val id: String,
        override val title: String,
        val subtitle: String = "",
    ) : Segment

    /** Schermata media: immagine, musica o video (dagli asset locali). */
    @Serializable
    @SerialName("media")
    data class Media(
        override val id: String,
        override val title: String,
        val kind: MediaKind = MediaKind.IMAGE,
        val assetId: String? = null,
        val musicId: String? = null,
        val caption: String = "",
    ) : Segment

    /** Mostra la classifica corrente (scoreboard condiviso). */
    @Serializable
    @SerialName("standings")
    data class Standings(
        override val id: String,
        override val title: String = "Classifica",
    ) : Segment

    /** Fase quiz (una o più domande) rivolta al pubblico. */
    @Serializable
    @SerialName("quiz")
    data class Quiz(
        override val id: String,
        override val title: String,
        val questions: List<Question>,
        val answerTimeSeconds: Int = 30,
    ) : Segment

    /** Fase di votazione dei concorrenti su una o più prove. */
    @Serializable
    @SerialName("voting")
    data class Voting(
        override val id: String,
        override val title: String,
        val prompts: List<VotingPrompt>,
        val voteScale: VoteScale = VoteScale(min = 1.0, max = 10.0, step = 0.5),
        val answerTimeSeconds: Int = 30,
    ) : Segment

    /** Fase questionario/sondaggio (nessuna risposta giusta). */
    @Serializable
    @SerialName("questionnaire")
    data class Questionnaire(
        override val id: String,
        override val title: String,
        val questions: List<Question>,
        val answerTimeSeconds: Int = 30,
    ) : Segment

    /** Fase torneo a eliminazione diretta tra i concorrenti (con tabellone). */
    @Serializable
    @SerialName("tournament")
    data class Tournament(
        override val id: String,
        override val title: String,
        val prompt: VotingPrompt,
        val voteScale: VoteScale = VoteScale(min = 1.0, max = 10.0, step = 0.5),
        val answerTimeSeconds: Int = 30,
        /** Bonus assegnato al campione nello scoreboard condiviso. */
        val winnerBonus: Double = 10.0,
    ) : Segment

    /** Schermata finale: classifica definitiva e vincitore. */
    @Serializable
    @SerialName("final")
    data class Final(
        override val id: String,
        override val title: String = "Finale",
    ) : Segment
}

@Serializable
enum class MediaKind { IMAGE, MUSIC, VIDEO }
