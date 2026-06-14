package no.mwmai.usm2.engine

import no.mwmai.usm2.Player

/**
 * Club strength used by the match engine: the mean overall rating of the best
 * eleven players. [Player.rating] is the mean of the real attribute bytes, so
 * this is on the same 0-99 scale as a single attribute.
 *
 * Refinement hook: once the attribute-column NAMES are recovered from the EXE
 * (still shown as S1..S10), this can split into attack / defence / midfield
 * lines instead of a single overall, without changing the engine's interface.
 */
object Strength {
    private const val EMPTY_SQUAD_DEFAULT = 35.0

    fun of(squad: List<Player>): Double {
        if (squad.isEmpty()) return EMPTY_SQUAD_DEFAULT
        return squad.map { it.rating.toDouble() }
            .sortedDescending()
            .take(11)
            .average()
    }
}
