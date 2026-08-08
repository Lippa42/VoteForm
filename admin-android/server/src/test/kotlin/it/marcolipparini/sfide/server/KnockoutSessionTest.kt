package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.ServerState
import it.marcolipparini.sfide.engine.samples.SampleRooms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class KnockoutSessionTest {

    private class Captured(id: String) {
        val received = mutableListOf<ServerState>()
        val conn = Connection(id) { text ->
            received += EngineJson.decodeFromString(ServerState.serializer(), text)
        }
    }

    private fun join(name: String) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.Join(pin = "5555", name = name, role = ClientRole.SPECTATOR),
    )

    private fun vote(target: String, value: Double) = EngineJson.encodeToString(
        ClientIntent.serializer(),
        ClientIntent.CastVote(turnId = target, targetId = target, values = mapOf("gusto" to value, "presentazione" to value)),
    )

    /**
     * Un votante premia sempre "t1" (10) e penalizza gli altri (2): "t1" deve
     * vincere ogni incontro e risultare campione, indipendentemente dal sorteggio.
     */
    @Test
    fun `il torneo elegge il campione atteso a prescindere dal sorteggio`() = runBlocking {
        val session = KnockoutSession(SampleRooms.cookingKnockout())
        val viewer = Captured("v")
        session.addConnection(viewer.conn)
        val voter = Captured("voter")
        session.addConnection(voter.conn)
        session.onIntent(voter.conn, join("Ada"))

        var guard = 0
        while (session.phase != RoomPhase.FINISHED && guard++ < 30) {
            session.next()
            if (session.phase == RoomPhase.FINISHED) break
            val turn = voter.received.filterIsInstance<ServerState.VoteTurn>().last()
            val value = if (turn.competitorId == "t1") 10.0 else 2.0
            session.onIntent(voter.conn, vote(turn.competitorId, value))
            session.reveal()
        }

        val bracket = viewer.received.filterIsInstance<ServerState.Bracket>().last()
        assertEquals("t1", bracket.champion)
    }
}
