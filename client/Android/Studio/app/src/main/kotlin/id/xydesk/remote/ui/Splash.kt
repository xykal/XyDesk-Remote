package id.xydesk.remote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.R
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.theme.XyDisplay
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.min

/**
 * Splash XyDesk Remote (Revisi):
 * Emblem vektor presisi (dual-monitor + kursor + sinyal sinkronisasi),
 * badge kapabilitas dengan ikon vektor, progress bar tipis responsif,
 * dan identitas XyVerse Technology Global di bagian bawah.
 */
@Composable
fun XySplashScreen(
    ready: Boolean,
    dark: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var animDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1_150, easing = FastOutSlowInEasing))
        animDone = true
    }

    LaunchedEffect(animDone, ready) {
        if (animDone && ready) {
            fade.animateTo(0f, tween(durationMillis = 260))
            onDone()
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    val p = progress.value.coerceIn(0f, 1f)
    val cardAlpha = ((p - 0.02f) / 0.35f).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxSize()
            .background(bg)
            .alpha(fade.value.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .alpha(cardAlpha),
        ) {
            Box(
                modifier = Modifier
                    .size(94.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(surface)
                    .border(1.2.dp, line, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(56.dp)) {
                    drawXySplashEmblem(
                        progress = p,
                        ink = ink,
                        accent = accent,
                        muted = muted,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "XyDesk Remote",
                fontFamily = XyDisplay,
                color = ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = xy(
                    "Remote Desktop & Direct PC Control",
                    "Remote Desktop & Direct PC Control",
                ),
                color = muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SplashFeatureBadge(icon = XyIcons.Monitor, label = "Multi-Display", ink = ink, border = line, surface = surface)
                SplashFeatureBadge(icon = XyIcons.Windows, label = "H.264 60FPS", ink = ink, border = line, surface = surface)
                SplashFeatureBadge(icon = XyIcons.Volume, label = "Low-Latency", ink = ink, border = line, surface = surface)
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .width(148.dp)
                    .height(3.dp)
                    .clip(XyPill)
                    .background(line.copy(alpha = 0.45f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(p.coerceIn(0.08f, 1f))
                        .height(3.dp)
                        .clip(XyPill)
                        .background(accent),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .alpha(((p - 0.10f) / 0.45f).coerceIn(0f, 1f)),
        ) {
            Image(
                painter = painterResource(
                    if (dark) R.drawable.xy_logo_h_white else R.drawable.xy_logo_h_black,
                ),
                contentDescription = "XyVerse Technology Global",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.36f)
                    .height(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "XyVerse Technology Global",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = muted.copy(alpha = 0.78f),
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun SplashFeatureBadge(
    icon: ImageVector,
    label: String,
    ink: Color,
    border: Color,
    surface: Color,
) {
    Row(
        modifier = Modifier
            .clip(XyPill)
            .background(surface)
            .border(1.dp, border, XyPill)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = ink,
        )
    }
}

private fun DrawScope.drawXySplashEmblem(
    progress: Float,
    ink: Color,
    accent: Color,
    muted: Color,
) {
    val u = min(size.width, size.height)
    val stroke = Stroke(width = u * 0.052f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thinStroke = Stroke(width = u * 0.038f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Back secondary display frame (multi-monitor depth)
    val backShift = u * 0.08f * ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)
    drawRoundRect(
        color = muted.copy(alpha = 0.45f),
        topLeft = Offset(u * 0.18f + backShift, u * 0.10f),
        size = Size(u * 0.64f, u * 0.42f),
        cornerRadius = CornerRadius(u * 0.06f, u * 0.06f),
        style = thinStroke,
    )

    // Primary monitor frame
    val monLeft = u * 0.08f
    val monTop = u * 0.20f
    val monW = u * 0.72f
    val monH = u * 0.48f
    drawRoundRect(
        color = ink,
        topLeft = Offset(monLeft, monTop),
        size = Size(monW, monH),
        cornerRadius = CornerRadius(u * 0.07f, u * 0.07f),
        style = stroke,
    )

    // Monitor stand
    drawLine(
        color = ink,
        start = Offset(monLeft + monW * 0.5f, monTop + monH),
        end = Offset(monLeft + monW * 0.5f, u * 0.82f),
        strokeWidth = u * 0.052f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ink,
        start = Offset(monLeft + monW * 0.28f, u * 0.82f),
        end = Offset(monLeft + monW * 0.72f, u * 0.82f),
        strokeWidth = u * 0.052f,
        cap = StrokeCap.Round,
    )

    // Precision cursor arrow inside primary monitor
    val cursorShift = (1f - ((progress - 0.1f) / 0.6f).coerceIn(0f, 1f)) * (u * 0.06f)
    val cx = monLeft + monW * 0.42f + cursorShift
    val cy = monTop + monH * 0.24f + cursorShift
    val cursorPath = Path().apply {
        moveTo(cx, cy)
        lineTo(cx, cy + u * 0.22f)
        lineTo(cx + u * 0.065f, cy + u * 0.165f)
        lineTo(cx + u * 0.11f, cy + u * 0.25f)
        lineTo(cx + u * 0.15f, cy + u * 0.23f)
        lineTo(cx + u * 0.105f, cy + u * 0.145f)
        lineTo(cx + u * 0.185f, cy + u * 0.14f)
        close()
    }
    drawPath(path = cursorPath, color = accent)
}
