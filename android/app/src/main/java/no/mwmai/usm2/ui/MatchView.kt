package no.mwmai.usm2.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import no.mwmai.usm2.GameData
import no.mwmai.usm2.Player
import no.mwmai.usm2.engine.Career
import no.mwmai.usm2.engine.PitchCondition
import no.mwmai.usm2.engine.Rng
import no.mwmai.usm2.engine.Sim
import kotlin.math.hypot

// One goal in the animated timeline.
data class MatchEvent(val minute: Int, val home: Boolean, val scorer: String)

/**
 * Everything the [MatchView] needs to animate one match: the two clubs, the final
 * score and its goal timeline (from [Sim.goalMinutes]), and a formation per side.
 * Built by [buildMatchPlan] off [Career.previewNextManagedMatch], so the score it
 * animates toward is exactly the one [Career.playNextRound] then records.
 */
data class MatchPlan(
    val homeName: String,
    val awayName: String,
    val homeShort: String,
    val awayShort: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val events: List<MatchEvent>,
    val homeForm: List<Pair<Double, Double>>,
    val awayForm: List<Pair<Double, Double>>,
    val seed: Long,
    val condition: PitchCondition,
    val homeIsManaged: Boolean,
    // Managed club rosters for in-match subs (global player indices). The XI is on
    // the pitch at kick-off; the bench is everyone else in the squad.
    val managedXI: List<Int>,
    val managedBench: List<Int>,
)

/** Picks a scorer (surname) from [outs], weighted by Attacking; deterministic in [rng]. */
private fun pickScorerName(outs: List<Player>, rng: Rng): String {
    if (outs.isEmpty()) return "Unknown"
    val weights = outs.map { (it.attacking + 5).toDouble() }
    var r = rng.nextDouble() * weights.sum()
    var k = 0
    for (i in outs.indices) {
        r -= weights[i]
        if (r <= 0) { k = i; break }
    }
    return outs[k].name.substringAfterLast(' ').ifBlank { outs[k].name }
}

// Fallback 4-4-2 (fx across 0..1, fy depth with 1=GK .. 0=striker) if FORM.DAT
// formations are unavailable; matches the convention of data/formations.json.
private val DEFAULT_FORM = listOf(
    0.50 to 0.97,
    0.16 to 0.74, 0.38 to 0.74, 0.62 to 0.74, 0.84 to 0.74,
    0.16 to 0.46, 0.38 to 0.46, 0.62 to 0.46, 0.84 to 0.46,
    0.35 to 0.16, 0.65 to 0.16,
)

/** Builds the [MatchPlan] for the managed club's next fixture, or null at season end. */
fun buildMatchPlan(data: GameData, career: Career): MatchPlan? {
    val pv = career.previewNextManagedMatch() ?: return null
    val home = data.clubsById[pv.homeId]
    val away = data.clubsById[pv.awayId]
    val (homeMin, awayMin) = Sim.goalMinutes(pv.homeGoals, pv.awayGoals, pv.seed)

    // A scorer per goal, drawn (weighted by Attacking) from the real squad. The
    // RNG is seeded from the fixture seed so the scorer line replays identically.
    val rng = Rng(pv.seed * 1_099_511_628_211L + 0x2545)
    fun scorer(clubId: String): String {
        // The managed club scores from its starting XI; opponents from their squad.
        val squad = if (clubId == career.managedClubId) career.startingSquad(data) else career.squadFor(data, clubId)
        val outs = squad.filter { !it.isGoalkeeper }.ifEmpty { squad }
        return pickScorerName(outs, rng)
    }

    val events = (homeMin.map { MatchEvent(it, true, scorer(pv.homeId)) } +
        awayMin.map { MatchEvent(it, false, scorer(pv.awayId)) }).sortedBy { it.minute }

    val form = data.formations.firstOrNull()?.mapNotNull {
        if (it.size >= 2) it[0] to it[1] else null
    }?.takeIf { it.size == 11 } ?: DEFAULT_FORM

    // managed rosters for in-match subs: the on-pitch XI + the rest of the squad
    val xi = career.effectiveXIIndices(data)
    val bench = (career.managedSquadIndices(data) - xi.toSet())
        .mapNotNull { i -> data.players.getOrNull(i)?.let { i to it.rating } }
        .sortedByDescending { it.second }
        .map { it.first }

    fun short(name: String?, fallback: String) =
        name?.takeIf { it.isNotBlank() } ?: fallback
    return MatchPlan(
        homeName = home?.name ?: "Home",
        awayName = away?.name ?: "Away",
        homeShort = short(home?.short, (home?.name ?: "HOM").take(3).uppercase()),
        awayShort = short(away?.short, (away?.name ?: "AWY").take(3).uppercase()),
        homeGoals = pv.homeGoals,
        awayGoals = pv.awayGoals,
        events = events,
        homeForm = form,
        awayForm = form,
        seed = pv.seed,
        condition = pv.condition,
        homeIsManaged = pv.homeIsManaged,
        managedXI = xi,
        managedBench = bench,
    )
}

