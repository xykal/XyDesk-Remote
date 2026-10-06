package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyIcons

/**
 * Navigasi bawah app, gaya monokrom (hitam-putih) sesuai permintaan pengguna.
 *
 * Sengaja memakai `onSurface`/`surface` alih-alih warna aksen supaya tetap
 * hitam-putih di tema terang maupun gelap: item terpilih digambar solid,
 * sisanya redup. Semua seksi drawer ada di sini supaya tidak ada tujuan yang
 * hilang ketika drawer ditutup.
 */
@Composable
internal fun XyBottomNav(
    selected: XySection,
    onSelect: (XySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val barColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(barColor)
            // Garis rambut di atas; pengganti elevation agar tetap datar/hitam-putih.
            .padding(top = 1.dp)
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XySection.entries.forEach { section ->
            val active = section == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onSelect(section) }
                    .semantics { contentDescription = sectionTitle(section) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) ink else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        bottomNavIcon(section),
                        contentDescription = null,
                        tint = if (active) barColor else ink.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = sectionTitle(section),
                    color = if (active) ink else ink.copy(alpha = 0.45f),
                    fontSize = 9.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Ikon per seksi. Dipetakan sama persis dengan daftar di drawer supaya satu
 * seksi tidak punya dua ikon berbeda.
 */
private fun bottomNavIcon(section: XySection): ImageVector = when (section) {
    XySection.PERANGKAT -> XyIcons.Monitor
    XySection.TAMPILAN -> XyIcons.Fit
    XySection.KREDENSIAL -> XyIcons.Lock
    XySection.UMUM -> XyIcons.Sliders
    XySection.KEAMANAN -> XyIcons.Shield
    XySection.TENTANG -> XyIcons.Info
}

/** Garis pemisah tipis di atas bar, digambar tanpa elevation. */
@Composable
internal fun XyBottomNavHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}
