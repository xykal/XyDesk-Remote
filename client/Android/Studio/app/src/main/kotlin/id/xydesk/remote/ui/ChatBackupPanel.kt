package id.xydesk.remote.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SAF destinations are chosen by the owner. No broad storage permission or auto upload. */
@Composable
internal fun ChatBackupPanel(login: String?, snapshot: () -> List<ChatMessage>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<ByteArray?>(null) }
    var importPassword by remember { mutableStateOf<CharArray?>(null) }
    var archive by remember { mutableStateOf<List<ChatMessage>?>(null) }
    fun notify(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    DisposableEffect(Unit) {
        onDispose { pendingFile?.fill(0); importPassword?.fill('\u0000') }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingFile
        pendingFile = null
        if (uri == null || bytes == null) { bytes?.fill(0); busy = false }
        else scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                        ?: error("Tujuan file tidak dapat dibuka.")
                }
                notify("Backup teks terenkripsi tersimpan. Simpan passphrase terpisah dari file.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notify("Backup gagal disimpan. Hapus file tidak lengkap bila ada, lalu coba lagi.")
            } finally { bytes.fill(0); busy = false }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = importPassword
        importPassword = null
        if (uri == null || password == null || login.isNullOrBlank()) { password?.fill('\u0000'); busy = false }
        else scope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(ChatBackupCrypto.MAX_FILE + 1)
                        var count = 0
                        while (count < buffer.size) {
                            val n = input.read(buffer, count, buffer.size - count)
                            if (n < 0) break
                            if (n == 0) continue
                            count += n
                        }
                        require(count <= ChatBackupCrypto.MAX_FILE)
                        buffer.copyOf(count).also { buffer.fill(0) }
                    } ?: error("File tidak dapat dibuka.")
                    val plain = try { ChatBackupCrypto.decrypt(bytes, password, login) } finally { bytes.fill(0) }
                    val rows = try { decodeChatArchive(plain) } finally { plain.fill(0) }
                    GlobalChatOfflineCache.storeArchive(context.applicationContext, login, rows)
                    GlobalChatOfflineCache.readArchive(context.applicationContext, login)
                }
                archive = restored
                notify("Arsip dipulihkan sebagai baca-saja; chat live tidak diubah.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notify("Restore gagal: periksa passphrase, akun GitHub asal, file dan ruang penyimpanan.")
            } finally { password.fill('\u0000'); busy = false }
        }
    }
    Text("Backup portabel • arsip teks")
    Text("Maksimal 80 pesan terkonfirmasi. Tanpa media, pesan sekali lihat, token atau antrean kirim. Restore tidak mengunggah/menghidupkan kembali pesan ke chat live. Backup lama tetap bisa memuat teks sebelum dihapus; hapus file lama jika perlu. Passphrase tidak dapat dipulihkan.")
    XyField(value = passphrase, onValueChange = { passphrase = it }, label = "Passphrase backup (minimal 12 karakter)", isPassword = true)
    XyPillButton(text = if (busy) "Memproses…" else "Ekspor terenkripsi", enabled = !busy && !login.isNullOrBlank() && passphrase.length >= 12,
        onClick = {
            val account = login ?: return@XyPillButton
            val password = passphrase.toCharArray()
            val rows = snapshot()
            passphrase = ""
            busy = true
            scope.launch {
                try {
                    pendingFile = withContext(Dispatchers.IO) {
                        val plain = encodeChatArchive(rows)
                        try { ChatBackupCrypto.encrypt(plain, password, account) } finally { plain.fill(0) }
                    }
                    save.launch("XyDesk-chat.xychat")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    pendingFile?.fill(0); pendingFile = null; busy = false
                    notify("Ekspor gagal. Periksa ukuran arsip dan coba lagi.")
                } finally { password.fill('\u0000') }
            }
        })
    XyPillButton(text = "Impor sebagai arsip baca-saja", enabled = !busy && !login.isNullOrBlank() && passphrase.length >= 12,
        onClick = {
            importPassword = passphrase.toCharArray(); passphrase = ""; busy = true
            try { restore.launch(arrayOf("*/*")) }
            catch (e: Exception) { importPassword?.fill('\u0000'); importPassword = null; busy = false; notify("Pemilih file tidak tersedia.") }
        })
    XyPillButton(text = "Buka arsip tersimpan", enabled = !busy && !login.isNullOrBlank(), onClick = {
        val account = login ?: return@XyPillButton
        busy = true
        scope.launch {
            try { archive = withContext(Dispatchers.IO) { GlobalChatOfflineCache.readArchive(context.applicationContext, account) } }
            finally { busy = false }
        }
    })
    archive?.let { rows ->
        XyOverlay(title = "Arsip lokal • baca-saja (${rows.size})", onDismiss = { archive = null }) {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rows.isEmpty()) Text("Belum ada arsip. Impor file backup terlebih dahulu.")
                rows.forEach { row -> Text("${row.name.take(100)}: ${if (row.deleted) "Pesan ini dihapus" else row.text.take(4000)}") }
                XyPillButton(text = "Tutup", onClick = { archive = null })
            }
        }
    }
}
