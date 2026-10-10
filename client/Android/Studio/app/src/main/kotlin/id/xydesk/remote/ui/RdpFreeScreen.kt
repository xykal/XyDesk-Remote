package id.xydesk.remote.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.rdpfree.RdpFreeApi
import id.xydesk.remote.rdpfree.RdpFreeStatus
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Native API screen, not a WebView. Browser is used only to issue a scoped device token. */
@Composable
fun RdpFreeScreen(onBack: () -> Unit, onConnect: (ConnectionProfile) -> Unit) {
    val context = LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val vault = remember { CredentialVault(context.applicationContext) }
    val api = remember { RdpFreeApi { vault.get(RdpFreeApi.VAULT_KEY) } }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(vault.has(RdpFreeApi.VAULT_KEY)) }
    var tokenInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<RdpFreeStatus?>(null) }
    var inputs by remember { mutableStateOf<JSONObject?>(null) }
    var durations by remember { mutableStateOf<List<String>>(emptyList()) }
    var duration by remember { mutableStateOf("360") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var chooseDuration by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var stopRunId by remember { mutableStateOf<Long?>(null) }

    suspend fun load() {
        status = api.status()
        if (inputs == null) {
            val config = api.settings()
            inputs = config.getJSONObject("inputs")
            duration = inputs?.optString("durasi_menit", "360") ?: "360"
            val options = config.getJSONObject("choices").getJSONArray("durasi_menit")
            durations = (0 until options.length()).map { options.getString(it) }
        }
    }

    fun error(e: Exception) {
        if (e is CancellationException) throw e
        message = e.message ?: "Koneksi RdpFree gagal. Coba lagi."
        status = null // Never connect using a stale address after an API error.
        if (e is RdpFreeApi.ApiException && e.status == 401) connected = false
    }

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        while (true) {
            var wait = 15000L
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !busy) {
                try { load() } catch (e: Exception) { error(e); wait = 30000L }
            }
            delay(wait)
        }
    }

    fun operate(action: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                when (action) {
                    "start" -> { api.start(); message = "Workflow dikirim. Tunggu status berikutnya." }
                    "stop" -> { api.stop(stopRunId ?: throw IllegalStateException("Run tidak tersedia")); message = "Permintaan stop dikirim." }
                    "revoke" -> {
                        api.revoke()
                        vault.remove(RdpFreeApi.VAULT_KEY)
                        connected = false
                        status = null
                        inputs = null
                        password = ""
                        message = "Akses perangkat dicabut."
                    }
                }
                if (action != "revoke") load()
            } catch (e: Exception) { error(e) } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        XyTopBar(title = "RdpFree", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("XyRDP · kontrol native", style = MaterialTheme.typography.titleLarge)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            if (!connected) {
                XyCard(Modifier.fillMaxWidth()) {
                    Text("Hubungkan perangkat", style = MaterialTheme.typography.titleMedium)
                    Text("Buat token dari menu XyDesk di website. Jangan masukkan password admin atau token GitHub.")
                    Spacer(Modifier.height(12.dp))
                    XyPillButton("Buka halaman token", {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RdpFreeApi.DEVICES_URL))) }
                            .onFailure { message = "Browser tidak tersedia" }
                    }, primary = false)
                    Spacer(Modifier.height(12.dp))
                    XyField(tokenInput, { tokenInput = it.take(100) }, "Token perangkat", isPassword = true)
                    Spacer(Modifier.height(12.dp))
                    XyPillButton("Hubungkan", {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val candidate = tokenInput.trim()
                                    RdpFreeApi { candidate }.settings() // Validate before replacing saved token.
                                    val saved = withContext(Dispatchers.IO) { vault.put(RdpFreeApi.VAULT_KEY, candidate) }
                                    check(saved) { "Android Keystore tidak dapat menyimpan token" }
                                    tokenInput = ""
                                    inputs = null
                                    connected = true
                                    message = "Perangkat terhubung."
                                } catch (e: Exception) { error(e) } finally { busy = false }
                            }
                        }
                    }, enabled = !busy && RdpFreeApi.TOKEN_PATTERN.matches(tokenInput.trim()))
                }
            } else {
                XyCard(Modifier.fillMaxWidth()) {
                    Text(status?.repo ?: "Memuat repository…", style = MaterialTheme.typography.titleMedium)
                    Text("Status: ${status?.phase ?: "menunggu"}")
                    status?.expiresAt?.let { Text("Berakhir (UTC): $it") }
                    Spacer(Modifier.height(12.dp))
                    XyPillButton("Durasi: $duration menit", { chooseDuration = true }, primary = false,
                        enabled = !busy && durations.isNotEmpty())
                    Spacer(Modifier.height(8.dp))
                    XyPillButton("Simpan durasi", {
                        busy = true
                        scope.launch {
                            try {
                                val updated = JSONObject(api.settings().getJSONObject("inputs").toString())
                                updated.put("durasi_menit", duration)
                                api.saveSettings(updated)
                                inputs = updated
                                message = "Durasi tersimpan untuk run berikutnya."
                            } catch (e: Exception) { error(e) } finally { busy = false }
                        }
                    }, primary = false, enabled = !busy && inputs != null)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton("Run", { confirmAction = "start" }, enabled = !busy && status != null && status?.active == false)
                        XyPillButton("Stop", { stopRunId = status?.runId; confirmAction = "stop" }, primary = false,
                            enabled = !busy && status?.active == true && status?.runId != null)
                    }
                    Spacer(Modifier.height(8.dp))
                    XyPillButton("Refresh", {
                        busy = true
                        scope.launch { try { load() } catch (e: Exception) { error(e) } finally { busy = false } }
                    }, primary = false, enabled = !busy)
                }
                status?.connection?.let { connection ->
                    XyCard(Modifier.fillMaxWidth()) {
                        Text("${connection.host}:${connection.port}", style = MaterialTheme.typography.titleMedium)
                        Text("${connection.username} · ${connection.transport}")
                        if (connection.transport == "tailscale") Text("Pastikan Tailscale di HP terhubung ke jaringan yang sama.")
                        Spacer(Modifier.height(12.dp))
                        XyField(password, { password = it }, "Password RDP_PASSWORD", isPassword = true)
                        Text("Password tidak dibaca dari GitHub dan tidak disimpan otomatis.")
                        Spacer(Modifier.height(12.dp))
                        XyPillButton("Hubungkan RDP", {
                            busy = true
                            scope.launch {
                                try {
                                    val fresh = api.status().connection ?: throw IllegalStateException("Sesi tidak lagi siap")
                                    val profile = ConnectionProfile(host = fresh.host, port = fresh.port,
                                        username = fresh.username, password = password, label = "RdpFree")
                                    password = ""
                                    onConnect(profile)
                                } catch (e: Exception) { error(e) } finally { busy = false }
                            }
                        }, enabled = !busy && password.isNotBlank())
                    }
                }
                XyPillButton("Cabut akses perangkat", { confirmAction = "revoke" }, primary = false, enabled = !busy)
            }
            Text("Credit: KallAncrit", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (chooseDuration) {
        XyOverlay(title = "Durasi sesi", onDismiss = { chooseDuration = false }) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                durations.forEach { minutes ->
                    XyPillButton("$minutes menit", { duration = minutes; chooseDuration = false },
                        primary = duration == minutes, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    confirmAction?.let { action ->
        XyDialog(
            title = when (action) { "start" -> "Mulai RDP?"; "stop" -> "Hentikan RDP?"; else -> "Cabut token perangkat?" },
            body = when (action) {
                "start" -> "Menggunakan pengaturan tersimpan dan kuota GitHub Actions. Jangan kirim ulang jika koneksi terputus; periksa status dahulu."
                "stop" -> "Sesi aktif akan dihentikan. Simpan pekerjaan terlebih dahulu."
                else -> "Aplikasi ini tidak bisa mengontrol repository sampai token baru dipasang."
            },
            confirmLabel = "Lanjutkan", onConfirm = { confirmAction = null; operate(action) },
            dismissLabel = "Batal", onDismiss = { confirmAction = null },
        )
    }
}
