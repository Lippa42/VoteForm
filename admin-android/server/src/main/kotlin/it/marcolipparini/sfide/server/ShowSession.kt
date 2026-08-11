package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.bracket.Bracket
import it.marcolipparini.sfide.engine.bracket.BracketEngine
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.Aggregation
import it.marcolipparini.sfide.engine.model.Competitor
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.model.Segment
import it.marcolipparini.sfide.engine.model.VoteCriterion
import it.marcolipparini.sfide.engine.model.VotingPrompt
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.phase.Standing
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.PlayerInfo
import it.marcolipparini.sfide.engine.protocol.PublicOption
import it.marcolipparini.sfide.engine.protocol.PublicPrompt
import it.marcolipparini.sfide.engine.protocol.ScoreEntry
import it.marcolipparini.sfide.engine.protocol.ServerState
import it.marcolipparini.sfide.engine.protocol.VoteCriterionView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Sessione guidata da una **timeline di fasi** ([Segment]). Scorre i segmenti in
 * ordine: le fasi di presentazione (Titolo/Media/Classifica/Finale) si mostrano e
 * con "Avanti" si passa oltre; le fasi interattive (Quiz/Votazione/Questionario)
 * girano al loro interno emettendo gli stessi messaggi già noti ai client.
 *
 * Scoreboard condiviso: le fasi di Votazione accumulano punti sui concorrenti;
 * Quiz/Questionario sono interludi col pubblico.
 */
class ShowSession(override val room: RoomDefinition) : GameSession {

    private val segments = room.timeline
    private val competitors = room.participants.competitors

    private val mutex = Mutex()
    private val connections = ConcurrentHashMap<String, Connection>()
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null

    private data class Player(val id: String, var name: String)

    private val players = LinkedHashMap<String, Player>()
    private val answers = HashMap<String, List<String>>()          // quiz/questionario
    private val votes = HashMap<String, Map<String, Double>>()      // votazione
    private val audienceScores = LinkedHashMap<String, Double>()    // punteggio pubblico (quiz)
    private val scoreboard = LinkedHashMap<String, Double>().apply { competitors.forEach { put(it.id, 0.0) } }

    override var onFinish: ((MatchResult) -> Unit)? = null

    @Volatile
    override var phase: RoomPhase = RoomPhase.LOBBY
        private set

    private var cursor = -1
    private var step = -1
    private var curTurnId = ""
    private var curCompetitorId: String? = null
    private var lastState: List<ServerState> = emptyList()

    // Stato della fase Torneo (attivo solo dentro un Segment.Tournament).
    private val competitorsById = competitors.associateBy { it.id }
    private var bracket: Bracket? = null
    private var tourMatchId: String? = null
    private val tourQueue = ArrayDeque<Competitor>()
    private var tourCurrent: Competitor? = null
    private val tourScores = HashMap<String, Double>()

    // "Pending" impostati sotto lock e consumati dopo il broadcast.
    private var pendingSchedSeconds = 0
    private var pendingSchedTurnId: String? = null
    private var pendingFinished: MatchResult? = null

    private val segment get() = segments.getOrNull(cursor)

    // ---- Connessioni --------------------------------------------------------

    override suspend fun addConnection(conn: Connection) {
        connections[conn.id] = conn
        val msgs = mutex.withLock {
            buildList {
                add(ServerState.Snapshot(phase = phase, room = room, standings = emptyList()))
                add(playersState())
                addAll(lastState)
            }
        }
        msgs.forEach { conn.send(encode(it)) }
    }

    override fun removeConnection(id: String) {
        connections.remove(id)
    }

    override suspend fun onIntent(conn: Connection, text: String) {
        val intent = runCatching { EngineJson.decodeFromString(ClientIntent.serializer(), text) }.getOrNull() ?: return
        when (intent) {
            is ClientIntent.Join -> handleJoin(conn, intent)
            is ClientIntent.SubmitAnswer -> handleAnswer(conn, intent)
            is ClientIntent.CastVote -> handleVote(conn, intent)
            else -> Unit
        }
    }

