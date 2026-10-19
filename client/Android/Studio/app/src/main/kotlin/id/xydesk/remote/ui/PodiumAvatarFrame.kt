package id.xydesk.remote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Cosmetic only; follows the same expiring identity snapshot as the duration badge. */
@Composable
internal fun Modifier.podiumAvatarFrame(githubLogin: String?): Modifier {
    val identity = normalizedChatAccount(githubLogin)
    if (identity.isEmpty()) return this
    val ranks by LeaderboardPodium.ranks.collectAsState()
    return podiumFrame(ranks[identity])
}

/** Draw inside existing bounds, after the image; leave avatar size and touch target unchanged. */
internal fun Modifier.podiumFrame(rank: Int?): Modifier {
    if (rank == null || rank !in 1..3) return this
    val metal = when (rank) {
        1 -> Color(0xFFF5CF72)
        2 -> Color(0xFFCDDEEF)
        else -> Color(0xFFE7AD86)
    }
    return semantics { stateDescription = "Bingkai Top $rank Sipaling Lama Main" }
        .drawWithCache {
            val stroke = 1.4.dp.toPx().coerceAtMost(size.minDimension / 12f)
            // Keep the pre-existing founder border visible outside the podium ring.
            val inset = 3.dp.toPx().coerceAtMost(size.minDimension / 7f)
            val radius = ((size.minDimension - stroke) / 2f - inset).coerceAtLeast(0f)
            val brush = Brush.linearGradient(listOf(metal, Color.White, metal))
            onDrawWithContent {
                drawContent()
                if (radius > 0f) {
                    when (rank) {
                        1 -> {
                            drawCircle(brush, radius, style = Stroke(stroke))
                            drawCircle(metal.copy(alpha = .65f), (radius - stroke * 1.7f).coerceAtLeast(0f),
                                style = Stroke(stroke * .45f))
                        }
                        2 -> repeat(4) { segment ->
                            drawArc(brush, segment * 90f + 8f, 74f, false,
                                Offset(center.x - radius, center.y - radius),
                                Size(radius * 2f, radius * 2f), style = Stroke(stroke))
                        }
                        else -> repeat(12) { dot ->
                            val angle = dot * Math.PI / 6.0
                            drawCircle(metal, stroke * .7f, Offset(
                                center.x + radius * kotlin.math.cos(angle).toFloat(),
                                center.y + radius * kotlin.math.sin(angle).toFloat(),
                            ))
                        }
                    }
                }
            }
        }
}
