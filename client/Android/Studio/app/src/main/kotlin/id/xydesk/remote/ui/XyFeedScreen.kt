package id.xydesk.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyGlassCard
import id.xydesk.remote.ui.components.XyGlassSectionLabel
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyTopBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight

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
    onOpenChat: () -> Unit = {},
) {
    // Pull-to-refresh (permintaan pemilik 2026-10-13): tarik layar feed ke
    // bawah untuk memuat ulang, seperti aplikasi sosial pada umumnya.
    var pullSignal by remember { mutableIntStateOf(0) }
    var pullBusy by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = pullBusy,
        onRefresh = { pullSignal += 1 },
        modifier = Modifier.fillMaxSize(),
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XyGlassSectionLabel(xy("Komunitas", "Community"))
            // Ronde 45: kartu sambutan supaya layar Feed punya konteks —
            // sebelumnya langsung menabrak daftar postingan tanpa pembuka.
            XyGlassCard(padding = 14.dp) {
                Text(
                    xy("Unggahan komunitas", "Community posts"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    xy(
                        "Bagikan tips, cerita, atau screenshot sesi terbaikmu. Tarik ke bawah untuk menyegarkan.",
                        "Share tips, stories, or your best session screenshots. Pull down to refresh.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            XyGlassCard(padding = 14.dp) {
                // Ronde 47 — pintu masuk Chat Global realtime.
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat() },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chat Global", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Sapa sesama pengguna XyDesk secara realtime — riwayat, indikator mengetik, tanpa perlu akun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Buka ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))

                FunHubCard(
                    onHide = null,
                    refreshSignal = pullSignal,
                    onBusyChange = { pullBusy = it },
                )
            }
        }
    }
    }
}
