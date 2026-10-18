package id.xydesk.remote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.theme.XyBg
import id.xydesk.remote.ui.theme.XyLine
import id.xydesk.remote.ui.theme.XySurface
import id.xydesk.remote.ui.theme.XySurfaceAlt
import id.xydesk.remote.ui.theme.XyText
import id.xydesk.remote.ui.theme.XyTextDim
import id.xydesk.remote.ui.theme.XyTextFaint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash screen XyDesk Remote — selaras penuh dengan palet monokrom matte
 * rana utama (`XyDeskTheme.kt` / `XyDeskHome.kt`):
 *  - Tengah layar: Emblem vektor tajam XyDesk + tipografi bersih "XyDesk Remote"
 *    serta indikator muat minimalis selaras rana utama.
 *  - Bawah layar: Identitas korporasi "XYVERSE TECHNOLOGY GLOBAL" selaras footer utama.
 */
@Composable
fun XySplashScreen(
    ready: Boolean = true,
    dark: Boolean = true,
    onDone: () -> Unit = {},
    onFinished: () -> Unit = onDone,
) {
    val bg0 = if (dark) XyBg else Color(0xFFF7F7F8)
    val bg1 = if (dark) XySurface else Color(0xFFFFFFFF)
    val bg2 = if (dark) XySurfaceAlt else Color(0xFFF1F1F3)
    val line0 = if (dark) XyLine else Color(0xFFDDDDDF)
    val ink0 = if (dark) XyText else Color(0xFF1A1A1D)
    val ink1 = if (dark) XyTextDim else Color(0xFF5F5F65)
    val ink2 = if (dark) XyTextFaint else Color(0xFF808086)

    var visible by remember { mutableStateOf(true) }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.92f) }
    val textAlpha = remember { Animatable(0f) }
    val footerAlpha = remember { Animatable(0f) }

    val inf = rememberInfiniteTransition(label = "xySplashPulse")
    val ambientPulse by inf.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambientPulse",
    )
    val shimmerPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
        delay(90)
        launch {
            textAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
        delay(70)
        launch {
            footerAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(ready) {
        if (ready) {
            delay(820)
            visible = false
            delay(200)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg0),
        ) {
            // Ambient glow tipis selaras dengan header XyDeskHome.kt
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.44f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ink0.copy(alpha = if (dark) 0.05f else 0.035f),
                            bg2.copy(alpha = if (dark) 0.16f else 0.08f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.56f * ambientPulse,
                    ),
                    center = center,
                    radius = size.minDimension * 0.56f * ambientPulse,
                )
            }

            // Bagian Tengah: Emblem XyDesk Vektor + Judul Utama + Bar Progress Monokrom
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(86.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                        .clip(RoundedCornerShape(24.dp))
                        .background(bg1)
                        .border(1.dp, line0, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.requiredSize(50.dp)) {
                        val w = size.width
                        val h = size.height
                        val strokeW = w * 0.078f

                        // Bingkai layar monitor tipis presisi selaras ikon rana utama
                        drawRoundRect(
                            color = ink0,
                            topLeft = Offset(w * 0.06f, h * 0.08f),
                            size = Size(w * 0.88f, h * 0.64f),
                            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )

                        // Monogram Xy di tengah layar monitor
                        val xyPath = Path().apply {
                            moveTo(w * 0.29f, h * 0.25f)
                            lineTo(w * 0.48f, h * 0.55f)
                            moveTo(w * 0.48f, h * 0.25f)
                            lineTo(w * 0.29f, h * 0.55f)

                            moveTo(w * 0.55f, h * 0.27f)
                            lineTo(w * 0.63f, h * 0.42f)
                            lineTo(w * 0.72f, h * 0.27f)
                            moveTo(w * 0.63f, h * 0.42f)
                            lineTo(w * 0.59f, h * 0.56f)
                        }
                        drawPath(
                            path = xyPath,
                            color = ink0,
                            style = Stroke(width = strokeW * 0.92f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )

                        // Kaki penyangga monitor
                        drawLine(
                            color = ink1,
                            start = Offset(w * 0.50f, h * 0.72f),
                            end = Offset(w * 0.50f, h * 0.88f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = ink0,
                            start = Offset(w * 0.30f, h * 0.88f),
                            end = Offset(w * 0.70f, h * 0.88f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier.alpha(textAlpha.value),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "XyDesk Remote",
                            color = ink0,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(bg2)
                                .border(0.75.dp, line0, RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "RDP · SSH",
                                color = ink1,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = "WINDOWS RDP · TERMINAL SSH-2 · CLOUD STUDIO",
                        color = ink2,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // Indikator loading bar tipis monokrom selaras XyControls
                    Box(
                        modifier = Modifier
                            .width(116.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(bg2),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val barW = size.width * 0.42f
                            val startX = (size.width + barW) * shimmerPhase - barW
                            drawRoundRect(
                                color = ink0.copy(alpha = 0.85f),
                                topLeft = Offset(startX.coerceAtLeast(0f), 0f),
                                size = Size(
                                    (startX + barW).coerceAtMost(size.width) - startX.coerceAtLeast(0f),
                                    size.height,
                                ),
                                cornerRadius = CornerRadius(size.height, size.height),
                            )
                        }
                    }
                }
            }

            // Bagian Bawah: Branding XyVerse selaras tema monokrom rana utama
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .alpha(footerAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Canvas(modifier = Modifier.requiredSize(14.dp)) {
                        val s = size.minDimension
                        val path = Path().apply {
                            moveTo(s * 0.50f, s * 0.06f)
                            lineTo(s * 0.94f, s * 0.50f)
                            lineTo(s * 0.50f, s * 0.94f)
                            lineTo(s * 0.06f, s * 0.50f)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = ink1,
                            style = Stroke(width = s * 0.13f, join = StrokeJoin.Round),
                        )
                        drawCircle(
                            color = ink0,
                            radius = s * 0.13f,
                            center = Offset(s * 0.50f, s * 0.50f),
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "XYVERSE",
                        color = ink0,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.6.sp,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "TECHNOLOGY GLOBAL",
                    color = ink2,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.0.sp,
                )
            }
        }
    }
}
