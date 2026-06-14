package no.mwmai.usm2.engine

/**
 * Double round-robin fixture generation via the circle method (Berger tables).
 *
 * Guarantees, verified against the real 20-club Premier League in
 * tools/proto_engine.py: every ordered (home, away) pairing occurs exactly once
 * over the season, every unordered pair meets twice (once each venue), no team
 * plays twice in a round, and home/away counts are balanced per team.
 */
object Schedule {

    /** One season's fixtures as flat [Fixture] rows (no results yet). The team
     * order is shuffled by [seed] so each career gets a distinct calendar. */
    fun season(nTeams: Int, seed: Long): List<Fixture> {
        val order = shuffledOrder(nTeams, seed)
        val firstHalf = singleRoundRobin(nTeams)
        val rounds = ArrayList<List<Pair<Int, Int>>>(2 * (nTeams - 1))
        rounds.addAll(firstHalf)
        // Second half: same pairings, venues reversed.
        firstHalf.forEach { rnd -> rounds.add(rnd.map { (h, a) -> a to h }) }

        val out = ArrayList<Fixture>(nTeams * (nTeams - 1))
        rounds.forEachIndexed { r, pairs ->
            pairs.forEach { (h, a) -> out.add(Fixture(r, order[h], order[a])) }
        }
        return out
    }

    /** Single round-robin as a list of rounds of (home, away) index pairs. A bye
     * (-1) is inserted for an odd team count and its matches are dropped. */
    private fun singleRoundRobin(nTeams: Int): List<List<Pair<Int, Int>>> {
        val teams = ArrayList<Int>(nTeams + 1)
        for (i in 0 until nTeams) teams.add(i)
        if (teams.size % 2 != 0) teams.add(-1) // bye marker
        val m = teams.size
        val fixed = teams[0]
        val rot = ArrayDeque(teams.subList(1, m))
        val rounds = ArrayList<List<Pair<Int, Int>>>(m - 1)
        repeat(m - 1) { r ->
            val orderList = ArrayList<Int>(m)
            orderList.add(fixed)
            orderList.addAll(rot)
            val pairs = ArrayList<Pair<Int, Int>>(m / 2)
            for (i in 0 until m / 2) {
                val a = orderList[i]
                val b = orderList[m - 1 - i]
                if (a == -1 || b == -1) continue
                // Alternate venue across rounds so the first half is balanced.
                pairs.add(if ((r + i) % 2 == 0) a to b else b to a)
            }
            rounds.add(pairs)
            rot.addFirst(rot.removeLast()) // rotate all but the fixed team
        }
        return rounds
    }

    /** Deterministic shuffle of team indices so the calendar varies by seed. */
    private fun shuffledOrder(nTeams: Int, seed: Long): IntArray {
        val a = IntArray(nTeams) { it }
        val rng = Rng(seed xor 0x5DEECE66DL)
        for (i in nTeams - 1 downTo 1) {
            val j = (rng.nextDouble() * (i + 1)).toInt().coerceIn(0, i)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        return a
    }
}
