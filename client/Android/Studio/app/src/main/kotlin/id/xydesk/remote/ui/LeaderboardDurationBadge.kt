package id.xydesk.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Vector-only medal; no downloads, looping animation or entitlement attached. */
@Composable
internal fun LeaderboardDurationBadge(rank: Int, compact: Boolean = true) {
    if (rank !in 1..3) return
    val metal = when (rank) { 1 -> Color(0xFFF5CF72); 2 -> Color(0xFFCDDEEF); else -> Color(0xFFE7AD86) }
    val surface = when (rank) { 1 -> Color(0xFF302619); 2 -> Color(0xFF202B38); else -> Color(0xFF33251F) }
    val title = when (rank) { 1 -> "RAJA MARATON"; 2 -> "MASTER DURASI"; else -> "VETERAN MAIN" }
    val shape = RoundedCornerShape(if (compact) 7.dp else 10.dp)
    Row(
        Modifier.widthIn(max = if (compact) 144.dp else 260.dp)
            .semantics { contentDescription = "Top $rank Sipaling Lama Main, $title" }
            .background(surface, shape).border(0.8.dp, metal.copy(alpha = 0.6f), shape)
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp),
    ) {
        Canvas(Modifier.size(if (compact) 17.dp else 26.dp)) {
            val w = size.width; val h = size.height
            val ribbons = Path().apply {
                moveTo(w * .18f, h * .45f); lineTo(w * .16f, h * .97f)
                lineTo(w * .43f, h * .78f); lineTo(w * .5f, h * .5f); close()
                moveTo(w * .82f, h * .45f); lineTo(w * .84f, h * .97f)
                lineTo(w * .57f, h * .78f); lineTo(w * .5f, h * .5f); close()
            }
            drawPath(ribbons, metal.copy(alpha = .6f))
            drawCircle(metal, w * .35f, Offset(w / 2, h * .39f))
            drawCircle(surface, w * .25f, Offset(w / 2, h * .39f), style = Stroke(w * .035f))
            // Distinct silhouettes remain identifiable without relying on metal colour.
            val emblem = Path().apply {
                when (rank) {
                    1 -> { // Crown: reigning duration champion.
                        moveTo(w * .3f, h * .43f); lineTo(w * .27f, h * .26f)
                        lineTo(w * .42f, h * .33f); lineTo(w * .5f, h * .19f)
                        lineTo(w * .58f, h * .33f); lineTo(w * .73f, h * .26f)
                        lineTo(w * .7f, h * .43f); close()
                    }
                    2 -> { // Hourglass: master of time.
                        moveTo(w * .34f, h * .23f); lineTo(w * .66f, h * .23f)
                        lineTo(w * .53f, h * .38f); lineTo(w * .66f, h * .54f)
                        lineTo(w * .34f, h * .54f); lineTo(w * .47f, h * .38f)
                        close()
                    }
                    else -> { // Comet: a persistent explorer, not another crown.
                        moveTo(w * .27f, h * .25f); lineTo(w * .56f, h * .31f)
                        lineTo(w * .69f, h * .43f); lineTo(w * .56f, h * .53f)
                        close()
                    }
                }
            }
            drawPath(emblem, surface)
            if (rank == 1) {
                drawLine(surface, Offset(w * .34f, h * .51f), Offset(w * .66f, h * .51f), w * .045f)
            }
            if (rank == 3) {
                drawCircle(surface, w * .10f, Offset(w * .61f, h * .43f))
                drawLine(surface, Offset(w * .27f, h * .39f), Offset(w * .38f, h * .47f), w * .035f)
            }
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text("TOP $rank · $title", color = metal, fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("SIPALING LAMA MAIN", color = metal.copy(alpha = .85f),
                style = MaterialTheme.typography.labelSmall, fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
