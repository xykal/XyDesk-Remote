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
// Aturan: permukaan matte dan rounded, kontras tinggi, tanpa glow/gradient.
// Garis dekoratif dibuat lembut; input, slider, dan switch tetap jelas terbaca.
// =============================================================

// Tema gelap matte: latar nyaris hitam, permukaan solid, tanpa efek kaca.
// Perbedaan luminans kecil antarlapisan menjaga hierarki tanpa menambah
// gradient, blur, atau glow dekoratif. Teks tetap off-white demi kontras.
val XyBg = Color(0xFF07060A)
val XySurface = Color(0xFF0E0D12)
val XySurfaceAlt = Color(0xFF18161D)
val XyPrimaryContainer = Color(0xFF272132)
val XyLine = Color(0xFF2A2730)
val XyLineStrong = Color(0xFF3C3843)
val XyText = Color(0xFFF4F2F7)
val XyTextDim = Color(0xFFB4AFBA)
val XyTextFaint = Color(0xFF817B88)
val XyAccent = Color(0xFFD8C9FF)
val XyAccentInk = Color(0xFF211833)
val XyOk = Color(0xFF57C08B)
val XyWarn = Color(0xFFD7A54E)
val XyDanger = Color(0xFFE0706F)

val DarkScheme = darkColorScheme(
    primary = XyAccent,
    onPrimary = XyAccentInk,
    primaryContainer = XyPrimaryContainer,
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
    scrim = Color(0x47000000),
)

val LightScheme = lightColorScheme(
    primary = Color(0xFF6A6084),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECE9F2),
    onPrimaryContainer = Color(0xFF2F2B39),
    secondary = Color(0xFF625C6D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9F3),
    onSecondaryContainer = Color(0xFF292532),
    tertiary = Color(0xFF267554),
    background = Color(0xFFF8F7FA),
    onBackground = Color(0xFF211F26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF201D26),
    surfaceVariant = Color(0xFFF1EFF4),
    onSurfaceVariant = Color(0xFF625E68),
    outline = Color(0xFFDED9E7),
    outlineVariant = Color(0xFFCEC9D4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
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

/** Radius lebih ramah sentuh; kontrol pill tetap terbaca sebagai kontrol. */
val XyPill = RoundedCornerShape(12.dp)

val XyShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
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
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                content()
            }
        },
    )
}
