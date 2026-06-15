package no.mwmai.usm2.engine

import kotlin.math.abs

/**
 * The cosmetic pitch surface for one fixture. Drives only the match-view backdrop
 * (and a subtle ball-friction feel) — NEVER the scoreline. DRY is the default
 * green pitch; MUD/WET are the worn-and-soaked variants; ICE is a deep-winter frost.
 */
enum class PitchCondition { DRY, MUD, WET, ICE }

// A salt that keeps the weather RNG stream disjoint from the goal RNG (which is
// seeded straight off Career.fixtureSeed), so deriving the weather can never
// perturb the recorded result. 0x6A09E667F3BCC909 = the sqrt(2) fractional bits,
// a positive Long with good avalanche.
private const val WEATHER_SALT = 0x6A09E667F3BCC909L

/**
 * Deterministic, purely cosmetic pitch condition for one fixture. Reproducible
 * from the same inputs (a loaded save replays the same weather) and independent of
 * the match result. Seasonal: round 0 ~ opening day (August), the final round ~
 * May, so mud and rain cluster mid-season and ICE only appears in deep winter;
 * DRY dominates at the season's ends. Mirrored in tools/proto_engine.py
 * (validate_weather) so the prototype and the app agree exactly.
 */
fun pitchConditionFor(
    seasonSeed: Long,
    round: Int,
    home: Int,
    away: Int,
    totalRounds: Int,
): PitchCondition {
    val seed = (seasonSeed * 1_000_003L + round * 9176L + home * 131L + away) xor WEATHER_SALT
    val rng = Rng(seed)
    val r = rng.nextDouble()
    // calendar proxy: 0 = both season ends (warm/dry), 1 = mid-season (deep winter).
    val progress = if (totalRounds > 1) round.toDouble() / (totalRounds - 1) else 0.0
    val winter = (1.0 - abs(progress - 0.5) * 2.0).coerceIn(0.0, 1.0)
    val pBad = 0.10 + 0.45 * winter // ~10% bad pitch in Aug/May, up to ~55% midwinter
    if (r >= pBad) return PitchCondition.DRY
    // among the bad pitches: rain most common, then mud; ice only in deep winter.
    val r2 = rng.nextDouble()
    return when {
        winter > 0.6 && r2 < 0.25 -> PitchCondition.ICE
        r2 < 0.60 -> PitchCondition.WET
        else -> PitchCondition.MUD
    }
}