    private suspend fun handleJoin(conn: Connection, join: ClientIntent.Join) {
        var playersMsg: ServerState? = null
        val initial = mutex.withLock {
            conn.role = join.role
            if (join.role == ClientRole.SPECTATOR) {
                if (join.pin != room.meta.pin) return
                conn.name = join.name?.takeIf { it.isNotBlank() } ?: "Ospite"
                players.getOrPut(conn.id) { Player(conn.id, conn.name) }.name = conn.name
                audienceScores.putIfAbsent(conn.id, 0.0)
                playersMsg = playersState()
            }
            buildList<ServerState> {
                add(ServerState.Snapshot(phase = phase, room = room, standings = emptyList()))
                add(playersState())
                addAll(lastState)
            }
        }
        initial.forEach { conn.send(encode(it)) }
        playersMsg?.let { broadcast(it) }
    }

    private suspend fun handleAnswer(conn: Connection, ans: ClientIntent.SubmitAnswer) {
        val progress = mutex.withLock {
            val seg = segment
            if (seg !is Segment.Quiz && seg !is Segment.Questionnaire) return
            if (phase != RoomPhase.INPUT || ans.turnId != curTurnId) return
            if (!players.containsKey(conn.id)) return
            answers[conn.id] = ans.optionIds
            ServerState.Progress(curTurnId, answers.size, players.size)
        }
        broadcast(progress)
    }

    private suspend fun handleVote(conn: Connection, vote: ClientIntent.CastVote) {
        val progress = mutex.withLock {
            val seg = segment
            if (seg !is Segment.Voting && seg !is Segment.Tournament) return
            if (phase != RoomPhase.INPUT || vote.turnId != curTurnId) return
            if (vote.targetId != curCompetitorId || conn.id !in players) return
            votes[conn.id] = vote.values
            ServerState.Progress(curTurnId, votes.size, players.size)
        }
        broadcast(progress)
    }

    // ---- Regia --------------------------------------------------------------

    override suspend fun next() {
        timerJob?.cancel()
        pendingSchedSeconds = 0; pendingSchedTurnId = null; pendingFinished = null
        val msgs = mutex.withLock {
            val seg = segment
            when {
                seg is Segment.Tournament && tournamentActive() -> advanceTournament(seg)
                seg != null && isGenericInteractive(seg) && step + 1 < stepCount(seg) -> {
                    step++
                    beginStepMessages(seg)
                }
                else -> {
                    cursor++
                    step = -1
                    resetTournament()
                    enterSegment()
                }
            }.also { lastState = it }
        }
        msgs.forEach { broadcast(it) }
        pendingSchedTurnId?.let { if (pendingSchedSeconds > 0) scheduleAutoLock(pendingSchedSeconds, it) }
        pendingFinished?.let { onFinish?.invoke(it) }
    }

    /** Entra nel segmento corrente ([cursor]); imposta gli eventuali pending. */
    private fun enterSegment(): List<ServerState> {
        val seg = segment ?: run {
            phase = RoomPhase.FINISHED
            pendingFinished = buildResult()
            return listOf(finalScoreboard())
        }
        return when {
            seg is Segment.Tournament -> initTournament(seg)
            isGenericInteractive(seg) -> { step = 0; beginStepMessages(seg) }
            else -> presentationMessages(seg)
        }
    }

    private fun beginStepMessages(seg: Segment): List<ServerState> {
        val r = beginStep(seg)
        pendingSchedSeconds = r.second
        pendingSchedTurnId = r.third
        return r.first
    }

    override suspend fun lock() {
        timerJob?.cancel()
        val msg = mutex.withLock {
            val seg = segment
            if (!(isGenericInteractive(seg) || seg is Segment.Tournament) || phase != RoomPhase.INPUT) return
            phase = RoomPhase.LOCKED
            lockedTurn(seg!!)
        } ?: return
        broadcast(msg)
    }

