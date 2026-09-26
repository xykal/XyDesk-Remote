package id.xydesk.remote.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyGhostButton
import id.xydesk.remote.ui.components.XySectionTitle

/**
 * M3-UI — layar Pengaturan (tema + kebijakan sesi).
 */
@Composable
fun XySettingsScreen(
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    autoDisconnect: Boolean,
    onAutoDisconnect: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        XySectionTitle("Tampilan")
        XyCard {
            val options = listOf(
                0 to "Ikuti sistem",
                1 to "Gelap",
                2 to "Terang",
            )
            for ((value, label) in options) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = themeMode == value, onClick = { onThemeMode(value) })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        XySectionTitle("Kebijakan sesi")
        XyCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Putuskan otomatis di background", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Sesi ditutup 15 detik setelah app di-background. " +
                            "Matikan kalau HP dipakai kedua tangan sesekali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(0.dp))
                Switch(checked = autoDisconnect, onCheckedChange = onAutoDisconnect)
            }
        }
    }
}

/**
 * M3-UI — layar Keamanan: sertifikat yang pernah dipercaya.
 */
@Composable
fun XySecurityScreen(
    onRefresh: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { CertificateTrustStore(context) }
    var entries by remember { mutableStateOf(store.entries()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        XySectionTitle("Sertifikat dipercaya")
        if (entries.isEmpty()) {
            XyCard {
                Text(
                    "Belum ada sertifikat yang diingat. Sertifikat akan masuk ke " +
                        "sini kalau Anda menekan “Percaya & ingat” saat koneksi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            XyCard {
                entries.forEach { (hostport, fp) ->
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(hostport, style = MaterialTheme.typography.titleSmall)
                            Text(
                                fp,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            val parts = hostport.split(":")
                            store.remove(parts[0], parts[1].toIntOrNull() ?: 3389)
                            entries = store.entries()
                        }) { Text("Lupakan") }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(12.dp))
            XyGhostButton(
                text = "Lupakan semua",
                onClick = {
                    store.clear()
                    entries = store.entries()
                },
                modifier = Modifier.align(Alignment.End),
            )
        }
        Spacer(Modifier.height(24.dp))
        XySectionTitle("Penandatanganan")
        XyCard {
            Text(
                "APK rilis ditandatangani keystore resmi XyVerse (RSA-4096, " +
                    "berlaku 100 tahun, rotasi 26 Sep 2026). " +
                    "Fingerprint SHA-256:\n\n" +
                        "9E:8A:36:A8:F6:05:AD:44:ED:2F:27:8C:08:43:04:EE:" +
                        "A5:DF:82:91:FC:7E:C8:88:18:AF:43:46:B8:E9:76:F2",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * M3-UI — layar Tentang.
 */
@Composable
fun XyAboutScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (t: Throwable) {
            "?"
        }
    }
    val freeRdpVersion = remember {
        try {
            com.freerdp.freerdpcore.services.LibFreeRDP.getVersion()
        } catch (t: Throwable) {
            "?"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        XyCard {
            Text("XyDesk Remote", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "versi $versionName • FreeRDP $freeRdpVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Aplikasi desktop jarak jauh XyVerse. Dibangun dengan inti " +
                    "FreeRDP (GPL) dan Jetpack Compose.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
