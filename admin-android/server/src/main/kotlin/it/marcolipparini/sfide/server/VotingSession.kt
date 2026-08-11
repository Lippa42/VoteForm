package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.Aggregation
import it.marcolipparini.sfide.engine.model.Competitor
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.model.VoteCriterion
import it.marcolipparini.sfide.engine.model.VotingPrompt
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.phase.Standing
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
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
 * Stato autoritativo di una Sfida a voti (culinaria / talent). Si valuta **un
 * concorrente alla volta** su più criteri pesati; l'aggregazione dei voti segue le
 * [it.marcolipparini.sfide.engine.model.ScoringRules] della stanza. Come per il
 * quiz, il calcolo avviene solo qui e lo stesso codice gira embedded su Android.
 */
class VotingSession(override val room: RoomDefinition) : GameSession {

    private val voting = room.mode as? GameModeConfig.Voting
        ?: error("VotingSession richiede una RoomDefinition in modalità Voting")

    private val scale = voting.voteScale
    private val rules = room.scoring

    private data class Subject(val prompt: VotingPrompt, val competitor: Competitor)

    private val subjects: List<Subject> = buildList {
        for (prompt in voting.prompts) {
            for (competitor in room.participants.competitors) add(Subject(prompt, competitor))
        }
    }

    private val mutex = Mutex()
    private val connections = ConcurrentHashMap<String, Connection>()
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private val voters = LinkedHashSet<String>()
    private val votes = HashMap<String, Map<String, Double>>() // voterId -> criterionId→valore
    private val totals = LinkedHashMap<String, Double>().apply {
        room.participants.competitors.forEach { put(it.id, 0.0) }
    }
    private var standings: List<Standing> = emptyList()
    private var lastReveal: ServerState.Reveal? = null

    override var onFinish: ((MatchResult) -> Unit)? = null

    @Volatile
    override var phase: RoomPhase = RoomPhase.LOBBY
        private set

    private var index = -1
    private val subject get() = subjects.getOrNull(index)
    private val pin get() = room.meta.pin

    // ---- Connessioni --------------------------------------------------------

    override suspend fun addConnection(conn: Connection) {
        connections[conn.id] = conn
        val msgs = mutex.withLock { initialMessages() }
        msgs.forEach { conn.send(encode(it)) }
    }

    override fun removeConnection(id: String) {
        connections.remove(id)
    }

    override suspend fun onIntent(conn: Connection, text: String) {
        val intent = runCatching { EngineJson.decodeFromString(ClientIntent.serializer(), text) }
            .getOrNull() ?: return
        when (intent) {
            is ClientIntent.Join -> handleJoin(conn, intent)
            is ClientIntent.CastVote -> handleVote(conn, intent)
            else -> Unit
        }
    }

    private suspend fun handleJoin(conn: Connection, join: ClientIntent.Join) {
        val initial = mutex.withLock {
            conn.role = join.role
            if (join.role == ClientRole.SPECTATOR) {
                if (join.pin != pin) return
                conn.name = join.name?.takeIf { it.isNotBlank() } ?: "Ospite"
                voters += conn.id
            }
            initialMessages()
        }
        initial.forEach { conn.send(encode(it)) }
    }

    private suspend fun handleVote(conn: Connection, vote: ClientIntent.CastVote) {
        val progress = mutex.withLock {
            val current = subject ?: return
            if (phase != RoomPhase.INPUT) return
            if (vote.targetId != current.competitor.id) return
            if (conn.id !in voters) return
            votes[conn.id] = vote.values
            progressState()
        }
        broadcast(progress)
    }

    // ---- Controlli admin ----------------------------------------------------

    override suspend fun next() {
        timerJob?.cancel()
        var scheduleId: String? = null
        var finished: MatchResult? = null
        val msgs = mutex.withLock {
            if (index + 1 >= subjects.size) {
                phase = RoomPhase.FINISHED
                standings = computeStandings()
                finished = MatchResult(
                    id = java.util.UUID.randomUUID().toString(),
                    roomTitle = room.meta.title,
                    playedAtEpochMs = System.currentTimeMillis(),
                    finalStandings = standings,
                    winnerLabel = standings.firstOrNull()?.competitorId
                        ?.let { id -> room.participants.competitors.firstOrNull { it.id == id }?.name },
                )
                listOf(ServerState.VoteTurn(
                    turnId = "end", phase = RoomPhase.FINISHED, promptTitle = "", competitorId = "",
                    competitorName = "", criteria = emptyList(), scaleMin = scale.min, scaleMax = scale.max,
                    scaleStep = scale.step, index = subjects.size, total = subjects.size,
                ))
            } else {
                index++
                votes.clear()
                phase = RoomPhase.INPUT
                scheduleId = subject?.let { turnId(it) }
                listOfNotNull(voteTurn(), progressState())
            }
        }
        msgs.forEach { broadcast(it) }
        scheduleId?.let { scheduleAutoLock(voting.answerTimeSeconds, it) }
        finished?.let { onFinish?.invoke(it) }
    }

