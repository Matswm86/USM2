package no.mwmai.usm2.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Decodes a PNG bundled under assets/ once and caches it for the composition. */
@Composable
fun rememberAssetImage(path: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) {
        runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it).asImageBitmap() }
        }.getOrNull()
    }
}

/** Full-width banner showing an original screen background, cropped to height. */
@Composable
fun ScreenBanner(asset: String, height: Int = 200, modifier: Modifier = Modifier) {
    val img = rememberAssetImage(asset)
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Color(0xFF06281A)),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun RatingChip(rating: Int, modifier: Modifier = Modifier) {
    val tone = when {
        rating >= 60 -> Color(0xFF2E7D32)
        rating >= 40 -> Color(0xFF9E7D00)
        else -> Color(0xFF5D4037)
    }
    Box(
        modifier
            .size(width = 34.dp, height = 26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tone),
        contentAlignment = Alignment.Center,
    ) {
        Text(rating.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Positional skill bars (attribute names still being recovered from the EXE). */
@Composable
fun SkillBars(attributes: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        attributes.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S${i + 1}",
                    modifier = Modifier.size(width = 34.dp, height = 18.dp),
                    color = Color(0xFFB9C7BD),
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(
                    Modifier
                        .height(10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF173A28)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (v.coerceIn(0, 99)) / 99f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF4CAF7D)),
                    )
                }
                Text(
                    v.toString(),
                    modifier = Modifier.padding(start = 8.dp),
                    color = Color(0xFFE3EFE8),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B3D24))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color(0xFFCDE8D7),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall,
    )
}
