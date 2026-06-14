package no.mwmai.usm2.engine

import kotlin.math.exp

/**
 * Match simulation. A team's attack and defence (0-99 — see [Strength], from the
 * real Attacking/Defending/Goalkeeping bytes) drive an expected-goals rate; the
 * scoreline is a seeded Poisson draw per side, with a fixed home bump.
 *
 * Expected goals centre on the division's own attack-minus-defence gap, so the
 * model self-calibrates for any league level. Constants were tuned against the
 * real EPL data (tools/proto_engine.py): ~2.8 goals/match, ~50/22/28 H/D/A, a
 * title race in the high-70s/80s and a relegated side in the high-20s.
 *
 * Saves made before the attack/defence split carry no attack/defence vectors, so
 * [Career] falls back to [playOverall] (the original single-strength model).
 */
object Sim {
    // attack/defence model
    private const val BASE = 1.10       // goals per side for an average matchup
    private const val HOME_ADV = 0.28   // extra xG for the home team
    private const val K = 0.8           // how hard a strength edge bends the rate
    private const val SCALE = 8.0       // attack/defence points per unit of edge

    // legacy single-strength model (pre-split saves)
    private const val OLD_BASE = 1.25
    private const val OLD_HOME = 0.30
    private const val OLD_SPREAD = 0.45

    /** xG for [attack] facing [oppDefence], centred on the division [leagueGap]
     * (mean attack - mean defence) so an average matchup yields [BASE]. */
    fun expectedGoals(attack: Double, oppDefence: Double, leagueGap: Double): Double =
        maxOf(0.05, BASE * exp(K * ((attack - oppDefence - leagueGap) / SCALE)))

    /** Plays one fixture from attack/defence lines. Deterministic in [seed]. */
    fun play(
        homeAttack: Double, homeDefence: Double,
        awayAttack: Double, awayDefence: Double,
        leagueGap: Double, seed: Long,
    ): IntArray {
        val rng = Rng(seed)
        val hg = poisson(rng, expectedGoals(homeAttack, awayDefence, leagueGap) + HOME_ADV)
        val ag = poisson(rng, expectedGoals(awayAttack, homeDefence, leagueGap))
        return intArrayOf(hg, ag)
    }

    /** Legacy single-strength fallback for saves without attack/defence vectors. */
    fun playOverall(homeStrength: Double, awayStrength: Double, seed: Long): IntArray {
        val rng = Rng(seed)
        fun xg(a: Double, d: Double) = maxOf(0.05, OLD_BASE * exp(OLD_SPREAD * ((a - d) / 18.0)))
        val hg = poisson(rng, xg(homeStrength, awayStrength) + OLD_HOME)
        val ag = poisson(rng, xg(awayStrength, homeStrength))
        return intArrayOf(hg, ag)
    }

    /** Knuth's Poisson sampler. */
    private fun poisson(rng: Rng, lambda: Double): Int {
        if (lambda <= 0.0) return 0
        val limit = exp(-lambda)
        var k = 0
        var p = 1.0
        while (true) {
            k++
            p *= rng.nextDouble()
            if (p <= limit) return k - 1
            if (k > 30) return k - 1 // safety cap against a pathological tail
        }
    }
}