    override suspend fun reveal() {
        timerJob?.cancel()
        val msgs = mutex.withLock {
            val seg = segment ?: return
            if (phase != RoomPhase.INPUT && phase != RoomPhase.LOCKED) return
            phase = RoomPhase.STANDINGS
            when (seg) {
                is Segment.Quiz -> revealQuiz(seg)
                is Segment.Questionnaire -> revealQuestionnaire(seg)
                is Segment.Voting -> revealVoting(seg)
                is Segment.Tournament -> revealTournament(seg)
                else -> return
            }.also { lastState = it }
        }
        msgs.forEach { broadcast(it) }
    }

    // ---- Passi interattivi --------------------------------------------------

    override suspend fun music(command: String) {
        broadcast(ServerState.Music(action = command))
    }

    private fun isGenericInteractive(seg: Segment?): Boolean =
        seg is Segment.Quiz || seg is Segment.Voting || seg is Segment.Questionnaire

    private fun stepCount(seg: Segment): Int = when (seg) {
        is Segment.Quiz -> seg.questions.size
        is Segment.Questionnaire -> seg.questions.size
        is Segment.Voting -> seg.prompts.size * competitors.size.coerceAtLeast(1)
        else -> 0
    }

    /** Prepara il passo corrente; ritorna (messaggi, secondi timer, turnId). */
    private fun beginStep(seg: Segment): Triple<List<ServerState>, Int, String?> {
        answers.clear(); votes.clear()
        phase = RoomPhase.INPUT
        return when (seg) {
            is Segment.Quiz -> {
                val q = seg.questions[step]
                curTurnId = "${seg.id}:${q.id}"; curCompetitorId = null
                Triple(
                    listOf(quizTurn(seg, q, false), ServerState.Progress(curTurnId, 0, players.size)),
                    seg.answerTimeSeconds, curTurnId,
                )
            }
            is Segment.Questionnaire -> {
                val q = seg.questions[step]
                curTurnId = "${seg.id}:${q.id}"; curCompetitorId = null
                Triple(
                    listOf(quizTurn(seg, q, false, seg.answerTimeSeconds), ServerState.Progress(curTurnId, 0, players.size)),
                    seg.answerTimeSeconds, curTurnId,
                )
            }
            is Segment.Voting -> {
                val nc = competitors.size.coerceAtLeast(1)
                val prompt = seg.prompts[step / nc]
                val comp = competitors[step % nc]
                curTurnId = "${seg.id}:${prompt.id}:${comp.id}"; curCompetitorId = comp.id
                Triple(
                    listOf(voteTurn(seg, prompt, comp, false), ServerState.Progress(curTurnId, 0, players.size)),
                    seg.answerTimeSeconds, curTurnId,
                )
            }
            else -> Triple(emptyList(), 0, null)
        }
    }

    private fun lockedTurn(seg: Segment): ServerState? = when (seg) {
        is Segment.Quiz -> seg.questions.getOrNull(step)?.let { quizTurn(seg, it, true) }
        is Segment.Questionnaire -> seg.questions.getOrNull(step)?.let { quizTurn(seg, it, true, seg.answerTimeSeconds) }
        is Segment.Voting -> {
            val nc = competitors.size.coerceAtLeast(1)
            voteTurn(seg, seg.prompts[step / nc], competitors[step % nc], true)
        }
        is Segment.Tournament -> tourVoteTurn(seg, true)
        else -> null
    }

    private fun revealQuiz(seg: Segment.Quiz): List<ServerState> {
        val q = seg.questions[step]
        val correct = q.options.filter { it.correct }.map { it.id }.toSet()
        answers.forEach { (pid, opts) ->
            if (opts.isNotEmpty() && opts.toSet() == correct) {
                audienceScores[pid] = (audienceScores[pid] ?: 0.0) + q.points
            }
        }
        val standings = audienceScores.entries.sortedByDescending { it.value }
            .mapIndexed { i, e -> Standing(e.key, e.value, i + 1) }
        return listOf(ServerState.Reveal(curTurnId, standings, correct.toList()), playersScore())
    }

