package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Come i concorrenti si affrontano e come prosegue la sfida, in modo indipendente
 * dalla modalità di gioco. La modalità produce un *risultato di match*; il formato
 * lo consuma e propone il match successivo.
 */
@Serializable
sealed interface FormatConfig {

    val draw: DrawConfig

    /** Eliminazione diretta con tabellone. */
    @Serializable
    @SerialName("knockout")
    data class Knockout(
        override val draw: DrawConfig = DrawConfig(),
        val seeded: Boolean = false,
        val thirdPlaceMatch: Boolean = false,
    ) : FormatConfig

    /** Girone all'italiana (campionato) a punti. */
    @Serializable
    @SerialName("league")
    data class League(
        override val draw: DrawConfig = DrawConfig(),
        val doubleRound: Boolean = false,
        val pointsWin: Int = 3,
        val pointsDraw: Int = 1,
    ) : FormatConfig

    /** Tutti contro tutti in un'unica valutazione. */
    @Serializable
    @SerialName("all_vs_all")
    data class AllVsAll(
        override val draw: DrawConfig = DrawConfig(),
    ) : FormatConfig

    /** Coppie 2 contro 2, con eventuale rotazione delle coppie. */
    @Serializable
    @SerialName("two_vs_two")
    data class TwoVsTwo(
        override val draw: DrawConfig = DrawConfig(),
        val rotatePairs: Boolean = true,
    ) : FormatConfig
}

@Serializable
data class DrawConfig(
    val strategy: DrawStrategy = DrawStrategy.RANDOM,
    val avoidRematches: Boolean = true,
    /** Mostra un'animazione del sorteggio sulla TV. */
    val animateOnScreen: Boolean = true,
)

@Serializable
enum class DrawStrategy { RANDOM, SEEDED }
