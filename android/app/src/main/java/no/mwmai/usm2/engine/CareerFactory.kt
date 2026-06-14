package no.mwmai.usm2.engine

import no.mwmai.usm2.Club
import no.mwmai.usm2.GameData

/** Builds a fresh [Career] for a chosen club out of the loaded world database. */
object CareerFactory {

    /** Max clubs in a manageable division. Excludes the 112-club "International"
     * pool (a transfer/Europe pool, not a real league) while admitting every
     * real pyramid tier (England 20-24, France 18-22, Germany 18). */
    const val MAX_DIVISION_SIZE = 30

    /** Clubs that share [club]'s group and division, in a stable display order. */
    fun divisionOf(data: GameData, club: Club): List<Club> =
        data.clubs
            .filter { it.group == club.group && it.division == club.division }
            .sortedBy { it.name }

    /** True if [club] sits in a division small enough to run a season over. */
    fun isManageable(data: GameData, club: Club): Boolean =
        divisionOf(data, club).size in 2..MAX_DIVISION_SIZE

    fun start(data: GameData, clubId: String, seed: Long): Career? {
        val club = data.clubsById[clubId] ?: return null
        val division = divisionOf(data, club)
        if (division.size < 2 || division.size > MAX_DIVISION_SIZE) return null
        val clubIds = division.map { it.id }
        val strengths = clubIds.map { Strength.of(data.squad(it)) }
        val fixtures = Schedule.season(clubIds.size, seed)
        return Career(
            managedClubId = clubId,
            group = club.group,
            division = club.division,
            divisionName = club.divisionName,
            clubIds = clubIds,
            strengths = strengths,
            seasonSeed = seed,
            fixtures = fixtures,
        )
    }
}
