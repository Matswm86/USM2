package no.mwmai.usm2.engine

import kotlinx.serialization.Serializable

/**
 * One league fixture. [home]/[away] are indices into [Career.clubIds]. A fixture
 * is unplayed while [homeGoals] is negative.
 */
@Serializable
data class Fixture(
    val round: Int,
    val home: Int,
    val away: Int,
    val homeGoals: Int = -1,
    val awayGoals: Int = -1,
) {
    val played: Boolean get() = homeGoals >= 0
}

/**
 * A single-division management career. Fully self-contained and serializable:
 * club strengths are frozen at season start, so advancing the season needs no
 * reference to the full [no.mwmai.usm2.GameData]. All mutation returns a new
 * [Career] (the ViewModel persists each result).
 */
@Serializable
data class Career(
    val managedClubId: String,
    val group: String,
    val division: Int,
    val divisionName: String,
    val clubIds: List<String>,
    val strengths: List<Double>,
    val seasonSeed: Long,
    val fixtures: List<Fixture>,
    val season: Int = 1,
) {
    val totalRounds: Int get() = (fixtures.maxOfOrNull { it.round } ?: -1) + 1

    val managedIndex: Int get() = clubIds.indexOf(managedClubId)

    /** Lowest round that still has an unplayed fixture; == [totalRounds] if done. */
    val nextRound: Int
        get() = fixtures.filter { !it.played }.minOfOrNull { it.round } ?: totalRounds

    val seasonComplete: Boolean get() = nextRound >= totalRounds

    /** The managed club's next unplayed fixture, or null at season end. */
    fun nextFixtureForManaged(): Fixture? =
        fixtures.filter { !it.played && (it.home == managedIndex || it.away == managedIndex) }
            .minByOrNull { it.round }

    /** The managed club's most recently played fixture, or null before kickoff. */
    fun lastFixtureForManaged(): Fixture? =
        fixtures.filter { it.played && (it.home == managedIndex || it.away == managedIndex) }
            .maxByOrNull { it.round }

    /** Simulates every fixture in [nextRound] and returns the updated career. */
    fun playNextRound(): Career {
        if (seasonComplete) return this
        val round = nextRound
        val updated = fixtures.map { f ->
            if (f.round == round && !f.played) {
                val r = Sim.play(strengths[f.home], strengths[f.away], fixtureSeed(round, f))
                f.copy(homeGoals = r[0], awayGoals = r[1])
            } else {
                f
            }
        }
        return copy(fixtures = updated)
    }

    private fun fixtureSeed(round: Int, f: Fixture): Long =
        seasonSeed * 1_000_003L + round * 9176L + f.home * 131L + f.away
}
