package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.bracket.Bracket
import it.marcolipparini.sfide.engine.bracket.BracketEngine
import it.marcolipparini.sfide.engine.model.Aggregation
import it.marcolipparini.sfide.engine.model.Competitor
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.model.VoteCriterion
import it.marcolipparini.sfide.engine.model.VotingPrompt
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.ServerState
import it.marcolipparini.sfide.engine.protocol.VoteCriterionView
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Sfida a voti in formato **torneo a eliminazione diretta**. Il tabellone è
 * sorteggiato all'avvio; ogni incontro valuta i due concorrenti (una prova a
 * testa) e chi ottiene il punteggio più alto avanza. La logica del tabellone vive
 * nel motore puro [BracketEngine]; qui si orchestrano le fasi e i voti.
 */
class KnockoutSession(override val room: RoomDefinition) : GameSession {

    private val voting = room.mode as? GameModeConfig.Voting
        ?: error("KnockoutSession richiede una RoomDefinition in modalità Voting")

    private val scale = voting.voteScale
    private val rules = room.scoring
    private val prompt: VotingPrompt = voting.prompts.firstOrNull()
        ?: VotingPrompt(id = "p0", title = "Sfida")
    private val competitorsById = room.participants.competitors.associateBy { it.id }

    private val mutex = Mutex()
    private val connections = ConcurrentHashMap<String, Connection>()
    private val voters = LinkedHashSet<String>()
    private val votes = HashMap<String, Map<String, Double>>() // voterId -> criterionId→valore

    private var bracket: Bracket = BracketEngine.create(room.participants.competitors.map { it.id }.shuffled())
    private var currentMatchId: String? = null
    private val queue = ArrayDeque<Competitor>()
    private var current: Competitor? = null
    private val matchScores = HashMap<String, Double>()
    private var lastReveal: ServerState.Reveal? = null

