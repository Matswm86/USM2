package no.mwmai.usm2.engine

/**
 * Deterministic xorshift64* PRNG. The match engine is seeded per fixture so a
 * given career replays identically (important for a saved season that is loaded
 * and continued). Not security-grade; reproducibility is the only requirement.
 */
class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    fun nextLong(): Long {
        var x = s
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        s = x
        // 0x2545F4914F6CDD1D — fits a positive Long; multiply wraps mod 2^64.
        return s * 2685821657736338717L
    }

    /** Uniform in [0, 1). Uses the high 53 bits (full double mantissa). */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()
}
