package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.PlayerInfo
import it.marcolipparini.sfide.engine.protocol.PublicOption
import it.marcolipparini.sfide.engine.protocol.PublicPrompt
import it.marcolipparini.sfide.engine.protocol.ServerState
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
 * Questionario/sondaggio: nessuna risposta "giusta". Alla rivelazione mostra la
 * **distribuzione** delle risposte del pubblico ("il pubblico ha detto"). Stessa
 * meccanica di raccolta del quiz, ma senza punteggio.
 */
class QuestionnaireSession(override val room: RoomDefinition) : GameSession {

    private val survey = room.mode as? GameModeConfig.Questionnaire
        ?: error("QuestionnaireSession richiede una RoomDefinition in modalità Questionnaire")

    private val mutex = Mutex()
    private val connections = ConcurrentHashMap<String, Connection>()
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null

    private data class Player(val id: String, var name: String)

    private val players = LinkedHashMap<String, Player>()
    private val answers = HashMap<String, List<String>>() // playerId -> opzioni
    private var lastReveal: ServerState.Reveal? = null

    override var onFinish: ((MatchResult) -> Unit)? = null

    @Volatile
    override var phase: RoomPhase = RoomPhase.LOBBY
        private set

    private var index = -1
    private val question get() = survey.questions.getOrNull(index)

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
            is ClientIntent.SubmitAnswer -> handleAnswer(conn, intent)
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
                playersMsg = playersState()
            }
            initialMessages()
        }
        initial.forEach { conn.send(encode(it)) }
        playersMsg?.let { broadcast(it) }
    }

    private suspend fun handleAnswer(conn: Connection, ans: ClientIntent.SubmitAnswer) {
        val progress = mutex.withLock {
            val q = question ?: return
            if (phase != RoomPhase.INPUT) return
            if (ans.turnId != q.id) return
            if (!players.containsKey(conn.id)) return
            answers[conn.id] = ans.optionIds
            progressState()
        }
        broadcast(progress)
    }

    override suspend fun next() {
        timerJob?.cancel()
        var scheduleId: String? = null
        var finished: MatchResult? = null
        val msgs = mutex.withLock {
            if (index + 1 >= survey.questions.size) {
                phase = RoomPhase.FINISHED
                finished = MatchResult(
                    id = java.util.UUID.randomUUID().toString(),
                    roomTitle = room.meta.title,
                    playedAtEpochMs = System.currentTimeMillis(),
                    finalStandings = emptyList(),
                )
                listOf(ServerState.Turn(turnId = "end", phase = RoomPhase.FINISHED))
            } else {
                index++
                answers.clear()
                phase = RoomPhase.INPUT
                scheduleId = question?.id
                listOfNotNull(turnState(), progressState())
            }
        }
        msgs.forEach { broadcast(it) }
        scheduleId?.let { scheduleAutoLock(survey.answerTimeSeconds, it) }
        finished?.let { onFinish?.invoke(it) }
    }

    override suspend fun lock() {
        timerJob?.cancel()
        val msg = mutex.withLock {
            if (phase != RoomPhase.INPUT) return
            phase = RoomPhase.LOCKED
            turnState(locked = true)
        } ?: return
        broadcast(msg)
    }

    override suspend fun reveal() {
        timerJob?.cancel()
        val msg = mutex.withLock {
            val q = question ?: return
            if (phase != RoomPhase.INPUT && phase != RoomPhase.LOCKED) return
            val distribution = LinkedHashMap<String, Int>()
            q.options.forEach { distribution[it.id] = 0 }
            answers.values.forEach { opts -> opts.forEach { id -> distribution[id] = (distribution[id] ?: 0) + 1 } }
            phase = RoomPhase.STANDINGS
            ServerState.Reveal(turnId = q.id, standings = emptyList(), distribution = distribution)
                .also { lastReveal = it }
        }
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
            if ((question?.id ?: "") != turnId) return
            phase = RoomPhase.LOCKED
            turnState(locked = true)
        } ?: return
        broadcast(msg)
    }

    private fun initialMessages(): List<ServerState> = buildList {
        add(ServerState.Snapshot(phase = phase, room = room, standings = emptyList()))
        add(playersState())
        when (phase) {
            RoomPhase.INPUT, RoomPhase.LOCKED -> turnState(locked = phase == RoomPhase.LOCKED)?.let { add(it) }
            RoomPhase.STANDINGS -> lastReveal?.let { add(it) }
            else -> Unit
        }
    }

    private fun turnState(locked: Boolean = false): ServerState.Turn? {
        val q = question ?: return null
        return ServerState.Turn(
            turnId = q.id,
            phase = phase,
            prompt = PublicPrompt(
                title = q.text,
                imageAssetId = q.imageAssetId,
                options = q.options.map { PublicOption(it.id, it.text) },
                selection = q.selection,
            ),
            index = index + 1,
            total = survey.questions.size,
            timerSeconds = survey.answerTimeSeconds,
            locked = locked,
        )
    }

    private fun progressState(): ServerState.Progress =
        ServerState.Progress(turnId = question?.id ?: "none", answered = answers.size, total = players.size)

    private fun playersState(): ServerState.Players =
        ServerState.Players(players.values.map { PlayerInfo(it.id, it.name) })

    private suspend fun broadcast(state: ServerState) {
        val text = encode(state)
        for (c in connections.values) runCatching { c.send(text) }
    }

    private fun encode(state: ServerState): String = EngineJson.encodeToString(ServerState.serializer(), state)
}
