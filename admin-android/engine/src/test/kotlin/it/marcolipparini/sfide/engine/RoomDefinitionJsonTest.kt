package it.marcolipparini.sfide.engine

import it.marcolipparini.sfide.engine.model.GameModeConfig
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.samples.SampleRooms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomDefinitionJsonTest {

    @Test
    fun `quiz room round-trips through JSON`() {
        val room = SampleRooms.quizTournament()
        val json = EngineJson.encodeToString(RoomDefinition.serializer(), room)
        val decoded = EngineJson.decodeFromString(RoomDefinition.serializer(), json)
        assertEquals(room, decoded)
        assertTrue(decoded.mode is GameModeConfig.Quiz)
    }

    @Test
    fun `voting room preserves scoring rules through JSON`() {
        val room = SampleRooms.cookingVoting()
        val json = EngineJson.encodeToString(RoomDefinition.serializer(), room)
        val decoded = EngineJson.decodeFromString(RoomDefinition.serializer(), json)
        assertEquals(room.scoring, decoded.scoring)
        assertTrue(decoded.mode is GameModeConfig.Voting)
    }

    @Test
    fun `sealed mode uses the type discriminator`() {
        val room = SampleRooms.quizTournament()
        val json = EngineJson.encodeToString(RoomDefinition.serializer(), room)
        assertTrue(json.contains("\"type\":\"quiz\""), "atteso il discriminatore di tipo 'quiz'")
    }
}
