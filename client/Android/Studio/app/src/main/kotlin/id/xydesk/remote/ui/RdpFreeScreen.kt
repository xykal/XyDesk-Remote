package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import id.xydesk.remote.ui.rdp.XyRdpClient
import id.xydesk.remote.ui.rdp.XyRdpConfig
import id.xydesk.remote.ui.rdp.XyRdpPhase
import id.xydesk.remote.ui.rdp.XyRdpSetupStatus
import id.xydesk.remote.ui.rdp.XyRdpValidatedSession
import id.xydesk.remote.ui.ssh.XySshTerminalScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Layar "Buat RDP" — RDP gratis dari GitHub Actions milik akun sendiri.
 *
 * Ronde 46–52:
 * - Banner utama kini ditempatkan di Beranda (`XyDeskHome.kt`).
 * - Dilengkapi **Secret Configuration** lengkap dengan indikator status per-secret,
 *   generator password kuat, simpan password lokal terenkripsi untuk auto-connect,
 *   serta **Link Resmi 1-Klik** menuju Tailscale Keys, Tailscale Machines,
 *   Portmap OpenVPN Configs, Portmap Mapping Rules, dan GitHub Actions Secrets.
 * - Dilengkapi **semua checklist / toggle pengaturan workflow `rdp-6h.yml`**:
 *   `xydesk`, `microphone`, `storage_boost`, `samp`, `win10`, `ekstra`,
 *   `roblox`, `minecraft_java`, `minecraft_bedrock`, `debloat`, `grafis`, dan `runner`.
 */
