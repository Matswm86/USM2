package no.mwmai.usm2.engine

import kotlinx.serialization.Serializable
import no.mwmai.usm2.GameData
import no.mwmai.usm2.Player

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
 * The managed club's upcoming fixture, pre-played to a result for the match view
 * (see [Career.previewNextManagedMatch]). Transient: never persisted — the real
 * record is written by [Career.playNextRound], which reproduces this exact score.
 */
data class ManagedPreview(
    val homeId: String,
    val awayId: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val seed: Long,
    val homeIsManaged: Boolean,
)

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
    // ---- transfers ----
    /** Transfer kitty in £k. Empty on pre-transfer saves (0) -> nothing affordable
     * until a new career is started; new careers are seeded by [CareerFactory]. */
    val budget: Long = 0,
    /** Every transfer made this career, oldest first. Folded (last per player wins)
     * to give each player's current club; see [currentClubOf] / [squadFor]. */
    val transfers: List<Transfer> = emptyList(),
    // ---- finances (all £k; 0 / defaults on pre-finance saves) ----
    /** Wage bill charged each matchday the club plays; recomputed on every transfer. */
    val wageBillPerMatchK: Long = 0,
    /** Squad value vs division average, frozen at start; scales gate receipts. */
    val clubSizeFactor: Double = 1.0,
    /** Gate income accumulated across this season's home games (Bank display + P&L). */
    val seasonGateK: Long = 0,
    /** Wages paid across this season so far (Bank display + P&L). */
    val seasonWagesK: Long = 0,
    /** Prize money from the season just completed (set at rollover, for display). */
    val lastSeasonPrizeK: Long = 0,
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

    /** Gate income for one of the managed club's home games at its current tier. */
    fun gatePerHomeK(): Long = Finance.gatePerHomeK(activeTierIndex.coerceAtLeast(0), clubSizeFactor)

    /** Simulates every fixture in [nextRound], applies the managed club's matchday
     * finances (wages out, gate in on a home game), and returns the updated career. */
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
        // Managed club's matchday finances: pay wages when it plays, take the gate at home.
        val mf = fixtures.firstOrNull { it.round == round && (it.home == managedIndex || it.away == managedIndex) }
        var newBudget = budget
        var gate = seasonGateK
        var wages = seasonWagesK
        if (mf != null && wageBillPerMatchK > 0L) {
            newBudget -= wageBillPerMatchK
            wages += wageBillPerMatchK
            if (mf.home == managedIndex) {
                val g = gatePerHomeK()
                newBudget += g
                gate += g
            }
        }
        return copy(
            fixtures = updated,
            budget = newBudget.coerceAtLeast(0L),
            seasonGateK = gate,
            seasonWagesK = wages,
        )
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

        // Finances: prize money for the season just finished (old tier + final rank),
        // plus a one-off bonus if the managed club won promotion to a higher tier.
        val oldTier = activeTierIndex.coerceAtLeast(0)
        val prize = Finance.seasonPrizeK(oldTier, managedFinalRank(), clubIds.size)
        val newTierIdx = newTiers.indexOfFirst { managedClubId in it.clubIds }.coerceAtLeast(0)
        val promoBonus = if (newTierIdx < oldTier) Finance.promotionBonusK(newTierIdx) else 0L
        val newBudget = (budget + prize + promoBonus).coerceAtLeast(0L)

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
            budget = newBudget,
            seasonGateK = 0,
            seasonWagesK = 0,
            lastSeasonPrizeK = prize,
        )
    }

    /**
     * The managed club's next fixture pre-played to its result, WITHOUT recording
     * it, so the match view can animate toward the real outcome. Uses the exact
     * same [fixtureSeed] + [Sim] path as [playNextRound], so the visualised score
     * is identical to the one [playNextRound] will store when the round is played.
     * Null at season end or when the managed club has no remaining fixture.
     */
    fun previewNextManagedMatch(): ManagedPreview? {
        if (seasonComplete) return null
        val f = nextFixtureForManaged() ?: return null
        // Only watchable when the managed fixture is in the round about to be
        // played; in an odd-sized division the club can have a bye this round
        // (its next fixture is later), so there is no match to watch — advance.
        if (f.round != nextRound) return null
        val seed = fixtureSeed(f.round, f)
        val splitReady = attackStrengths.size == clubIds.size && defenceStrengths.size == clubIds.size
        val r = if (splitReady) {
            Sim.play(
                attackStrengths[f.home], defenceStrengths[f.home],
                attackStrengths[f.away], defenceStrengths[f.away],
                leagueGap, seed,
            )
        } else {
            Sim.playOverall(strengths[f.home], strengths[f.away], seed)
        }
        return ManagedPreview(
            homeId = clubIds[f.home], awayId = clubIds[f.away],
            homeGoals = r[0], awayGoals = r[1], seed = seed,
            homeIsManaged = f.home == managedIndex,
        )
    }

    // ---- transfers -------------------------------------------------------------

    /** Player index -> current club id (null = released), folded from [transfers]
     * with the last move per player winning. */
    private fun movedTo(): Map<Int, String?> = transfers.associate { it.playerIndex to it.toClub }

    /** The club [playerIndex] is at now: his last transfer's destination if he has
     * moved, otherwise his [default] (original) club. */
    fun currentClubOf(playerIndex: Int, default: String?): String? {
        val moved = movedTo()
        return if (moved.containsKey(playerIndex)) moved[playerIndex] else default
    }

    /** [clubId]'s current squad after transfers: its original players who have not
     * left, plus anyone transferred in, sorted by rating (best first). */
    fun squadFor(data: GameData, clubId: String): List<Player> {
        val moved = movedTo()
        val stayed = data.playersIndexed
            .filter { it.value.club == clubId && !moved.containsKey(it.index) }
            .map { it.value }
        val joined = moved.filterValues { it == clubId }.keys
            .mapNotNull { data.players.getOrNull(it) }
        return (stayed + joined).sortedByDescending { it.rating }
    }

    /** The managed club's current squad. */
    fun managedSquad(data: GameData): List<Player> = squadFor(data, managedClubId)

    /** Global indices of the managed club's current players (for "already mine" UI checks). */
    fun managedSquadIndices(data: GameData): Set<Int> {
        val moved = movedTo()
        val stayed = data.playersIndexed
            .filter { it.value.club == managedClubId && !moved.containsKey(it.index) }
            .map { it.index }
        val joined = moved.filterValues { it == managedClubId }.keys
        return (stayed + joined).toSet()
    }

    /** Signs [playerIndex] into the managed club for [feeK], debiting the budget and
     * recomputing both clubs' strengths. Caller checks affordability / squad cap. */
    fun signPlayer(data: GameData, playerIndex: Int, feeK: Long): Career =
        withMove(data, playerIndex, managedClubId, -feeK, feeK)

    /** Releases [playerIndex] from the managed club for [feeK], crediting the budget
     * and recomputing the managed strength. Caller checks the squad floor. */
    fun sellPlayer(data: GameData, playerIndex: Int, feeK: Long): Career =
        withMove(data, playerIndex, null, +feeK, feeK)

    /** Records a move, adjusts the budget, and re-freezes the strengths of every
     * club whose squad changed (the player's old club and his new one). */
    private fun withMove(data: GameData, playerIndex: Int, toClub: String?, budgetDelta: Long, feeK: Long): Career {
        val player = data.players.getOrNull(playerIndex) ?: return this
        val from = currentClubOf(playerIndex, player.club)
        if (from == toClub) return this // no-op move
        var c = copy(
            budget = (budget + budgetDelta).coerceAtLeast(0L),
            transfers = transfers + Transfer(playerIndex, toClub, feeK),
        )
        for (cid in listOfNotNull(from, toClub).distinct()) c = c.recomputeStrength(data, cid)
        // Every transfer changes the managed squad, so re-rate the wage bill.
        return c.copy(wageBillPerMatchK = Finance.wageBillPerMatchK(c.managedSquad(data)))
    }

    /** Re-freezes [clubId]'s strength triple from its CURRENT squad, writing both the
     * pyramid map (for rollover sims) and, if it is in the active division, the live
     * per-club arrays + [leagueGap] that the goals model reads. */
    private fun recomputeStrength(data: GameData, clubId: String): Career {
        val squad = squadFor(data, clubId)
        val triple = ClubStrength(Strength.of(squad), Strength.attack(squad), Strength.defence(squad))
        val cs = if (clubStrengths.containsKey(clubId)) clubStrengths + (clubId to triple) else clubStrengths
        val ai = clubIds.indexOf(clubId)
        if (ai < 0) return copy(clubStrengths = cs)
        val str = strengths.toMutableList().also { it[ai] = triple.overall }
        val splitReady = attackStrengths.size == clubIds.size && defenceStrengths.size == clubIds.size
        if (!splitReady) return copy(strengths = str, clubStrengths = cs)
        val att = attackStrengths.toMutableList().also { it[ai] = triple.attack }
        val def = defenceStrengths.toMutableList().also { it[ai] = triple.defence }
        return copy(
            strengths = str,
            attackStrengths = att,
            defenceStrengths = def,
            leagueGap = att.average() - def.average(),
            clubStrengths = cs,
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
