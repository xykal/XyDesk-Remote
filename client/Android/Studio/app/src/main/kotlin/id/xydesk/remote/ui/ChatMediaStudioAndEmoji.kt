package id.xydesk.remote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Komponen Emoji Reaksi Asli (Native Color Emoji) dengan Animasi Interaktif
 * (Pop-In Elastic Bounce + Gerakan Ekspresif Hidup sesuai jenis emoji + Ring Burst).
 */
@Composable
fun IosWhatsAppEmoji3D(
    codeOrEmoji: String = "",
    tag: String = "",
    size: Dp = 18.dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier
) {
    val activeInput = codeOrEmoji.ifBlank { tag }
    val norm = remember(activeInput) { normalizeReactionToIosType(activeInput) }
    val glyph = remember(norm, activeInput) { resolveNativeEmojiGlyph(norm, activeInput) }

    val popScale = remember(activeInput) { Animatable(if (animated) 0.38f else 1f) }
    val burstProgress = remember(activeInput) { Animatable(0f) }

    LaunchedEffect(activeInput) {
        if (animated) {
            launch {
                popScale.snapTo(0.38f)
                popScale.animateTo(
                    targetValue = 1.28f,
                    animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
                )
                popScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                burstProgress.snapTo(0f)
                burstProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "emojiLiveAnim")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "emojiPhase"
    )

    val angleRad = phase * 2f * PI.toFloat()
    val liveScale = if (!animated) 1f else when (norm) {
        "heart", "love_eyes" -> 1f + 0.14f * kotlin.math.abs(sin(angleRad * 1.5f))
        "fire", "bolt", "hundred" -> 1f + 0.10f * sin(angleRad * 2f)
        "party", "crown" -> 1f + 0.09f * cos(angleRad)
        else -> 1f + 0.06f * sin(angleRad)
    }
    val liveRotation = if (!animated) 0f else when (norm) {
        "laugh", "rofl", "party", "clap" -> sin(angleRad * 2f) * 10f
        "like", "dislike", "pray" -> sin(angleRad) * 7f
        "think", "wow", "mindblown" -> cos(angleRad) * 6f
        else -> sin(angleRad) * 4f
    }

    Box(
        modifier = modifier.requiredSize(size),
        contentAlignment = Alignment.Center
    ) {
        if (animated && burstProgress.value in 0.02f..0.95f) {
            val bp = burstProgress.value
            val burstAlpha = (1f - bp).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val maxR = this.size.minDimension * 0.56f
                for (i in 0 until 6) {
                    val a = (i * 60f) * (PI.toFloat() / 180f)
                    val dist = maxR * (0.45f + 0.55f * bp)
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = 0.65f * burstAlpha),
                        radius = (this.size.minDimension * 0.07f) * (1f - bp * 0.5f),
                        center = Offset(cx + cos(a) * dist, cy + sin(a) * dist)
                    )
                }
            }
        }
        Text(
            text = glyph,
            fontSize = (size.value * 0.78f).sp,
            modifier = Modifier
                .scale(popScale.value * liveScale)
                .rotate(liveRotation)
        )
    }
}

private fun resolveNativeEmojiGlyph(norm: String, raw: String): String {
    return when (norm) {
        "heart" -> "\u2764\uFE0F"
        "fire" -> "\uD83D\uDD25"
        "like" -> "\uD83D\uDC4D"
        "dislike" -> "\uD83D\uDC4E"
        "laugh" -> "\uD83D\uDE02"
        "rofl" -> "\uD83E\uDD23"
        "sob" -> "\uD83D\uDE2D"
        "love_eyes" -> "\uD83D\uDE0D"
        "cool" -> "\uD83D\uDE0E"
        "think" -> "\uD83E\uDD14"
        "party" -> "\uD83C\uDF89"
        "wow" -> "\uD83D\uDE2E"
        "angry" -> "\uD83D\uDE21"
        "cold" -> "\uD83E\uDD76"
        "mindblown" -> "\uD83E\uDD2F"
        "skull" -> "\uD83D\uDC80"
        "hundred" -> "\uD83D\uDCAF"
        "pray" -> "\uD83D\uDE4F"
        "clap" -> "\uD83D\uDC4F"
        "bolt" -> "\u26A1"
        "gem" -> "\uD83D\uDC8E"
        "crown", "trophy" -> "\uD83D\uDC51"
        "check" -> "\u2705"
        else -> if (raw.any { it.code > 0x2000 }) raw.trim() else "\uD83D\uDD25"
    }
}

fun normalizeReactionToIosType(raw: String): String {
    return when (raw.trim().lowercase()) {
        "heart", "❤️", "❤", "💜", "💖", "💘", "💕" -> "heart"
        "fire", "🔥" -> "fire"
        "like", "👍", "👍🏻", "👍🏼" -> "like"
        "dislike", "👎" -> "dislike"
        "laugh", "😂" -> "laugh"
        "rofl", "🤣" -> "rofl"
        "sob", "😭", "😢" -> "sob"
        "love_eyes", "😍", "🥰" -> "love_eyes"
        "cool", "😎" -> "cool"
        "think", "🤔" -> "think"
        "party", "🎉", "🥳" -> "party"
        "wow", "😮", "😱" -> "wow"
        "angry", "😡" -> "angry"
        "cold", "🥶" -> "cold"
        "mindblown", "🤯" -> "mindblown"
        "skull", "💀", "☠️" -> "skull"
        "hundred", "💯" -> "hundred"
        "pray", "🙏" -> "pray"
        "clap", "👏" -> "clap"
        "bolt", "⚡", "rocket", "🚀" -> "bolt"
        "gem", "💎", "star", "⭐", "✨" -> "gem"
        "crown", "👑", "trophy", "🏆" -> "crown"
        "check", "✅" -> "check"
        else -> "laugh"
    }
}

private fun DrawScope.drawIosSphereBase(
    cx: Float,
    cy: Float,
    r: Float,
    colors: List<Color>
) {
    // Bayangan bawah halus khas iOS 3D
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = r * 0.96f,
        center = Offset(cx, cy + r * 0.08f)
    )
    // Bola 3D radial berlapis
    drawCircle(
        brush = Brush.radialGradient(
            colors = colors,
            center = Offset(cx - r * 0.28f, cy - r * 0.32f),
            radius = r * 1.38f
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    // Rim light bawah
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = r * 0.96f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.05f)
    )
    // Kilau spekular 3D khas Apple Color Emoji di bagian atas
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.72f),
                Color.White.copy(alpha = 0.04f)
            ),
            startY = cy - r * 0.88f,
            endY = cy - r * 0.18f
        ),
        topLeft = Offset(cx - r * 0.56f, cy - r * 0.86f),
        size = Size(r * 1.12f, r * 0.54f)
    )
}

