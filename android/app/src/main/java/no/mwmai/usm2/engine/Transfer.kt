package no.mwmai.usm2.engine

import kotlinx.serialization.Serializable

/**
 * One recorded transfer. [playerIndex] is the stable global index into
 * GameData.players (the same id the UI routes on). [toClub] is the club id the
 * player moved to, or null when released (sold out of the managed club to an
 * abstract buyer). [fee] is the £k that changed hands. The career keeps the full
 * list; folding it (last entry per player wins) gives each player's current club.
 */
@Serializable
data class Transfer(val playerIndex: Int, val toClub: String? = null, val fee: Long = 0)

/** Squad-size bounds the transfer market enforces. The smallest real squad in
 * the DB is 13, so a floor of 12 still lets every club sell at least one. */
object TransferLimits {
    const val MIN_SQUAD = 12
    const val MAX_SQUAD = 30
}
