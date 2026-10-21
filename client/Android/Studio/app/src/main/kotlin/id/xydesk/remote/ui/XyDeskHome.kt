@file:OptIn(ExperimentalMaterial3Api::class)

package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import id.xydesk.remote.ui.components.XyEmptyState
import id.xydesk.remote.ui.ssh.XySshTerminalScreen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import id.xydesk.remote.R
import id.xydesk.remote.core.ConnectionLog
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.RdpOptions
import id.xydesk.remote.core.WakeOnLan
import id.xydesk.remote.core.XyStreamProfile
import id.xydesk.remote.security.CrashLog
import id.xydesk.remote.sessions.SessionsRepository
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyDialog
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyDivider
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyNoticeHost
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.rememberXyNotice
import id.xydesk.remote.ui.components.XyLogo
import id.xydesk.remote.ui.components.XyGlassCard
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XyRow
import id.xydesk.remote.ui.components.XySectionLabel
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.components.XyWordmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seksi di drawer samping. Judul dua bahasa dibaca di [sectionTitle]. */
internal enum class XySection(val title: String, val titleEn: String) {
    PERANGKAT("Perangkat", "Devices"),
    TAMPILAN("Tampilan", "Display"),
    KREDENSIAL("Kredensial", "Credentials"),
    UMUM("Umum", "General"),
    KEAMANAN("Keamanan", "Security"),
    TENTANG("Tentang", "About"),
}

/** Judul seksi yang ikut bahasa aktif. */
@Composable
internal fun sectionTitle(section: XySection): String = xy(section.title, section.titleEn)

/** Layar yang sedang tampil. */
private sealed interface XyRoute {
    data object Devices : XyRoute
    data object CreateRdp : XyRoute
    data object SshTerminal : XyRoute
    data class EditDevice(val profile: ConnectionProfile?, val pcQuickMode: Boolean = false) : XyRoute
    data object Section : XyRoute

    /** Feed komunitas — layar sendiri, dipetakan ke tab nav [XyTab.FEED]. */
    data object Feed : XyRoute
    data object ChatGlobal : XyRoute

    /** Dashboard pengaturan — dipetakan ke tab nav [XyTab.PROFILE]. */
    data object Profile : XyRoute
}

/**
 * Home XyDesk: daftar perangkat + drawer seksi.
 *
 * Semua info yang ditampilkan sengaja tipis: nama perangkat, alamat, akun.
 * Preview pakai wallpaper motif OS perangkat (digambar prosedural, tanpa aset).
 */
private object XyHomeNavPersist {
    var section: XySection = XySection.PERANGKAT
    var tab: XyTab = XyTab.HOME
    var route: XyRoute = XyRoute.Devices
}

