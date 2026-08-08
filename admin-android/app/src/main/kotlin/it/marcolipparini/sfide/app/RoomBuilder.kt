package it.marcolipparini.sfide.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.marcolipparini.sfide.engine.model.Aggregation
import it.marcolipparini.sfide.engine.model.AnswerOption
import it.marcolipparini.sfide.engine.model.Competitor
import it.marcolipparini.sfide.engine.model.CompetitorKind
import it.marcolipparini.sfide.engine.model.FormatConfig
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.ParticipantsConfig
import it.marcolipparini.sfide.engine.model.Question
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.model.RoomMeta
import it.marcolipparini.sfide.engine.model.ScoringRules
import it.marcolipparini.sfide.engine.model.SpectatorInteraction
import it.marcolipparini.sfide.engine.model.Theme
import it.marcolipparini.sfide.engine.model.VoteCriterion
import it.marcolipparini.sfide.engine.model.VotingPrompt
import java.util.UUID

enum class BuildMode { QUIZ, VOTING }

/** Palette selezionabili nel Builder e applicate ai client. */
data class Palette(val name: String, val primary: String, val background: String)

val palettes = listOf(
    Palette("Corallo", "#FF5A63", "#0F1117"),
    Palette("Oceano", "#3A9FF5", "#0C1622"),
    Palette("Foresta", "#2FBE93", "#0D1512"),
    Palette("Ambra", "#F5B53A", "#17130B"),
    Palette("Viola", "#A06BFF", "#140F1C"),
)

/** Bozze modificabili dal form (stato osservabile da Compose). */
class CompetitorDraft(name: String = "") {
    var name by mutableStateOf(name)
}

class OptionDraft(text: String = "", correct: Boolean = false) {
    var text by mutableStateOf(text)
    var correct by mutableStateOf(correct)
}

class QuestionDraft {
    var text by mutableStateOf("")
    var points by mutableStateOf("100")
    val options = mutableStateListOf(OptionDraft(), OptionDraft())
}

class CriterionDraft(label: String = "", weight: String = "1") {
    var label by mutableStateOf(label)
    var weight by mutableStateOf(weight)
}

class PromptDraft {
    var title by mutableStateOf("")
    val criteria = mutableStateListOf(CriterionDraft("Voto complessivo", "1"))
}

/** Assembla una RoomDefinition dai draft del form. */
fun buildRoom(
    mode: BuildMode,
    title: String,
    pin: String,
    competitors: List<CompetitorDraft>,
    questions: List<QuestionDraft>,
    prompts: List<PromptDraft>,
    palette: Palette = palettes.first(),
): RoomDefinition {
    val comps = competitors
        .filter { it.name.isNotBlank() }
        .mapIndexed { i, c -> Competitor(id = "c$i", name = c.name.trim()) }
    val participants = ParticipantsConfig(kind = CompetitorKind.TEAMS, competitors = comps)
    val meta = RoomMeta(
        title = title.ifBlank { "Stanza senza nome" },
        pin = pin.ifBlank { "0000" },
        id = UUID.randomUUID().toString(),
    )
    val theme = Theme(
        paletteName = palette.name,
        primaryColor = palette.primary,
        backgroundColor = palette.background,
    )

    return when (mode) {
        BuildMode.QUIZ -> RoomDefinition(
            meta = meta,
            mode = GameModeConfig.Quiz(
                questions = questions.filter { it.text.isNotBlank() }.mapIndexed { i, q ->
                    Question(
                        id = "q$i",
                        text = q.text.trim(),
                        options = q.options.filter { it.text.isNotBlank() }.mapIndexed { j, o ->
                            AnswerOption(id = "q${i}o$j", text = o.text.trim(), correct = o.correct)
                        },
                        points = q.points.toIntOrNull() ?: 100,
                    )
                },
            ),
            participants = participants,
            theme = theme,
            format = FormatConfig.AllVsAll(),
            scoring = ScoringRules(),
            interaction = SpectatorInteraction(canAnswer = true, canVote = false),
        )

        BuildMode.VOTING -> RoomDefinition(
            meta = meta,
            mode = GameModeConfig.Voting(
                prompts = prompts.filter { it.title.isNotBlank() }.mapIndexed { i, p ->
                    VotingPrompt(
                        id = "p$i",
                        title = p.title.trim(),
                        criteria = p.criteria.filter { it.label.isNotBlank() }.mapIndexed { j, c ->
                            VoteCriterion(id = "p${i}c$j", label = c.label.trim(), weight = c.weight.toDoubleOrNull() ?: 1.0)
                        },
                    )
                },
            ),
            participants = participants,
            theme = theme,
            format = FormatConfig.AllVsAll(),
            scoring = ScoringRules(aggregation = Aggregation.WEIGHTED_MEAN),
            interaction = SpectatorInteraction(canAnswer = false, canVote = true, requireName = true),
        )
    }
}