/** A short label for the weather chip (null = dry, no chip). */
private fun conditionLabel(c: PitchCondition): String? = when (c) {
    PitchCondition.DRY -> null
    PitchCondition.MUD -> "Muddy pitch"
    PitchCondition.WET -> "Wet pitch"
    PitchCondition.ICE -> "Frozen pitch"
}

/** Cosmetic ball-pace multiplier by surface: mud drags, a wet pitch skids, ice
 * slides. Affects only the live ball animation, never the scoreline. */
private fun ballPaceFor(c: PitchCondition): Float = when (c) {
    PitchCondition.DRY -> 1.0f
    PitchCondition.MUD -> 0.82f
    PitchCondition.WET -> 1.08f
    PitchCondition.ICE -> 1.18f
}

// --- live choreography ------------------------------------------------------
// Not a football AI: a deterministic-looking flow that keeps the formation shape,
// pulls the cluster toward the ball, and animates run cycles. The SCORE is fixed
// by the plan; this only makes the 90 minutes watchable.
private const val MS_PER_MIN = 520f          // real ms per match-minute at 1x
private const val PLAYER_SPEED = 0.42f       // pitch-fractions / sec
private const val BALL_SPEED = 0.62f
private const val DIVE_GOAL = 0.95f          // beaten-keeper dive on a goal (seconds)
private const val DIVE_SAVE = 0.70f          // reflex dive when the ball reaches the box
private const val MAX_SUBS = 3               // era-faithful: 3 substitutions per match

private class MatchSim(homeForm: List<Pair<Double, Double>>, awayForm: List<Pair<Double, Double>>, seed: Long, private val ballPace: Float = 1f) {
    val n = 22
    val px = FloatArray(n)
    val py = FloatArray(n)
    val vx = FloatArray(n)
    val moving = BooleanArray(n)
    val diveT = FloatArray(n)        // >0 while a keeper is mid-dive (cosmetic only)
    val diveDur = FloatArray(n)      // the dive's full duration, for render progress
    val diveSign = FloatArray(n)     // -1 dives toward the top post, +1 toward the bottom
    private val baseX = FloatArray(n)
    private val baseY = FloatArray(n)
    private val gk = BooleanArray(n)
    private var homeGk = 0
    private var awayGk = 11
    var bx = 0.5f
    var by = 0.5f
    private var tx = 0.5f
    private var ty = 0.5f
    private var poss = 0
    private var decisionT = 0f
    private val rng = java.util.Random(seed)

    init {
        place(homeForm, 0, home = true)
        place(awayForm, 11, home = false)
    }

    private fun place(form: List<Pair<Double, Double>>, off: Int, home: Boolean) {
        val gkSlot = form.indices.maxByOrNull { form[it].second } ?: 0
        form.forEachIndexed { k, (fx, fy) ->
            val i = off + k
            val depth = (1.0 - fy).toFloat()                     // 0 = own goal, 1 = forward
            baseX[i] = if (home) 0.07f + depth * 0.40f else 0.93f - depth * 0.40f
            baseY[i] = (0.12 + fx * 0.76).toFloat()
            gk[i] = k == gkSlot
            if (k == gkSlot) { if (home) homeGk = i else awayGk = i }
            px[i] = baseX[i]
            py[i] = baseY[i]
        }
    }

