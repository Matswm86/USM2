package no.mwmai.usm2.engine

import no.mwmai.usm2.Player

/**
 * Season finances. The seed DB carries no balances, so every figure is derived
 * honestly from the real squad (via [Valuation]) and the club's tier:
 *   - wages: a slice of squad value, charged each matchday the club plays;
 *   - gate receipts: a tier base scaled by club size, credited each home game;
 *   - prize money: a tier base scaled by final league position, at season end;
 *   - a promotion bonus on moving up a tier.
 * Constants tuned on the real England pyramid so a top club running well nets a
 * few £M a season, a mid club roughly breaks even, and a struggling/relegated
 * club loses money (and must sell) — a meaningful but non-fatal swing against the
 * ~£25M top-flight transfer budget. All figures in £k.
 */
object Finance {
    private const val WAGE_RATE_PER_MATCH = 0.0045
    private val GATE_BASE_BY_TIER = longArrayOf(420, 190, 95, 55, 32)
    private val PRIZE_BASE_BY_TIER = longArrayOf(12_000, 3_400, 1_400, 700, 350)
    /** Bonus for being promoted INTO tier index t (0 = top flight). */
    private val PROMO_BONUS_BY_TIER = longArrayOf(2_500, 1_200, 600, 300, 0)

    /** Per-matchday wage bill from the current squad's total value. */
    fun wageBillPerMatchK(squad: List<Player>): Long =
        (squad.sumOf { Valuation.valueK(it) } * WAGE_RATE_PER_MATCH).toLong()

    /** Club size = squad value vs its division average (richer club -> bigger gates). */
    fun sizeFactor(squadValueK: Long, divisionAvgK: Long): Double =
        if (divisionAvgK <= 0) 1.0 else squadValueK.toDouble() / divisionAvgK.toDouble()

    /** Income from one home match. */
    fun gatePerHomeK(tierIndex: Int, sizeFactor: Double): Long =
        (base(GATE_BASE_BY_TIER, tierIndex) * sizeFactor).toLong()

    /** End-of-season prize: champion gets the tier base, last place a sliver. */
    fun seasonPrizeK(tierIndex: Int, rank: Int, size: Int): Long =
        if (rank <= 0 || size <= 0) 0
        else base(PRIZE_BASE_BY_TIER, tierIndex) * (size - rank + 1) / size

    fun promotionBonusK(destTierIndex: Int): Long = base(PROMO_BONUS_BY_TIER, destTierIndex)

    private fun base(arr: LongArray, tier: Int): Long = arr[tier.coerceIn(0, arr.size - 1)]
}
