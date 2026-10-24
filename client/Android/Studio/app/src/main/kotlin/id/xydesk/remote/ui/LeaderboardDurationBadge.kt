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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge Top Leaderboard (Top 1..10).
 * - Mode `compact = true` (sebaris dengan nama di Komentar TikTok, Feed, & Chat Global):
 *   Pill metalik berkilau 1-baris ("TOP #1", "TOP #2", "TOP #3", "TOP #N") dengan ikon Mahkota/Medali.
 * - Mode `compact = false` (Kartu Profil & Detail Podium):
 *   Kartu medali lengkap dengan julukan ("RAJA MARATON", "MASTER DURASI", "VETERAN MAIN", "ELITE PLAYER").
 */
@Composable
internal fun LeaderboardDurationBadge(rank: Int, compact: Boolean = true) {
    if (rank !in 1..10) return

    val bgBrush = when (rank) {
        1 -> Brush.horizontalGradient(listOf(Color(0xFF451A03), Color(0xFFB45309), Color(0xFFD97706)))
        2 -> Brush.horizontalGradient(listOf(Color(0xFF0F172A), Color(0xFF334155), Color(0xFF475569)))
        3 -> Brush.horizontalGradient(listOf(Color(0xFF431407), Color(0xFF9A3412), Color(0xFFC2410C)))
        else -> Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA)))
    }
    val borderBrush = when (rank) {
        1 -> Brush.horizontalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFFEF08A)))
        2 -> Brush.horizontalGradient(listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFFE2E8F0)))
        3 -> Brush.horizontalGradient(listOf(Color(0xFFFED7AA), Color(0xFFFB923C), Color(0xFFFDBA74)))
        else -> Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFFA78BFA), Color(0xFFC084FC)))
    }
    val metal = when (rank) {
        1 -> Color(0xFFFEF08A)
        2 -> Color(0xFFF1F5F9)
        3 -> Color(0xFFFED7AA)
        else -> Color(0xFFE0E7FF)
    }
    val iconAccent = when (rank) {
        1 -> Color(0xFFFBBF24)
        2 -> Color(0xFFCBD5E1)
        3 -> Color(0xFFFB923C)
        else -> Color(0xFF818CF8)
    }
    val title = when (rank) {
        1 -> "RAJA MARATON"
        2 -> "MASTER DURASI"
        3 -> "VETERAN MAIN"
        else -> "ELITE MARATON"
    }

    if (compact) {
        val pillShape = RoundedCornerShape(50)
        Row(
            modifier = Modifier
                .semantics { contentDescription = "Top $rank Sipaling Lama Main, $title" }
                .clip(pillShape)
                .background(bgBrush, pillShape)
                .border(0.9.dp, borderBrush, pillShape)
                .padding(horizontal = 6.5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        ) {
            Canvas(Modifier.size(10.5.dp)) {
                val w = size.width
                val h = size.height
                if (rank == 1) {
                    val crown = Path().apply {
                        moveTo(w * 0.12f, h * 0.80f)
                        lineTo(w * 0.08f, h * 0.28f)
                        lineTo(w * 0.34f, h * 0.52f)
                        lineTo(w * 0.50f, h * 0.14f)
                        lineTo(w * 0.66f, h * 0.52f)
                        lineTo(w * 0.92f, h * 0.28f)
                        lineTo(w * 0.88f, h * 0.80f)
                        close()
                    }
                    drawPath(crown, color = iconAccent, style = Fill)
                    drawCircle(color = Color.White, radius = w * 0.09f, center = Offset(w * 0.50f, h * 0.14f))
                } else {
                    drawCircle(color = iconAccent, radius = w * 0.42f, center = Offset(w * 0.50f, h * 0.50f))
                    val star = Path().apply {
                        moveTo(w * 0.50f, h * 0.20f)
                        lineTo(w * 0.62f, h * 0.42f)
                        lineTo(w * 0.84f, h * 0.46f)
                        lineTo(w * 0.66f, h * 0.62f)
                        lineTo(w * 0.72f, h * 0.84f)
                        lineTo(w * 0.50f, h * 0.72f)
                        lineTo(w * 0.28f, h * 0.84f)
                        lineTo(w * 0.34f, h * 0.62f)
                        lineTo(w * 0.16f, h * 0.46f)
                        lineTo(w * 0.38f, h * 0.42f)
                        close()
                    }
                    drawPath(star, color = Color(0xFF09090B), style = Fill)
                }
            }
            Text(
                text = "TOP #$rank",
                color = metal,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    val shape = RoundedCornerShape(11.dp)
    Row(
        Modifier
            .widthIn(max = 260.dp)
            .semantics { contentDescription = "Top $rank Sipaling Lama Main, $title" }
            .clip(shape)
            .background(bgBrush, shape)
            .border(1.dp, borderBrush, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val ribbons = Path().apply {
                moveTo(w * .18f, h * .45f); lineTo(w * .16f, h * .96f)
                lineTo(w * .43f, h * .78f); lineTo(w * .5f, h * .5f); close()
                moveTo(w * .82f, h * .45f); lineTo(w * .84f, h * .96f)
                lineTo(w * .57f, h * .78f); lineTo(w * .5f, h * .5f); close()
            }
            drawPath(ribbons, iconAccent.copy(alpha = .75f))
            drawCircle(iconAccent, w * .36f, Offset(w / 2, h * .39f))
            drawCircle(Color(0xFF09090B), w * .26f, Offset(w / 2, h * .39f), style = Stroke(w * .045f))
            val emblem = Path().apply {
                moveTo(w * .31f, h * .46f)
                lineTo(w * .27f, h * .26f)
                lineTo(w * .42f, h * .35f)
                lineTo(w * .5f, h * .20f)
                lineTo(w * .58f, h * .35f)
                lineTo(w * .73f, h * .26f)
                lineTo(w * .69f, h * .46f)
                close()
            }
            drawPath(emblem, Color(0xFF09090B), style = Fill)
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                "TOP #$rank · $title",
                color = metal,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "SIPALING LAMA MAIN · LEADERBOARD",
                color = metal.copy(alpha = .85f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
