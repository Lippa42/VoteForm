package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
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
            if (segment !is Segment.Voting) return
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
        var finished: MatchResult? = null
        var schedSeconds = 0
        var schedTurnId: String? = null
        val msgs = mutex.withLock {
            val seg = segment
            if (seg != null && isInteractive(seg) && step + 1 < stepCount(seg)) {
                step++
                val r = beginStep(seg)
                schedSeconds = r.second; schedTurnId = r.third
                r.first
            } else {
                cursor++
                step = -1
                val next = segment
                when {
                    next == null -> {
                        phase = RoomPhase.FINISHED
                        finished = buildResult()
                        listOf(finalScoreboard())
                    }
                    isInteractive(next) -> {
                        step = 0
                        val r = beginStep(next)
                        schedSeconds = r.second; schedTurnId = r.third
                        r.first
                    }
                    else -> presentationMessages(next)
                }
            }.also { lastState = it }
        }
        msgs.forEach { broadcast(it) }
        schedTurnId?.let { if (schedSeconds > 0) scheduleAutoLock(schedSeconds, it) }
        finished?.let { onFinish?.invoke(it) }
    }

    override suspend fun lock() {
        timerJob?.cancel()
        val msg = mutex.withLock {
            val seg = segment
            if (!isInteractive(seg) || phase != RoomPhase.INPUT) return
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
                else -> return
            }.also { lastState = it }
        }
        msgs.forEach { broadcast(it) }
    }

    // ---- Passi interattivi --------------------------------------------------

    private fun isInteractive(seg: Segment?): Boolean =
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

    // ---- Fasi di presentazione ----------------------------------------------

    private fun presentationMessages(seg: Segment): List<ServerState> = when (seg) {
        is Segment.Title -> listOf(screen("title", seg.title, subtitle = seg.subtitle))
        is Segment.Media -> listOf(screen("media", seg.title, caption = seg.caption, mediaKind = seg.kind.name))
        is Segment.Standings -> listOf(scoreBoard(seg.title, isFinal = false))
        is Segment.Final -> listOf(finalScoreboard(title = seg.title))
        else -> emptyList()
    }

    private fun screen(kind: String, title: String, subtitle: String = "", caption: String = "", mediaKind: String? = null) =
        ServerState.Screen(
            index = cursor + 1, total = segments.size, kind = kind,
            title = title, subtitle = subtitle, caption = caption, mediaKind = mediaKind,
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

    private fun buildResult(): MatchResult {
        val entries = competitorEntries()
        return MatchResult(
            id = java.util.UUID.randomUUID().toString(),
            roomTitle = room.meta.title,
            playedAtEpochMs = System.currentTimeMillis(),
            finalStandings = entries.map { Standing(it.id, it.score, it.rank) },
            winnerLabel = entries.firstOrNull()?.name,
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
            if (!isInteractive(seg) || phase != RoomPhase.INPUT || curTurnId != turnId) return
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