private fun DrawScope.drawIos3DFace(cx: Float, cy: Float, r: Float, mode: String) {
    val baseColors = when (mode) {
        "angry" -> listOf(Color(0xFFFF9A76), Color(0xFFFF3B30), Color(0xFFB80D18))
        "cold" -> listOf(Color(0xFFB8F2FF), Color(0xFF32ADE6), Color(0xFF0062CC))
        else -> listOf(Color(0xFFFFF59D), Color(0xFFFFCC00), Color(0xFFF57C00))
    }
    drawIosSphereBase(cx, cy, r, baseColors)

    val featureDark = Color(0xFF4E2600)
    when (mode) {
        "laugh", "rofl" -> {
            // Mata melengkung bahagia ><
            val eyeStroke = Stroke(width = r * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val leftEye = Path().apply {
                moveTo(cx - r * 0.52f, cy - r * 0.24f)
                lineTo(cx - r * 0.22f, cy - r * 0.10f)
                lineTo(cx - r * 0.52f, cy + r * 0.02f)
            }
            val rightEye = Path().apply {
                moveTo(cx + r * 0.52f, cy - r * 0.24f)
                lineTo(cx + r * 0.22f, cy - r * 0.10f)
                lineTo(cx + r * 0.52f, cy + r * 0.02f)
            }
            drawPath(leftEye, color = featureDark, style = eyeStroke)
            drawPath(rightEye, color = featureDark, style = eyeStroke)

            // Mulut tawa lebar dengan gigi putih & lidah merah muda khas iOS 😂
            val mouth = Path().apply {
                moveTo(cx - r * 0.56f, cy + r * 0.14f)
                quadraticBezierTo(cx, cy + r * 0.88f, cx + r * 0.56f, cy + r * 0.14f)
                close()
            }
            drawPath(mouth, color = Color(0xFF5A1807))
            // Baris gigi putih di atas mulut
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - r * 0.44f, cy + r * 0.14f),
                size = Size(r * 0.88f, r * 0.16f),
                cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
            )
            // Air mata bahagia biru glossy di kiri & kanan (khusus 😂 / 🤣)
            val tearBrush = Brush.radialGradient(
                colors = listOf(Color(0xFFB3E5FC), Color(0xFF00B0FF), Color(0xFF0077C2))
            )
            drawOval(
                brush = tearBrush,
                topLeft = Offset(cx - r * 0.94f, cy - r * 0.02f),
                size = Size(r * 0.34f, r * 0.48f)
            )
            drawOval(
                brush = tearBrush,
                topLeft = Offset(cx + r * 0.60f, cy - r * 0.02f),
                size = Size(r * 0.34f, r * 0.48f)
            )
        }
        "sob" -> {
            // Alis sedih & aliran air mata deras biru glossy khas 😭 iOS
            drawOval(
                color = featureDark,
                topLeft = Offset(cx - r * 0.46f, cy - r * 0.26f),
                size = Size(r * 0.24f, r * 0.20f)
            )
            drawOval(
                color = featureDark,
                topLeft = Offset(cx + r * 0.22f, cy - r * 0.26f),
                size = Size(r * 0.24f, r * 0.20f)
            )
            val streamBrush = Brush.verticalGradient(
                colors = listOf(Color(0xFF80D8FF), Color(0xFF0091EA))
            )
            drawRoundRect(
                brush = streamBrush,
                topLeft = Offset(cx - r * 0.44f, cy - r * 0.08f),
                size = Size(r * 0.22f, r * 0.86f),
                cornerRadius = CornerRadius(r * 0.11f, r * 0.11f)
            )
            drawRoundRect(
                brush = streamBrush,
                topLeft = Offset(cx + r * 0.22f, cy - r * 0.08f),
                size = Size(r * 0.22f, r * 0.86f),
                cornerRadius = CornerRadius(r * 0.11f, r * 0.11f)
            )
            drawOval(
                color = Color(0xFF5A1807),
                topLeft = Offset(cx - r * 0.26f, cy + r * 0.18f),
                size = Size(r * 0.52f, r * 0.40f)
            )
        }
        "love_eyes" -> {
            // Dua mata hati 3D merah menyala khas 😍 iOS
            drawIos3DHeart(cx - r * 0.34f, cy - r * 0.12f, r * 0.34f)
            drawIos3DHeart(cx + r * 0.34f, cy - r * 0.12f, r * 0.34f)
            val smile = Path().apply {
                moveTo(cx - r * 0.46f, cy + r * 0.22f)
                quadraticBezierTo(cx, cy + r * 0.72f, cx + r * 0.46f, cy + r * 0.22f)
                close()
            }
            drawPath(smile, color = Color(0xFF5A1807))
        }
        "cool" -> {
            // Kacamata hitam glossy khas 😎 iOS
            val glasses = Brush.verticalGradient(
                colors = listOf(Color(0xFF3A3A3C), Color(0xFF111113))
            )
            drawRoundRect(
                brush = glasses,
                topLeft = Offset(cx - r * 0.76f, cy - r * 0.28f),
                size = Size(r * 0.68f, r * 0.42f),
                cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
            )
            drawRoundRect(
                brush = glasses,
                topLeft = Offset(cx + r * 0.08f, cy - r * 0.28f),
                size = Size(r * 0.68f, r * 0.42f),
                cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
            )
            drawLine(
                color = Color(0xFF1C1C1E),
                start = Offset(cx - r * 0.88f, cy - r * 0.18f),
                end = Offset(cx + r * 0.88f, cy - r * 0.18f),
                strokeWidth = r * 0.10f,
                cap = StrokeCap.Round
            )
            val smirk = Path().apply {
                moveTo(cx - r * 0.38f, cy + r * 0.34f)
                quadraticBezierTo(cx + r * 0.06f, cy + r * 0.56f, cx + r * 0.44f, cy + r * 0.24f)
            }
            drawPath(smirk, color = featureDark, style = Stroke(width = r * 0.12f, cap = StrokeCap.Round))
        }
        else -> {
            // Mata bulat 3D + senyum hangat khas iOS
            drawOval(
                color = featureDark,
                topLeft = Offset(cx - r * 0.44f, cy - r * 0.26f),
                size = Size(r * 0.22f, r * 0.30f)
            )
            drawOval(
                color = featureDark,
                topLeft = Offset(cx + r * 0.22f, cy - r * 0.26f),
                size = Size(r * 0.22f, r * 0.30f)
            )
            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = r * 0.05f, center = Offset(cx - r * 0.36f, cy - r * 0.18f))
            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = r * 0.05f, center = Offset(cx + r * 0.30f, cy - r * 0.18f))
            if (mode == "wow" || mode == "mindblown") {
                drawOval(
                    color = Color(0xFF5A1807),
                    topLeft = Offset(cx - r * 0.22f, cy + r * 0.16f),
                    size = Size(r * 0.44f, r * 0.48f)
                )
            } else {
                val smile = Path().apply {
                    moveTo(cx - r * 0.42f, cy + r * 0.22f)
                    quadraticBezierTo(cx, cy + r * 0.62f, cx + r * 0.42f, cy + r * 0.22f)
                }
                drawPath(smile, color = featureDark, style = Stroke(width = r * 0.13f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawIos3DHeart(cx: Float, cy: Float, r: Float) {
    val path = Path().apply {
        moveTo(cx, cy + r * 0.84f)
        cubicTo(cx - r * 1.15f, cy + r * 0.18f, cx - r * 0.95f, cy - r * 0.78f, cx, cy - r * 0.26f)
        cubicTo(cx + r * 0.95f, cy - r * 0.78f, cx + r * 1.15f, cy + r * 0.18f, cx, cy + r * 0.84f)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF8FA3),
                Color(0xFFFF2D55),
                Color(0xFFC9184A),
                Color(0xFF800F2F)
            ),
            center = Offset(cx - r * 0.28f, cy - r * 0.32f),
            radius = r * 1.45f
        )
    )
    // Kilau spekular 3D di kubah kiri & kanan hati khas ❤️ iOS
    drawOval(
        color = Color.White.copy(alpha = 0.62f),
        topLeft = Offset(cx - r * 0.58f, cy - r * 0.46f),
        size = Size(r * 0.34f, r * 0.20f)
    )
    drawOval(
        color = Color.White.copy(alpha = 0.38f),
        topLeft = Offset(cx + r * 0.18f, cy - r * 0.44f),
        size = Size(r * 0.24f, r * 0.14f)
    )
}

