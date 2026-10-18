package id.xydesk.remote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared geometry; values preserve the existing theme and chat layout. */
object XyRadius {
    val small = 10.dp
    val medium = 14.dp
    val large = 20.dp
    val extraLarge = 26.dp
    val pill = 18.dp
}

object XySpacing {
    val compact = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
}

/** Radius lebih ramah sentuh; kontrol pill tetap terbaca sebagai kontrol. */
val XyPill = RoundedCornerShape(XyRadius.pill)

val XyShapes = Shapes(
    small = RoundedCornerShape(XyRadius.small),
    medium = RoundedCornerShape(XyRadius.medium),
    large = RoundedCornerShape(XyRadius.large),
    extraLarge = RoundedCornerShape(XyRadius.extraLarge),
)