    private fun nearest(home: Boolean): Int {
        val r = if (home) 0..10 else 11..21
        var best = r.first
        var bd = Float.MAX_VALUE
        for (i in r) {
            val d = (px[i] - bx) * (px[i] - bx) + (py[i] - by) * (py[i] - by)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return best
    }

    /** Sends the ball at the goal [home] is attacking (for a goal celebration). */
    fun shootAt(home: Boolean) {
        tx = if (home) 0.985f else 0.015f
        ty = 0.5f
        poss = if (home) 0 else 1
        decisionT = 1.2f
        // the beaten keeper throws himself at it: the side NOT scoring concedes.
        startDive(if (home) awayGk else homeGk, by, DIVE_GOAL)
    }

    /** Centre-spot restart after a goal; the conceding side kicks off. */
    fun kickoff(scoredHome: Boolean) {
        bx = 0.5f; by = 0.5f; tx = 0.5f; ty = 0.5f
        poss = if (scoredHome) 1 else 0
        decisionT = 0.3f
    }

    /** Begin a cosmetic keeper dive toward [ballY]'s side (random if dead-centre). */
    private fun startDive(i: Int, ballY: Float, dur: Float) {
        diveSign[i] = when {
            kotlin.math.abs(ballY - 0.5f) < 0.04f -> if (rng.nextBoolean()) 1f else -1f
            ballY < 0.5f -> -1f
            else -> 1f
        }
        diveDur[i] = dur
        diveT[i] = dur
    }

    fun step(dt: Float) {
        // ball: head to target; pick a new one on arrival or when the clock runs out
        val dx = tx - bx
        val dy = ty - by
        val d = hypot(dx, dy)
        val s = BALL_SPEED * ballPace * dt
        decisionT -= dt
        if (d <= s || decisionT <= 0f) {
            if (d <= s) {
                bx = tx; by = ty
            }
            decisionT = 0.6f + rng.nextFloat() * 0.9f
            if (rng.nextFloat() < 0.22f) poss = 1 - poss
            val dir = if (poss == 0) 1f else -1f                 // possessor attacks toward his goal
            tx = (bx + dir * (0.10f + rng.nextFloat() * 0.30f)).coerceIn(0.07f, 0.93f)
            ty = (by + (rng.nextFloat() - 0.5f) * 0.26f).coerceIn(0.12f, 0.88f)
        } else {
            bx += dx / d * s
            by += dy / d * s
        }

        // reflex keeper dive once play reaches the goalmouth (cosmetic, no save logic)
        if (rng.nextFloat() < 0.012f) {
            if (bx > 0.86f && diveT[awayGk] <= 0f) startDive(awayGk, by, DIVE_SAVE)
            else if (bx < 0.14f && diveT[homeGk] <= 0f) startDive(homeGk, by, DIVE_SAVE)
        }

        val carrier = nearest(poss == 0)
        val presser = nearest(poss != 0)
        val move = PLAYER_SPEED * dt
        for (i in 0 until n) {
            if (gk[i] && diveT[i] > 0f) {
                diveT[i] -= dt
                val goalX = if (i < 11) 0.05f else 0.95f
                val tgtY = 0.5f + diveSign[i] * 0.30f
                px[i] += (goalX - px[i]) * minOf(1f, 9f * dt)
                py[i] += (tgtY - py[i]) * minOf(1f, 7f * dt)
                py[i] = py[i].coerceIn(0.16f, 0.84f)
                vx[i] = 0f
                moving[i] = false
                continue
            }
            // Each player heads from his base TOWARD the ball; the pull is strongest
            // for the two players contesting it and for anyone whose zone the ball is
            // in, so a knot forms around the ball while the rest hold their shape.
            val toBallX = bx - baseX[i]
            val toBallY = by - baseY[i]
            val distBase = hypot(toBallX, toBallY)
            val attract = when {
                i == carrier || i == presser -> 0.90f
                gk[i] -> 0.06f
                else -> (0.60f / (1f + 7f * distBase)).coerceIn(0.07f, 0.55f)
            }
            val targetX = baseX[i] + toBallX * attract
            val targetY = baseY[i] + toBallY * attract
            val spd = if (i == carrier || i == presser) move * 1.6f else move
            val mdx = targetX - px[i]
            val mdy = targetY - py[i]
            val md = hypot(mdx, mdy)
            if (md > 1e-4f) {
                val st = minOf(spd, md)
                px[i] += mdx / md * st
                py[i] += mdy / md * st
                vx[i] = mdx
                moving[i] = st > move * 0.2f
            } else {
                moving[i] = false
            }
            if (gk[i]) {
                px[i] = px[i].coerceIn(if (i < 11) 0.04f else 0.80f, if (i < 11) 0.20f else 0.96f)
                py[i] = py[i].coerceIn(0.32f, 0.68f)
            } else {
                px[i] = px[i].coerceIn(0.03f, 0.97f)
                py[i] = py[i].coerceIn(0.05f, 0.95f)
            }
        }
    }
}

private val Overlay = Color(0xCC0A1810)
private val Gold = Color(0xFFE7C84A)
private val Ink = Color(0xFFEAF2EA)

@Composable
fun MatchView(data: GameData, plan: MatchPlan, onFinish: () -> Unit) {
    val pitch = rememberAssetImage(
        when (plan.condition) {
            PitchCondition.MUD -> "img/match/pitch_mud.png"
            PitchCondition.WET -> "img/match/pitch_wet.png"
            PitchCondition.ICE -> "img/match/pitch_ice.png"
            PitchCondition.DRY -> "img/match/pitch.png"
        },
    )
    val homeRun = listOf(
        rememberAssetImage("img/match/h_run0.png"),
        rememberAssetImage("img/match/h_run1.png"),
        rememberAssetImage("img/match/h_run2.png"),
        rememberAssetImage("img/match/h_run3.png"),
        rememberAssetImage("img/match/h_run4.png"),
    )
    val awayRun = listOf(
        rememberAssetImage("img/match/a_run0.png"),
        rememberAssetImage("img/match/a_run1.png"),
        rememberAssetImage("img/match/a_run2.png"),
        rememberAssetImage("img/match/a_run3.png"),
        rememberAssetImage("img/match/a_run4.png"),
    )
    val homeIdle = rememberAssetImage("img/match/h_idle.png")
    val awayIdle = rememberAssetImage("img/match/a_idle.png")
    val q = data.pitchQuad

    val audio = rememberMatchAudio()
    val sim = remember(plan) { MatchSim(plan.homeForm, plan.awayForm, plan.seed, ballPaceFor(plan.condition)) }
    var elapsed by remember(plan) { mutableFloatStateOf(0f) }
    var minute by remember(plan) { mutableIntStateOf(0) }
    var evIdx by remember(plan) { mutableIntStateOf(0) }
    var hScore by remember(plan) { mutableIntStateOf(0) }
    var aScore by remember(plan) { mutableIntStateOf(0) }
    var last by remember(plan) { mutableStateOf("Kick-off") }
    var flashUntil by remember(plan) { mutableFloatStateOf(-1f) }
    var pendingKickoff by remember(plan) { mutableStateOf<Boolean?>(null) }
    var finished by remember(plan) { mutableStateOf(false) }
    var speed by remember(plan) { mutableIntStateOf(2) }
    var redraw by remember(plan) { mutableIntStateOf(0) }

    // --- in-match substitutions (managed club only) ---
    val onField = remember(plan) { mutableStateListOf<Int>().also { it.addAll(plan.managedXI) } }
    val bench = remember(plan) { mutableStateListOf<Int>().also { it.addAll(plan.managedBench) } }
    var subsUsed by remember(plan) { mutableIntStateOf(0) }
    var showSubs by remember(plan) { mutableStateOf(false) }
    var pickedOff by remember(plan) { mutableStateOf<Int?>(null) }

    fun scoreGoal(e: MatchEvent) {
        if (e.home) hScore++ else aScore++
        // A managed goal after a sub is credited to whoever is on the pitch NOW.
        val who = if (e.home == plan.homeIsManaged && subsUsed > 0) {
            val outs = onField.mapNotNull { data.players.getOrNull(it) }.filter { !it.isGoalkeeper }
            pickScorerName(outs, Rng(plan.seed xor (e.minute * 0x9E3779B1L) xor 0x5151L))
        } else {
            e.scorer
        }
        last = "GOAL  ${e.minute}'  $who  (${if (e.home) plan.homeShort else plan.awayShort})"
        flashUntil = elapsed + 1700f
        pendingKickoff = e.home
        sim.shootAt(e.home)
        if (e.home == plan.homeIsManaged) audio.goalFor(hScore + aScore) else audio.goalAgainst()
    }

    fun makeSub(onIdx: Int) {
        val off = pickedOff ?: return
        val pos = onField.indexOf(off)
        if (pos < 0 || onIdx !in bench || subsUsed >= MAX_SUBS) return
        onField[pos] = onIdx          // the bench player takes the field
        bench.remove(onIdx)
        pickedOff = null              // the player coming off cannot return
        subsUsed++
        if (subsUsed >= MAX_SUBS || bench.isEmpty()) showSubs = false
    }

    LaunchedEffect(plan) {
        audio.welcome()
        audio.whistle() // kick-off
        var lastNs = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dtMs = (now - lastNs) / 1_000_000f
            lastNs = now
            if (!finished && !showSubs) {       // the subs panel pauses the match
                val adv = dtMs * speed
                elapsed += adv
                val m = (elapsed / MS_PER_MIN).toInt().coerceAtMost(90)
                if (m != minute) minute = m
                while (evIdx < plan.events.size && plan.events[evIdx].minute <= m) {
                    scoreGoal(plan.events[evIdx]); evIdx++
                }
                pendingKickoff?.let { who ->
                    if (elapsed >= flashUntil) {
                        sim.kickoff(who); pendingKickoff = null
                        audio.whistle() // restart after the goal
                    }
                }
                sim.step(minOf(adv / 1000f, 0.05f))
                if (elapsed >= MS_PER_MIN * 90f) {
                    finished = true
                    last = "Full time  ${plan.homeShort} ${plan.homeGoals} - ${plan.awayGoals} ${plan.awayShort}"
                    audio.fullTime()
                }
            }
            redraw++
        }
    }

