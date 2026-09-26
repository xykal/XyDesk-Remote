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
// XyDesk Design System — brand violet (properti XyVerse)
// =============================================================

val XyViolet = Color(0xFF7C3AED)
val XyVioletBright = Color(0xFFA78BFA)
val XyVioletDeep = Color(0xFF5B21B6)
val XyFuchsia = Color(0xFFF0ABFC)
val XyInk = Color(0xFF0E0B16)
val XyInkSurface = Color(0xFF171226)
val XyInkCard = Color(0xFF1F1833)
val XyLine = Color(0xFF2E2447)

val DarkScheme = darkColorScheme(
    primary = XyVioletBright,
    onPrimary = Color(0xFF1E1233),
    primaryContainer = Color(0xFF3B2A66),
    onPrimaryContainer = Color(0xFFEBE4FF),
    secondary = XyFuchsia,
    onSecondary = Color(0xFF330A4D),
    secondaryContainer = Color(0xFF4A2160),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF0A2A55),
    background = XyInk,
    onBackground = Color(0xFFEFEBFB),
    surface = XyInkSurface,
    onSurface = Color(0xFFEFEBFB),
    surfaceVariant = XyInkCard,
    onSurfaceVariant = Color(0xFFCFC6E4),
    outline = XyLine,
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3D0000),
)

val LightScheme = lightColorScheme(
    primary = XyVioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFFA21CAF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBEBFA),
    tertiary = Color(0xFF1D4ED8),
    background = Color(0xFFF8F6FF),
    onBackground = Color(0xFF1E1930),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1930),
    surfaceVariant = Color(0xFFF1EDFB),
    onSurfaceVariant = Color(0xFF4C4462),
    outline = Color(0xFFD8D0EA),
    error = Color(0xFFB3261E),
    onError = Color.White,
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
