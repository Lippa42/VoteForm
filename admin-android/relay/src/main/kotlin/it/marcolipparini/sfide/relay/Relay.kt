package it.marcolipparini.sfide.relay

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.protocol.RelayFrame
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Relay WebSocket per spettatori da remoto. Non conosce il gioco: per ogni "stanza"
 * (room code) tiene un host e N client, e inoltra i messaggi tra loro. Host e client
 * si connettono entrambi **in uscita** verso il relay, quindi il NAT non è un
 * problema per nessuno dei due. Serve anche i client web (viewer/spectator).
 */
private class Room {
    @Volatile
    var host: WebSocketSession? = null
    val clients = ConcurrentHashMap<String, WebSocketSession>()
}

private val rooms = ConcurrentHashMap<String, Room>()

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 9090
    println("Relay Sfide in ascolto sulla porta $port")
    embeddedServer(CIO, port = port, host = "0.0.0.0") { relayModule() }.start(wait = true)
}

fun Application.relayModule() {
    install(WebSockets)
    routing {
        webSocket("/host") {
            val code = call.request.queryParameters["room"]
                ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "room mancante"))
            val room = rooms.getOrPut(code) { Room() }
            room.host = this
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val f = decode(frame.readText())
                        if (f is RelayFrame.To) room.clients[f.c]?.send(Frame.Text(f.d))
                    }
                }
            } finally {
                room.host = null
                room.clients.values.forEach { runCatching { it.close() } }
                rooms.remove(code)
            }
        }

        webSocket("/client") {
            val code = call.request.queryParameters["room"]
                ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "room mancante"))
            val room = rooms[code]
            if (room?.host == null) {
                return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "host non disponibile"))
            }
            val cid = UUID.randomUUID().toString()
            room.clients[cid] = this
            room.host?.send(Frame.Text(encode(RelayFrame.Join(cid))))
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        room.host?.send(Frame.Text(encode(RelayFrame.Msg(cid, frame.readText()))))
                    }
                }
            } finally {
                room.clients.remove(cid)
                room.host?.runCatching { send(Frame.Text(encode(RelayFrame.Leave(cid)))) }
            }
        }

        get("/") { call.respondRedirect("/viewer/index.html") }

        resolveWebDir()?.let { staticFiles("/", it) }
    }
}

private fun encode(frame: RelayFrame): String = EngineJson.encodeToString(RelayFrame.serializer(), frame)
private fun decode(text: String): RelayFrame? = runCatching { EngineJson.decodeFromString(RelayFrame.serializer(), text) }.getOrNull()

private fun resolveWebDir(): File? {
    System.getenv("SFIDE_WEB_DIR")?.let { File(it) }?.takeIf { it.hasWeb() }?.let { return it }
    val here = File(System.getProperty("user.dir"))
    return listOf(File(here, "web"), File(here, "../web"), File(here, "../../web"), File(here, "../../../web"))
        .firstOrNull { it.hasWeb() }?.canonicalFile
}

private fun File.hasWeb(): Boolean = File(this, "viewer/index.html").exists()
