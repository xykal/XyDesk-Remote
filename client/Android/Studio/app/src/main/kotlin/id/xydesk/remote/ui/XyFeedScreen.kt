package id.xydesk.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyGlassCard
import id.xydesk.remote.ui.components.XyGlassSectionLabel
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyTopBar

/**
 * Layar Feed — layar sendiri, bukan kartu yang menumpang di Beranda.
 *
 * Sebelumnya feed jokes komunitas (`FunHubCard`) hanya muncul sebagai satu
 * kartu di tengah daftar perangkat, jadi isinya sempit dan terpotong oleh
 * `INITIAL_JOKES_VISIBLE`. Sebagai tujuan nav sendiri, feed mendapat lebar
 * penuh dan bisa digulir tanpa berebut ruang dengan daftar perangkat.
 *
 * `FunHubCard` dipanggil dengan `onHide = null`: tombol "Sembunyikan" tidak
 * relevan di sini karena feed memang tujuan yang dipilih pengguna, bukan
 * kartu opsional. Tombol itu tetap ada di kartu Beranda.
 */
@Composable
internal fun XyFeedScreen(
    onMenu: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        XyTopBar(
            title = xy("Feed", "Feed"),
            actions = { XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = "Menu") },
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XyGlassSectionLabel(xy("Komunitas", "Community"))
            XyGlassCard(padding = 14.dp) {
                FunHubCard(onHide = null)
            }
        }
    }
}
