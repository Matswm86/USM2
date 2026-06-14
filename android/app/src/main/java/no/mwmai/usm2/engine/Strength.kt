package no.mwmai.usm2.engine

import no.mwmai.usm2.Player

/**
 * Club strengths for the match engine, from the real attribute bytes
 * ([Player.attacking] = idx3, [Player.defending] = idx1, [Player.goalkeeping] =
 * idx0 — confirmed against the player data). All on the 0-99 attribute scale.
 *
 * [of] (best-XI mean overall) is kept for the legacy single-strength model and
 * for sorting; new careers use [attack] + [defence].
 */
object Strength {
    private const val EMPTY_SQUAD_DEFAULT = 35.0

    /** Best-XI mean overall rating. */
    fun of(squad: List<Player>): Double {
        if (squad.isEmpty()) return EMPTY_SQUAD_DEFAULT
        return squad.map { it.rating.toDouble() }.sortedDescending().take(11).average()
    }

    /** Attacking line = mean of the five best attackers' Attacking. */
    fun attack(squad: List<Player>): Double {
        if (squad.isEmpty()) return EMPTY_SQUAD_DEFAULT
        return squad.map { it.attacking.toDouble() }.sortedDescending().take(5).average()
    }

    /** Defensive line = the four best defenders' Defending, plus the best keeper. */
    fun defence(squad: List<Player>): Double {
        if (squad.isEmpty()) return EMPTY_SQUAD_DEFAULT
        val backLine = squad.map { it.defending.toDouble() }.sortedDescending().take(4)
        val keeper = squad.maxOfOrNull { it.goalkeeping.toDouble() } ?: 50.0
        return 0.75 * backLine.average() + 0.25 * keeper
    }
}
