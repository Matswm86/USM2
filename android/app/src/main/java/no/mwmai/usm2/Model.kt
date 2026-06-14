package no.mwmai.usm2

import kotlinx.serialization.Serializable

/**
 * Slim data model mirroring the JSON emitted by tools/stage_assets.py.
 *
 * The original game stores 11 skill bytes per player; index 5 is the const-100
 * normaliser. The displayed categories use the real USM2E.EXE labels (Passing /
 * Defending / Attacking / Ball Skills / Fitness/Physical / Goalkeeping); see
 * [Player] for which byte backs each (goalkeeping/defending/attacking confirmed
 * against the real player data, the rest closest-fit).
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
    // Skill-byte indices in `skills` (byte 128 / index 5 is the const-100
    // normaliser). idx0=Goalkeeping, idx1=Defending and idx3=Attacking are
    // confirmed from the USM2E.EXE label set ("Passing / Defending / Attacking /
    // Ball Skills / Fitness/Physical") cross-checked against the real player data
    // (keepers ~98 on idx0, defenders ~78 on idx1, forwards ~74 on idx3). The
    // remaining categories are the closest-fitting bytes; index 10 is a flat
    // hidden trait, not a displayed skill.
    private fun b(i: Int) = skills.getOrElse(i) { 0 }

    val goalkeeping get() = b(0)
    val defending get() = b(1)
    val ballSkills get() = b(2)
    val attacking get() = b(3)
    /** Fitness/Physical = mean of the pace / strength / stamina bytes. */
    val fitness get() = (b(4) + b(6) + b(9)) / 3
    val passing get() = b(7)

    val isGoalkeeper get() = goalkeeping > 55

    /** Named rating categories shown on the player screen (real USM2 labels). */
    val ratings: List<Pair<String, Int>>
        get() = if (isGoalkeeper) {
            listOf("Goalkeeping" to goalkeeping, "Defending" to defending, "Passing" to passing, "Fitness/Physical" to fitness)
        } else {
            listOf("Attacking" to attacking, "Defending" to defending, "Passing" to passing, "Ball Skills" to ballSkills, "Fitness/Physical" to fitness)
        }

    /** Overall, role-weighted so keepers aren't judged on outfield skills. */
    val rating: Int
        get() = if (isGoalkeeper) {
            (goalkeeping * 0.6 + fitness * 0.2 + defending * 0.2).toInt()
        } else {
            ratings.sumOf { it.second } / ratings.size
        }
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
