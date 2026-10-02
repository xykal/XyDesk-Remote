@file:OptIn(ExperimentalMaterial3Api::class)

package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
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
    data class EditDevice(val profile: ConnectionProfile?, val pcQuickMode: Boolean = false) : XyRoute
    data object Section : XyRoute
}

/**
 * Home XyDesk: daftar perangkat + drawer seksi.
 *
 * Semua info yang ditampilkan sengaja tipis: nama perangkat, alamat, akun.
 * Preview pakai wallpaper motif OS perangkat (digambar prosedural, tanpa aset).
 */
@Composable
fun XyDeskHome(
    onExit: () -> Unit,
    onReady: () -> Unit = {},
    sharedFileNotice: String? = null,
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
    var section by remember { mutableStateOf(XySection.PERANGKAT) }
    var route by remember { mutableStateOf<XyRoute>(XyRoute.Devices) }
    var crashLog by remember { mutableStateOf(CrashLog.last(context.applicationContext)) }
    var showCrash by remember { mutableStateOf(false) }
    val bootTail = remember { ConnectionLog.tailFromFile(context.applicationContext, 20) }
    var showBoot by remember { mutableStateOf(false) }
    var feedbackOpen by remember { mutableStateOf(false) }
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
            scope.launch { repo.touch(profile) }
            context.startActivity(XyDeskSessionActivity.connectIntent(context, profile))
        }
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

    fun shareFeedback(category: String, message: String) {
        feedbackOpen = false
        val draft = feedbackShareDraft(category, message)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "XyDesk Remote feedback")
            putExtra(Intent.EXTRA_TEXT, draft)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(sendIntent, xyNow("Pilih aplikasi untuk berbagi", "Choose an app to share")),
            )
        }.onFailure {
            notice.show(xyNow("Menu berbagi tidak tersedia", "Share menu is unavailable"))
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
            route != XyRoute.Devices -> route = XyRoute.Devices
            section != XySection.PERANGKAT -> section = XySection.PERANGKAT
            else -> confirmExitApp = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {

            when (val current = route) {
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

                XyRoute.Section -> Unit
            }
        }

    // ---- drawer kiri (custom, bukan ModalNavigationDrawer bawaan) ----
    if (drawerOpen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .clickable { drawerOpen = false }
                .zIndex(40f),
        )
    }
    AnimatedVisibility(
        visible = drawerOpen,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it },
        modifier = Modifier.zIndex(41f),
    ) {
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                )
                .pointerInput(Unit) {}
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                XyLogo(modifier = Modifier.size(30.dp))
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
            Spacer(Modifier.height(8.dp))
            XySection.entries.forEach { item ->
                XyRow(
                    // Dulu title mentah (Indonesia saja) — drawer satu-satunya
                    // tempat yang tidak ikut bahasa. Sekarang dua bahasa.
                    title = xy(item.title, item.titleEn),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    leading = when (item) {
                        XySection.PERANGKAT -> XyIcons.Monitor
                        XySection.TAMPILAN -> XyIcons.Fit
                        XySection.KREDENSIAL -> XyIcons.Lock
                        XySection.UMUM -> XyIcons.Sliders
                        // Dulu Info — sama dengan Tentang, jadi dua seksi
                        // bersebelahan punya ikon identik.
                        XySection.KEAMANAN -> XyIcons.Shield
                        XySection.TENTANG -> XyIcons.Info
                    },
                    leadingTint = if (item == section) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        section = item
                        route = XyRoute.Devices
                        drawerOpen = false
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            XyPillButton(
                text = xy("Cadangkan / Pulihkan Profil", "Backup / Restore Profiles"),
                onClick = {
                    drawerOpen = false
                    backupJsonInput = exportProfilesJson(context, favorites)
                    backupModalOpen = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                primary = false,
                icon = XyIcons.Folder,
                compact = true,
            )
            Spacer(Modifier.height(6.dp))
            XyPillButton(
                text = xy("Cek Pembaruan & RilisIn Store", "Check Update & RilisIn Store"),
                onClick = {
                    drawerOpen = false
                    updateModalOpen = true
                    updateStatusText = xyNow("Memeriksa status rilis terbaru dari rdp.xydesk.my.id...", "Checking latest release status from rdp.xydesk.my.id...")
                    scope.launch {
                        val status = withContext(Dispatchers.IO) { fetchRemoteReleaseStatus(context) }
                        updateStatusText = status
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                primary = false,
                icon = XyIcons.Shield,
                compact = true,
            )
            Spacer(Modifier.height(6.dp))
            XyPillButton(
                text = xy("Masukan & saran", "Feedback & suggestions"),
                onClick = {
                    drawerOpen = false
                    feedbackOpen = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                primary = false,
                icon = XyIcons.Info,
                compact = true,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "v${appVersion(context)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
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
        // Dua bagian: log app + log native FreeRDP (kanal audio/mikrofon,
        // clipboard, drive, DISP). Yang native muncul kalau appender file
        // aktif — dipasang XyApp sebelum library dimuat.
        val nativeTail = remember(showBoot) { ConnectionLog.nativeTail(160) }
        val body = buildString {
            if (nativeTail.isNotEmpty()) {
                appendLine("=== LOG NATIVE (FreeRDP) ===")
                appendLine(nativeTail.joinToString("\n"))
                appendLine()
            }
            appendLine("=== LOG APP (xydesk-boot.log) ===")
            append(bootTail.joinToString("\n"))
        }
        InfoDialog(
            title = xy("Log sesi terakhir", "Last session log"),
            body = body,
            onDismiss = { showBoot = false },
        )
    }
    if (feedbackOpen) {
        FeedbackDialog(
            onDismiss = { feedbackOpen = false },
            onShare = ::shareFeedback,
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
            title = xy("Pembaruan & RilisIn Store", "Updates & RilisIn Store"),
            onDismiss = { updateModalOpen = false },
        ) {
            Text(
                text = "XyDesk Remote v${appVersion(context)}  ·  XyVerse Technology Global",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = updateStatusText ?: xy("Memeriksa pembaruan...", "Checking for updates..."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = xy("Portal rdp.xydesk.my.id", "rdp.xydesk.my.id Portal"),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rdp.xydesk.my.id")))
                        }
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Buka RilisIn Store", "Open RilisIn Store"),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rilisin.xyverse.my.id")))
                        }
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
private fun LiveSessionsCard() {
    var live by remember { mutableStateOf(XySessionRegistry.list()) }
    var confirmKill by remember { mutableStateOf<XySessionRegistry.Live?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            live = XySessionRegistry.list()
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
    var showAddBubble by remember { mutableStateOf(false) }

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

            XyTopBar(
                title = xy("Perangkat", "Devices"),
                onBack = null,
                actions = {
                    XyIconPill(XyIcons.Menu, onMenu, contentDescription = xy("Menu", "Menu"))
                    XyIconPill(
                        XyIcons.Plus,
                        { showAddBubble = !showAddBubble },
                        active = true,
                        contentDescription = xy("Tambah perangkat", "Add device"),
                    )
                },
            )

            if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    XyCard(modifier = Modifier.fillMaxWidth()) {
                        Text(xy("Belum ada perangkat", "No devices yet"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            xy(
                                "Pilih Koneksi PC (ID & Password via XyDeskHost.exe dengan dukungan GPU Gaming) " +
                                    "atau Koneksi RDP standar lewat IP lokal, domain, maupun Tailscale.",
                                "Choose PC Connection (ID & Password via XyDeskHost.exe with Gaming GPU support) " +
                                    "or standard RDP Connection over a local IP, domain, or Tailscale.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
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
                    items(filteredFavorites, key = { it.id }) { profile ->
                        DeviceCard(
                            profile = profile,
                            onConnect = { onConnect(profile) },
                            onWakeConnect = { onWakeConnect(profile) },
                            onEdit = { onEdit(profile) },
                            onDelete = { onDelete(profile) },
                        )
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
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
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            showAddBubble = false
                            onAddPcQuick()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            xy("Koneksi PC (ID & Password)", "PC Connection (ID & Password)"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            xy("Tahap Pengembangan", "Experimental"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        xy(
                            "Jalur E2EE + Ultra-Low Latency FPS Gaming (NVIDIA / AMD / Intel GPU)",
                            "E2EE + Ultra-Low Latency FPS Gaming path (NVIDIA / AMD / Intel GPU)",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            showAddBubble = false
                            onAddRdp()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        xy("Koneksi RDP (Host / IP)", "RDP Connection (Host / IP)"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        xy(
                            "Koneksi langsung lewat IP lokal, domain publik, atau Tailscale",
                            "Direct connection via local IP, public domain, or Tailscale",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
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
            // Badge Arsitektur Koneksi di Pojok Kiri Atas Preview
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xD91A1D23))
                    .border(1.dp, Color(0xFF3C424A), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (rdpOptions.pcConnectMode) {
                        "DIRECT PC  ·  ${rdpOptions.pcStreamEngine.badge}  ·  ${rdpOptions.pcTargetFps}FPS"
                    } else {
                        "RDP DESKTOP  ·  $streamBadge"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE4E7EB),
                )
            }
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
                    if (rdpOptions.pcConnectMode) {
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
                    },
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
            XyIconPill(XyIcons.Gear, onEdit, size = 40.dp, contentDescription = xy("Pengaturan RDP", "RDP settings"))
            XyIconPill(XyIcons.Trash, onDelete, size = 40.dp, contentDescription = xy("Hapus", "Delete"))
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
            .background(Color.Black.copy(alpha = 0.34f))
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
        val conn = (java.net.URL("https://rdp.xydesk.my.id/api/status").openConnection() as java.net.HttpURLConnection).apply {
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
            "Versi terpasang: v{0} · Kunjungi rdp.xydesk.my.id atau rilisin.xyverse.my.id untuk informasi rilis terbaru.",
            "Installed version: v{0} · Visit rdp.xydesk.my.id or rilisin.xyverse.my.id for the latest release info.",
            current,
        )
    }
}


