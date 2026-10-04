package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.theme.XyPill

/**
 * Compact vertical mouse-wheel control: tap either arrow for one notch, or
 * drag the inner pill to send smooth wheel units to the remote desktop.
 */
@Composable
internal fun SessionScrollPill(
    plate: HudPlate,
    scrollSpeed: Float,
    onScrollUnits: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hudPalette(plate)
    val trackColor = if (plate == HudPlate.NONE) Color(0xA60B0D10) else palette.plate
    val edgeColor = if (plate == HudPlate.NONE) Color(0xBFFFFFFF) else palette.border
    val latestScroll = rememberUpdatedState(onScrollUnits)
    var thumbFraction by remember { mutableFloatStateOf(0.5f) }
    val thumbHeight = 28.dp
    val travel = 92.dp
    val bodyHeight = 120.dp

    Column(
        modifier = modifier
            .width(54.dp)
            .clip(XyPill)
            .background(trackColor)
            .border(1.dp, edgeColor, XyPill)
            .padding(horizontal = 7.dp, vertical = 8.dp)
            .semantics {
                contentDescription = xyNow("Kontrol scroll mouse atas dan bawah", "Mouse scroll up and down control")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { latestScroll.value(120) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                XyIcons.ChevronUp,
                contentDescription = xyNow("Scroll mouse ke atas", "Scroll mouse up"),
                tint = palette.ink,
                modifier = Modifier.size(16.dp),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(bodyHeight)
                .pointerInput(scrollSpeed) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val thumbPx = thumbHeight.toPx()
                        val maxTravel = (size.height - thumbPx).coerceAtLeast(1f)
                        thumbFraction = ((down.position.y - thumbPx / 2f) / maxTravel).coerceIn(0f, 1f)
                        var lastY = down.position.y
                        val accumulator = ScrollWheelAccumulator()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val deltaY = change.position.y - lastY
                            if (deltaY != 0f) {
                                thumbFraction = (thumbFraction + deltaY / maxTravel).coerceIn(0f, 1f)
                                accumulator.consume(deltaY, scrollSpeed)
                                    .takeIf { it != 0 }
                                    ?.let(latestScroll.value)
                                lastY = change.position.y
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val thumbOffset = travel * thumbFraction
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffset)
                    .width(12.dp)
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(50))
                    .background(palette.ink.copy(alpha = 0.76f))
                    .border(1.dp, edgeColor.copy(alpha = 0.72f), RoundedCornerShape(50)),
            )
        }

        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { latestScroll.value(-120) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                XyIcons.ChevronDown,
                contentDescription = xyNow("Scroll mouse ke bawah", "Scroll mouse down"),
                tint = palette.ink,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
