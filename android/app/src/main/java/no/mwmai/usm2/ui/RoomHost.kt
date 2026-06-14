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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.mwmai.usm2.GameData
import no.mwmai.usm2.engine.Career
import no.mwmai.usm2.engine.Fixture
import no.mwmai.usm2.engine.Standings

private val Ink = Color(0xFFE8F0E8)
private val Sub = Color(0xFFB7C7BC)
private val Bars = Color(0xFF09140D)   // pillarbox surround
private val PitchGreen = Color(0xFF2E7D43)
private val ShirtRed = Color(0xFFC62828)
private val Gold = Color(0xFFE7C84A)

enum class Room { OFFICE, BOARDROOM, DUGOUT, BANK, NEWS, TEAM, TABLE, TACTICS }

/** A clickable region in 640x480 image space (fractions of the image). */
private class Hotspot(
    val fx: Float, val fy: Float, val fw: Float, val fh: Float,
    val label: String? = null,
    val onTap: () -> Unit,
)

// Toolbar icon geometry, measured from the real baked toolbar (640x480 image).
private const val IMG_W = 640f
private const val IMG_H = 480f
private const val TB_X0 = 8f
private const val TB_W = 26f
private const val TB_Y = 15f
private const val TB_H = 28f

/**
 * The faithful room shell. Each room shows the REAL full-screen .PIC (with its
 * own baked toolbar) at full height, smooth-scaled, with invisible hotspots laid
 * over the real toolbar icons and the room's objects. Data rooms (team / table /
 * tactics) carry the same toolbar as a real icon row so navigation is consistent.
 *
 * Toolbar index -> room mapping is read off the actual icons. Bank (11) and
 * dugout (16) are provisional pending on-device confirmation.
 */
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

    val toolbar = remember { (0 until 24).map { i -> i } }

    when (room) {
        Room.OFFICE -> RoomScene("img/MANASCR.png", toolbar, ::nav, officeObjects { room = it })
        Room.BOARDROOM -> RoomScene("img/CHAIRSCR.png", toolbar, ::nav, emptyList())
        Room.DUGOUT -> RoomScene("img/BENCHSCR.png", toolbar, ::nav, emptyList(), bottom = {
            MatchStrip(data, career, onPlayMatch)
        })
        Room.BANK -> RoomScene("img/BANKSCR.png", toolbar, ::nav, emptyList())
        Room.NEWS -> RoomScene("img/NEWS.png", toolbar, ::nav, emptyList(), bottom = { NewsStrip(data, career) })
        Room.TEAM -> DataRoom(::nav) { TeamContent(data, career) }
        Room.TABLE -> DataRoom(::nav) { TableContent(data, career) }
        Room.TACTICS -> DataRoom(::nav) { PitchLineup(data, career) }
    }
}

// ---- scene room: real PIC + aligned hotspots -------------------------------

@Composable
private fun RoomScene(
    asset: String,
    toolbarIcons: List<Int>,
    onIcon: (Int) -> Unit,
    objects: List<Hotspot>,
    bottom: (@Composable () -> Unit)? = null,
) {
    val img = rememberAssetImage(asset)
    var note by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Bars), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(IMG_W / IMG_H, matchHeightConstraintsFirst = true)) {
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                val h = maxHeight
                // real toolbar icons
                toolbarIcons.forEach { i ->
                    val fx = (TB_X0 + i * TB_W) / IMG_W
                    HotspotBox(w, h, fx, TB_Y / IMG_H, TB_W / IMG_W, TB_H / IMG_H) { onIcon(i) }
                }
                // room objects
                objects.forEach { hs ->
                    HotspotBox(w, h, hs.fx, hs.fy, hs.fw, hs.fh) {
                        note = hs.label
                        hs.onTap()
                    }
                }
            }
            note?.let {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 40.dp)) {
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
    Box(
        Modifier
            .offset(x = w * fx, y = h * fy)
            .size(width = w * fw, height = h * fh)
            .clickable(onClick = onTap),
    )
}

/** Office objects -> a name label (so they respond) and a best-guess room. The
 * object->function mapping is provisional and to be confirmed with Mats. */
private fun officeObjects(go: (Room) -> Unit): List<Hotspot> = listOf(
    Hotspot(0.031f, 0.115f, 0.141f, 0.250f, "Noticeboard") { go(Room.NEWS) },
    Hotspot(0.234f, 0.104f, 0.281f, 0.271f, "Window") {},
    Hotspot(0.242f, 0.438f, 0.320f, 0.250f, "Desk & phone") {},
    Hotspot(0.563f, 0.250f, 0.094f, 0.396f, "Filing cabinet") { go(Room.TEAM) },
    Hotspot(0.672f, 0.146f, 0.141f, 0.146f, "Team photo") { go(Room.TEAM) },
    Hotspot(0.719f, 0.333f, 0.156f, 0.188f, "TV") { go(Room.NEWS) },
    Hotspot(0.133f, 0.688f, 0.141f, 0.104f, "Printer") {},
)

// ---- data room: real toolbar icon row + content ----------------------------

@Composable
private fun DataRoom(onIcon: (Int) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF06140D))) {
        Toolbar(onIcon = onIcon)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

// ---- bottom strips ---------------------------------------------------------

@Composable
private fun MatchStrip(data: GameData, career: Career, onPlayMatch: () -> Unit) {
    val last = career.lastFixtureForManaged()
    val next = career.nextFixtureForManaged()
    Row(
        Modifier.fillMaxWidth().background(Color(0xCC06140D)).padding(horizontal = 14.dp, vertical = 8.dp),
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
        TextButton(if (career.seasonComplete) "Done" else "Play MD ${career.nextRound + 1}", enabled = !career.seasonComplete, onClick = onPlayMatch)
    }
}

@Composable
private fun NewsStrip(data: GameData, career: Career) {
    val lastRound = career.fixtures.filter { it.played }.maxOfOrNull { it.round }
    val results = career.fixtures.filter { it.played && it.round == lastRound }
    Column(Modifier.fillMaxWidth().background(Color(0xCC06140D)).padding(horizontal = 14.dp, vertical = 8.dp)) {
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
                TextButton("‹") { formIdx = (formIdx - 1 + forms.size) % forms.size }
                Text("Formation ${formIdx + 1}/${forms.size}", color = Ink, style = MaterialTheme.typography.bodyMedium)
                TextButton("›") { formIdx = (formIdx + 1) % forms.size }
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
private fun TextButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Gold else Color(0xFF3A4A40))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
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
