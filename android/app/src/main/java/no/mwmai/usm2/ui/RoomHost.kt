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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import no.mwmai.usm2.GameData
import no.mwmai.usm2.engine.Career
import no.mwmai.usm2.engine.Fixture
import no.mwmai.usm2.engine.Standings

private val Ink = Color(0xFFE8F0E8)
private val Sub = Color(0xFFB7C7BC)
private val Panel = Color(0xCC0A1F14)
private val PitchGreen = Color(0xFF2E7D43)
private val ShirtRed = Color(0xFFC62828)
private val Gold = Color(0xFFE7C84A)

enum class Room { OFFICE, BOARDROOM, DUGOUT, BANK, NEWS, TEAM, TABLE, TACTICS }

/**
 * The faithful, room-based game shell: the real toolbar on top, a room scene
 * below. Navigation mirrors the original: each toolbar icon opens a room. The
 * index -> action mapping is derived from the actual toolbar art (the icons were
 * cropped and identified by eye). Bank and Dugout are flagged provisional until
 * confirmed on-device; everything else is a confident visual ID.
 */
@Composable
fun RoomHost(data: GameData, career: Career, onPlayMatch: () -> Unit, onExit: () -> Unit) {
    var room by remember { mutableStateOf(Room.OFFICE) }
    Column(Modifier.fillMaxSize().background(Color(0xFF06140D))) {
        Toolbar(onIcon = { i ->
            when (i) {
                2 -> room = Room.OFFICE        // "BOSS" sign = manager's office
                7 -> room = Room.NEWS          // newspaper
                8 -> room = Room.TABLE         // league-table sheet
                10 -> room = Room.BOARDROOM    // chairman portrait
                11 -> room = Room.BANK         // accounts ledger (provisional)
                14 -> room = Room.TACTICS      // tactics board
                15 -> room = Room.TEAM         // player in kit
                16 -> room = Room.DUGOUT       // substitutes / bench (provisional)
                21 -> onPlayMatch()            // football = play match
                22 -> onExit()                 // EXIT
                else -> {}                     // other icons: not wired yet
            }
        })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (room) {
                Room.OFFICE -> SceneRoom("img/scene/MANASCR.png") {
                    PanelText("Manager's Office", "${data.clubName(career, career.managedIndex)} · Season ${career.season}")
                }
                Room.BOARDROOM -> SceneRoom("img/scene/CHAIRSCR.png") {
                    val pos = leaguePosition(career)
                    PanelText("The Boardroom", "Currently $pos. The chairman is watching.")
                }
                Room.BANK -> SceneRoom("img/scene/BANKSCR.png") {
                    PanelText("The Bank", "Club finances are not modelled yet.")
                }
                Room.NEWS -> SceneRoom("img/scene/NEWS.png") { NewsPanel(data, career) }
                Room.DUGOUT -> SceneRoom("img/scene/BENCHSCR.png") { DugoutPanel(data, career, onPlayMatch) }
                Room.TEAM -> TeamRoom(data, career)
                Room.TABLE -> TableRoom(data, career)
                Room.TACTICS -> PitchLineup(data, career)
            }
        }
    }
}

// ---- scene rooms (real art + translucent overlay) --------------------------

@Composable
private fun SceneRoom(asset: String, overlay: @Composable () -> Unit) {
    val img = rememberAssetImage(asset)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) { overlay() }
    }
}

@Composable
private fun PanelText(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(body, color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DugoutPanel(data: GameData, career: Career, onPlayMatch: () -> Unit) {
    val last = career.lastFixtureForManaged()
    val next = career.nextFixtureForManaged()
    Column(Modifier.fillMaxWidth().background(Panel).padding(16.dp)) {
        if (last != null) {
            Text("Last: ${resultLine(data, career, last)}", color = Ink, fontWeight = FontWeight.SemiBold)
        }
        Text(
            if (next != null) "Next: ${resultLine(data, career, next)}" else "Season complete",
            color = Sub,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPlayMatch,
            enabled = !career.seasonComplete,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF06140D)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (career.seasonComplete) "Season over" else "Play Matchday ${career.nextRound + 1}",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NewsPanel(data: GameData, career: Career) {
    val lastRound = career.fixtures.filter { it.played }.maxOfOrNull { it.round }
    val results = career.fixtures.filter { it.played && it.round == lastRound }
    Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            if (lastRound != null) "Matchday ${lastRound + 1} results" else "No matches played yet",
            color = Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        results.take(6).forEach {
            Text(resultLine(data, career, it), color = Ink, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- team & table rooms ----------------------------------------------------

@Composable
private fun TeamRoom(data: GameData, career: Career) {
    val squad = remember(career.managedClubId) { data.squad(career.managedClubId) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            "${data.clubName(career, career.managedIndex)} squad",
            color = Gold,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(squad) { i, p ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
private fun TableRoom(data: GameData, career: Career) {
    val table = remember(career) { Standings.compute(career.clubIds.size, career.fixtures) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
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
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (mine) Color(0xFF14543A) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = if (mine) Gold else Sub, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                    Text(
                        data.clubName(career, r.clubIndex),
                        color = Ink,
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
    val squad = remember(career.managedClubId) { data.squad(career.managedClubId).take(11) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF0B3D24)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Tactics", color = Gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (forms.isNotEmpty()) {
                SmallBtn("‹") { formIdx = (formIdx - 1 + forms.size) % forms.size }
                Text("Formation ${formIdx + 1}/${forms.size}", color = Ink, style = MaterialTheme.typography.bodyMedium)
                SmallBtn("›") { formIdx = (formIdx + 1) % forms.size }
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
            val form = forms.getOrNull(formIdx).orEmpty()
            form.forEachIndexed { i, pos ->
                if (pos.size >= 2) {
                    val name = squad.getOrNull(i)?.name?.substringAfterLast(' ') ?: "-"
                    PlayerChip(
                        number = i + 1,
                        name = name,
                        modifier = Modifier.offset(
                            x = w * pos[0].toFloat() - 26.dp,
                            y = h * pos[1].toFloat() - 18.dp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(number: Int, name: String, modifier: Modifier) {
    Column(modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(ShirtRed),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = Color.White, style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF14543A)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, color = Ink, style = MaterialTheme.typography.titleMedium)
    }
}

// ---- shared helpers --------------------------------------------------------

private fun GameData.clubName(career: Career, idx: Int): String =
    clubsById[career.clubIds.getOrNull(idx)]?.name ?: "?"

private fun resultLine(data: GameData, career: Career, f: Fixture): String {
    val h = data.clubName(career, f.home)
    val a = data.clubName(career, f.away)
    return if (f.played) "$h ${f.homeGoals}-${f.awayGoals} $a" else "$h v $a"
}

private fun leaguePosition(career: Career): String {
    val table = Standings.compute(career.clubIds.size, career.fixtures)
    val rank = table.indexOfFirst { it.clubIndex == career.managedIndex }
    if (rank < 0) return "unranked"
    val n = rank + 1
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix of ${career.clubIds.size}"
}
