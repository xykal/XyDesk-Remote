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
 * Layar "Tambah perangkat" — bukan dialog. Semua yang dibutuhkan satu
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
    val deviceId = existing?.id

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
            else DisplayPrefs.resolutions.indexOfFirst {
                it.first == DisplayPrefs.resolution(context, deviceId)
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
                error = "Alamat host wajib diisi"
                return
            }
            hostValue.any { it.isWhitespace() } -> {
                error = "Alamat host tidak boleh ada spasi"
                return
            }
            portValue !in 1..65535 -> {
                error = "Port harus 1-65535"
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
        DisplayPrefs.setResolution(context, profile.id, DisplayPrefs.resolutions[resolutionIndex].first)
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
            title = if (existing == null) "Perangkat baru" else "Ubah perangkat",
            onBack = onCancel,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            XySectionLabel("Alamat")
            XyCard {
                XyField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Nama perangkat",
                    hint = "mis. PC kantor",
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = "Host / IP / tailnet",
                    hint = "192.168.1.10 atau pc.tailnet.ts.net",
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = "Port RDP",
                    hint = "3389",
                    keyboardType = KeyboardType.Number,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
            }

            XySectionLabel("Kredensial")
            XyCard {
                if (savedUsers.isNotEmpty()) {
                    Text(
                        "Akun tersimpan",
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
                    label = "Username",
                    hint = "kosongkan kalau mau ditanya saat connect",
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = "Password",
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = "Domain (opsional)",
                    hint = "CORP",
                )
                Spacer(Modifier.height(6.dp))
                XyToggleRow(
                    title = "Ingat password",
                    subtitle = "Disimpan terenkripsi AES-GCM, kunci di Android Keystore",
                    checked = rememberPass,
                    onCheckedChange = { rememberPass = it },
                    leading = XyIcons.Lock,
                )
            }

            XySectionLabel("Tampilan")
            XyCard {
                Text(
                    "Resolusi desktop remote",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayPrefs.resolutions.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { colIndex, (_, title) ->
                                val index = rowIndex * 2 + colIndex
                                XyPillButton(
                                    text = title,
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
                    "Orientasi perangkat",
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
                androidx.compose.material3.Slider(
                    value = dpi.toFloat(),
                    onValueChange = { dpi = it.toInt() },
                    valueRange = 80f..200f,
                    steps = 11,
                )
                Text(
                    "Resolusi & skala diterapkan saat sesi dibuka; mengubahnya " +
                        "menyambungkan ulang sesi dengan ukuran baru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            XySectionLabel("Audio & kanal")
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
                                Text(mode.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    mode.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                XyToggleRow(
                    title = "Mikrofon",
                    subtitle = "Kirim audio HP ke remote (butuh izin mikrofon)",
                    checked = options.microphone,
                    onCheckedChange = { options = options.copy(microphone = it) },
                    leading = XyIcons.Mic,
                )
                XyToggleRow(
                    title = "Clipboard",
                    subtitle = "Copy-paste dua arah",
                    checked = options.clipboard,
                    onCheckedChange = { options = options.copy(clipboard = it) },
                    leading = XyIcons.Clip,
                )
                XyToggleRow(
                    title = "Penyimpanan lokal",
                    subtitle = "Folder HP muncul sebagai drive 'sdcard' di remote",
                    checked = options.localDrive,
                    onCheckedChange = { options = options.copy(localDrive = it) },
                    leading = XyIcons.Folder,
                )
                if (options.localDrive) {
                    StorageAccessRow()
                }
                XyToggleRow(
                    title = "Kamera",
                    subtitle = "Kamera HP sebagai webcam remote (kalau didukung server)",
                    checked = options.camera,
                    onCheckedChange = { options = options.copy(camera = it) },
                    leading = XyIcons.Shot,
                )
            }

            XySectionLabel("Jaringan")
            XyCard {
                XyToggleRow(
                    title = "Transport UDP",
                    subtitle = "RDP-UDP + FEC; lebih halus untuk gerakan cepat, " +
                        "butuh dukungan server",
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
                    title = "H.264 / RemoteFX (GFX)",
                    subtitle = "Wajib untuk konten bergerak; matikan kalau remote lama",
                    checked = options.h264,
                    onCheckedChange = { options = options.copy(h264 = it) },
                    leading = XyIcons.Grid,
                )
            }

            XySectionLabel("Gateway")
            XyCard {
                XyToggleRow(
                    title = "Lewat RDP Gateway",
                    subtitle = "RD Gateway untuk jaringan kantor / tanpa port langsung",
                    checked = gatewayOn,
                    onCheckedChange = { gatewayOn = it },
                    leading = XyIcons.Lock,
                )
                if (gatewayOn) {
                    Spacer(Modifier.height(6.dp))
                    XyField(
                        value = gwHost,
                        onValueChange = { gwHost = it.trim() },
                        label = "Gateway host",
                        hint = "gw.kantor.com",
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwPort,
                        onValueChange = { gwPort = it.filter(Char::isDigit).take(5) },
                        label = "Gateway port",
                        hint = "443",
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwUser,
                        onValueChange = { gwUser = it },
                        label = "Gateway username (opsional)",
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwPass,
                        onValueChange = { gwPass = it },
                        label = "Gateway password (opsional)",
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                    )
                    Spacer(Modifier.height(12.dp))
                    XyField(
                        value = gwDomain,
                        onValueChange = { gwDomain = it },
                        label = "Gateway domain (opsional)",
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
                text = "Simpan",
                onClick = { submit(false) },
                primary = false,
                modifier = Modifier.weight(1f),
            )
            XyPillButton(
                text = "Simpan & connect",
                onClick = { submit(true) },
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}
