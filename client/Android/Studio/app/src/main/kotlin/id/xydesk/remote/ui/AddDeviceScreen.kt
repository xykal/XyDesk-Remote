package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.freerdp.freerdpcore.services.LibFreeRDP
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.RdpOptions
import id.xydesk.remote.core.WakeOnLan
import id.xydesk.remote.core.XySecurityProtocol
import id.xydesk.remote.core.XyStreamProfile
import id.xydesk.remote.core.formatRdpEndpoint
import id.xydesk.remote.core.parseRdpEndpoint
import id.xydesk.remote.core.XyAudioMode
import id.xydesk.remote.core.XyGateway
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.xydesk.remote.ui.components.XySlider
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyDialog
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
    var host by remember(existing) {
        mutableStateOf(existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty())
    }
    var user by remember { mutableStateOf(existing?.username.orEmpty()) }
    var pass by remember { mutableStateOf(existing?.password.orEmpty()) }
    var domain by remember { mutableStateOf(existing?.domain.orEmpty()) }
    var rememberPass by remember { mutableStateOf(existing?.password?.isNotEmpty() ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val stored = remember(deviceId, existing) {
        if (existing != null) {
            RdpOptions.of(context, deviceId)
        } else {
            // Perangkat baru mewarisi default General & Security.
            val app = AppPrefs(context)
            val baseProfile = XyStreamProfile.fromCode(app.defaultStreamProfile)
            RdpOptions(
                clipboard = app.defaultClipboard,
                localDrive = app.defaultLocalDrive,
                udpTransport = app.defaultUdp,
                networkAutoDetect = app.defaultNetAuto,
                h264 = app.defaultH264,
                dynamicResolution = app.defaultDynamicResolution,
                asyncUpdate = app.defaultAsyncUpdate,
                asyncChannels = app.defaultAsyncChannels,
                securityProtocol = XySecurityProtocol.fromCode(app.defaultSecurityProtocol),
                tlsSecLevel = app.defaultTlsSecLevel,
            ).applyStreamProfile(baseProfile).copy(
                udpTransport = app.defaultUdp,
                networkAutoDetect = app.defaultNetAuto,
                h264 = app.defaultH264,
                dynamicResolution = app.defaultDynamicResolution,
                asyncUpdate = app.defaultAsyncUpdate,
                asyncChannels = app.defaultAsyncChannels,
            )
        }
    }
    var options by remember { mutableStateOf(stored) }
    var wolStatusText by remember { mutableStateOf<String?>(null) }
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
    var confirmDiscard by remember { mutableStateOf(false) }
    var validationPopup by remember { mutableStateOf<String?>(null) }

    val initialHost = remember(existing) {
        existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty()
    }
    val hasChanges = host != initialHost ||
        label != existing?.label.orEmpty() ||
        user != existing?.username.orEmpty() ||
        pass != existing?.password.orEmpty() ||
        domain != existing?.domain.orEmpty() ||
        options != stored ||
        gatewayOn != (stored.gateway != null)

    fun requestCancel() {
        if (hasChanges) confirmDiscard = true else onCancel()
    }

    BackHandler {
        when {
            validationPopup != null -> validationPopup = null
            confirmDiscard -> confirmDiscard = false
            else -> requestCancel()
        }
    }

    fun submit(connect: Boolean) {
        val endpoint = parseRdpEndpoint(host)
        if (host.isBlank()) {
            val msg = xyNow("Alamat host wajib diisi.", "Host address is required.")
            error = msg
            validationPopup = msg
            return
        }
        if (endpoint == null) {
            val msg = xyNow(
                "Alamat/port tidak valid. Port harus 1-65535; IPv6 dengan port gunakan [alamat]:port.",
                "Invalid address/port. Port must be 1-65535; write IPv6 with a port as [address]:port.",
            )
            error = msg
            validationPopup = msg
            return
        }
        if (gatewayOn && gwHost.isBlank()) {
            val msg = xyNow(
                "Gateway diaktifkan tetapi alamat Gateway host masih kosong.",
                "RDP Gateway is enabled, but the Gateway host address is empty.",
            )
            error = msg
            validationPopup = msg
            return
        }
        if (options.wolMacAddress.isNotBlank() && WakeOnLan.parseMacBytes(options.wolMacAddress) == null) {
            val msg = xyNow(
                "Format MAC Address Wake-on-LAN tidak valid. Gunakan 6 pasang heksadesimal seperti AA:BB:CC:DD:EE:FF.",
                "Invalid Wake-on-LAN MAC address format. Use 6 hex pairs like AA:BB:CC:DD:EE:FF.",
            )
            error = msg
            validationPopup = msg
            return
        }
        val profile = try {
            ConnectionProfile(
                host = endpoint.host,
                port = endpoint.port,
                username = user.trim().ifEmpty { null },
                password = pass.ifEmpty { null },
                domain = domain.trim().ifEmpty { null },
                label = label.trim().ifEmpty { null },
                key = deviceKey,
            )
        } catch (e: IllegalArgumentException) {
            error = e.message
            validationPopup = e.message
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
            onBack = { requestCancel() },
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
                    onValueChange = { host = it },
                    label = xy("Host / IP (port opsional)", "Host / IP (optional port)"),
                    hint = xy("pc.tailnet.ts.net atau [2001:db8::1]:3390", "pc.example.com or [2001:db8::1]:3390"),
                    keyboardType = KeyboardType.Uri,
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
                Text(
                    xy(
                        "Default \"Otomatis\" selalu 16:9 (pas untuk desktop Windows). " +
                            "\"Ikuti layar HP\" memakai rasio HP — untuk video/game.",
                        "The \"Automatic\" default is always 16:9 (right for Windows " +
                            "desktops). \"Follow phone screen\" uses the phone ratio — for video/games.",
                    ),
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
                    xy("Skala lokal awal: {0}%", "Initial local zoom: {0}%", dpi),
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
                        "Zoom lokal ini menjadi nilai awal saat sesi dibuka. DPI desktop Windows " +
                            "diatur terpisah dari panel sesi; resolusi remote juga kontrol terpisah.",
                        "This local zoom is the starting value when a session opens. Windows desktop DPI " +
                            "is controlled separately in the session panel; remote resolution is separate too.",
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
                    subtitle = xy("Folder HP muncul sebagai drive 'XyDesk' di PC remote", "Phone folder appears as the 'XyDesk' drive on the remote PC"),
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

            XySectionLabel(xy("Streaming & Latensi", "Streaming & Latency"))
            XyCard {
                Text(
                    xy("Profil Streaming & Latensi", "Streaming & Latency Profile"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    xy(
                        "Pilih profil instan atau atur codec, warna, dan optimasi latensi di bawah.",
                        "Pick an instant preset or customize codec, color depth, and latency flags below.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val streamPresets = listOf(
                    XyStreamProfile.BALANCED to xy("Seimbang", "Balanced"),
                    XyStreamProfile.LOW_LATENCY to xy("Latensi Ultra-Rendah", "Ultra-Low Latency"),
                    XyStreamProfile.HIGH_COLOR to xy("Warna Akurat (AVC444)", "Accurate Color (AVC444)"),
                    XyStreamProfile.DATA_SAVER to xy("Hemat Kuota", "Data Saver"),
                )
                streamPresets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (preset, label) ->
                            XyPillButton(
                                text = label,
                                onClick = { options = options.applyStreamProfile(preset) },
                                primary = options.streamProfile == preset,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (options.streamProfile == XyStreamProfile.CUSTOM) {
                    Text(
                        xy("Profil aktif: Kustom", "Active profile: Custom"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                XyToggleRow(
                    title = xy("Transport UDP (RDP-UDP + FEC)", "UDP transport (RDP-UDP + FEC)"),
                    subtitle = xy(
                        "RDP-UDP + FEC; lebih halus untuk gerakan cepat, butuh dukungan server",
                        "RDP-UDP + FEC; smoother fast motion, needs server support",
                    ),
                    checked = options.udpTransport,
                    onCheckedChange = { options = options.copy(udpTransport = it) },
                    leading = XyIcons.Wifi,
                )
                XyToggleRow(
                    title = xy("Deteksi bandwidth otomatis", "Automatic bandwidth detection"),
                    subtitle = xy(
                        "Profil otomatis FreeRDP mengikuti kondisi jaringan",
                        "FreeRDP auto profile adapts to network conditions",
                    ),
                    checked = options.networkAutoDetect,
                    onCheckedChange = {
                        options = options.copy(networkAutoDetect = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                    leading = XyIcons.Sliders,
                    enabled = !options.lowBandwidth,
                )
                XyToggleRow(
                    title = xy("Jaringan terbatas (broadband-low)", "Low-bandwidth mode"),
                    subtitle = xy(
                        "Pakai profil broadband-low + AVC420 untuk koneksi seluler/hemat",
                        "Use broadband-low + AVC420 hints for cellular/metered connections",
                    ),
                    checked = options.lowBandwidth,
                    onCheckedChange = {
                        options = options.copy(lowBandwidth = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                    leading = XyIcons.Sliders,
                )
                XyToggleRow(
                    title = xy("H.264 / RemoteFX (GFX)", "H.264 / RemoteFX (GFX)"),
                    subtitle = xy(
                        "Akselerasi hardware H.264 untuk video dan animasi 60 FPS",
                        "H.264 hardware acceleration for 60 FPS video and animations",
                    ),
                    checked = options.h264,
                    onCheckedChange = {
                        options = options.copy(h264 = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                    leading = XyIcons.Grid,
                )
                if (options.h264 && !options.lowBandwidth) {
                    XyToggleRow(
                        title = xy("Chroma 4:4:4 penuh (AVC444)", "Full Chroma 4:4:4 (AVC444)"),
                        subtitle = xy(
                            "Teks & garis warna tajam (matikan untuk pakai AVC420 yang lebih ringan)",
                            "Crisp text & colored edges (turn off for lighter AVC420)",
                        ),
                        checked = options.avc444,
                        onCheckedChange = {
                            options = options.copy(avc444 = it, streamProfile = XyStreamProfile.CUSTOM)
                        },
                    )
                }
                XyToggleRow(
                    title = xy("Async Frame Update (+async-update)", "Async Frame Update (+async-update)"),
                    subtitle = xy(
                        "Render layar tidak menahan antrean input untuk respons maksimal",
                        "Decouple frame rendering from input queue for maximum responsiveness",
                    ),
                    checked = options.asyncUpdate,
                    onCheckedChange = {
                        options = options.copy(asyncUpdate = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                XyToggleRow(
                    title = xy("Async Input & Channels (+async-channels)", "Async Input & Channels (+async-channels)"),
                    subtitle = xy(
                        "Proses kanal input, audio, dan clipboard di thread mandiri",
                        "Process input, audio, and clipboard channels on dedicated threads",
                    ),
                    checked = options.asyncChannels,
                    onCheckedChange = {
                        options = options.copy(asyncChannels = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                XyToggleRow(
                    title = xy("Kompresi stream RDP (+compression)", "RDP stream compression (+compression)"),
                    subtitle = xy(
                        "Kompresi paket data untuk menghemat bandwidth",
                        "Compress RDP data packets to save bandwidth",
                    ),
                    checked = options.compression,
                    onCheckedChange = {
                        options = options.copy(compression = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    xy("Kedalaman warna (Color Depth)", "Color depth (BPP)"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                val depthValues = listOf(32, 24, 16)
                XySegmented(
                    options = listOf("32-bit", "24-bit", "16-bit"),
                    selectedIndex = depthValues.indexOf(options.colorDepth).coerceAtLeast(0),
                    onSelect = {
                        options = options.copy(
                            colorDepth = depthValues[it],
                            streamProfile = XyStreamProfile.CUSTOM,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    xy("Pengalaman Visual Desktop Windows", "Windows Desktop Visual Experience"),
                    style = MaterialTheme.typography.titleSmall,
                )
                XyToggleRow(
                    title = xy("Font Smoothing (ClearType)", "Font Smoothing (ClearType)"),
                    checked = options.fontSmoothing,
                    onCheckedChange = {
                        options = options.copy(fontSmoothing = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                XyToggleRow(
                    title = xy("Desktop Composition (Aero DWM)", "Desktop Composition (Aero DWM)"),
                    checked = options.desktopComposition,
                    onCheckedChange = {
                        options = options.copy(desktopComposition = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                XyToggleRow(
                    title = xy("Wallpaper Desktop", "Desktop Wallpaper"),
                    checked = options.desktopWallpaper,
                    onCheckedChange = {
                        options = options.copy(desktopWallpaper = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                XyToggleRow(
                    title = xy("Tema Visual Windows", "Windows Visual Themes"),
                    checked = options.visualThemes,
                    onCheckedChange = {
                        options = options.copy(visualThemes = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                XyToggleRow(
                    title = xy("Tarik Jendela Penuh (Full Window Drag)", "Full Window Drag"),
                    checked = options.fullWindowDrag,
                    onCheckedChange = {
                        options = options.copy(fullWindowDrag = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
                XyToggleRow(
                    title = xy("Animasi Menu Windows", "Windows Menu Animations"),
                    checked = options.menuAnimations,
                    onCheckedChange = {
                        options = options.copy(menuAnimations = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                )
            }

            XySectionLabel(xy("Keamanan & Sesi Admin", "Security & Admin Session"))
            XyCard {
                Text(
                    xy("Protokol keamanan autentikasi", "Authentication security protocol"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                val secOptions = listOf(
                    XySecurityProtocol.AUTO to xy("Otomatis", "Auto"),
                    XySecurityProtocol.NLA to "NLA (CredSSP)",
                    XySecurityProtocol.TLS to "TLS",
                    XySecurityProtocol.RDP to xy("RDP Klasik", "Classic RDP"),
                )
                secOptions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (proto, label) ->
                            XyPillButton(
                                text = label,
                                onClick = { options = options.copy(securityProtocol = proto) },
                                primary = options.securityProtocol == proto,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    xy("Level Kebijakan OpenSSL TLS", "OpenSSL TLS Security Level"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                XySegmented(
                    options = listOf(
                        xy("Kompatibel (0)", "Legacy (0)"),
                        xy("Standar (1)", "Standard (1)"),
                        xy("Ketat (2)", "Strict (2)"),
                    ),
                    selectedIndex = options.tlsSecLevel.coerceIn(0, 2),
                    onSelect = { options = options.copy(tlsSecLevel = it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                XyToggleRow(
                    title = xy("Sesi Konsol / Admin (/admin)", "Console / Admin Session (/admin)"),
                    subtitle = xy(
                        "Masuk ke sesi konsol fisik atau sesi administrator Windows Server",
                        "Connect to the physical console or Windows Server administrator session",
                    ),
                    checked = options.consoleAdminSession,
                    onCheckedChange = { options = options.copy(consoleAdminSession = it) },
                    leading = XyIcons.Lock,
                )
                XyToggleRow(
                    title = xy("Mode Restricted Admin (/restricted-admin)", "Restricted Admin Mode (/restricted-admin)"),
                    subtitle = xy(
                        "Login NLA tanpa menyerahkan kredensial plaintext ke host remote (anti pass-the-hash)",
                        "Authenticate via NLA without sending plaintext credentials to the remote host",
                    ),
                    checked = options.restrictedAdmin,
                    onCheckedChange = { options = options.copy(restrictedAdmin = it) },
                    leading = XyIcons.Lock,
                )
            }

            XySectionLabel(xy("Wake-on-LAN & SSH Tunnel", "Wake-on-LAN & SSH Tunnel"))
            XyCard {
                Text(
                    xy("Wake-on-LAN (Magic Packet)", "Wake-on-LAN (Magic Packet)"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    xy(
                        "Nyalakan PC dari kondisi Sleep/Shutdown lewat jaringan lokal atau VPN sebelum konek RDP.",
                        "Wake the PC from Sleep/Shutdown over LAN or VPN before connecting via RDP.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                XyField(
                    value = options.wolMacAddress,
                    onValueChange = { options = options.copy(wolMacAddress = it.trim()) },
                    label = xy("MAC Address kartu jaringan PC (opsional)", "PC network card MAC Address (optional)"),
                    hint = "AA:BB:CC:DD:EE:FF",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyField(
                        value = options.wolBroadcastIp,
                        onValueChange = { options = options.copy(wolBroadcastIp = it.trim()) },
                        label = xy("Broadcast / IP tujuan", "Broadcast / target IP"),
                        hint = "255.255.255.255",
                        modifier = Modifier.weight(1.4f),
                    )
                    XyField(
                        value = options.wolPort.toString(),
                        onValueChange = {
                            val p = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 9
                            options = options.copy(wolPort = p.coerceIn(1, 65535))
                        },
                        label = xy("Port UDP", "UDP Port"),
                        hint = "9",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(0.7f),
                    )
                }
                if (options.wolMacAddress.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    XyPillButton(
                        text = xy("Kirim Tes Magic Packet Sekarang", "Send Test Magic Packet Now"),
                        onClick = {
                            val mac = options.wolMacAddress
                            if (WakeOnLan.parseMacBytes(mac) == null) {
                                wolStatusText = xyNow(
                                    "MAC Address belum valid (contoh: AA:BB:CC:DD:EE:FF)",
                                    "Invalid MAC address (e.g. AA:BB:CC:DD:EE:FF)",
                                )
                            } else {
                                wolStatusText = xyNow("Mengirim Magic Packet...", "Sending Magic Packet...")
                                scope.launch {
                                    val endpointHost = parseRdpEndpoint(host)?.host
                                    val targets = buildList {
                                        add(options.wolBroadcastIp.ifBlank { "255.255.255.255" })
                                        if (!endpointHost.isNullOrBlank()) add(endpointHost)
                                    }.distinct()
                                    val result = withContext(Dispatchers.IO) {
                                        var lastRes = Result.success(Unit)
                                        targets.forEach { target ->
                                            lastRes = WakeOnLan.sendMagicPacket(mac, target, options.wolPort)
                                        }
                                        lastRes
                                    }
                                    wolStatusText = if (result.isSuccess) {
                                        xyNow(
                                            "Magic Packet terkirim ke ${targets.joinToString()} (UDP ${options.wolPort})",
                                            "Magic Packet sent to ${targets.joinToString()} (UDP ${options.wolPort})",
                                        )
                                    } else {
                                        xyNow(
                                            "Gagal mengirim Magic Packet: ${result.exceptionOrNull()?.message ?: "error"}",
                                            "Failed to send Magic Packet: ${result.exceptionOrNull()?.message ?: "error"}",
                                        )
                                    }
                                }
                            }
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    wolStatusText?.let { msg ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    xy("SSH Jump-Host / Port Forwarding Helper", "SSH Jump-Host / Port Forwarding Helper"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    xy(
                        "Jika PC berada di belakang firewall/NAT dan diakses via tunnel SSH lokal (mis. Termux), simpan detail jump-host di sini untuk membuat perintah port-forward otomatis.",
                        "If the PC sits behind a firewall/NAT and is reached via a local SSH tunnel (e.g. Termux), save jump-host details here to generate the port-forwarding command.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                XyField(
                    value = options.sshJumpHost,
                    onValueChange = { options = options.copy(sshJumpHost = it.trim()) },
                    label = xy("SSH Jump Host (user@host:port)", "SSH Jump Host (user@host:port)"),
                    hint = "user@ssh.kantor.com:22",
                )
                if (options.sshJumpHost.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    XyField(
                        value = options.sshRemoteTarget,
                        onValueChange = { options = options.copy(sshRemoteTarget = it.trim()) },
                        label = xy("Target LAN di balik SSH (host:port)", "LAN target behind SSH (host:port)"),
                        hint = "192.168.1.50:3389",
                    )
                    val parsedEndpoint = parseRdpEndpoint(host)
                    val localTunnelPort = if (parsedEndpoint?.host == "127.0.0.1" || parsedEndpoint?.host == "localhost") {
                        parsedEndpoint.port
                    } else {
                        13389
                    }
                    val targetParts = options.sshRemoteTarget.ifBlank { "127.0.0.1:3389" }
                    val targetParsed = parseRdpEndpoint(targetParts)
                    val sshCmd = WakeOnLan.buildSshTunnelCommand(
                        sshJumpHost = options.sshJumpHost,
                        localPort = localTunnelPort,
                        remoteHost = targetParsed?.host ?: "127.0.0.1",
                        remotePort = targetParsed?.port ?: 3389,
                    )
                    if (sshCmd.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            sshCmd,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            XyPillButton(
                                text = xy("Salin Perintah SSH", "Copy SSH Command"),
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("SSH Tunnel", sshCmd))
                                    wolStatusText = xyNow("Perintah SSH disalin ke clipboard.", "SSH command copied to clipboard.")
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                            XyPillButton(
                                text = xy("Pakai 127.0.0.1:$localTunnelPort", "Use 127.0.0.1:$localTunnelPort"),
                                onClick = { host = "127.0.0.1:$localTunnelPort" },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
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

    if (confirmDiscard) {
        XyDialog(
            title = xy("Buang perubahan?", "Discard changes?"),
            body = xy(
                "Perubahan pada pengaturan perangkat ini belum disimpan. Kembali tanpa menyimpan?",
                "Changes to this device configuration have not been saved. Leave without saving?",
            ),
            confirmLabel = xy("Buang", "Discard"),
            onConfirm = {
                confirmDiscard = false
                onCancel()
            },
            dismissLabel = xy("Lanjut ubah", "Keep editing"),
            onDismiss = { confirmDiscard = false },
        )
    }

    validationPopup?.let { msg ->
        XyDialog(
            title = xy("Data belum valid", "Validation error"),
            body = msg,
            confirmLabel = xy("Mengerti", "Got it"),
            onConfirm = { validationPopup = null },
            onDismiss = { validationPopup = null },
        )
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
    // Cek ulang izin saat layar ini kembali ke depan. Dulu `granted` hanya
    // dibaca sekali tepat setelah startActivity — padahal activity asal
    // tidak pernah pause-hilang di momen itu, jadi status selalu "terbatas"
    // walaupun user baru saja memberi izin di Setelan.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = LibFreeRDP.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
