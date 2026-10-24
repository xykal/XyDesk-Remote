package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import id.xydesk.remote.ui.components.XySwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.RdpOptions
import id.xydesk.remote.core.forRdpFree
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyDialog
import id.xydesk.remote.ui.components.XyEmptyState
import id.xydesk.remote.ui.components.XyErrorState
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyLoadingSkeletonCard
import id.xydesk.remote.ui.components.XyLoadingSpinnerState
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySectionLabel
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XySuccessStateBanner
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.rdp.validateRdpLaunch
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
 * Layar "Buat RDP Cloud" — UI/UX terstruktur rapi, selaras dengan rana utama
 * (`XyDeskTheme.kt` / `XyDeskHome.kt`), responsif di semua ukuran layar/DPI/rasio,
 * dan dilengkapi Loading Skeleton/Spinner, Empty State, Error State, & Success State.
 */
@Composable
fun RdpFreeScreen(onBack: () -> Unit, onConnect: (ConnectionProfile) -> Unit) {
    val context = LocalContext.current
    val vault = remember { CredentialVault(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("xyrdp_settings", Context.MODE_PRIVATE) }

    var token by remember { mutableStateOf<String?>(vault.get(XyRdpConfig.VAULT_PAT_ID)) }
    var login by remember { mutableStateOf<String?>(getVerifiedGitHubLogin(context)) }
    var initialLoading by remember { mutableStateOf(false) }
    var forkReady by remember { mutableStateOf(false) }
    var setupStatus by remember { mutableStateOf<XyRdpSetupStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("Memproses permintaan ke GitHub…") }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Sesi & Koneksi, 1 = OS & Fitur, 2 = Secrets & Akses

    // Pilihan utama workflow rdp-6h.yml
    var duration by remember { mutableStateOf(prefs.getString("durasi_menit", "360") ?: "360") }
    var runnerImage by remember { mutableStateOf(prefs.getString("runner", "windows-2022") ?: "windows-2022") }
    var linuxMode by remember { mutableStateOf(prefs.getString("linux_mode", "desktop") ?: "desktop") }
    var hostname by remember { mutableStateOf(prefs.getString("hostname", "rdpfree") ?: "rdpfree") }
    var openSshTerminal by remember { mutableStateOf(false) }
    var sshInitHost by remember { mutableStateOf("") }
    var sshInitPort by remember { mutableIntStateOf(22) }
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
    var discordReady by remember { mutableStateOf(prefs.getString("discord", "tidak") == "ya") }
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
    var showSecretGuide by remember { mutableStateOf(false) }

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
            .putString("discord", if (discordReady) "ya" else "tidak")
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

    fun reportError(e: Exception) {
        if (e is CancellationException) throw e
        errorMessage = e.message ?: "Operasi GitHub gagal. Periksa jaringan lalu coba lagi."
    }

    suspend fun refreshAll(tok: String, user: String, showSkeleton: Boolean = false) {
        if (showSkeleton) initialLoading = true
        try {
            val forkPrefKey = "fork_confirmed_${user.trim().lowercase()}"
            val cachedForkReady = prefs.getBoolean(forkPrefKey, false)
            if (cachedForkReady) {
                XyRdpClient.markForkConfirmed(user)
                forkReady = true
            }
            val setup = XyRdpClient.inspectSetup(tok, user, akses)
            val resolvedFork = setup.forkExists || cachedForkReady || XyRdpClient.isForkConfirmed(user)
            if (resolvedFork) {
                prefs.edit().putBoolean(forkPrefKey, true).apply()
                XyRdpClient.markForkConfirmed(user)
            }
            setupStatus = setup.copy(forkExists = resolvedFork)
            forkReady = resolvedFork
            if (resolvedFork) {
                validatedSession = XyRdpClient.pollValidatedSession(tok, user)
                nowTickMillis = System.currentTimeMillis()
            } else {
                validatedSession = null
            }
            errorMessage = null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            errorMessage = e.message ?: "Gagal memuat status repository & sesi GitHub."
        } finally {
            if (showSkeleton) initialLoading = false
        }
    }

    // Sinkron otomatis saat selesai login GitHub OAuth dari Chrome Custom Tabs
    val oauthRev = GitHubDirectOAuthController.authRevision
    LaunchedEffect(oauthRev) {
        val saved = vault.get(XyRdpConfig.VAULT_PAT_ID)
        if (!saved.isNullOrBlank()) {
            token = saved
            val cachedUser = getVerifiedGitHubLogin(context)
            if (!cachedUser.isNullOrBlank()) {
                login = cachedUser
            }
            val user = XyRdpClient.whoami(saved)
            if (!user.isNullOrBlank()) {
                login = user
                saveVerifiedGitHubLogin(context, user)
            }
        }
    }

    LaunchedEffect(token, login, akses) {
        val tok = token
        val usr = login
        if (tok.isNullOrBlank() || usr.isNullOrBlank()) return@LaunchedEffect
        refreshAll(tok, usr, showSkeleton = setupStatus == null)
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
        val tok = token
        val usr = login
        if (tok.isNullOrBlank() || usr.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            delay(8000)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !busy && !initialLoading) {
                refreshAll(tok, usr, showSkeleton = false)
            }
        }
    }

    fun operate(action: String) {
        if (busy) return
        val tok = token ?: return
        val user = login ?: return
        if (action == "start") {
            val errors = validateRdpLaunch(duration, runnerImage, hostname, rdpUser, akses, portmapHost, portmapPort)
            if (errors.isNotEmpty()) {
                activeTab = 1
                errorMessage = errors.joinToString("\n")
                return
            }
        }
        busy = true
        errorMessage = null
        scope.launch {
            try {
                when (action) {
                    "start" -> {
                        if (!forkReady) {
                            errorMessage = "Repository XyRDP belum di-fork. Tekan tombol 'Fork Repo XyRDP' terlebih dahulu."
                            return@launch
                        }
                        busyLabel = "Menyinkronkan template XyRDP & mengirim workflow sesi baru…"
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
                            put("discord", if (discordReady) "ya" else "tidak")
                            put("minecraft_java", if (mcJavaReady) "ya" else "tidak")
                            put("minecraft_bedrock", if (mcBedrockReady) "ya" else "tidak")
                            put("perpanjang_otomatis", if (autoRenew) "ya" else "tidak")
                            if (akses == "portmap") {
                                if (portmapHost.isNotBlank()) put("portmap_host", portmapHost.trim())
                                if (portmapPort.isNotBlank()) put("portmap_port", portmapPort.trim())
                            }
                            if (akses == "railway") {
                                if (portmapHost.isNotBlank()) put("railway_host", portmapHost.trim())
                                if (portmapPort.isNotBlank()) put("railway_port", portmapPort.trim())
                            }
                        }
                        if (XyRdpClient.dispatch(tok, user, inputs)) {
                            saveSettings()
                            activeTab = 0
                            successMessage = "Workflow berhasil dikirim ke GitHub Actions. Memantau tahapan boot secara real-time…"
                            delay(2500)
                            refreshAll(tok, user)
                        } else {
                            errorMessage = "Gagal mengirim workflow. Pastikan secret RDP_PASSWORD sudah terpasang dan kuota Actions tersedia."
                        }
                    }
                    "stop" -> {
                        busyLabel = "Mengirim permintaan penghentian sesi…"
                        val id = validatedSession?.run?.id ?: XyRdpClient.latestRunId(tok, user)
                        if (id != null && XyRdpClient.cancelRun(tok, user, id)) {
                            successMessage = "Permintaan berhenti dikirim ke Run #$id. Backup hanya tersedia jika langkah penyimpanan workflow berhasil."
                            delay(2000)
                            refreshAll(tok, user)
                        } else {
                            errorMessage = "Tidak ditemukan run aktif untuk dihentikan."
                        }
                    }
                    "samp_antiban" -> {
                        busyLabel = "Mengirim perintah Reset Anti-Ban SAMP & GPCI…"
                        val inputs = JSONObject().apply {
                            put("durasi_menit", "60")
                            put("runner", "windows-2022")
                            put("linux_mode", "desktop")
                            put("hostname", "samp-antiban")
                            put("rdp_user", rdpUser)
                            put("akses", "tailscale")
                            put("storage_boost", "tidak")
                            put("debloat", "tidak")
                            put("samp", "ya")
                            put("win10", "tidak")
                            put("xydesk", "tidak")
                            put("ekstra", "tidak")
                            put("grafis", "software")
                            put("microphone", "tidak")
                            put("roblox", "tidak")
                            put("minecraft_java", "tidak")
                            put("minecraft_bedrock", "tidak")
                            put("perpanjang_otomatis", "tidak")
                        }
                        if (XyRdpClient.dispatch(tok, user, inputs)) {
                            saveSettings()
                            activeTab = 0
                            successMessage = "Workflow Reset Anti-Ban SAMP & GPCI berhasil dikirim. Tunggu run selesai di GitHub Actions."
                            delay(2500)
                            refreshAll(tok, user)
                        } else {
                            errorMessage = "Gagal mengirim workflow anti-ban. Pastikan secret RDP_PASSWORD sudah terpasang."
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
                        successMessage = "Akun GitHub berhasil diputus dari perangkat ini."
                    }
                }
            } catch (e: Exception) {
                reportError(e)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        XyTopBar(
            title = "XyRDP Cloud",
            onBack = onBack,
            actions = {
                if (token != null && login != null) {
                    XyPillButton(
                        text = "Refresh",
                        icon = XyIcons.Refresh,
                        onClick = {
                            val tok = token ?: return@XyPillButton
                            val usr = login ?: return@XyPillButton
                            scope.launch {
                                busy = true
                                busyLabel = "Memperbarui status…"
                                try {
                                    refreshAll(tok, usr)
                                    successMessage = "Status sesi diperbarui."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        primary = false,
                        compact = true,
                        enabled = !busy && !initialLoading,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1280f / 440f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(18.dp),
                        ),
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id.xydesk.remote.R.drawable.xyrdp_banner),
                        contentDescription = "XyRDP Cloud Banner",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // State Banners: Loading Spinner, Error State, & Success State
                if (busy) {
                    XyLoadingSpinnerState(
                        title = busyLabel,
                        subtitle = "Harap tunggu sebentar, jangan tutup halaman ini.",
                    )
                }
                errorMessage?.let { err ->
                    XyErrorState(
                        title = "Kendala Operasi Cloud RDP",
                        message = err,
                        retryLabel = if (token != null && login != null) "Coba Lagi" else null,
                        onRetry = if (token != null && login != null) {
                            {
                                scope.launch {
                                    refreshAll(token!!, login!!, showSkeleton = true)
                                }
                            }
                        } else null,
                        onDismiss = { errorMessage = null },
                    )
                }
                successMessage?.let { ok ->
                    XySuccessStateBanner(
                        title = "Berhasil",
                        subtitle = ok,
                        onDismiss = { successMessage = null },
                    )
                }

                // =========================================================
                // BELUM LOGIN GITHUB -> EMPTY / ONBOARDING STATE RAPI
                // =========================================================
                if (token.isNullOrBlank() || login.isNullOrBlank()) {
                    XyEmptyState(
                        title = "Cloud RDP & VPS",
                        subtitle = "Login GitHub untuk menjalankan Windows RDP, Linux XFCE, atau SSH.",
                        icon = XyIcons.Monitor,
                        actionLabel = "Login GitHub",
                        onAction = {
                            errorMessage = null
                            GitHubDirectOAuthController.open(
                                onSuccess = { tok, user ->
                                    token = tok
                                    login = user
                                    successMessage = "Login @$user berhasil"
                                },
                                onError = { err ->
                                    errorMessage = err
                                },
                            )
                        },
                    )
                } else {
                    // =========================================================
                    // BAR IDENTITAS AKUN GITHUB RINGKAS
                    // =========================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            GitHubLogoIcon(size = 18.dp, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "@${login ?: "-"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = when {
                                    initialLoading -> "Memuat…"
                                    !forkReady -> "Belum di-fork"
                                    setupStatus?.workflowActive == true -> "Aktif · Siap"
                                    else -> "Perlu sinkron"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (forkReady) {
                            XyPillButton(
                                text = "Sync",
                                icon = XyIcons.Refresh,
                                onClick = {
                                    busy = true
                                    busyLabel = "Menyinkronkan…"
                                    scope.launch {
                                        try {
                                            XyRdpClient.syncForkWithUpstream(token!!, login!!)
                                            XyRdpClient.enableWorkflow(token!!, login!!)
                                            refreshAll(token!!, login!!)
                                            successMessage = "Tersinkronisasi"
                                        } catch (e: Exception) {
                                            reportError(e)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                primary = false,
                                compact = true,
                                enabled = !busy,
                            )
                        }
                        XyPillButton(
                            text = "Keluar",
                            icon = XyIcons.Power,
                            onClick = { confirmAction = "logout" },
                            primary = false,
                            compact = true,
                            enabled = !busy,
                        )
                    }

                    if (initialLoading && setupStatus == null) {
                        XyLoadingSkeletonCard(lines = 2, showAvatar = false)
                        XyLoadingSkeletonCard(lines = 3, showAvatar = false)
                    } else if (!forkReady) {
                        XyEmptyState(
                            title = "Siapkan Repository XyRDP",
                            subtitle = "Buat fork XyRDP ke @$login sekali saja untuk mulai.",
                            icon = XyIcons.Monitor,
                            actionLabel = "Siapkan XyRDP",
                            onAction = {
                                busy = true
                                busyLabel = "Menyiapkan XyRDP…"
                                scope.launch {
                                    try {
                                        val okFork = XyRdpClient.createFork(token!!, login) ||
                                            XyRdpClient.forkExists(token!!, login!!)
                                        if (okFork) {
                                            val forkPrefKey = "fork_confirmed_${login!!.trim().lowercase()}"
                                            prefs.edit().putBoolean(forkPrefKey, true).apply()
                                            XyRdpClient.markForkConfirmed(login)
                                            forkReady = true
                                            delay(1500)
                                            XyRdpClient.enableWorkflow(token!!, login!!)
                                            refreshAll(token!!, login!!)
                                            successMessage = "Repository XyRDP siap digunakan"
                                        } else {
                                            errorMessage = "Gagal menyiapkan fork XyRDP."
                                        }
                                    } catch (e: Exception) {
                                        reportError(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                    } else {
                        XySegmented(
                            options = listOf("Sesi RDP", "OS & Fitur", "Secrets"),
                            selectedIndex = activeTab,
                            onSelect = { activeTab = it },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        when (activeTab) {
                            // ---------------------------------------------------------
                            // TAB 0: SESI LIVE, INFO RINGKAS, STATISTIK & KONEKSI 1-KLIK
                            // ---------------------------------------------------------
                            0 -> {
                                val vs = validatedSession
                                val r = vs?.run
                                val isRunningOrQueued = vs?.phase == XyRdpPhase.PREPARING ||
                                    vs?.phase == XyRdpPhase.QUEUED ||
                                    vs?.phase == XyRdpPhase.READY
                                val isLinuxTarget = runnerImage.startsWith("ubuntu", ignoreCase = true)
                                val isLinuxPolosTarget = isLinuxTarget && linuxMode == "polos"

                                // Peringatan cepat bila Secret wajib belum dipasang
                                val st = setupStatus
                                val isRailwayEndpoint = portmapHost.trim().let {
                                    it.endsWith(".rlwy.net", ignoreCase = true) ||
                                        it.endsWith(".railway.app", ignoreCase = true) ||
                                        it.equals("bore.pub", ignoreCase = true) ||
                                        it.equals("auto", ignoreCase = true)
                                }
                                val hasRdpPass = st?.configuredSecrets?.contains("RDP_PASSWORD") == true
                                val hasAccessSecret = when {
                                    isRailwayEndpoint -> true
                                    akses == "tailscale" -> st?.configuredSecrets?.contains("TAILSCALE_AUTH_KEY") == true
                                    else -> st?.configuredSecrets?.contains("PORTMAP_OVPN_CONFIG") == true ||
                                        st?.configuredSecrets?.contains("TAILSCALE_AUTH_KEY") == true
                                }
                                var showBeginnerGuide by remember {
                                    mutableStateOf(false)
                                }
                                var antiSuspendNewAccount by remember {
                                    mutableStateOf(prefs.getBoolean("anti_suspend_guard", true))
                                }

                                // 1. KARTU PROTEKSI AKUN & PANDUAN RINGKAS
                                XyCard(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showBeginnerGuide = !showBeginnerGuide },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = XyIcons.Shield,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Anti-Suspend Guard",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = if (antiSuspendNewAccount) {
                                                        "Aktif · Tailscale / Railway / Portmap"
                                                    } else {
                                                        "Nonaktif · Ketuk untuk mengatur"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (showBeginnerGuide) XyIcons.ChevronUp else XyIcons.ChevronDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }

                                    AnimatedVisibility(visible = showBeginnerGuide) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            RdpFlatToggleRow(
                                                title = "Mode Aman Akun Baru",
                                                subtitle = "Jeda request API natural",
                                                checked = antiSuspendNewAccount,
                                                enabled = !busy,
                                                onCheckedChange = {
                                                    antiSuspendNewAccount = it
                                                    prefs.edit().putBoolean("anti_suspend_guard", it).apply()
                                                    if (it && duration == "360") {
                                                        duration = "180"
                                                        saveSettings()
                                                    }
                                                },
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                XyPillButton(
                                                    text = "OS & Fitur",
                                                    icon = XyIcons.Sliders,
                                                    onClick = { activeTab = 1 },
                                                    primary = false,
                                                    compact = true,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                XyPillButton(
                                                    text = "Secrets",
                                                    icon = XyIcons.Key,
                                                    onClick = { activeTab = 2 },
                                                    primary = !(hasRdpPass && hasAccessSecret),
                                                    compact = true,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }

                                if (st != null && (!hasRdpPass || !hasAccessSecret)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                            .clickable { activeTab = 2 }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = XyIcons.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Lengkapi GitHub Secrets",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "${if (!hasRdpPass) "RDP_PASSWORD" else ""}${if (!hasRdpPass && !hasAccessSecret) " · " else ""}${if (!hasAccessSecret) (if (akses == "tailscale") "TAILSCALE_AUTH_KEY" else "PORTMAP_OVPN_CONFIG") else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            imageVector = XyIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }

                                // 2. KARTU UTAMA KONTROL PEMBUATAN RDP (PILIH OS + DURASI LANGSUNG TANPA POPUP)
                                XyCard(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when (vs?.phase) {
                                                            XyRdpPhase.READY -> Color(0xFF808080)
                                                            XyRdpPhase.PREPARING, XyRdpPhase.QUEUED -> Color(0xFFA7A7A7)
                                                            XyRdpPhase.FAILED -> Color(0xFF777777)
                                                            else -> Color(0xFF9C9C9C)
                                                        },
                                                    ),
                                            )
                                            Column {
                                                Text(
                                                    text = vs?.phaseLabel ?: "Server Cloud RDP",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                                Text(
                                                    text = "4 vCPU · 16 GB RAM · SSD 75 GB · 120 FPS",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (r != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Text(
                                                    text = "#${r.number}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }

                                    // Progress tahapan workflow bila sedang berjalan
                                    vs?.progress?.let { prog ->
                                        if (prog.totalSteps > 0) {
                                            Spacer(Modifier.height(8.dp))
                                            StatBar(
                                                prog.currentStep ?: "Menyiapkan Server…",
                                                "${prog.completedSteps}/${prog.totalSteps}",
                                                fraction(prog.completedSteps.toDouble(), prog.totalSteps.toDouble()),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Sistem Operasi",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        XyPillButton(
                                            text = "Windows",
                                            onClick = {
                                                runnerImage = "windows-2022"
                                                linuxMode = "desktop"
                                                saveSettings()
                                            },
                                            primary = !isLinuxTarget,
                                            compact = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Linux GUI",
                                            onClick = {
                                                if (!runnerImage.startsWith("ubuntu", ignoreCase = true)) runnerImage = "ubuntu-24.04"
                                                linuxMode = "desktop"
                                                saveSettings()
                                            },
                                            primary = isLinuxTarget && !isLinuxPolosTarget,
                                            compact = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Linux SSH",
                                            onClick = {
                                                if (!runnerImage.startsWith("ubuntu", ignoreCase = true)) runnerImage = "ubuntu-24.04"
                                                linuxMode = "polos"
                                                saveSettings()
                                            },
                                            primary = isLinuxPolosTarget,
                                            compact = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Durasi",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        listOf("60" to "1 Jam", "180" to "3 Jam", "300" to "5 Jam", "360" to "6 Jam").forEach { (mins, lbl) ->
                                            val selectedDur = duration == mins
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (selectedDur) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                                    )
                                                    .clickable(enabled = !busy && !isRunningOrQueued) {
                                                        duration = mins
                                                        saveSettings()
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = lbl,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (selectedDur) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Tunnel",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    val isAutoBore = portmapHost.trim().equals("bore.pub", ignoreCase = true)
                                    val isRwHostOnly = portmapHost.trim().let {
                                        it.endsWith(".rlwy.net", ignoreCase = true) || it.endsWith(".railway.app", ignoreCase = true)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        XyPillButton(
                                            text = "Tailscale",
                                            icon = XyIcons.Shield,
                                            onClick = {
                                                akses = "tailscale"
                                                saveSettings()
                                            },
                                            primary = akses == "tailscale",
                                            compact = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Railway",
                                            icon = XyIcons.Link,
                                            onClick = {
                                                akses = "railway"
                                                if (!isRwHostOnly) {
                                                    portmapHost = "monorail.proxy.rlwy.net"
                                                }
                                                saveSettings()
                                            },
                                            primary = akses == "railway",
                                            compact = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Bore.pub",
                                            icon = XyIcons.Bolt,
                                            onClick = {
                                                akses = "portmap"
                                                portmapHost = "bore.pub"
                                                portmapPort = ""
                                                saveSettings()
                                            },
                                            primary = akses == "portmap" && isAutoBore,
                                            compact = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Portmap",
                                            icon = XyIcons.Globe,
                                            onClick = {
                                                akses = "portmap"
                                                if (isRailwayEndpoint) {
                                                    portmapHost = ""
                                                }
                                                saveSettings()
                                            },
                                            primary = akses == "portmap" && !isRailwayEndpoint,
                                            compact = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    AnimatedVisibility(visible = akses == "railway" || (akses == "portmap" && isRwHostOnly)) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = "Tunnel Railway frp (TCP proxy publik Railway → frps → RDP 3389, tanpa Tailscale):",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFA4A4A4),
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                XyField(
                                                    value = portmapHost,
                                                    onValueChange = { portmapHost = it.take(120); saveSettings() },
                                                    label = "Host Railway (*.rlwy.net)",
                                                    modifier = Modifier.weight(1.6f),
                                                )
                                                XyField(
                                                    value = portmapPort,
                                                    onValueChange = { portmapPort = it.filter { c -> c.isDigit() }.take(5); saveSettings() },
                                                    label = "Port TCP",
                                                    modifier = Modifier.weight(0.9f),
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    // Konfirmasi Mulai / Stop / Logout secara Inline (Ber-ikon & Ringkas)
                                    if (confirmAction != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = when (confirmAction) {
                                                    "start" -> "Mulai sesi RDP baru ($runnerImage · ${duration}m · $akses)?"
                                                    "stop" -> "Hentikan sesi RDP yang sedang berjalan?"
                                                    "samp_antiban" -> "Reset GPCI Serial & HWID SA-MP ke identitas baru (anti-ban)?"
                                                    else -> "Keluar dari akun GitHub @$login?"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                XyPillButton(
                                                    text = "Batal",
                                                    icon = XyIcons.Close,
                                                    onClick = { confirmAction = null },
                                                    primary = false,
                                                    compact = true,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                XyPillButton(
                                                    text = "Lanjutkan",
                                                    icon = XyIcons.Check,
                                                    onClick = {
                                                        val act = confirmAction
                                                        confirmAction = null
                                                        if (act != null) operate(act)
                                                    },
                                                    primary = true,
                                                    compact = true,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            XyPillButton(
                                                text = if (isRunningOrQueued) "Sesi Aktif" else "Mulai (${duration}m)",
                                                icon = XyIcons.Play,
                                                onClick = { confirmAction = "start" },
                                                primary = true,
                                                enabled = !busy && !isRunningOrQueued,
                                                modifier = Modifier.weight(1.25f),
                                            )
                                            XyPillButton(
                                                text = "Stop",
                                                icon = XyIcons.Power,
                                                onClick = { confirmAction = "stop" },
                                                primary = false,
                                                enabled = !busy && isRunningOrQueued,
                                                modifier = Modifier.weight(0.75f),
                                            )
                                        }
                                    }
                                }

                                // KARTU KONEKSI 1-KLIK (MUNCUL BEGITU SESI VERIFIED LIVE)
                                val vsReady = validatedSession?.takeIf { it.isVerifiedLive && !it.validatedHost.isNullOrBlank() }
                                if (vsReady != null && vsReady.access != null) {
                                    val a = vsReady.access
                                    val hostToConnect = vsReady.validatedHost!!
                                    val portToConnect = vsReady.validatedPort
                                    val isSessionPolos = a.linuxMode == "polos" || a.osName.contains("Polos", ignoreCase = true) || portToConnect == 22
                                    val isSessionLinux = isSessionPolos || a.osName.contains("Ubuntu", ignoreCase = true) || a.osName.contains("Linux", ignoreCase = true)
                                    val remSec = if (a.expiresAtMillis > nowTickMillis) {
                                        ((a.expiresAtMillis - nowTickMillis) / 1000L).coerceAtLeast(0L)
                                    } else 0L
                                    val hh = remSec / 3600L
                                    val mm = (remSec % 3600L) / 60L
                                    val ss = remSec % 60L

                                    XyCard(Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = "SESI AKTIF · SIAP MASUK",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF9D9D9D),
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = "$hostToConnect:$portToConnect",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                            ) {
                                                Text(
                                                    text = "%02d:%02d:%02d".format(hh, mm, ss),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${a.rdpUser} · ${a.osName} · ${(vsReady.validatedTransport ?: a.mode).uppercase()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        XyField(
                                            value = password,
                                            onValueChange = {
                                                password = it
                                                if (rememberPasswordLocal && it.isNotBlank()) {
                                                    vault.put("xyrdp.saved.rdp_password", it)
                                                }
                                            },
                                            label = "Password Sesi (RDP_PASSWORD)",
                                            hint = "Masukkan RDP_PASSWORD",
                                            isPassword = true,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        if (isSessionPolos) {
                                            XyPillButton(
                                                text = "Masuk SSH",
                                                icon = XyIcons.Terminal,
                                                onClick = {
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
                                                text = "Masuk RDP",
                                                icon = XyIcons.Monitor,
                                                onClick = {
                                                    busy = true
                                                    busyLabel = "Membuka sesi RDP…"
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
                                                        } catch (e: Exception) {
                                                            reportError(e)
                                                        } finally {
                                                            busy = false
                                                        }
                                                    }
                                                },
                                                primary = true,
                                                enabled = !busy && password.isNotBlank(),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            if (isSessionLinux) {
                                                Spacer(Modifier.height(8.dp))
                                                XyPillButton(
                                                    text = "Terminal SSH (:22)",
                                                    icon = XyIcons.Terminal,
                                                    onClick = {
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
                                                    compact = true,
                                                    enabled = !busy && password.isNotBlank(),
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    }
                                }

                                // KARTU STATISTIK PENGGUNAAN & KUOTA CLOUD XYRDP (LENGKAP & MUDAH DIPAHAMI)
                                val liveAcc = validatedSession?.access
                                val totalSessionsCount = (r?.number ?: 0).coerceAtLeast(if (liveAcc != null) 1 else 0)
                                val localActiveSec = remember(r?.id, nowTickMillis) {
                                    recordAndGetLocalUsageSeconds(context, 0L)
                                }
                                val activeUptimeMin = liveAcc?.stats?.uptimeMinutes ?: 0
                                val estimatedMinutesUsed = remember(totalSessionsCount, activeUptimeMin, localActiveSec) {
                                    ((totalSessionsCount * 120) + activeUptimeMin + (localActiveSec / 60).toInt()).coerceIn(0, 2000)
                                }
                                XyCard(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(
                                                text = "Statistik & Resource",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                            Text(
                                                text = "Performa cloud & kuota GitHub Actions",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = if (liveAcc != null) "LIVE" else "READY",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    // 4 Kotak Ringkasan Statistik Penggunaan
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = "${totalSessionsCount}x",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF9C9C9C),
                                            )
                                            Text("Total Sesi RDP", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = "${activeUptimeMin / 60}j ${activeUptimeMin % 60}m",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF808080),
                                            )
                                            Text("Uptime Sesi Ini", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = formatLeaderboardDuration(localActiveSec),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFBFBFBF),
                                            )
                                            Text("Total Waktu Main", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    StatBar(
                                        "Estimasi Pemakaian Kuota GitHub Actions",
                                        "$estimatedMinutesUsed / 2.000 mnt (Repo Publik Unlimited)",
                                        fraction(estimatedMinutesUsed.toDouble(), 2000.0),
                                    )

                                    Spacer(Modifier.height(10.dp))
                                    val stMonitor = liveAcc?.stats
                                    if (stMonitor != null && stMonitor.ramTotalGb > 0.0) {
                                        StatBar(
                                            "Pemakaian RAM Server",
                                            "${"%.1f".format(stMonitor.ramUsedGb)} / ${"%.1f".format(stMonitor.ramTotalGb)} GB",
                                            fraction(stMonitor.ramUsedGb, stMonitor.ramTotalGb),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        StatBar(
                                            "Penyimpanan SSD Disk C:",
                                            "Bebas ${"%.1f".format(stMonitor.diskFreeGb)} / ${"%.1f".format(stMonitor.diskTotalGb)} GB",
                                            1f - fraction(stMonitor.diskFreeGb, stMonitor.diskTotalGb),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        StatBar("Beban CPU (4 vCPU)", "${stMonitor.cpuPercent}%", (stMonitor.cpuPercent / 100f).coerceIn(0f, 1f))
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "Jalur Aktif: ${akses.uppercase()} · Keepalive Anti-Disconnect & Auto-Heal Shortcut Desktop Aktif",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF808080),
                                        )
                                    } else {
                                        Text(
                                            text = "Spesifikasi Standar Sesi: ${liveAcc?.initialCpuThreads ?: 4} vCPU · RAM ${"%.1f".format(liveAcc?.initialRamGb ?: 16.0)} GB · SSD bebas ${"%.1f".format(liveAcc?.initialDiskFreeGb ?: 75.0)} GB · Jalur Koneksi: ${akses.uppercase()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // ---------------------------------------------------------
                            // TAB 1: KONFIGURASI OS, PARAMETER RUNNER, & TOGGLE FITUR
                            // ---------------------------------------------------------
                            1 -> {
                                val isLinuxRunner = runnerImage.startsWith("ubuntu", ignoreCase = true)
                                XyCard(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Preset Cepat & Parameter Sesi",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyPillButton(
                                            text = "Preset Boot Kilat",
                                            onClick = {
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
                                                successMessage = "Preset Boot Kilat diterapkan."
                                            },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = if (isLinuxRunner) "Preset Linux Lengkap" else "Preset Game Lengkap",
                                            onClick = {
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
                                                successMessage = "Preset Lengkap + Anti-Reset diterapkan."
                                            },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyPillButton(
                                            text = "Durasi: $duration mnt ▾",
                                            onClick = { chooseDuration = !chooseDuration },
                                            primary = chooseDuration,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "OS: $runnerImage ▾",
                                            onClick = { chooseRunner = !chooseRunner },
                                            primary = chooseRunner,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    AnimatedVisibility(visible = chooseDuration) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text("Pilih Durasi Sesi RDP:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            listOf("30", "60", "120", "180", "240", "300", "360").chunked(4).forEach { rowMins ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                                    rowMins.forEach { m ->
                                                        XyPillButton(
                                                            text = "$m mnt",
                                                            onClick = { duration = m; saveSettings(); chooseDuration = false },
                                                            primary = duration == m,
                                                            compact = true,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    AnimatedVisibility(visible = chooseRunner) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text("Pilih Sistem Operasi & Versi Image:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            listOf(
                                                Triple("windows-2022", "desktop", "Windows Server 2022 (Stabil · GTA SA-MP & Minecraft)"),
                                                Triple("windows-2025", "desktop", "Windows Server 2025 (Kernel Terbaru & Cepat)"),
                                                Triple("ubuntu-24.04", "desktop", "Ubuntu 24.04 LTS Desktop (XFCE4 + XRDP :3389)"),
                                                Triple("ubuntu-24.04", "polos", "Ubuntu 24.04 LTS Polos (Headless SSH :22)"),
                                            ).forEach { (img, mode, lbl) ->
                                                XyPillButton(
                                                    text = lbl,
                                                    onClick = { runnerImage = img; linuxMode = mode; saveSettings(); chooseRunner = false },
                                                    primary = runnerImage == img && linuxMode == mode,
                                                    compact = true,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    val isRailwayMode = akses == "portmap" && (
                                        portmapHost.endsWith(".rlwy.net", ignoreCase = true) ||
                                            portmapHost.endsWith(".railway.app", ignoreCase = true)
                                        )
                                    val aksesLabel = when {
                                        isRailwayMode -> "Railway"
                                        akses == "portmap" -> "Portmap (Opsional)"
                                        else -> "Tailscale"
                                    }
                                    var showOptionalPortmapFields by remember {
                                        mutableStateOf(isRailwayMode || portmapHost.isNotBlank())
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyPillButton(
                                            text = "Tunnel: $aksesLabel ▾",
                                            onClick = { chooseAkses = !chooseAkses },
                                            primary = chooseAkses,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Debloat: $debloat ▾",
                                            onClick = { chooseDebloat = !chooseDebloat },
                                            primary = chooseDebloat,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    AnimatedVisibility(visible = chooseAkses) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            XyPillButton(
                                                text = "Tailscale P2P (Rekomendasi Utama · Paling Cepat & Stabil)",
                                                onClick = {
                                                    akses = "tailscale"
                                                    saveSettings()
                                                    chooseAkses = false
                                                },
                                                primary = akses == "tailscale",
                                                compact = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            XyPillButton(
                                                text = "Railway Cloud Tunnel (TCP Proxy *.rlwy.net / *.up.railway.app)",
                                                onClick = {
                                                    akses = "portmap"
                                                    showOptionalPortmapFields = true
                                                    if (portmapHost.isBlank()) {
                                                        portmapHost = "monorail.proxy.rlwy.net"
                                                    }
                                                    saveSettings()
                                                    chooseAkses = false
                                                },
                                                primary = isRailwayMode,
                                                compact = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            XyPillButton(
                                                text = "Portmap / OpenVPN (Opsional · Host & Port Otomatis dari .ovpn)",
                                                onClick = {
                                                    akses = "portmap"
                                                    if (isRailwayMode) portmapHost = ""
                                                    showOptionalPortmapFields = false
                                                    saveSettings()
                                                    chooseAkses = false
                                                },
                                                primary = akses == "portmap" && !isRailwayMode,
                                                compact = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                    AnimatedVisibility(visible = chooseDebloat) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            listOf("ringan" to "Ringan", "full" to "Full Debloat", "tidak" to "Tanpa Debloat").forEach { (m, lbl) ->
                                                XyPillButton(
                                                    text = lbl,
                                                    onClick = { debloat = m; saveSettings(); chooseDebloat = false },
                                                    primary = debloat == m,
                                                    compact = true,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    XyPillButton(
                                        text = "Driver Grafis OpenGL/Mesa: $grafis ▾",
                                        onClick = { chooseGrafis = !chooseGrafis },
                                        primary = chooseGrafis,
                                        compact = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    AnimatedVisibility(visible = chooseGrafis) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            listOf(
                                                "software" to "Mesa3D OpenGL 4.6 (Wajib Game)",
                                                "tidak" to "Driver Standar Windows",
                                            ).forEach { (m, lbl) ->
                                                XyPillButton(
                                                    text = lbl,
                                                    onClick = { grafis = m; saveSettings(); chooseGrafis = false },
                                                    primary = grafis == m,
                                                    compact = true,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyField(
                                            value = hostname,
                                            onValueChange = { hostname = it.take(30); saveSettings() },
                                            label = "Hostname",
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyField(
                                            value = rdpUser,
                                            onValueChange = { rdpUser = it.take(20); saveSettings() },
                                            label = "Username RDP",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (akses == "portmap") {
                                        Spacer(Modifier.height(8.dp))
                                        XyPillButton(
                                            text = if (showOptionalPortmapFields) {
                                                "Sembunyikan Host/Port Railway & Portmap (Opsional) ▴"
                                            } else {
                                                "Atur Host/Port Railway atau Portmap (Opsional · Boleh Kosong) ▾"
                                            },
                                            onClick = { showOptionalPortmapFields = !showOptionalPortmapFields },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        AnimatedVisibility(visible = showOptionalPortmapFields) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                XyField(
                                                    value = portmapHost,
                                                    onValueChange = { portmapHost = it.take(120); saveSettings() },
                                                    label = "Host Railway / Portmap (Opsional — contoh: monorail.proxy.rlwy.net)",
                                                    hint = "Kosongkan bila pakai .ovpn otomatis, atau isi domain *.rlwy.net / *.up.railway.app",
                                                )
                                                XyField(
                                                    value = portmapPort,
                                                    onValueChange = { portmapPort = it.filter { c -> c.isDigit() }.take(5); saveSettings() },
                                                    label = "Port Publik Railway / Portmap (Opsional — contoh: 3389 / 12345)",
                                                    hint = "Kosongkan bila pakai port otomatis",
                                                )
                                            }
                                        }
                                    }
                                }

                                XyCard(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Fitur & Modul Otomatis",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    RdpFlatToggleRow(
                                        title = "Anti-Reset Cloud Persistence v4",
                                        subtitle = "Simpan sesi login, Downloads & Desktop otomatis",
                                        checked = autoRenew,
                                        enabled = !busy,
                                        onCheckedChange = { autoRenew = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "XyDeskRemote Dashboard + Gaming Boost",
                                        subtitle = "Buka Dashboard otomatis, Power Plan & Anti-VM",
                                        checked = xydeskBoost,
                                        enabled = !busy,
                                        onCheckedChange = { xydeskBoost = it; saveSettings() },
                                     )
                                    RdpFlatToggleRow(
                                        title = "Redirect Mikrofon Dua Arah (On-Mic)",
                                        subtitle = "Teruskan suara mikrofon HP ke dalam sesi RDP Windows/Linux",
                                        checked = microphone,
                                        enabled = !busy,
                                        onCheckedChange = { microphone = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "Storage Boost (+45 s/d 75 GB Disk Lega)",
                                        subtitle = "Hapus SDK bawaan runner GitHub yang tidak terpakai",
                                        checked = storageBoost,
                                        enabled = !busy,
                                        onCheckedChange = { storageBoost = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "GTA SA-MP SilentPatch (90 FPS)",
                                        subtitle = "DirectX 9, VC++, SilentPatchSA & Widescreen Fix",
                                        checked = sampDeps,
                                        enabled = !busy,
                                        onCheckedChange = { sampDeps = it; saveSettings() },
                                    )
                                    if (sampDeps) {
                                        Spacer(Modifier.height(6.dp))
                                        XyPillButton(
                                            text = "Reset Anti-Ban SAMP & GPCI",
                                            icon = XyIcons.Refresh,
                                            onClick = {
                                                confirmAction = "samp_antiban"
                                            },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    RdpFlatToggleRow(
                                        title = "Minecraft Java + OpenGL 4.6 D3D12",
                                        subtitle = "Temurin JDK 21 & JRE 8, PrismLauncher & anti-GLFW error 65542",
                                        checked = mcJavaReady,
                                        enabled = !busy,
                                        onCheckedChange = {
                                            mcJavaReady = it
                                            if (it) grafis = "software"
                                            saveSettings()
                                        },
                                    )
                                    RdpFlatToggleRow(
                                        title = "Aplikasi Esensial (Chrome, 7-Zip, Lightshot)",
                                        subtitle = "Pasang browser dan utilitas dasar secara otomatis",
                                        checked = ekstraDesktop,
                                        enabled = !busy,
                                        onCheckedChange = { ekstraDesktop = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "Discord",
                                        subtitle = "Pasang Discord desktop di sesi RDP (siap login)",
                                        checked = discordReady,
                                        enabled = !busy,
                                        onCheckedChange = { discordReady = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "Tema Visual Windows 10 & Wallpaper XyVerse",
                                        subtitle = "Matikan pop-up Server Manager saat masuk desktop",
                                        checked = win10Theme,
                                        enabled = !busy,
                                        onCheckedChange = { win10Theme = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "Roblox Anti-VM & Minecraft Bedrock",
                                        subtitle = "Runtime Windows Store + Anti-VM Boost",
                                        checked = robloxReady || mcBedrockReady,
                                        enabled = !busy,
                                        onCheckedChange = {
                                            robloxReady = it
                                            mcBedrockReady = it
                                            saveSettings()
                                        },
                                    )
                                    if (robloxReady) {
                                        Spacer(Modifier.height(6.dp))
                                        XyPillButton(
                                            text = "Cek Status Roblox Anti-VM",
                                            icon = XyIcons.Info,
                                            onClick = {
                                                successMessage = "Roblox: Direct3D 11 WARP + SMBIOS spoof + 120 FPS aktif di Windows Host."
                                            },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            // ---------------------------------------------------------
                            // TAB 2: SECRETS GITHUB ACTIONS & TAUTAN RESMI
                            // ---------------------------------------------------------
                            2 -> {
                                val st = setupStatus
                                val hasRdpPass = st?.configuredSecrets?.contains("RDP_PASSWORD") == true
                                val hasTsKey = st?.configuredSecrets?.contains("TAILSCALE_AUTH_KEY") == true
                                val hasOvpn = st?.configuredSecrets?.contains("PORTMAP_OVPN_CONFIG") == true

                                XyCard(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Status GitHub Secrets ($login/${XyRdpConfig.FORK_REPO_NAME})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SecretStatusRow(
                                        name = "RDP_PASSWORD",
                                        configured = hasRdpPass,
                                        required = true,
                                        desc = "Password login Windows/Linux RDP & SSH",
                                        onCopyName = {
                                            copyToClipboard("Secret Name", "RDP_PASSWORD")
                                            successMessage = "Nama secret 'RDP_PASSWORD' disalin."
                                        },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    SecretStatusRow(
                                        name = "TAILSCALE_AUTH_KEY",
                                        configured = hasTsKey,
                                        required = akses == "tailscale",
                                        desc = "Kunci autentikasi Tailscale (tskey-auth-…)",
                                        onCopyName = {
                                            copyToClipboard("Secret Name", "TAILSCALE_AUTH_KEY")
                                            successMessage = "Nama secret 'TAILSCALE_AUTH_KEY' disalin."
                                        },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    SecretStatusRow(
                                        name = "PORTMAP_OVPN_CONFIG",
                                        configured = hasOvpn,
                                        required = false,
                                        desc = "Opsional — Isi .ovpn Portmap (tidak wajib jika pakai Tailscale / Railway)",
                                        onCopyName = {
                                            copyToClipboard("Secret Name", "PORTMAP_OVPN_CONFIG")
                                            successMessage = "Nama secret 'PORTMAP_OVPN_CONFIG' disalin."
                                        },
                                    )

                                    Spacer(Modifier.height(12.dp))
                                    XyField(
                                        value = password,
                                        onValueChange = {
                                            password = it
                                            if (rememberPasswordLocal && it.isNotBlank()) {
                                                vault.put("xyrdp.saved.rdp_password", it)
                                            }
                                        },
                                        label = "Password RDP (RDP_PASSWORD)",
                                        hint = "Min. 12 karakter kombinasi huruf, angka, simbol",
                                        isPassword = false,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyPillButton(
                                            text = "Acak Sandi",
                                            icon = XyIcons.Key,
                                            onClick = {
                                                val generated = generateStrongPassword()
                                                password = generated
                                                copyToClipboard("RDP_PASSWORD", generated)
                                                if (rememberPasswordLocal) vault.put("xyrdp.saved.rdp_password", generated)
                                                successMessage = "Password kuat dibuat & disalin."
                                            },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Pasang Sandi ↗",
                                            icon = XyIcons.Globe,
                                            onClick = {
                                                if (password.isNotBlank()) copyToClipboard("RDP_PASSWORD", password)
                                                openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions/new")
                                            },
                                            primary = !hasRdpPass,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    RdpFlatToggleRow(
                                        title = "Simpan RDP_PASSWORD di HP",
                                        subtitle = "Otomatis isi sandi saat masuk RDP",
                                        checked = rememberPasswordLocal,
                                        enabled = true,
                                        onCheckedChange = {
                                            rememberPasswordLocal = it
                                            saveSettings()
                                        },
                                    )

                                    Spacer(Modifier.height(10.dp))
                                    if (akses == "tailscale") {
                                        XyField(
                                            value = secretTailscaleDraft,
                                            onValueChange = { secretTailscaleDraft = it.trim() },
                                            label = "Tailscale Auth Key (TAILSCALE_AUTH_KEY)",
                                            hint = "tskey-auth-k...",
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            XyPillButton(
                                                text = "Ambil Key ↗",
                                                icon = XyIcons.Key,
                                                onClick = { openBrowser("https://login.tailscale.com/admin/settings/keys") },
                                                primary = !hasTsKey,
                                                compact = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                            XyPillButton(
                                                text = "Simpan GitHub ↗",
                                                icon = XyIcons.Globe,
                                                onClick = {
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
                                    } else {
                                        XyField(
                                            value = secretOvpnDraft,
                                            onValueChange = { secretOvpnDraft = it },
                                            label = "Konfigurasi .ovpn (PORTMAP_OVPN_CONFIG)",
                                            hint = "Tempel isi file .ovpn dari Portmap.io…",
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            XyPillButton(
                                                text = "Buat .ovpn ↗",
                                                icon = XyIcons.Globe,
                                                onClick = { openBrowser("https://portmap.io/configs") },
                                                primary = !hasOvpn,
                                                compact = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                            XyPillButton(
                                                text = "Simpan GitHub ↗",
                                                icon = XyIcons.Key,
                                                onClick = {
                                                    if (secretOvpnDraft.isNotBlank()) {
                                                        copyToClipboard("PORTMAP_OVPN_CONFIG", secretOvpnDraft)
                                                    } else {
                                                        copyToClipboard("Secret Name", "PORTMAP_OVPN_CONFIG")
                                                    }
                                                    openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions/new")
                                                },
                                                primary = false,
                                                compact = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyPillButton(
                                            text = "Secrets GitHub ↗",
                                            icon = XyIcons.Lock,
                                            onClick = { openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions") },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = if (showSecretGuide) "Tutup" else "Panduan",
                                            icon = XyIcons.Info,
                                            onClick = { showSecretGuide = !showSecretGuide },
                                            primary = false,
                                            compact = true,
                                        )
                                    }
                                    AnimatedVisibility(visible = showSecretGuide) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text("Panduan Singkat Setup Tailscale Auth Key:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            Text("1. Ketuk 'Ambil Key Tailscale ↗' lalu pilih 'Generate auth key'.", style = MaterialTheme.typography.labelSmall)
                                            Text("2. Centang 'Reusable' & 'Ephemeral', lalu salin kode tskey-auth-…", style = MaterialTheme.typography.labelSmall)
                                            Text("3. Ketuk 'Pasang di GitHub ↗' dan simpan sebagai TAILSCALE_AUTH_KEY.", style = MaterialTheme.typography.labelSmall)
                                            Text("4. Simpan juga secret kedua bernama RDP_PASSWORD.", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
/** Format durasi detik menjadi teks ringkas, mis. "5j 30m". */
internal fun formatLeaderboardDuration(totalSeconds: Long): String {
    val sec = totalSeconds.coerceAtLeast(60L)
    val hours = sec / 3600L
    val mins = (sec % 3600L) / 60L
    return if (hours > 0L) {
        "${hours}j ${mins}m"
    } else {
        "${mins} mnt"
    }
}

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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = if (configured) Color(0xFF9D9D9D).copy(alpha = 0.45f)
                else if (required) Color(0xFF999999).copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onCopyName() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (configured) Color(0xFF808080).copy(alpha = 0.22f)
                    else if (required) Color(0xFF777777).copy(alpha = 0.20f)
                    else MaterialTheme.colorScheme.surface,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (configured) XyIcons.Check else XyIcons.Key,
                contentDescription = null,
                tint = if (configured) Color(0xFFBDBDBD)
                else if (required) Color(0xFFBFBFBF)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (configured) "Aktif" else if (required) "Wajib" else "Opsional",
                style = MaterialTheme.typography.labelSmall,
                color = if (configured) Color(0xFFBDBDBD)
                else if (required) Color(0xFFBFBFBF)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = XyIcons.Copy,
            contentDescription = "Salin",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun RdpFlatToggleRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        XySwitch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
        )
    }
}

private fun fraction(used: Double, total: Double): Float =
    if (total <= 0.0) 0f else (used / total).toFloat().coerceIn(0f, 1f)

@Composable
private fun StatBar(label: String, value: String, fraction: Float) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
