package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.ServerState
import it.marcolipparini.sfide.engine.samples.SampleRooms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class VotingSessionTest {

    private class Captured(id: String) {
        val received = mutableListOf<ServerState>()
        val conn = Connection(id) { text ->
            received += EngineJson.decodeFromString(ServerState.serializer(), text)
        }
    }

    private fun join(name: String) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.Join(pin = "7788", name = name, role = ClientRole.SPECTATOR),
    )

    private fun vote(turnId: String, target: String, values: Map<String, Double>) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.CastVote(turnId = turnId, targetId = target, values = values),
    )

    private fun flat(v: Double) = mapOf("gusto" to v, "presentazione" to v, "originalita" to v)

    /** Media pesata dei criteri (Gusto ×2) e media tra due votanti. */
    @Test
    fun `la sfida a voti aggrega i criteri pesati e i votanti`() = runBlocking {
        val session = VotingSession(SampleRooms.cookingVoting())
        val a = Captured("a"); session.addConnection(a.conn); session.onIntent(a.conn, join("Ada"))
        val b = Captured("b"); session.addConnection(b.conn); session.onIntent(b.conn, join("Bea"))

        session.next() // prima prova, primo concorrente (p1:t1)

        // Ada: gusto=8, pres=6, orig=10 -> (8*2+6+10)/4 = 8.0
        session.onIntent(a.conn, vote("p1:t1", "t1", mapOf("gusto" to 8.0, "presentazione" to 6.0, "originalita" to 10.0)))
        // Bea: gusto=6, pres=8, orig=4  -> (6*2+8+4)/4 = 6.0
        session.onIntent(b.conn, vote("p1:t1", "t1", mapOf("gusto" to 6.0, "presentazione" to 8.0, "originalita" to 4.0)))

        session.reveal() // media(8.0, 6.0) = 7.0

        val reveal = a.received.filterIsInstance<ServerState.Reveal>().last()
        assertEquals("t1", reveal.subjectId)
        assertEquals(7.0, reveal.subjectScore)
        assertEquals(7.0, reveal.standings.first { it.competitorId == "t1" }.points)
    }

    /** Con trimExtremes attivo e 3 votanti si scartano il più alto e il più basso. */
    @Test
    fun `trimExtremes scarta gli estremi con tre votanti`() = runBlocking {
        val session = VotingSession(SampleRooms.cookingVoting())
        val voters = listOf("x" to 2.0, "y" to 7.0, "z" to 9.0).map { (id, value) ->
            val c = Captured(id)
            session.addConnection(c.conn)
            session.onIntent(c.conn, join(id))
            c to value
        }
        session.next()
        voters.forEach { (c, value) -> session.onIntent(c.conn, vote("p1:t1", "t1", flat(value))) }
        session.reveal() // [2,7,9] -> scarta 2 e 9 -> media(7) = 7.0

        val reveal = voters.first().first.received.filterIsInstance<ServerState.Reveal>().last()
        assertEquals(7.0, reveal.subjectScore)
    }
}
