package id.xydesk.remote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.R
import id.xydesk.remote.ui.theme.XyDisplay
import id.xydesk.remote.ui.theme.XyThemeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash screen XyDesk Remote dengan Logo Resmi XyVerse Brand Kit:
 *  - Tengah: Logo Vektor XyDesk (Monitor + Click Ripple + Cursor tajam) + Wordmark "XyDesk Remote"
 *  - Bawah (Footer): "Pengembang · Developer" + Logo Resmi XyVerse Technology Global (`xyverse_logo_h_white` / `xyverse_logo_h_black`)
 */
@Composable
internal fun XySplashScreen(
    ready: Boolean = true,
    dark: Boolean = when (XyThemeState.mode) {
        XyThemeState.DARK -> true
        XyThemeState.LIGHT -> false
        else -> isSystemInDarkTheme()
    },
    onDone: () -> Unit = {},
    onFinished: () -> Unit = onDone,
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }
    val footerAlpha = remember { Animatable(0f) }
    val minTimeDone = remember { androidx.compose.runtime.mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "splashPulse")
    val rippleScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rippleScale",
    )

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
        }
        launch {
            delay(80)
            footerAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            )
        }
        delay(420)
        minTimeDone.value = true
    }

    LaunchedEffect(ready, minTimeDone.value) {
        if (ready && minTimeDone.value) {
            launch {
                footerAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                )
            }
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            )
            onFinished()
        }
    }

    val bgColor = if (dark) Color(0xFF07080D) else Color(0xFFF8FAFC)
    val cardGradient = if (dark) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1A102E), Color(0xFF0E0B1A)),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)),
        )
    }
    val primaryTextColor = if (dark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val subTextColor = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val footerLogoRes = if (dark) R.drawable.xyverse_logo_h_white else R.drawable.xyverse_logo_h_black

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        // TENGAH: Logo Utama XyDesk (100% Vector Crisp) + Wordmark XyDesk Remote
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(alpha.value)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(cardGradient)
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFA855F7).copy(alpha = 0.55f),
                                Color(0xFF818CF8).copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.10f),
                            ),
                        ),
                        shape = RoundedCornerShape(26.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                XyDeskVectorEmblem(
                    modifier = Modifier.size(62.dp),
                    tint = Color.White,
                    rippleScale = rippleScale,
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "XyDesk",
                fontFamily = XyDisplay,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                letterSpacing = (-0.6).sp,
                color = primaryTextColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "REMOTE DESKTOP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                letterSpacing = 2.8.sp,
                color = subTextColor,
            )
        }

        // BAWAH (FOOTER): Logo Resmi XyVerse Tech
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .alpha(footerAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = xy("Pengembang · Developer", "Developer · Pengembang"),
                style = MaterialTheme.typography.labelSmall,
                color = subTextColor.copy(alpha = 0.78f),
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
            )
            Image(
                painter = painterResource(footerLogoRes),
                contentDescription = "XyVerse Tech",
                modifier = Modifier.height(24.dp),
                contentScale = ContentScale.FillHeight,
            )
        }
    }
}

/**
 * Emblem Vektor XyDesk (Monitor + Click Ripple + Cursor) tajam di segala ukuran DPI.
 */
@Composable
internal fun XyDeskVectorEmblem(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    rippleScale: Float = 1f,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h) / 64f

        // 1. Layar Monitor Rounded
        val frameStroke = Stroke(width = 3.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(7f * s, 9f * s),
            size = Size(50f * s, 34f * s),
            cornerRadius = CornerRadius(5.5f * s, 5.5f * s),
            style = frameStroke,
        )

        // 2. Leher & Kaki Stand Monitor
        drawLine(
            color = tint,
            start = Offset(32f * s, 43.5f * s),
            end = Offset(32f * s, 51.5f * s),
            strokeWidth = 4.2f * s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(21f * s, 52f * s),
            end = Offset(43f * s, 52f * s),
            strokeWidth = 4.2f * s,
            cap = StrokeCap.Round,
        )

        // 3. Click Ripple Concentric Rings di ujung kursor
        val tipX = 24.5f * s
        val tipY = 21.5f * s
        drawArc(
            color = tint.copy(alpha = 0.92f),
            startAngle = 145f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(tipX - 3.6f * s * rippleScale, tipY - 3.6f * s * rippleScale),
            size = Size(7.2f * s * rippleScale, 7.2f * s * rippleScale),
            style = Stroke(width = 1.8f * s, cap = StrokeCap.Round),
        )
        drawArc(
            color = tint.copy(alpha = 0.65f),
            startAngle = 145f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(tipX - 6.4f * s * rippleScale, tipY - 6.4f * s * rippleScale),
            size = Size(12.8f * s * rippleScale, 12.8f * s * rippleScale),
            style = Stroke(width = 1.6f * s, cap = StrokeCap.Round),
        )

        // 4. Panah Kursor Presisi
        val cursorPath = Path().apply {
            moveTo(24.8f * s, 21.8f * s)
            lineTo(29.8f * s, 36.2f * s)
            lineTo(33.2f * s, 32.6f * s)
            lineTo(37.4f * s, 36.8f * s)
            lineTo(40.2f * s, 34.0f * s)
            lineTo(36.0f * s, 29.8f * s)
            lineTo(39.8f * s, 26.4f * s)
            close()
        }
        drawPath(path = cursorPath, color = tint)
    }
}