@Composable
fun RdpFreeScreen(onBack: () -> Unit, onConnect: (ConnectionProfile) -> Unit) {
    val context = LocalContext.current
    val vault = remember { CredentialVault(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("xyrdp_settings", Context.MODE_PRIVATE) }

    var token by remember { mutableStateOf<String?>(null) }
    var login by remember { mutableStateOf<String?>(null) }
    var authWaitingCode by remember { mutableStateOf<String?>(null) }
    var authWaitingUrl by remember { mutableStateOf<String?>(null) }
    var authBusy by remember { mutableStateOf(false) }
    var forkReady by remember { mutableStateOf(false) }
    var setupStatus by remember { mutableStateOf<XyRdpSetupStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    // Pilihan utama workflow rdp-6h.yml
    var duration by remember { mutableStateOf(prefs.getString("durasi_menit", "360") ?: "360") }
    var runnerImage by remember { mutableStateOf(prefs.getString("runner", "windows-2022") ?: "windows-2022") }
    var linuxMode by remember { mutableStateOf(prefs.getString("linux_mode", "desktop") ?: "desktop") }
    var hostname by remember { mutableStateOf(prefs.getString("hostname", "rdpfree") ?: "rdpfree") }
    var openSshTerminal by remember { mutableStateOf(false) }
    var sshInitHost by remember { mutableStateOf("") }
    var sshInitPort by remember { mutableStateOf(22) }
    var sshInitUser by remember { mutableStateOf("xyadmin") }
    var sshInitAuto by remember { mutableStateOf(false) }
    var rdpUser by remember { mutableStateOf(prefs.getString("rdp_user", "xyadmin") ?: "xyadmin") }
    var akses by remember { mutableStateOf(prefs.getString("akses", "tailscale") ?: "tailscale") }
    var portmapHost by remember { mutableStateOf(prefs.getString("portmap_host", "") ?: "") }
    var portmapPort by remember { mutableStateOf(prefs.getString("portmap_port", "") ?: "") }
    var debloat by remember { mutableStateOf(prefs.getString("debloat", "ringan") ?: "ringan") }
    var grafis by remember { mutableStateOf(prefs.getString("grafis", "software") ?: "software") }

    // Semua checklist / toggle workflow rdp-6h.yml
    var autoRenew by remember { mutableStateOf(prefs.getString("perpanjang_otomatis", "ya") != "tidak") }
    var xydeskBoost by remember { mutableStateOf(prefs.getString("xydesk", "ya") != "tidak") }
    var microphone by remember { mutableStateOf(prefs.getString("microphone", "ya") != "tidak") }
    var storageBoost by remember { mutableStateOf(prefs.getString("storage_boost", "ya") != "tidak") }
    var sampDeps by remember { mutableStateOf(prefs.getString("samp", "ya") != "tidak") }
    var win10Theme by remember { mutableStateOf(prefs.getString("win10", "ya") != "tidak") }
    var ekstraDesktop by remember { mutableStateOf(prefs.getString("ekstra", "ya") != "tidak") }
    var robloxReady by remember { mutableStateOf(prefs.getString("roblox", "tidak") == "ya") }
    var mcJavaReady by remember { mutableStateOf(prefs.getString("minecraft_java", "tidak") == "ya") }
    var mcBedrockReady by remember { mutableStateOf(prefs.getString("minecraft_bedrock", "tidak") == "ya") }

    // Secret configuration & password vault lokal
    var password by remember {
        mutableStateOf(vault.get("xyrdp.saved.rdp_password").orEmpty())
    }
    var rememberPasswordLocal by remember {
        mutableStateOf(prefs.getBoolean("remember_rdp_pass", true))
    }
    var secretTailscaleDraft by remember { mutableStateOf("") }
    var secretOvpnDraft by remember { mutableStateOf("") }
    var showSecretGuide by remember { mutableStateOf(true) }

    var validatedSession by remember { mutableStateOf<XyRdpValidatedSession?>(null) }
    var nowTickMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showStepsDetail by remember { mutableStateOf(false) }
    var chooseDuration by remember { mutableStateOf(false) }
    var chooseAkses by remember { mutableStateOf(false) }
    var chooseRunner by remember { mutableStateOf(false) }
    var chooseDebloat by remember { mutableStateOf(false) }
    var chooseGrafis by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }

    fun openBrowser(url: String) {
        openInAppCustomTab(context, url)
    }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun generateStrongPassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%^&*"
        val rng = SecureRandom()
        val sb = StringBuilder("Xy!9")
        repeat(12) { sb.append(chars[rng.nextInt(chars.length)]) }
        return sb.toString()
    }

    fun saveSettings() {
        prefs.edit()
            .putString("durasi_menit", duration)
            .putString("runner", runnerImage)
            .putString("linux_mode", linuxMode)
            .putString("hostname", hostname)
            .putString("rdp_user", rdpUser)
            .putString("akses", akses)
            .putString("portmap_host", portmapHost)
            .putString("portmap_port", portmapPort)
            .putString("debloat", debloat)
            .putString("grafis", grafis)
            .putString("perpanjang_otomatis", if (autoRenew) "ya" else "tidak")
            .putString("xydesk", if (xydeskBoost) "ya" else "tidak")
            .putString("microphone", if (microphone) "ya" else "tidak")
            .putString("storage_boost", if (storageBoost) "ya" else "tidak")
            .putString("samp", if (sampDeps) "ya" else "tidak")
            .putString("win10", if (win10Theme) "ya" else "tidak")
            .putString("ekstra", if (ekstraDesktop) "ya" else "tidak")
            .putString("roblox", if (robloxReady) "ya" else "tidak")
            .putString("minecraft_java", if (mcJavaReady) "ya" else "tidak")
            .putString("minecraft_bedrock", if (mcBedrockReady) "ya" else "tidak")
            .putBoolean("remember_rdp_pass", rememberPasswordLocal)
            .apply()
        if (rememberPasswordLocal && password.isNotBlank()) {
            vault.put("xyrdp.saved.rdp_password", password)
        } else if (!rememberPasswordLocal) {
            vault.remove("xyrdp.saved.rdp_password")
        }
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

    LaunchedEffect(validatedSession?.isVerifiedLive) {
        if (validatedSession?.isVerifiedLive == true) {
            while (true) {
                delay(1000)
                nowTickMillis = System.currentTimeMillis()
            }
        }
    }

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
                            put("runner", runnerImage)
                            put("linux_mode", linuxMode)
                            put("hostname", hostname)
                            put("rdp_user", rdpUser)
                            put("akses", akses)
                            put("storage_boost", if (storageBoost) "ya" else "tidak")
                            put("debloat", debloat)
                            put("samp", if (sampDeps) "ya" else "tidak")
                            put("win10", if (win10Theme) "ya" else "tidak")
                            put("xydesk", if (xydeskBoost) "ya" else "tidak")
                            put("ekstra", if (ekstraDesktop) "ya" else "tidak")
                            put("grafis", grafis)
                            put("microphone", if (microphone) "ya" else "tidak")
                            put("roblox", if (robloxReady) "ya" else "tidak")
                            put("minecraft_java", if (mcJavaReady) "ya" else "tidak")
                            put("minecraft_bedrock", if (mcBedrockReady) "ya" else "tidak")
                            put("perpanjang_otomatis", if (autoRenew) "ya" else "tidak")
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

    if (openSshTerminal) {
        XySshTerminalScreen(
            initialHost = sshInitHost,
            initialPort = sshInitPort,
            initialUser = sshInitUser,
            initialPassword = password,
            initialLabel = if (sshInitHost.isNotBlank()) "XyRDP Linux ($sshInitHost)" else "",
            autoConnectOnStart = sshInitAuto,
            onBack = { openSshTerminal = false },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        XyTopBar(
            title = "Buat RDP Cloud",
            onBack = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (message.isNotBlank()) {
                XyCard(Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (token == null || login == null) {
                XyCard(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GitHubLogoIcon(size = 24.dp, tint = MaterialTheme.colorScheme.primary)
                        Text("RDP Cloud Gratis (Windows & Linux)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Login GitHub langsung tanpa kode: tinggal masuk ke akun GitHub kamu lalu klik tombol hijau Authorize " +
                            "(akses profil, nama, repository, workflow, & instalasi otomatis).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    XyPillButton(
                        "Login dengan GitHub (Langsung Authorize)",
                        {
                            GitHubDirectOAuthController.open(
                                onSuccess = { tok, user ->
                                    authBusy = false
                                    authWaitingCode = null
                                    authWaitingUrl = null
                                    token = tok
                                    login = user
                                    message = "Berhasil terhubung ke GitHub sebagai @$user!"
                                },
                                onError = { err ->
                                    authBusy = false
                                    message = err
                                },
                            )
                        },
                        primary = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                // =========================================================
                // 1. KARTU AKUN GITHUB & STATUS REPOSITORY
                // =========================================================
                XySectionLabel("1. Akun GitHub & Repository")
                XyCard(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GitHubLogoIcon(size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                        Text("@${login ?: "-"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    }
                    Spacer(Modifier.height(4.dp))
                    val wfTxt = when {
                        !forkReady -> "belum di-fork"
                        setupStatus?.workflowActive == true -> "workflow aktif ✓"
                        else -> "workflow perlu diaktifkan"
                    }
                    Text(
                        "Repository: $login/${XyRdpConfig.FORK_REPO_NAME} · $wfTxt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (!forkReady) {
                        XyPillButton(
                            "Fork Template Resmi xykal/XyRDP",
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
                            modifier = Modifier.fillMaxWidth(),
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
                                    "Buka Repo ↗",
                                    { openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}") },
                                    primary = false,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                if (forkReady) {
                    // =========================================================
                    // 2. KONFIGURASI SECRET (SECRET CONFIGURATION + LINK RESMI)
                    // =========================================================
                    XySectionLabel("2. Secret Configuration & Link Resmi")
                    XyCard(Modifier.fillMaxWidth()) {
                        val st = setupStatus
                        val hasRdpPass = st?.configuredSecrets?.contains("RDP_PASSWORD") == true
                        val hasTsKey = st?.configuredSecrets?.contains("TAILSCALE_AUTH_KEY") == true
                        val hasOvpn = st?.configuredSecrets?.contains("PORTMAP_OVPN_CONFIG") == true

                        Text(
                            "Status GitHub Actions Secrets di $login/${XyRdpConfig.FORK_REPO_NAME}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))

                        // Checklist status secret nyata dari GitHub API
                        SecretStatusRow(
                            name = "RDP_PASSWORD",
                            configured = hasRdpPass,
                            required = true,
                            desc = "Password login Windows RDP (wajib untuk semua mode)",
                            onCopyName = {
                                copyToClipboard("Secret Name", "RDP_PASSWORD")
                                message = "Nama secret 'RDP_PASSWORD' disalin ke clipboard."
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        SecretStatusRow(
                            name = "TAILSCALE_AUTH_KEY",
                            configured = hasTsKey,
                            required = akses == "tailscale",
                            desc = "Auth Key Tailscale (tskey-auth-…) untuk koneksi privat",
                            onCopyName = {
                                copyToClipboard("Secret Name", "TAILSCALE_AUTH_KEY")
                                message = "Nama secret 'TAILSCALE_AUTH_KEY' disalin ke clipboard."
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        SecretStatusRow(
                            name = "PORTMAP_OVPN_CONFIG",
                            configured = hasOvpn,
                            required = akses == "portmap",
                            desc = "Isi file .ovpn Portmap.io untuk mode TCP publik tanpa VPN di HP",
                            onCopyName = {
                                copyToClipboard("Secret Name", "PORTMAP_OVPN_CONFIG")
                                message = "Nama secret 'PORTMAP_OVPN_CONFIG' disalin ke clipboard."
                            },
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Siapkan & Salin Nilai Secret",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))

                        // Input & Generator Password RDP
                        XyField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (rememberPasswordLocal && it.isNotBlank()) {
                                    vault.put("xyrdp.saved.rdp_password", it)
                                }
                            },
                            label = "Password RDP (RDP_PASSWORD)",
                            hint = "Min. 12 karakter (huruf besar, kecil, angka, simbol)",
                            isPassword = false,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                "Buat Password Kuat",
                                {
                                    val generated = generateStrongPassword()
                                    password = generated
                                    copyToClipboard("RDP_PASSWORD", generated)
                                    if (rememberPasswordLocal) vault.put("xyrdp.saved.rdp_password", generated)
                                    message = "Password kuat dibuat & disalin ke clipboard!"
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                "Salin & Pasang RDP_PASSWORD ↗",
                                {
                                    if (password.isNotBlank()) copyToClipboard("RDP_PASSWORD", password)
                                    openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions/new")
                                },
                                primary = !hasRdpPass,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    rememberPasswordLocal = !rememberPasswordLocal
                                    saveSettings()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = rememberPasswordLocal,
                                onCheckedChange = {
                                    rememberPasswordLocal = it
                                    saveSettings()
                                },
                            )
                            Text(
                                "Simpan salinan RDP_PASSWORD terenkripsi di HP agar siap langsung Connect saat RDP menyala",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        if (akses == "tailscale") {
                            XyField(
                                value = secretTailscaleDraft,
                                onValueChange = { secretTailscaleDraft = it.trim() },
                                label = "Tailscale Auth Key (TAILSCALE_AUTH_KEY)",
                                hint = "tskey-auth-k...",
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                XyPillButton(
                                    "Ambil Tailscale Auth Key ↗",
                                    { openBrowser("https://login.tailscale.com/admin/settings/keys") },
                                    primary = !hasTsKey,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                                XyPillButton(
                                    "Salin & Pasang di GitHub ↗",
                                    {
                                        if (secretTailscaleDraft.isNotBlank()) {
                                            copyToClipboard("TAILSCALE_AUTH_KEY", secretTailscaleDraft)
                                        } else {
                                            copyToClipboard("Secret Name", "TAILSCALE_AUTH_KEY")
                                        }
                                        openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions/new")
                                    },
                                    primary = false,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                XyPillButton(
                                    "Panel Mesin Tailscale ↗",
                                    { openBrowser("https://login.tailscale.com/admin/machines") },
                                    primary = false,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                                XyPillButton(
                                    "Panduan Auth Key ↗",
                                    { openBrowser("https://tailscale.com/kb/1085/auth-keys") },
                                    primary = false,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            XyField(
                                value = secretOvpnDraft,
                                onValueChange = { secretOvpnDraft = it },
                                label = "Isi Konfigurasi .ovpn (PORTMAP_OVPN_CONFIG)",
                                hint = "Tempel isi file .ovpn dari Portmap.io di sini…",
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                XyPillButton(
                                    "1. Buat Config .ovpn ↗",
                                    { openBrowser("https://portmap.io/configs") },
                                    primary = !hasOvpn,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                                XyPillButton(
                                    "2. Buat Mapping TCP 3389 ↗",
                                    { openBrowser("https://portmap.io/mapping_rules") },
                                    primary = false,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            XyPillButton(
                                "Salin & Pasang PORTMAP_OVPN_CONFIG di GitHub ↗",
                                {
                                    if (secretOvpnDraft.isNotBlank()) {
                                        copyToClipboard("PORTMAP_OVPN_CONFIG", secretOvpnDraft)
                                    } else {
                                        copyToClipboard("Secret Name", "PORTMAP_OVPN_CONFIG")
                                    }
                                    openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions/new")
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                "Buka Halaman Secrets GitHub ↗",
                                { openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions") },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                if (showSecretGuide) "Tutup Panduan" else "Panduan Cepat",
                                { showSecretGuide = !showSecretGuide },
                                primary = false,
                                compact = true,
                            )
                        }

                        if (showSecretGuide) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "Cara Cepat Mendapatkan Tailscale Auth Key:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("1. Ketuk tombol 'Ambil Tailscale Auth Key ↗' di atas lalu pilih 'Generate auth key'.", style = MaterialTheme.typography.labelSmall)
                                Text("2. Aktifkan 'Reusable' dan 'Ephemeral', lalu salin kode 'tskey-auth-…'.", style = MaterialTheme.typography.labelSmall)
                                Text("3. Ketuk 'Salin & Pasang di GitHub ↗' dan simpan dengan nama TAILSCALE_AUTH_KEY.", style = MaterialTheme.typography.labelSmall)
                                Text("4. Simpan juga secret kedua bernama RDP_PASSWORD berisi password RDP di atas.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // =========================================================
                    // 3. SISTEM OPERASI, IMAGE & CHECKLIST FITUR LENGKAP
                    // =========================================================
                    XySectionLabel("3. Sistem Operasi, Image & Pengaturan Sesi")
                    XyCard(Modifier.fillMaxWidth()) {
                        val isLinuxRunner = runnerImage.startsWith("ubuntu", ignoreCase = true)
                        val isLinuxPolos = isLinuxRunner && linuxMode == "polos"
                        Text(
                            "Pilih Sistem Operasi Cloud RDP / VPS:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                "Windows Server",
                                {
                                    runnerImage = "windows-2022"
                                    linuxMode = "desktop"
                                    saveSettings()
                                },
                                primary = !isLinuxRunner,
                                compact = true,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                "Linux Desktop (XFCE4)",
                                {
                                    if (!isLinuxRunner) runnerImage = "ubuntu-24.04"
                                    linuxMode = "desktop"
                                    saveSettings()
                                },
                                primary = isLinuxRunner && !isLinuxPolos,
                                compact = true,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                "Linux Polos (Tanpa XFCE4 · SSH VPS)",
                                {
                                    if (!isLinuxRunner) runnerImage = "ubuntu-24.04"
                                    linuxMode = "polos"
                                    saveSettings()
                                },
                                primary = isLinuxPolos,
                                compact = true,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                "Terminal SSH / VPS",
                                {
                                    val live = validatedSession?.takeIf { it.isVerifiedLive && !it.validatedHost.isNullOrBlank() }
                                    sshInitHost = live?.validatedHost.orEmpty()
                                    sshInitPort = if (live != null && live.access?.linuxMode == "polos") live.validatedPort else 22
                                    sshInitUser = live?.access?.rdpUser ?: rdpUser.ifBlank { "xyadmin" }
                                    sshInitAuto = false
                                    openSshTerminal = true
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                isLinuxPolos ->
                                    "Mode Linux Polos aktif ($runnerImage · Tanpa XFCE4): Server SSH murni di port :22 & :3389, waktu boot super kilat ~20 detik, cocok untuk bot, Docker, coding, & VPS terminal."
                                isLinuxRunner ->
                                    "Mode Linux Desktop aktif ($runnerImage): XFCE4 + XRDP :3389 + SSH :22 + Virtual Mic PulseAudio, waktu boot ~90 detik, penyimpanan /persist anti-reset."
                                else ->
                                    "Mode Windows Server aktif ($runnerImage): Mendukung penuh GTA SA-MP SilentPatch, Minecraft OpenGL 4.6 D3D12, On-Mic Audio Capture, dan Dual-Storage Persistence."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))

                        // Tombol Preset Cepat
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                "⚡ Profil Cepat (Boot Kilat)",
                                {
                                    xydeskBoost = true
                                    microphone = true
                                    storageBoost = false
                                    debloat = "tidak"
                                    sampDeps = false
                                    win10Theme = false
                                    ekstraDesktop = false
                                    grafis = "tidak"
                                    robloxReady = false
                                    mcJavaReady = false
                                    mcBedrockReady = false
                                    saveSettings()
                                    message = "Profil Cepat diterapkan — waktu tunggu setup paling singkat."
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                if (isLinuxRunner) "🐧 Profil Linux Lengkap" else "🎮 Profil Game Lengkap",
                                {
                                    autoRenew = true
                                    xydeskBoost = true
                                    microphone = true
                                    storageBoost = true
                                    debloat = "ringan"
                                    sampDeps = !isLinuxRunner
                                    win10Theme = !isLinuxRunner
                                    ekstraDesktop = true
                                    grafis = "software"
                                    mcJavaReady = !isLinuxRunner
                                    saveSettings()
                                    message = if (isLinuxRunner) {
                                        "Profil Linux Desktop Lengkap (XFCE4 + Chrome + VS Code + Wine + Storage Boost + Anti-Reset) diterapkan."
                                    } else {
                                        "Profil Game Lengkap (GTA SA-MP SilentPatch + Minecraft OpenGL 4.6 + Anti-Reset) diterapkan."
                                    }
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                "Durasi: $duration mnt",
                                { chooseDuration = true },
                                primary = false,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                "OS/Image: $runnerImage",
                                { chooseRunner = true },
                                primary = false,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyField(
                                value = hostname,
                                onValueChange = { hostname = it.take(30) },
                                label = "Hostname",
                                modifier = Modifier.weight(1f),
                            )
                            XyField(
                                value = rdpUser,
                                onValueChange = { rdpUser = it.take(20) },
                                label = "Username RDP",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                "Akses: $akses",
                                { chooseAkses = true },
                                primary = false,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                "Debloat: $debloat",
                                { chooseDebloat = true },
                                primary = false,
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (akses == "portmap") {
                            Spacer(Modifier.height(8.dp))
                            XyField(portmapHost, { portmapHost = it.take(120) }, "Hostname publik Portmap (tanpa tcp://)")
                            Spacer(Modifier.height(8.dp))
                            XyField(portmapPort, { portmapPort = it.filter { c -> c.isDigit() }.take(5) }, "Port publik Portmap (1–65535)")
                        }
                        Spacer(Modifier.height(8.dp))
                        XyPillButton(
                            "Toolkit Grafis CPU (Mesa): $grafis",
                            { chooseGrafis = true },
                            primary = false,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Checklist & Toggle Fitur Sesi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))

                        RdpToggleRow(
                            title = "Anti-Reset & Lanjutkan Sesi Otomatis",
                            subtitle = "Simpan otomatis C:\\Persist, C:\\Games, .minecraft, GTA SA-MP & Desktop tiap 15 menit + estafet sesi berikutnya tanpa mengulang dari nol",
                            checked = autoRenew,
                            enabled = !busy,
                            onCheckedChange = { autoRenew = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "Setup Host XyDesk (120 FPS Boost & UDP 4433)",
                            subtitle = "Aktifkan AVC444, DWM 120Hz, ClearType, Audio/Mic, & RawInput Game",
                            checked = xydeskBoost,
                            enabled = !busy,
                            onCheckedChange = { xydeskBoost = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "Input Mikrofon RDP",
                            subtitle = "Izinkan suara mikrofon HP masuk ke sesi Windows RDP",
                            checked = microphone,
                            enabled = !busy,
                            onCheckedChange = { microphone = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "Storage Boost (+45 s/d 75 GB Disk Kosong)",
                            subtitle = "Bersihkan SDK Android/Haskell/CodeQL bawaan runner agar C: lega",
                            checked = storageBoost,
                            enabled = !busy,
                            onCheckedChange = { storageBoost = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "GTA SA & SA-MP By SilentPatch (90 FPS + Mouse Fix)",
                            subtitle = "Pasang DirectX 9, VC++, DirectPlay, SilentPatchSA.asi, ASI Loader, Widescreen Fix & Auto-Patcher GTA SA-MP",
                            checked = sampDeps,
                            enabled = !busy,
                            onCheckedChange = { sampDeps = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "Tweak Tampilan Windows 10",
                            subtitle = "Matikan Server Manager, aktifkan personalisasi & wallpaper XyVerse",
                            checked = win10Theme,
                            enabled = !busy,
                            onCheckedChange = { win10Theme = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "Ekstra Desktop (7-Zip, Chrome, Lightshot)",
                            subtitle = "Pasang aplikasi esensial desktop dan tool screenshot",
                            checked = ekstraDesktop,
                            enabled = !busy,
                            onCheckedChange = { ekstraDesktop = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "Persiapan Kompatibilitas Roblox",
                            subtitle = "Pemeriksaan dependensi & konfigurasi lingkungan Roblox",
                            checked = robloxReady,
                            enabled = !busy,
                            onCheckedChange = { robloxReady = it; saveSettings() },
                        )
                        RdpToggleRow(
                            title = "Minecraft Java + OpenGL 4.6 D3D12 (Anti-GLFW 65542)",
                            subtitle = "Pasang Temurin JDK 21 & JRE 8, PrismLauncher, Minecraft Launcher & driver OpenGL 4.6",
                            checked = mcJavaReady,
                            enabled = !busy,
                            onCheckedChange = {
                                mcJavaReady = it
                                if (it) grafis = "software"
                                saveSettings()
                            },
                        )
                        RdpToggleRow(
                            title = "Persiapan Dukungan Minecraft Bedrock",
                            subtitle = "Periksa layanan Store/Xbox pada image runner",
                            checked = mcBedrockReady,
                            enabled = !busy,
                            onCheckedChange = { mcBedrockReady = it; saveSettings() },
                        )
                    }

                    // =========================================================
                    // 4. SESI & VALIDASI OTORITATIF
                    // =========================================================
                    XySectionLabel("4. Sesi & Validasi Live")
                    XyCard(Modifier.fillMaxWidth()) {
                        val vs = validatedSession
                        val r = vs?.run
                        Text(
                            text = vs?.phaseLabel ?: "Memeriksa status sesi…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
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
                                fontWeight = FontWeight.Bold,
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

                    // =========================================================
                    // 5. MONITOR & STATISTIK LIVE
                    // =========================================================
                    val liveAcc = validatedSession?.access
                    if (liveAcc != null) {
                        XySectionLabel("5. Monitor & Statistik Live")
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

                    // =========================================================
                    // 6. HUBUNGKAN KE SESI RDP / TERMINAL SSH
                    // =========================================================
                    val vsReady = validatedSession?.takeIf { it.isVerifiedLive && !it.validatedHost.isNullOrBlank() }
                    if (vsReady != null && vsReady.access != null) {
                        val a = vsReady.access
                        val hostToConnect = vsReady.validatedHost!!
                        val portToConnect = vsReady.validatedPort
                        val isSessionPolos = a.linuxMode == "polos" || a.osName.contains("Polos", ignoreCase = true) || portToConnect == 22
                        val isSessionLinux = isSessionPolos || a.osName.contains("Ubuntu", ignoreCase = true) || a.osName.contains("Linux", ignoreCase = true)
                        XySectionLabel(if (isSessionPolos) "6. Hubungkan ke Terminal SSH (Linux Polos)" else "6. Hubungkan ke RDP / SSH")
                        XyCard(Modifier.fillMaxWidth()) {
                            Text("$hostToConnect:$portToConnect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Username: ${a.rdpUser} · OS: ${a.osName} · Mode: ${vsReady.validatedTransport ?: a.mode}")
                            if ((vsReady.validatedTransport ?: a.mode) == "tailscale") {
                                Text(
                                    "Pastikan aplikasi Tailscale di HP sudah ON dan login ke tailnet yang sama.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            XyField(password, { password = it }, "Password RDP_PASSWORD", isPassword = true)
                            Spacer(Modifier.height(8.dp))
                            if (isSessionPolos) {
                                XyPillButton(
                                    "Hubungkan Terminal SSH (Linux Polos)",
                                    {
                                        if (rememberPasswordLocal && password.isNotBlank()) {
                                            vault.put("xyrdp.saved.rdp_password", password)
                                        }
                                        sshInitHost = hostToConnect
                                        sshInitPort = portToConnect
                                        sshInitUser = a.rdpUser
                                        sshInitAuto = true
                                        openSshTerminal = true
                                    },
                                    primary = true,
                                    enabled = !busy && password.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                XyPillButton(
                                    "Hubungkan RDP (120 FPS Boost + On-Mic)",
                                    {
                                        busy = true
                                        scope.launch {
                                            try {
                                                val fresh = XyRdpClient.pollValidatedSession(token!!, login!!)
                                                check(fresh.isVerifiedLive && !fresh.validatedHost.isNullOrBlank()) {
                                                    "Sesi tidak lagi aktif di GitHub Actions"
                                                }
                                                if (rememberPasswordLocal && password.isNotBlank()) {
                                                    vault.put("xyrdp.saved.rdp_password", password)
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
                                                onConnect(profile)
                                            } catch (e: Exception) { error(e) } finally { busy = false }
                                        }
                                    },
                                    primary = true,
                                    enabled = !busy && password.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (isSessionLinux) {
                                    Spacer(Modifier.height(8.dp))
                                    XyPillButton(
                                        "Buka Terminal SSH Pendamping (:22)",
                                        {
                                            if (rememberPasswordLocal && password.isNotBlank()) {
                                                vault.put("xyrdp.saved.rdp_password", password)
                                            }
                                            sshInitHost = hostToConnect
                                            sshInitPort = 22
                                            sshInitUser = a.rdpUser
                                            sshInitAuto = true
                                            openSshTerminal = true
                                        },
                                        primary = false,
                                        enabled = !busy && password.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
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
                        { duration = minutes; saveSettings(); chooseDuration = false },
                        primary = duration == minutes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (chooseRunner) {
        XyOverlay(title = "Pilih OS & Image Runner", onDismiss = { chooseRunner = false }) {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Windows Server (Desktop Penuh + Game Win64)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                listOf(
                    "windows-2022" to "Windows Server 2022 (Paling Stabil · GTA SA-MP & Minecraft)",
                    "windows-2025" to "Windows Server 2025 (Kernel Terbaru & Performa I/O Tinggi)",
                    "windows-latest" to "windows-latest (Otomatis Image Windows Terbaru GitHub)",
                ).forEach { (id, label) ->
                    XyPillButton(
                        label,
                        { runnerImage = id; linuxMode = "desktop"; saveSettings(); chooseRunner = false },
                        primary = runnerImage == id && !runnerImage.startsWith("ubuntu"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Linux Desktop (XFCE4 + XRDP :3389 · Boot Kilat ~90 Detik)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                listOf(
                    "ubuntu-24.04" to "Ubuntu 24.04 LTS Desktop (XFCE4 + XRDP + SSH :22)",
                    "ubuntu-22.04" to "Ubuntu 22.04 LTS Desktop (XFCE4 LTS Klasik Kompatibilitas Tinggi)",
                    "ubuntu-latest" to "ubuntu-latest Desktop (Otomatis Image Ubuntu Terbaru)",
                ).forEach { (id, label) ->
                    XyPillButton(
                        label,
                        { runnerImage = id; linuxMode = "desktop"; saveSettings(); chooseRunner = false },
                        primary = runnerImage == id && linuxMode == "desktop",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Linux Polos Tanpa XFCE4 (Headless SSH VPS :22 & :3389 · Boot ~20 Detik)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                listOf(
                    "ubuntu-24.04" to "Ubuntu 24.04 LTS Polos (Server SSH Murni Tanpa GUI)",
                    "ubuntu-22.04" to "Ubuntu 22.04 LTS Polos (Server SSH Murni Tanpa GUI)",
                    "ubuntu-latest" to "ubuntu-latest Polos (Server SSH Murni Tanpa GUI)",
                ).forEach { (id, label) ->
                    XyPillButton(
                        label,
                        { runnerImage = id; linuxMode = "polos"; saveSettings(); chooseRunner = false },
                        primary = runnerImage == id && linuxMode == "polos",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (chooseAkses) {
        XyOverlay(title = "Jalur Akses Koneksi", onDismiss = { chooseAkses = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "tailscale" to "Tailscale (Jaringan Privat Paling Cepat & Aman)",
                    "portmap" to "Portmap.io (TCP Publik Tanpa VPN di HP)",
                ).forEach { (mode, label) ->
                    XyPillButton(
                        label,
                        { akses = mode; saveSettings(); chooseAkses = false },
                        primary = akses == mode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (chooseDebloat) {
        XyOverlay(title = "Mode Debloat Windows", onDismiss = { chooseDebloat = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ringan" to "Ringan (Hapus OneDrive/Xbox/Bloatware Appx)",
                    "full" to "Full (Ringan + Hapus Edge bila Chrome ada)",
                    "tidak" to "Tidak (Biarkan bawaan runner)",
                ).forEach { (mode, label) ->
                    XyPillButton(
                        label,
                        { debloat = mode; saveSettings(); chooseDebloat = false },
                        primary = debloat == mode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (chooseGrafis) {
        XyOverlay(title = "Toolkit Grafis CPU (Mesa)", onDismiss = { chooseGrafis = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "tidak" to "Tidak (Gunakan driver bawaan Windows)",
                    "software" to "Software (Pasang Mesa3D OpenGL CPU)",
                ).forEach { (mode, label) ->
                    XyPillButton(
                        label,
                        { grafis = mode; saveSettings(); chooseGrafis = false },
                        primary = grafis == mode,
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
                "start" -> "Workflow rdp-6h dikirim dengan pengaturan & checklist di atas menggunakan kuota GitHub Actions-mu. Data pengguna dari sesi sebelumnya otomatis dipulihkan."
                "stop" -> "Sesi aktif dihentikan. Folder pengguna disimpan dulu ke artifact supaya sesi berikutnya lanjut, bukan dari nol."
                else -> "App lupa token GitHub di HP ini (repo fork dan sesi di GitHub tidak tersentuh)."
            },
            confirmLabel = "Lanjutkan",
            onConfirm = { confirmAction = null; operate(action) },
            dismissLabel = "Batal",
            onDismiss = { confirmAction = null },
        )
    }
}

@Composable
private fun SecretStatusRow(
    name: String,
    configured: Boolean,
    required: Boolean,
    desc: String,
    onCopyName: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (configured) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else if (required) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            )
            .border(
                width = 1.dp,
                color = if (configured) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else if (required) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onCopyName() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (configured) "✓" else if (required) "⚠" else "○",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (configured) MaterialTheme.colorScheme.primary
            else if (required) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (configured) "Terpasang" else if (required) "Wajib diisi" else "Opsional",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (configured) MaterialTheme.colorScheme.primary
                    else if (required) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Salin",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RdpToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

private fun fraction(used: Double, total: Double): Float =
    if (total <= 0.0) 0f else (used / total).toFloat().coerceIn(0f, 1f)

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
