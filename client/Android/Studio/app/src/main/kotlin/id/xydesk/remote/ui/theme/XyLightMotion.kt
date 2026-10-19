package id.xydesk.remote.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Finite UI motion only. Do not enable infinite badge/shimmer loops globally. */
internal val LocalUiMotionEnabled = staticCompositionLocalOf { false }
@Composable
internal fun <T> XyRouteMotion(target: T, content: @Composable (T) -> Unit) {
    val enabled = LocalUiMotionEnabled.current
    val opacity = remember { Animatable(1f) }
    LaunchedEffect(target, enabled) {
        // Keep one screen alive, not old+new chat/feed trees with two players or
        // sockets. A subtle enter fade never hides a screen behind an opacity-0 gate.
        opacity.snapTo(if (enabled) 0.92f else 1f)
        if (enabled) opacity.animateTo(1f, tween(140))
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }) {
        key(target) { content(target) }
    }
}
