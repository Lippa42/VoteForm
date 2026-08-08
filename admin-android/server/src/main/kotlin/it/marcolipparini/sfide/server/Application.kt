package it.marcolipparini.sfide.server

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import it.marcolipparini.sfide.engine.samples.SampleRooms
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

const val DEFAULT_PORT = 8080

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    printBanner(port)
    embeddedServer(CIO, port = port, host = "0.0.0.0") { sfideModule(port = port) }.start(wait = true)
}

/**
 * Modulo Ktor con l'API della stanza (WebSocket, regia, QR). Il serving dei client
 * web statici è **iniettabile** ([staticRoutes]) così lo stesso modulo serve da
 * filesystem sul desktop e dagli asset sull'app Android, senza duplicare la logica.
 */
fun Application.sfideModule(
    session: QuizSession = QuizSession(SampleRooms.quizTournament()),
    port: Int = DEFAULT_PORT,
    staticRoutes: Route.() -> Unit = { fileStatic() },
) {
    install(WebSockets)
    routing {
        apiRoutes(session, port)
        get("/") { call.respondRedirect("/viewer/index.html") }
        staticRoutes()
    }
}

/** Rotte comuni a ogni host (desktop o Android). */
fun Route.apiRoutes(session: QuizSession, port: Int = DEFAULT_PORT) {
    webSocket("/ws") {
        val conn = Connection(UUID.randomUUID().toString()) { msg -> send(Frame.Text(msg)) }
        session.addConnection(conn)
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) session.onIntent(conn, frame.readText())
            }
        } finally {
            session.removeConnection(conn.id)
        }
    }

    post("/admin/next") { session.next(); call.respondText("ok") }
    post("/admin/lock") { session.lock(); call.respondText("ok") }
    post("/admin/reveal") { session.reveal(); call.respondText("ok") }
    get("/admin") { call.respondText(adminHtml(), ContentType.Text.Html) }

    get("/qr") {
        val url = call.request.queryParameters["url"] ?: joinUrl(port)
        call.respondText(QrSvg.svg(url), ContentType.Image.SVG)
    }
    get("/join-url") { call.respondText(joinUrl(port)) }
}

/** Serving statico da filesystem (desktop): cerca la cartella `web` del repo. */
private fun Route.fileStatic() {
    val webDir = resolveWebDir() ?: return
    staticFiles("/", webDir)
}

/** URL che gli spettatori aprono (usato anche per il QR): punta all'IP di LAN. */
fun joinUrl(port: Int = DEFAULT_PORT): String =
    "http://${localIp()}:$port/spectator/index.html"

private fun printBanner(port: Int) {
    val ip = localIp()
    println(
        """
        |
        |  ┌───────────────────────────────────────────────┐
        |   Sfide · host in ascolto
        |   Visualizzatore (TV) : http://$ip:$port/viewer/index.html
        |   Spettatore (join)   : http://$ip:$port/spectator/index.html
        |   Regia (admin)       : http://$ip:$port/admin
        |  └───────────────────────────────────────────────┘
        |
        """.trimMargin(),
    )
}

/** Primo indirizzo IPv4 di rete locale, per far raggiungere l'host dai telefoni. */
fun localIp(): String = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull() ?: "127.0.0.1"

/** Trova la cartella `web` (client statici) partendo da posizioni plausibili. */
fun resolveWebDir(): File? {
    System.getenv("SFIDE_WEB_DIR")?.let { File(it) }?.takeIf { it.hasWeb() }?.let { return it }
    val here = File(System.getProperty("user.dir"))
    val candidates = listOf(
        File(here, "web"),
        File(here, "../web"),
        File(here, "../../web"),
        File(here, "../../../web"),
    )
    return candidates.firstOrNull { it.hasWeb() }?.canonicalFile
}

private fun File.hasWeb(): Boolean = File(this, "viewer/index.html").exists()

private fun adminHtml(): String = """
    <!doctype html><html lang="it"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Regia — Sfide</title>
    <style>
      body{font-family:system-ui,sans-serif;background:#0f1117;color:#eceef5;margin:0;
           display:flex;flex-direction:column;gap:16px;align-items:center;justify-content:center;height:100vh}
      h1{font-size:1.2rem;margin:0;color:#ff5a63;letter-spacing:.02em}
      button{font-size:1.1rem;padding:14px 22px;border-radius:12px;border:1px solid #373c4c;
             background:#1e212b;color:#eceef5;cursor:pointer;min-width:220px}
      button:hover{border-color:#ff5a63}
      #log{font-family:ui-monospace,monospace;font-size:.8rem;color:#767c90;min-height:1.2em}
    </style></head><body>
    <h1>REGIA · controlli</h1>
    <button onclick="hit('next')">▶ Prossima domanda</button>
    <button onclick="hit('lock')">🔒 Chiudi risposte</button>
    <button onclick="hit('reveal')">🎉 Svela risultati</button>
    <div id="log"></div>
    <script>
      async function hit(a){
        const r = await fetch('/admin/'+a, {method:'POST'});
        document.getElementById('log').textContent = a + ' → ' + (await r.text());
      }
    </script></body></html>
""".trimIndent()
