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
            val setup = XyRdpClient.inspectSetup(tok, user, akses)
            setupStatus = setup
            forkReady = setup.forkExists
            if (setup.forkExists) {
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
            title = "Buat RDP Cloud",
            onBack = onBack,
            actions = {
                if (token != null && login != null) {
                    XyPillButton(
                        text = "Segarkan",
                        onClick = {
                            val tok = token ?: return@XyPillButton
                            val usr = login ?: return@XyPillButton
                            scope.launch {
                                busy = true
                                busyLabel = "Memperbarui status repository & sesi…"
                                try {
                                    refreshAll(tok, usr)
                                    successMessage = "Status sesi & telemetri diperbarui."
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
                                XyUserRoleBadges(name = login, githubLogin = login, compact = true)
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
                                text = "Sinkron",
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
                            title = "Fork Repository XyRDP",
                            subtitle = "Buat fork template XyRDP ke akun @$login untuk mulai.",
                            icon = XyIcons.Monitor,
                            actionLabel = "Fork XyRDP",
                            onAction = {
                                busy = true
                                busyLabel = "Membuat fork…"
                                scope.launch {
                                    try {
                                        if (XyRdpClient.createFork(token!!)) {
                                            delay(2000)
                                            XyRdpClient.enableWorkflow(token!!, login!!)
                                            refreshAll(token!!, login!!)
                                            successMessage = "Fork siap digunakan"
                                        } else {
                                            errorMessage = "Gagal membuat fork XyRDP."
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
                            options = listOf("Sesi", "Opsi", "Secrets"),
                            selectedIndex = activeTab,
                            onSelect = { activeTab = it },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        when (activeTab) {
                            // ---------------------------------------------------------
                            // TAB 0: SESI LIVE, KONTROL MULAI/STOP, & KONEKSI 1-KLIK
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
                                val hasRdpPass = st?.configuredSecrets?.contains("RDP_PASSWORD") == true
                                val hasAccessSecret = if (akses == "tailscale") {
                                    st?.configuredSecrets?.contains("TAILSCALE_AUTH_KEY") == true
                                } else {
                                    st?.configuredSecrets?.contains("PORTMAP_OVPN_CONFIG") == true
                                }
                                if (st != null && (!hasRdpPass || !hasAccessSecret)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { activeTab = 2 }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = XyIcons.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Secrets belum lengkap · Ketuk untuk atur",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            imageVector = XyIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }

                                // Kartu Utama Status Sesi & Kontrol Cepat
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
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when (vs?.phase) {
                                                            XyRdpPhase.READY -> Color(0xFF10B981)
                                                            XyRdpPhase.PREPARING, XyRdpPhase.QUEUED -> Color(0xFFF59E0B)
                                                            XyRdpPhase.FAILED -> Color(0xFFEF4444)
                                                            else -> MaterialTheme.colorScheme.outlineVariant
                                                        },
                                                    ),
                                            )
                                            Text(
                                                text = vs?.phaseLabel ?: "Siap Mulai",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (r != null) {
                                            Text(
                                                text = "#${r.number}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    // Progress tahapan workflow bila sedang berjalan
                                    vs?.progress?.let { prog ->
                                        if (prog.totalSteps > 0) {
                                            Spacer(Modifier.height(8.dp))
                                            StatBar(
                                                prog.currentStep ?: "Booting",
                                                "${prog.completedSteps}/${prog.totalSteps}",
                                                fraction(prog.completedSteps.toDouble(), prog.totalSteps.toDouble()),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))

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
                                            text = "Linux XFCE",
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

                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyPillButton(
                                            text = if (isRunningOrQueued) "Berjalan" else "Mulai ($duration mnt)",
                                            onClick = { confirmAction = "start" },
                                            primary = true,
                                            enabled = !busy && !isRunningOrQueued,
                                            modifier = Modifier.weight(1.3f),
                                        )
                                        XyPillButton(
                                            text = "Stop",
                                            onClick = { confirmAction = "stop" },
                                            primary = false,
                                            enabled = !busy && isRunningOrQueued,
                                            modifier = Modifier.weight(0.7f),
                                        )
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
                                                    text = "SESI AKTIF · SIAP TERHUBUNG",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF34D399),
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = "$hostToConnect:$portToConnect",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold,
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
                                            text = "User: ${a.rdpUser} · OS: ${a.osName} · Jalur: ${vsReady.validatedTransport ?: a.mode}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            hint = "Masukkan RDP_PASSWORD untuk terhubung",
                                            isPassword = true,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        if (isSessionPolos) {
                                            XyPillButton(
                                                text = "Buka Terminal SSH (Linux Polos)",
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
                                                text = "Hubungkan ke Desktop RDP Sekarang",
                                                onClick = {
                                                    busy = true
                                                    busyLabel = "Memvalidasi endpoint & membuka sesi RDP…"
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
                                                    text = "Buka Terminal SSH Pendamping (:22)",
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

                                // TELEMETRI & MONITOR RESOURCE LIVE
                                val liveAcc = validatedSession?.access
                                if (liveAcc != null) {
                                    XyCard(Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Telemetri & Resource Server",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        val stMonitor = liveAcc.stats
                                        if (stMonitor != null && stMonitor.ramTotalGb > 0.0) {
                                            StatBar(
                                                "RAM",
                                                "${"%.1f".format(stMonitor.ramUsedGb)} / ${"%.1f".format(stMonitor.ramTotalGb)} GB",
                                                fraction(stMonitor.ramUsedGb, stMonitor.ramTotalGb),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            StatBar(
                                                "Disk C:",
                                                "Bebas ${"%.1f".format(stMonitor.diskFreeGb)} / ${"%.1f".format(stMonitor.diskTotalGb)} GB",
                                                1f - fraction(stMonitor.diskFreeGb, stMonitor.diskTotalGb),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            StatBar("CPU", "${stMonitor.cpuPercent}%", (stMonitor.cpuPercent / 100f).coerceIn(0f, 1f))
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = "Uptime: ${stMonitor.uptimeMinutes / 60}j ${stMonitor.uptimeMinutes % 60}m · Anti-Disconnect Keepalive Aktif",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Text(
                                                text = "${liveAcc.initialCpuThreads} vCPU · RAM ${"%.1f".format(liveAcc.initialRamGb)} GB · Disk bebas ${"%.1f".format(liveAcc.initialDiskFreeGb)} GB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
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
                                            text = "Durasi: $duration mnt",
                                            onClick = { chooseDuration = true },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Image: $runnerImage",
                                            onClick = { chooseRunner = true },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XyPillButton(
                                            text = "Akses: $akses",
                                            onClick = { chooseAkses = true },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Debloat: $debloat",
                                            onClick = { chooseDebloat = true },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    XyPillButton(
                                        text = "Driver Grafis OpenGL/Mesa: $grafis",
                                        onClick = { chooseGrafis = true },
                                        primary = false,
                                        compact = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
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
                                        XyField(portmapHost, { portmapHost = it.take(120); saveSettings() }, "Host Portmap (tanpa tcp://)")
                                        Spacer(Modifier.height(8.dp))
                                        XyField(portmapPort, { portmapPort = it.filter { c -> c.isDigit() }.take(5); saveSettings() }, "Port Publik Portmap")
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
                                        title = "Anti-Reset Cloud Persistence v3",
                                        subtitle = "Simpan otomatis sesi login Chrome/Edge, Desktop, Documents & lanjutkan sesi berikutnya",
                                        checked = autoRenew,
                                        enabled = !busy,
                                        onCheckedChange = { autoRenew = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "XyDesk Host Boost (120 FPS + XyDeskTools.exe)",
                                        subtitle = "Pasang otomatis XyDeskTools.exe di Desktop RDP, DWM 120Hz & AVC444",
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
                                        title = "GTA SA-MP SilentPatch (90 FPS + Mouse Fix)",
                                        subtitle = "DirectX 9, VC++, DirectPlay, SilentPatchSA & Widescreen Fix",
                                        checked = sampDeps,
                                        enabled = !busy,
                                        onCheckedChange = { sampDeps = it; saveSettings() },
                                    )
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
                                        title = "Tema Visual Windows 10 & Wallpaper XyVerse",
                                        subtitle = "Matikan pop-up Server Manager saat masuk desktop",
                                        checked = win10Theme,
                                        enabled = !busy,
                                        onCheckedChange = { win10Theme = it; saveSettings() },
                                    )
                                    RdpFlatToggleRow(
                                        title = "Kompatibilitas Roblox & Minecraft Bedrock",
                                        subtitle = "Siapkan dependensi runtime Windows Store / Roblox",
                                        checked = robloxReady || mcBedrockReady,
                                        enabled = !busy,
                                        onCheckedChange = {
                                            robloxReady = it
                                            mcBedrockReady = it
                                            saveSettings()
                                        },
                                    )
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
                                        required = akses == "portmap",
                                        desc = "Isi konfigurasi .ovpn untuk mode Portmap publik",
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
                                            text = "Buat Password Kuat",
                                            onClick = {
                                                val generated = generateStrongPassword()
                                                password = generated
                                                copyToClipboard("RDP_PASSWORD", generated)
                                                if (rememberPasswordLocal) vault.put("xyrdp.saved.rdp_password", generated)
                                                successMessage = "Password kuat dibuat & disalin ke clipboard."
                                            },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = "Pasang RDP_PASSWORD ↗",
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
                                        title = "Simpan RDP_PASSWORD Terenkripsi di HP",
                                        subtitle = "Otomatis mengisi password saat sesi RDP siap disambungkan",
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
                                                text = "Ambil Key Tailscale ↗",
                                                onClick = { openBrowser("https://login.tailscale.com/admin/settings/keys") },
                                                primary = !hasTsKey,
                                                compact = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                            XyPillButton(
                                                text = "Pasang di GitHub ↗",
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
                                                onClick = { openBrowser("https://portmap.io/configs") },
                                                primary = !hasOvpn,
                                                compact = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                            XyPillButton(
                                                text = "Pasang di GitHub ↗",
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
                                            text = "Halaman Secrets GitHub ↗",
                                            onClick = { openBrowser("https://github.com/$login/${XyRdpConfig.FORK_REPO_NAME}/settings/secrets/actions") },
                                            primary = false,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        XyPillButton(
                                            text = if (showSecretGuide) "Tutup Panduan" else "Panduan Cepat",
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

    if (chooseDuration) {
        XyOverlay(title = "Durasi Sesi RDP", onDismiss = { chooseDuration = false }) {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("15", "30", "60", "120", "180", "240", "300", "330", "360").forEach { minutes ->
                    XyPillButton(
                        "$minutes menit (${if (minutes == "360") "Maksimal 6 Jam" else "${minutes.toInt() / 60}j ${minutes.toInt() % 60}m"})",
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
                    "windows-2022" to "Windows Server 2022 (Stabil · GTA SA-MP & Minecraft)",
                    "windows-2025" to "Windows Server 2025 (Kernel Terbaru & Performa I/O Tinggi)",
                    "windows-latest" to "windows-latest (Image Windows Terbaru GitHub)",
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
                    "Linux Desktop (XFCE4 + XRDP :3389 · Boot ~90 Detik)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                listOf(
                    "ubuntu-24.04" to "Ubuntu 24.04 LTS Desktop (XFCE4 + XRDP + SSH :22)",
                    "ubuntu-22.04" to "Ubuntu 22.04 LTS Desktop (XFCE4 LTS Klasik)",
                    "ubuntu-latest" to "ubuntu-latest Desktop (Otomatis Image Terbaru)",
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
                    "Linux Polos Tanpa XFCE4 (Headless SSH VPS · Boot ~20 Detik)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                listOf(
                    "ubuntu-24.04" to "Ubuntu 24.04 LTS Polos (Server SSH Murni)",
                    "ubuntu-22.04" to "Ubuntu 22.04 LTS Polos (Server SSH Murni)",
                    "ubuntu-latest" to "ubuntu-latest Polos (Server SSH Murni)",
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
                    "tailscale" to "Tailscale (Jaringan Privat Paling Cepat & Stabil)",
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
        XyOverlay(title = "Driver Grafis OpenGL / Mesa", onDismiss = { chooseGrafis = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "software" to "Software (Mesa3D OpenGL 4.6 D3D12 — Wajib untuk Game)",
                    "tidak" to "Tidak (Gunakan driver bawaan Windows)",
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
        val isLin = runnerImage.startsWith("ubuntu", ignoreCase = true)
        val osDesc = when {
            isLin && linuxMode == "polos" -> "Linux Polos SSH ($runnerImage · Port :22)"
            isLin -> "Linux Desktop XFCE4 ($runnerImage · RDP :3389)"
            else -> "Windows Server RDP ($runnerImage · RDP :3389)"
        }
        XyDialog(
            title = when (action) {
                "start" -> "Mulai Sesi Cloud RDP?"
                "stop" -> "Hentikan Sesi Aktif?"
                else -> "Putuskan Akun GitHub?"
            },
            body = when (action) {
                "start" -> "Menjalankan $osDesc selama $duration menit di akun GitHub Anda. Kuota, biaya, dan kebijakan GitHub/provider berlaku; bukan server permanen. Pemulihan data bergantung backup workflow yang berhasil."
                "stop" -> "Meminta GitHub menghentikan run aktif. Pembatalan dapat menghentikan langkah backup; data yang belum tersimpan bisa hilang. Pastikan backup selesai sebelum Stop."
                else -> "Melepas token GitHub dari HP ini (repository dan sesi aktif di GitHub tetap aman)."
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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = if (configured) Color(0xFF34D399).copy(alpha = 0.45f)
                else if (required) Color(0xFFF87171).copy(alpha = 0.45f)
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
                    if (configured) Color(0xFF10B981).copy(alpha = 0.22f)
                    else if (required) Color(0xFFEF4444).copy(alpha = 0.20f)
                    else MaterialTheme.colorScheme.surface,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (configured) XyIcons.Check else XyIcons.Key,
                contentDescription = null,
                tint = if (configured) Color(0xFF6EE7B7)
                else if (required) Color(0xFFFCA5A5)
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
                color = if (configured) Color(0xFF6EE7B7)
                else if (required) Color(0xFFFCA5A5)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Salin",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
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
