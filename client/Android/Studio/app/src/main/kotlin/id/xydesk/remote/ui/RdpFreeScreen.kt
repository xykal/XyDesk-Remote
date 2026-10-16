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
import androidx.compose.ui.Alignment
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
import id.xydesk.remote.ui.rdp.XyRdpPhase
import id.xydesk.remote.ui.rdp.XyRdpRun
import id.xydesk.remote.ui.rdp.XyRdpSetupStatus
import id.xydesk.remote.ui.rdp.XyRdpValidatedSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Layar "Buat RDP" — RDP gratis dari GitHub Actions milik akun sendiri.
 *
 * Ronde 46–48: sepenuhnya NATIVE via GitHub API (fork template XyRDP → sinkron
 * upstream → cek Secrets & Actions → dispatch workflow → validasi silang
 * `run` + `jobs/steps` + `rdp-status.json` di branch `status`).
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
    var setupStatus by remember { mutableStateOf<XyRdpSetupStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var duration by remember { mutableStateOf(prefs.getString("durasi_menit", "360") ?: "360") }
    var hostname by remember { mutableStateOf(prefs.getString("hostname", "rdpfree") ?: "rdpfree") }
    var rdpUser by remember { mutableStateOf(prefs.getString("rdp_user", "xyadmin") ?: "xyadmin") }
    var akses by remember { mutableStateOf(prefs.getString("akses", "tailscale") ?: "tailscale") }
    var portmapHost by remember { mutableStateOf(prefs.getString("portmap_host", "") ?: "") }
    var portmapPort by remember { mutableStateOf(prefs.getString("portmap_port", "") ?: "") }
    var storageBoost by remember { mutableStateOf(prefs.getString("storage_boost", "tidak") ?: "tidak") }
    var debloat by remember { mutableStateOf(prefs.getString("debloat", "ringan") ?: "ringan") }
    var microphone by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }

    var validatedSession by remember { mutableStateOf<XyRdpValidatedSession?>(null) }
    var nowTickMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showStepsDetail by remember { mutableStateOf(false) }
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
            .putString("portmap_host", portmapHost)
            .putString("portmap_port", portmapPort)
            .putString("storage_boost", storageBoost)
            .putString("debloat", debloat)
            .apply()
    }

    fun error(e: Exception) {
        if (e is CancellationException) throw e
        message = e.message ?: "Operasi GitHub gagal. Coba lagi."
    }

    suspend fun refreshAll(tok: String, user: String) {
        val setup = XyRdpClient.inspectSetup(tok, user, akses)
        setupStatus = setup
        forkReady = setup.forkExists
        if (setup.forkExists) {
            validatedSession = XyRdpClient.pollValidatedSession(tok, user)
            nowTickMillis = System.currentTimeMillis()
        } else {
            validatedSession = null
        }
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

    LaunchedEffect(token, login, akses) {
        if (token == null || login == null) return@LaunchedEffect
        refreshAll(token!!, login!!)
    }

    // Detak 1 detik untuk hitung mundur sisa waktu live saat sesi aktif.
    LaunchedEffect(validatedSession?.isVerifiedLive) {
        if (validatedSession?.isVerifiedLive == true) {
            while (true) {
                delay(1000)
                nowTickMillis = System.currentTimeMillis()
            }
        }
    }

    // Polling status + validasi silang run/steps/branch status tiap 8 detik.
    // Berhenti poll saat layar di belakang supaya baterai + kuota API aman.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(token, login) {
        if (token == null || login == null) return@LaunchedEffect
        while (true) {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !busy) {
                refreshAll(token!!, login!!)
            }
            delay(8000)
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
                        XyRdpClient.syncForkWithUpstream(tok, user)
                        XyRdpClient.enableWorkflow(tok, user)
                        val inputs = JSONObject().apply {
                            put("durasi_menit", duration)
                            put("hostname", hostname)
                            put("rdp_user", rdpUser)
                            put("akses", akses)
                            put("storage_boost", storageBoost)
                            put("debloat", debloat)
                            put("xydesk", "ya")
                            put("microphone", if (microphone) "ya" else "tidak")
                            if (akses == "portmap") {
                                if (portmapHost.isNotBlank()) put("portmap_host", portmapHost.trim())
                                if (portmapPort.isNotBlank()) put("portmap_port", portmapPort.trim())
                            }
                        }
                        if (XyRdpClient.dispatch(tok, user, inputs)) {
                            saveSettings()
                            message = "Workflow dikirim ke GitHub Actions — memantau progres tahap demi tahap…"
                            delay(2500)
                            refreshAll(tok, user)
                        } else {
                            message = "Gagal mengirim workflow. Cek izin token (`repo` + `workflow`) atau kuota Actions."
                        }
                    }
                    "stop" -> {
                        val id = validatedSession?.run?.id ?: XyRdpClient.latestRunId(tok, user)
                        if (id != null && XyRdpClient.cancelRun(tok, user, id)) {
                            message = "Permintaan stop dikirim ke Run #$id."
                            delay(2000)
                            refreshAll(tok, user)
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
                        setupStatus = null
                        validatedSession = null
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
                            "memvalidasi sesi & tahapan workflow secara real-time, lalu menampilkan " +
                            "monitor RAM, disk, CPU, dan sisa waktu. Tanpa web perantara, tanpa token server.",
                    )
                    Spacer(Modifier.height(12.dp))
                    XyPillButton("Login GitHub", { loginDialog = true }, primary = true, enabled = !busy)
                }
            } else {
                XySectionLabel("Akun GitHub & Status Repo")
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
                    val wfTxt = when {
                        !forkReady -> "belum di-fork"
                        setupStatus?.workflowActive == true -> "workflow aktif"
                        else -> "workflow perlu diaktifkan"
                    }
                    Text("Repo: $login/${XyRdpConfig.FORK_REPO_NAME} · $wfTxt")
                    setupStatus?.let { st ->
                        if (st.forkExists) {
                            Spacer(Modifier.height(4.dp))
                            if (st.missingSecrets.isEmpty()) {
                                Text(
                                    "✓ Secrets wajib lengkap (RDP_PASSWORD & ${if (akses == "portmap") "PORTMAP_OVPN_CONFIG" else "TAILSCALE_AUTH_KEY"})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    "⚠ Secret GitHub belum diisi: ${st.missingSecrets.joinToString(", ")}. Isi dulu di halaman Secrets repo agar workflow tidak langsung gagal.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(6.dp))
                                XyPillButton(
                                    "Buka Pengaturan Secrets di GitHub ↗",
                                    { openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions") },
                                    primary = false,
                                    enabled = !busy,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!forkReady) {
                        XyPillButton(
                            "Fork template XyRDP",
                            {
                                busy = true
                                scope.launch {
                                    try {
                                        if (XyRdpClient.createFork(token!!)) {
                                            delay(2000)
                                            XyRdpClient.enableWorkflow(token!!, login!!)
                                            refreshAll(token!!, login!!)
                                            message = if (forkReady) "Fork siap & workflow diaktifkan." else "Fork sedang dibuat — tunggu sebentar lalu tekan Periksa Ulang."
                                        } else {
                                            message = "Fork gagal — cek koneksi/kuota GitHub."
                                        }
                                    } catch (e: Exception) { error(e) } finally { busy = false }
                                }
                            },
                            primary = true,
                            enabled = !busy,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                XyPillButton(
                                    "Sinkronkan Fork",
                                    {
                                        busy = true
                                        scope.launch {
                                            try {
                                                val okSync = XyRdpClient.syncForkWithUpstream(token!!, login!!)
                                                XyRdpClient.enableWorkflow(token!!, login!!)
                                                refreshAll(token!!, login!!)
                                                message = if (okSync) {
                                                    "Fork disinkronkan dengan template xykal/XyRDP terbaru."
                                                } else {
                                                    "Status fork diperbarui."
                                                }
                                            } catch (e: Exception) { error(e) } finally { busy = false }
                                        }
                                    },
                                    primary = false,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                XyPillButton(
                                    "Keluar GitHub",
                                    { confirmAction = "logout" },
                                    primary = false,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
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
                        if (akses == "portmap") {
                            Spacer(Modifier.height(8.dp))
                            XyField(portmapHost, { portmapHost = it.take(120) }, "Hostname publik Portmap (tanpa tcp://)")
                            Spacer(Modifier.height(8.dp))
                            XyField(portmapPort, { portmapPort = it.filter { c -> c.isDigit() }.take(5) }, "Port publik Portmap (1–65535)")
                        }
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

                    // ---- Sesi & Validasi Otoritatif ----
                    XySectionLabel("Sesi & Validasi")
                    XyCard(Modifier.fillMaxWidth()) {
                        val vs = validatedSession
                        val r = vs?.run
                        Text(
                            text = vs?.phaseLabel ?: "Memeriksa status sesi…",
                            style = MaterialTheme.typography.titleSmall,
                            color = when (vs?.phase) {
                                XyRdpPhase.READY -> MaterialTheme.colorScheme.primary
                                XyRdpPhase.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (r != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Run #${r.number} (ID ${r.id}): ${r.status}${r.conclusion?.let { " · $it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        vs?.validationNote?.let { note ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                note,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (vs.isVerifiedLive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Progres tahapan workflow (saat PREPARING / FAILED / READY)
                        vs?.progress?.let { prog ->
                            if (prog.totalSteps > 0) {
                                Spacer(Modifier.height(8.dp))
                                StatBar(
                                    "Progres Tahap Workflow",
                                    "${prog.completedSteps} / ${prog.totalSteps} tahap",
                                    fraction(prog.completedSteps.toDouble(), prog.totalSteps.toDouble()),
                                )
                                prog.currentStep?.let { cur ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "⟳ Sedang berjalan: $cur",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                prog.failedStep?.let { fail ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "✕ Tahap gagal: $fail",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                XyPillButton(
                                    if (showStepsDetail) "Sembunyikan rincian tahap" else "Lihat rincian tahap (${prog.completedSteps}/${prog.totalSteps})",
                                    { showStepsDetail = !showStepsDetail },
                                    primary = false,
                                )
                                if (showStepsDetail) {
                                    Spacer(Modifier.height(6.dp))
                                    prog.steps.forEachIndexed { idx, (sName, sState) ->
                                        val icon = when (sState) {
                                            "success" -> "✓"
                                            "in_progress" -> "⟳"
                                            "failure", "timed_out" -> "✕"
                                            "skipped" -> "–"
                                            else -> "·"
                                        }
                                        Text(
                                            "${idx + 1}. $icon $sName ($sState)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (sState) {
                                                "failure", "timed_out" -> MaterialTheme.colorScheme.error
                                                "in_progress" -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // Hitung mundur live & informasi endpoint saat sesi aktif
                        val liveAccess = vs?.access
                        if (vs?.isVerifiedLive == true && liveAccess != null) {
                            Spacer(Modifier.height(8.dp))
                            val remSec = if (liveAccess.expiresAtMillis > nowTickMillis) {
                                ((liveAccess.expiresAtMillis - nowTickMillis) / 1000L).coerceAtLeast(0L)
                            } else 0L
                            val hh = remSec / 3600L
                            val mm = (remSec % 3600L) / 60L
                            val ss = remSec % 60L
                            Text(
                                "Sisa waktu aktif: %02d:%02d:%02d".format(hh, mm, ss),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text("Hostname: ${liveAccess.hostname} (${liveAccess.osName})")
                            Text("Endpoint tervalidasi: ${vs.validatedHost}:${vs.validatedPort} (${vs.validatedTransport})")
                            if (liveAccess.expiresAt.isNotBlank()) {
                                Text("Berakhir (UTC): ${liveAccess.expiresAt}")
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        val isRunningOrQueued = vs?.phase == XyRdpPhase.PREPARING ||
                            vs?.phase == XyRdpPhase.QUEUED ||
                            vs?.phase == XyRdpPhase.READY
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                XyPillButton(
                                    "Mulai RDP",
                                    { confirmAction = "start" },
                                    primary = true,
                                    enabled = !busy && !isRunningOrQueued,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                XyPillButton(
                                    "Stop",
                                    { confirmAction = "stop" },
                                    primary = false,
                                    enabled = !busy && isRunningOrQueued,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                XyPillButton(
                                    "Periksa Ulang",
                                    {
                                        scope.launch {
                                            busy = true
                                            try {
                                                refreshAll(token!!, login!!)
                                                message = "Status sesi & monitor diperbarui."
                                            } catch (e: Exception) { error(e) } finally { busy = false }
                                        }
                                    },
                                    primary = false,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (r?.url?.isNotBlank() == true) {
                                Box(Modifier.weight(1f)) {
                                    XyPillButton(
                                        "Log GitHub ↗",
                                        { openBrowser(r.url) },
                                        primary = false,
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    // ---- Monitor & Statistik Live ----
                    val liveAcc = validatedSession?.access
                    if (liveAcc != null) {
                        XySectionLabel("Monitor & Statistik Live")
                        XyCard(Modifier.fillMaxWidth()) {
                            val st = liveAcc.stats
                            if (st != null && st.ramTotalGb > 0.0) {
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
                                Text("Uptime sesi: ${st.uptimeMinutes / 60}j ${st.uptimeMinutes % 60}m")
                                if (st.updatedAt.isNotBlank()) {
                                    Text(
                                        "Diperbarui: ${st.updatedAt.take(19).replace('T', ' ')} UTC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Text(
                                    "Spesifikasi Runner: ${liveAcc.initialCpuThreads} vCPU · RAM ${"%.1f".format(liveAcc.initialRamGb)} GB · Disk bebas awal ${"%.1f".format(liveAcc.initialDiskFreeGb)} GB",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Menunggu detak telemetri 60 detik pertama dari loop keepalive…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ---- Connect ----
                    val vsReady = validatedSession?.takeIf { it.isVerifiedLive && !it.validatedHost.isNullOrBlank() }
                    if (vsReady != null && vsReady.access != null) {
                        val a = vsReady.access
                        val hostToConnect = vsReady.validatedHost!!
                        val portToConnect = vsReady.validatedPort
                        XySectionLabel("Hubungkan")
                        XyCard(Modifier.fillMaxWidth()) {
                            Text("$hostToConnect:$portToConnect", style = MaterialTheme.typography.titleMedium)
                            Text("Username: ${a.rdpUser} · Mode: ${vsReady.validatedTransport ?: a.mode}")
                            if ((vsReady.validatedTransport ?: a.mode) == "tailscale") {
                                Text("Pastikan aplikasi Tailscale di HP sudah ON dan login ke tailnet yang sama.")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Input mikrofon", modifier = Modifier.weight(1f))
                                Switch(checked = microphone, onCheckedChange = { microphone = it }, enabled = !busy)
                            }
                            Spacer(Modifier.height(8.dp))
                            XyField(password, { password = it }, "Password RDP_PASSWORD", isPassword = true)
                            Text(
                                "Password tidak pernah bisa dibaca dari GitHub — masukkan sesuai secret RDP_PASSWORD milikmu.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            XyPillButton(
                                "Hubungkan RDP (120 FPS Ready)",
                                {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val fresh = XyRdpClient.pollValidatedSession(token!!, login!!)
                                            check(fresh.isVerifiedLive && !fresh.validatedHost.isNullOrBlank()) {
                                                "Sesi tidak lagi aktif di GitHub Actions"
                                            }
                                            val profile = ConnectionProfile(
                                                host = fresh.validatedHost,
                                                port = fresh.validatedPort,
                                                username = fresh.access?.rdpUser ?: a.rdpUser,
                                                password = password,
                                                label = "XyRDP (${fresh.access?.hostname ?: "Cloud"})",
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
