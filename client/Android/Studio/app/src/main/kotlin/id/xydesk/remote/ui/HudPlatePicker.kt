package id.xydesk.remote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyIcons

/**
 * Latar contoh untuk pratinjau gaya tombol.
 *
 * Tombol kontrol selalu digambar di atas **gambar sesi**, bukan di atas
 * permukaan app, jadi pratinjau yang memakai warna permukaan app akan
 * menyesatkan: pelat "Transparan" akan tampak seperti pelat gelap. Karena itu
 * pratinjau ini memakai gradien mirip wallpaper desktop supaya perbedaan
 * ketiga pelat langsung terlihat.
 */
private val HudPreviewBackdrop = Brush.linearGradient(
    0f to Color(0xFF3E4C63),
    0.5f to Color(0xFF6E7C8C),
    1f to Color(0xFF2B3242),
)

/**
 * Pemilih gaya tombol kontrol dengan **pratinjau gaya aslinya**.
 *
 * Sebelumnya pilihan ini berupa `XySegmented` berisi teks ("Gelap tegas",
 * "Gelap lembut", "Transparan") sehingga pengguna harus menebak hasilnya lalu
 * mencoba satu per satu di atas layar remote. Sekarang tiap pilihan menggambar
 * tombol sungguhan memakai [hudPalette] — fungsi yang sama yang dipakai lapisan
 * tombol saat sesi berjalan — jadi yang terlihat di pemilih adalah yang akan
 * muncul di layar.
 */
@Composable
internal fun HudPlatePicker(
    selected: HudPlate,
    onSelect: (HudPlate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HudPlate.entries.forEach { plate ->
            val active = plate == selected
            val palette = hudPalette(plate)
            val borderColor by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
                animationSpec = tween(durationMillis = 140),
                label = "plateBorder",
            )
            val title = xy(plate.title, plate.titleEn)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = { onSelect(plate) })
                    .semantics { contentDescription = title },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HudPreviewBackdrop)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Tombol contoh: bentuk, tepi, dan warna sama persis dengan
                    // yang digambar SessionKeyLayer saat sesi berjalan.
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(palette.plate)
                            .border(1.dp, palette.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            XyIcons.ClickLeft,
                            contentDescription = null,
                            tint = palette.ink,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    title,
                    modifier = Modifier.padding(bottom = 2.dp),
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 10.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
