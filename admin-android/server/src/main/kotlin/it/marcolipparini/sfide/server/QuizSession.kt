package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.phase.Standing
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

/** Una connessione WebSocket verso un client, con la sua funzione di invio. */
class Connection(
    val id: String,
    val send: suspend (String) -> Unit,
) {
    var role: ClientRole? = null
    var name: String = "Ospite"
}

/**
 * Stato autoritativo di una stanza in modalità Quiz (stile Kahoot): l'unica
 * verità vive qui. I client mostrano lo stato e inviano intenti; il calcolo del
 * punteggio avviene solo qui. È deliberatamente in memoria e single-writer
 * (protetto da [mutex]); la persistenza (Room DB) arriverà come modulo a parte.
 *
 * Lo stesso codice girerà embedded nell'app Android host: non dipende da Android.
 */
class QuizSession(override val room: RoomDefinition) : GameSession {

    private val quiz = room.mode as? GameModeConfig.Quiz
        ?: error("QuizSession richiede una RoomDefinition in modalità Quiz")

    private val mutex = Mutex()
    private val connections = ConcurrentHashMap<String, Connection>()
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null

    private data class Player(val id: String, var name: String, var score: Double = 0.0)

    private val players = LinkedHashMap<String, Player>()
    private val answers = HashMap<String, List<String>>() // playerId -> opzioni scelte nel turno
    private var standings: List<Standing> = emptyList()
    private var lastCorrect: List<String> = emptyList()

    override var onFinish: ((MatchResult) -> Unit)? = null

    @Volatile
    override var phase: RoomPhase = RoomPhase.LOBBY
        private set

    private var index = -1
    private val question get() = quiz.questions.getOrNull(index)
    private val pin get() = room.meta.pin

    // ---- Connessioni --------------------------------------------------------

    override suspend fun addConnection(conn: Connection) {
        connections[conn.id] = conn
        val msgs = mutex.withLock { initialMessages() }
        msgs.forEach { conn.send(encode(it)) }
    }

    override fun removeConnection(id: String) {
        connections.remove(id)
        // Il giocatore resta nello scoreboard anche se si disconnette.
    }

    override suspend fun onIntent(conn: Connection, text: String) {
        val intent = runCatching { EngineJson.decodeFromString(ClientIntent.serializer(), text) }
            .getOrNull() ?: return
        when (intent) {
            is ClientIntent.Join -> handleJoin(conn, intent)
            is ClientIntent.SubmitAnswer -> handleAnswer(conn, intent)
            else -> Unit // buzz/reaction/vote: non usati nella fetta quiz
        }
    }

    private suspend fun handleJoin(conn: Connection, join: ClientIntent.Join) {
        var playersMsg: ServerState? = null
        val initial = mutex.withLock {
            conn.role = join.role
            if (join.role == ClientRole.SPECTATOR) {
                if (join.pin != pin) return // PIN errato: nella fetta ignoriamo silenziosamente
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

    // ---- Controlli admin (guidano la macchina a stati) ----------------------

    override suspend fun next() {
        timerJob?.cancel()
        var scheduleId: String? = null
        var finished: MatchResult? = null
        val msgs = mutex.withLock {
            if (index + 1 >= quiz.questions.size) {
                phase = RoomPhase.FINISHED
                standings = computeStandings()
                finished = MatchResult(
                    id = java.util.UUID.randomUUID().toString(),
                    roomTitle = room.meta.title,
                    playedAtEpochMs = System.currentTimeMillis(),
                    finalStandings = standings,
                    winnerLabel = standings.firstOrNull()?.competitorId?.let { players[it]?.name },
                )
                listOf(ServerState.Turn(turnId = "end", phase = RoomPhase.FINISHED))
            } else {
                index++
                answers.clear()
                lastCorrect = emptyList()
                phase = RoomPhase.INPUT
                scheduleId = question?.id
                listOfNotNull(turnState(), progressState())
            }
        }
        msgs.forEach { broadcast(it) }
        scheduleId?.let { scheduleAutoLock(quiz.answerTimeSeconds, it) }
        finished?.let { onFinish?.invoke(it) }
    }

    override fun snapshotResult(): MatchResult? {
        if (phase == RoomPhase.LOBBY || players.isEmpty()) return null
        val s = computeStandings()
        return MatchResult(
            id = java.util.UUID.randomUUID().toString(),
            roomTitle = room.meta.title,
            playedAtEpochMs = System.currentTimeMillis(),
            finalStandings = s,
            winnerLabel = s.firstOrNull()?.competitorId?.let { players[it]?.name },
        )
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

    /** Chiusura automatica allo scadere del tempo (se il turno è ancora quello). */
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

    override suspend fun reveal() {
        timerJob?.cancel()
        val msgs = mutex.withLock {
            val q = question ?: return
            if (phase != RoomPhase.INPUT && phase != RoomPhase.LOCKED) return
            val correct = q.options.filter { it.correct }.map { it.id }.toSet()
            for ((pid, chosen) in answers) {
                val player = players[pid] ?: continue
                if (chosen.isNotEmpty() && chosen.toSet() == correct) player.score += q.points
            }
            lastCorrect = correct.toList()
            phase = RoomPhase.STANDINGS
            standings = computeStandings()
            listOf(revealState(), playersState())
        }
        msgs.forEach { broadcast(it) }
    }

    // ---- Costruzione dei messaggi di stato (chiamati sotto mutex) -----------

    private fun initialMessages(): List<ServerState> = buildList {
        add(snapshot())
        add(playersState())
        when (phase) {
            RoomPhase.INPUT, RoomPhase.LOCKED -> turnState(locked = phase == RoomPhase.LOCKED)?.let { add(it) }
            RoomPhase.STANDINGS -> add(revealState())
            else -> Unit
        }
    }

    /** Snapshot con una versione "pubblica" della stanza: nessuna risposta corretta. */
    private fun snapshot(): ServerState.Snapshot =
        ServerState.Snapshot(phase = phase, room = publicRoom(), standings = standings)

    private fun publicRoom(): RoomDefinition {
        val q = quiz.copy(
            questions = quiz.questions.map { question ->
                question.copy(options = question.options.map { it.copy(correct = false) })
            },
        )
        return room.copy(mode = q)
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
            total = quiz.questions.size,
            timerSeconds = quiz.answerTimeSeconds,
            locked = locked,
        )
    }

    private fun progressState(): ServerState.Progress =
        ServerState.Progress(turnId = question?.id ?: "none", answered = answers.size, total = players.size)

    private fun playersState(): ServerState.Players =
        ServerState.Players(players.values.map { PlayerInfo(it.id, it.name, it.score) })

    private fun revealState(): ServerState.Reveal =
        ServerState.Reveal(turnId = question?.id ?: "none", standings = standings, correctOptionIds = lastCorrect)

    private fun computeStandings(): List<Standing> =
        players.values
            .sortedByDescending { it.score }
            .mapIndexed { i, p -> Standing(competitorId = p.id, points = p.score, rank = i + 1) }

    // ---- Utilità ------------------------------------------------------------

    private suspend fun broadcast(state: ServerState) {
        val text = encode(state)
        for (c in connections.values) {
            runCatching { c.send(text) }
        }
    }

    private fun encode(state: ServerState): String =
        EngineJson.encodeToString(ServerState.serializer(), state)
}