    fun skip() {
        while (evIdx < plan.events.size) {
            if (plan.events[evIdx].home) hScore++ else aScore++
            evIdx++
        }
        minute = 90
        elapsed = MS_PER_MIN * 90f
        finished = true
        last = "Full time  ${plan.homeShort} ${plan.homeGoals} - ${plan.awayGoals} ${plan.awayShort}"
        audio.fullTime()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF06140D))) {
        if (pitch != null) {
            Image(pitch, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Canvas(Modifier.fillMaxSize()) {
            redraw // subscribe: redraw every animation frame
            fun map(fx: Float, fy: Float): Offset {
                val topX = lp(q.tl[0].toFloat(), q.tr[0].toFloat(), fx)
                val topY = lp(q.tl[1].toFloat(), q.tr[1].toFloat(), fx)
                val botX = lp(q.bl[0].toFloat(), q.br[0].toFloat(), fx)
                val botY = lp(q.bl[1].toFloat(), q.br[1].toFloat(), fx)
                return Offset(lp(topX, botX, fy) * size.width, lp(topY, botY, fy) * size.height)
            }
            // ball (drawn under the nearest players is fine at this scale)
            val bp = map(sim.bx, sim.by)
            val br = size.height * (0.005f + 0.004f * sim.by)   // ~half a player-width; was 2x too big
            drawOval(Color(0x55000000), topLeft = Offset(bp.x - br, bp.y - br * 0.4f), size = androidx.compose.ui.geometry.Size(br * 2, br * 0.8f))
            drawCircle(Color.White, radius = br, center = Offset(bp.x, bp.y - br))
            drawCircle(Color(0xFF202020), radius = br, center = Offset(bp.x, bp.y - br), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))

            val frame = ((elapsed / 90f).toInt()) % 5
            // draw far players first (smaller y) so nearer ones overlap correctly
            val order = (0 until sim.n).sortedBy { sim.py[it] }
            for (i in order) {
                val home = i < 11
                val diving = sim.diveT[i] > 0f
                val mv = sim.moving[i] && !diving
                val bmp = when {
                    diving && home -> homeIdle
                    diving -> awayIdle
                    mv && home -> homeRun[frame]
                    mv && !home -> awayRun[frame]
                    home -> homeIdle
                    else -> awayIdle
                } ?: continue
                val pos = map(sim.px[i], sim.py[i])
                val persp = 0.62f + 0.62f * sim.py[i]
                val hPx = size.height * 0.072f * persp
                val wPx = hPx * 30f / 32f
                val faceRight = if (mv) sim.vx[i] > 0f else home
                // tip the diving keeper toward his post (out-and-up over the dive)
                val lean = if (diving) {
                    val prog = (1f - sim.diveT[i] / sim.diveDur[i].coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                    sim.diveSign[i] * 78f * kotlin.math.sin(prog * Math.PI.toFloat())
                } else 0f
                drawSprite(bmp, pos, wPx, hPx, faceRight, lean)
            }
        }

        // top scoreboard
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                .background(Overlay).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(plan.homeShort, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("$hScore - $aScore", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(plan.awayShort, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("$minute'", color = Ink, style = MaterialTheme.typography.titleMedium)
            conditionLabel(plan.condition)?.let {
                Text(it, color = Color(0xFFBFE0FF), style = MaterialTheme.typography.labelMedium)
            }
        }

        if (elapsed < flashUntil && !finished) {
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp)).background(Color(0xE6132B1C)).padding(horizontal = 22.dp, vertical = 12.dp)) {
                Text(last, color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            }
        }

        // bottom strip: latest event + controls
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Overlay).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                last,
                color = Ink,
                modifier = Modifier.padding(end = 10.dp),
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!finished) {
                    if (bench.isNotEmpty() && subsUsed < MAX_SUBS) {
                        MatchPill("Subs $subsUsed/$MAX_SUBS") { pickedOff = null; showSubs = true }
                        Spacer(Modifier.padding(end = 8.dp))
                    }
                    MatchPill("x$speed") { speed = when (speed) { 1 -> 2; 2 -> 4; else -> 1 } }
                    Spacer(Modifier.padding(end = 8.dp))
                    MatchPill("Skip") { skip() }
                } else {
                    MatchPill("Continue", primary = true) { onFinish() }
                }
            }
        }

        if (showSubs && !finished) {
            SubPanel(
                data = data,
                onField = onField,
                bench = bench,
                pickedOff = pickedOff,
                subsUsed = subsUsed,
                onPickOff = { pickedOff = if (pickedOff == it) null else it },
                onBringOn = { makeSub(it) },
                onClose = { showSubs = false; pickedOff = null },
            )
        }
    }
}