    private fun revealQuestionnaire(seg: Segment.Questionnaire): List<ServerState> {
        val q = seg.questions[step]
        val dist = LinkedHashMap<String, Int>()
        q.options.forEach { dist[it.id] = 0 }
        answers.values.forEach { opts -> opts.forEach { dist[it] = (dist[it] ?: 0) + 1 } }
        return listOf(ServerState.Reveal(curTurnId, emptyList(), distribution = dist))
    }

    private fun revealVoting(seg: Segment.Voting): List<ServerState> {
        val nc = competitors.size.coerceAtLeast(1)
        val prompt = seg.prompts[step / nc]
        val comp = competitors[step % nc]
        val score = aggregate(prompt.criteria)
        scoreboard[comp.id] = (scoreboard[comp.id] ?: 0.0) + score
        return listOf(ServerState.Reveal(curTurnId, emptyList(), subjectId = comp.id, subjectScore = round2(score)))
    }

    // ---- Fase Torneo --------------------------------------------------------

    private fun tournamentActive(): Boolean {
        val b = bracket ?: return false
        return tourQueue.isNotEmpty() || BracketEngine.nextMatch(b) != null
    }

    private fun resetTournament() {
        bracket = null; tourMatchId = null; tourQueue.clear(); tourCurrent = null; tourScores.clear()
    }

    private fun initTournament(seg: Segment.Tournament): List<ServerState> {
        bracket = BracketEngine.create(competitors.map { it.id }.shuffled())
        tourScores.clear(); tourQueue.clear(); tourCurrent = null; tourMatchId = null
        return startTournamentMatchOrEnd(seg)
    }

    private fun advanceTournament(seg: Segment.Tournament): List<ServerState> {
        if (tourQueue.isNotEmpty()) {
            tourCurrent = tourQueue.removeFirst()
            beginTournamentSubject(seg)
            return listOf(bracketState(), tourVoteTurn(seg, false), ServerState.Progress(curTurnId, 0, players.size))
        }
        return startTournamentMatchOrEnd(seg)
    }

    private fun startTournamentMatchOrEnd(seg: Segment.Tournament): List<ServerState> {
        val b = bracket!!
        val match = BracketEngine.nextMatch(b)
        if (match == null) {
            // Torneo finito: passa alla fase successiva della timeline.
            cursor++; step = -1; resetTournament()
            return enterSegment()
        }
        tourMatchId = match.id
        tourScores.clear()
        tourQueue.addAll(listOfNotNull(competitorsById[match.slotA], competitorsById[match.slotB]))
        tourCurrent = tourQueue.removeFirst()
        beginTournamentSubject(seg)
        return listOf(bracketState(), tourVoteTurn(seg, false), ServerState.Progress(curTurnId, 0, players.size))
    }

    private fun beginTournamentSubject(seg: Segment.Tournament) {
        votes.clear()
        phase = RoomPhase.INPUT
        val comp = tourCurrent!!
        curTurnId = "${seg.id}:${tourMatchId}:${comp.id}"
        curCompetitorId = comp.id
        pendingSchedSeconds = seg.answerTimeSeconds
        pendingSchedTurnId = curTurnId
    }

    private fun revealTournament(seg: Segment.Tournament): List<ServerState> {
        val comp = tourCurrent ?: return emptyList()
        val score = aggregate(seg.prompt.criteria)
        tourScores[comp.id] = score
        val out = mutableListOf<ServerState>(ServerState.Reveal(curTurnId, emptyList(), subjectId = comp.id, subjectScore = round2(score)))
        if (tourQueue.isEmpty() && tourScores.size >= 2 && tourMatchId != null && bracket != null) {
            val winner = tourScores.maxByOrNull { it.value }!!.key
            bracket = BracketEngine.recordWinner(bracket!!, tourMatchId!!, winner)
            bracket!!.champion?.let { scoreboard[it] = (scoreboard[it] ?: 0.0) + seg.winnerBonus }
            out += bracketState()
        }
        return out
    }

