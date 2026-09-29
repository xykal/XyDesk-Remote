package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.core.coreBuildInfo
import id.xydesk.remote.ui.components.XySlider
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyDialog
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyLogo
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XyRow
import id.xydesk.remote.ui.components.XySectionLabel
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyToggleRow
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.theme.XyPill

/** Router seksi drawer (selain "Perangkat" yang punya layarnya sendiri). */
@Composable
internal fun SectionScreen(
    section: XySection,
    favorites: List<ConnectionProfile>,
    onMenu: () -> Unit,
    appPrefs: AppPrefs,
    onEditDevice: (ConnectionProfile) -> Unit,
    onClearAllCredentials: () -> Unit,
    onShowLog: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        XyTopBar(
            title = sectionTitle(section),
            onBack = null,
            actions = { XyIconPill(XyIcons.Menu, onMenu, contentDescription = "Menu") },
        )
        when (section) {
            XySection.TAMPILAN -> DisplaySection(favorites, onEditDevice)
            XySection.KREDENSIAL -> CredentialsSection(favorites, onClearAllCredentials)
            XySection.UMUM -> GeneralSection(appPrefs, onShowLog)
            XySection.KEAMANAN -> SecuritySection()
            XySection.TENTANG -> AboutSection()
            XySection.PERANGKAT -> Unit
        }
    }
}

// =============================================================
// Tampilan — resolusi, orientasi, skala per perangkat
// =============================================================

