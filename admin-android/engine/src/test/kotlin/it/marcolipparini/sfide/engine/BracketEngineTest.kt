package it.marcolipparini.sfide.engine

import it.marcolipparini.sfide.engine.bracket.BracketEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BracketEngineTest {

    @Test
    fun `torneo a quattro produce tre incontri e un campione`() {
        var b = BracketEngine.create(listOf("a", "b", "c", "d"))
        assertEquals(3, b.matches.size)
        assertNull(b.champion)
        assertEquals("r1m0", BracketEngine.nextMatch(b)!!.id)

        b = BracketEngine.recordWinner(b, "r1m0", "a")
        b = BracketEngine.recordWinner(b, "r1m1", "c")
        val final = BracketEngine.nextMatch(b)!!
        assertEquals("r2m0", final.id)
        assertEquals("a", final.slotA)
        assertEquals("c", final.slotB)

        b = BracketEngine.recordWinner(b, "r2m0", "a")
        assertEquals("a", b.champion)
        assertNull(BracketEngine.nextMatch(b))
    }

    @Test
    fun `il bye avanza automaticamente il concorrente solo`() {
        val b = BracketEngine.create(listOf("a", "b", "c"))
        // Con 3 concorrenti il tabellone è a 4 slot: "c" ha un bye e passa già.
        assertEquals("c", b.matches.first { it.id == "r1m1" }.winner)
        assertEquals("r1m0", BracketEngine.nextMatch(b)!!.id) // "a" vs "b" ancora da giocare
    }
}
