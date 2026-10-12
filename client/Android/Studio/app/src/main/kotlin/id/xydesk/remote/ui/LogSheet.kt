package id.xydesk.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyOverlay

/**
 * Penampil log yang terbaca: tiap baris diwarnai menurut tingkatnya
 * (merah = error/gagal, kuning = peringatan, hijau = sukses, ungu = judul
 * seksi) alih-alih dinding teks polos (laporan pemilik 2026-10-12:
 * "log masih kurang jelas").
 */
@Composable
internal fun LogSheet(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    XyOverlay(title = title, onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            body.lineSequence().forEach { line ->
                val lower = line.lowercase()
                val isHeader = line.startsWith("===")
                val color = when {
                    isHeader -> MaterialTheme.colorScheme.primary
                    lower.contains("error") || lower.contains("fail") ||
                        lower.contains("gagal") || lower.contains("disconnect") ->
                        Color(0xFFE57373)
                    lower.contains("warn") || lower.contains("timeout") ->
                        Color(0xFFE8C468)
                    lower.contains("success") || lower.contains("sukses") ||
                        lower.contains("connected") || lower.contains(" ok") ->
                        Color(0xFF81C784)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = line.ifEmpty { " " },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    color = color,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
