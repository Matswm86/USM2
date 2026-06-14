package no.mwmai.usm2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.usm2.Club
import no.mwmai.usm2.GameData
import no.mwmai.usm2.Player

private val Green = Color(0xFF0B3D24)
private val Bg = Color(0xFF06281A)
private val Ink = Color(0xFFE3EFE8)
private val Sub = Color(0xFFA8C0B2)

@Composable
fun UsmScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Green).padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Text(
                    "‹",
                    color = Ink,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp),
                )
            }
            Text(title, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
        content(PaddingValues(0.dp))
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFF4CAF7D))
    }
}

@Composable
fun ErrorScreen(message: String) {
    Box(Modifier.fillMaxSize().background(Bg).padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Could not load data:\n$message", color = Ink)
    }
}

@Composable
fun OfficeScreen(data: GameData, onSquads: () -> Unit, onTransfers: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        ScreenBanner("img/MAINSCR.png", height = 230)
        Spacer(Modifier.height(18.dp))
        Text(
            "Ultimate Soccer Manager 2",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            color = Ink,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "${data.clubs.size} clubs · ${data.players.size} players · 1996/97 database",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            color = Sub,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        OfficeButton("Squads & Tables", onSquads)
        OfficeButton("Transfer Market", onTransfers)
    }
}

@Composable
private fun OfficeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Ink),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun GroupsScreen(data: GameData, onGroup: (String) -> Unit, onBack: () -> Unit) {
    UsmScaffold("Leagues", onBack) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(data.groups) { group ->
                val clubs = data.clubsInGroup(group)
                RowCard(
                    title = group,
                    subtitle = "${clubs.size} clubs",
                    onClick = { onGroup(group) },
                )
            }
        }
    }
}

@Composable
fun GroupScreen(data: GameData, group: String, onClub: (String) -> Unit, onBack: () -> Unit) {
    val clubs = remember(group) { data.clubsInGroup(group) }
    UsmScaffold(group, onBack) {
        LazyColumn(Modifier.fillMaxSize()) {
            var lastDiv = Int.MIN_VALUE
            clubs.forEach { club ->
                if (club.division != lastDiv) {
                    lastDiv = club.division
                    item(key = "h-${club.id}") { SectionHeader(club.divisionName) }
                }
                item(key = club.id) {
                    val n = data.squad(club.id).size
                    RowCard(title = club.name, subtitle = "${club.stadium} · $n players") { onClub(club.id) }
                }
            }
        }
    }
}

@Composable
fun ClubScreen(data: GameData, clubId: String, onPlayer: (Int) -> Unit, onBack: () -> Unit) {
    val club: Club? = data.clubsById[clubId]
    val squad = remember(clubId) {
        data.playersIndexed.filter { it.value.club == clubId }.sortedByDescending { it.value.rating }
    }
    UsmScaffold(club?.name ?: "Squad", onBack) {
        LazyColumn(Modifier.fillMaxSize()) {
            club?.let {
                item {
                    Text(
                        "${it.divisionName} · ${it.stadium} · Mgr ${it.manager}",
                        modifier = Modifier.padding(16.dp),
                        color = Sub,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(squad, key = { it.index }) { (idx, p) ->
                PlayerRow(p) { onPlayer(idx) }
                HorizontalDivider(color = Color(0xFF123527))
            }
        }
    }
}

@Composable
fun TransfersScreen(data: GameData, onPlayer: (Int) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim().lowercase()
        data.playersIndexed
            .asSequence()
            .filter { q.isEmpty() || it.value.name.lowercase().contains(q) }
            .sortedByDescending { it.value.rating }
            .take(200)
            .toList()
    }
    UsmScaffold("Transfer Market", onBack) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                label = { Text("Search players", color = Sub) },
            )
            Text(
                "Top ${results.size} by rating" + if (query.isBlank()) " (all leagues)" else "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = Sub,
                style = MaterialTheme.typography.labelMedium,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.index }) { (idx, p) ->
                    PlayerRow(p, club = data.clubsById[p.club]?.name) { onPlayer(idx) }
                    HorizontalDivider(color = Color(0xFF123527))
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(data: GameData, idx: Int, onBack: () -> Unit) {
    val p: Player? = data.players.getOrNull(idx)
    UsmScaffold(p?.name ?: "Player", onBack) {
        if (p == null) {
            Text("Unknown player", color = Ink, modifier = Modifier.padding(16.dp))
            return@UsmScaffold
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingChip(p.rating)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(p.name, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    val clubName = data.clubsById[p.club]?.name ?: "Free agent"
                    Text(
                        "Age ${p.age} · $clubName" + if (p.key) " · ★ key player" else "",
                        color = Sub,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Green)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Attributes",
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    SkillBars(p.attributes)
                    Text(
                        "Column meanings (tackle/pass/shoot/…) still being recovered.",
                        color = Sub,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E3320)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlayerRow(p: Player, club: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatingChip(p.rating)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = Ink, style = MaterialTheme.typography.bodyLarge)
                if (p.key) Text("  ★", color = Color(0xFFE7C84A), style = MaterialTheme.typography.bodyMedium)
            }
            val sub = buildString {
                append("Age ${p.age}")
                if (club != null) append(" · $club")
            }
            Text(sub, color = Sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
