package id.xydesk.remote.ui

import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import id.xydesk.remote.ui.components.XyCard
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
            title = section.title,
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
        EmptyHint("Belum ada perangkat", "Tambahkan perangkat dulu, lalu atur tampilannya di sini.")
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
        XySectionLabel("Perangkat")
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
                        contentDescription = "Ubah perangkat",
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            XySectionLabel("Resolusi desktop remote")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DisplayPrefs.resolutions.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { colIndex, (value, title) ->
                            XyPillButton(
                                text = title,
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
            XySectionLabel("Orientasi")
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
            XySectionLabel("Skala tampilan awal")
            Text(
                "$scale%",
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = scale.toFloat(),
                onValueChange = {
                    scale = it.toInt()
                    DisplayPrefs.setDpi(context, profile.id, scale)
                },
                valueRange = 80f..200f,
                steps = 11,
            )
            Text(
                "Resolusi & orientasi dipakai saat sesi dibuka. Skala bisa diubah " +
                    "langsung dari panel sesi tanpa memutus koneksi.",
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel("Password tersimpan")
        XyCard {
            Text(
                "Password dienkripsi AES-256-GCM; kuncinya tidak bisa keluar dari " +
                    "Android Keystore perangkat ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (favorites.isEmpty()) {
                Text("Belum ada perangkat tersimpan.", style = MaterialTheme.typography.bodyMedium)
            } else {
                favorites.forEach { profile ->
                    val remembered = vault.has(profile.id)
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
                                    append(profile.username ?: "tanpa username")
                                    append(if (remembered) "  ·  password tersimpan" else "  ·  password tidak disimpan")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (remembered) {
                            XyPillButton(
                                text = "Lupakan",
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
                title = "Hapus semua kredensial & perangkat",
                subtitle = "Vault dikosongkan dan daftar perangkat dibersihkan",
                leading = XyIcons.Trash,
                onClick = { confirmClear = true },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (tick < 0) Text("") // tick dipakai untuk memaksa recompose setelah "Lupakan"
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua?") },
            text = { Text("Semua perangkat tersimpan dan password-nya akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Batal") }
            },
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
    var keyboardScale by remember { mutableStateOf(prefs.keyboardScale) }
    var keyboardAuto by remember { mutableStateOf(prefs.keyboardAutoOpen) }
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
    var clusterScale by remember { mutableStateOf(prefs.clusterScale) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var corner by remember { mutableIntStateOf(prefs.keyboardCorner.ordinal) }
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
        XySectionLabel("Tampilan app")
        XyCard {
            XySegmented(
                options = listOf("Ikut sistem", "Gelap", "Terang"),
                selectedIndex = themeMode,
                onSelect = {
                    themeMode = it
                    appPrefs.themeMode = it
                    recreateActivity(context)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            XyToggleRow(
                title = "Putuskan sesi saat app ke background",
                subtitle = "Cegah sesi menggantung saat HP dipakai untuk hal lain",
                checked = autoDisconnect,
                onCheckedChange = {
                    autoDisconnect = it
                    appPrefs.autoDisconnect = it
                },
            )
        }

        XySectionLabel("Kontrol sesi")
        XyCard {
            Text("Mode input default", style = MaterialTheme.typography.titleSmall)
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
            Text("Pointer", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            XySegmented(
                options = PointerStyle.entries.map { it.title },
                selectedIndex = pointerStyle,
                onSelect = {
                    pointerStyle = it
                    prefs.pointerStyle = PointerStyle.entries[it]
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Ukuran pointer: ${pointerSize.toInt()} dp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = pointerSize,
                onValueChange = {
                    pointerSize = it
                    prefs.pointerSize = it
                },
                valueRange = 10f..52f,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Kecepatan scroll: ${"%.1f".format(scrollSpeed)}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = scrollSpeed,
                onValueChange = {
                    scrollSpeed = it
                    prefs.scrollSpeed = it
                },
                valueRange = 0.4f..2.5f,
            )
            XyToggleRow(
                title = "Getaran saat tombol ditekan",
                subtitle = "Haptic halus di cluster mouse dan keyboard",
                checked = haptics,
                onCheckedChange = {
                    haptics = it
                    prefs.haptics = it
                },
            )
        }

        XySectionLabel("Tombol HUD")
        XyCard {
            Text(
                "Setiap tombol di layar sesi berdiri sendiri: bentuknya bulat " +
                    "penuh, bisa digeser bebas, ukurannya diatur, dan aksinya " +
                    "dipilih per tombol (sekali klik / tahan / toggle). Tombol " +
                    "bawaan di bawah ini menentukan set awal untuk perangkat " +
                    "yang belum pernah diubah; setelah itu daftar tombolnya " +
                    "diatur langsung dari panel kanan di layar sesi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ukuran tombol bawaan: ${(clusterScale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = clusterScale,
                onValueChange = {
                    clusterScale = it
                    prefs.clusterScale = it
                },
                valueRange = 0.7f..1.8f,
            )
            XyToggleRow(
                title = "Tombol kiri",
                checked = showLeft,
                onCheckedChange = { showLeft = it; prefs.showLeft = it },
            )
            XyToggleRow(
                title = "Tombol kanan",
                checked = showRight,
                onCheckedChange = { showRight = it; prefs.showRight = it },
            )
            XyToggleRow(
                title = "Tombol tengah",
                checked = showMiddle,
                onCheckedChange = { showMiddle = it; prefs.showMiddle = it },
            )
            XyToggleRow(
                title = "Scroll atas/bawah",
                checked = showScroll,
                onCheckedChange = { showScroll = it; prefs.showScroll = it },
            )
            XyToggleRow(
                title = "Tombol ganti mode input",
                subtitle = "Pindah trackpad <-> sentuh langsung dari layar sesi",
                checked = showSwitch,
                onCheckedChange = { showSwitch = it; prefs.showSwitch = it },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Posisi cluster diatur dengan menyeretnya langsung di layar sesi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        XySectionLabel("Keyboard")
        XyCard {
            XySegmented(
                options = Corner.entries.map { it.title },
                selectedIndex = corner,
                onSelect = {
                    corner = it
                    prefs.keyboardCorner = Corner.entries[it]
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tombol keyboard selalu di pojok bawah, di atas baris tombol " +
                    "fungsi tambahan supaya tidak tertutup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        XySectionLabel("Keyboard & HUD")
        XyCard {
            Text("Ukuran tombol kontrol HUD: ${hudSize.toInt()} dp", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tombol kontrol selalu bulat penuh; angka ini sama dengan " +
                    "diameternya (radius = setengah).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = hudSize,
                onValueChange = { hudSize = it; prefs.hudButtonSize = it },
                valueRange = 40f..80f,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Skala keyboard layar: ${(keyboardScale * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = keyboardScale,
                onValueChange = { keyboardScale = it; prefs.keyboardScale = it },
                valueRange = 0.7f..1.6f,
            )
            XyToggleRow(
                title = "Buka keyboard layar otomatis",
                subtitle = "Keyboard QWERTY/Fn/NUM/KOMBO langsung tampil saat sesi terhubung",
                checked = keyboardAuto,
                onCheckedChange = { keyboardAuto = it; prefs.keyboardAutoOpen = it },
            )
        }

        XySectionLabel("Bahasa / Language")
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

        XySectionLabel("Transport (default perangkat baru)")
        XyCard {
            Text(
                "Nilai ini dipakai saat menambah perangkat baru. Perangkat yang " +
                    "sudah ada tetap diatur sendiri di layar Ubah perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XyToggleRow(
                title = "Transport UDP",
                subtitle = "RDP-UDP + FEC untuk gerakan halus",
                checked = defaultUdp,
                onCheckedChange = { defaultUdp = it; appPrefs.defaultUdp = it },
            )
            XyToggleRow(
                title = "Deteksi bandwidth otomatis",
                subtitle = "Kualitas mengikuti kondisi jaringan",
                checked = defaultNetAuto,
                onCheckedChange = { defaultNetAuto = it; appPrefs.defaultNetAuto = it },
            )
            XyToggleRow(
                title = "H.264 / RemoteFX (GFX)",
                subtitle = "Matikan kalau server lama tidak mendukung",
                checked = defaultH264,
                onCheckedChange = { defaultH264 = it; appPrefs.defaultH264 = it },
            )
            XyToggleRow(
                title = "Resolusi dinamis",
                subtitle = "Desktop remote bisa diubah saat sesi hidup (kanal DISP)",
                checked = defaultDynRes,
                onCheckedChange = { defaultDynRes = it; appPrefs.defaultDynamicResolution = it },
            )
            XyToggleRow(
                title = "Clipboard dua arah",
                checked = defaultClipboard,
                onCheckedChange = { defaultClipboard = it; appPrefs.defaultClipboard = it },
            )
            XyToggleRow(
                title = "Penyimpanan lokal (drive)",
                subtitle = "Folder Download HP muncul sebagai drive di remote",
                checked = defaultDrive,
                onCheckedChange = { defaultDrive = it; appPrefs.defaultLocalDrive = it },
            )
        }

        XySectionLabel("Masalah koneksi")
        XyCard {
            Text(
                "Audio, mikrofon, clipboard, drive, kamera, dan gateway diatur " +
                    "per perangkat di layar Tambah/Ubah perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text("Inti: ${coreBuildInfo()}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            XyPillButton(
                "Lihat log sesi terakhir",
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
    val entries = remember(tick) { store.entries() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel("Penyimpanan kredensial")
        XyCard {
            Bullet("Password: AES-256-GCM, kunci non-exportable di Android Keystore")
            Bullet("Password tidak pernah masuk log aplikasi atau URI yang ditulis ke log")
            Bullet("Backup & transfer data app dimatikan (allowBackup=false)")
            Bullet("Tidak ada kredensial yang dikirim ke layanan pihak ketiga")
        }
        XySectionLabel("Sertifikat server yang dipercaya")
        XyCard {
            if (entries.isEmpty()) {
                Text(
                    "Belum ada sertifikat yang ditandai percaya. Sertifikat hanya " +
                        "diingat kalau kamu menekan \"Percaya & ingat\" saat connect.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                entries.take(12).forEach { (host, fingerprint) ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(host, style = MaterialTheme.typography.titleSmall)
                        Text(
                            fingerprint,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                XyPillButton(
                    text = "Hapus semua kepercayaan sertifikat",
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
            Text("XyDesk Remote", style = MaterialTheme.typography.headlineSmall)
            Text(
                t("app.tagline"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            XyRow(title = t("about.version"), subtitle = appVersionName(context))
            XyRow(
                title = t("about.engine"),
                subtitle = native ?: "FreeRDP (dimuat saat connect)",
            )
            XyRow(title = t("about.developer"), subtitle = "XyVerse / xykal")
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
                "XyDesk Remote dibangun di atas FreeRDP (Apache License 2.0). " +
                    "Kode aplikasi milik XyVerse. Daftar perubahan terhadap tree " +
                    "FreeRDP ada di XYDESK-REMOTE-NOTICE.md.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            XyPillButton(t("about.license"), { showLicense = true }, primary = false)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text(t("about.license.title")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t("about.license.body"), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("OK") } },
        )
    }
}

/**
 * "Dukung saya" — Saweria, GitHub Sponsors, dan bintang repo.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                body.lineSequence().forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"
