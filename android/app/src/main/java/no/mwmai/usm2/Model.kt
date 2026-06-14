package no.mwmai.usm2

import kotlinx.serialization.Serializable

/**
 * Slim data model mirroring the JSON emitted by tools/stage_assets.py.
 *
 * The original game stores 11 skill bytes per player; index 5 is a constant
 * normaliser (100), not an attribute, so it is excluded from [Player.rating] and
 * from display. The per-column attribute names (tackle / pass / shoot / ...) are
 * still being recovered from the EXE, so skills are shown positionally for now.
 */
@Serializable
data class Club(
    val id: String,
    val name: String,
    val short: String = "",
    val manager: String = "",
    val stadium: String = "",
    val group: String,
    val division: Int,
    val divisionName: String,
)

@Serializable
data class Player(
    val name: String,
    val age: Int,
    val key: Boolean = false,
    val skills: List<Int> = emptyList(),
    val club: String? = null,
    val league: String = "",
) {
    /** Index of the constant normaliser byte that is not a real attribute. */
    private val normaliserIndex get() = 5

    /** Ordered attribute values with the normaliser byte removed. */
    val attributes: List<Int>
        get() = skills.filterIndexed { i, _ -> i != normaliserIndex }

    /** Heuristic overall = mean of the real attribute bytes (0-99 scale). */
    val rating: Int
        get() = attributes.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size } ?: 0
}

/**
 * Fully-loaded world database plus the lookups the UI needs. Built once on the
 * IO dispatcher (see [GameRepository]); screens read it from the ViewModel.
 */
class GameData(
    val clubs: List<Club>,
    val players: List<Player>,
    /** 18 real formations from FORM.DAT: each = 11 [x,y] pairs, normalised [0,1]. */
    val formations: List<List<List<Double>>> = emptyList(),
) {
    val clubsById: Map<String, Club> = clubs.associateBy { it.id }

    /** Players grouped by their club id; free agents (club == null) excluded. */
    val playersByClub: Map<String, List<Player>> =
        players.filter { it.club != null }
            .groupBy { it.club!! }
            .mapValues { (_, squad) -> squad.sortedByDescending { it.rating } }

    /** Stable index for routing to a single player. */
    val playersIndexed: List<IndexedValue<Player>> = players.withIndex().toList()

    /** UI groupings in display order, each its own browsable "league". */
    val groups: List<String> = clubs.map { it.group }.distinct().sortedBy { groupOrder(it) }

    fun clubsInGroup(group: String): List<Club> =
        clubs.filter { it.group == group }.sortedWith(compareBy({ it.division }, { it.name }))

    fun squad(clubId: String): List<Player> = playersByClub[clubId].orEmpty()

    companion object {
        private val ORDER = listOf("England", "Europe", "France", "Germany")
        fun groupOrder(g: String): Int = ORDER.indexOf(g).let { if (it < 0) ORDER.size else it }
    }
}
