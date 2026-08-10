package it.marcolipparini.sfide.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.protocol.RelayFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Connette l'host al [relay] come "host" di una stanza: apre una connessione in
 * uscita (quindi il NAT del telefono non è un problema) e mappa ogni client remoto
 * su una [Connection] virtuale, così la sessione di gioco non nota la differenza tra
 * client locali (LAN) e remoti (relay).
 */
class RelayHost(
    private val session: GameSession,
    private val relayWsUrl: String,
    private val roomCode: String,
) {
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val connections = ConcurrentHashMap<String, Connection>()
    private val sendMutex = Mutex()
    private var send: (suspend (String) -> Unit)? = null

    /** Avvia il collegamento al relay con riconnessione automatica. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            val base = relayWsUrl.trimEnd('/')
            while (true) {
                runCatching {
                    client.webSocket("$base/host?room=$roomCode") {
                        send = { text -> sendMutex.withLock { send(Frame.Text(text)) } }
                        for (frame in incoming) {
                            if (frame is Frame.Text) handle(frame.readText())
                        }
                    }
                }
                send = null
                connections.keys.toList().forEach { session.removeConnection(it); connections.remove(it) }
                delay(2000) // riconnessione
            }
        }
    }

    private suspend fun handle(text: String) {
        when (val frame = EngineJson.runCatching { decodeFromString(RelayFrame.serializer(), text) }.getOrNull()) {
            is RelayFrame.Join -> {
                val cid = frame.c
                val conn = Connection(cid) { msg ->
                    send?.invoke(EngineJson.encodeToString(RelayFrame.serializer(), RelayFrame.To(cid, msg)))
                }
                connections[cid] = conn
                session.addConnection(conn)
            }
            is RelayFrame.Msg -> connections[frame.c]?.let { session.onIntent(it, frame.d) }
            is RelayFrame.Leave -> {
                connections.remove(frame.c)
                session.removeConnection(frame.c)
            }
            else -> Unit
        }
    }
}
