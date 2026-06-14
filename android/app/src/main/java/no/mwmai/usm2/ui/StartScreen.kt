package no.mwmai.usm2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.usm2.GameData

/** Landscape title screen: the real TITLE art with New Career / Continue. */
@Composable
fun StartScreen(
    data: GameData,
    careerClub: String?,
    onNew: () -> Unit,
    onContinue: () -> Unit,
) {
    val title = rememberAssetImage("img/TITLE.png")
    Box(Modifier.fillMaxSize().background(Color(0xFF06140D)), contentAlignment = Alignment.Center) {
        if (title != null) {
            Image(
                bitmap = title,
                contentDescription = "Ultimate Soccer Manager 2",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${data.clubs.size} clubs · ${data.players.size} players · 1996/97",
                color = Color(0xFFB7C7BC),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${data.formations.size} authentic formations",
                color = Color(0xFFB7C7BC),
                style = MaterialTheme.typography.labelMedium,
            )
            if (careerClub != null) {
                StartButton("Continue · $careerClub", onContinue, accent = true)
            }
            StartButton(if (careerClub != null) "New career" else "New career (choose a club)", onNew)
        }
    }
}

@Composable
private fun StartButton(label: String, onClick: () -> Unit, accent: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(360.dp).padding(vertical = 5.dp).height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) Color(0xFFE7C84A) else Color(0xFF0B3D24),
            contentColor = if (accent) Color(0xFF06140D) else Color(0xFFE8F0E8),
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label, fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.titleMedium)
    }
}
