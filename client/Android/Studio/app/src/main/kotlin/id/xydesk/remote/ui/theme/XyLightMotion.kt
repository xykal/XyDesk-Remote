package id.xydesk.remote.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Finite UI motion only. Do not enable infinite badge/shimmer loops globally. */
internal val LocalUiMotionEnabled = staticCompositionLocalOf { false }
@Composable
internal fun <T> XyRouteMotion(target: T, content: @Composable (T) -> Unit) {
    Crossfade(targetState = target, animationSpec = tween(if (LocalUiMotionEnabled.current) 140 else 0), label = "route") { content(it) }
}
