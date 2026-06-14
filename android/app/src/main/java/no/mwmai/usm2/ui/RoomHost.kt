package no.mwmai.usm2.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.mwmai.usm2.GameData
import no.mwmai.usm2.Player
import no.mwmai.usm2.engine.Career
import no.mwmai.usm2.engine.Fixture
import no.mwmai.usm2.engine.Standings

private val Ink = Color(0xFFE8F0E8)
private val Sub = Color(0xFFB7C7BC)
private val Bars = Color(0xFF09140D)
private val NavBg = Color(0xFF12201A)
private val PitchGreen = Color(0xFF2E7D43)
private val ShirtRed = Color(0xFFC62828)
private val Gold = Color(0xFFE7C84A)

enum class Room { OFFICE, BOARDROOM, DUGOUT, BANK, NEWS, TEAM, TABLE, TACTICS }

/** A clickable region in scene-image space (fractions of the displayed scene). */
private class Hotspot(
    val fx: Float, val fy: Float, val fw: Float, val fh: Float,
    val label: String,
    val onTap: () -> Unit,
)

// The big touch nav: the original's tiny mouse toolbar is unhittable on a phone,
// so the main functions are exposed as large labelled buttons using the real
// icons. Each pair is (toolbar-icon index, label). Bank/Dugout provisional.
private val NAV = listOf(
    2 to "Office", 15 to "Team", 14 to "Tactics", 8 to "Table",
    7 to "News", 11 to "Bank", 16 to "Dugout", 21 to "Play", 22 to "Exit",
)

private const val SCENE_W = 640f
private const val SCENE_H = 437f   // PIC height after the baked toolbar is cropped

@Composable
fun RoomHost(
    data: GameData,
    career: Career,
    onPlayMatch: () -> Unit,
    onRollover: () -> Unit,
    onExit: () -> Unit,
) {
    var room by remember { mutableStateOf(Room.OFFICE) }
    // When set, the managed club's next fixture is watched as an animated match
    // before its result (and the rest of the round) is recorded by onPlayMatch.
    var match by remember { mutableStateOf<no.mwmai.usm2.ui.MatchPlan?>(null) }

    fun startMatch() {
        if (career.seasonComplete) {
            onRollover()
            return
        }
        val plan = buildMatchPlan(data, career)
        if (plan == null) onPlayMatch() else match = plan
    }

    fun nav(icon: Int) {
        when (icon) {
            2 -> room = Room.OFFICE
            7 -> room = Room.NEWS
            8 -> room = Room.TABLE
            10 -> room = Room.BOARDROOM
            11 -> room = Room.BANK
            14 -> room = Room.TACTICS
            15 -> room = Room.TEAM
            16 -> room = Room.DUGOUT
            21 -> startMatch()
            22 -> onExit()
            else -> {}
        }
    }

    val plan = match
    if (plan != null) {
        MatchView(data, plan) {
            match = null
            onPlayMatch()
        }
        return
    }

    RoomShell(onIcon = ::nav) {
        when (room) {
            Room.OFFICE -> SceneArea("img/scene/MANASCR.png", officeObjects { room = it })
            Room.BOARDROOM -> SceneArea("img/scene/CHAIRSCR.png", emptyList())
            Room.BANK -> SceneArea("img/scene/BANKSCR.png", emptyList()) { BankPanel(data, career) }
            Room.DUGOUT -> SceneArea("img/scene/BENCHSCR.png", emptyList()) { MatchCentre(data, career, ::startMatch, onRollover) }
            Room.NEWS -> SceneArea("img/scene/NEWS.png", emptyList()) { NewsStrip(data, career) }
            Room.TEAM -> TeamContent(data, career)
            Room.TABLE -> TableContent(data, career)
            Room.TACTICS -> PitchLineup(data, career)
        }
    }
}

