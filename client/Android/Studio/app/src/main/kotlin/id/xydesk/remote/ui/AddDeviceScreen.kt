package id.xydesk.remote.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.freerdp.freerdpcore.services.LibFreeRDP
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.RdpOptions
import id.xydesk.remote.core.XyAudioMode
import id.xydesk.remote.core.XyGateway
import id.xydesk.remote.ui.components.XySlider
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySectionLabel
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyToggleRow
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.theme.XyPill

/**
 * Layar xy("Tambah perangkat", "Add device") — bukan dialog. Semua yang dibutuhkan satu
 * koneksi RDP ada di sini: alamat, kredensial, tampilan, kanal yang
 * di-redirect, dan gateway.
 *
 * Preferensi (tampilan + opsi RDP) ditulis langsung ke SharedPreferences
 * dengan id = `host:port`, jadi begitu profil disimpan, sesi berikutnya
 * memakai setelan yang sama.
 */
@Composable
fun AddDeviceScreen(
    existing: ConnectionProfile?,
    savedUsers: List<String>,
    onCancel: () -> Unit,
    onSubmit: (profile: ConnectionProfile, rememberPassword: Boolean, connect: Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Kunci tetap: dipakai ulang saat mengubah perangkat supaya simpan tidak
    // menghasilkan baris kedua. Data lama (belum punya key) memakai id-nya.
    val deviceKey = remember(existing) {
        existing?.key ?: existing?.id ?: java.util.UUID.randomUUID().toString()
    }
    val deviceId = deviceKey

    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember { mutableStateOf((existing?.port ?: 3389).toString()) }
    var user by remember { mutableStateOf(existing?.username.orEmpty()) }
    var pass by remember { mutableStateOf(existing?.password.orEmpty()) }
    var domain by remember { mutableStateOf(existing?.domain.orEmpty()) }
    var rememberPass by remember { mutableStateOf(existing?.password?.isNotEmpty() ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    val stored = remember(deviceId) {
        if (deviceId != null) {
            RdpOptions.of(context, deviceId)
        } else {
            // Perangkat baru mewarisi default General (transport/clipboard/drive).
            val app = AppPrefs(context)
            RdpOptions(
                clipboard = app.defaultClipboard,
                localDrive = app.defaultLocalDrive,
                udpTransport = app.defaultUdp,
                networkAutoDetect = app.defaultNetAuto,
                h264 = app.defaultH264,
                dynamicResolution = app.defaultDynamicResolution,
            )
        }
    }
    var options by remember { mutableStateOf(stored) }
    var gatewayOn by remember { mutableStateOf(stored.gateway != null) }
    var gwHost by remember { mutableStateOf(stored.gateway?.host.orEmpty()) }
    var gwPort by remember { mutableStateOf((stored.gateway?.port ?: 443).toString()) }
    var gwUser by remember { mutableStateOf(stored.gateway?.username.orEmpty()) }
    var gwPass by remember { mutableStateOf(stored.gateway?.password.orEmpty()) }
    var gwDomain by remember { mutableStateOf(stored.gateway?.domain.orEmpty()) }

    var resolutionIndex by remember(deviceId) {
        mutableStateOf(
            if (deviceId == null) 0
            else DisplayPrefs.resolutionOptions.indexOfFirst {
                it.value == DisplayPrefs.resolution(context, deviceId)
            }.coerceAtLeast(0)
        )
    }
    var rotationIndex by remember(deviceId) {
        mutableStateOf(
            if (deviceId == null) 0
            else DisplayPrefs.rotations.indexOf(DisplayPrefs.rotation(context, deviceId))
                .coerceAtLeast(0)
        )
    }
    var dpi by remember(deviceId) {
        mutableStateOf(if (deviceId == null) 100 else DisplayPrefs.dpi(context, deviceId))
    }
    var audioIndex by remember { mutableStateOf(options.audioMode.ordinal) }

    fun submit(connect: Boolean) {
        val hostValue = host.trim()
        val portValue = port.toIntOrNull() ?: 3389
        when {
            hostValue.isEmpty() -> {
                error = xyNow("Alamat host wajib diisi", "Host address is required")
                return
            }
            hostValue.any { it.isWhitespace() } -> {
                error = xyNow("Alamat host tidak boleh ada spasi", "Host address cannot contain spaces")
                return
            }
            portValue !in 1..65535 -> {
                error = xyNow("Port harus 1-65535", "Port must be 1-65535")
                return
            }
        }
        val profile = try {
            ConnectionProfile(
                host = hostValue,
                port = portValue,
                username = user.trim().ifEmpty { null },
                password = pass.ifEmpty { null },
                domain = domain.trim().ifEmpty { null },
                label = label.trim().ifEmpty { null },
                key = deviceKey,
            )
        } catch (e: IllegalArgumentException) {
            error = e.message
            return
        }
        error = null
        val finalOptions = options.copy(
            audioMode = XyAudioMode.entries[audioIndex],
            gateway = if (gatewayOn && gwHost.isNotBlank()) {
                XyGateway(
                    host = gwHost.trim(),
                    port = gwPort.toIntOrNull() ?: 443,
                    username = gwUser.trim().ifEmpty { null },
                    password = gwPass.ifEmpty { null },
                    domain = gwDomain.trim().ifEmpty { null },
                )
            } else {
                null
            },
        )
        finalOptions.write(context, profile.id)
        DisplayPrefs.setResolution(
            context,
            profile.id,
            DisplayPrefs.resolutionOptions[resolutionIndex].value,
        )
        DisplayPrefs.setRotation(context, profile.id, DisplayPrefs.rotations[rotationIndex])
        DisplayPrefs.setDpi(context, profile.id, dpi)
        onSubmit(profile, rememberPass, connect)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        XyTopBar(
            title = if (existing == null) xy("Perangkat baru", "New device") else xy("Ubah perangkat", "Edit device"),
            onBack = onCancel,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            XySectionLabel(xy("Alamat", "Address"))
            XyCard {
                XyField(
                    value = label,
                    onValueChange = { label = it },
                    label = xy("Nama perangkat", "Device name"),
                    hint = xy("mis. PC kantor", "e.g. Office PC"),
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = xy("Host / IP / tailnet", "Host / IP / tailnet"),
                    hint = xy("192.168.1.10 atau pc.tailnet.ts.net", "192.168.1.10 or pc.tailnet.ts.net"),
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = xy("Port RDP", "RDP port"),
                    hint = "3389",
                    keyboardType = KeyboardType.Number,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
            }

            XySectionLabel(xy("Kredensial", "Credentials"))
            XyCard {
                if (savedUsers.isNotEmpty()) {
                    Text(
                        xy("Akun tersimpan", "Saved account"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedUsers.take(4).forEach { name ->
                            Box(
                                Modifier
                                    .clip(XyPill)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, XyPill)
                                    .clickable {
                                        user = name
                                        if (domain.isBlank()) domain = ""
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text(name, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                XyField(
                    value = user,
                    onValueChange = { user = it },
                    label = xy("Username", "Username"),
                    hint = xy(
                            "kosongkan kalau mau ditanya saat connect",
                            "leave empty to be asked on connect",
                        ),
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = xy("Password", "Password"),
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = xy("Domain (opsional)", "Domain (optional)"),
                    hint = "CORP",
                )
                Spacer(Modifier.height(6.dp))
                XyToggleRow(
                    title = xy("Ingat password", "Remember password"),
                    subtitle = xy("Disimpan terenkripsi AES-GCM, kunci di Android Keystore", "Stored AES-GCM encrypted, key in Android Keystore"),
                    checked = rememberPass,
                    onCheckedChange = { rememberPass = it },
                    leading = XyIcons.Lock,
                )
            }

            XySectionLabel(xy("Tampilan", "Display"))
            XyCard {
                Text(
                    xy("Resolusi desktop remote", "Remote desktop resolution"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayPrefs.resolutionOptions.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { colIndex, option ->
                                val index = rowIndex * 2 + colIndex
                                XyPillButton(
                                    text = xy(option.id, option.en),
                                    onClick = { resolutionIndex = index },
                                    primary = index == resolutionIndex,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    xy("Orientasi perangkat", "Device orientation"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                XySegmented(
                    options = DisplayPrefs.rotations,
                    selectedIndex = rotationIndex,
                    onSelect = { rotationIndex = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Skala tampilan awal: $dpi%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                XySlider(
                    value = dpi.toFloat(),
                    onValueChange = { dpi = it.toInt() },
                    valueRange = 80f..200f,
                    steps = 11,
                )
                Text(
                    xy(
                        "Resolusi & skala diterapkan saat sesi dibuka; mengubahnya " +
                            "menyambungkan ulang sesi dengan ukuran baru.",
                        "Resolution & scale apply when the session opens; changing " +
                            "them reconnects with the new size.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            XySectionLabel(xy("Audio & kanal", "Audio & channels"))
            XyCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    XyAudioMode.entries.forEachIndexed { index, mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (index == audioIndex) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (index == audioIndex) MaterialTheme.colorScheme.outlineVariant
                                    else MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.medium,
                                )
                                .clickable { audioIndex = index }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(xy(mode.title, mode.titleEn), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    xy(mode.detail, mode.detailEn),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                XyToggleRow(
                    title = xy("Mikrofon", "Microphone"),
                    subtitle = xy("Kirim audio HP ke remote (butuh izin mikrofon)", "Send phone audio to remote (needs mic permission)"),
                    checked = options.microphone,
                    onCheckedChange = { options = options.copy(microphone = it) },
                    leading = XyIcons.Mic,
                )
                XyToggleRow(
                    title = xy("Clipboard", "Clipboard"),
                    subtitle = xy("Copy-paste dua arah", "Two-way copy-paste"),
                    checked = options.clipboard,
                    onCheckedChange = { options = options.copy(clipboard = it) },
                    leading = XyIcons.Clip,
                )
                XyToggleRow(
                    title = xy("Penyimpanan lokal", "Local storage"),
                    subtitle = "Folder HP muncul sebagai drive 'sdcard' di remote",
                    checked = options.localDrive,
                    onCheckedChange = { options = options.copy(localDrive = it) },
                    leading = XyIcons.Folder,
                )
                if (options.localDrive) {
                    StorageAccessRow()
                }
                XyToggleRow(
                    title = xy("Kamera", "Camera"),
                    subtitle = xy("Kamera HP sebagai webcam remote (kalau didukung server)", "Phone camera as remote webcam (if the server supports it)"),
                    checked = options.camera,
                    onCheckedChange = { options = options.copy(camera = it) },
                    leading = XyIcons.Shot,
                )
            }

            XySectionLabel(xy("Jaringan", "Network"))
            XyCard {
                XyToggleRow(
                    title = xy("Transport UDP", "UDP transport"),
                    subtitle = xy(
                        "RDP-UDP + FEC; lebih halus untuk gerakan cepat, " +
                            "butuh dukungan server",
                        "RDP-UDP + FEC; smoother fast motion, needs server support",
                    ),
                    checked = options.udpTransport,
                    onCheckedChange = { options = options.copy(udpTransport = it) },
                    leading = XyIcons.Wifi,
                )
                XyToggleRow(
                    title = "Deteksi bandwidth otomatis",
                    subtitle = "FreeRDP menyesuaikan kualitas mengikuti jaringan",
                    checked = options.networkAutoDetect,
                    onCheckedChange = { options = options.copy(networkAutoDetect = it) },
                    leading = XyIcons.Sliders,
                )
                XyToggleRow(
                    title = xy("H.264 / RemoteFX (GFX)", "H.264 / RemoteFX (GFX)"),
                    subtitle = xy(
                        "Wajib untuk konten bergerak; matikan kalau remote lama",
                        "Required for moving content; turn off for an old remote",
                    ),
                    checked = options.h264,
                    onCheckedChange = { options = options.copy(h264 = it) },
                    leading = XyIcons.Grid,
                )
            }

            XySectionLabel(xy("Gateway", "Gateway"))
            XyCard {
                XyToggleRow(
                    title = xy("Lewat RDP Gateway", "Use RDP Gateway"),
                    subtitle = xy("RD Gateway untuk jaringan kantor / tanpa port langsung", "RD Gateway for office networks / no direct port"),
                    checked = gatewayOn,
                    onCheckedChange = { gatewayOn = it },
                    leading = XyIcons.Lock,
                )
                if (gatewayOn) {
                    Spacer(Modifier.height(6.dp))
                    XyField(
                        value = gwHost,
                        onValueChange = { gwHost = it.trim() },
                        label = xy("Gateway host", "Gateway host"),
                        hint = "gw.kantor.com",
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwPort,
                        onValueChange = { gwPort = it.filter(Char::isDigit).take(5) },
                        label = xy("Gateway port", "Gateway port"),
                        hint = "443",
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwUser,
                        onValueChange = { gwUser = it },
                        label = xy("Gateway username (opsional)", "Gateway username (optional)"),
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwPass,
                        onValueChange = { gwPass = it },
                        label = xy("Gateway password (opsional)", "Gateway password (optional)"),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwDomain,
                        onValueChange = { gwDomain = it },
                        label = xy("Gateway domain (opsional)", "Gateway domain (optional)"),
                    )
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            XyPillButton(
                text = xy("Simpan", "Save"),
                onClick = { submit(false) },
                primary = false,
                modifier = Modifier.weight(1f),
            )
            XyPillButton(
                text = xy("Simpan & connect", "Save & connect"),
                onClick = { submit(true) },
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

/**
 * Status akses storage untuk redirect drive.
 *
 * Android 11+ hanya mengizinkan app membaca folder miliknya sendiri; kalau
 * user mau seluruh isi /storage/emulated/0, izin "semua file" harus diminta
 * lewat halaman Setelan (tidak bisa lewat dialog runtime biasa).
 */
@Composable
private fun StorageAccessRow() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LibFreeRDP.hasAllFilesAccess()) }
    val path = remember(granted) { LibFreeRDP.appDrivePath(context) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(
            if (granted) {
                xy("Akses penuh: seluruh isi HP terbaca remote", "Full access: the remote reads all phone storage")
            } else {
                xy("Akses terbatas: hanya folder app yang terbaca remote", "Limited access: only the app folder is visible to the remote")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Path: $path",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!granted) {
            Spacer(Modifier.height(8.dp))
            XyPillButton(
                text = xy("Beri akses semua file", "Grant all-files access"),
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    }
                    runCatching { context.startActivity(intent) }
                    granted = LibFreeRDP.hasAllFilesAccess()
                },
                primary = false,
                compact = true,
            )
        }
    }
}
