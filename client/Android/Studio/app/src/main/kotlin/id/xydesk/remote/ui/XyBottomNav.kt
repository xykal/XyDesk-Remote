package id.xydesk.remote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.xyGlass

/**
 * Tujuan navigasi bawah.
 *
 * Empat tujuan; Chat Global kini punya pintu masuk langsung sesuai permintaan pengguna. Sebelumnya bar ini memuat
 * enam seksi (`XySection`) sehingga penuh dan tiap item jadi sempit; seksi
 * lain sekarang hidup di drawer kiri dan di layar Profil.
 */
internal enum class XyTab(val title: String, val titleEn: String) {
    HOME("Beranda", "Home"),
    FEED("Berita", "News"),
    PROFILE("Profil", "Profile"),
}

/**
 * Dock bawah melayang — gaya macOS dock / iOS tab bar modern.
 *
 * Perubahan dari bar lama (menempel penuh di dasar layar):
 *  - kapsul melayang dengan margin samping + bawah dan bayangan lembut,
 *    tidak lagi menutupi tepi layar;
 *  - menghormati gesture bar sistem ([navigationBarsPadding]);
 *  - item aktif menjadi pil berisi ikon + label (label menyusut masuk/keluar),
 *    item non-aktif hanya ikon — seperti tab bar iPadOS;
 *  - ikon aktif membesar sedikit dengan pegas memantul (magnify ala dock macOS).
 *
 * Warna tetap hitam-putih monokrom (`onSurface`/`surface`) sesuai arah desain
 * app; pil aktif memakai tinta gelap dengan isi terang di tema gelap dan
 * sebaliknya di tema terang.
 */
@Composable
internal fun XyBottomNav(
    selected: XyTab,
    onSelect: (XyTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.22f),
                )
                .xyGlass(shape = RoundedCornerShape(50), opacity = 0.94f)
                // Liquid glass ungu (2026-10-24): gradasi violet tembus
                // pandang; tanpa blur backdrop asli supaya tetap ringan.
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(
                            Color(0xD92A1650),
                            Color(0xD94C1D95),
                            Color(0xD92A1650),
                        ),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(XyTab.HOME, XyTab.FEED, XyTab.PROFILE).forEach { tab ->
                val active = tab == selected
                val title = xy(tab.title, tab.titleEn)
                val pillColor by animateColorAsState(
                    targetValue = if (active) Color(0xFF7C3AED) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "navPillColor",
                )
                val iconTint by animateColorAsState(
                    targetValue = if (active) Color(0xFFFFFFFF) else ink.copy(alpha = 0.55f),
                    animationSpec = tween(durationMillis = 200),
                    label = "navIconTint",
                )
                val labelColor by animateColorAsState(
                    targetValue = if (active) Color(0xFFFFFFFF) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "navLabelColor",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (active) 1.12f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "navIconScale",
                )
                val pillHPadding by animateDpAsState(
                    targetValue = if (active) 14.dp else 10.dp,
                    animationSpec = tween(durationMillis = 200),
                    label = "navPillPad",
                )

                Box(
                    modifier = Modifier
                        .weight(if (active) 1.38f else 0.88f)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = { onSelect(tab) })
                        .semantics { contentDescription = title }
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillColor)
                            .padding(horizontal = pillHPadding, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box {
                            Icon(
                                tabIcon(tab),
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                        }
                        AnimatedVisibility(
                            visible = active,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Text(
                                text = title,
                                color = labelColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ikon per tujuan. Ikon unik untuk setiap tujuan. */
private fun tabIcon(tab: XyTab): ImageVector = when (tab) {
    XyTab.HOME -> XyIcons.Monitor
    XyTab.FEED -> XyIcons.Globe
    XyTab.PROFILE -> XyIcons.Gear
}
