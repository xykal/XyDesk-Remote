package id.xydesk.remote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.theme.XyDisplay
import id.xydesk.remote.ui.theme.XyThemeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash screen XyDesk Remote minimalis murni (Ronde 64):
 * Cukup menampilkan tipografi "XYDESK REMOTE" yang tajam, bersih, dan elegan.
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
    val scale = remember { Animatable(0.95f) }
    val barWidth = remember { Animatable(0f) }
    val minTimeDone = remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        }
        launch {
            barWidth.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
        }
        delay(360)
        minTimeDone.value = true
    }

    LaunchedEffect(ready, minTimeDone.value) {
        if (ready && minTimeDone.value) {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            )
            onFinished()
        }
    }

    val bgBrush = if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF05070B),
                Color(0xFF0A0E17),
                Color(0xFF05070B),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF8FAFC),
                Color(0xFFFFFFFF),
            ),
        )
    }

    val primaryWordColor = if (dark) Color(0xFFF8FAFC) else Color(0xFF090D16)
    val accentWordBrush = if (dark) {
        Brush.linearGradient(
            colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8)),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF334155), Color(0xFF64748B)),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .alpha(alpha.value)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = primaryWordColor,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.2.sp,
                            ),
                        ) {
                            append("XYDESK ")
                        }
                        withStyle(
                            SpanStyle(
                                brush = accentWordBrush,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 4.5.sp,
                            ),
                        ) {
                            append("REMOTE")
                        }
                    },
                    style = TextStyle(
                        fontFamily = XyDisplay,
                        fontSize = 26.sp,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .width((64f * barWidth.value).dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryWordColor.copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}
