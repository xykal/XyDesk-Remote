package id.xydesk.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Primitif "liquid glass" XyDesk.
 *
 * Gayanya diambil dari pemutar musik mengambang ([id.xydesk.remote.ui.SpotifyFloatingPlayer])
 * atas permintaan pengguna, lalu diangkat ke sini supaya seluruh app memakai
 * satu bahasa visual yang sama.
 *
 * Kacanya **disusun dari lapisan**, bukan blur sungguhan, karena tiga alasan
 * yang sudah diverifikasi di proyek ini:
 *  1. `Modifier.blur` memburamkan konten yang ditempeli, bukan yang di
 *     belakangnya — jadi tidak bisa dipakai sebagai backdrop blur.
 *  2. Backdrop blur tidak bekerja di atas `SurfaceView` tempat layar RDP
 *     digambar.
 *  3. `Modifier.blur` baru ada di API 31, sedangkan `MIN_API` proyek 24.
 *
 * Karena itu efeknya dibangun dari permukaan transparan + kilau vertikal +
 * tepi terang. Hasilnya tetap terbaca di atas latar apa pun (wallpaper,
 * desktop remote, tema terang/gelap) karena tidak bergantung pada warna latar.
 */

/** Radius kartu kaca. 20dp konsisten dengan [XyCard] lama supaya grid rapi. */
val XyGlassShape: Shape = RoundedCornerShape(20.dp)

/** Radius lebih kecil untuk ubin kecil di grid dashboard. */
val XyGlassTileShape: Shape = RoundedCornerShape(16.dp)

/**
 * Permukaan datar (matte) XyDesk.
 *
 * SEJARAH 2026-10-10: pemilik meminta gaya kaca/liquid glass DIBUANG TOTAL —
 * kilau, sapuan diagonal, dan bevel membuat tampilan "pusing" dan tidak
 * cocok. Semua permukaan kini matte: warna solid hampir pekat + garis tepi
 * tipis satu warna. Nama fungsi tetap [xyGlass] supaya puluhan pemanggil lama
 * tidak perlu diubah; [opacity] masih berpengaruh, [strength] diabaikan
 * (dipertahankan hanya untuk kompatibilitas tanda tangan).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.xyGlass(
    shape: Shape = XyGlassShape,
    opacity: Float = 1f,
    strength: Float = 1f,
    edge: Boolean = true,
): Modifier = this
    .clip(shape)
    .background(MaterialTheme.colorScheme.surface.copy(alpha = (0.97f * opacity).coerceIn(0f, 1f)))
    .then(
        if (edge) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), shape)
        else Modifier
    )

/**
 * Kartu kaca untuk mengelompokkan konten — pengganti [XyCard] di layar yang
 * sudah memakai bahasa visual baru. Isinya `ColumnScope` seperti [XyCard]
 * supaya pemindahan konten lama tidak perlu mengubah tubuh kartunya.
 */
@Composable
fun XyGlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    shape: Shape = XyGlassShape,
    opacity: Float = 1f,
    strength: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .xyGlass(shape = shape, opacity = opacity, strength = strength)
            .padding(padding),
        content = content,
    )
}

/**
 * Ubin dashboard: ikon dalam kotak membulat + judul + keterangan opsional.
 *
 * Dipakai berpasangan dengan [XyTileGrid] supaya daftar menu tampil sebagai
 * grid kartu yang rata, bukan daftar panjang yang harus digulir.
 */
@Composable
fun XyGlassTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    // Ubin grid panel memakai 2 baris karena judul seksi ada yang panjang
    // ("Clipboard otomatis (Teks & Gambar)") dan terpotong di lebar setengah
    // panel. Nilai bawaan 1 menjaga tampilan ubin dashboard tetap satu baris.
    titleMaxLines: Int = 1,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    // Tekanan tombol dihaluskan; perubahan warna instan terasa kasar di grid.
    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled -> ink.copy(alpha = 0.30f)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> ink
        },
        animationSpec = tween(durationMillis = 140),
        label = "tileIconTint",
    )
    val iconBox by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> MaterialTheme.colorScheme.primary
            else -> ink.copy(alpha = 0.08f)
        },
        animationSpec = tween(durationMillis = 140),
        label = "tileIconBox",
    )
    val pad by animateDpAsState(
        targetValue = if (selected) 9.dp else 10.dp,
        animationSpec = tween(durationMillis = 140),
        label = "tilePad",
    )

    Row(
        modifier = modifier
            .xyGlass(shape = XyGlassTileShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = title }
            .padding(pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBox),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) ink else ink.copy(alpha = 0.40f),
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Wadah grid kartu yang **bukan** `LazyVerticalGrid`.
 *
 * `LazyVerticalGrid` di dalam `Column` yang bisa digulir melempar pengecualian
 * pada runtime, dan layar XyDesk menggulir seluruh kolomnya. Grid ini menyusun ubin
 * per baris dengan bobot sama, lalu mengisi baris terakhir yang tidak penuh
 * dengan `Spacer` berbobot supaya tepinya tetap rata — itulah bagian "rapi"
 * yang diminta pengguna.
 */
class XyTileGridScope internal constructor() {
    internal val items: MutableList<@Composable () -> Unit> = mutableListOf()

    /** Menambahkan satu ubin ke grid. */
    fun item(content: @Composable () -> Unit) {
        items.add(content)
    }
}

@Composable
fun XyTileGrid(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    spacing: Dp = 10.dp,
    content: @Composable XyTileGridScope.() -> Unit,
) {
    // Builder dipanggil langsung (bukan lewat `apply`) karena bertanda
    // @Composable: pemanggil boleh memakai fungsi composable seperti
    // sectionTitle() saat menyusun ubin.
    val scope = XyTileGridScope()
    scope.content()
    val tiles = scope.items
    val span = columns.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        tiles.chunked(span).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEach { tile ->
                    Box(modifier = Modifier.weight(1f)) { tile() }
                }
                // Pengisi baris terakhir: tanpa ini ubin sendirian melebar
                // sepenuh baris dan grid terlihat berantakan.
                repeat(span - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Baris daftar bergaya kaca: ikon + judul + keterangan + aksi di kanan.
 * Dipakai untuk daftar menu yang memang lebih cocok sebagai baris (misalnya
 * pilihan dengan status aktif), pendamping [XyGlassTile] yang berbentuk grid.
 */
@Composable
fun XyGlassRow(
    icon: ImageVector?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .xyGlass(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Judul seksi kecil di atas kartu/ubin, supaya hierarki dashboard jelas. */
@Composable
fun XyGlassSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
