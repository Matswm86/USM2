package no.mwmai.usm2.engine

/** One league-table row. [clubIndex] indexes into [Career.clubIds]. */
data class TableRow(
    val clubIndex: Int,
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
    val points: Int get() = won * 3 + drawn
}

object Standings {
    /** Table over the played fixtures, ordered by points, then GD, then GF. */
    fun compute(nTeams: Int, fixtures: List<Fixture>): List<TableRow> {
        val rows = Array(nTeams) { TableRow(it) }
        fun bump(i: Int, gf: Int, ga: Int, w: Int, d: Int, l: Int) {
            val r = rows[i]
            rows[i] = r.copy(
                played = r.played + 1,
                won = r.won + w, drawn = r.drawn + d, lost = r.lost + l,
                goalsFor = r.goalsFor + gf, goalsAgainst = r.goalsAgainst + ga,
            )
        }
        fixtures.filter { it.played }.forEach { f ->
            val h = f.homeGoals
            val a = f.awayGoals
            when {
                h > a -> { bump(f.home, h, a, 1, 0, 0); bump(f.away, a, h, 0, 0, 1) }
                h < a -> { bump(f.home, h, a, 0, 0, 1); bump(f.away, a, h, 1, 0, 0) }
                else -> { bump(f.home, h, a, 0, 1, 0); bump(f.away, a, h, 0, 1, 0) }
            }
        }
        return rows.sortedWith(
            compareByDescending<TableRow> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.clubIndex },
        )
    }
}