    @Volatile
    override var phase: RoomPhase = RoomPhase.LOBBY
        private set

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
        val intent = runCatching { EngineJson.decodeFromString(ClientIntent.serializer(), text) }.getOrNull() ?: return
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
                if (join.pin != room.meta.pin) return
                conn.name = join.name?.takeIf { it.isNotBlank() } ?: "Ospite"
                voters += conn.id
            }
            initialMessages()
        }
        initial.forEach { conn.send(encode(it)) }
    }

    private suspend fun handleVote(conn: Connection, vote: ClientIntent.CastVote) {
        val progress = mutex.withLock {
            val subject = current ?: return
            if (phase != RoomPhase.INPUT) return
            if (vote.targetId != subject.id) return
            if (conn.id !in voters) return
            votes[conn.id] = vote.values
            progressState()
        }
        broadcast(progress)
    }

    // ---- Controlli admin ----------------------------------------------------

    override suspend fun next() {
        val msgs = mutex.withLock {
            if (queue.isNotEmpty()) {
                current = queue.removeFirst()
                votes.clear()
                phase = RoomPhase.INPUT
                listOfNotNull(voteTurn(), progressState())
            } else {
                val match = BracketEngine.nextMatch(bracket)
                if (match == null) {
                    phase = RoomPhase.FINISHED
                    listOf(finishedTurn(), bracketState())
                } else {
                    currentMatchId = match.id
                    matchScores.clear()
                    queue.addAll(listOfNotNull(competitorsById[match.slotA], competitorsById[match.slotB]))
                    current = queue.removeFirst()
                    votes.clear()
                    phase = RoomPhase.INPUT
                    listOfNotNull(bracketState(), voteTurn(), progressState())
                }
            }
        }
        msgs.forEach { broadcast(it) }
    }

    override suspend fun lock() {
        val msg = mutex.withLock {
            if (phase != RoomPhase.INPUT) return
            phase = RoomPhase.LOCKED
            voteTurn(locked = true)
        } ?: return
        broadcast(msg)
    }

    override suspend fun reveal() {
        val msgs = mutex.withLock {
            val subject = current ?: return
            if (phase != RoomPhase.INPUT && phase != RoomPhase.LOCKED) return
            val score = aggregate(prompt.criteria)
            matchScores[subject.id] = score
            phase = RoomPhase.STANDINGS
            val out = mutableListOf<ServerState>()
            val reveal = ServerState.Reveal(
                turnId = subject.id,
                standings = emptyList(),
                subjectId = subject.id,
                subjectScore = round2(score),
            )
            lastReveal = reveal
            out += reveal
            // Se entrambi i concorrenti dell'incontro sono stati valutati, decide il vincitore.
            if (queue.isEmpty() && matchScores.size >= 2 && currentMatchId != null) {
                val winner = matchScores.maxByOrNull { it.value }!!.key
                bracket = BracketEngine.recordWinner(bracket, currentMatchId!!, winner)
                out += bracketState()
            }
            out
        }
        msgs.forEach { broadcast(it) }
    }

    // ---- Aggregazione (come VotingSession) ---------------------------------

    private fun aggregate(criteria: List<VoteCriterion>): Double {
        val perVoter = votes.values.map { weightedCriteria(it, criteria) }.sorted()
        if (perVoter.isEmpty()) return 0.0
        val trimmed = if (rules.trimExtremes && perVoter.size >= 3) perVoter.subList(1, perVoter.size - 1) else perVoter
        return when (rules.aggregation) {
            Aggregation.MEDIAN -> median(trimmed)
            Aggregation.MEAN, Aggregation.WEIGHTED_MEAN -> trimmed.average()
        }
    }

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

    // ---- Messaggi di stato (sotto mutex) -----------------------------------

    private fun initialMessages(): List<ServerState> = buildList {
        add(ServerState.Snapshot(phase = phase, room = room, standings = emptyList()))
        // L'ultimo messaggio determina cosa mostra la TV al (ri)collegamento:
        // durante il voto il turno, altrimenti il tabellone.
        when (phase) {
            RoomPhase.INPUT, RoomPhase.LOCKED -> {
                add(bracketState())
                voteTurn(locked = phase == RoomPhase.LOCKED)?.let { add(it) }
            }
            RoomPhase.STANDINGS -> {
                lastReveal?.let { add(it) }
                add(bracketState())
            }
            else -> add(bracketState())
        }
    }

    private fun bracketState(): ServerState.Bracket = ServerState.Bracket(
        matches = bracket.matches,
        champion = bracket.champion,
        nextMatchId = BracketEngine.nextMatch(bracket)?.id,
    )

    private fun voteTurn(locked: Boolean = false): ServerState.VoteTurn? {
        val subject = current ?: return null
        val decided = bracket.matches.count { it.winner != null }
        return ServerState.VoteTurn(
            turnId = subject.id,
            phase = phase,
            promptTitle = prompt.title,
            competitorId = subject.id,
            competitorName = subject.name,
            criteria = prompt.criteria.map { VoteCriterionView(it.id, it.label, it.weight) },
            scaleMin = scale.min,
            scaleMax = scale.max,
            scaleStep = scale.step,
            index = decided + 1,
            total = bracket.matches.size,
            timerSeconds = voting.answerTimeSeconds,
            locked = locked,
        )
    }

    private fun finishedTurn(): ServerState.VoteTurn = ServerState.VoteTurn(
        turnId = "end", phase = RoomPhase.FINISHED, promptTitle = "", competitorId = "", competitorName = "",
        criteria = emptyList(), scaleMin = scale.min, scaleMax = scale.max, scaleStep = scale.step,
        index = bracket.matches.size, total = bracket.matches.size,
    )

    private fun progressState(): ServerState.Progress =
        ServerState.Progress(turnId = current?.id ?: "none", answered = votes.size, total = voters.size)

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    private suspend fun broadcast(state: ServerState) {
        val text = encode(state)
        for (c in connections.values) runCatching { c.send(text) }
    }

    private fun encode(state: ServerState): String = EngineJson.encodeToString(ServerState.serializer(), state)
}
