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
        val active = divisionOf(data, club)
        if (active.size < 2 || active.size > MAX_DIVISION_SIZE) return null

        // The managed group's whole pyramid: every manageable division in the
        // group, top tier (lowest division byte) first. The 112-club Europe pool
        // is excluded by the size gate, so only the real ladder remains.
        val tiers = data.clubs
            .filter { it.group == club.group }
            .groupBy { it.division }
            .filterValues { it.size in 2..MAX_DIVISION_SIZE }
            .toSortedMap()
            .map { (_, clubsInDiv) ->
                val ordered = clubsInDiv.sortedBy { it.name }
                Tier(ordered.first().division, ordered.first().divisionName, ordered.map { it.id })
            }

        // Freeze every pyramid club's strengths once (no aging/transfers yet).
        val clubStrengths = tiers.flatMap { it.clubIds }.associateWith { id ->
            val squad = data.squad(id)
            ClubStrength(Strength.of(squad), Strength.attack(squad), Strength.defence(squad))
        }
        val smallestTier = tiers.minOfOrNull { it.clubIds.size } ?: active.size
        val promotionSlots = maxOf(1, minOf(3, smallestTier / 4))

        // Active-division working set (drives the current season + all the UI).
        val clubIds = active.map { it.id }
        val squads = clubIds.map { data.squad(it) }
        val strengths = squads.map { Strength.of(it) }
        val attack = squads.map { Strength.attack(it) }
        val defence = squads.map { Strength.defence(it) }
        val leagueGap = attack.average() - defence.average()
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
            attackStrengths = attack,
            defenceStrengths = defence,
            leagueGap = leagueGap,
            clubStrengths = clubStrengths,
            pyramid = tiers,
            promotionSlots = promotionSlots,
        )
    }
}
