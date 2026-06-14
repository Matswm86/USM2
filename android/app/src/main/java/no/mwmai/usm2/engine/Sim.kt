package no.mwmai.usm2.engine

import kotlin.math.exp

/**
 * Match simulation. A team's strength (0-99, best-XI mean overall — see
 * [Strength]) maps to an expected-goals rate; the scoreline is a seeded Poisson
 * draw on each side. The home side gets a fixed xG bump.
 *
 * Constants were tuned in tools/proto_engine.py against the real EPL data:
 * ~2.9 goals/match, ~46% home / ~27% draw / ~27% away over a full season.
 */
object Sim {
    private const val BASE = 1.25       // league-average goals per side
    private const val HOME_ADV = 0.30   // extra xG for the home team
    private const val SPREAD = 0.45     // how hard strength gaps bend the rate

    fun expectedGoals(attack: Double, defence: Double): Double {
        val gap = (attack - defence) / 18.0
        return maxOf(0.05, BASE * exp(SPREAD * gap))
    }

    /** Plays one fixture, returning [homeGoals, awayGoals]. Deterministic in [seed]. */
    fun play(homeStrength: Double, awayStrength: Double, seed: Long): IntArray {
        val rng = Rng(seed)
        val hg = poisson(rng, expectedGoals(homeStrength, awayStrength) + HOME_ADV)
        val ag = poisson(rng, expectedGoals(awayStrength, homeStrength))
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