private fun DrawScope.drawIos3DFire(cx: Float, cy: Float, r: Float) {
    // Lidah api luar (Merah-Oranye 3D)
    val outer = Path().apply {
        moveTo(cx, cy - r * 0.96f)
        cubicTo(cx + r * 0.65f, cy - r * 0.38f, cx + r * 0.92f, cy + r * 0.25f, cx + r * 0.48f, cy + r * 0.82f)
        quadraticBezierTo(cx, cy + r * 1.04f, cx - r * 0.48f, cy + r * 0.82f)
        cubicTo(cx - r * 0.92f, cy + r * 0.25f, cx - r * 0.56f, cy - r * 0.28f, cx - r * 0.20f, cy - r * 0.56f)
        quadraticBezierTo(cx - r * 0.12f, cy - r * 0.22f, cx, cy - r * 0.96f)
        close()
    }
    drawPath(
        path = outer,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFF9500), Color(0xFFFF3B30), Color(0xFFC41E3A))
        )
    )
    // Lidah api tengah (Kuning-Emas 3D)
    val mid = Path().apply {
        moveTo(cx + r * 0.04f, cy - r * 0.38f)
        cubicTo(cx + r * 0.46f, cy + r * 0.04f, cx + r * 0.52f, cy + r * 0.52f, cx, cy + r * 0.84f)
        cubicTo(cx - r * 0.52f, cy + r * 0.52f, cx - r * 0.40f, cy + r * 0.04f, cx + r * 0.04f, cy - r * 0.38f)
        close()
    }
    drawPath(
        path = mid,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF59D), Color(0xFFFFCC00), Color(0xFFFF9500))
        )
    )
    // Inti api putih-kuning terang
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFFFF176), Color.Transparent)
        ),
        radius = r * 0.34f,
        center = Offset(cx, cy + r * 0.52f)
    )
}

private fun DrawScope.drawIos3DThumb(cx: Float, cy: Float, r: Float, up: Boolean) {
    val dir = if (up) 1f else -1f
    val goldBrush = Brush.radialGradient(
        colors = listOf(Color(0xFFFFF59D), Color(0xFFFFCC00), Color(0xFFE68A00)),
        center = Offset(cx - r * 0.2f, cy - r * 0.2f),
        radius = r * 1.3f
    )
    // Kepalan tangan 3D
    drawRoundRect(
        brush = goldBrush,
        topLeft = Offset(cx - r * 0.32f, cy - r * 0.18f),
        size = Size(r * 0.88f, r * 0.72f),
        cornerRadius = CornerRadius(r * 0.24f, r * 0.24f)
    )
    // Ibu jari 3D
    drawRoundRect(
        brush = goldBrush,
        topLeft = Offset(cx - r * 0.14f, if (up) cy - r * 0.82f else cy + r * 0.18f),
        size = Size(r * 0.38f, r * 0.72f),
        cornerRadius = CornerRadius(r * 0.19f, r * 0.19f)
    )
    // Pergelangan tangan biru glossy
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFF5AC8FA), Color(0xFF007AFF))),
        topLeft = Offset(cx - r * 0.74f, cy - r * 0.14f * dir - r * 0.30f),
        size = Size(r * 0.36f, r * 0.68f),
        cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
    )
}

private fun DrawScope.drawIos3DSkull(cx: Float, cy: Float, r: Float) {
    drawIosSphereBase(
        cx,
        cy - r * 0.08f,
        r * 0.84f,
        listOf(Color(0xFFFFFFFF), Color(0xFFE5E5EA), Color(0xFFAEAEB2))
    )
    drawRoundRect(
        color = Color(0xFFD1D1D6),
        topLeft = Offset(cx - r * 0.42f, cy + r * 0.32f),
        size = Size(r * 0.84f, r * 0.46f),
        cornerRadius = CornerRadius(r * 0.16f, r * 0.16f)
    )
    drawCircle(color = Color(0xFF1C1C1E), radius = r * 0.20f, center = Offset(cx - r * 0.30f, cy - r * 0.06f))
    drawCircle(color = Color(0xFF1C1C1E), radius = r * 0.20f, center = Offset(cx + r * 0.30f, cy - r * 0.06f))
}

