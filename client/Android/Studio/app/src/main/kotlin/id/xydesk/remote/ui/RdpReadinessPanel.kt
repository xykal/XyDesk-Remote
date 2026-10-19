package id.xydesk.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.xydesk.remote.core.AudioBridgeDiagnostics
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton

/** Local evidence plus explicitly confirmed native mic reconnect; no Windows setup or invented checks. */
@Composable
internal fun RdpReadinessPanel(
    diagnostics: AudioBridgeDiagnostics,
    nowMs: Long,
    micPermission: Boolean,
    nativeMicConfigured: Boolean,
    connectionId: Long,
    nativeMicRequested: Boolean,
    nativeMicChangePending: Boolean,
    canChangeNativeMic: Boolean,
    onChangeNativeMic: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val host = diagnostics.host
    val fresh = host?.isFresh(nowMs) == true
    var confirmMicChange by remember(connectionId, nativeMicRequested, canChangeNativeMic) { mutableStateOf(false) }
    XyOverlay(title = "Kesiapan RDP · Mic & Game", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadinessLine("Izin mic Android", if (micPermission) "Diberikan" else "Belum diberikan")
            ReadinessLine("Mic RDP pada sambungan", when {
                nativeMicChangePending -> "Perubahan diminta; menunggu native berhenti. Belum boleh dianggap OFF."
                nativeMicRequested -> "AUDIN diminta pada URI sambungan; bukan bukti hardware/game menerima suara."
                else -> "AUDIN tidak diminta pada URI sambungan ini; bukan pengukuran perangkat Windows."
            })
            if (confirmMicChange && canChangeNativeMic) {
                Text("Perubahan ini akan memutus dan menyambungkan ulang desktop. Native lama harus selesai dilepas dahulu. Mic bridge dihentikan untuk menghindari rekaman ganda. Profil tersimpan tidak berubah; ini BUKAN live mute.")
                XyPillButton(text = "Konfirmasi sambung ulang", onClick = {
                    confirmMicChange = false
                    onChangeNativeMic(!nativeMicRequested)
                }, modifier = Modifier.fillMaxWidth())
                XyPillButton(text = "Batal", onClick = { confirmMicChange = false }, primary = false)
            } else {
                XyPillButton(
                    text = if (nativeMicRequested) "Nonaktifkan mic RDP (sambung ulang)" else "Aktifkan mic RDP (sambung ulang)",
                    onClick = { confirmMicChange = true },
                    enabled = canChangeNativeMic && !nativeMicChangePending,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ReadinessLine("Privasi capture saat aplikasi di latar", "Koneksi yang meminta mic/kamera native diminta putus ketika layar sesi tidak tampil. Reconnect background tidak meminta mic/kamera. Ini bukan live mute: desktop bisa ikut terputus, dan berhentinya capture tetap menunggu konfirmasi engine. Sambungan baru di foreground dapat meminta capture sesuai profil dan izin.")
            ReadinessLine("Mic bridge sesi", if (diagnostics.requested) "Diminta pengguna" else "Tidak diminta")
            ReadinessLine("Rekaman mic HP (bridge)", if (diagnostics.capturing) "AudioRecord sedang merekam" else "Tidak merekam / belum mulai")
            ReadinessLine("Laporan host UDP", when {
                host == null -> "Belum ada laporan. Host mungkin belum terpasang atau bridge belum berjalan."
                !fresh -> "Laporan lama; jangan dianggap sebagai status saat ini."
                else -> "Ada laporan baru; bukan identitas host yang terautentikasi."
            })
            if (fresh && host != null) {
                ReadinessLine("Routing menurut host", buildString {
                    append(if (host.flags and 2 != 0) "Render mic dilaporkan aktif" else "Render mic dilaporkan idle")
                    append(if (host.flags and 4 != 0) " · target virtual" else " · target default/belum virtual")
                    append(" · level ${host.micLevel}/255 · frame mic ${host.micFrames}")
                })
                if (host.detail.isNotBlank()) ReadinessLine("Endpoint menurut host", host.detail)
            }
            ReadinessLine("Perangkat input Windows & pilihan mic game", "Belum diverifikasi. Target render virtual tidak membuktikan perangkat capture/input tersedia atau dipilih game.")
            ReadinessLine("Kanal mic RDP native (setelan profil)", if (nativeMicConfigured)
                "Profil meminta AUDIN bawaan RDP; tidak memerlukan Host Agent UDP. Izin Android diperiksa pada setiap sambungan. Pilih input Remote Audio di Windows/aplikasi lalu uji level suara. Tombol Mic bridge TIDAK mengendalikan kanal ini. Kontrol RDP di atas memakai sambung ulang, bukan live mute."
                else "Tidak dikonfigurasi pada profil. Negosiasi/perangkat Windows belum diperiksa.")
            ReadinessLine("Driver / GPU / encoder Windows", "Belum dipindai. Tidak ada driver, registry, firewall atau skrip yang diubah oleh panel ini.")
            ReadinessLine("Roblox menolak VM", "Gunakan lingkungan yang didukung game. PC Stream tidak mengubah VM menjadi PC fisik; tidak ada bypass proteksi.")
            ReadinessLine("SA-MP berhenti setelah Connect", "Belum terdiagnosis. Cek apakah game berjalan langsung di PC, lalu periksa path GTA, versi launcher, proses/exit code dan log crash. Daftar server berhasil bukan bukti game berhasil diluncurkan.")
            Text("Status diperbarui selama sesi tampil. Inventaris ini bukan setup Windows otomatis atau jaminan game; kontrol mic hanya mengubah permintaan kanal melalui sambung ulang.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            XyPillButton(text = "Tutup", onClick = onDismiss, primary = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReadinessLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
