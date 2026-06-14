package no.mwmai.usm2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * The original USM2 navigation toolbar, rebuilt from the real icons cropped out
 * of the baked-in top strip (`assets/img/tb/ic_NN.png`). The 24 icons share the
 * width equally, exactly as in the 640x480 game. Dumb on purpose: it reports the
 * tapped index; [no.mwmai.usm2.ui.RoomHost] owns the index -> action mapping so
 * the (source-derived) mapping lives in one place.
 */
const val TOOLBAR_ICON_COUNT = 24

@Composable
fun Toolbar(onIcon: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(38.dp).background(Color(0xFF120C04))) {
        for (i in 0 until TOOLBAR_ICON_COUNT) {
            val img = rememberAssetImage("img/tb/ic_%02d.png".format(i))
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { onIcon(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        modifier = Modifier.fillMaxHeight(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("$i", color = Color(0xFF6A7A6F), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
