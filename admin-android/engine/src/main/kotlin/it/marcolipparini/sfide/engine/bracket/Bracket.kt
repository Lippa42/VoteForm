package it.marcolipparini.sfide.engine.bracket

import kotlinx.serialization.Serializable

/** Un incontro del tabellone a eliminazione diretta. */
@Serializable
data class BracketMatch(
    val id: String,
    val round: Int,
    val slotA: String? = null,
    val slotB: String? = null,
    val winner: String? = null,
)

/** Tabellone a eliminazione diretta con l'eventuale campione. */
@Serializable
data class Bracket(
    val matches: List<BracketMatch>,
    val champion: String? = null,
)

/**
 * Logica pura del tabellone a eliminazione diretta: creazione (con bye per numeri
 * non potenza di due), avanzamento del vincitore, prossimo incontro pronto. Niente
 * dipendenze: interamente testabile.
 */
object BracketEngine {

    fun create(competitorIds: List<String>): Bracket {
        val n = competitorIds.size
        if (n == 0) return Bracket(emptyList())
        if (n == 1) return Bracket(emptyList(), champion = competitorIds.first())

        var size = 1
        while (size < n) size *= 2
        val rounds = Integer.numberOfTrailingZeros(size) // log2(size)

        val matches = mutableListOf<BracketMatch>()
        for (m in 0 until size / 2) {
            matches += BracketMatch(
                id = matchId(1, m),
                round = 1,
                slotA = competitorIds.getOrNull(2 * m),
                slotB = competitorIds.getOrNull(2 * m + 1),
            )
        }
        for (r in 2..rounds) {
            val count = size / (1 shl r)
            for (m in 0 until count) matches += BracketMatch(id = matchId(r, m), round = r)
        }

        var bracket = Bracket(matches)
        // Risolve subito i bye del primo turno (un solo concorrente presente).
        bracket.matches.filter { it.round == 1 }.forEach { match ->
            val only = when {
                match.slotA != null && match.slotB == null -> match.slotA
                match.slotB != null && match.slotA == null -> match.slotB
                else -> null
            }
            if (only != null) bracket = recordWinner(bracket, match.id, only)
        }
        return bracket
    }

    fun recordWinner(bracket: Bracket, matchId: String, winnerId: String): Bracket {
        val match = bracket.matches.firstOrNull { it.id == matchId } ?: return bracket
        val rounds = bracket.matches.maxOf { it.round }
        val updated = bracket.matches
            .map { if (it.id == matchId) it.copy(winner = winnerId) else it }
            .toMutableList()

        if (match.round == rounds) {
            return Bracket(updated, champion = winnerId)
        }
        val idx = indexOf(matchId)
        val nextId = matchId(match.round + 1, idx / 2)
        val ni = updated.indexOfFirst { it.id == nextId }
        if (ni >= 0) {
            updated[ni] = if (idx % 2 == 0) updated[ni].copy(slotA = winnerId) else updated[ni].copy(slotB = winnerId)
        }
        return Bracket(updated, bracket.champion)
    }

    /** Primo incontro pronto (entrambi gli slot pieni) ancora da decidere. */
    fun nextMatch(bracket: Bracket): BracketMatch? =
        bracket.matches.firstOrNull { it.winner == null && it.slotA != null && it.slotB != null }

    private fun matchId(round: Int, index: Int): String = "r${round}m$index"
    private fun indexOf(id: String): Int = id.substringAfter("m").toInt()
}
