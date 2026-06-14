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
fun RoomHost(data: GameData, career: Career, onPlayMatch: () -> Unit, onExit: () -> Unit) {
    var room by remember { mutableStateOf(Room.OFFICE) }

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
            21 -> onPlayMatch()
            22 -> onExit()
            else -> {}
        }
    }

    RoomShell(onIcon = ::nav) {
        when (room) {
            Room.OFFICE -> SceneArea("img/scene/MANASCR.png", officeObjects { room = it })
            Room.BOARDROOM -> SceneArea("img/scene/CHAIRSCR.png", emptyList())
            Room.BANK -> SceneArea("img/scene/BANKSCR.png", emptyList())
            Room.DUGOUT -> SceneArea("img/scene/BENCHSCR.png", emptyList()) { MatchStrip(data, career, onPlayMatch) }
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

@Composable
private fun MatchStrip(data: GameData, career: Career, onPlayMatch: () -> Unit) {
    val last = career.lastFixtureForManaged()
    val next = career.nextFixtureForManaged()
    Row(
        Modifier.fillMaxWidth().background(Color(0xDD06140D)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (last != null) Text("Last: ${resultLine(data, career, last)}", color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (next != null) "Next: ${resultLine(data, career, next)}" else "Season complete",
                color = Sub,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PillButton(if (career.seasonComplete) "Done" else "Play MD ${career.nextRound + 1}", enabled = !career.seasonComplete, onClick = onPlayMatch)
    }
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
                    Modifier.fillMaxWidth().background(if (mine) Color(0xFF14543A) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = if (mine) Gold else Sub, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
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
                PillButton("‹") { formIdx = (formIdx - 1 + forms.size) % forms.size }
                Text("Formation ${formIdx + 1}/${forms.size}", color = Ink, style = MaterialTheme.typography.bodyMedium)
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
            val form = forms.getOrNull(formIdx).orEmpty()
            form.forEachIndexed { i, pos ->
                if (pos.size >= 2) {
                    val name = squad.getOrNull(i)?.name?.substringAfterLast(' ') ?: "-"
                    PlayerChip(i + 1, name, Modifier.offset(x = w * pos[0].toFloat() - 26.dp, y = h * pos[1].toFloat() - 18.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(number: Int, name: String, modifier: Modifier) {
    Column(modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(ShirtRed), contentAlignment = Alignment.Center) {
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
