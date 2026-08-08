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
                VotingPrompt(
                    id = "p1",
                    title = "Piatto d'apertura",
                    criteria = listOf(
                        VoteCriterion(id = "gusto", label = "Gusto", weight = 2.0),
                        VoteCriterion(id = "presentazione", label = "Presentazione", weight = 1.0),
                        VoteCriterion(id = "originalita", label = "Originalità", weight = 1.0),
                    ),
                ),
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
            electorate = Electorate(
                groups = listOf(
                    VoterGroup(id = "giuria", label = "Giuria", weight = 0.6),
                    VoterGroup(id = "pubblico", label = "Pubblico", weight = 0.4),
                ),
                assignment = VoterAssignment.ADMIN_ASSIGNED,
            ),
            eligibility = EligibilityRules(
                selfVote = SelfVoteRule(allowed = false),
                overrides = listOf(
                    // Esempio: il Team Basilico non può votare affatto il Team Peperoncino.
                    VoteWeightRule(fromCompetitorId = "t1", toCompetitorId = "t2", weight = 0.0),
                ),
            ),
            specialVotes = listOf(SpecialVote(id = "chef", label = "Voto dello Chef", multiplier = 2.0, oneShot = true)),
            reveal = RevealStyle.PROGRESSIVE,
        ),
        interaction = SpectatorInteraction(canAnswer = false, canVote = true, requireName = true),
    )
}
