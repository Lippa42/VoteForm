package it.marcolipparini.sfide.app

import android.content.Context
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.samples.SampleRooms
import it.marcolipparini.sfide.server.DEFAULT_PORT
import it.marcolipparini.sfide.server.GameSession
import it.marcolipparini.sfide.server.localIp
import it.marcolipparini.sfide.server.sessionFor
import it.marcolipparini.sfide.server.sfideModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        val gameSession = sessionFor(room)
        session = gameSession
        val assets = context.applicationContext.assets
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            sfideModule(session = gameSession, port = port, staticRoutes = { assetStatic(assets) })
        }.also { it.start(wait = false) }
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
        engine?.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
        engine = null
        session = null
        _state.value = _state.value.copy(running = false)
    }
}
