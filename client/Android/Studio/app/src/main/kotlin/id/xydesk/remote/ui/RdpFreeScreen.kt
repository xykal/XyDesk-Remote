package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.xydesk.remote.R
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.RdpOptions
import id.xydesk.remote.core.forRdpFree
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyDialog
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySectionLabel
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.rdp.XyRdpAccess
import id.xydesk.remote.ui.rdp.XyRdpClient
import id.xydesk.remote.ui.rdp.XyRdpConfig
import id.xydesk.remote.ui.rdp.XyRdpRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Layar "Buat RDP" — RDP gratis dari GitHub Actions milik akun sendiri.
 *
 * Ronde 46: sepenuhnya NATIVE via GitHub API (fork template XyRDP → dispatch
 * workflow → baca status dari branch `status`) — TANPA web perantara, tanpa
 * device token server. Login pakai GitHub device flow (OAuth), PAT disimpan
 * terenkripsi di CredentialVault. Statistik live (RAM/disk/CPU/uptime)
 * diterbitkan workflow tiap 60 detik ke status JSON.
 */
@Composable
fun RdpFreeScreen(onBack: () -> Unit, onConnect: (ConnectionProfile) -> Unit) {
    val context = LocalContext.current
    val vault = remember { CredentialVault(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("xyrdp_settings", Context.MODE_PRIVATE) }

    var token by remember { mutableStateOf<String?>(null) }
    var login by remember { mutableStateOf<String?>(null) }
    var loginDialog by remember { mutableStateOf(false) }
    var forkReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var duration by remember { mutableStateOf(prefs.getString("durasi_menit", "360") ?: "360") }
    var hostname by remember { mutableStateOf(prefs.getString("hostname", "rdpfree") ?: "rdpfree") }
    var rdpUser by remember { mutableStateOf(prefs.getString("rdp_user", "xyadmin") ?: "xyadmin") }
    var akses by remember { mutableStateOf(prefs.getString("akses", "tailscale") ?: "tailscale") }
    var storageBoost by remember { mutableStateOf(prefs.getString("storage_boost", "tidak") ?: "tidak") }
    var debloat by remember { mutableStateOf(prefs.getString("debloat", "ringan") ?: "ringan") }
    var microphone by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    var run by remember { mutableStateOf<XyRdpRun?>(null) }
    var access by remember { mutableStateOf<XyRdpAccess?>(null) }
    var chooseDuration by remember { mutableStateOf(false) }
    var chooseAkses by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }

    fun openBrowser(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { message = "Browser tidak tersedia" }
    }

    fun saveSettings() {
        prefs.edit()
            .putString("durasi_menit", duration)
            .putString("hostname", hostname)
            .putString("rdp_user", rdpUser)
            .putString("akses", akses)
            .putString("storage_boost", storageBoost)
            .putString("debloat", debloat)
            .apply()
    }

    fun error(e: Exception) {
        if (e is CancellationException) throw e
        message = e.message ?: "Operasi GitHub gagal. Coba lagi."
    }

    // Token tersimpan → whoami → invalid dibuang.
    LaunchedEffect(Unit) {
        val saved = vault.get(XyRdpConfig.VAULT_PAT_ID)
        if (saved != null) {
            val user = XyRdpClient.whoami(saved)
            if (user != null) {
                token = saved
                login = user
                saveVerifiedGitHubLogin(context, user)
            } else {
                vault.remove(XyRdpConfig.VAULT_PAT_ID)
            }
        }
    }

    LaunchedEffect(token, login) {
        if (token == null || login == null) return@LaunchedEffect
        forkReady = XyRdpClient.forkExists(token!!, login!!)
    }

    // Polling status + run terbaru tiap 15 detik (statistik live ikut ke sini).
    // Berhenti poll saat layar di belakang supaya baterai + kuota API aman.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(token, login) {
        if (token == null || login == null) return@LaunchedEffect
        while (true) {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !busy) {
                run = XyRdpClient.latestRun(token!!, login!!)
                access = XyRdpClient.access(token!!, login!!)
            }
            delay(15000)
        }
    }

    fun operate(action: String) {
        if (busy) return
        val tok = token ?: return
        val user = login ?: return
        busy = true
        scope.launch {
            try {
                when (action) {
                    "start" -> {
                        if (!forkReady) {
                            message = "Fork repo XyRDP dulu (tombol di atas)."
                            return@launch
                        }
                        XyRdpClient.enableWorkflow(tok, user)
                        val inputs = JSONObject().apply {
                            put("durasi_menit", duration)
                            put("hostname", hostname)
                            put("rdp_user", rdpUser)
                            put("akses", akses)
                            put("storage_boost", storageBoost)
                            put("debloat", debloat)
                        }
                        if (XyRdpClient.dispatch(tok, user, inputs)) {
                            saveSettings()
                            message = "Workflow dikirim — sesi sedang disiapkan."
                        } else {
                            message = "Gagal mengirim workflow. Cek kuota Actions / run yang masih aktif."
                        }
                    }
                    "stop" -> {
                        val id = XyRdpClient.latestRunId(tok, user)
                        if (id != null && XyRdpClient.cancelRun(tok, user, id)) {
                            message = "Permintaan stop dikirim."
                        } else {
                            message = "Tidak ada run aktif untuk dihentikan."
                        }
                    }
                    "logout" -> {
                        vault.remove(XyRdpConfig.VAULT_PAT_ID)
                        token = null
                        login = null
                        saveVerifiedGitHubLogin(context, null)
                        forkReady = false
                        run = null
                        access = null
                        message = "GitHub terputus dari app."
                    }
                }
            } catch (e: Exception) {
                error(e)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        XyTopBar(title = "Buat RDP", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Banner ----
            Image(
                painter = painterResource(R.drawable.xyrdp_banner),
                contentDescription = "XyRDP",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )

            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary)
            }

            if (token == null || login == null) {
                XyCard(Modifier.fillMaxWidth()) {
                    Text("RDP gratis dari GitHub Actions-mu", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Login dengan akun GitHub-mu. App otomatis menyiapkan repo template XyRDP, " +
                            "lalu kamu bisa mulai sesi Windows RDP langsung dari sini — lengkap dengan " +
                            "statistik RAM, disk, CPU, dan sisa waktu. Tanpa web perantara, tanpa token server.",
                    )
                    Spacer(Modifier.height(12.dp))
                    XyPillButton("Login GitHub", { loginDialog = true }, primary = true, enabled = !busy)
                }
            } else {
                XySectionLabel("Akun GitHub")
                XyCard(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(login ?: "-", style = MaterialTheme.typography.titleMedium)
                        if (isFounderAccount(login)) {
                            XyFounderBadge(compact = true)
                        }
                    }
                    if (isFounderAccount(login)) {
                        Spacer(Modifier.height(6.dp))
                        XyFounderBadge(
                            compact = false,
                            subtitle = "Pemilik & Kreator XyVerse",
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("Repo: $login/${XyRdpConfig.FORK_REPO_NAME} · ${if (forkReady) "siap" else "belum di-fork"}")
                    Spacer(Modifier.height(12.dp))
                    if (!forkReady) {
                        XyPillButton(
                            "Fork template XyRDP",
                            {
                                busy = true
                                scope.launch {
                                    try {
                                        if (XyRdpClient.createFork(token!!)) {
                                            XyRdpClient.enableWorkflow(token!!, login!!)
                                            forkReady = XyRdpClient.forkExists(token!!, login!!)
                                            message = if (forkReady) "Fork siap." else "Fork sedang dibuat — tunggu sebentar lalu tekan lagi."
                                        } else {
                                            message = "Fork gagal — cek koneksi/kuota GitHub."
                                        }
                                    } catch (e: Exception) { error(e) } finally { busy = false }
                                }
                            },
                            primary = true,
                            enabled = !busy,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    XyPillButton("Keluar GitHub", { confirmAction = "logout" }, primary = false, enabled = !busy)
                }

                if (forkReady) {
                    XySectionLabel("Pengaturan sesi")
                    XyCard(Modifier.fillMaxWidth()) {
                        XyPillButton("Durasi: $duration menit", { chooseDuration = true }, primary = false, enabled = !busy)
                        Spacer(Modifier.height(8.dp))
                        XyField(hostname, { hostname = it.take(30) }, "Hostname")
                        Spacer(Modifier.height(8.dp))
                        XyField(rdpUser, { rdpUser = it.take(20) }, "Username RDP")
                        Spacer(Modifier.height(8.dp))
                        XyPillButton("Akses: $akses", { chooseAkses = true }, primary = false, enabled = !busy)
                        Spacer(Modifier.height(8.dp))
                        XyPillButton(
                            "Storage boost: $storageBoost",
                            { storageBoost = if (storageBoost == "ya") "tidak" else "ya" },
                            primary = false,
                            enabled = !busy,
                        )
                        Spacer(Modifier.height(8.dp))
                        XyPillButton(
                            "Debloat: $debloat",
                            { debloat = when (debloat) { "ringan" -> "full"; "full" -> "tidak"; else -> "ringan" } },
                            primary = false,
                            enabled = !busy,
                        )
                    }

                    XySectionLabel("Sesi")
                    XyCard(Modifier.fillMaxWidth()) {
                        run?.let { r ->
                            Text(
                                "Run #${r.number}: ${r.status}${r.conclusion?.let { " · $it" } ?: ""}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        access?.let { a ->
                            Text("Status: ${if (a.active) "AKTIF" else "nonaktif"}")
                            if (a.expiresAt.isNotBlank()) Text("Berakhir (UTC): ${a.expiresAt}")
                            if (a.tunnelAddress.isNotBlank()) Text("Koneksi: ${a.tunnelAddress}")
                            if (a.tailscaleIp.isNotBlank()) Text("Tailscale: ${a.tailscaleIp}:3389")
                            if (a.rustdeskId.isNotBlank()) Text("RustDesk: ${a.rustdeskId}")
                        } ?: Text("Belum ada status sesi — mulai sesi dulu.")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                XyPillButton(
                                    "Mulai RDP",
                                    { confirmAction = "start" },
                                    primary = true,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                XyPillButton(
                                    "Stop",
                                    { confirmAction = "stop" },
                                    primary = false,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    // ---- Statistik live ----
                    access?.stats?.let { st ->
                        XySectionLabel("Statistik live")
                        XyCard(Modifier.fillMaxWidth()) {
                            StatBar(
                                "RAM",
                                "${"%.1f".format(st.ramUsedGb)} / ${"%.1f".format(st.ramTotalGb)} GB",
                                fraction(st.ramUsedGb, st.ramTotalGb),
                            )
                            Spacer(Modifier.height(8.dp))
                            StatBar(
                                "Disk C:",
                                "bebas ${"%.1f".format(st.diskFreeGb)} / ${"%.1f".format(st.diskTotalGb)} GB",
                                1f - fraction(st.diskFreeGb, st.diskTotalGb),
                            )
                            Spacer(Modifier.height(8.dp))
                            StatBar("CPU", "${st.cpuPercent}%", (st.cpuPercent / 100f).coerceIn(0f, 1f))
                            Spacer(Modifier.height(8.dp))
                            Text("Uptime: ${st.uptimeMinutes / 60}j ${st.uptimeMinutes % 60}m")
                            if (st.updatedAt.isNotBlank()) {
                                Text(
                                    "Diperbarui: ${st.updatedAt.take(19).replace('T', ' ')} UTC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ---- Connect ----
                    access?.takeIf { it.active && it.tunnelHost.isNotBlank() }?.let { a ->
                        XySectionLabel("Hubungkan")
                        XyCard(Modifier.fillMaxWidth()) {
                            Text("${a.tunnelHost}:${a.tunnelPort}", style = MaterialTheme.typography.titleMedium)
                            Text("Username: ${a.rdpUser} · ${a.mode}")
                            if (a.mode == "tailscale") {
                                Text("Pastikan Tailscale di HP terhubung ke tailnet yang sama.")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Input mikrofon", modifier = Modifier.weight(1f))
                                Switch(checked = microphone, onCheckedChange = { microphone = it }, enabled = !busy)
                            }
                            Spacer(Modifier.height(8.dp))
                            XyField(password, { password = it }, "Password RDP_PASSWORD", isPassword = true)
                            Text(
                                "Password tidak pernah bisa dibaca dari GitHub — isi manual.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            XyPillButton(
                                "Hubungkan RDP",
                                {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val fresh = XyRdpClient.access(token!!, login!!)
                                                ?: throw IllegalStateException("Sesi tidak lagi siap")
                                            check(fresh.active && fresh.tunnelHost.isNotBlank()) { "Sesi tidak lagi siap" }
                                            val profile = ConnectionProfile(
                                                host = fresh.tunnelHost,
                                                port = fresh.tunnelPort,
                                                username = fresh.rdpUser,
                                                password = password,
                                                label = "XyRDP",
                                                key = "xyrdp:${login}",
                                            )
                                            RdpOptions.of(context, profile.id).forRdpFree(microphone).write(context, profile.id)
                                            password = ""
                                            onConnect(profile)
                                        } catch (e: Exception) { error(e) } finally { busy = false }
                                    }
                                },
                                primary = true,
                                enabled = !busy && password.isNotBlank(),
                            )
                        }
                    }
                }
            }

            Text(
                "Sesi berjalan di runner GitHub — data pengguna otomatis disimpan ke artifact dan dipulihkan di sesi berikutnya.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (chooseDuration) {
        XyOverlay(title = "Durasi sesi", onDismiss = { chooseDuration = false }) {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("15", "30", "60", "120", "180", "240", "300", "330", "360").forEach { minutes ->
                    XyPillButton(
                        "$minutes menit",
                        { duration = minutes; chooseDuration = false },
                        primary = duration == minutes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (chooseAkses) {
        XyOverlay(title = "Jenis akses", onDismiss = { chooseAkses = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("tailscale", "portmap").forEach { mode ->
                    XyPillButton(
                        mode,
                        { akses = mode; chooseAkses = false },
                        primary = akses == mode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    confirmAction?.let { action ->
        XyDialog(
            title = when (action) {
                "start" -> "Mulai RDP?"
                "stop" -> "Hentikan RDP?"
                else -> "Keluar GitHub?"
            },
            body = when (action) {
                "start" -> "Workflow rdp-6h dikirim dengan pengaturan di atas dan memakai kuota GitHub Actions-mu. Data pengguna dari sesi sebelumnya otomatis dipulihkan."
                "stop" -> "Sesi aktif dihentikan. Folder pengguna disimpan dulu ke artifact supaya sesi berikutnya lanjut, bukan dari nol."
                else -> "App lupa token GitHub di HP ini (repo fork dan sesi di GitHub tidak tersentuh)."
            },
            confirmLabel = "Lanjutkan",
            onConfirm = { confirmAction = null; operate(action) },
            dismissLabel = "Batal",
            onDismiss = { confirmAction = null },
        )
    }
    if (loginDialog) {
        XyOverlay(title = "Login GitHub", onDismiss = { loginDialog = false }) {
            GitHubLoginBody(
                onCancel = { loginDialog = false },
                onSuccess = { tok, user ->
                    scope.launch {
                        vault.put(XyRdpConfig.VAULT_PAT_ID, tok)
                        token = tok
                        login = user
                        saveVerifiedGitHubLogin(context, user)
                        loginDialog = false
                        message = "Login berhasil: $user"
                    }
                },
                onError = { message = it },
            )
        }
    }
}

private fun fraction(used: Double, total: Double): Float =
    if (total <= 0.0) 0f else (used / total).toFloat().coerceIn(0f, 1f)

/** Bar statistik: label + nilai + progress manual (tanpa dependensi tambahan). */
@Composable
private fun StatBar(label: String, value: String, fraction: Float) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Device flow GitHub ringkas — dipakai layar Buat RDP (ronde 46). */
@Composable
private fun GitHubLoginBody(
    onCancel: () -> Unit,
    onSuccess: (token: String, user: String) -> Unit,
    onError: (String) -> Unit,
) {
    var busy by remember { mutableStateOf(true) }
    var userCode by remember { mutableStateOf("") }
    var verifyUri by remember { mutableStateOf("https://github.com/login/device") }
    var statusText by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        busy = true
        val start = githubDeviceStart(GITHUB_OAUTH_CLIENT_ID)
        if (start == null) {
            busy = false
            statusText = "Gagal menghubungi GitHub. Coba lagi."
            return@LaunchedEffect
        }
        userCode = start.optString("user_code", "")
        verifyUri = start.optString("verification_uri", "https://github.com/login/device")
        val intervalMs = ((start.optInt("interval", 5) + 2).coerceIn(5, 30)) * 1000L
        val deviceCode = start.optString("device_code", "")
        val deadline = System.currentTimeMillis() + start.optInt("expires_in", 900) * 1000L
        busy = false
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            val poll = githubDevicePoll(GITHUB_OAUTH_CLIENT_ID, deviceCode) ?: continue
            val tok = poll.optString("access_token")
            if (tok.isNotEmpty()) {
                busy = true
                val user = XyRdpClient.whoami(tok)
                busy = false
                if (user != null) onSuccess(tok, user)
                else onError("Login gagal: profil tidak terbaca.")
                return@LaunchedEffect
            }
            when (poll.optString("error", "")) {
                "expired_token" -> { statusText = "Kode kedaluwarsa — tutup lalu buka lagi."; return@LaunchedEffect }
                "access_denied" -> { statusText = "Login ditolak."; return@LaunchedEffect }
            }
        }
        statusText = "Waktu login habis — coba lagi."
    }

    Text("Buka github.com/login/device di browser, lalu masukkan kode:")
    Spacer(Modifier.height(8.dp))
    Text(
        userCode.ifBlank { "…" },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    if (statusText.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(statusText, color = MaterialTheme.colorScheme.error)
    }
    if (busy) {
        Spacer(Modifier.height(8.dp))
        Text("Memproses…")
    }
    Spacer(Modifier.height(12.dp))
    XyPillButton(
        "Buka halaman login GitHub",
        {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verifyUri))) }
                .onFailure { statusText = "Browser tidak tersedia — salin kode dan buka manual." }
        },
        primary = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    XyPillButton("Batal", onCancel, primary = false, modifier = Modifier.fillMaxWidth())
}