    private fun tourVoteTurn(seg: Segment.Tournament, locked: Boolean): ServerState.VoteTurn {
        val comp = tourCurrent!!
        val total = bracket?.matches?.size ?: 0
        val decided = bracket?.matches?.count { it.winner != null } ?: 0
        return ServerState.VoteTurn(
            turnId = curTurnId,
            phase = phase,
            promptTitle = seg.prompt.title,
            competitorId = comp.id,
            competitorName = comp.name,
            criteria = seg.prompt.criteria.map { VoteCriterionView(it.id, it.label, it.weight) },
            scaleMin = seg.voteScale.min,
            scaleMax = seg.voteScale.max,
            scaleStep = seg.voteScale.step,
            index = (decided + 1).coerceAtMost(total.coerceAtLeast(1)),
            total = total,
            timerSeconds = seg.answerTimeSeconds,
            locked = locked,
        )
    }

    private fun bracketState(): ServerState.Bracket {
        val b = bracket ?: return ServerState.Bracket(emptyList())
        return ServerState.Bracket(b.matches, b.champion, BracketEngine.nextMatch(b)?.id)
    }

    // ---- Fasi di presentazione ----------------------------------------------

    private fun presentationMessages(seg: Segment): List<ServerState> = when (seg) {
        is Segment.Title -> listOf(screen("title", seg.title, subtitle = seg.subtitle))
        is Segment.Media -> listOf(screen("media", seg.title, caption = seg.caption, mediaKind = seg.kind.name, assetId = seg.assetId))
        is Segment.Standings -> listOf(scoreBoard(seg.title, isFinal = false))
        is Segment.Final -> listOf(finalScoreboard(title = seg.title))
        else -> emptyList()
    }

    private fun screen(kind: String, title: String, subtitle: String = "", caption: String = "", mediaKind: String? = null, assetId: String? = null) =
        ServerState.Screen(
            index = cursor + 1, total = segments.size, kind = kind,
            title = title, subtitle = subtitle, caption = caption, mediaKind = mediaKind, assetId = assetId,
        )

    private fun scoreBoard(title: String, isFinal: Boolean): ServerState.ScoreBoard {
        val entries = competitorEntries()
        return ServerState.ScoreBoard(title = title, entries = entries, isFinal = isFinal, champion = if (isFinal) entries.firstOrNull()?.id else null)
    }

    private fun finalScoreboard(title: String = "Classifica finale"): ServerState.ScoreBoard =
        scoreBoard(title, isFinal = true)

    private fun competitorEntries(): List<ScoreEntry> =
        scoreboard.entries.sortedByDescending { it.value }.mapIndexed { i, e ->
            ScoreEntry(id = e.key, name = competitors.firstOrNull { it.id == e.key }?.name ?: e.key, score = round2(e.value), rank = i + 1)
        }

    // ---- Costruttori di messaggi interattivi --------------------------------

    private fun quizTurn(seg: Segment, q: it.marcolipparini.sfide.engine.model.Question, locked: Boolean, timer: Int? = null): ServerState.Turn {
        val total = when (seg) {
            is Segment.Quiz -> seg.questions.size
            is Segment.Questionnaire -> seg.questions.size
            else -> 1
        }
        val t = timer ?: (seg as? Segment.Quiz)?.answerTimeSeconds
        return ServerState.Turn(
            turnId = curTurnId,
            phase = phase,
            prompt = PublicPrompt(q.text, q.imageAssetId, q.options.map { PublicOption(it.id, it.text) }, q.selection),
            index = step + 1,
            total = total,
            timerSeconds = t,
            locked = locked,
        )
    }