    override fun snapshotResult(): MatchResult? {
        if (phase == RoomPhase.LOBBY || index < 0) return null
        val s = computeStandings()
        return MatchResult(
            id = java.util.UUID.randomUUID().toString(),
            roomTitle = room.meta.title,
            playedAtEpochMs = System.currentTimeMillis(),
            finalStandings = s,
            winnerLabel = s.firstOrNull()?.competitorId
                ?.let { id -> room.participants.competitors.firstOrNull { it.id == id }?.name },
        )
    }

    override suspend fun lock() {
        timerJob?.cancel()
        val msg = mutex.withLock {
            if (phase != RoomPhase.INPUT) return
            phase = RoomPhase.LOCKED
            voteTurn(locked = true)
        } ?: return
        broadcast(msg)
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
            if (phase != RoomPhase.INPUT) return
            if ((subject?.let { turnId(it) } ?: "") != turnId) return
            phase = RoomPhase.LOCKED
            voteTurn(locked = true)
        } ?: return
        broadcast(msg)
    }

    override suspend fun reveal() {
        timerJob?.cancel()
        val msg = mutex.withLock {
            val current = subject ?: return
            if (phase != RoomPhase.INPUT && phase != RoomPhase.LOCKED) return
            val subjectScore = aggregate(current.prompt.criteria)
            totals[current.competitor.id] = (totals[current.competitor.id] ?: 0.0) + subjectScore
            phase = RoomPhase.STANDINGS
            standings = computeStandings()
            ServerState.Reveal(
                turnId = turnId(current),
                standings = standings,
                subjectId = current.competitor.id,
                subjectScore = round2(subjectScore),
            ).also { lastReveal = it }
        }
        broadcast(msg)
    }

    // ---- Aggregazione dei voti ---------------------------------------------

    /** Punteggio della prova per il concorrente corrente, secondo le regole. */
    private fun aggregate(criteria: List<VoteCriterion>): Double {
        val perVoter = votes.values
            .map { values -> weightedCriteria(values, criteria) }
            .sorted()
        if (perVoter.isEmpty()) return 0.0
        val trimmed = if (rules.trimExtremes && perVoter.size >= 3) {
            perVoter.subList(1, perVoter.size - 1)
        } else {
            perVoter
        }
        return when (rules.aggregation) {
            Aggregation.MEDIAN -> median(trimmed)
            Aggregation.MEAN, Aggregation.WEIGHTED_MEAN -> trimmed.average()
        }
    }

    /** Media pesata dei criteri per un singolo votante (es. Gusto ×2). */
    private fun weightedCriteria(values: Map<String, Double>, criteria: List<VoteCriterion>): Double {
        val weightSum = criteria.sumOf { it.weight }
        if (weightSum == 0.0) return 0.0
        return criteria.sumOf { (values[it.id] ?: 0.0) * it.weight } / weightSum
    }

    private fun median(sorted: List<Double>): Double {
        if (sorted.isEmpty()) return 0.0
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun computeStandings(): List<Standing> =
        totals.entries
            .sortedByDescending { it.value }
            .mapIndexed { i, e -> Standing(competitorId = e.key, points = round2(e.value), rank = i + 1) }

    // ---- Messaggi di stato (sotto mutex) -----------------------------------

    private fun initialMessages(): List<ServerState> = buildList {
        add(ServerState.Snapshot(phase = phase, room = room, standings = standings))
        when (phase) {
            RoomPhase.INPUT, RoomPhase.LOCKED -> voteTurn(locked = phase == RoomPhase.LOCKED)?.let { add(it) }
            RoomPhase.STANDINGS -> lastReveal?.let { add(it) }
            else -> Unit
        }
    }

    private fun voteTurn(locked: Boolean = false): ServerState.VoteTurn? {
        val current = subject ?: return null
        return ServerState.VoteTurn(
            turnId = turnId(current),
            phase = phase,
            promptTitle = current.prompt.title,
            competitorId = current.competitor.id,
            competitorName = current.competitor.name,
            criteria = current.prompt.criteria.map { VoteCriterionView(it.id, it.label, it.weight) },
            scaleMin = scale.min,
            scaleMax = scale.max,
            scaleStep = scale.step,
            index = index + 1,
            total = subjects.size,
            timerSeconds = voting.answerTimeSeconds,
            locked = locked,
        )
    }

    private fun progressState(): ServerState.Progress =
        ServerState.Progress(turnId = subject?.let { turnId(it) } ?: "none", answered = votes.size, total = voters.size)

    private fun turnId(s: Subject): String = "${s.prompt.id}:${s.competitor.id}"

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    // ---- Utilità ------------------------------------------------------------

    private suspend fun broadcast(state: ServerState) {
        val text = encode(state)
        for (c in connections.values) runCatching { c.send(text) }
    }

    private fun encode(state: ServerState): String =
        EngineJson.encodeToString(ServerState.serializer(), state)
}
