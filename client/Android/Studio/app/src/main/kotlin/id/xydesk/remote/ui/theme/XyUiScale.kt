package id.xydesk.remote.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import id.xydesk.remote.ui.AppPrefs
import id.xydesk.remote.ui.XyUiScaleState
import kotlin.math.min

/**
 * Skala UI global terhadap ukuran layar.
 *
 * Masalah: banyak nilai dp/sp tetap yang dirancang di HP besar sehingga di HP
 * kecil (360dp ke bawah, atau mode Tampilan "Besar") UI terasa kegedean dan
 * tidak konsisten antar layar (menu utama, menu sesi, dialog, kartu).
 *
 * Solusi: seluruh subtree dikalikan faktor skala lewat [LocalDensity], sehingga
 * SEMUA layar mengecil/membesar secara proporsional dan konsisten — termasuk
 * menu sesi fullscreen — tanpa mengubah tiap komponen satu per satu. Skala
 * huruf (fontScale milik pengguna) tetap dihormati.
 *
 * Mode: 0 = Otomatis (adaptif), 1 = Kecil (0.85), 2 = Sedang (0.93), 3 = Besar (1.0).
 */
@Composable
fun XyScaledDensity(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = XyUiScaleState.current(AppPrefs(context))
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    // Referensi = HP besar umum (Pixel/flagship 393x852dp). Layar lebih kecil
    // mendapat faktor < 1; layar besar tetap 1.0 (tidak berubah).
    val auto = min(1f, min(cfg.screenWidthDp / 393f, cfg.screenHeightDp / 852f))
        .coerceIn(0.85f, 1f)
    val factor = when (mode) {
        1 -> 0.85f
        2 -> 0.93f
        3 -> 1f
        else -> auto
    }
    if (factor >= 0.999f) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * factor, density.fontScale),
        content = content,
    )
}
