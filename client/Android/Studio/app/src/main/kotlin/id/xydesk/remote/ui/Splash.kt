package id.xydesk.remote.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import id.xydesk.remote.R
import kotlinx.coroutines.delay

/**
 * Splash sederhana: hanya logo XyDesk di tengah latar gelap.
 * Tidak ada teks tahapan, badge versi, grid, atau animasi orbit.
 */
@Composable
fun XySplashScreen(
    ready: Boolean,
    dark: Boolean = true,
    onDone: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var minTimeElapsed by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "splashAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "splashScale",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(650)
        minTimeElapsed = true
    }

    LaunchedEffect(ready, minTimeElapsed) {
        if (ready && minTimeElapsed) onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF14161A) else Color(0xFFF6F7F9)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.xy_mark_white),
            contentDescription = "XyDesk",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(148.dp)
                .alpha(alpha)
                .scale(scale),
        )
    }
}
