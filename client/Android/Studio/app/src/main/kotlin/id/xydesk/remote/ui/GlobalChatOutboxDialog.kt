package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class OutboxControlRow(val entry: OutboxEntry, val preview: OutboxTextPreview)
private data class OutboxPanelData(val snapshot: OutboxSnapshot, val rows: List<OutboxControlRow>)

/** Local own-account controls only; no media decoder, image export or new send nonce. */
@Composable
internal fun GlobalChatOutboxDialog(account: String, binding: String, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var data by remember(account, binding) { mutableStateOf<OutboxPanelData?>(null) }
    var error by remember(account, binding) { mutableStateOf<String?>(null) }
    var busy by remember(account, binding) { mutableStateOf(false) }
    var cancelId by remember(account, binding) { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(account, binding) {
        GlobalChatOutbox.revision.collectLatest {
            delay(100L) // Coalesce metadata bursts; no repeated full queue reads per UI event.
            val loaded = withContext(Dispatchers.IO) {
                val snapshot = GlobalChatOutbox.snapshot(context)
                val rows = snapshot.entries.filter { it.state == "pending" }.map { entry ->
                    OutboxControlRow(entry.copy(payload = ""), ChatOutboxControlPolicy.preview(entry))
                }
                // Never hold base64 media/ciphertext payloads in dialog state.
                OutboxPanelData(snapshot.copy(entries = emptyList()), rows)
            }
            if (loaded.snapshot.binding != binding || !chatAccountSnapshotIsCurrent(account, getVerifiedGitHubLogin(context))) {
                onClose(); return@collectLatest
            }
            data = loaded
            now = System.currentTimeMillis()
        }
    }
    val rows = data?.rows.orEmpty()
    val verifiedOwner = data?.snapshot?.verifiedOwner ?: 0
    val waiting = rows.any { ChatOutboxControlPolicy.retryState(it.entry, binding, verifiedOwner, now) == OutboxRetryState.WAIT }
    LaunchedEffect(waiting) {
        while (waiting && isActive) { delay(1_000); now = System.currentTimeMillis() }
    }
    fun act(action: suspend () -> String) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val message = action()
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                error = xyNow("Tindakan belum terkonfirmasi. Periksa akun, koneksi atau penyimpanan; data tidak direset.",
                    "Action not confirmed. Check account, connection or storage; data was not reset.")
            } finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(xyNow("Antrean kirim", "Send queue")) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("@$account", style = MaterialTheme.typography.labelMedium)
                Text(xyNow("Coba ulang memakai ID dan isi yang sama. Pesan baru dianggap terkirim setelah receipt server valid.",
                    "Retries keep the same ID and content. A message is sent only after a valid server receipt."),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy && binding.isNotBlank(), onClick = {
                    act {
                        withContext(Dispatchers.IO) { GlobalChatOutbox.checkAccount(context, account, binding) }
                        xyNow("Identitas akun terverifikasi; antrean tidak otomatis dipindah ke akun lain.",
                            "Account identity verified; queues are not migrated to another account.")
                    }
                }) { Text(xyNow("Periksa identitas akun", "Verify account identity")) }
                if (data == null || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (data?.snapshot?.readFailed == true) {
                    Text(xyNow("Antrean tidak dapat dibuka. Data lama tetap disimpan; jangan hapus data aplikasi.",
                        "Queue could not be opened. Existing data is retained; do not clear app data."), color = MaterialTheme.colorScheme.error)
                } else if (data != null && rows.isEmpty()) {
                    Text(xyNow("Tidak ada pesan tertunda yang dapat diakses dengan identitas saat ini. Antrean akun/token lain tidak ditampilkan.",
                        "No pending messages accessible to this identity. Other account/token queues are not shown."))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.entry.id }) { row ->
                        val entry = row.entry
                        val state = ChatOutboxControlPolicy.retryState(entry, binding, verifiedOwner, now)
                        val waitUntil = maxOf(entry.retryNotBefore, if (entry.nextAttemptAt == Long.MAX_VALUE) 0 else entry.nextAttemptAt)
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (row.preview.viewOnce) xyNow("Media sekali lihat · tidak diekspor", "View-once media · not exported")
                                    else row.preview.text.ifBlank { xyNow("Media tertunda", "Pending media") },
                                    maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                if (row.preview.media.isNotBlank()) Text(row.preview.media, style = MaterialTheme.typography.labelSmall)
                                Text(when (state) {
                                    OutboxRetryState.READY -> xyNow("Menunggu konfirmasi / siap dicoba ulang", "Awaiting confirmation / ready to retry")
                                    OutboxRetryState.WAIT -> xyNow("Tunggu ${((waitUntil - now).coerceAtLeast(0) / 1000) + 1} dtk; backoff server tetap dihormati", "Wait ${((waitUntil - now).coerceAtLeast(0) / 1000) + 1}s; server backoff is respected")
                                    OutboxRetryState.NEEDS_LOGIN -> xyNow("Periksa login terlebih dahulu", "Verify sign-in first")
                                    OutboxRetryState.EXPIRED -> xyNow("Batas retry 7 hari terlewati. Salin teks untuk kiriman baru secara manual, atau batalkan.", "Seven-day retry limit reached. Copy text for a manual new message, or cancel.")
                                    OutboxRetryState.CLOCK -> xyNow("Waktu perangkat perlu diperiksa; timestamp pesan tidak diubah otomatis.", "Check device time; message timestamp is not automatically changed.")
                                    else -> xyNow("Status menahan retry. Tidak dibuat ID baru otomatis; salin teks atau batalkan.", "Retry is blocked. A new ID is never created automatically; copy text or cancel.")
                                }, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(enabled = !busy && state in setOf(OutboxRetryState.READY, OutboxRetryState.NEEDS_LOGIN), onClick = {
                                        act {
                                            val result = withContext(Dispatchers.IO) { GlobalChatOutbox.retryPending(context, entry.id, account, binding) }
                                            if (result == "pending") xyNow("Retry dijadwalkan; belum berarti terkirim.", "Retry scheduled; this is not a sent receipt.")
                                            else xyNow("Status pesan sudah berubah; tampilan akan diperbarui.", "Message state changed; the view will refresh.")
                                        }
                                    }) { Text(xyNow("Coba lagi", "Retry")) }
                                    TextButton(enabled = !busy && !row.preview.viewOnce && row.preview.text.isNotBlank(), onClick = {
                                        act {
                                            val text = withContext(Dispatchers.IO) { GlobalChatOutbox.pendingText(context, entry.id, account, binding) }
                                            val clip = ClipData.newPlainText("XyDesk pending message", text)
                                            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                            }
                                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                            xyNow("Teks disalin. Mengirimnya dari composer adalah kiriman baru yang kamu pilih sendiri.", "Text copied. Sending from the composer is an explicit new message.")
                                        }
                                    }) { Text(xyNow("Salin teks", "Copy text")) }
                                }
                                TextButton(enabled = !busy, onClick = { cancelId = entry.id }) { Text(xyNow("Batalkan", "Cancel")) }
                            }
                        }
                    }
                }
                Text(xyNow("Pembatalan menghentikan percobaan berikutnya, bukan menarik pesan yang mungkin sudah diterima server.",
                    "Cancellation stops future attempts; it cannot recall a message the server may already have received."), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(xyNow("Tutup", "Close")) } },
    )
    cancelId?.let { id ->
        AlertDialog(onDismissRequest = { cancelId = null }, title = { Text(xyNow("Batalkan antrean?", "Cancel queued message?")) },
            text = { Text(xyNow("Payload lokal pesan ini akan dilepas setelah pembatalan tersimpan. Kiriman yang sudah masuk server tidak ditarik.",
                "This local payload is released after durable cancellation. Already received server messages are not recalled.")) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                cancelId = null
                act {
                    val result = withContext(Dispatchers.IO) { GlobalChatOutbox.cancelPending(context, id, account, binding) }
                    if (result?.state == "cancelled") xyNow("Percobaan kirim berikutnya dibatalkan.", "Future send attempts cancelled.")
                    else xyNow("Status antrean sudah berubah; tidak ada pesan server yang dihapus.", "Queue state changed; no server message was deleted.")
                }
            }) { Text(xyNow("Ya, batalkan", "Yes, cancel")) } },
            dismissButton = { TextButton(onClick = { cancelId = null }) { Text(xyNow("Kembali", "Back")) } })
    }
}
