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
 * Frozen strengths for one club, captured once at career start and reused for
 * every later season (no aging/transfers yet, so they never change). [overall]
 * drives sorting; [attack]/[defence] drive the goals model. All 0-99.
 */
@Serializable
data class ClubStrength(val overall: Double, val attack: Double, val defence: Double)

/**
 * One tier of the managed group's league pyramid. [division] is the original
 * USM2 division byte (0 = top flight); [clubIds] is the CURRENT membership,
 * which promotion/relegation rewrites at each [Career.rolloverSeason].
 */
@Serializable
data class Tier(val division: Int, val divisionName: String, val clubIds: List<String>)

/**
 * A multi-season management career. Fully self-contained and serializable: every
 * club's strength is frozen at career start ([clubStrengths]), and the whole
 * group [pyramid] travels in the save, so advancing a season OR rolling it over
 * (promotion/relegation) needs no reference to [no.mwmai.usm2.GameData]. All
 * mutation returns a new [Career] (the ViewModel persists each result).
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
    // Attack/defence lines per club (parallel to clubIds), frozen at season start.
    // Empty on saves made before the attack/defence split -> [playNextRound] then
    // falls back to the legacy single-strength model.
    val attackStrengths: List<Double> = emptyList(),
    val defenceStrengths: List<Double> = emptyList(),
    /** Division mean(attack) - mean(defence), centres the goals model. */
    val leagueGap: Double = 0.0,
    // ---- season rollover (promotion/relegation) ----
    // Frozen strengths for every club across the managed group's pyramid. Empty
    // on pre-rollover saves -> [rolloverSeason] is then a no-op.
    val clubStrengths: Map<String, ClubStrength> = emptyMap(),
    /** The whole group pyramid, tier 0 (top flight) first. Rewritten each rollover. */
    val pyramid: List<Tier> = emptyList(),
    /** Clubs exchanged between adjacent tiers at season end (top K up, bottom K down). */
    val promotionSlots: Int = 3,
) {
    val totalRounds: Int get() = (fixtures.maxOfOrNull { it.round } ?: -1) + 1

    val managedIndex: Int get() = clubIds.indexOf(managedClubId)

    /** Lowest round that still has an unplayed fixture; == [totalRounds] if done. */
    val nextRound: Int
        get() = fixtures.filter { !it.played }.minOfOrNull { it.round } ?: totalRounds

    val seasonComplete: Boolean get() = nextRound >= totalRounds

    /** Index of the managed division within [pyramid]; -1 on pre-rollover saves. */
    val activeTierIndex: Int get() = pyramid.indexOfFirst { it.division == division }

    /** True when the active division has a higher tier to be promoted into. */
    val hasPromotion: Boolean get() = activeTierIndex > 0

    /** True when the active division has a lower tier to be relegated into. */
    val hasRelegation: Boolean get() = activeTierIndex in 0 until pyramid.lastIndex

    /** The managed club's next unplayed fixture, or null at season end. */
    fun nextFixtureForManaged(): Fixture? =
        fixtures.filter { !it.played && (it.home == managedIndex || it.away == managedIndex) }
            .minByOrNull { it.round }

    /** The managed club's most recently played fixture, or null before kickoff. */
    fun lastFixtureForManaged(): Fixture? =
        fixtures.filter { it.played && (it.home == managedIndex || it.away == managedIndex) }
            .maxByOrNull { it.round }

    /** 1-based final league position of the managed club (use at season end). */
    fun managedFinalRank(): Int =
        Standings.compute(clubIds.size, fixtures)
            .indexOfFirst { it.clubIndex == managedIndex }
            .let { if (it < 0) 0 else it + 1 }

    /** Simulates every fixture in [nextRound] and returns the updated career. */
    fun playNextRound(): Career {
        if (seasonComplete) return this
        val round = nextRound
        val splitReady = attackStrengths.size == clubIds.size && defenceStrengths.size == clubIds.size
        val updated = fixtures.map { f ->
            if (f.round == round && !f.played) {
                val seed = fixtureSeed(round, f)
                val r = if (splitReady) {
                    Sim.play(
                        attackStrengths[f.home], defenceStrengths[f.home],
                        attackStrengths[f.away], defenceStrengths[f.away],
                        leagueGap, seed,
                    )
                } else {
                    Sim.playOverall(strengths[f.home], strengths[f.away], seed)
                }
                f.copy(homeGoals = r[0], awayGoals = r[1])
            } else {
                f
            }
        }
        return copy(fixtures = updated)
    }

    /**
     * Promotion/relegation across the whole [pyramid], then a fresh season for the
     * (possibly new) managed division. The managed tier's outcome uses the real
     * played table; every other tier is settled by a deterministic full-season
     * simulation from the frozen strengths. A no-op before the season is complete,
     * or for pre-rollover saves (empty [pyramid]).
     */
    fun rolloverSeason(): Career {
        if (!seasonComplete || pyramid.isEmpty() || clubStrengths.isEmpty()) return this
        val n = pyramid.size

        // 1. End-of-season order per tier (managed = real results; others simulated).
        val orderedByTier: List<List<String>> = pyramid.map { tier ->
            if (tier.division == division) {
                Standings.compute(clubIds.size, fixtures).map { clubIds[it.clubIndex] }
            } else {
                simulateTierOrder(tier.clubIds, clubStrengths, rolloverSeed(tier.division))
            }
        }

        // 2. Promotion (top K, move up a tier) and relegation (bottom K, move down),
        //    computed simultaneously off the pre-move orders.
        val promoted = Array(n) { emptyList<String>() }
        val relegated = Array(n) { emptyList<String>() }
        for (t in 0 until n) {
            val order = orderedByTier[t]
            val k = promotionSlots.coerceAtMost(order.size / 2)
            if (t > 0 && k > 0) promoted[t] = order.take(k)
            if (t < n - 1 && k > 0) relegated[t] = order.takeLast(k)
        }

        // 3. Rebuild each tier: who stayed + who came up from below + who came down from above.
        val newTiers = pyramid.mapIndexed { t, tier ->
            val moved = (promoted[t] + relegated[t]).toSet()
            val stayed = orderedByTier[t].filterNot { it in moved }
            val cameUp = if (t < n - 1) promoted[t + 1] else emptyList()
            val cameDown = if (t > 0) relegated[t - 1] else emptyList()
            tier.copy(clubIds = (stayed + cameUp + cameDown).sorted())
        }

        // 4. The managed club's new tier defines next season's active division.
        val newActive = newTiers.firstOrNull { managedClubId in it.clubIds }
            ?: newTiers[activeTierIndex.coerceIn(0, n - 1)]
        fun str(id: String) = clubStrengths[id] ?: ClubStrength(35.0, 35.0, 35.0)
        val newClubIds = newActive.clubIds
        val attack = newClubIds.map { str(it).attack }
        val defence = newClubIds.map { str(it).defence }
        val newSeed = rolloverSeed(0xFFFF)

        return copy(
            division = newActive.division,
            divisionName = newActive.divisionName,
            clubIds = newClubIds,
            strengths = newClubIds.map { str(it).overall },
            attackStrengths = attack,
            defenceStrengths = defence,
            leagueGap = attack.average() - defence.average(),
            seasonSeed = newSeed,
            fixtures = Schedule.season(newClubIds.size, newSeed),
            season = season + 1,
            pyramid = newTiers,
        )
    }

    private fun fixtureSeed(round: Int, f: Fixture): Long =
        seasonSeed * 1_000_003L + round * 9176L + f.home * 131L + f.away

    /** Deterministic, distinct seed per tier and season for the rollover sims. */
    private fun rolloverSeed(salt: Int): Long =
        seasonSeed * 1_000_003L xor (salt.toLong() * 0x100000001B3L) xor (season.toLong() shl 21)
}

/**
 * Settles a non-managed tier by simulating a full deterministic season from the
 * frozen strengths and returning its final club order (champions first). Reuses
 * the live [Schedule]/[Sim]/[Standings] so it matches what the player would see.
 */
private fun simulateTierOrder(
    tierClubIds: List<String>,
    strengths: Map<String, ClubStrength>,
    seed: Long,
): List<String> {
    val n = tierClubIds.size
    if (n < 2) return tierClubIds
    val st = tierClubIds.map { strengths[it] ?: ClubStrength(35.0, 35.0, 35.0) }
    val attack = st.map { it.attack }
    val defence = st.map { it.defence }
    val gap = attack.average() - defence.average()
    val played = Schedule.season(n, seed).map { f ->
        val fseed = seed * 1_000_003L + f.round * 9176L + f.home * 131L + f.away
        val r = Sim.play(attack[f.home], defence[f.home], attack[f.away], defence[f.away], gap, fseed)
        f.copy(homeGoals = r[0], awayGoals = r[1])
    }
    return Standings.compute(n, played).map { tierClubIds[it.clubIndex] }
}
