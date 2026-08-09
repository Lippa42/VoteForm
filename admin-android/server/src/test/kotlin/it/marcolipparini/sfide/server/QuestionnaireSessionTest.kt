package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.ServerState
import it.marcolipparini.sfide.engine.samples.SampleRooms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestionnaireSessionTest {

    private class Captured(id: String) {
        val received = mutableListOf<ServerState>()
        val conn = Connection(id) { text ->
            received += EngineJson.decodeFromString(ServerState.serializer(), text)
        }
    }

    private fun join(name: String) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.Join(pin = "2020", name = name, role = ClientRole.SPECTATOR),
    )

    private fun answer(turnId: String, options: List<String>) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.SubmitAnswer(turnId = turnId, optionIds = options),
    )

    @Test
    fun `il questionario aggrega la distribuzione delle risposte`() = runBlocking {
        val session = QuestionnaireSession(SampleRooms.quickSurvey())
        val viewer = Captured("v")
        session.addConnection(viewer.conn)
        val a = Captured("a"); session.addConnection(a.conn); session.onIntent(a.conn, join("Ada"))
        val b = Captured("b"); session.addConnection(b.conn); session.onIntent(b.conn, join("Bea"))

        session.next()
        session.onIntent(a.conn, answer("q1", listOf("a"))) // Margherita
        session.onIntent(b.conn, answer("q1", listOf("a"))) // Margherita
        session.reveal()

        val reveal = viewer.received.filterIsInstance<ServerState.Reveal>().last()
        assertEquals(2, reveal.distribution["a"])
        assertEquals(0, reveal.distribution["b"])
    }
}
