package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.model.AnswerOption
import it.marcolipparini.sfide.engine.model.CompetitorKind
import it.marcolipparini.sfide.engine.model.FormatConfig
import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.ParticipantsConfig
import it.marcolipparini.sfide.engine.model.Question
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.model.RoomMeta
import it.marcolipparini.sfide.engine.model.ScoringRules
import it.marcolipparini.sfide.engine.phase.RoomPhase
import it.marcolipparini.sfide.engine.protocol.ClientIntent
import it.marcolipparini.sfide.engine.protocol.ClientRole
import it.marcolipparini.sfide.engine.protocol.ServerState
import it.marcolipparini.sfide.engine.samples.SampleRooms
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuizSessionTest {

    /** Cattura i messaggi inviati a un client, come farebbe un vero WebSocket. */
    private class Captured(id: String, val role: ClientRole) {
        val received = mutableListOf<ServerState>()
        val conn = Connection(id) { text ->
            received += EngineJson.decodeFromString(ServerState.serializer(), text)
        }.also { it.role = role }
    }

    private fun intent(i: ClientIntent) = EngineJson.encodeToString(ClientIntent.serializer(), i)

    @Test
    fun `spettatore che risponde giusto segna punti e il visualizzatore vede il reveal`() = runBlocking {
        val session = QuizSession(SampleRooms.quizTournament())

        val viewer = Captured("v", ClientRole.VIEWER)
        session.addConnection(viewer.conn)

        val spec = Captured("s", ClientRole.SPECTATOR)
        session.addConnection(spec.conn)
        session.onIntent(spec.conn, intent(ClientIntent.Join(pin = "4291", name = "Ada", role = ClientRole.SPECTATOR)))

        // Il visualizzatore riceve la lista giocatori con Ada.
        val players = viewer.received.filterIsInstance<ServerState.Players>().last()
        assertTrue(players.players.any { it.name == "Ada" }, "Ada dovrebbe comparire tra i giocatori")

        // Avvia la prima domanda: gli spettatori NON ricevono la risposta corretta.
        session.next()
        val turn = spec.received.filterIsInstance<ServerState.Turn>().last()
        assertEquals("q1", turn.turnId)
        assertTrue(turn.prompt!!.options.all { it.text.isNotBlank() })

        // Ada risponde "Canberra" (opzione corretta = "b").
        session.onIntent(spec.conn, intent(ClientIntent.SubmitAnswer(turnId = "q1", optionIds = listOf("b"))))

        // Il visualizzatore vede l'avanzamento delle risposte.
        val progress = viewer.received.filterIsInstance<ServerState.Progress>().last()
        assertEquals(1, progress.answered)

        // L'admin svela: reveal con risposta corretta e classifica aggiornata.
        session.reveal()
        val reveal = viewer.received.filterIsInstance<ServerState.Reveal>().last()
        assertEquals(listOf("b"), reveal.correctOptionIds)
        assertEquals(100.0, reveal.standings.first { it.competitorId == "s" }.points)
    }

    @Test
    fun `risposta sbagliata non assegna punti`() = runBlocking {
        val session = QuizSession(SampleRooms.quizTournament())
        val spec = Captured("s", ClientRole.SPECTATOR)
        session.addConnection(spec.conn)
        session.onIntent(spec.conn, intent(ClientIntent.Join(pin = "4291", name = "Bob", role = ClientRole.SPECTATOR)))

        session.next()
        session.onIntent(spec.conn, intent(ClientIntent.SubmitAnswer(turnId = "q1", optionIds = listOf("a")))) // Sydney
        session.reveal()

        val reveal = spec.received.filterIsInstance<ServerState.Reveal>().last()
        assertEquals(0.0, reveal.standings.first { it.competitorId == "s" }.points)
    }

    @Test
    fun `il PIN errato non registra lo spettatore`() = runBlocking {
        val session = QuizSession(SampleRooms.quizTournament())
        val spec = Captured("s", ClientRole.SPECTATOR)
        session.addConnection(spec.conn)
        session.onIntent(spec.conn, intent(ClientIntent.Join(pin = "0000", name = "Eve", role = ClientRole.SPECTATOR)))

        session.next()
        session.onIntent(spec.conn, intent(ClientIntent.SubmitAnswer(turnId = "q1", optionIds = listOf("b"))))
        session.reveal()

        val reveal = spec.received.filterIsInstance<ServerState.Reveal>().last()
        assertTrue(reveal.standings.none { it.competitorId == "s" }, "Eve non doveva entrare col PIN errato")
    }

    @Test
    fun `snapshotResult salva la classifica anche se la partita non arriva in fondo`() = runBlocking {
        val session = QuizSession(SampleRooms.quizTournament())
        // Prima di iniziare non c'è nulla da salvare.
        assertEquals(null, session.snapshotResult())

        val spec = Captured("s", ClientRole.SPECTATOR)
        session.addConnection(spec.conn)
        session.onIntent(spec.conn, intent(ClientIntent.Join(pin = "4291", name = "Ada", role = ClientRole.SPECTATOR)))

        session.next()
        session.onIntent(spec.conn, intent(ClientIntent.SubmitAnswer(turnId = "q1", optionIds = listOf("b"))))
        session.reveal()

        // L'host ferma la stanza senza premere "Avanti" fino alla fine: il risultato
        // corrente deve comunque essere disponibile per lo storico.
        val snap = session.snapshotResult()
        assertTrue(snap != null, "snapshotResult non deve essere null a partita iniziata")
        assertEquals("Ada", snap!!.winnerLabel)
        assertEquals(100.0, snap.finalStandings.first { it.competitorId == "s" }.points)
    }

    @Test
    fun `il timer chiude automaticamente le risposte allo scadere`() = runBlocking {
        val room = RoomDefinition(
            meta = RoomMeta(title = "Timer", pin = "1"),
            mode = GameModeConfig.Quiz(
                questions = listOf(
                    Question(id = "q1", text = "?", options = listOf(AnswerOption("a", "A", correct = true))),
                ),
                answerTimeSeconds = 1,
            ),
            participants = ParticipantsConfig(CompetitorKind.INDIVIDUALS, emptyList()),
            format = FormatConfig.AllVsAll(),
            scoring = ScoringRules(),
        )
        val session = QuizSession(room)
        val viewer = Captured("v", ClientRole.VIEWER)
        session.addConnection(viewer.conn)

        session.next()
        assertEquals(RoomPhase.INPUT, session.phase)

        delay(1300) // oltre il tempo di risposta (1s)
        assertEquals(RoomPhase.LOCKED, session.phase)
    }
}
