package it.marcolipparini.sfide.desktop

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.server.DEFAULT_PORT
import it.marcolipparini.sfide.server.GameSession
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
    val title: String = "",
)

/**
 * Ciclo di vita del server embedded sul desktop: avvia lo stesso `:server` (Ktor
 * CIO) che gira sull'app Android, servendo i client web dal filesystem. Salva lo
 * storico a fine partita **e** allo stop (guardia anti-doppione), come su Android.
 */
object DesktopHost {

    private var engine: ApplicationEngine? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var store: DesktopStore? = null

    @Volatile
    private var resultSaved = false

    /** URL WebSocket del relay per spettatori da remoto (vuoto = solo LAN). */
    @Volatile
    var relayUrl: String? = null

    var session: GameSession? = null
        private set

    private val _state = MutableStateFlow(HostInfo())
    val state: StateFlow<HostInfo> = _state.asStateFlow()

    fun start(store: DesktopStore, room: RoomDefinition, port: Int = DEFAULT_PORT) {
        if (engine != null) return
        resultSaved = false
        this.store = store
        val gameSession = sessionFor(room)
        session = gameSession
        gameSession.onFinish = { result -> persistResult(result) }
        val resolver = RoomAssetResolver(room)
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            sfideModule(session = gameSession, port = port, assets = resolver)
        }.also { it.start(wait = false) }
        relayUrl?.takeIf { it.isNotBlank() }?.let { url ->
            RelayHost(gameSession, url.trim(), room.meta.pin, resolver).start(ioScope)
        }
        _state.value = HostInfo(
            running = true,
            ip = localIp(),
            port = port,
            pin = room.meta.pin,
            mode = modeLabel(room),
            title = room.meta.title,
        )
    }

    fun stop() {
        session?.snapshotResult()?.let { persistResult(it) }
        engine?.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
        engine = null
        session = null
        store = null
        _state.value = _state.value.copy(running = false)
    }

    private fun persistResult(result: MatchResult) {
        if (resultSaved) return
        resultSaved = true
        val target = store ?: return
        ioScope.launch { runCatching { target.saveResult(result) } }
    }

    private fun modeLabel(room: RoomDefinition): String = when {
        room.timeline.isNotEmpty() -> "Timeline"
        room.mode is GameModeConfig.Quiz -> "Quiz"
        room.mode is GameModeConfig.Voting -> "Sfida a voti"
        else -> "Questionario"
    }
}