@Composable
private fun DisplaySection(
    favorites: List<ConnectionProfile>,
    onEditDevice: (ConnectionProfile) -> Unit,
) {
    val context = LocalContext.current
    if (favorites.isEmpty()) {
        EmptyHint(
            title = xy("Belum ada perangkat", "No devices yet"),
            body = xy(
                "Tambahkan perangkat dulu, lalu atur tampilannya di sini.",
                "Add a device first, then set its display options here.",
            ),
        )
        return
    }
    var index by remember { mutableIntStateOf(0) }
    val profile = favorites[index.coerceIn(0, favorites.lastIndex)]
    var resolution by remember(profile.id) {
        mutableStateOf(DisplayPrefs.resolution(context, profile.id))
    }
    var rotation by remember(profile.id) {
        mutableStateOf(DisplayPrefs.rotation(context, profile.id))
    }
    var scale by remember(profile.id) {
        mutableIntStateOf(DisplayPrefs.dpi(context, profile.id))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(xy("Perangkat", "Devices"))
        XySegmented(
            options = favorites.map { it.label ?: it.host },
            selectedIndex = index.coerceIn(0, favorites.lastIndex),
            onSelect = { index = it },
            modifier = Modifier.fillMaxWidth(),
        )
        XyCard {
            XyRow(
                title = profile.label ?: profile.host,
                subtitle = "${profile.host}:${profile.port}",
                trailing = {
                    XyIconPill(
                        XyIcons.Gear,
                        { onEditDevice(profile) },
                        size = 38.dp,
                        contentDescription = xy("Ubah perangkat", "Edit device"),
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            XySectionLabel(xy("Resolusi desktop remote", "Remote desktop resolution"))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DisplayPrefs.resolutionOptions.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { colIndex, option ->
                            val value = option.value
                            XyPillButton(
                                text = xy(option.id, option.en),
                                onClick = {
                                    resolution = value
                                    DisplayPrefs.setResolution(context, profile.id, value)
                                },
                                primary = value == resolution,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            XySectionLabel(xy("Orientasi", "Orientation"))
            Spacer(Modifier.height(8.dp))
            XySegmented(
                options = DisplayPrefs.rotations,
                selectedIndex = DisplayPrefs.rotations.indexOf(rotation).coerceAtLeast(0),
                onSelect = {
                    rotation = DisplayPrefs.rotations[it]
                    DisplayPrefs.setRotation(context, profile.id, rotation)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            XySectionLabel(xy("Zoom lokal awal", "Initial local zoom"))
            Text(
                "$scale%",
                style = MaterialTheme.typography.titleMedium,
            )
            XySlider(
                value = scale.toFloat(),
                onValueChange = {
                    scale = it.toInt()
                    DisplayPrefs.setDpi(context, profile.id, scale)
                },
                valueRange = 80f..200f,
                steps = 11,
            )
            Text(
                xy(
                    "Zoom lokal bisa diubah dari panel sesi tanpa memutus koneksi. DPI desktop " +
                        "Windows dan resolusi remote punya kontrol masing-masing.",
                    "Local zoom can be changed from the session panel without disconnecting. Windows " +
                        "desktop DPI and remote resolution each have their own controls.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// =============================================================
// Kredensial
// =============================================================

@Composable
private fun CredentialsSection(
    favorites: List<ConnectionProfile>,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val vault = remember { CredentialVault(context.applicationContext) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    // Flag "password tersimpan?" dihitung ulang saat `tick` naik (tombol
    // Lupakan ditekan) atau daftar perangkat berubah. Dulu ini pakai trik
    // `if (tick < 0) Text("")` di ujung layar untuk memaksa recompose.
    val rememberedFlags = remember(tick, favorites) { favorites.map { vault.has(it.id) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(xy("Password tersimpan", "Saved passwords"))
        XyCard {
            Text(
                xy(
                    "Password dienkripsi AES-256-GCM; kuncinya tidak bisa keluar dari " +
                        "Android Keystore perangkat ini.",
                    "Passwords are encrypted with AES-256-GCM; the key cannot leave this " +
                        "device's Android Keystore.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (favorites.isEmpty()) {
                Text(xy("Belum ada perangkat tersimpan.", "No saved devices yet."), style = MaterialTheme.typography.bodyMedium)
            } else {
                favorites.forEachIndexed { idx, profile ->
                    val remembered = rememberedFlags[idx]
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile.label ?: profile.host,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(profile.username ?: xy("tanpa username", "no username"))
                                    append(
                                        if (remembered) xy("  ·  password tersimpan", "  ·  password saved")
                                        else xy("  ·  password tidak disimpan", "  ·  password not saved"),
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (remembered) {
                            XyPillButton(
                                text = xy("Lupakan", "Forget"),
                                onClick = {
                                    vault.remove(profile.id)
                                    tick++
                                },
                                primary = false,
                                compact = true,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        XyCard {
            XyRow(
                title = xy("Hapus semua kredensial & perangkat", "Delete all credentials and devices"),
                subtitle = xy("Vault dikosongkan dan daftar perangkat dibersihkan", "Vault cleared and device list wiped"),
                leading = XyIcons.Trash,
                onClick = { confirmClear = true },
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmClear) {
        XyDialog(
            title = xy("Hapus semua?", "Delete everything?"),
            body = xy("Semua perangkat tersimpan dan password-nya akan dihapus.", "All saved devices and their passwords will be removed."),
            confirmLabel = xy("Hapus", "Delete"),
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            dismissLabel = xy("Batal", "Cancel"),
            onDismiss = { confirmClear = false },
        )
    }
}

// =============================================================
// Umum — tema, kontrol default, sesi
// =============================================================

@Composable
private fun GeneralSection(appPrefs: AppPrefs, onShowLog: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SessionPrefs(context) }
    var hudSize by remember { mutableStateOf(prefs.hudButtonSize) }
    var autoFit by remember { mutableStateOf(prefs.autoFit) }
    var defaultUdp by remember { mutableStateOf(appPrefs.defaultUdp) }
    var defaultNetAuto by remember { mutableStateOf(appPrefs.defaultNetAuto) }
    var defaultH264 by remember { mutableStateOf(appPrefs.defaultH264) }
    var defaultDynRes by remember { mutableStateOf(appPrefs.defaultDynamicResolution) }
    var defaultClipboard by remember { mutableStateOf(appPrefs.defaultClipboard) }
    var defaultDrive by remember { mutableStateOf(appPrefs.defaultLocalDrive) }
    var themeMode by remember { mutableIntStateOf(appPrefs.themeMode) }
    var autoDisconnect by remember { mutableStateOf(appPrefs.autoDisconnect) }
    var inputMode by remember { mutableIntStateOf(prefs.inputMode.ordinal) }
    var pointerStyle by remember { mutableIntStateOf(prefs.pointerStyle.ordinal) }
    var pointerSize by remember { mutableStateOf(prefs.pointerSize) }
    var scrollSpeed by remember { mutableStateOf(prefs.scrollSpeed) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var lang by remember { mutableStateOf(LangPrefs.current()) }
    var showLeft by remember { mutableStateOf(prefs.showLeft) }
    var showRight by remember { mutableStateOf(prefs.showRight) }
    var showMiddle by remember { mutableStateOf(prefs.showMiddle) }
    var showScroll by remember { mutableStateOf(prefs.showScroll) }
    var showSwitch by remember { mutableStateOf(prefs.showSwitch) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(xy("Tampilan app", "App appearance"))
        XyCard {
            XySegmented(
                options = listOf(xy("Ikut sistem", "Follow system"), xy("Gelap", "Dark"), xy("Terang", "Light")),
                selectedIndex = themeMode,
                onSelect = {
                    themeMode = it
                    // Tanpa recreate(): state tema reaktif, semua layar
                    // recompose sendiri (dulu seluruh activity di-recreate,
                    // layar kedip dan posisi scroll hilang).
                    id.xydesk.remote.ui.theme.XyThemeState.set(appPrefs, it)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            XyToggleRow(
                title = xy("Putuskan sesi saat app ke background", "Disconnect when app goes background"),
                subtitle = xy(
                    "Cegah sesi menggantung saat HP dipakai untuk hal lain",
                    "Avoid a dangling session while the phone does something else",
                ),
                checked = autoDisconnect,
                onCheckedChange = {
                    autoDisconnect = it
                    appPrefs.autoDisconnect = it
                },
            )
        }

        XySectionLabel(xy("Kontrol sesi", "Session controls"))
        XyCard {
            Text(xy("Mode input default", "Default input mode"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            XySegmented(
                options = InputMode.entries.map { it.title },
                selectedIndex = inputMode,
                onSelect = {
                    inputMode = it
                    prefs.inputMode = InputMode.entries[it]
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                InputMode.entries[inputMode].detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(xy("Pointer", "Pointer"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            XySegmented(
                options = PointerStyle.entries.map { xy(it.title, it.titleEn) },
                selectedIndex = pointerStyle,
                onSelect = {
                    pointerStyle = it
                    prefs.pointerStyle = PointerStyle.entries[it]
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                xy("Ukuran pointer: {0} dp", "Pointer size: {0} dp", pointerSize.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XySlider(
                value = pointerSize,
                onValueChange = {
                    pointerSize = it
                    prefs.pointerSize = it
                },
                valueRange = 10f..52f,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                xy(
                    "Kecepatan scroll: {0}x",
                    "Scroll speed: {0}x",
                    "%.1f".format(scrollSpeed),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XySlider(
                value = scrollSpeed,
                onValueChange = {
                    scrollSpeed = it
                    prefs.scrollSpeed = it
                },
                valueRange = 0.4f..2.5f,
            )
            XyToggleRow(
                title = xy("Getaran saat tombol ditekan", "Haptic feedback on button press"),
                subtitle = xy("Haptic halus di cluster mouse dan keyboard", "Subtle haptics on the mouse cluster and keyboard"),
                checked = haptics,
                onCheckedChange = {
                    haptics = it
                    prefs.haptics = it
                },
            )
        }

        XySectionLabel(xy("Tombol HUD", "HUD buttons"))
        XyCard {
            Text(
                xy(
                    "Setiap tombol di layar sesi berdiri sendiri: bulat, ukurannya " +
                        "bisa diatur, dan aksinya dipilih per tombol " +
                        "(sekali klik / tahan / toggle). Geser tombol hanya saat " +
                        "mode \"Atur posisi\" menyala, supaya tidak kepencet waktu dipakai. " +
                        "Tombol bawaan di bawah ini menentukan set awal untuk perangkat " +
                        "yang belum pernah diubah.",
                    "Every on-screen button stands alone: round, resizable, and " +
                        "with its own action (single tap / " +
                        "hold / toggle). Dragging only works while \"Edit layout\" is on, " +
                        "so buttons cannot move by accident while you use them. The " +
                        "defaults below seed devices that were never customised.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XyToggleRow(
                title = xy("Tombol kiri", "Left button"),
                checked = showLeft,
                onCheckedChange = { showLeft = it; prefs.showLeft = it },
            )
            XyToggleRow(
                title = xy("Tombol kanan", "Right button"),
                checked = showRight,
                onCheckedChange = { showRight = it; prefs.showRight = it },
            )
            XyToggleRow(
                title = xy("Tombol tengah", "Middle button"),
                checked = showMiddle,
                onCheckedChange = { showMiddle = it; prefs.showMiddle = it },
            )
            XyToggleRow(
                title = xy("Scroll atas/bawah", "Scroll up/down"),
                checked = showScroll,
                onCheckedChange = { showScroll = it; prefs.showScroll = it },
            )
            XyToggleRow(
                title = xy("Tombol ganti mode input", "Input-mode switch button"),
                subtitle = xy(
                    "Pindah trackpad <-> sentuh langsung dari layar sesi",
                    "Switch trackpad <-> direct touch from the session screen",
                ),
                checked = showSwitch,
                onCheckedChange = { showSwitch = it; prefs.showSwitch = it },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                xy(
                    "Posisi cluster diatur dengan menyeretnya langsung di layar sesi.",
                    "Cluster position is set by dragging it on the session screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        XySectionLabel(xy("Keyboard", "Keyboard"))
        XyCard {
            Text(
                xy(
                    "Mengetik memakai keyboard HP (IME). Tidak ada keyboard virtual " +
                        "bawaan app dan tidak ada toolbar di atas keyboard: semua " +
                        "kontrol dibuat dari tombol overlay satu-satu di layar sesi.",
                    "Typing uses the phone keyboard (IME). There is no built-in " +
                        "virtual keyboard and no toolbar above it: every control is a " +
                        "separate overlay button on the session screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            XyToggleRow(
                title = xy("Muat seluruh desktop (fit)", "Fit whole desktop"),
                subtitle = xy("Taskbar dan tepi desktop selalu ikut kelihatan", "Taskbar and desktop edges always stay visible"),
                checked = autoFit,
                onCheckedChange = { autoFit = it; prefs.autoFit = it },
            )
        }

        XySectionLabel(xy("Keyboard & HUD", "Keyboard & HUD"))
        XyCard {
            Text(
                xy("Ukuran default tombol HUD: {0} dp", "Default HUD button size: {0} dp", hudSize.toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                xy(
                    "Ukuran ini dipakai untuk tombol baru dan tata letak bawaan. " +
                        "Tombol yang sudah diatur tetap memakai ukuran masing-masing.",
                    "New buttons and restored default layouts use this size. " +
                        "Existing custom buttons keep their individual sizes.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XySlider(
                value = hudSize,
                onValueChange = { hudSize = it; prefs.hudButtonSize = it },
                valueRange = 56f..80f,
            )
        }

        XySectionLabel(xy("Bahasa / Language", "Language / Bahasa"))
        XyCard {
            XySegmented(
                options = XyLang.entries.map { it.label },
                selectedIndex = lang.ordinal,
                onSelect = {
                    lang = XyLang.entries[it]
                    LangPrefs.set(context, lang)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                t("home.language.sub"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        XySectionLabel(xy("Transport (default perangkat baru)", "Transport (new device defaults)"))
        XyCard {
            Text(
                xy(
                    "Nilai ini dipakai saat menambah perangkat baru. Perangkat yang " +
                        "sudah ada tetap diatur sendiri di layar Ubah perangkat.",
                    "These values are used when adding a new device. Existing devices " +
                        "keep their own settings on the Edit device screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XyToggleRow(
                title = xy("Transport UDP", "UDP transport"),
                subtitle = xy("RDP-UDP + FEC untuk gerakan halus", "RDP-UDP + FEC for smoother motion"),
                checked = defaultUdp,
                onCheckedChange = { defaultUdp = it; appPrefs.defaultUdp = it },
            )
            XyToggleRow(
                title = xy("Deteksi bandwidth otomatis", "Automatic bandwidth detection"),
                subtitle = xy("Kualitas mengikuti kondisi jaringan", "Quality follows network conditions"),
                checked = defaultNetAuto,
                onCheckedChange = { defaultNetAuto = it; appPrefs.defaultNetAuto = it },
            )
            XyToggleRow(
                title = xy("H.264 / RemoteFX (GFX)", "H.264 / RemoteFX (GFX)"),
                subtitle = xy("Matikan kalau server lama tidak mendukung", "Turn off if an older server does not support it"),
                checked = defaultH264,
                onCheckedChange = { defaultH264 = it; appPrefs.defaultH264 = it },
            )
            XyToggleRow(
                title = xy("Resolusi dinamis", "Dynamic resolution"),
                subtitle = xy("Desktop remote bisa diubah saat sesi hidup (kanal DISP)", "Change remote desktop size live (DISP channel)"),
                checked = defaultDynRes,
                onCheckedChange = { defaultDynRes = it; appPrefs.defaultDynamicResolution = it },
            )
            XyToggleRow(
                title = xy("Clipboard dua arah", "Two-way clipboard"),
                checked = defaultClipboard,
                onCheckedChange = { defaultClipboard = it; appPrefs.defaultClipboard = it },
            )
            XyToggleRow(
                title = xy("Penyimpanan lokal (drive)", "Local storage (drive)"),
                subtitle = xy("Folder Download HP muncul sebagai drive di remote", "Phone storage shows up as a remote drive"),
                checked = defaultDrive,
                onCheckedChange = { defaultDrive = it; appPrefs.defaultLocalDrive = it },
            )
        }

        XySectionLabel(xy("Masalah koneksi", "Connection issues"))
        XyCard {
            Text(
                xy(
                    "Audio, mikrofon, clipboard, drive, kamera, dan gateway diatur " +
                        "per perangkat di layar Tambah/Ubah perangkat.",
                    "Audio, microphone, clipboard, drive, camera and gateway are set " +
                        "per device on the Add/Edit device screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                xy("Inti: {0}", "Core: {0}", coreBuildInfo()),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            XyPillButton(
                xy("Lihat log sesi terakhir", "View last session log"),
                onShowLog,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// =============================================================
// Keamanan
// =============================================================

@Composable
private fun SecuritySection() {
    val context = LocalContext.current
    val store = remember { CertificateTrustStore(context.applicationContext) }
    var tick by remember { mutableIntStateOf(0) }
    var showTrustedCertificates by remember { mutableStateOf(false) }
    val entries = remember(tick) { store.entries() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(xy("Penyimpanan kredensial", "Credential storage"))
        XyCard {
            Bullet(xy("Password: AES-256-GCM, kunci non-exportable di Android Keystore",
                "Passwords: AES-256-GCM, non-exportable key in the Android Keystore"))
            Bullet(xy("Password tidak pernah masuk log aplikasi atau URI yang ditulis ke log",
                "Passwords never end up in app logs or in logged URIs"))
            Bullet(xy("Backup & transfer data app dimatikan (allowBackup=false)",
                "App backup & transfer are disabled (allowBackup=false)"))
            Bullet(xy("Tidak ada kredensial yang dikirim ke layanan pihak ketiga",
                "No credentials are sent to any third-party service"))
        }
        XySectionLabel(xy("Sertifikat server yang dipercaya", "Trusted server certificates"))
        XyCard {
            Row(Modifier.fillMaxWidth().clickable { showTrustedCertificates = !showTrustedCertificates },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${if (showTrustedCertificates) "▾" else "▸"} ${xy("Daftar tepercaya ({0})", "Trusted list ({0})", entries.size)}",
                    style = MaterialTheme.typography.titleSmall)
            }
            if (showTrustedCertificates && entries.isEmpty()) {
                Text(
                    xy(
                        "Belum ada sertifikat yang ditandai percaya. Sertifikat hanya " +
                            "diingat kalau kamu menekan \"Percaya & ingat\" saat connect.",
                        "No certificate has been trusted yet. A certificate is only " +
                            "remembered when you press \"Trust & remember\" while connecting.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (showTrustedCertificates) {
                entries.take(12).forEach { (host, fingerprint) ->
                    Row(
                        Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(host, style = MaterialTheme.typography.titleSmall)
                            Text(
                                fingerprint,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Hapus per entri — dulu satu-satunya jalan adalah
                        // menghapus SEMUA kepercayaan sertifikat.
                        Box(
                            Modifier
                                .clip(XyPill)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, XyPill)
                                .clickable {
                                    store.removeKey(host)
                                    tick++
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                xy("Hapus", "Delete"),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                XyPillButton(
                    text = xy("Hapus semua kepercayaan sertifikat", "Clear all trusted certificates"),
                    onClick = {
                        store.clear()
                        tick++
                    },
                    primary = false,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Bullet(text: String) {
    Row(
        Modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .height(5.dp)
                .clip(XyPill)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
                .fillMaxWidth(0.014f),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// =============================================================
// Tentang
// =============================================================

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(LangPrefs.current()) }
    val native = remember {
        runCatching { com.freerdp.freerdpcore.services.LibFreeRDP.getVersion() }.getOrNull()
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XyCard {
            XyLogo(modifier = Modifier.height(38.dp).fillMaxWidth(0.16f))
            Spacer(Modifier.height(12.dp))
            Text(xy("XyDesk Remote", "XyDesk Remote"), style = MaterialTheme.typography.headlineSmall)
            Text(
                t("app.tagline"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            XyRow(title = t("about.version"), subtitle = appVersionName(context))
            XyRow(
                title = t("about.engine"),
                subtitle = native ?: xy("FreeRDP (dimuat saat connect)", "FreeRDP (loaded on connect)"),
            )
            XyRow(
                title = t("about.developer"),
                subtitle = xy(
                    "Didukung oleh ${AppBrand.PUBLISHER}",
                    "Powered by ${AppBrand.PUBLISHER}",
                ),
            )
        }

        // ---- Bahasa: satu klik, langsung ganti seluruh teks app ----
        XyCard {
            XySectionLabel(t("home.language").uppercase())
            Text(
                t("home.language.sub"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            XySegmented(
                options = XyLang.entries.map { it.label },
                selectedIndex = lang.ordinal,
                onSelect = {
                    lang = XyLang.entries[it]
                    LangPrefs.set(context, lang)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SupportCard()

        XyCard {
            Text(
                xy(
                    "XyDesk Remote dibangun di atas FreeRDP (Apache License 2.0). " +
                        "Kode aplikasi milik XyVerse. Daftar perubahan terhadap tree " +
                        "FreeRDP ada di XYDESK-REMOTE-NOTICE.md.",
                    "XyDesk Remote is built on FreeRDP (Apache License 2.0). The app " +
                        "code belongs to XyVerse. The list of changes to the FreeRDP " +
                        "tree is in XYDESK-REMOTE-NOTICE.md.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            XyPillButton(t("about.license"), { showLicense = true }, primary = false)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showLicense) {
        XyDialog(
            title = t("about.license.title"),
            body = t("about.license.body"),
            confirmLabel = "OK",
            onConfirm = { showLicense = false },
        )
    }
}

/**
 * xy("Dukung saya", "Support me") — Saweria, GitHub Sponsors, dan bintang repo.
 *
 * Semua tautan dibuka lewat browser (Intent VIEW), tanpa SDK pihak ketiga
 * dan tanpa jaringan dari dalam app sendiri.
 */
@Composable
internal fun SupportCard() {
    val context = LocalContext.current
    XyCard {
        XySectionLabel(t("about.support.title").uppercase())
        Text(
            t("about.support.body"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportLink(
                icon = XyIcons.Heart,
                title = t("about.support.saweria"),
                subtitle = "saweria.co/kallsptra",
                url = "https://saweria.co/kallsptra",
            )
            SupportLink(
                icon = XyIcons.Sponsor,
                title = t("about.support.github"),
                subtitle = "github.com/sponsors/xykal",
                url = "https://github.com/sponsors/xykal",
            )
            SupportLink(
                icon = XyIcons.Star,
                title = t("about.support.star"),
                subtitle = "github.com/xykal/XyDesk-Remote",
                url = "https://github.com/xykal/XyDesk-Remote/stargazers",
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            t("about.thanks"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SupportLink(icon: ImageVector, title: String, subtitle: String, url: String) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                runCatching { context.startActivity(intent) }
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            XyIcons.ExternalLink,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(16.dp).width(16.dp),
        )
    }
}

@Composable
private fun EmptyHint(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        XyCard(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    XyDialog(
        title = title,
        body = body,
        confirmLabel = xy("Tutup", "Close"),
        onConfirm = onDismiss,
        dismissLabel = null,
        onDismiss = onDismiss,
        mono = true,
    )
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"