@Composable
fun XyDeskHome(
    onExit: () -> Unit,
    onReady: () -> Unit = {},
    sharedFileNotice: String? = null,
    autoResumeSignal: Int = 0,
    skipAutoResume: Boolean = false,
    openChatSignal: Int = 0,
    onDismissSharedNotice: () -> Unit = {},
    onAuthenticateConnect: (() -> Unit) -> Unit = { it() },
) {
    val context = LocalContext.current
    val repo = remember { SessionsRepository(context.applicationContext) }
    val favoritesFlow = remember { repo.favorites() }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    // Data siap = daftar perangkat sudah keluar sekali dari database; dipakai
    // splash supaya dia berhenti tepat waktu, bukan menebak-nebak.
    var dataReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        favoritesFlow.first()
        dataReady = true
    }
    LaunchedEffect(dataReady) { if (dataReady) onReady() }
    val scope = rememberCoroutineScope()
    val appPrefs = remember { AppPrefs(context) }
    var drawerOpen by remember { mutableStateOf(false) }
    var helpToolsOpen by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(XyHomeNavPersist.section) }
    // Tab nav bawah (4 tujuan). `section` tetap ada karena seksi dibuka dari
    // drawer maupun dari layar Profil, dan keduanya menggambar SectionScreen.
    var tab by remember { mutableStateOf(XyHomeNavPersist.tab) }
    var route by remember { mutableStateOf<XyRoute>(XyHomeNavPersist.route) }
    LaunchedEffect(section, tab, route) {
        XyHomeNavPersist.section = section
        XyHomeNavPersist.tab = tab
        XyHomeNavPersist.route = route
    }
    LaunchedEffect(openChatSignal) {
        if (openChatSignal > 0) {
            tab = XyTab.CHAT
            route = XyRoute.ChatGlobal
        }
    }
    var crashLog by remember { mutableStateOf(CrashLog.last(context.applicationContext)) }
    var showCrash by remember { mutableStateOf(false) }
    val bootTail = remember { ConnectionLog.tailFromFile(context.applicationContext, 20) }
    var showBoot by remember { mutableStateOf(false) }
    var feedbackOpen by remember { mutableStateOf(false) }
    // Pembaruan app: cek maksimal 12 jam sekali; dialog muncul bila portal
    // melaporkan versi lebih baru yang belum dilewati user.
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var dataPackProgress by remember { mutableStateOf<Int?>(null) }
    var dataPackStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        appPrefs.lastUpdateCheckAt = now
        val info = AppUpdate.fetch(context)
        if (info != null) {
            val current = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
            if (AppUpdate.isNewer(info.version, current) && appPrefs.updateSkipVersion != info.version) {
                updateInfo = info
            }
        }
    }
    // Ronde 54: Detak kehadiran seluruh aplikasi (App-Wide Presence Heartbeat)
    // agar jumlah Total Online menghitung seluruh orang yang sedang membuka app.
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            runCatching {
                AppUpdate.sendAppPresencePing(context, screen = "app", active = true)
                if (tick > 0) {
                    recordAndGetLocalUsageSeconds(context, 25L)
                }
                if (tick % 2 == 0) {
                    CommunityJokesApi(context).fetchLeaderboard(reportDeltaSec = 0L)
                }
                tick += 1
            }
            kotlinx.coroutines.delay(25_000L)
        }
    }
    var backupModalOpen by remember { mutableStateOf(false) }
    var backupJsonInput by remember { mutableStateOf("") }
    var confirmRestoreProfiles by remember { mutableStateOf<List<Pair<ConnectionProfile, Boolean>>?>(null) }
    var updateModalOpen by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var confirmDeleteDevice by remember { mutableStateOf<ConnectionProfile?>(null) }
    var confirmExitApp by remember { mutableStateOf(false) }
    var wolWaitingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    // Pesan app sendiri (bukan Toast bawaan Android).
    val notice = rememberXyNotice()

    fun connect(profile: ConnectionProfile) {
        onAuthenticateConnect {
            incrementLocalUsageSessionCount(context)
            scope.launch { repo.touch(profile) }
            context.startActivity(XyDeskSessionActivity.connectIntent(context, profile))
        }
    }

    LaunchedEffect(autoResumeSignal, skipAutoResume, dataReady) {
        if (autoResumeSignal <= 0) return@LaunchedEffect
        if (skipAutoResume) {
            // Home navigation and Share are deliberate; consume the marker so a
            // later lifecycle callback cannot unexpectedly reopen the session.
            appPrefs.clearAutoResumeSession()
            return@LaunchedEffect
        }
        if (!dataReady) return@LaunchedEffect
        if (!appPrefs.autoResumeLastSession) {
            appPrefs.clearAutoResumeSession()
            return@LaunchedEffect
        }

        val profileId = appPrefs.autoResumeProfileId
        val savedAt = appPrefs.autoResumeSavedAtMillis
        val now = System.currentTimeMillis()
        if (profileId.isNullOrBlank() || savedAt <= 0L || now < savedAt ||
            now - savedAt > AppPrefs.AUTO_RESUME_WINDOW_MS
        ) {
            appPrefs.clearAutoResumeSession(profileId)
            return@LaunchedEffect
        }

        val savedProfiles = favoritesFlow.first()
        val profile = savedProfiles.firstOrNull { it.id == profileId }
        if (profile == null) {
            appPrefs.clearAutoResumeSession(profileId)
            return@LaunchedEffect
        }

        // Consume before launching so a failed auth or Activity recreation cannot
        // create an auto-launch loop. Credentials are loaded only from the vault.
        appPrefs.clearAutoResumeSession(profileId)
        val existing = XySessionRegistry.list().firstOrNull { it.id == profileId }
        if (existing != null) existing.open() else connect(profile)
    }

    fun wakeAndConnect(profile: ConnectionProfile) {
        val opts = RdpOptions.of(context, profile.id)
        val mac = opts.macAddress?.trim().orEmpty()
        if (WakeOnLan.parseMacBytes(mac) == null) {
            notice.show(xyNow("MAC Address Wake-on-LAN belum diatur atau tidak valid", "Wake-on-LAN MAC address is missing or invalid"))
            return
        }
        wolWaitingProfile = profile
        scope.launch {
            val targets = listOf(opts.wolBroadcast.ifBlank { "255.255.255.255" }, profile.host).distinct()
            val sendRes = withContext(Dispatchers.IO) {
                runCatching {
                    targets.forEach { target ->
                        WakeOnLan.sendMagicPacket(mac, target, opts.wolPort)
                    }
                }
            }
            if (sendRes.isFailure) {
                wolWaitingProfile = null
                notice.show(
                    xyNow(
                        "Gagal mengirim paket Wake-on-LAN: ${sendRes.exceptionOrNull()?.message ?: "error"}",
                        "Failed to send Wake-on-LAN packet: ${sendRes.exceptionOrNull()?.message ?: "error"}",
                    ),
                )
                return@launch
            }
            notice.show(
                xyNow(
                    "Magic Packet terkirim ke ${profile.label ?: profile.host}. Menunggu port RDP aktif...",
                    "Magic Packet sent to ${profile.label ?: profile.host}. Waiting for RDP port...",
                ),
            )
            val ready = withContext(Dispatchers.IO) {
                WakeOnLan.pollHostReady(
                    host = profile.host,
                    port = profile.port,
                    timeoutMs = 35_000L,
                    intervalMs = 1_200L,
                )
            }
            if (wolWaitingProfile?.id == profile.id) {
                wolWaitingProfile = null
                if (ready) {
                    notice.show(xyNow("PC menyala! Membuka sesi RDP...", "PC is awake! Launching RDP session..."))
                    connect(profile)
                } else {
                    notice.show(
                        xyNow(
                            "Paket WoL sudah dikirim, tetapi port ${profile.port} belum merespons dalam 35 detik.",
                            "WoL packet sent, but port ${profile.port} did not respond within 35 seconds.",
                        ),
                    )
                }
            }
        }
    }

    fun submit(profile: ConnectionProfile, rememberPassword: Boolean, startNow: Boolean) {
        scope.launch {
            val saved = runCatching { repo.save(profile, rememberPassword) }
            if (saved.isFailure) {
                notice.show(
                    xyNow("Profil tidak tersimpan: ", "Profile not saved: ") +
                        (saved.exceptionOrNull()?.message ?: xyNow("kesalahan penyimpanan", "storage error")),
                )
            } else if (saved.getOrDefault(true) == false) {
                notice.show(
                    xyNow(
                        "Profil tersimpan, tapi password gagal disimpan aman. Isi ulang nanti; password tidak diingat.",
                        "Profile saved, but the password could not be stored securely. Re-enter it later; it was not remembered.",
                    ),
                )
            }
            route = XyRoute.Devices
            if (startNow) connect(profile)
        }
    }

    /**
     * Kirim masukan ke tujuan yang dipilih pengguna.
     *
     * Tidak ada tujuan yang di-hardcode ke alamat tertentu: repo ini tidak
     * menyimpan alamat dukungan, jadi pilihannya adalah pemilih aplikasi
     * Android (bebas ke aplikasi mana pun) atau salin draf ke clipboard
     * supaya pengguna menempelkannya sendiri.
     */
    fun sendFeedback(destination: FeedbackDestination, category: String, message: String) {
        feedbackOpen = false
        val draft = feedbackShareDraft(category, message)
        when (destination) {
            FeedbackDestination.COPY -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard == null) {
                    notice.show(xyNow("Clipboard tidak tersedia", "Clipboard is unavailable"))
                    return
                }
                runCatching {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("XyDesk Remote feedback", draft),
                    )
                }.onSuccess {
                    notice.show(
                        xyNow(
                            "Draf disalin. Tempel ke aplikasi mana pun.",
                            "Draft copied. Paste it into any app.",
                        ),
                    )
                }.onFailure {
                    notice.show(xyNow("Gagal menyalin draf", "Could not copy the draft"))
                }
            }

            FeedbackDestination.SHARE -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "XyDesk Remote feedback")
                    putExtra(Intent.EXTRA_TEXT, draft)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            sendIntent,
                            xyNow("Pilih aplikasi untuk berbagi", "Choose an app to share"),
                        ),
                    )
                }.onFailure {
                    notice.show(xyNow("Menu berbagi tidak tersedia", "Share menu is unavailable"))
                }
            }
        }
    }

    BackHandler {
        when {
            confirmRestoreProfiles != null -> confirmRestoreProfiles = null
            backupModalOpen -> backupModalOpen = false
            updateModalOpen -> updateModalOpen = false
            confirmDeleteDevice != null -> confirmDeleteDevice = null
            confirmExitApp -> confirmExitApp = false
            feedbackOpen -> feedbackOpen = false
            drawerOpen -> drawerOpen = false
            route != XyRoute.Devices || section != XySection.PERANGKAT -> {
                route = XyRoute.Devices
                section = XySection.PERANGKAT
                tab = XyTab.HOME
            }
            else -> confirmExitApp = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Kolom: konten mendapat sisa tinggi, nav bawah punya tinggi tetap
            // sehingga tidak menutupi isi layar.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().weight(1f)) {

            id.xydesk.remote.ui.theme.XyRouteMotion(target = route) { current ->
            when (current) {
                XyRoute.CreateRdp -> RdpFreeScreen(
                    onBack = { route = XyRoute.Devices },
                    onConnect = { connect(it) },
                )

                XyRoute.SshTerminal -> XySshTerminalScreen(
                    onBack = { route = XyRoute.Devices },
                )

                is XyRoute.EditDevice -> AddDeviceScreen(
                    existing = current.profile,
                    savedUsers = favorites.mapNotNull { it.username }.distinct(),
                    savedProfiles = favorites,
                    initialPcQuickMode = current.pcQuickMode,
                    onCancel = { route = XyRoute.Devices },
                    onSubmit = { profile, remember, startNow -> submit(profile, remember, startNow) },
                )

                XyRoute.Devices -> if (section == XySection.PERANGKAT) {
                    DevicesScreen(
                        favorites = favorites,
                        crashLog = crashLog,
                        bootTail = bootTail,
                        sharedFileNotice = sharedFileNotice,
                        onDismissSharedNotice = onDismissSharedNotice,
                        onMenu = { drawerOpen = true },
                        onAddRdp = { route = XyRoute.EditDevice(null, pcQuickMode = false) },
                        onAddPcQuick = { route = XyRoute.EditDevice(null, pcQuickMode = true) },
                        onOpenPcStream = { context.startActivity(PcStreamSessionActivity.intent(context)) },
                        onCreateRdp = { route = XyRoute.CreateRdp },
                        onOpenSshTerminal = { route = XyRoute.SshTerminal },
                        onEdit = {
                            route = XyRoute.EditDevice(
                                it,
                                pcQuickMode = RdpOptions.of(context, it.id).pcConnectMode,
                            )
                        },
                        onConnect = { connect(it) },
                        onWakeConnect = { wakeAndConnect(it) },
                        onDelete = { profile -> confirmDeleteDevice = profile },
                        onShowCrash = { showCrash = true },
                        onShowBoot = { showBoot = true },
                    )
                } else {
                    SectionScreen(
                        section = section,
                        favorites = favorites,
                        onMenu = { drawerOpen = true },
                        appPrefs = appPrefs,
                        onEditDevice = {
                            route = XyRoute.EditDevice(
                                it,
                                pcQuickMode = RdpOptions.of(context, it.id).pcConnectMode,
                            )
                        },
                        onClearAllCredentials = {
                            scope.launch {
                                repo.clear()
                                notice.show(
                                    xyNow(
                                        "Kredensial & perangkat dihapus",
                                        "Credentials and devices cleared",
                                    ),
                                )
                            }
                        },
                        onShowLog = { showBoot = true },
                    )
                }

                XyRoute.Feed -> XyFeedScreen(
                    onMenu = { drawerOpen = true },
                    onOpenChat = { tab = XyTab.CHAT; route = XyRoute.ChatGlobal },
                )

                XyRoute.ChatGlobal -> GlobalChatScreen(onBack = { tab = XyTab.HOME; route = XyRoute.Devices })
                XyRoute.Profile -> XyProfileScreen(
                    onMenu = { drawerOpen = true },
                    onOpenSection = {
                        section = it
                        route = XyRoute.Devices
                    },
                    appPrefs = appPrefs,
                    deviceCount = favorites.size,
                    onShowLog = { showBoot = true },
                    onOpenFeedback = { feedbackOpen = true },
                )

                XyRoute.Section -> Unit
            }
            }
                }

                // Nav bawah disembunyikan saat form tambah/ubah perangkat, Chat Global,
                // atau Terminal SSH terbuka supaya layar itu tetap penuh dan composer tidak tertutup.
                if (route !is XyRoute.EditDevice && route != XyRoute.ChatGlobal && route != XyRoute.SshTerminal) {
                    // Dock melayang (macOS/iOS style): tanpa garis pemisah lagi —
                    // kapsulnya sudah dipisahkan bayangan dari konten.
                    XyBottomNav(
                        selected = tab,
                        onSelect = { next ->
                            tab = next
                            when (next) {
                                XyTab.HOME -> {
                                    section = XySection.PERANGKAT
                                    route = XyRoute.Devices
                                }
                                XyTab.FEED -> route = XyRoute.Feed
                                XyTab.PROFILE -> route = XyRoute.Profile
                                XyTab.CHAT -> route = XyRoute.ChatGlobal
                            }
                        },
                    )
                }
            }
        }

    // Modal OAuth GitHub Langsung Tanpa Kode (tersedia global untuk Beranda, Buat RDP, Profil, & Chat Global)
    GitHubDirectOAuthModalHost()

    // In-App Notification Banner (Pengumuman, Balasan, Like, Reaksi, Mention, Chat, & OTA Data)
    XyInAppNotificationBannerHost(
        onOpenChat = { tab = XyTab.CHAT; route = XyRoute.ChatGlobal },
        onOpenFeed = {
            tab = XyTab.FEED
            route = XyRoute.Feed
        },
    )

    // ---- Menu utama: 2 gaya — popup tengah atau sidebar kanan ----
    val homeMenuStyle = XyMenuStyleState.current(appPrefs)
    XyHomeMenuShell(
        open = drawerOpen,
        style = homeMenuStyle,
        onDismiss = { drawerOpen = false },
    ) {
        XyHomeMenuBody(
            section = section,
            helpToolsOpen = helpToolsOpen,
            onToggleTools = { helpToolsOpen = !helpToolsOpen },
            onPick = { item ->
                section = item
                route = XyRoute.Devices
                // Tab ikut menyorot: seksi adalah bagian Profil, kecuali
                // Perangkat yang memang isi Beranda.
                tab = if (item == XySection.PERANGKAT) XyTab.HOME else XyTab.PROFILE
                drawerOpen = false
            },
            onBackup = {
                drawerOpen = false
                backupJsonInput = exportProfilesJson(context, favorites)
                backupModalOpen = true
            },
            onUpdate = {
                drawerOpen = false
                updateModalOpen = true
                updateStatusText = xyNow("Memeriksa status rilis terbaru dari www.xydeskremote.biz.id...", "Checking latest release status from www.xydeskremote.biz.id...")
                scope.launch {
                    val status = withContext(Dispatchers.IO) { fetchRemoteReleaseStatus(context) }
                    updateStatusText = status
                }
            },
            onFeedback = {
                drawerOpen = false
                feedbackOpen = true
            },
            version = "v${appVersion(context)}",
        )
    }

    if (showCrash) {
        InfoDialog(
            title = xy("Log error terakhir", "Last error log"),
            body = crashLog.orEmpty(),
            onDismiss = {
                showCrash = false
                CrashLog.clear(context.applicationContext)
                crashLog = null
            },
        )
    }
    if (showBoot) {
        // Ronde 48: seluruh log mesin telah diserap dan dirapikan ke dalam
        // Log XyDesk satu per satu (tidak ada lagi blok log FreeRDP terpisah).
        val body = remember(showBoot) {
            ConnectionLog.formattedXyDeskLog(context.applicationContext, 100)
        }
        LogSheet(
            title = xy("Log XyDesk", "XyDesk Log"),
            body = body,
            onDismiss = { showBoot = false },
        )
    }
    if (feedbackOpen) {
        FeedbackDialog(
            onDismiss = { feedbackOpen = false },
            onSend = ::sendFeedback,
        )
    }
    confirmDeleteDevice?.let { target ->
        XyDialog(
            title = xy("Hapus perangkat?", "Delete device?"),
            body = xy(
                "Hapus \"{0}\" beserta password dan pengaturannya?",
                "Delete \"{0}\" along with its saved password and settings?",
                target.label ?: "${target.host}:${target.port}",
            ),
            confirmLabel = xy("Hapus", "Delete"),
            onConfirm = {
                confirmDeleteDevice = null
                scope.launch {
                    repo.remove(target.id)
                    id.xydesk.remote.core.RdpOptions.clear(context, target.id)
                    DisplayPrefs.clear(context, target.id)
                    notice.show(xyNow("Perangkat dihapus", "Device deleted"))
                }
            },
            dismissLabel = xy("Batal", "Cancel"),
            onDismiss = { confirmDeleteDevice = null },
        )
    }
    updateInfo?.let { info ->
        XyOverlay(
            title = xy("Versi {0} tersedia", "Version {0} available", info.version),
            onDismiss = { updateInfo = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (info.changelog.isBlank()) {
                        xy("Pembaruan baru siap diunduh.", "A new update is ready to download.")
                    } else {
                        info.changelog.take(600)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                XyPillButton(
                    text = xy("Unduh sekarang", "Download now"),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(
                                        if (info.url.isNotBlank()) info.url
                                        else "https://www.xydeskremote.biz.id/",
                                    ),
                                ),
                            )
                        }
                        updateInfo = null
                    },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        text = xy("Nanti", "Later"),
                        onClick = { updateInfo = null },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        text = xy("Lewati versi ini", "Skip this version"),
                        onClick = {
                            appPrefs.updateSkipVersion = info.version
                            updateInfo = null
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    wolWaitingProfile?.let { target ->
        XyDialog(
            title = xy("Membangunkan PC (Wake-on-LAN)", "Waking PC (Wake-on-LAN)"),
            body = xy(
                "Magic Packet sudah dikirim ke \"{0}\" ({1}:{2}). XyDesk sedang memantau kesiapan port RDP otomatis hingga 35 detik...",
                "Magic Packet sent to \"{0}\" ({1}:{2}). XyDesk is automatically polling the RDP port for up to 35 seconds...",
                target.label ?: target.host,
                target.host,
                target.port,
            ),
            confirmLabel = xy("Konek Sekarang", "Connect Now"),
            onConfirm = {
                wolWaitingProfile = null
                connect(target)
            },
            dismissLabel = xy("Batal Tunggu", "Cancel Wait"),
            onDismiss = { wolWaitingProfile = null },
        )
    }

    if (confirmExitApp) {
        XyDialog(
            title = xy("Keluar dari XyDesk Remote?", "Exit XyDesk Remote?"),
            body = xy(
                "Tutup aplikasi sekarang? Sesi yang sedang aktif di latar akan tetap berjalan jika keep-alive menyala.",
                "Close the application now? Active background sessions will stay running while keep-alive is enabled.",
            ),
            confirmLabel = xy("Keluar", "Exit"),
            onConfirm = {
                confirmExitApp = false
                onExit()
            },
            dismissLabel = xy("Batal", "Cancel"),
            onDismiss = { confirmExitApp = false },
        )
    }

    if (backupModalOpen) {
        XyOverlay(
            title = xy("Cadangkan & Pulihkan Profil (JSON)", "Backup & Restore Profiles (JSON)"),
            onDismiss = { backupModalOpen = false },
        ) {
            Text(
                xy(
                    "Salin JSON di bawah untuk mencadangkan seluruh daftar Koneksi PC & Koneksi RDP Anda, atau tempel JSON cadangan lalu ketuk Pulihkan.",
                    "Copy the JSON below to back up all your PC Connect & RDP profiles, or paste a backup JSON and tap Restore.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XyField(
                value = backupJsonInput,
                onValueChange = { backupJsonInput = it },
                label = xy("Data JSON Profil Perangkat", "Device Profiles JSON Data"),
                hint = "{\"version\":1,\"profiles\":[...]}",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = xy("Salin Cadangan", "Copy Backup"),
                    onClick = {
                        val json = exportProfilesJson(context, favorites)
                        backupJsonInput = json
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("xydesk-profiles-backup", json))
                        notice.show(xyNow("Cadangan JSON disalin ke clipboard ({0} perangkat)", "Backup JSON copied to clipboard ({0} devices)", favorites.size))
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Pulihkan JSON", "Restore JSON"),
                    onClick = {
                        val parsed = importProfilesJson(backupJsonInput)
                        if (parsed.isEmpty()) {
                            notice.show(xyNow("Format JSON tidak valid atau kosong", "Invalid or empty JSON format"))
                        } else {
                            confirmRestoreProfiles = parsed
                        }
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            XyPillButton(
                text = xy("Tutup", "Close"),
                onClick = { backupModalOpen = false },
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    confirmRestoreProfiles?.let { listToRestore ->
        XyDialog(
            title = xy("Pulihkan {0} profil perangkat?", "Restore {0} device profiles?", listToRestore.size),
            body = xy(
                "Tambahkan / perbarui {0} profil perangkat dari data cadangan JSON sekarang?",
                "Add / update {0} device profiles from the JSON backup now?",
                listToRestore.size,
            ),
            confirmLabel = xy("Pulihkan", "Restore"),
            onConfirm = {
                val items = listToRestore
                confirmRestoreProfiles = null
                backupModalOpen = false
                scope.launch {
                    items.forEach { (prof, isPcMode) ->
                        runCatching {
                            repo.save(prof, rememberPassword = false)
                            val opts = RdpOptions.of(context, prof.id)
                            if (isPcMode) {
                                opts.withPcStreamEngine(opts.pcStreamEngine).write(context, prof.id)
                            } else {
                                opts.copy(pcConnectMode = false).write(context, prof.id)
                            }
                        }
                    }
                    notice.show(xyNow("{0} profil perangkat berhasil dipulihkan", "{0} device profiles restored", items.size))
                }
            },
            dismissLabel = xy("Batal", "Cancel"),
            onDismiss = { confirmRestoreProfiles = null },
        )
    }

    if (updateModalOpen) {
        XyOverlay(
            title = xy("Pusat Pembaruan (Download Data & APK)", "Update Center (Download Data & APK)"),
            onDismiss = { updateModalOpen = false },
        ) {
            Text(
                text = "XyDesk Remote v${appVersion(context)}  ·  Data OTA: ${AppUpdate.installedDataVersion(context)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = dataPackStatus ?: updateStatusText ?: xy(
                    "Update ringan cukup dengan Download Data di dalam HP (tanpa instal ulang APK). Update besar/native menggunakan APK.",
                    "Lightweight updates only need Download Data in-app (no APK reinstall). Major native updates use APK.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dataPackProgress?.let { pct ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Text(
                        "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            XyPillButton(
                text = xy("Download Data Update (OTA Ringan In-App)", "Download Data Update (Lightweight In-App OTA)"),
                onClick = {
                    scope.launch {
                        dataPackProgress = 5
                        val res = AppUpdate.downloadAndApplyDataPack(context) { pct, lbl ->
                            dataPackProgress = pct
                            dataPackStatus = lbl
                        }
                        res.onSuccess { ver ->
                            notice.show(xyNow("Paket Data OTA $ver berhasil diterapkan!", "OTA Data Pack $ver applied!"))
                        }.onFailure { err ->
                            dataPackStatus = err.message
                        }
                    }
                },
                primary = true,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = xy("Unduh APK Terbaru", "Download Latest APK"),
                    onClick = {
                        openInAppCustomTab(context, "https://api.xydeskremote.biz.id/XyDesk-Remote64bit.apk")
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Portal Rilis", "Release Portal"),
                    onClick = {
                        openInAppCustomTab(context, "https://www.xydeskremote.biz.id")
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            XyPillButton(
                text = xy("Tutup", "Close"),
                onClick = { updateModalOpen = false },
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Pesan app sendiri — paling atas supaya tidak ketutup drawer/panel.
    XyNoticeHost(state = notice, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Sesi yang sedang hidup di proses ini.
 *
 * Daftarnya dibaca dari [XySessionRegistry] dan disegarkan tiap 1,5 detik
 * selama home tampil — cukup untuk kasus "buka server kedua sambil yang
 * pertama tetap jalan".
 */
@Composable
private fun HomeRdpFreeBannerCard(
    onCreateRdp: () -> Unit,
    onOpenPcStream: () -> Unit,
    onOpenSshTerminal: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1280f / 440f)
                .clickable { onCreateRdp() },
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id.xydesk.remote.R.drawable.xyrdp_banner),
                contentDescription = "Buat RDP Cloud Gratis (XyRDP)",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF07090F).copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )
            Text(
                text = xy(
                    "Buat RDP Cloud Gratis · Windows Server · Linux XFCE4 · SSH · 120 FPS",
                    "Free Cloud RDP · Windows Server · Linux XFCE4 · SSH · 120 FPS",
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XyPillButton(
                text = xy("Buat RDP Gratis", "Create Free RDP"),
                onClick = onCreateRdp,
                icon = XyIcons.Monitor,
                modifier = Modifier.weight(1.15f).height(38.dp),
            )
            XyPillButton(
                text = xy("PC Stream", "PC Stream"),
                onClick = onOpenPcStream,
                primary = false,
                modifier = Modifier.weight(0.85f).height(38.dp),
            )
            XyPillButton(
                text = xy("SSH / VPS", "SSH / VPS"),
                onClick = onOpenSshTerminal,
                primary = false,
                modifier = Modifier.weight(0.85f).height(38.dp),
            )
        }
    }
}

@Composable
private fun LiveSessionsCard() {
    var live by remember { mutableStateOf(XySessionRegistry.list()) }
    var confirmKill by remember { mutableStateOf<XySessionRegistry.Live?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            // Update state hanya kalau isi benar-benar berubah — instance Live
            // stabil di registry, jadi polling 1.5 detik tidak memicu
            // recomposition sia-sia saat tidak ada sesi baru/mati.
            val next = XySessionRegistry.list()
            if (next != live) live = next
            kotlinx.coroutines.delay(1_500)
        }
    }
    if (live.isEmpty()) return
    XyCard {
        XySectionLabel(xy("Sesi aktif ({0})", "Active sessions ({0})", live.size))
        Spacer(Modifier.height(6.dp))
        live.forEach { item ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                XyPillButton(
                    text = xy("Buka", "Open"),
                    onClick = { item.open() },
                    compact = true,
                )
                XyPillButton(
                    text = xy("Putus", "Disconnect"),
                    onClick = { confirmKill = item },
                    primary = false,
                    compact = true,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    confirmKill?.let { target ->
        XyDialog(
            title = xy("Putuskan sesi aktif?", "Disconnect active session?"),
            body = xy(
                "Putuskan koneksi ke \"{0}\" ({1}) sekarang?",
                "Disconnect from \"{0}\" ({1}) now?",
                target.label,
                target.address,
            ),
            confirmLabel = xy("Putuskan", "Disconnect"),
            onConfirm = {
                confirmKill = null
                target.kill()
            },
            dismissLabel = xy("Batal", "Cancel"),
            onDismiss = { confirmKill = null },
        )
    }
}

// =============================================================
// Menu utama — 2 gaya (popup tengah / sidebar kanan), revisi UI ronde 41.
// Konten satu (XyHomeMenuBody) supaya kedua gaya tidak pernah beda fitur.
// =============================================================

/**
 * Cangkang menu: scrim + animasi. Gaya [AppPrefs.HOME_MENU_POPUP] mengambang
 * di tengah (fade + scale), [AppPrefs.HOME_MENU_SIDEBAR] menyamping dari kanan.
 */
@Composable
private fun BoxScope.XyHomeMenuShell(
    open: Boolean,
    style: Int,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (open) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .clickable { onDismiss() }
                .zIndex(40f),
        )
    }
    val isPopup = style == AppPrefs.HOME_MENU_POPUP
    AnimatedVisibility(
        visible = open,
        enter = if (isPopup) {
            fadeIn(tween(180)) + scaleIn(animationSpec = tween(220), initialScale = 0.92f)
        } else {
            slideInHorizontally(tween(240)) { it }
        },
        exit = if (isPopup) {
            fadeOut(tween(140)) + scaleOut(animationSpec = tween(140), targetScale = 0.95f)
        } else {
            slideOutHorizontally(tween(200)) { it }
        },
        modifier = if (isPopup) {
            Modifier.align(Alignment.Center).zIndex(41f)
        } else {
            Modifier.align(Alignment.CenterEnd).zIndex(41f)
        },
    ) {
        if (isPopup) {
            Column(
                Modifier
                    .padding(horizontal = 30.dp, vertical = 26.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(28.dp),
                    )
                    .pointerInput(Unit) {}
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 18.dp, horizontal = 10.dp),
                content = content,
            )
        } else {
            Column(
                Modifier
                    .width(312.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {}
                    .padding(vertical = 18.dp, horizontal = 10.dp),
                content = content,
            )
        }
    }
}

/** Isi menu: brand, daftar seksi, alat & bantuan, versi. Dipakai 2 gaya. */
@Composable
private fun ColumnScope.XyHomeMenuBody(
    section: XySection,
    helpToolsOpen: Boolean,
    onToggleTools: () -> Unit,
    onPick: (XySection) -> Unit,
    onBackup: () -> Unit,
    onUpdate: () -> Unit,
    onFeedback: () -> Unit,
    version: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        XyLogo(modifier = Modifier.size(32.dp))
        Column {
            XyWordmark(fontSize = 21.sp)
            Text(
                "XyVerse \u2022 Remote Desktop",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    XyDivider()
    Spacer(Modifier.height(6.dp))
    // Ronde 13 dulu seksi Tampilan dibuang dari drawer demi rail minim; sejak
    // revisi UI ronde 41 semua seksi kembali lengkap (dashboard Profil sudah
    // lama menampilkan semuanya) supaya menunya terstruktur & mudah dijangkau.
    Text(
        xy("MENU", "MENU"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    XySection.entries.forEach { item ->
        XyHomeMenuItem(
            title = xy(item.title, item.titleEn),
            icon = when (item) {
                XySection.PERANGKAT -> XyIcons.Monitor
                XySection.TAMPILAN -> XyIcons.Fit
                XySection.KREDENSIAL -> XyIcons.Lock
                XySection.UMUM -> XyIcons.Sliders
                XySection.KEAMANAN -> XyIcons.Shield
                XySection.TENTANG -> XyIcons.Info
            },
            subtitle = when (item) {
                XySection.PERANGKAT -> xy("Daftar & koneksi PC", "PC list & connections")
                XySection.TAMPILAN -> xy("Tema & gaya menu", "Theme & menu style")
                XySection.KREDENSIAL -> xy("Akun & kredensial RDP", "Accounts & RDP credentials")
                XySection.UMUM -> xy("Bahasa & perilaku app", "Language & app behavior")
                XySection.KEAMANAN -> xy("Kunci perangkat & sesi", "Device & session locks")
                XySection.TENTANG -> xy("Versi & kredit", "Version & credits")
            },
            selected = item == section,
            onClick = { onPick(item) },
        )
    }
    XyHomeMenuItem(
        title = xy("Alat & bantuan", "Tools & help"),
        subtitle = xy("Cadangkan, pembaruan, masukan", "Backup, updates, feedback"),
        icon = XyIcons.Folder,
        trailing = {
            Text(
                if (helpToolsOpen) "−" else "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onToggleTools,
    )
    AnimatedVisibility(visible = helpToolsOpen) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            XyPillButton(
                text = xy("Cadangkan / Pulihkan Profil", "Backup / Restore Profiles"),
                onClick = onBackup,
                modifier = Modifier.fillMaxWidth(),
                primary = false,
                icon = XyIcons.Folder,
                compact = true,
            )
            Spacer(Modifier.height(6.dp))
            XyPillButton(
                text = xy("Cek Pembaruan & RilisIn Store", "Check Update & RilisIn Store"),
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth(),
                primary = false,
                icon = XyIcons.Shield,
                compact = true,
            )
            Spacer(Modifier.height(6.dp))
            XyPillButton(
                text = xy("Masukan & saran", "Feedback & suggestions"),
                onClick = onFeedback,
                modifier = Modifier.fillMaxWidth(),
                primary = false,
                icon = XyIcons.Info,
                compact = true,
            )
        }
    }
    Spacer(Modifier.weight(1f))
    Text(
        version,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/** Satu baris item menu: kotak ikon rounded + judul + state terpilih jelas. */
@Composable
private fun XyHomeMenuItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean = false,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun DevicesScreen(
    favorites: List<ConnectionProfile>,
    crashLog: String?,
    bootTail: List<String>,
    sharedFileNotice: String?,
    onDismissSharedNotice: () -> Unit,
    onMenu: () -> Unit,
    onAddRdp: () -> Unit,
    onAddPcQuick: () -> Unit,
    onOpenPcStream: () -> Unit,
    onCreateRdp: () -> Unit,
    onOpenSshTerminal: () -> Unit,
    onEdit: (ConnectionProfile) -> Unit,
    onConnect: (ConnectionProfile) -> Unit,
    onWakeConnect: (ConnectionProfile) -> Unit,
    onDelete: (ConnectionProfile) -> Unit,
    onShowCrash: () -> Unit,
    onShowBoot: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var modeFilterIndex by remember { mutableStateOf(0) } // 0 = Semua, 1 = Koneksi PC, 2 = Koneksi RDP
    val devicesPrefs = remember { AppPrefs(context) }
    var bigCards by remember { mutableStateOf(devicesPrefs.deviceGridMode) }
    var showAddBubble by remember { mutableStateOf(false) }
    var showPcConnectionNotice by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (sharedFileNotice != null) {
                Banner(
                    text = sharedFileNotice,
                    onClick = onDismissSharedNotice,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (showPcConnectionNotice) {
                Banner(
                    text = xy("PC Stream menggantikan Koneksi PC lama. RDP tetap tersedia.", "PC Stream replaces legacy PC Connect. RDP remains available."),
                    onClick = { showPcConnectionNotice = false },
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (crashLog != null) {
                Banner(
                    text = xy(
                        "Terjadi error sebelumnya — ketuk untuk lihat log",
                        "An error happened before — tap to see the log",
                    ),
                    onClick = onShowCrash,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            val bootSuspect = bootTail.isNotEmpty() && bootTail.last().let { last ->
                !last.contains("session.connect kembali") &&
                    !last.contains("-> Connected") &&
                    !last.contains("-> Disconnected") &&
                    !last.contains("intent tanpa profil")
            }
            if (bootSuspect) {
                Banner(
                    text = xy(
                        "Sesi terakhir terhenti di tengah jalan — ketuk untuk lihat log",
                        "The last session stopped midway — tap to see the log",
                    ),
                    onClick = onShowBoot,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val inAppUnread by XyInAppNotificationBus.unreadCount.collectAsState()
            val inAppHistory by XyInAppNotificationBus.history.collectAsState()
            XyTopBar(
                title = xy("Perangkat", "Devices"),
                onBack = null,
                actions = {
                    // Tombol Lonceng Pusat Notifikasi Dalam Aplikasi (In-App Notification Center)
                    Box(contentAlignment = Alignment.TopEnd) {
                        XyIconPill(
                            XyIcons.Bell,
                            { XyInAppNotificationBus.openInbox() },
                            active = inAppUnread > 0,
                            flat = true,
                            contentDescription = xy("Notifikasi Dalam Aplikasi", "In-App Notifications"),
                        )
                        val badgeCount = if (inAppUnread > 0) inAppUnread else inAppHistory.size
                        if (badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp, end = 2.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (inAppUnread > 0) Color(0xFFFE2C55) else Color(0xFF38BDF8))
                                    .padding(horizontal = 4.5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    XyIconPill(
                        XyIcons.Grid,
                        {
                            bigCards = !bigCards
                            devicesPrefs.deviceGridMode = bigCards
                        },
                        active = bigCards,
                        flat = true,
                        contentDescription = xy("Kartu besar / list", "Big cards / list"),
                    )
                    XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = xy("Menu", "Menu"))
                    XyIconPill(
                        XyIcons.Plus,
                        { showAddBubble = !showAddBubble },
                        active = true,
                        contentDescription = xy("Tambah perangkat", "Add device"),
                    )
                },
            )

            if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        HomeRdpFreeBannerCard(
                            onCreateRdp = onCreateRdp,
                            onOpenPcStream = onOpenPcStream,
                            onOpenSshTerminal = onOpenSshTerminal,
                        )
                        XyEmptyState(
                            title = xy("Belum Ada Perangkat Tersimpan", "No Saved Devices Yet"),
                            subtitle = xy(
                                "Tambahkan Koneksi PC, PC Stream, atau RDP untuk mulai mengakses desktop jarak jauh.",
                                "Add a PC, PC Stream, or RDP connection to start accessing your remote desktop.",
                            ),
                            icon = XyIcons.Monitor,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                xy("Koneksi PC (ID)", "PC (ID & Pass)"),
                                onAddPcQuick,
                                icon = XyIcons.Plus,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                xy("Koneksi RDP", "Standard RDP"),
                                onAddRdp,
                                primary = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                val filteredFavorites = remember(favorites, searchQuery, modeFilterIndex) {
                    val q = searchQuery.trim().lowercase()
                    favorites.filter { p ->
                        val isPc = RdpOptions.of(context, p.id).pcConnectMode
                        val modeOk = when (modeFilterIndex) {
                            1 -> isPc
                            2 -> !isPc
                            else -> true
                        }
                        val queryOk = q.isEmpty() ||
                            p.label.orEmpty().lowercase().contains(q) ||
                            p.host.lowercase().contains(q) ||
                            p.username.orEmpty().lowercase().contains(q)
                        modeOk && queryOk
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { LiveSessionsCard() }
                    item {
                        HomeRdpFreeBannerCard(
                            onCreateRdp = onCreateRdp,
                            onOpenPcStream = onOpenPcStream,
                            onOpenSshTerminal = onOpenSshTerminal,
                        )
                    }
                    item {
                        XySegmented(
                            options = listOf(
                                xy("Semua ({0})", "All ({0})", favorites.size),
                                xy("Koneksi PC", "PC Connect"),
                                xy("Koneksi RDP", "RDP Session"),
                            ),
                            selectedIndex = modeFilterIndex,
                            onSelect = { modeFilterIndex = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (favorites.size >= 2) {
                        item {
                            XyField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = xy("Cari perangkat", "Search devices"),
                                hint = xy("Nama PC, alamat IP, atau username...", "PC name, IP address, or username..."),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (filteredFavorites.isEmpty()) {
                        item {
                            XyEmptyState(
                                title = xy("Perangkat Tidak Ditemukan", "No Matching Devices"),
                                subtitle = xy(
                                    "Tidak ada perangkat yang cocok dengan filter atau pencarian saat ini.",
                                    "No devices match the current filter or search query.",
                                ),
                                icon = XyIcons.Monitor,
                                actionText = xy("Reset Filter", "Reset Filter"),
                                onAction = {
                                    searchQuery = ""
                                    modeFilterIndex = 0
                                },
                            )
                        }
                    } else if (bigCards) {
                        // Kartu besar (permintaan pemilik 2026-10-16): SATU
                        // kolom full-width dengan preview tinggi — bukan grid
                        // 2 kolom. Paling enak dipandang & paling ringan
                        // dirender (satu kartu per baris LazyColumn).
                        items(filteredFavorites, key = { it.id }) { profile ->
                            DeviceCard(
                                profile = profile,
                                onConnect = { onConnect(profile) },
                                onWakeConnect = { onWakeConnect(profile) },
                                onEdit = { onEdit(profile) },
                                onDelete = { onDelete(profile) },
                            )
                        }
                    } else {
                        // Mode list ringkas.
                        items(filteredFavorites, key = { it.id }) { profile ->
                            DeviceCard(
                                profile = profile,
                                onConnect = { onConnect(profile) },
                                onWakeConnect = { onWakeConnect(profile) },
                                onEdit = { onEdit(profile) },
                                onDelete = { onDelete(profile) },
                                compact = true,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }

        // Bubble Popover Menu saat tombol + ditekan
        if (showAddBubble) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { showAddBubble = false }
                    .zIndex(25f),
            )
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp)
                    .width(296.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(18.dp), clip = false)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(18.dp),
                    )
                    .pointerInput(Unit) {}
                    .padding(10.dp)
                    .zIndex(26f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    xy("Pilih Jenis Koneksi", "Choose Connection Type"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                XyRow(
                    title = xy("Koneksi PC (ID & Password)", "PC Connection (ID & Password)"),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    trailing = {
                        Text(
                            xy("Eksperimental", "Experimental"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        showAddBubble = false
                        onAddPcQuick()
                    },
                )
                XyRow(
                    title = xy("PC Stream & Engine Konsol Non-RDP (120Hz)", "PC Stream & Non-RDP Console Engine (120Hz)"),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    trailing = {
                        Text(
                            xy("120Hz", "120Hz"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        showAddBubble = false
                        onOpenPcStream()
                    },
                )
                XyRow(
                    title = xy("Koneksi RDP (Host / IP)", "RDP Connection (Host / IP)"),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    onClick = {
                        showAddBubble = false
                        onAddRdp()
                    },
                )
                XyRow(
                    title = xy("Terminal SSH / Linux VPS", "SSH Terminal / Linux VPS"),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    trailing = {
                        Text(
                            "SSH-2",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        showAddBubble = false
                        onOpenSshTerminal()
                    },
                )
                XyRow(
                    title = xy("Setup On-Mic (Untuk RDP Beli / VPS)", "On-Mic Setup (For Purchased RDP / VPS)"),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    trailing = {
                        Text(
                            "On-Mic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        showAddBubble = false
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(
                            ClipData.newPlainText(
                                "XyDesk On-Mic Setup",
                                "powershell -NoProfile -ExecutionPolicy Bypass -Command \"irm https://xydeskremote.biz.id/onmic.ps1 | iex\"",
                            ),
                        )
                        Toast.makeText(
                            context,
                            xyNow("Perintah PowerShell On-Mic disalin! Jalankan di PowerShell Admin pada RDP beli Anda.", "On-Mic PowerShell command copied! Run in Admin PowerShell on your purchased RDP."),
                            Toast.LENGTH_LONG,
                        ).show()
                        openInAppCustomTab(context, "https://xydeskremote.biz.id/XyDesk-RDP-OnMic-Setup.bat")
                    },
                )
                XyRow(
                    title = xy("RDP Gratis 6 Jam (GitHub Actions)", "Free 6-Hour RDP (GitHub Actions)"),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    trailing = {
                        Text(
                            xy("Baru", "New"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        showAddBubble = false
                        onCreateRdp()
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    profile: ConnectionProfile,
    onConnect: () -> Unit,
    onWakeConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val rdpOptions = remember(profile.id) { RdpOptions.of(context, profile.id) }
    val hasWol = !rdpOptions.macAddress.isNullOrBlank()
    val streamBadge = when (rdpOptions.streamProfile) {
        XyStreamProfile.ULTRA_LOW_LATENCY -> xy("Latensi Rendah", "Low Latency")
        XyStreamProfile.HIGH_VISUAL -> "AVC444"
        XyStreamProfile.DATA_SAVER -> xy("Hemat Kuota", "Data Saver")
        XyStreamProfile.CUSTOM -> xy("Kustom", "Custom")
        XyStreamProfile.BALANCED, XyStreamProfile.AUTO -> if (rdpOptions.udpTransport) "UDP+H264" else "H264"
    }
    // Art preview milik app (bukan wallpaper RDP/OS): geometris, diturunkan
    // dari nama perangkat + jenis OS sebagai penanda kecil di pojok.
    val wall = remember(profile.id, profile.label, profile.host) {
        XyWall.WIN11.forDevice(profile.label ?: profile.host)
    }
    val previewSeed = remember(profile.id, profile.label, profile.host) {
        (profile.label ?: profile.host) + "|" + profile.host
    }
    val shape = RoundedCornerShape(if (compact) 10.dp else 16.dp)
    // Baris keterangan dipakai kartu grid (overlay) dan list (compact).
    val detailLine = if (rdpOptions.pcConnectMode) {
        buildString {
            append(xy("Koneksi PC", "Connect PC"))
            append("  ·  ")
            append(profile.host)
            append("  ·  ")
            append(rdpOptions.activeCodecLabel())
        }
    } else {
        buildString {
            append(profile.host).append(':').append(profile.port)
            if (!profile.username.isNullOrBlank()) append("  ·  ").append(profile.username)
            append("  ·  ").append(streamBadge)
            if (rdpOptions.consoleAdmin) append("  ·  Admin")
        }
    }
    if (compact) {
        // Mode list (permintaan pemilik): bar ramping — preview kecil di kiri,
        // nama + keterangan di kanan, aksi berupa ikon.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onConnect)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(width = 104.dp, height = 64.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                DevicePreviewArt(seed = previewSeed, os = wall)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    profile.label ?: profile.host,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detailLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasWol) {
                XyPillButton(xy("WoL", "WoL"), onWakeConnect, primary = false, compact = true)
            }
            XyIconPill(
                XyIcons.Sliders,
                onEdit,
                size = 34.dp,
                flat = true,
                contentDescription = xy("Pengaturan RDP", "RDP settings"),
            )
            XyIconPill(
                XyIcons.Trash,
                onDelete,
                size = 34.dp,
                flat = true,
                contentDescription = xy("Hapus", "Delete"),
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onConnect),
    ) {
        // Preview desktop penuh yang lebih tinggi (212.dp) supaya proporsi
        // monitor desktop terlihat lega dan jelas di layar Home.
        Box(
            Modifier
                .fillMaxWidth()
                .height(212.dp)
                .clip(shape),
        ) {
            DevicePreviewArt(seed = previewSeed, os = wall)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 40.dp),
            ) {
                Text(
                    profile.label ?: profile.host,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detailLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DesktopTaskbar(Modifier.align(Alignment.BottomCenter))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            XyPillButton(xy("Connect", "Connect"), onConnect, compact = true)
            if (hasWol) {
                XyPillButton(
                    xy("Bangunkan (WoL)", "Wake (WoL)"),
                    onWakeConnect,
                    primary = false,
                    compact = true,
                )
            }
            Spacer(Modifier.weight(1f))
            XyIconPill(
                XyIcons.Sliders,
                onEdit,
                size = 40.dp,
                flat = true,
                contentDescription = xy("Pengaturan RDP", "RDP settings"),
            )
            XyIconPill(
                XyIcons.Trash,
                onDelete,
                size = 40.dp,
                flat = true,
                contentDescription = xy("Hapus", "Delete"),
            )
        }
    }
}

/** Strip taskbar tipis di dasar preview supaya terbaca sebagai desktop. */
@Composable
private fun DesktopTaskbar(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFF090B0E))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.85f)))
        repeat(3) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.45f)),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 18.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun Banner(
    text: String,
    onClick: () -> Unit,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"

private fun exportProfilesJson(
    context: Context,
    profiles: List<ConnectionProfile>,
): String {
    val arr = org.json.JSONArray()
    profiles.forEach { p ->
        val opts = RdpOptions.of(context, p.id)
        val obj = org.json.JSONObject()
            .put("host", p.host)
            .put("port", p.port)
            .put("username", p.username.orEmpty())
            .put("domain", p.domain.orEmpty())
            .put("label", p.label.orEmpty())
            .put("pcConnectMode", opts.pcConnectMode)
        arr.put(obj)
    }
    return org.json.JSONObject()
        .put("version", 1)
        .put("profiles", arr)
        .toString()
}

private fun importProfilesJson(raw: String): List<Pair<ConnectionProfile, Boolean>> = runCatching {
    val root = org.json.JSONObject(raw.trim())
    val arr = root.optJSONArray("profiles") ?: return@runCatching emptyList()
    buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val host = obj.optString("host", "").trim()
            if (host.isEmpty()) continue
            val port = obj.optInt("port", 3389).coerceIn(1, 65535)
            val username = obj.optString("username", "").trim().ifEmpty { null }
            val domain = obj.optString("domain", "").trim().ifEmpty { null }
            val label = obj.optString("label", "").trim().ifEmpty { null }
            val isPc = obj.optBoolean("pcConnectMode", false)
            add(
                ConnectionProfile(
                    host = host,
                    port = port,
                    username = username,
                    domain = domain,
                    label = label,
                ) to isPc,
            )
        }
    }
}.getOrDefault(emptyList())

private fun fetchRemoteReleaseStatus(context: Context): String {
    val current = appVersion(context)
    return runCatching {
        val conn = (java.net.URL("https://api.xydeskremote.biz.id/api/status").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(body)
        val latest = json.optString("version", "1.0.0")
        val launchDate = json.optString("launchWib", "Sabtu, 3 Oktober 2026 · 10:00 WIB")
        xyNow(
            "Versi terpasang: v{0} · Target Rilis Publik: v{1} ({2}). Toko aplikasi resmi ekosistem XyVerse akan tersedia di rilisin.xyverse.my.id.",
            "Installed version: v{0} · Public Release Target: v{1} ({2}). Official XyVerse app store will be available at rilisin.xyverse.my.id.",
            current,
            latest,
            launchDate,
        )
    }.getOrElse {
        xyNow(
            "Versi terpasang: v{0} · Kunjungi www.xydeskremote.biz.id atau rilisin.xyverse.my.id untuk informasi rilis terbaru.",
            "Installed version: v{0} · Visit www.xydeskremote.biz.id or rilisin.xyverse.my.id for the latest release info.",
            current,
        )
    }
}


