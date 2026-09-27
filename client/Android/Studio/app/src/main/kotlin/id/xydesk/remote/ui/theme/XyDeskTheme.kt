package id.xydesk.remote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.R

// =============================================================
// XyDesk Design System — deep slate + calm teal, tuned for long remote sessions
// =============================================================

val XyViolet = Color(0xFF0B8E7E)
val XyVioletBright = Color(0xFF69E3CB)
val XyVioletDeep = Color(0xFF08796D)
val XyFuchsia = Color(0xFF91EBD7)
val XyInk = Color(0xFF0B1217)
val XyInkSurface = Color(0xFF121D24)
val XyInkCard = Color(0xFF1B2932)
val XyLine = Color(0xFF334650)

val DarkScheme = darkColorScheme(
    primary = XyVioletBright,
    onPrimary = Color(0xFF052B27),
    primaryContainer = Color(0xFF16443E),
    onPrimaryContainer = Color(0xFFD7FFF4),
    secondary = Color(0xFF91BDF2),
    onSecondary = Color(0xFF112B43),
    secondaryContainer = Color(0xFF263D55),
    onSecondaryContainer = Color(0xFFDCEBFF),
    tertiary = Color(0xFFE6B86A),
    onTertiary = Color(0xFF372500),
    background = XyInk,
    onBackground = Color(0xFFEAF1F4),
    surface = XyInkSurface,
    onSurface = Color(0xFFEAF1F4),
    surfaceVariant = XyInkCard,
    onSurfaceVariant = Color(0xFFB7C7CE),
    outline = XyLine,
    outlineVariant = Color(0xFF40545E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val LightScheme = lightColorScheme(
    primary = XyVioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F4EB),
    onPrimaryContainer = Color(0xFF073A33),
    secondary = XyVioletDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAFF),
    onSecondaryContainer = Color(0xFF152E48),
    tertiary = Color(0xFF8B5B08),
    onTertiary = Color.White,
    background = Color(0xFFF2F6F6),
    onBackground = Color(0xFF172126),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172126),
    surfaceVariant = Color(0xFFE7EFF0),
    onSurfaceVariant = Color(0xFF52646A),
    outline = Color(0xFFC8D7D9),
    outlineVariant = Color(0xFFDCE6E7),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

// Space Grotesk = display, Inter = body (variable fonts, OFL).
val XyDisplay = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)
val XyBody = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

val XyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun XyDeskTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = XyShapes,
        typography = MaterialTheme.typography.copy(
            displaySmall = TextStyle(
                fontFamily = XyDisplay, fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
            ),
            displayMedium = TextStyle(
                fontFamily = XyDisplay, fontSize = 32.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = XyDisplay, fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = XyDisplay, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            ),
            titleLarge = TextStyle(
                fontFamily = XyDisplay, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            ),
            titleMedium = TextStyle(
                fontFamily = XyDisplay, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            ),
            titleSmall = TextStyle(
                fontFamily = XyBody, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            ),
            bodyLarge = TextStyle(fontFamily = XyBody, fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontFamily = XyBody, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = XyBody, fontSize = 12.5.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(
                fontFamily = XyDisplay, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = XyBody, fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = XyBody, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp,
            ),
        ),
        content = content,
    )
}