/** The in-match substitution panel: pick a player to take off (left), then a bench
 *  player to bring on (right). Full-screen scrim; pauses the match while open. */
@Composable
private fun SubPanel(
    data: GameData,
    onField: List<Int>,
    bench: List<Int>,
    pickedOff: Int?,
    subsUsed: Int,
    onPickOff: (Int) -> Unit,
    onBringOn: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE605100A)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).padding(16.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0C1F14))
                // consume taps on the card so they don't fall through to the close-scrim
                .clickable(onClick = {}).padding(16.dp),
        ) {
            Text("Substitutions  $subsUsed/$MAX_SUBS", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                if (pickedOff == null) "Tap a player to take off." else "Now tap a substitute to bring on.",
                color = Ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubColumn("On pitch", onField, Modifier.weight(1f)) { idx ->
                    SubRow(data, idx, selected = idx == pickedOff) { onPickOff(idx) }
                }
                SubColumn("Bench", bench, Modifier.weight(1f)) { idx ->
                    SubRow(data, idx, selected = false, enabled = pickedOff != null) { onBringOn(idx) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                MatchPill("Done", primary = true) { onClose() }
            }
        }
    }
}

@Composable
private fun SubColumn(title: String, ids: List<Int>, modifier: Modifier, row: @Composable (Int) -> Unit) {
    Column(modifier) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            if (ids.isEmpty()) Text("(none)", color = Ink, style = MaterialTheme.typography.labelMedium)
            ids.forEach { row(it) }
        }
    }
}

