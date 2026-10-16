package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton

/**
 * Penampil Log XyDesk yang terstruktur satu per satu:
 * tiap langkah diberi kartu tipis dan warna status (hijau = sukses/aktif,
 * merah = gagal/error, kuning = peringatan/timeout, biru = koneksi/sesi)
 * sehingga mudah dibaca 1 per 1 tanpa dinding teks mentah.
 */
@Composable
internal fun LogSheet(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    XyOverlay(title = title, onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            body.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                val lower = trimmed.lowercase()
                val isHeader = trimmed.startsWith("===")
                val color = when {
                    isHeader -> MaterialTheme.colorScheme.primary
                    lower.contains("error") || lower.contains("fail") ||
                        lower.contains("gagal") || lower.contains("terputus") ||
                        lower.contains("ditolak") ->
                        Color(0xFFE57373)
                    lower.contains("warn") || lower.contains("peringatan") ||
                        lower.contains("timeout") ->
                        Color(0xFFE8C468)
                    lower.contains("success") || lower.contains("sukses") ||
                        lower.contains("terhubung") || lower.contains("siap") ||
                        lower.contains("aktif") ->
                        Color(0xFF81C784)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                if (isHeader) {
                    Text(
                        text = trimmed,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = trimmed,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = color,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            XyPillButton(
                text = xy("Salin Log XyDesk", "Copy XyDesk Log"),
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("XyDesk Log", body))
                    Toast.makeText(
                        context,
                        xyNow("Log XyDesk disalin ke clipboard", "XyDesk Log copied to clipboard"),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                primary = false,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            XyPillButton(
                text = xy("Tutup", "Close"),
                onClick = onDismiss,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
