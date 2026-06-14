package no.mwmai.usm2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.usm2.GameData
import no.mwmai.usm2.engine.Career
import no.mwmai.usm2.engine.Fixture
import no.mwmai.usm2.engine.Standings
import no.mwmai.usm2.engine.TableRow

private val CGreen = androidx.compose.ui.graphics.Color(0xFF0B3D24)
private val CBg = androidx.compose.ui.graphics.Color(0xFF06281A)
private val CInk = androidx.compose.ui.graphics.Color(0xFFE3EFE8)
private val CSub = androidx.compose.ui.graphics.Color(0xFFA8C0B2)
private val CGold = androidx.compose.ui.graphics.Color(0xFFE7C84A)
private val CHighlight = androidx.compose.ui.graphics.Color(0xFF14543A)

private fun GameData.clubName(career: Career, idx: Int): String =
    clubsById[career.clubIds.getOrNull(idx)]?.name ?: "?"

private fun resultLine(data: GameData, career: Career, f: Fixture): String {
    val h = data.clubName(career, f.home)
    val a = data.clubName(career, f.away)
    return if (f.played) "$h ${f.homeGoals} - ${f.awayGoals} $a" else "$h vs $a"
}

@Composable
fun CareerScreen(
    data: GameData,
    career: Career,
    onPlayRound: () -> Unit,
    onTable: () -> Unit,
    onFixtures: () -> Unit,
    onSquad: (String) -> Unit,
    onQuit: () -> Unit,
    onBack: () -> Unit,
) {
    val club = data.clubsById[career.managedClubId]
    val table = remember(career) { Standings.compute(career.clubIds.size, career.fixtures) }
    val mine = table.firstOrNull { it.clubIndex == career.managedIndex }
    val rank = table.indexOfFirst { it.clubIndex == career.managedIndex }.let { if (it < 0) 0 else it + 1 }
    val last = career.lastFixtureForManaged()
    val next = career.nextFixtureForManaged()

    UsmScaffold(club?.name ?: "Career", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Text(
                    "${career.divisionName} · Season ${career.season}",
                    color = CSub,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CGreen)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("League position", color = CSub, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                ordinal(rank),
                                color = CGold,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "of ${career.clubIds.size}",
                                color = CSub,
                                modifier = Modifier.padding(bottom = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (mine != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "P ${mine.played}  ·  ${mine.won}W ${mine.drawn}D ${mine.lost}L  ·  " +
                                    "GD ${signed(mine.goalDifference)}  ·  ${mine.points} pts",
                                color = CInk,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (last != null) {
                item {
                    InfoLine("Last result", resultLine(data, career, last), "Matchday ${last.round + 1}")
                    Spacer(Modifier.height(10.dp))
                }
            }
            item {
                if (next != null) {
                    val venue = if (next.home == career.managedIndex) "Home" else "Away"
                    InfoLine("Next match", resultLine(data, career, next), "Matchday ${next.round + 1} · $venue")
                } else {
                    InfoLine("Next match", "Season complete", "${career.totalRounds} matchdays played")
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                Button(
                    onClick = onPlayRound,
                    enabled = !career.seasonComplete,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CGold, contentColor = CBg),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (career.seasonComplete) "Season over" else "Play Matchday ${career.nextRound + 1}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                CareerLink("League Table", onTable)
                CareerLink("Fixtures & Results", onFixtures)
                CareerLink("My Squad", { onSquad(career.managedClubId) })
                Spacer(Modifier.height(20.dp))
                Text(
                    "Abandon career",
                    color = androidx.compose.ui.graphics.Color(0xFFB07A7A),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable(onClick = onQuit),
                )
            }
        }
    }
}

@Composable
fun TableScreen(data: GameData, career: Career, onBack: () -> Unit) {
    val table = remember(career) { Standings.compute(career.clubIds.size, career.fixtures) }
    UsmScaffold("${career.divisionName} Table", onBack) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { TableHeaderRow() }
            items(table.size, key = { table[it].clubIndex }) { i ->
                TableBodyRow(
                    rank = i + 1,
                    name = data.clubName(career, table[i].clubIndex),
                    row = table[i],
                    highlight = table[i].clubIndex == career.managedIndex,
                )
            }
        }
    }
}

@Composable
fun FixturesScreen(data: GameData, career: Career, onBack: () -> Unit) {
    val byRound = remember(career) { career.fixtures.groupBy { it.round }.toSortedMap() }
    UsmScaffold("Fixtures & Results", onBack) {
        LazyColumn(Modifier.fillMaxSize()) {
            byRound.forEach { (round, fixtures) ->
                item(key = "md-$round") { SectionHeader("Matchday ${round + 1}") }
                items(fixtures, key = { "f-${it.round}-${it.home}-${it.away}" }) { f ->
                    FixtureRow(
                        text = resultLine(data, career, f),
                        played = f.played,
                        mine = f.home == career.managedIndex || f.away == career.managedIndex,
                    )
                }
            }
        }
    }
}

// ---- small building blocks -------------------------------------------------

@Composable
private fun InfoLine(label: String, value: String, sub: String) {
    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF0E3320))) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, color = CSub, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            Text(value, color = CInk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = CSub, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CareerLink(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, color = CInk, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        Modifier.fillMaxWidth().background(CGreen).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", color = CSub, modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall)
        Text("Club", color = CSub, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        StatHead("P"); StatHead("W"); StatHead("D"); StatHead("L"); StatHead("GD"); StatHead("Pts")
    }
}

@Composable
private fun TableBodyRow(rank: Int, name: String, row: TableRow, highlight: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) CHighlight else CBg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            color = if (highlight) CGold else CSub,
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            name,
            color = CInk,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        StatCell(row.played.toString()); StatCell(row.won.toString()); StatCell(row.drawn.toString())
        StatCell(row.lost.toString()); StatCell(signed(row.goalDifference))
        StatCell(row.points.toString(), bold = true)
    }
}

@Composable
private fun RowScope.StatHead(t: String) {
    Text(t, color = CSub, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun RowScope.StatCell(t: String, bold: Boolean = false) {
    Text(
        t,
        color = CInk,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.width(30.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FixtureRow(text: String, played: Boolean, mine: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (mine) CHighlight else CBg)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (mine) CInk else CSub,
            fontWeight = if (mine) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!played) Text("to play", color = CSub, style = MaterialTheme.typography.labelSmall)
    }
}

private fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()

private fun ordinal(n: Int): String {
    if (n <= 0) return "-"
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}
