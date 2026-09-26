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

val XyViolet = Color(0xFF6957D8)
val XyVioletBright = Color(0xFFB7AAFF)
val XyVioletDeep = Color(0xFF5747C7)
val XyFuchsia = Color(0xFFB7AAFF)
val XyInk = Color(0xFF111217)
val XyInkSurface = Color(0xFF191A20)
val XyInkCard = Color(0xFF23252D)
val XyLine = Color(0xFF353741)

val DarkScheme = darkColorScheme(
    primary = XyVioletBright,
    onPrimary = Color(0xFF21194A),
    primaryContainer = Color(0xFF302950),
    onPrimaryContainer = Color(0xFFE9E5FF),
    secondary = Color(0xFFB7AAFF),
    onSecondary = Color(0xFF241A51),
    secondaryContainer = Color(0xFF302950),
    onSecondaryContainer = Color(0xFFE9E5FF),
    tertiary = Color(0xFF91C5FF),
    onTertiary = Color(0xFF102A43),
    background = XyInk,
    onBackground = Color(0xFFF1F1F6),
    surface = XyInkSurface,
    onSurface = Color(0xFFF1F1F6),
    surfaceVariant = XyInkCard,
    onSurfaceVariant = Color(0xFFC3C5D0),
    outline = XyLine,
    outlineVariant = Color(0xFF41434E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val LightScheme = lightColorScheme(
    primary = XyVioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE7FF),
    onPrimaryContainer = Color(0xFF21194A),
    secondary = XyVioletDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE7FF),
    onSecondaryContainer = Color(0xFF21194A),
    tertiary = Color(0xFF245D9B),
    onTertiary = Color.White,
    background = Color(0xFFF4F5F8),
    onBackground = Color(0xFF191A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191A20),
    surfaceVariant = Color(0xFFEBEDF2),
    onSurfaceVariant = Color(0xFF555864),
    outline = Color(0xFFD4D6DF),
    outlineVariant = Color(0xFFE2E4EA),
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
