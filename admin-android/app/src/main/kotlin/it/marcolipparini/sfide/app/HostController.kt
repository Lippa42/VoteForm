package it.marcolipparini.sfide.app

import android.content.Context
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.samples.SampleRooms
import it.marcolipparini.sfide.server.DEFAULT_PORT
import it.marcolipparini.sfide.server.GameSession
import it.marcolipparini.sfide.persistence.SfideStore
import it.marcolipparini.sfide.server.RelayHost
import it.marcolipparini.sfide.server.RoomAssetResolver
import it.marcolipparini.sfide.server.localIp
import it.marcolipparini.sfide.server.sessionFor
import it.marcolipparini.sfide.server.sfideModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HostInfo(
    val running: Boolean = false,
    val ip: String = "—",
    val port: Int = DEFAULT_PORT,
    val pin: String = "—",
    val mode: String = "",
)

/**
 * Governa il ciclo di vita del server embedded sul dispositivo host. È qui che il
 * codice condiviso del modulo `:server` (Ktor + QuizSession) viene avviato dentro
 * l'app Android; i client web vengono serviti dagli asset dell'app.
 */
object HostController {

    private var engine: ApplicationEngine? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Store dello storico (creato all'avvio) e guardia anti-doppio-salvataggio. */
    private var store: SfideStore? = null

    @Volatile
    private var resultSaved = false

    /** URL WebSocket del relay per spettatori da remoto (vuoto = solo LAN). */
    @Volatile
    var relayUrl: String? = null

    /** Sessione attiva: la regia (UI Compose) la usa per far avanzare le fasi. */
    var session: GameSession? = null
        private set

    private val _state = MutableStateFlow(HostInfo())
    val state: StateFlow<HostInfo> = _state.asStateFlow()

    fun start(
        context: Context,
        room: RoomDefinition = SampleRooms.quizTournament(),
        port: Int = DEFAULT_PORT,
    ) {
        if (engine != null) return
        resultSaved = false
        val gameSession = sessionFor(room)
        session = gameSession
        // A fine partita salva il risultato nello storico locale.
        val localStore = SfideStore(context.applicationContext)
        store = localStore
        gameSession.onFinish = { result -> persistResult(result) }
        val webAssets = context.applicationContext.assets
        val assetResolver = RoomAssetResolver(room)
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            sfideModule(session = gameSession, port = port, assets = assetResolver, staticRoutes = { assetStatic(webAssets) })
        }.also { it.start(wait = false) }
        // Modalità remota: l'host si collega anche al relay (in uscita, oltre il NAT).
        relayUrl?.takeIf { it.isNotBlank() }?.let { url ->
            RelayHost(gameSession, url.trim(), room.meta.pin, assetResolver).start(ioScope)
        }
        _state.value = HostInfo(
            running = true,
            ip = localIp(),
            port = port,
            pin = room.meta.pin,
            mode = modeLabel(room),
        )
    }

    private fun modeLabel(room: RoomDefinition): String = when (room.mode) {
        is GameModeConfig.Quiz -> "Quiz"
        is GameModeConfig.Voting -> "Sfida a voti"
        is GameModeConfig.Questionnaire -> "Questionario"
    }

    fun stop() {
        // Salva lo storico anche se la partita non è arrivata all'ultima fase.
        session?.snapshotResult()?.let { persistResult(it) }
        engine?.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
        engine = null
        session = null
        store = null
        _state.value = _state.value.copy(running = false)
    }

    /** Salva il risultato una sola volta per partita (onFinish e stop possono coincidere). */
    private fun persistResult(result: MatchResult) {
        if (resultSaved) return
        resultSaved = true
        val target = store ?: return
        ioScope.launch { target.saveResult(result) }
    }
}
