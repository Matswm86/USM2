package no.mwmai.usm2.engine

import no.mwmai.usm2.Player

/**
 * Transfer valuation. USM2's seed DB carries no transfer prices, so a value is
 * derived honestly from the real overall [Player.rating] (steep cubic curve, so
 * a few elite players dominate the market) and age (peak resale ~24-27, steep
 * decline past 30). All figures in £k. The single source of truth for the Bank
 * room's squad value and the transfer market's buy/sell prices.
 */
object Valuation {
    /** Asking price premium when buying, and the discount when selling: a 10%
     * spread each way makes a buy-then-sell round trip a net loss, so there is no
     * money pump. */
    private const val BUY_PREMIUM = 1.10
    private const val SELL_DISCOUNT = 0.90

    /** Base value in £k from overall rating and age. */
    fun valueK(rating: Int, age: Int): Long {
        val r = rating.coerceIn(1, 99) / 100.0
        return (r * r * r * 14_000 * ageMultiplier(age)).toLong()
    }

    fun valueK(p: Player): Long = valueK(p.rating, p.age)

    /** What it costs to sign [p] (value + premium). */
    fun buyPriceK(p: Player): Long = (valueK(p) * BUY_PREMIUM).toLong()

    /** What selling [p] brings in (value - discount). */
    fun sellPriceK(p: Player): Long = (valueK(p) * SELL_DISCOUNT).toLong()

    /** Age curve: youth unproven, mid-20s peak, sharp fall after 30. */
    private fun ageMultiplier(age: Int): Double = when {
        age <= 18 -> 0.80
        age in 19..23 -> 0.95
        age in 24..27 -> 1.10
        age in 28..30 -> 0.90
        age in 31..33 -> 0.65
        else -> 0.40
    }
}
