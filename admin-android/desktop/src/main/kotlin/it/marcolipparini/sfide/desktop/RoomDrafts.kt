package it.marcolipparini.sfide.desktop

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
import it.marcolipparini.sfide.engine.model.MediaAsset
import it.marcolipparini.sfide.engine.model.MediaConfig
import it.marcolipparini.sfide.engine.model.MediaKind
import it.marcolipparini.sfide.engine.model.ParticipantsConfig
import it.marcolipparini.sfide.engine.model.Question
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.model.RoomMeta
import it.marcolipparini.sfide.engine.model.ScoringRules
import it.marcolipparini.sfide.engine.model.Segment
import it.marcolipparini.sfide.engine.model.SpectatorInteraction
import it.marcolipparini.sfide.engine.model.Theme
import it.marcolipparini.sfide.engine.model.VoteCriterion
import it.marcolipparini.sfide.engine.model.VotingPrompt
import java.util.UUID

/** Palette selezionabili nel Builder e applicate ai client. */
data class Palette(val name: String, val primary: String, val background: String)

val palettes = listOf(
    Palette("Corallo", "#FF5A63", "#0F1117"),
    Palette("Oceano", "#3A9FF5", "#0C1622"),
    Palette("Foresta", "#2FBE93", "#0D1512"),
    Palette("Ambra", "#F5B53A", "#17130B"),
    Palette("Viola", "#A06BFF", "#140F1C"),
)

// ---- Bozze osservabili da Compose -------------------------------------------

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

enum class SegmentType(val label: String) {
    TITLE("Titolo"),
    MEDIA("Media"),
    STANDINGS("Classifica"),
    QUIZ("Quiz"),
    VOTING("Votazione"),
    TOURNAMENT("Torneo"),
    QUESTIONNAIRE("Questionario"),
    FINAL("Finale"),
}

/** Bozza di una fase nella timeline (stato osservabile per Compose). */
class SegmentDraft(val type: SegmentType) {
    val id: String = UUID.randomUUID().toString()
    var title by mutableStateOf(type.label)
    var subtitle by mutableStateOf("")
    var mediaKind by mutableStateOf(MediaKind.IMAGE)
    var assetId by mutableStateOf<String?>(null)
    val questions = mutableStateListOf<QuestionDraft>().apply {
        if (type == SegmentType.QUIZ || type == SegmentType.QUESTIONNAIRE) add(QuestionDraft())
    }
    val prompts = mutableStateListOf<PromptDraft>().apply {
        if (type == SegmentType.VOTING || type == SegmentType.TOURNAMENT) add(PromptDraft())
    }
}

/** Assembla una RoomDefinition con timeline dai draft. */
fun buildTimelineRoom(
    title: String,
    pin: String,
    palette: Palette,
    competitors: List<CompetitorDraft>,
    segments: List<SegmentDraft>,
    assets: List<MediaAsset> = emptyList(),
): RoomDefinition {
    val comps = competitors.filter { it.name.isNotBlank() }
        .mapIndexed { i, c -> Competitor(id = "c$i", name = c.name.trim()) }

    val timeline: List<Segment> = segments.mapIndexed { i, s ->
        val sid = "seg$i"
        when (s.type) {
            SegmentType.TITLE -> Segment.Title(sid, s.title.ifBlank { "Titolo" }, s.subtitle)
            SegmentType.MEDIA -> Segment.Media(sid, s.title.ifBlank { "Media" }, s.mediaKind, assetId = s.assetId, caption = s.subtitle)
            SegmentType.STANDINGS -> Segment.Standings(sid, s.title.ifBlank { "Classifica" })
            SegmentType.FINAL -> Segment.Final(sid, s.title.ifBlank { "Finale" })
            SegmentType.QUIZ -> Segment.Quiz(sid, s.title.ifBlank { "Quiz" }, questionsOf(sid, s.questions))
            SegmentType.QUESTIONNAIRE -> Segment.Questionnaire(sid, s.title.ifBlank { "Questionario" }, questionsOf(sid, s.questions))
            SegmentType.VOTING -> Segment.Voting(
                sid,
                s.title.ifBlank { "Votazione" },
                prompts = s.prompts.filter { it.title.isNotBlank() }.mapIndexed { j, p ->
                    VotingPrompt(
                        id = "${sid}p$j",
                        title = p.title.trim(),
                        criteria = p.criteria.filter { it.label.isNotBlank() }.mapIndexed { k, c ->
                            VoteCriterion("${sid}p${j}c$k", c.label.trim(), c.weight.toDoubleOrNull() ?: 1.0)
                        },
                    )
                },
            )
            SegmentType.TOURNAMENT -> Segment.Tournament(
                id = sid,
                title = s.title.ifBlank { "Torneo" },
                prompt = tournamentPrompt(sid, s.prompts.firstOrNull()),
            )
        }
    }

    return RoomDefinition(
        meta = RoomMeta(title = title.ifBlank { "Serata" }, pin = pin.ifBlank { "0000" }, id = UUID.randomUUID().toString()),
        mode = GameModeConfig.Quiz(questions = emptyList()),
        participants = ParticipantsConfig(CompetitorKind.TEAMS, comps),
        format = FormatConfig.AllVsAll(),
        scoring = ScoringRules(aggregation = Aggregation.WEIGHTED_MEAN),
        theme = Theme(paletteName = palette.name, primaryColor = palette.primary, backgroundColor = palette.background),
        media = MediaConfig(assets = assets),
        interaction = SpectatorInteraction(canAnswer = true, canVote = true, requireName = true),
        timeline = timeline,
    )
}

private fun tournamentPrompt(sid: String, draft: PromptDraft?): VotingPrompt {
    if (draft == null || draft.title.isBlank()) return VotingPrompt("${sid}p0", "Sfida")
    return VotingPrompt(
        id = "${sid}p0",
        title = draft.title.trim(),
        criteria = draft.criteria.filter { it.label.isNotBlank() }.mapIndexed { k, c ->
            VoteCriterion("${sid}c$k", c.label.trim(), c.weight.toDoubleOrNull() ?: 1.0)
        },
    )
}

private fun questionsOf(sid: String, drafts: List<QuestionDraft>): List<Question> =
    drafts.filter { it.text.isNotBlank() }.mapIndexed { j, q ->
        Question(
            id = "${sid}q$j",
            text = q.text.trim(),
            options = q.options.filter { it.text.isNotBlank() }.mapIndexed { k, o ->
                AnswerOption("${sid}q${j}o$k", o.text.trim(), correct = o.correct)
            },
            points = q.points.toIntOrNull() ?: 100,
        )
    }
