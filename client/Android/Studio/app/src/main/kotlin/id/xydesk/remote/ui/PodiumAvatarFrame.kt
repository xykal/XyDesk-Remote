package id.xydesk.remote.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Cosmetic only; follows the same expiring identity snapshot as the duration badge. */
@Composable
internal fun Modifier.podiumAvatarFrame(githubLogin: String?): Modifier {
    val identity = normalizedChatAccount(githubLogin)
    if (identity.isEmpty()) return this
    val topTen by LeaderboardPodium.topTen.collectAsState()
    return podiumFrame(topTen[identity])
}

/** Draw inside existing bounds, after the image; leave avatar size and touch target unchanged. */
internal fun Modifier.podiumFrame(rank: Int?): Modifier {
    if (rank == null || rank !in 1..10) return this
    val metal = when (rank) {
        1 -> Color(0xFFEAEAEA)
        2 -> Color(0xFFDCDCDC)
        3 -> Color(0xFFC4C4C4)
        else -> Color(0xFF9E9E9E)
    }
    return semantics { stateDescription = "Bingkai Top $rank Sipaling Lama Main" }
        .drawWithCache {
            val stroke = 1.6.dp.toPx().coerceAtMost(size.minDimension / 11f)
            val inset = 2.5.dp.toPx().coerceAtMost(size.minDimension / 7f)
            val radius = ((size.minDimension - stroke) / 2f - inset).coerceAtLeast(0f)
            val brush = Brush.linearGradient(listOf(metal, Color.White, metal))
            onDrawWithContent {
                drawContent()
                if (radius > 0f) {
                    when (rank) {
                        1 -> {
                            drawCircle(brush, radius, style = Stroke(stroke))
                            drawCircle(
                                metal.copy(alpha = .72f),
                                (radius - stroke * 1.6f).coerceAtLeast(0f),
                                style = Stroke(stroke * .55f),
                            )
                        }
                        2 -> repeat(4) { segment ->
                            drawArc(
                                brush,
                                segment * 90f + 8f,
                                74f,
                                false,
                                Offset(center.x - radius, center.y - radius),
                                Size(radius * 2f, radius * 2f),
                                style = Stroke(stroke),
                            )
                        }
                        3 -> repeat(12) { dot ->
                            val angle = dot * Math.PI / 6.0
                            drawCircle(
                                metal,
                                stroke * .75f,
                                Offset(
                                    center.x + radius * cos(angle).toFloat(),
                                    center.y + radius * sin(angle).toFloat(),
                                ),
                            )
                        }
                        else -> repeat(6) { segment ->
                            drawArc(
                                brush,
                                segment * 60f + 6f,
                                46f,
                                false,
                                Offset(center.x - radius, center.y - radius),
                                Size(radius * 2f, radius * 2f),
                                style = Stroke(stroke * 0.9f),
                            )
                        }
                    }
                }
            }
        }
}

/**
 * Overlay animasi eksklusif untuk Foto Profil Animasi Custom (Hadiah Top 1–10 Leaderboard & Founder).
 */
@Composable
internal fun AnimatedAvatarEffectOverlay(
    style: XyAnimatedAvatarStyle,
    modifier: Modifier = Modifier,
) {
    if (style == XyAnimatedAvatarStyle.NONE) return
    val infinite = rememberInfiniteTransition(label = "avatarEffectOverlay")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "avatarAngle",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "avatarPulse",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        val stroke = (size.minDimension * 0.048f).coerceIn(1.5f, 3.8f)
        val ringR = (r - stroke * 0.9f).coerceAtLeast(2f)
        when (style) {
            XyAnimatedAvatarStyle.ORBITAL_CROWN, XyAnimatedAvatarStyle.CUSTOM_GIF -> {
                rotate(angle) {
                    repeat(3) { i ->
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, Color.White, Color(0xFFB8B8B8), Color.Transparent),
                            ),
                            startAngle = i * 120f,
                            sweepAngle = 78f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringR, center.y - ringR),
                            size = Size(ringR * 2f, ringR * 2f),
                            style = Stroke(width = stroke),
                        )
                    }
                }
                val crownW = size.minDimension * 0.28f
                val crownH = size.minDimension * 0.16f
                val cx = center.x
                val cy = stroke * 1.8f
                val crown = Path().apply {
                    moveTo(cx - crownW / 2f, cy + crownH)
                    lineTo(cx - crownW * 0.42f, cy)
                    lineTo(cx - crownW * 0.16f, cy + crownH * 0.55f)
                    lineTo(cx, cy - crownH * 0.2f)
                    lineTo(cx + crownW * 0.16f, cy + crownH * 0.55f)
                    lineTo(cx + crownW * 0.42f, cy)
                    lineTo(cx + crownW / 2f, cy + crownH)
                    close()
                }
                drawPath(crown, color = Color.White.copy(alpha = 0.92f), style = Fill)
            }
            XyAnimatedAvatarStyle.PULSE_RINGS -> {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = ringR,
                    style = Stroke(width = stroke * 0.85f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = (1f - pulse) * 0.75f),
                    radius = ringR * (0.82f + pulse * 0.17f),
                    style = Stroke(width = stroke * 1.15f),
                )
            }
            XyAnimatedAvatarStyle.STARLIGHT_AURA -> {
                rotate(angle * 0.7f) {
                    repeat(8) { idx ->
                        val rad = Math.toRadians((idx * 45.0))
                        val px = center.x + ringR * cos(rad).toFloat()
                        val py = center.y + ringR * sin(rad).toFloat()
                        drawCircle(
                            color = Color.White.copy(alpha = if (idx % 2 == 0) pulse else 1f - pulse * 0.5f),
                            radius = stroke * (if (idx % 2 == 0) 1.25f else 0.8f),
                            center = Offset(px, py),
                        )
                    }
                }
                drawCircle(
                    color = Color(0xFFD8D8D8).copy(alpha = 0.65f),
                    radius = ringR,
                    style = Stroke(width = stroke * 0.7f),
                )
            }
            XyAnimatedAvatarStyle.VORTEX_HALO -> {
                rotate(angle) {
                    repeat(4) { seg ->
                        drawArc(
                            color = Color.White.copy(alpha = 0.9f),
                            startAngle = seg * 90f,
                            sweepAngle = 52f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringR, center.y - ringR),
                            size = Size(ringR * 2f, ringR * 2f),
                            style = Stroke(width = stroke),
                        )
                    }
                }
                val innerR = (ringR - stroke * 1.7f).coerceAtLeast(2f)
                rotate(-angle * 1.3f) {
                    repeat(4) { seg ->
                        drawArc(
                            color = Color(0xFFB0B0B0).copy(alpha = 0.85f),
                            startAngle = seg * 90f + 22f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(center.x - innerR, center.y - innerR),
                            size = Size(innerR * 2f, innerR * 2f),
                            style = Stroke(width = stroke * 0.75f),
                        )
                    }
                }
            }
            XyAnimatedAvatarStyle.NONE -> Unit
        }
    }
}
