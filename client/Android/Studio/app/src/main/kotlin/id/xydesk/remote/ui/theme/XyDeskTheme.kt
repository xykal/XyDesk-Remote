package id.xydesk.remote.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import id.xydesk.remote.R
import id.xydesk.remote.ui.AppPrefs

// =============================================================
// XyDesk design tokens.
//
// Aturan: permukaan matte gelap netral dan rounded, aksen ungu untuk
// primary/kontrol aktif, kontras tinggi, tanpa glow/gradient dekoratif.
// Warna status (ok/warn/danger) tetap abu netral.
// Garis dekoratif dibuat lembut; input, slider, dan switch tetap jelas terbaca.
// =============================================================

// Tema gelap matte: latar nyaris hitam, permukaan solid, tanpa efek kaca.
// Perbedaan luminans kecil antarlapisan menjaga hierarki tanpa menambah
// gradient, blur, atau glow dekoratif. Teks tetap off-white demi kontras.
val XyBg = Color(0xFF08080A)
val XySurface = Color(0xFF101013)
val XySurfaceAlt = Color(0xFF1A1A1E)
val XyPrimaryContainer = Color(0xFF232327)
val XyLine = Color(0xFF2A2A2E)
val XyLineStrong = Color(0xFF3C3C42)
val XyText = Color(0xFFF3F3F5)
val XyTextDim = Color(0xFFB2B2B8)
val XyTextFaint = Color(0xFF808086)
// Aksen ungu (2026-10-24): primary di semua layar jadi violet seperti
// referensi Connection Hub; permukaan tetap gelap netral (bukan lavender
// penuh) dan status tetap abu netral.
val XyAccent = Color(0xFF8B5CF6)
val XyAccentInk = Color(0xFFFFFFFF)
val XyOk = Color(0xFFE2E2E6)
val XyWarn = Color(0xFFAAAAAA)
val XyDanger = Color(0xFF858585)

val DarkScheme = darkColorScheme(
    primary = XyAccent,
    onPrimary = XyAccentInk,
    primaryContainer = Color(0xFF2A1650),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1E1040),
    secondaryContainer = XySurfaceAlt,
    onSecondaryContainer = XyText,
    tertiary = Color(0xFFC4B5FD),
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
    errorContainer = Color(0xFF2C2C31),
    onErrorContainer = Color(0xFFE4E4E8),
    scrim = Color(0x47000000),
)

val LightScheme = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDEF),
    onSecondaryContainer = Color(0xFF232328),
    tertiary = Color(0xFF8B5CF6),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF1A1A1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF5F5F65),
    outline = Color(0xFFDDDDDF),
    outlineVariant = Color(0xFFCECED2),
    error = Color(0xFF4A4A50),
    onError = Color.White,
    errorContainer = Color(0xFFE6E6E9),
    onErrorContainer = Color(0xFF29292D),
    scrim = Color(0x38000000),
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

/** Sudut membulat penuh (all-rounded pill) yang konsisten di seluruh aplikasi. */
val XyPill = RoundedCornerShape(50)

val XyShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Mode tema sebagai state Compose yang di-share lintas activity.
 *
 * Dulu mengganti tema memanggil Activity.recreate() — seluruh layar
 * kedip-kedip dan state UI (posisi scroll, drawer) ikut reset. Sekarang
 * mode disimpan di sini: mengubahnya membuat semua layar yang memakai
 * [xyDark] recompose sendiri, tanpa recreate.
 *
 * [mode] bernilai Int.MIN_VALUE sampai [init] dipanggil di activity
 * pertama (membaca preferensi tersimpan).
 */
object XyThemeState {
    const val FOLLOW_SYSTEM = 0
    const val DARK = 1
    const val LIGHT = 2

    var mode by mutableIntStateOf(Int.MIN_VALUE)
        private set

    /** Baca preferensi sekali per proses; panggil di setContent activity. */
    fun init(prefs: AppPrefs) {
        if (mode == Int.MIN_VALUE) mode = prefs.themeMode
    }

    /** Set dari UI pengaturan: tulis prefs + state sekaligus. */
    fun set(prefs: AppPrefs, value: Int) {
        mode = value
        prefs.themeMode = value
    }
}

/**
 * Gelap atau tidaknya app SAAT INI, sebagai state: ikut pilihan user,
 * fallback ke tema sistem.
 */
@Composable
fun xyDark(): Boolean = when (XyThemeState.mode) {
    XyThemeState.DARK -> true
    XyThemeState.LIGHT -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun XyDeskTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.decorView.setBackgroundColor(scheme.background.toArgb())
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !dark
            insets.isAppearanceLightNavigationBars = !dark
        }
    }
    val uiMotionEnabled = rememberDecorativeMotionEnabled()
    MaterialTheme(
        colorScheme = scheme,
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
        content = {
            CompositionLocalProvider(
                LocalContentColor provides scheme.onSurface,
                LocalUiMotionEnabled provides uiMotionEnabled,
                LocalDecorativeMotionEnabled provides uiMotionEnabled,
            ) {
                content()
            }
        },
    )
}