private fun DrawScope.drawIos3DHundred(cx: Float, cy: Float, r: Float) {
    val redBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFF5E62), Color(0xFFFF2D55), Color(0xFFC8102E))
    )
    val stroke = Stroke(width = r * 0.20f, cap = StrokeCap.Round)
    drawLine(brush = redBrush, start = Offset(cx - r * 0.68f, cy - r * 0.42f), end = Offset(cx - r * 0.76f, cy + r * 0.26f), strokeWidth = r * 0.20f, cap = StrokeCap.Round)
    drawOval(brush = redBrush, topLeft = Offset(cx - r * 0.42f, cy - r * 0.42f), size = Size(r * 0.42f, r * 0.68f), style = stroke)
    drawOval(brush = redBrush, topLeft = Offset(cx + r * 0.16f, cy - r * 0.42f), size = Size(r * 0.42f, r * 0.68f), style = stroke)
    drawLine(brush = redBrush, start = Offset(cx - r * 0.78f, cy + r * 0.54f), end = Offset(cx + r * 0.72f, cy + r * 0.44f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
    drawLine(brush = redBrush, start = Offset(cx - r * 0.66f, cy + r * 0.76f), end = Offset(cx + r * 0.78f, cy + r * 0.66f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
}

private fun DrawScope.drawIos3DPray(cx: Float, cy: Float, r: Float) {
    val handBrush = Brush.verticalGradient(colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFE65100)))
    val leftHand = Path().apply {
        moveTo(cx - r * 0.04f, cy - r * 0.82f)
        lineTo(cx - r * 0.44f, cy + r * 0.36f)
        lineTo(cx - r * 0.04f, cy + r * 0.48f)
        close()
    }
    val rightHand = Path().apply {
        moveTo(cx + r * 0.04f, cy - r * 0.82f)
        lineTo(cx + r * 0.44f, cy + r * 0.36f)
        lineTo(cx + r * 0.04f, cy + r * 0.48f)
        close()
    }
    drawPath(leftHand, brush = handBrush)
    drawPath(rightHand, brush = handBrush)
    drawRoundRect(
        color = Color(0xFF007AFF),
        topLeft = Offset(cx - r * 0.56f, cy + r * 0.42f),
        size = Size(r * 1.12f, r * 0.40f),
        cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
    )
}

private fun DrawScope.drawIos3DClap(cx: Float, cy: Float, r: Float) {
    drawIosSphereBase(cx, cy + r * 0.08f, r * 0.76f, listOf(Color(0xFFFFF59D), Color(0xFFFFCA28), Color(0xFFF57F17)))
    // Garis efek tepuk tangan di atas
    val rayColor = Color(0xFFFFD60A)
    drawLine(rayColor, Offset(cx, cy - r * 0.94f), Offset(cx, cy - r * 0.68f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
    drawLine(rayColor, Offset(cx - r * 0.56f, cy - r * 0.76f), Offset(cx - r * 0.36f, cy - r * 0.54f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
    drawLine(rayColor, Offset(cx + r * 0.56f, cy - r * 0.76f), Offset(cx + r * 0.36f, cy - r * 0.54f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
}

private fun DrawScope.drawIos3DBolt(cx: Float, cy: Float, r: Float) {
    val bolt = Path().apply {
        moveTo(cx + r * 0.16f, cy - r * 0.92f)
        lineTo(cx - r * 0.62f, cy + r * 0.08f)
        lineTo(cx - r * 0.04f, cy + r * 0.08f)
        lineTo(cx - r * 0.22f, cy + r * 0.92f)
        lineTo(cx + r * 0.62f, cy - r * 0.08f)
        lineTo(cx + r * 0.04f, cy - r * 0.08f)
        close()
    }
    drawPath(
        path = bolt,
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD60A), Color(0xFFFF9500)))
    )
}

private fun DrawScope.drawIos3DGem(cx: Float, cy: Float, r: Float) {
    val gem = Path().apply {
        moveTo(cx - r * 0.48f, cy - r * 0.68f)
        lineTo(cx + r * 0.48f, cy - r * 0.68f)
        lineTo(cx + r * 0.88f, cy - r * 0.18f)
        lineTo(cx, cy + r * 0.86f)
        lineTo(cx - r * 0.88f, cy - r * 0.18f)
        close()
    }
    drawPath(
        path = gem,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFE0F7FA), Color(0xFF32ADE6), Color(0xFF5E5CE6), Color(0xFFA855F7))
        )
    )
}

private fun DrawScope.drawIos3DCrown(cx: Float, cy: Float, r: Float) {
    val crown = Path().apply {
        moveTo(cx - r * 0.72f, cy + r * 0.56f)
        lineTo(cx - r * 0.82f, cy - r * 0.36f)
        lineTo(cx - r * 0.32f, cy + r * 0.06f)
        lineTo(cx, cy - r * 0.64f)
        lineTo(cx + r * 0.32f, cy + r * 0.06f)
        lineTo(cx + r * 0.82f, cy - r * 0.36f)
        lineTo(cx + r * 0.72f, cy + r * 0.56f)
        close()
    }
    drawPath(
        path = crown,
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFFAF5FF), Color(0xFFC084FC), Color(0xFF7E22CE)))
    )
}

