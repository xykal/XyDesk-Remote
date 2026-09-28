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
// XyDesk design tokens.
//
// Aturan: netral, kontras tinggi, hairlines, tanpa glow / gradient
// dekoratif. Warna hanya dipakai untuk status, bukan hiasan. Radius
// kecil (6-14dp) untuk panel; kontrol utama pakai bentuk pil penuh.
// =============================================================

// Tema gelap: dipisah lebih tegas (latar -> permukaan -> permukaan naik)
// supaya kartu dan panel tidak menyatu jadi satu bidang gelap. Garis naik
// satu tingkat supaya hairline tetap kelihatan di layar terang.
val XyBg = Color(0xFF06070A)
val XySurface = Color(0xFF11141A)
val XySurfaceAlt = Color(0xFF191E25)
val XyLine = Color(0xFF2A3038)
val XyLineStrong = Color(0xFF404952)
val XyText = Color(0xFFF0F3F6)
val XyTextDim = Color(0xFFB6BEC6)
val XyTextFaint = Color(0xFF9AA2AB)
val XyAccent = Color(0xFFF1F4F6)
val XyAccentInk = Color(0xFF0A0B0D)
val XyOk = Color(0xFF57C08B)
val XyWarn = Color(0xFFD7A54E)
val XyDanger = Color(0xFFE0706F)

val DarkScheme = darkColorScheme(
    primary = XyAccent,
    onPrimary = XyAccentInk,
    primaryContainer = XySurfaceAlt,
    onPrimaryContainer = XyText,
    secondary = XyTextDim,
    onSecondary = XyAccentInk,
    secondaryContainer = XySurfaceAlt,
    onSecondaryContainer = XyText,
    tertiary = XyOk,
    background = XyBg,
    onBackground = XyText,
    surface = XySurface,
    onSurface = XyText,
    surfaceVariant = XySurfaceAlt,
    onSurfaceVariant = XyTextDim,
    outline = XyLine,
    outlineVariant = XyLineStrong,
    error = XyDanger,
    onError = XyAccentInk,
    errorContainer = Color(0xFF3A1D1D),
    onErrorContainer = Color(0xFFFFDAD8),
    scrim = Color(0xCC000000),
)

val LightScheme = lightColorScheme(
    primary = Color(0xFF14171B),
    onPrimary = Color(0xFFF7F9FA),
    primaryContainer = Color(0xFFE7EAEE),
    onPrimaryContainer = Color(0xFF14171B),
    secondary = Color(0xFF5B636B),
    onSecondary = Color(0xFFF7F9FA),
    secondaryContainer = Color(0xFFE9ECEF),
    onSecondaryContainer = Color(0xFF14171B),
    tertiary = Color(0xFF1F7A55),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF14171B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14171B),
    surfaceVariant = Color(0xFFEFF1F4),
    onSurfaceVariant = Color(0xFF5B636B),
    outline = Color(0xFFDDE1E6),
    outlineVariant = Color(0xFFC6CCD3),
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

/** Panel: radius kecil. Kontrol: [XyPill]. */
val XyPill = RoundedCornerShape(50)

val XyShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
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
                fontFamily = XyDisplay, fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = XyDisplay, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = XyDisplay, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            ),
            titleLarge = TextStyle(
                fontFamily = XyDisplay, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            ),
            titleMedium = TextStyle(
                fontFamily = XyDisplay, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            ),
            titleSmall = TextStyle(
                fontFamily = XyBody, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            ),
            bodyLarge = TextStyle(fontFamily = XyBody, fontSize = 15.5.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontFamily = XyBody, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = XyBody, fontSize = 12.5.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(
                fontFamily = XyDisplay, fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = XyBody, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = XyBody, fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp,
            ),
        ),
        content = content,
    )
}
