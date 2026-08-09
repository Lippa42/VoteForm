package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.history.MatchResult
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.ServerState
import it.marcolipparini.sfide.engine.samples.SampleRooms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShowSessionTest {

    private class Captured(id: String) {
        val received = mutableListOf<ServerState>()
        val conn = Connection(id) { text ->
            received += EngineJson.decodeFromString(ServerState.serializer(), text)
        }
    }

    private fun join(name: String) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.Join(pin = "9000", name = name, role = ClientRole.SPECTATOR),
    )

    private fun answer(turnId: String, opt: String) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.SubmitAnswer(turnId = turnId, optionIds = listOf(opt)),
    )

    private fun vote(turnId: String, target: String, value: Double) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.CastVote(turnId = turnId, targetId = target, values = mapOf("gusto" to value, "pres" to value)),
    )

    private fun joinPin(pin: String, name: String) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.Join(pin = pin, name = name, role = ClientRole.SPECTATOR),
    )

    private fun voteMap(turnId: String, target: String, values: Map<String, Double>) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.CastVote(turnId = turnId, targetId = target, values = values),
    )

    @Test
    fun `la fase Torneo elegge un campione e gli assegna il bonus`() = runBlocking {
        val session = ShowSession(SampleRooms.tournamentShow())
        val viewer = Captured("v"); session.addConnection(viewer.conn)
        val voter = Captured("voter"); session.addConnection(voter.conn)
        session.onIntent(voter.conn, joinPin("7000", "Ada"))

        var guard = 0
        while (session.phase != RoomPhase.FINISHED && guard++ < 40) {
            session.next()
            if (session.phase != RoomPhase.INPUT) continue
            val vt = voter.received.filterIsInstance<ServerState.VoteTurn>().last()
            val value = if (vt.competitorId == "t1") 10.0 else 2.0
            val values = vt.criteria.associate { it.id to value }
            session.onIntent(voter.conn, voteMap(vt.turnId, vt.competitorId, values))
            session.reveal()
        }

        val finalBoard = viewer.received.filterIsInstance<ServerState.ScoreBoard>().last { it.isFinal }
        assertEquals("t1", finalBoard.champion)
        assertEquals(10.0, finalBoard.entries.first { it.id == "t1" }.score) // bonus al campione
    }

    @Test
    fun `la timeline scorre le fasi e chiude con la classifica finale`() = runBlocking {
        val session = ShowSession(SampleRooms.showcase())
        var result: MatchResult? = null
        session.onFinish = { result = it }

        val viewer = Captured("v"); session.addConnection(viewer.conn)
        val voter = Captured("voter"); session.addConnection(voter.conn)
        session.onIntent(voter.conn, join("Ada"))

        var guard = 0
        while (session.phase != RoomPhase.FINISHED && guard++ < 40) {
            session.next()
            if (session.phase != RoomPhase.INPUT) continue // fase di presentazione: si avanza

            val vIdx = voter.received.indexOfLast { it is ServerState.VoteTurn && it.phase == RoomPhase.INPUT }
            val tIdx = voter.received.indexOfLast { it is ServerState.Turn && it.phase == RoomPhase.INPUT }
            if (vIdx > tIdx) {
                val vt = voter.received[vIdx] as ServerState.VoteTurn
                val value = if (vt.competitorId == "t1") 10.0 else 2.0
                session.onIntent(voter.conn, vote(vt.turnId, vt.competitorId, value))
            } else {
                val tn = voter.received[tIdx] as ServerState.Turn
                session.onIntent(voter.conn, answer(tn.turnId, "b")) // Roma
            }
            session.reveal()
        }

        // Ha attraversato un titolo, una classifica e una schermata finale.
        assertTrue(viewer.received.any { it is ServerState.Screen && it.kind == "title" }, "atteso un titolo")
        assertTrue(viewer.received.any { it is ServerState.ScoreBoard }, "attesa una classifica")

        assertEquals(RoomPhase.FINISHED, session.phase)
        assertNotNull(result)
        // La votazione ha premiato "Rossi" (t1): campione della serata.
        assertEquals("Rossi", result!!.winnerLabel)
        val finalBoard = viewer.received.filterIsInstance<ServerState.ScoreBoard>().last { it.isFinal }
        assertEquals("t1", finalBoard.champion)
    }
}
