package it.marcolipparini.sfide.engine.samples

import it.marcolipparini.sfide.engine.model.*

/** Esempi di definizioni di stanza, utili per test, anteprime e onboarding. */
object SampleRooms {

    /** Un quiz a squadre in torneo a eliminazione. */
    fun quizTournament(): RoomDefinition = RoomDefinition(
        meta = RoomMeta(title = "Quiz Night", pin = "4291", maxParticipants = 15),
        mode = GameModeConfig.Quiz(
            questions = listOf(
                Question(
                    id = "q1",
                    text = "Qual è la capitale dell'Australia?",
                    options = listOf(
                        AnswerOption("a", "Sydney"),
                        AnswerOption("b", "Canberra", correct = true),
                        AnswerOption("c", "Melbourne"),
                    ),
                    selection = SelectionType.SINGLE,
                    points = 100,
                ),
            ),
            answerTimeSeconds = 25,
            speedBonus = true,
        ),
        participants = ParticipantsConfig(
            kind = CompetitorKind.TEAMS,
            competitors = listOf(
                Competitor(id = "t1", name = "Rossi", color = "#E23744"),
                Competitor(id = "t2", name = "Blu", color = "#2F6BE2"),
            ),
        ),
        format = FormatConfig.Knockout(seeded = false),
        scoring = ScoringRules(),
        interaction = SpectatorInteraction(canAnswer = true, canVote = false),
    )

    /** Una sfida culinaria a voti con giuria + pubblico e voto speciale. */
    fun cookingVoting(): RoomDefinition = RoomDefinition(
        meta = RoomMeta(title = "Sfida ai Fornelli", pin = "7788", maxParticipants = 12),
        mode = GameModeConfig.Voting(
            prompts = listOf(
                VotingPrompt(id = "p1", title = "Piatto d'apertura"),
            ),
            voteScale = VoteScale(min = 1.0, max = 10.0, step = 0.5),
        ),
        participants = ParticipantsConfig(
            kind = CompetitorKind.TEAMS,
            competitors = listOf(
                Competitor(id = "t1", name = "Team Basilico"),
                Competitor(id = "t2", name = "Team Peperoncino"),
                Competitor(id = "t3", name = "Team Zenzero"),
            ),
        ),
        format = FormatConfig.AllVsAll(),
        scoring = ScoringRules(
            aggregation = Aggregation.WEIGHTED_MEAN,
            trimExtremes = true,
            juryPublicWeight = JuryPublicWeight(jury = 0.6, public = 0.4),
            specialVotes = listOf(SpecialVote(id = "chef", label = "Voto dello Chef", multiplier = 2.0, oneShot = true)),
            forbidSelfVote = true,
            reveal = RevealStyle.PROGRESSIVE,
        ),
        interaction = SpectatorInteraction(canAnswer = false, canVote = true, requireName = true),
    )
}