    private fun voteTurn(seg: Segment.Voting, prompt: VotingPrompt, comp: Competitor, locked: Boolean): ServerState.VoteTurn =
        ServerState.VoteTurn(
            turnId = curTurnId,
            phase = phase,
            promptTitle = prompt.title,
            competitorId = comp.id,
            competitorName = comp.name,
            criteria = prompt.criteria.map { VoteCriterionView(it.id, it.label, it.weight) },
            scaleMin = seg.voteScale.min,
            scaleMax = seg.voteScale.max,
            scaleStep = seg.voteScale.step,
            index = step + 1,
            total = stepCount(seg),
            timerSeconds = seg.answerTimeSeconds,
            locked = locked,
        )

    private fun playersState(): ServerState.Players =
        ServerState.Players(players.values.map { PlayerInfo(it.id, it.name, audienceScores[it.id] ?: 0.0) })

    private fun playersScore(): ServerState.Players = playersState()

    // ---- Aggregazione voti (come VotingSession) -----------------------------

    private fun aggregate(criteria: List<VoteCriterion>): Double {
        val perVoter = votes.values.map { weightedCriteria(it, criteria) }.sorted()
        if (perVoter.isEmpty()) return 0.0
        val trimmed = if (room.scoring.trimExtremes && perVoter.size >= 3) perVoter.subList(1, perVoter.size - 1) else perVoter
        return when (room.scoring.aggregation) {
            Aggregation.MEDIAN -> if (trimmed.size % 2 == 1) trimmed[trimmed.size / 2] else (trimmed[trimmed.size / 2 - 1] + trimmed[trimmed.size / 2]) / 2.0
            Aggregation.MEAN, Aggregation.WEIGHTED_MEAN -> trimmed.average()
        }
    }

    private fun weightedCriteria(values: Map<String, Double>, criteria: List<VoteCriterion>): Double {
        val w = criteria.sumOf { it.weight }
        if (w == 0.0) return 0.0
        return criteria.sumOf { (values[it.id] ?: 0.0) * it.weight } / w
    }

    override fun snapshotResult(): MatchResult? {
        if (phase == RoomPhase.LOBBY) return null
        return buildResult()
    }

    private fun buildResult(): MatchResult {
        // Con squadre: classifica dei concorrenti. Show incentrato sul pubblico
        // (nessun concorrente): classifica dal punteggio degli spettatori.
        val standings: List<Standing>
        val winner: String?
        if (competitors.isNotEmpty()) {
            val entries = competitorEntries()
            standings = entries.map { Standing(it.id, it.score, it.rank) }
            winner = entries.firstOrNull()?.name
        } else {
            standings = audienceScores.entries.sortedByDescending { it.value }
                .mapIndexed { i, e -> Standing(e.key, round2(e.value), i + 1) }
            winner = standings.firstOrNull()?.competitorId?.let { players[it]?.name }
        }
        return MatchResult(
            id = java.util.UUID.randomUUID().toString(),
            roomTitle = room.meta.title,
            playedAtEpochMs = System.currentTimeMillis(),
            finalStandings = standings,
            winnerLabel = winner,
        )
    }

    private fun scheduleAutoLock(seconds: Int, turnId: String) {
        timerJob?.cancel()
        if (seconds <= 0) return
        timerJob = timerScope.launch {
            delay(seconds * 1000L)
            autoLock(turnId)
        }
    }

    private suspend fun autoLock(turnId: String) {
        val msg = mutex.withLock {
            val seg = segment
            if (!(isGenericInteractive(seg) || seg is Segment.Tournament) || phase != RoomPhase.INPUT || curTurnId != turnId) return
            phase = RoomPhase.LOCKED
            lockedTurn(seg!!)
        } ?: return
        broadcast(msg)
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    private suspend fun broadcast(state: ServerState) {
        val text = encode(state)
        for (c in connections.values) runCatching { c.send(text) }
    }

    private fun encode(state: ServerState): String = EngineJson.encodeToString(ServerState.serializer(), state)
}