@Composable
private fun RoomShell(onIcon: (Int) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bars)) {
        BigNav(onIcon)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun BigNav(onIcon: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).background(NavBg)) {
        NAV.forEach { (idx, label) ->
            val ic = rememberAssetImage("img/tb/ic_%02d.png".format(idx))
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onIcon(idx) }.padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (ic != null) {
                    Image(bitmap = ic, contentDescription = label, modifier = Modifier.size(34.dp), contentScale = ContentScale.Fit)
                }
                Text(label, color = Ink, maxLines = 1, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ---- scene room: denoised PIC + object hotspots ----------------------------

@Composable
private fun SceneArea(asset: String, objects: List<Hotspot>, bottom: (@Composable () -> Unit)? = null) {
    val img = rememberAssetImage(asset)
    var note by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Bars), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(SCENE_W / SCENE_H, matchHeightConstraintsFirst = true)) {
            if (img != null) {
                Image(bitmap = img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                val h = maxHeight
                objects.forEach { hs ->
                    HotspotBox(w, h, hs.fx, hs.fy, hs.fw, hs.fh) {
                        note = hs.label
                        hs.onTap()
                    }
                }
            }
            note?.let {
                Box(Modifier.align(Alignment.BottomCenter).padding(10.dp)) {
                    Text(
                        it,
                        color = Color(0xFF06140D),
                        modifier = Modifier.background(Gold).padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            bottom?.let { Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { it() } }
        }
    }
}

@Composable
private fun HotspotBox(w: Dp, h: Dp, fx: Float, fy: Float, fw: Float, fh: Float, onTap: () -> Unit) {
    Box(Modifier.offset(x = w * fx, y = h * fy).size(width = w * fw, height = h * fh).clickable(onClick = onTap))
}

/** Office objects mapped to distinct screens; functions not built yet say so. */
private fun officeObjects(go: (Room) -> Unit): List<Hotspot> = listOf(
    Hotspot(0.031f, 0.027f, 0.141f, 0.275f, "Noticeboard") { go(Room.NEWS) },
    Hotspot(0.234f, 0.016f, 0.281f, 0.297f, "Window: the stadium") {},
    Hotspot(0.242f, 0.382f, 0.320f, 0.275f, "Phone: transfers (coming soon)") {},
    Hotspot(0.563f, 0.176f, 0.094f, 0.435f, "Filing cabinet: squad") { go(Room.TEAM) },
    Hotspot(0.672f, 0.062f, 0.141f, 0.160f, "Team photo: squad") { go(Room.TEAM) },
    Hotspot(0.719f, 0.268f, 0.156f, 0.206f, "TV: league table") { go(Room.TABLE) },
    Hotspot(0.133f, 0.657f, 0.141f, 0.114f, "Printer: reports (coming soon)") {},
)

// ---- bottom strips ---------------------------------------------------------

// The dugout = the match centre: form guide, league position, last/next match,
// and the advance control (Play Matchday, or Start next Season once one is over).
@Composable
private fun MatchCentre(data: GameData, career: Career, onPlayMatch: () -> Unit, onRollover: () -> Unit) {
    val table = remember(career) { Standings.compute(career.clubIds.size, career.fixtures) }
    val rank = table.indexOfFirst { it.clubIndex == career.managedIndex }.let { if (it < 0) 0 else it + 1 }
    val played = remember(career) {
        career.fixtures
            .filter { it.played && (it.home == career.managedIndex || it.away == career.managedIndex) }
            .sortedBy { it.round }
    }
    val form = played.takeLast(5)
    val last = played.lastOrNull()
    val next = career.nextFixtureForManaged()
    Column(
        Modifier.fillMaxWidth().background(Color(0xE60A1810)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Match Centre", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("${ordinal(rank)} of ${career.clubIds.size}", color = Ink, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Form ", color = Sub, style = MaterialTheme.typography.labelMedium)
            if (form.isEmpty()) {
                Text("no matches yet", color = Sub, style = MaterialTheme.typography.labelSmall)
            } else {
                form.forEach { f ->
                    val o = managedOutcome(career, f)
                    val c = when (o) {
                        'W' -> Color(0xFF2E9E5B); 'L' -> Color(0xFFC0413B); else -> Color(0xFF8A8F45)
                    }
                    Box(
                        Modifier.padding(end = 4.dp).size(20.dp).clip(RoundedCornerShape(4.dp)).background(c),
                        contentAlignment = Alignment.Center,
                    ) { Text(o.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (last != null) Text("Last: ${resultLine(data, career, last)}", color = Ink, style = MaterialTheme.typography.bodySmall)
        Text(
            if (next != null) "Next: ${resultLine(data, career, next)}  (${if (next.home == career.managedIndex) "Home" else "Away"})"
            else "Season ${career.season} complete",
            color = Sub,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        if (career.seasonComplete) {
            seasonOutcomeLabel(career)?.let {
                Text(it, color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
            if (career.pyramid.isNotEmpty()) {
                PillButton("Start Season ${career.season + 1}", onClick = onRollover)
            } else {
                PillButton("Season over", enabled = false, onClick = {})
            }
        } else {
            PillButton("Play Matchday ${career.nextRound + 1}", onClick = onPlayMatch)
        }
    }
}

/** W/D/L of the managed club in a played fixture. */
private fun managedOutcome(career: Career, f: Fixture): Char {
    if (!f.played) return '-'
    val mineHome = f.home == career.managedIndex
    val gf = if (mineHome) f.homeGoals else f.awayGoals
    val ga = if (mineHome) f.awayGoals else f.homeGoals
    return if (gf > ga) 'W' else if (gf < ga) 'L' else 'D'
}

/** End-of-season verdict for the managed club, or null mid-season / on old saves. */
private fun seasonOutcomeLabel(career: Career): String? {
    if (!career.seasonComplete || career.pyramid.isEmpty()) return null
    val rank = career.managedFinalRank()
    val size = career.clubIds.size
    val champion = rank == 1
    val promoted = career.hasPromotion && rank <= career.promotionSlots
    val relegated = career.hasRelegation && rank > size - career.promotionSlots
    return when {
        champion && !career.hasPromotion -> "Champions!"
        champion -> "Champions — promoted!"
        promoted -> "Promoted (${ordinal(rank)})"
        relegated -> "Relegated (${ordinal(rank)})"
        else -> "Finished ${ordinal(rank)}"
    }
}

// Bank = the club's finances. USM2 ships NO balance in the seed DB (the stats
// block is zeroed until a new game initialises it), so these are honest figures
// derived from the real squad attributes, not invented "decoded" numbers.
@Composable
private fun BankPanel(data: GameData, career: Career) {
    val club = data.clubsById[career.managedClubId]
    val squad = remember(career.managedClubId) { data.squad(career.managedClubId) }
    val valueK = remember(career.managedClubId) { squad.sumOf { playerValueK(it) } }
    val avg = if (squad.isNotEmpty()) squad.map { it.rating }.average().toInt() else 0
    val prized = squad.maxByOrNull { it.rating }
    Column(
        Modifier.fillMaxWidth().background(Color(0xE60A1810)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Finances", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FinanceRow("Club", club?.name ?: "-")
        FinanceRow("Stadium", club?.stadium ?: "-")
        FinanceRow("Tier", "${career.divisionName} · S${career.season}")
        FinanceRow("Squad value (est.)", money(valueK))
        FinanceRow("Squad", "${squad.size} players · avg rating $avg")
        prized?.let { FinanceRow("Prize asset", "${it.name} (${it.rating}) · ${money(playerValueK(it))}") }
        Spacer(Modifier.height(4.dp))
        Text(
            "Estimates from real squad ratings; the seed DB carries no balance.",
            color = Sub,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FinanceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Sub, modifier = Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

/** A player's transfer value in £k, from his real overall rating (steep curve). */
private fun playerValueK(p: Player): Long {
    val r = p.rating.coerceIn(1, 99) / 100.0
    return (r * r * r * 14_000).toLong()
}

private fun money(k: Long): String =
    if (k >= 1000) "£%.1fM".format(k / 1000.0) else "£${k}k"

private fun ordinal(n: Int): String {
    if (n <= 0) return "-"
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}

@Composable
private fun NewsStrip(data: GameData, career: Career) {
    val lastRound = career.fixtures.filter { it.played }.maxOfOrNull { it.round }
    val results = career.fixtures.filter { it.played && it.round == lastRound }
    Column(Modifier.fillMaxWidth().background(Color(0xDD06140D)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            if (lastRound != null) "Matchday ${lastRound + 1}" else "No matches played",
            color = Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
        results.take(4).forEach { Text(resultLine(data, career, it), color = Ink, style = MaterialTheme.typography.bodySmall) }
    }
}

// ---- team & table ----------------------------------------------------------

@Composable
private fun TeamContent(data: GameData, career: Career) {
    val squad = remember(career.managedClubId) { data.squad(career.managedClubId) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("${clubName(data, career, career.managedIndex)} squad", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(squad) { i, p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Sub, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                    RatingChip(p.rating)
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, color = Ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (p.key) Text("★", color = Gold)
                    Spacer(Modifier.width(8.dp))
                    Text("age ${p.age}", color = Sub, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TableContent(data: GameData, career: Career) {
    val table = remember(career) { Standings.compute(career.clubIds.size, career.fixtures) }
    val n = table.size
    val promoCut = if (career.hasPromotion) career.promotionSlots else 0
    val relegCut = if (career.hasRelegation) career.promotionSlots else 0
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("${career.divisionName} · S${career.season}", color = Gold, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("#", color = Sub, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
            Text("Club", color = Sub, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            listOf("P", "W", "D", "L", "GD", "Pts").forEach {
                Text(it, color = Sub, modifier = Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(table, key = { _, r -> r.clubIndex }) { i, r ->
                val mine = r.clubIndex == career.managedIndex
                val promo = i < promoCut
                val releg = i >= n - relegCut
                val accent = when {
                    promo -> Color(0xFF2E9E5B)
                    releg -> Color(0xFFC0413B)
                    else -> Color.Transparent
                }
                Row(
                    Modifier.fillMaxWidth().background(if (mine) Color(0xFF14543A) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).height(18.dp).background(accent))
                    Text("${i + 1}", color = if (mine) Gold else Sub, modifier = Modifier.width(24.dp).padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
                    Text(clubName(data, career, r.clubIndex), color = Ink, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    listOf(r.played, r.won, r.drawn, r.lost, r.goalDifference, r.points).forEachIndexed { idx, v ->
                        Text(
                            if (idx == 4 && v > 0) "+$v" else "$v",
                            color = Ink,
                            fontWeight = if (idx == 5) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.width(34.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// ---- tactics: real FORM.DAT formation on a pitch ---------------------------

@Composable
private fun PitchLineup(data: GameData, career: Career) {
    val forms = data.formations
    var formIdx by remember { mutableStateOf(0) }
    val squad = remember(career.managedClubId) { data.squad(career.managedClubId) }
    val form = forms.getOrNull(formIdx).orEmpty()
    val gkSlot = remember(form) { form.indices.maxByOrNull { form[it].getOrElse(1) { 0.0 } } ?: 0 }
    val depthOrder = remember(form) { form.indices.sortedByDescending { form[it].getOrElse(1) { 0.0 } } }
    val lineup = remember(career.managedClubId, formIdx, forms) { assignLineup(squad, form) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF0B3D24)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Tactics", color = Gold, fontWeight = FontWeight.Bold)
            if (form.isNotEmpty()) {
                Text(formationLabel(form), color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            if (forms.isNotEmpty()) {
                PillButton("‹") { formIdx = (formIdx - 1 + forms.size) % forms.size }
                Text("${formIdx + 1}/${forms.size}", color = Ink, style = MaterialTheme.typography.bodyMedium)
                PillButton("›") { formIdx = (formIdx + 1) % forms.size }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(PitchGreen)) {
            val w = maxWidth
            val h = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                val s = Stroke(width = 3f)
                val line = Color(0xFFDDEFE0)
                drawRect(color = line, topLeft = Offset(6f, 6f), size = Size(size.width - 12f, size.height - 12f), style = s)
                drawLine(line, Offset(6f, size.height / 2f), Offset(size.width - 6f, size.height / 2f), strokeWidth = 3f)
                drawCircle(line, radius = size.height * 0.11f, center = Offset(size.width / 2f, size.height / 2f), style = s)
            }
            form.forEachIndexed { i, pos ->
                if (pos.size >= 2) {
                    val p = lineup.getOrNull(i)
                    val name = p?.name?.substringAfterLast(' ') ?: "-"
                    PlayerChip(
                        depthOrder.indexOf(i) + 1,
                        name,
                        i == gkSlot,
                        Modifier.offset(x = w * pos[0].toFloat() - 26.dp, y = h * pos[1].toFloat() - 18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Maps the best available XI onto the formation slots by the players' REAL
 * attributes (the source data, not slot index): the genuine keeper (top
 * Goalkeeping) takes the deepest slot, and the rest fill front-to-back by how
 * attacking they are, so forwards lead the line and defenders sit at the back.
 */
private fun assignLineup(squad: List<Player>, form: List<List<Double>>): List<Player?> {
    val n = form.size
    if (n == 0 || squad.isEmpty()) return List(n) { null }
    val gkSlot = form.indices.maxByOrNull { form[it].getOrElse(1) { 0.0 } } ?: 0
    val keeperIdx = squad.indices.maxByOrNull { squad[it].goalkeeping } ?: 0
    val outfield = squad.filterIndexed { i, _ -> i != keeperIdx }
        .sortedByDescending { it.rating }
        .take(n - 1)
        .sortedByDescending { it.attacking - it.defending } // most attacking first
    val slotsFrontToBack = form.indices.filter { it != gkSlot }
        .sortedBy { form[it].getOrElse(1) { 0.0 } } // lowest y (front) first
    val out = arrayOfNulls<Player>(n)
    out[gkSlot] = squad[keeperIdx]
    slotsFrontToBack.forEachIndexed { i, slot -> out[slot] = outfield.getOrNull(i) }
    return out.toList()
}

/** "4-4-2"-style label from the slot depths (outfield bands, back to front). */
private fun formationLabel(form: List<List<Double>>): String {
    if (form.size < 11) return ""
    val gkSlot = form.indices.maxByOrNull { form[it].getOrElse(1) { 0.0 } } ?: 0
    val ys = form.indices.filter { it != gkSlot }
        .map { form[it].getOrElse(1) { 0.0 } }
        .sortedDescending()
    val bands = mutableListOf<Int>()
    var count = 1
    for (i in 1 until ys.size) {
        if (ys[i - 1] - ys[i] > 0.09) { bands.add(count); count = 1 } else count++
    }
    bands.add(count)
    return bands.joinToString("-")
}

@Composable
private fun PlayerChip(number: Int, name: String, isKeeper: Boolean, modifier: Modifier) {
    Column(modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (isKeeper) Gold else ShirtRed),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                color = if (isKeeper) Color(0xFF06140D) else Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            name,
            color = Color.White,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.background(Color(0x99000000)).padding(horizontal = 3.dp),
        )
    }
}

// ---- shared ----------------------------------------------------------------

@Composable
private fun PillButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Gold else Color(0xFF3A4A40))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color(0xFF06140D), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

private fun clubName(data: GameData, career: Career, idx: Int): String =
    data.clubsById[career.clubIds.getOrNull(idx)]?.name ?: "?"

private fun resultLine(data: GameData, career: Career, f: Fixture): String {
    val h = clubName(data, career, f.home)
    val a = clubName(data, career, f.away)
    return if (f.played) "$h ${f.homeGoals}-${f.awayGoals} $a" else "$h v $a"
}