private fun DrawScope.drawIos3DCheck(cx: Float, cy: Float, r: Float) {
    drawIosSphereBase(cx, cy, r, listOf(Color(0xFF69F0AE), Color(0xFF34C759), Color(0xFF1B8A3D)))
    val check = Path().apply {
        moveTo(cx - r * 0.44f, cy + r * 0.04f)
        lineTo(cx - r * 0.12f, cy + r * 0.36f)
        lineTo(cx + r * 0.46f, cy - r * 0.28f)
    }
    drawPath(check, color = Color.White, style = Stroke(width = r * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Data coretan garis (stroke) dan stiker emoji pada Pre-Send Media Studio.
 */
data class MediaDrawStroke(
    val points: List<Offset>, // normalized 0..1 coordinates
    val color: Color,
    val widthRatio: Float
)

data class MediaStickerItem(
    val code: String,
    val emojiChar: String,
    val normX: Float,
    val normY: Float,
    val scale: Float = 1f
)

/**
 * Studio Preview & Edit Media (Foto / Video / File) bergaya WhatsApp / iOS
 * sebelum dikirim ke Chat Global, Floating Chat Sesi, maupun Feed Komunitas:
 * - Bisa tambah pesan / caption
 * - Bisa menggambar garis coretan (Drawing Brush multi-warna + ketebalan + Undo)
 * - Bisa menempel stiker emoji 3D gaya iPhone / WhatsApp di atas foto
 * - Bisa menambah teks overlay & memutar foto 90°
 * - Bisa mengaktifkan mode Sekali Lihat (1×)
 */
@Composable
fun PreSendMediaStudioDialog(
    initialAttachmentData: String,
    initialAttachmentName: String,
    initialAttachmentType: String,
    initialCaption: String = "",
    initialViewOnce: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSend: (finalData: String, finalName: String, finalType: String, caption: String, viewOnce: Boolean) -> Unit
) {
    var caption by remember(initialCaption) { mutableStateOf(initialCaption) }
    var viewOnce by remember(initialViewOnce) { mutableStateOf(initialViewOnce) }
    var activeTool by remember { mutableStateOf("none") } // "none", "draw", "sticker", "text"
    var selectedColor by remember { mutableStateOf(Color(0xFFA855F7)) }
    var strokeRatio by remember { mutableFloatStateOf(0.014f) }
    var overlayText by remember { mutableStateOf("") }
    var rotationQuarter by remember { mutableIntStateOf(0) }

    val strokes = remember { mutableStateListOf<MediaDrawStroke>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }
    val stickers = remember { mutableStateListOf<MediaStickerItem>() }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val isPhoto = initialAttachmentType == "photo" || initialAttachmentData.startsWith("data:image/")
    val isVideo = initialAttachmentType == "video" || initialAttachmentData.startsWith("data:video/")

    val sourceBitmap = remember(initialAttachmentData) {
        if (!isPhoto) null
        else runCatching {
            val raw = initialAttachmentData.substringAfter("base64,", initialAttachmentData)
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    val rotatedPreviewBitmap = remember(sourceBitmap, rotationQuarter) {
        if (sourceBitmap == null) null
        else if (rotationQuarter % 4 == 0) sourceBitmap
        else {
            val m = Matrix().apply { postRotate((rotationQuarter % 4) * 90f) }
            runCatching {
                Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, m, true)
            }.getOrDefault(sourceBitmap)
        }
    }

    val drawColors = listOf(
        Color(0xFFA855F7), // Royal Purple
        Color(0xFFFF2D55), // iOS Red/Pink
        Color(0xFFFFCC00), // iOS Gold Yellow
        Color(0xFF34C759), // WhatsApp Emerald
        Color(0xFF00C7BE), // Cyan
        Color(0xFFFFFFFF), // Pure White
        Color(0xFF1C1C1E)  // Obsidian Black
    )

    val stickerPresets = listOf(
        "fire" to "🔥",
        "laugh" to "😂",
        "heart" to "❤️",
        "love_eyes" to "😍",
        "cool" to "😎",
        "like" to "👍",
        "hundred" to "💯",
        "skull" to "💀",
        "crown" to "👑",
        "bolt" to "⚡",
        "gem" to "💎",
        "party" to "🎉"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF507070B)),
            color = Color(0xF507070B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Studio Toolbar (Tutup + Alat Edit: Garis Drawing, Stiker Emoji iOS, Teks, Putar, Undo)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF181822),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            modifier = Modifier.clickable { onDismiss() }
                        ) {
                            Text(
                                text = "✕ Batal",
                                color = Color(0xFFF4F4F6),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPhoto) {
                                StudioToolChip(
                                    label = "✎ Garis",
                                    active = activeTool == "draw",
                                    onClick = { activeTool = if (activeTool == "draw") "none" else "draw" }
                                )
                                StudioToolChip(
                                    label = "😊 Stiker",
                                    active = activeTool == "sticker",
                                    onClick = { activeTool = if (activeTool == "sticker") "none" else "sticker" }
                                )
                                StudioToolChip(
                                    label = "Aa Teks",
                                    active = activeTool == "text",
                                    onClick = { activeTool = if (activeTool == "text") "none" else "text" }
                                )
                                StudioToolChip(
                                    label = "↻ Putar",
                                    active = false,
                                    onClick = { rotationQuarter = (rotationQuarter + 1) % 4 }
                                )
                                if (strokes.isNotEmpty() || stickers.isNotEmpty()) {
                                    StudioToolChip(
                                        label = "↶ Undo",
                                        active = false,
                                        onClick = {
                                            if (activeTool == "sticker" && stickers.isNotEmpty()) {
                                                stickers.removeAt(stickers.lastIndex)
                                            } else if (strokes.isNotEmpty()) {
                                                strokes.removeAt(strokes.lastIndex)
                                            } else if (stickers.isNotEmpty()) {
                                                stickers.removeAt(stickers.lastIndex)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Sub-bar untuk alat Drawing Garis
                    AnimatedVisibility(visible = isPhoto && activeTool == "draw") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF13131C))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                drawColors.forEach { c ->
                                    val selected = c == selectedColor
                                    Box(
                                        modifier = Modifier
                                            .requiredSize(if (selected) 24.dp else 20.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) Color.White else Color.White.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = c }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Tipis" to 0.008f, "Sedang" to 0.015f, "Tebal" to 0.026f).forEach { (lbl, ratio) ->
                                    val sel = kotlin.math.abs(strokeRatio - ratio) < 0.002f
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (sel) Color(0xFF7E22CE) else Color(0xFF1F1F2C),
                                        modifier = Modifier.clickable { strokeRatio = ratio }
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sub-bar untuk tambah Stiker Emoji 3D iOS
                    AnimatedVisibility(visible = isPhoto && activeTool == "sticker") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF13131C))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ketuk tambah:",
                                color = Color(0xFFA1A1AA),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            stickerPresets.forEach { (code, emo) ->
                                Box(
                                    modifier = Modifier
                                        .requiredSize(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable {
                                            val offsetIdx = stickers.size
                                            val nx = (0.28f + (offsetIdx * 0.14f) % 0.48f).coerceIn(0.18f, 0.82f)
                                            val ny = (0.30f + (offsetIdx * 0.12f) % 0.44f).coerceIn(0.18f, 0.82f)
                                            stickers.add(MediaStickerItem(code = code, emojiChar = emo, normX = nx, normY = ny))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    IosWhatsAppEmoji3D(codeOrEmoji = code, size = 24.dp)
                                }
                            }
                        }
                    }

                    // Sub-bar untuk Teks Overlay di atas foto
                    AnimatedVisibility(visible = isPhoto && activeTool == "text") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF13131C))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Teks Foto:", color = Color(0xFFA855F7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.weight(1f)) {
                                if (overlayText.isEmpty()) {
                                    Text(
                                        text = "Ketik teks di atas gambar...",
                                        color = Color(0xFF71717A),
                                        fontSize = 12.sp
                                    )
                                }
                                BasicTextField(
                                    value = overlayText,
                                    onValueChange = { if (it.length <= 60) overlayText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                                    cursorBrush = SolidColor(Color(0xFFC084FC)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (overlayText.isNotEmpty()) {
                                Text(
                                    text = "Hapus",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { overlayText = "" }
                                )
                            }
                        }
                    }
                }

                // Kanvas Tengah: Preview Foto Interaktif (dengan Garis Coretan + Stiker Emoji + Teks) atau Preview Video/File
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101017))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPhoto && rotatedPreviewBitmap != null) {
                        val imgRatio = remember(rotatedPreviewBitmap) {
                            (rotatedPreviewBitmap.width.toFloat() / rotatedPreviewBitmap.height.toFloat().coerceAtLeast(1f))
                                .coerceIn(0.45f, 2.2f)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .aspectRatio(imgRatio, matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(14.dp))
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(activeTool, selectedColor, strokeRatio, canvasSize) {
                                    if (activeTool == "draw") {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                currentPoints.clear()
                                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                                currentPoints.add(
                                                    Offset(
                                                        (start.x / w).coerceIn(0f, 1f),
                                                        (start.y / h).coerceIn(0f, 1f)
                                                    )
                                                )
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                                currentPoints.add(
                                                    Offset(
                                                        (change.position.x / w).coerceIn(0f, 1f),
                                                        (change.position.y / h).coerceIn(0f, 1f)
                                                    )
                                                )
                                            },
                                            onDragEnd = {
                                                if (currentPoints.size >= 2) {
                                                    strokes.add(
                                                        MediaDrawStroke(
                                                            points = currentPoints.toList(),
                                                            color = selectedColor,
                                                            widthRatio = strokeRatio
                                                        )
                                                    )
                                                }
                                                currentPoints.clear()
                                            }
                                        )
                                    } else if (activeTool == "sticker") {
                                        detectTapGestures { tap ->
                                            if (stickers.isNotEmpty()) {
                                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                                val last = stickers.last()
                                                stickers[stickers.lastIndex] = last.copy(
                                                    normX = (tap.x / w).coerceIn(0.1f, 0.9f),
                                                    normY = (tap.y / h).coerceIn(0.1f, 0.9f)
                                                )
                                            }
                                        }
                                    }
                                }
                        ) {
                            Image(
                                bitmap = rotatedPreviewBitmap.asImageBitmap(),
                                contentDescription = initialAttachmentName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Layer Garis Coretan (Freehand Drawing)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val minDim = min(size.width, size.height)
                                fun drawStrokeList(pts: List<Offset>, col: Color, ratio: Float) {
                                    if (pts.size < 2) return
                                    val path = Path().apply {
                                        moveTo(pts.first().x * size.width, pts.first().y * size.height)
                                        for (i in 1 until pts.size) {
                                            lineTo(pts[i].x * size.width, pts[i].y * size.height)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = col,
                                        style = Stroke(
                                            width = (minDim * ratio).coerceAtLeast(3f),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                strokes.forEach { s -> drawStrokeList(s.points, s.color, s.widthRatio) }
                                if (currentPoints.size >= 2) {
                                    drawStrokeList(currentPoints, selectedColor, strokeRatio)
                                }
                            }

                            // Layer Stiker Emoji 3D iOS
                            stickers.forEach { st ->
                                val wDp = canvasSize.width
                                val hDp = canvasSize.height
                                if (wDp > 1 && hDp > 1) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cx = st.normX * size.width
                                        val cy = st.normY * size.height
                                        val r = min(size.width, size.height) * 0.085f * st.scale
                                        when (st.code) {
                                            "heart" -> drawIos3DHeart(cx, cy, r)
                                            "fire" -> drawIos3DFire(cx, cy, r)
                                            "like" -> drawIos3DThumb(cx, cy, r, up = true)
                                            "skull" -> drawIos3DSkull(cx, cy, r)
                                            "hundred" -> drawIos3DHundred(cx, cy, r)
                                            "bolt" -> drawIos3DBolt(cx, cy, r)
                                            "gem" -> drawIos3DGem(cx, cy, r)
                                            "crown" -> drawIos3DCrown(cx, cy, r)
                                            else -> drawIos3DFace(cx, cy, r, mode = st.code)
                                        }
                                    }
                                }
                            }

                            // Layer Teks Overlay
                            if (overlayText.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 18.dp, start = 16.dp, end = 16.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.68f))
                                        .border(1.dp, Color(0xFFC084FC).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = overlayText,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    } else {
                        // Preview Video / Dokumen / File sebelum kirim dengan dukungan tambah Caption
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .requiredSize(72.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF4C1D95), Color(0xFF7E22CE), Color(0xFFA855F7))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isVideo) "▶ VIDEO" else "📄 FILE",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = initialAttachmentName.ifBlank { if (isVideo) "video.mp4" else "dokumen" },
                                color = Color(0xFFF4F4F6),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isVideo) {
                                    "Video siap dikirim · Tambahkan pesan/caption di bawah sebelum mengirim."
                                } else {
                                    "Lampiran berkas siap dikirim · Tambahkan pesan/caption di bawah sebelum mengirim."
                                },
                                color = Color(0xFFA1A1AA),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Bottom Bar: Bar Emoji Cepat iOS + Input Pesan/Caption + Toggle 1× + Tombol Kirim
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Deretan reaksi/emoji cepat gaya iPhone/WhatsApp untuk disisipkan ke caption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        stickerPresets.forEach { (code, emo) ->
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF171722),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.clickable { caption += emo }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IosWhatsAppEmoji3D(codeOrEmoji = code, size = 18.dp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Input Caption / Pesan seperti WhatsApp / iOS
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF171722))
                                .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (caption.isEmpty()) {
                                    Text(
                                        text = "Tambah pesan / caption...",
                                        color = Color(0xFF8E8E99),
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = caption,
                                    onValueChange = { if (it.length <= 500) caption = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                    cursorBrush = SolidColor(Color(0xFFC084FC)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Tombol 1× View-Once seperti WhatsApp
                            if (isPhoto || isVideo) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (viewOnce) Color(0xFF7E22CE) else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (viewOnce) Color(0xFFE9D5FF) else Color.White.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.clickable { viewOnce = !viewOnce }
                                ) {
                                    Text(
                                        text = "1×",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Tombol Kirim (Merender hasil edit foto bila ada coretan/stiker/teks/rotasi)
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7E22CE),
                            border = BorderStroke(1.dp, Color(0xFFD8B4FE)),
                            modifier = Modifier
                                .requiredSize(46.dp)
                                .clickable {
                                    val finalData = if (isPhoto && rotatedPreviewBitmap != null &&
                                        (strokes.isNotEmpty() || stickers.isNotEmpty() || overlayText.isNotBlank() || rotationQuarter % 4 != 0)
                                    ) {
                                        bakeEditedBitmapToDataUri(
                                            base = rotatedPreviewBitmap,
                                            strokes = strokes,
                                            stickers = stickers,
                                            overlayText = overlayText
                                        ) ?: initialAttachmentData
                                    } else {
                                        initialAttachmentData
                                    }
                                    onConfirmSend(
                                        finalData,
                                        initialAttachmentName,
                                        initialAttachmentType,
                                        caption.trim(),
                                        viewOnce
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "➤",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioToolChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) Color(0xFF7E22CE) else Color(0xFF181822),
        border = BorderStroke(
            1.dp,
            if (active) Color(0xFFE9D5FF) else Color.White.copy(alpha = 0.14f)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun bakeEditedBitmapToDataUri(
    base: Bitmap,
    strokes: List<MediaDrawStroke>,
    stickers: List<MediaStickerItem>,
    overlayText: String
): String? = runCatching {
    val w = base.width.coerceAtLeast(1)
    val h = base.height.coerceAtLeast(1)
    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
    val minDim = min(w, h).toFloat()

    // 1. Gambar semua garis coretan (strokes)
    strokes.forEach { stroke ->
        if (stroke.points.size >= 2) {
            val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = AndroidPaint.Style.STROKE
                strokeWidth = (minDim * stroke.widthRatio).coerceAtLeast(4f)
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
            }
            val path = AndroidPath().apply {
                moveTo(stroke.points.first().x * w, stroke.points.first().y * h)
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
                }
            }
            canvas.drawPath(path, p)
        }
    }

    // 2. Gambar semua stiker emoji
    stickers.forEach { st ->
        val tp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textSize = (minDim * 0.14f * st.scale).coerceAtLeast(28f)
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText(st.emojiChar, st.normX * w, st.normY * h + tp.textSize * 0.35f, tp)
    }

    // 3. Gambar teks overlay bila ada
    if (overlayText.isNotBlank()) {
        val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = (minDim * 0.052f).coerceIn(22f, 56f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = AndroidPaint.Align.CENTER
        }
        val textW = textPaint.measureText(overlayText)
        val padH = minDim * 0.035f
        val padV = minDim * 0.020f
        val cy = h - minDim * 0.08f
        val bgRect = RectF(
            (w / 2f - textW / 2f - padH).coerceAtLeast(8f),
            cy - textPaint.textSize - padV,
            (w / 2f + textW / 2f + padH).coerceAtMost(w - 8f),
            cy + padV
        )
        val bgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(190, 20, 10, 38)
            style = AndroidPaint.Style.FILL
        }
        canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)
        canvas.drawText(overlayText, w / 2f, cy - padV * 0.3f, textPaint)
    }

    val baos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 86, baos)
    "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

/**
 * Panel kustomisasi Shape Bubble Chat, Elemen Melayang Beranimasi, dan
 * Generator "Generate with AI" di dalam BubbleCustomizerModal.
 */
@Composable
fun BubbleShapeAndAiSection(
    currentShape: String,
    currentFloating: String,
    onSelectShape: (String) -> Unit,
    onSelectFloating: (String) -> Unit,
    onApplyAiPreset: (bgStart: String, bgEnd: String, accent: String, shape: String, floating: String, styleMode: String) -> Unit
) {
    var aiPrompt by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Pilihan Gaya Bentuk (Shape) Bubble
        Text(
            text = "Gaya Bentuk (Shape) Bubble",
            color = Color(0xFFD4D4D8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "rounded" to "Rounded",
                "sharp" to "Ujung Tajam",
                "morph" to "Liquid Morph",
                "abstract" to "Abstrak HUD",
                "cloud" to "Cloud Soft"
            ).forEach { (id, label) ->
                val sel = currentShape == id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) Color(0xFFF4F4F5) else Color(0xFF18181B),
                    border = BorderStroke(
                        1.dp,
                        if (sel) Color.White else Color.White.copy(alpha = 0.10f)
                    ),
                    modifier = Modifier.clickable { onSelectShape(id) }
                ) {
                    Text(
                        text = label,
                        color = if (sel) Color(0xFF09090B) else Color(0xFFA1A1AA),
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 2. Pilihan Elemen Melayang Beranimasi di dalam Bubble (Tanpa Emoji di Kontrol UI)
        Text(
            text = "Elemen Melayang Beranimasi",
            color = Color(0xFFD4D4D8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "none" to "Tanpa Efek",
                "crystals" to "Kristal",
                "orbs" to "Bola Plasma",
                "stars" to "Bintang",
                "cyber" to "Cyber HUD",
                "sakura" to "Kelopak",
                "fireflies" to "Kunang-Kunang"
            ).forEach { (id, label) ->
                val sel = currentFloating == id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) Color(0xFFF4F4F5) else Color(0xFF18181B),
                    border = BorderStroke(
                        1.dp,
                        if (sel) Color.White else Color.White.copy(alpha = 0.10f)
                    ),
                    modifier = Modifier.clickable { onSelectFloating(id) }
                ) {
                    Text(
                        text = label,
                        color = if (sel) Color(0xFF09090B) else Color(0xFFA1A1AA),
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 3. Generate with AI (Selaras Palet Utama Monokrom Matte)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141418))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generate Bubble with AI",
                    color = Color(0xFFF4F4F5),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "AI Style Engine",
                    color = Color(0xFFA1A1AA),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C0C0F))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (aiPrompt.isEmpty()) {
                        Text(
                            text = "Misal: Kristal Tajam / Cyber Morph / Bintang...",
                            color = Color(0xFF71717A),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = aiPrompt,
                        onValueChange = { if (it.length <= 80) aiPrompt = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF4F4F5),
                    modifier = Modifier.clickable {
                        val generated = synthesizeAiBubbleStyle(aiPrompt)
                        onApplyAiPreset(
                            generated.bgStart,
                            generated.bgEnd,
                            generated.accent,
                            generated.shape,
                            generated.floating,
                            generated.styleMode
                        )
                    }
                ) {
                    Text(
                        text = "Generate",
                        color = Color(0xFF09090B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private data class AiBubbleSynthesis(
    val bgStart: String,
    val bgEnd: String,
    val accent: String,
    val shape: String,
    val floating: String,
    val styleMode: String
)

private fun synthesizeAiBubbleStyle(promptRaw: String): AiBubbleSynthesis {
    val q = promptRaw.trim().lowercase()
    val presets = listOf(
        AiBubbleSynthesis("#2E1065", "#4C1D95", "#C084FC", "morph", "crystals", "aurora"),
        AiBubbleSynthesis("#1E0938", "#581C87", "#E879F9", "sharp", "cyber", "neon"),
        AiBubbleSynthesis("#0F172A", "#1E3A8A", "#38BDF8", "abstract", "stars", "aurora"),
        AiBubbleSynthesis("#064E3B", "#0F766E", "#34D399", "sharp", "cyber", "neon"),
        AiBubbleSynthesis("#3B0764", "#701A75", "#F472B6", "cloud", "sakura", "aurora"),
        AiBubbleSynthesis("#1F1235", "#311042", "#FACC15", "morph", "fireflies", "aurora")
    )
    if (q.isBlank()) {
        return presets[Random.nextInt(presets.size)]
    }
    val shape = when {
        "tajam" in q || "sharp" in q || "lancip" in q || "blade" in q -> "sharp"
        "morph" in q || "liquid" in q || "cair" in q || "organik" in q -> "morph"
        "abstrak" in q || "abstract" in q || "hud" in q || "mecha" in q -> "abstract"
        "cloud" in q || "awan" in q || "soft" in q -> "cloud"
        else -> listOf("morph", "sharp", "abstract", "cloud")[kotlin.math.abs(q.hashCode()) % 4]
    }
    val floating = when {
        "kristal" in q || "crystal" in q || "ungu" in q || "amethyst" in q || "diamond" in q -> "crystals"
        "bintang" in q || "star" in q || "galaxy" in q || "angkasa" in q -> "stars"
        "cyber" in q || "matrix" in q || "hacker" in q || "hud" in q -> "cyber"
        "sakura" in q || "bunga" in q || "pink" in q -> "sakura"
        "kunang" in q || "firefl" in q || "emas" in q || "gold" in q -> "fireflies"
        "orb" in q || "plasma" in q || "neon" in q -> "orbs"
        else -> listOf("crystals", "orbs", "stars", "cyber", "sakura", "fireflies")[kotlin.math.abs(q.hashCode() / 3) % 6]
    }
    return when {
        "ungu" in q || "purple" in q || "amethyst" in q || "founder" in q ->
            AiBubbleSynthesis("#230B42", "#4C1D95", "#C084FC", shape, floating, "aurora")
        "merah" in q || "red" in q || "crimson" in q || "fire" in q ->
            AiBubbleSynthesis("#3B0A14", "#7F1D1D", "#F87171", shape, floating, "neon")
        "hijau" in q || "green" in q || "emerald" in q || "matrix" in q ->
            AiBubbleSynthesis("#052E16", "#065F46", "#34D399", shape, floating, "neon")
        "biru" in q || "blue" in q || "cyber" in q || "ocean" in q ->
            AiBubbleSynthesis("#082F49", "#1E3A8A", "#38BDF8", shape, floating, "aurora")
        "pink" in q || "sakura" in q || "rose" in q ->
            AiBubbleSynthesis("#3B0736", "#831843", "#F472B6", shape, floating, "aurora")
        else -> presets[kotlin.math.abs(q.hashCode()) % presets.size].copy(shape = shape, floating = floating)
    }
}

/**
 * Overload BubbleShapeAndAiSection untuk GlobalChatScreen (3-parameter AI preset callback).
 */
@Composable
fun BubbleShapeAndAiSection(
    selectedShape: String,
    selectedFloating: String,
    onSelectShape: (String) -> Unit,
    onSelectFloating: (String) -> Unit,
    onApplyAiPreset: (styleMode: String, shape: String, floating: String) -> Unit,
) {
    BubbleShapeAndAiSection(
        currentShape = selectedShape.lowercase(),
        currentFloating = selectedFloating.lowercase(),
        onSelectShape = onSelectShape,
        onSelectFloating = onSelectFloating,
        onApplyAiPreset = { _, _, _, shape, floating, styleMode ->
            val mappedStyle = when (styleMode.lowercase()) {
                "aurora" -> "glass"
                "neon" -> "amber"
                else -> "glass"
            }
            onApplyAiPreset(mappedStyle, shape, floating)
        },
    )
}

/**
 * Overload PreSendMediaStudioDialog berbasis Bitmap untuk GlobalChatScreen.
 */
@Composable
fun PreSendMediaStudioDialog(
    mediaKind: String,
    mediaName: String,
    initialBitmap: Bitmap?,
    initialCaption: String = "",
    initialViewOnce: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSend: (caption: String, editedBitmap: Bitmap?) -> Unit,
) {
    val initialData = remember(initialBitmap, mediaKind) {
        if (initialBitmap != null) {
            val out = ByteArrayOutputStream()
            initialBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } else {
            ""
        }
    }
    val mappedType = when (mediaKind.lowercase()) {
        "image", "photo" -> "photo"
        "video" -> "video"
        else -> "file"
    }
    PreSendMediaStudioDialog(
        initialAttachmentData = initialData,
        initialAttachmentName = mediaName,
        initialAttachmentType = mappedType,
        initialCaption = initialCaption,
        initialViewOnce = initialViewOnce,
        onDismiss = onDismiss,
        onConfirmSend = { finalData, _, _, caption, _ ->
            val decoded = if (finalData != initialData && finalData.startsWith("data:image/")) {
                runCatching {
                    val raw = finalData.substringAfter("base64,", "")
                    val bytes = Base64.decode(raw, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            } else {
                null
            }
            onConfirmSend(caption, decoded)
        },
    )
}