@Composable
private fun SubRow(data: GameData, idx: Int, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val p: Player = data.players.getOrNull(idx) ?: return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) Gold else Color(0xFF173A26))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fg = if (selected) Color(0xFF06140D) else if (enabled) Ink else Color(0xFF6F8479)
        if (p.isGoalkeeper) Text("GK", color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(p.name, color = fg, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("${p.rating}", color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun DrawScope.drawSprite(bmp: ImageBitmap, feet: Offset, w: Float, h: Float, faceRight: Boolean, leanDeg: Float = 0f) {
    val dstOffset = IntOffset((feet.x - w / 2f).toInt(), (feet.y - h).toInt())
    val dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    val src = IntSize(bmp.width, bmp.height)
    rotate(degrees = leanDeg, pivot = feet) {
        if (faceRight) {
            scale(scaleX = -1f, scaleY = 1f, pivot = feet) {
                drawImage(bmp, srcOffset = IntOffset.Zero, srcSize = src, dstOffset = dstOffset, dstSize = dstSize)
            }
        } else {
            drawImage(bmp, srcOffset = IntOffset.Zero, srcSize = src, dstOffset = dstOffset, dstSize = dstSize)
        }
    }
}

@Composable
private fun MatchPill(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (primary) Gold else Color(0xFF274234))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color(0xFF06140D) else Ink,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

private fun lp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
