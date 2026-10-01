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
import id.xydesk.remote.core.DiscoveredPcHost
import id.xydesk.remote.core.LanScanner
import id.xydesk.remote.core.RdpOptions
import id.xydesk.remote.core.WakeOnLan
import id.xydesk.remote.core.XyGpuProfile
import id.xydesk.remote.core.XyPcStreamEngine
import id.xydesk.remote.core.XySecurityProtocol
import id.xydesk.remote.core.XyStreamProfile
import id.xydesk.remote.core.encodeIpv4ToPcId
import id.xydesk.remote.core.formatRdpEndpoint
import id.xydesk.remote.core.parsePcIdOrEndpoint
import id.xydesk.remote.core.parseRdpEndpoint
import id.xydesk.remote.core.XyAudioMode
import id.xydesk.remote.core.XyGateway
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.components.XyNoticeBus
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.xydesk.remote.ui.components.XySlider
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyDialog
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyOverlay
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
private data class SavedAccountOption(
    val username: String,
    val domain: String,
    val password: String,
)

@Composable
fun AddDeviceScreen(
    existing: ConnectionProfile?,
    savedUsers: List<String>,
    savedProfiles: List<ConnectionProfile> = emptyList(),
    initialPcQuickMode: Boolean = false,
    onCancel: () -> Unit,
    onSubmit: (profile: ConnectionProfile, rememberPassword: Boolean, connect: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val vault = remember { CredentialVault(context.applicationContext) }
    // Kunci tetap: dipakai ulang saat mengubah perangkat supaya simpan tidak
    // menghasilkan baris kedua. Data lama (belum punya key) memakai id-nya.
    val deviceKey = remember(existing) {
        existing?.key ?: existing?.id ?: java.util.UUID.randomUUID().toString()
    }
    val deviceId = deviceKey
    val existingStored = remember(deviceId, existing) {
        if (existing != null) RdpOptions.of(context, deviceId) else null
    }
    var isPcQuickMode by remember(existing, initialPcQuickMode) {
        mutableStateOf(
            if (existing != null) existingStored?.pcConnectMode == true
            else initialPcQuickMode,
        )
    }

    // "Setelan cepat" menyembunyikan bagian lanjutan supaya layar tidak panjang
    // dan tidak perlu scroll jauh saat cuma mau konek.
    var showAdvanced by remember { mutableStateOf(false) }

    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var host by remember(existing) {
        mutableStateOf(existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty())
    }
    var user by remember {
        mutableStateOf(existing?.username.orEmpty())
    }
    var pass by remember {
        mutableStateOf(
            existing?.password
                ?: existing?.let { vault.get(it.id) }.orEmpty(),
        )
    }
    var domain by remember { mutableStateOf(existing?.domain.orEmpty()) }
    var rememberPass by remember { mutableStateOf(existing?.password?.isNotEmpty() ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    val baseAccounts = remember(savedProfiles, savedUsers, existing) {
        val list = mutableListOf<SavedAccountOption>()
        savedProfiles.forEach { prof ->
            val u = prof.username?.trim().orEmpty()
            if (u.isNotEmpty() && !u.equals("XyDesk", ignoreCase = true)) {
                val p = prof.password ?: vault.get(prof.id).orEmpty()
                val d = prof.domain?.trim().orEmpty()
                if (list.none { it.username.equals(u, ignoreCase = true) && it.domain.equals(d, ignoreCase = true) }) {
                    list.add(SavedAccountOption(u, d, p))
                }
            }
        }
        savedUsers.forEach { u ->
            val clean = u.trim()
            if (clean.isNotEmpty() && !clean.equals("XyDesk", ignoreCase = true) &&
                list.none { it.username.equals(clean, ignoreCase = true) }
            ) {
                list.add(SavedAccountOption(clean, "", ""))
            }
        }
        list
    }
    var extraAccounts by remember { mutableStateOf<List<SavedAccountOption>>(emptyList()) }
    val allAccounts = remember(baseAccounts, extraAccounts) {
        (extraAccounts + baseAccounts).distinctBy { "${it.domain.lowercase()}\\${it.username.lowercase()}" }
    }
    // Jika sudah ada akun yang terisi (mis. saat edit perangkat atau setelah
    // user input akun), sembunyikan form dan tampilkan sebagai dropdown.
    var showCredentialForm by remember(existing) {
        mutableStateOf(existing?.username.isNullOrBlank() && baseAccounts.isEmpty())
    }
    var accountDropdownOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val stored = remember(deviceId, existing, initialPcQuickMode) {
        if (existingStored != null) {
            existingStored
        } else {
            val app = AppPrefs(context)
            val baseProfile = if (initialPcQuickMode) {
                XyStreamProfile.ULTRA_LOW_LATENCY
            } else {
                XyStreamProfile.entries.getOrElse(app.defaultStreamProfile) {
                    XyStreamProfile.AUTO
                }
            }
            val baseSecProto = if (initialPcQuickMode) {
                XySecurityProtocol.NLA
            } else {
                XySecurityProtocol.entries.getOrElse(app.defaultSecurityProtocol) {
                    XySecurityProtocol.AUTO
                }
            }
            RdpOptions(
                pcConnectMode = initialPcQuickMode,
                clipboard = app.defaultClipboard,
                localDrive = if (initialPcQuickMode) false else app.defaultLocalDrive,
                udpTransport = if (initialPcQuickMode) true else app.defaultUdp,
                networkAutoDetect = app.defaultNetAuto,
                h264 = true,
                dynamicResolution = if (initialPcQuickMode) false else app.defaultDynamicResolution,
                asyncUpdate = if (initialPcQuickMode) true else app.defaultAsyncUpdate,
                asyncChannels = if (initialPcQuickMode) true else app.defaultAsyncChannels,
                securityProtocol = baseSecProto,
                tlsSecLevel = if (initialPcQuickMode) 2 else app.defaultTlsSecLevel,
            ).withStreamProfile(baseProfile).copy(
                pcConnectMode = initialPcQuickMode,
                udpTransport = if (initialPcQuickMode) true else app.defaultUdp,
                networkAutoDetect = app.defaultNetAuto,
                h264 = true,
                dynamicResolution = if (initialPcQuickMode) false else app.defaultDynamicResolution,
                asyncUpdate = if (initialPcQuickMode) true else app.defaultAsyncUpdate,
                asyncChannels = if (initialPcQuickMode) true else app.defaultAsyncChannels,
                securityProtocol = baseSecProto,
                tlsSecLevel = if (initialPcQuickMode) 2 else app.defaultTlsSecLevel,
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
    var lanScanModalOpen by remember { mutableStateOf(false) }
    var lanScanning by remember { mutableStateOf(false) }
    var lanFoundHosts by remember { mutableStateOf<List<DiscoveredPcHost>>(emptyList()) }
    var lanSelfIp by remember { mutableStateOf<String?>(null) }

    fun triggerLanScan() {
        lanScanModalOpen = true
        if (lanScanning) return
        lanScanning = true
        scope.launch {
            val localIp = withContext(Dispatchers.IO) { LanScanner.detectLocalIpv4Address() }
            lanSelfIp = localIp
            val hosts = withContext(Dispatchers.IO) { LanScanner.scanLocalSubnet() }
            lanFoundHosts = hosts
            lanScanning = false
        }
    }

    val initialHost = remember(existing) {
        existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty()
    }
    val hasChanges = if (existing == null) {
        host.isNotBlank() || label.isNotBlank() || pass.isNotBlank()
    } else {
        host != initialHost ||
            label != existing.label.orEmpty() ||
            user != existing.username.orEmpty() ||
            pass != existing.password.orEmpty() ||
            domain != existing.domain.orEmpty() ||
            options != stored ||
            gatewayOn != (stored.gateway != null)
    }

    fun requestCancel() {
        if (confirmDiscard || !hasChanges) {
            confirmDiscard = false
            onCancel()
        } else {
            confirmDiscard = true
        }
    }

    BackHandler {
        when {
            validationPopup != null -> validationPopup = null
            confirmDiscard -> confirmDiscard = false
            else -> requestCancel()
        }
    }

    fun submit(connect: Boolean) {
        val endpoint = parsePcIdOrEndpoint(host)
        if (host.isBlank()) {
            val msg = if (isPcQuickMode) {
                xyNow("ID PC atau alamat host wajib diisi.", "PC ID or host address is required.")
            } else {
                xyNow("Alamat host wajib diisi.", "Host address is required.")
            }
            error = msg
            validationPopup = msg
            return
        }
        if (endpoint == null) {
            val msg = xyNow(
                "ID PC atau Alamat/port tidak valid. Masukkan 10 digit ID PC (mis. 323-223-5826) atau host:port.",
                "Invalid PC ID or address/port. Enter a 10-digit PC ID (e.g. 323-223-5826) or host:port.",
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
        if (!options.macAddress.isNullOrBlank() && WakeOnLan.parseMacBytes(options.macAddress) == null) {
            val msg = xyNow(
                "Format MAC Address Wake-on-LAN tidak valid. Gunakan 6 pasang heksadesimal seperti AA:BB:CC:DD:EE:FF.",
                "Invalid Wake-on-LAN MAC address format. Use 6 hex pairs like AA:BB:CC:DD:EE:FF.",
            )
            error = msg
            validationPopup = msg
            return
        }
        val effectiveUser = if (isPcQuickMode) {
            if (pass.isNotBlank()) "XyDesk" else null
        } else {
            user.trim().ifEmpty { null }
        }
        val effectiveDomain = if (isPcQuickMode) null else domain.trim().ifEmpty { null }
        val profile = try {
            ConnectionProfile(
                host = endpoint.host,
                port = endpoint.port,
                username = effectiveUser,
                password = pass.ifEmpty { null },
                domain = effectiveDomain,
                label = label.trim().ifEmpty { null },
                key = deviceKey,
            )
        } catch (e: IllegalArgumentException) {
            error = e.message
            validationPopup = e.message
            return
        }
        error = null
        val finalOptions = if (isPcQuickMode) {
            options.withPcStreamEngine(options.pcStreamEngine).copy(
                pcConnectMode = true,
                pcTargetFps = options.pcTargetFps,
                pcBitrateMbps = options.pcBitrateMbps,
                rawInputMouse = options.rawInputMouse,
                udpTransport = options.udpTransport,
                clipboard = options.clipboard,
                batterySaver = options.batterySaver,
                audioMode = XyAudioMode.entries[audioIndex],
                microphone = false,
                localDrive = false,
                camera = false,
                dynamicResolution = false,
                consoleAdmin = true,
                restrictedAdmin = false,
                remoteProgram = null,
                remoteWorkDir = null,
                securityProtocol = XySecurityProtocol.NLA,
                tlsSecLevel = 2,
                gateway = null,
            )
        } else {
            options.copy(
                pcConnectMode = false,
                gpuProfile = XyGpuProfile.AUTO,
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
        }
        finalOptions.write(context, profile.id)
        if (isPcQuickMode) {
            DisplayPrefs.setResolution(context, profile.id, DisplayPrefs.AUTOMATIC)
            DisplayPrefs.setRotation(context, profile.id, DisplayPrefs.rotations[rotationIndex])
            DisplayPrefs.setDpi(context, profile.id, 100)
        } else {
            DisplayPrefs.setResolution(
                context,
                profile.id,
                DisplayPrefs.resolutionOptions[resolutionIndex].value,
            )
            DisplayPrefs.setRotation(context, profile.id, DisplayPrefs.rotations[rotationIndex])
            DisplayPrefs.setDpi(context, profile.id, dpi)
        }
        onSubmit(profile, rememberPass, connect)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        XyTopBar(
            title = when {
                existing != null && isPcQuickMode -> xy("Ubah Koneksi PC", "Edit PC Connection")
                existing != null -> xy("Ubah Perangkat RDP", "Edit RDP Device")
                isPcQuickMode -> xy("Koneksi PC [Tahap Pengembangan]", "PC Connect [Experimental]")
                else -> xy("Koneksi RDP Baru", "New RDP Connection")
            },
            onBack = { requestCancel() },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            XySegmented(
                options = listOf(
                    xy("Setelan cepat", "Quick setup"),
                    xy("Lanjutan", "Advanced"),
                ),
                selectedIndex = if (showAdvanced) 1 else 0,
                onSelect = { showAdvanced = it == 1 },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!showAdvanced) {
                Text(
                    xy(
                        "Mode cepat hanya menampilkan yang perlu untuk konek: akun, tipe koneksi, audio, " +
                            "clipboard, drive, dan kamera.",
                        "Quick mode shows only what a connection needs: account, connection type, audio, " +
                            "clipboard, drive, and camera.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (existing == null) {
                XySegmented(
                    options = listOf(
                        xy("Koneksi RDP (Desktop / Server)", "RDP Session (Desktop / Server)"),
                        xy("Koneksi PC (Direct Stream)", "PC Connect (Direct Stream)"),
                    ),
                    selectedIndex = if (isPcQuickMode) 1 else 0,
                    onSelect = { idx ->
                        val wantPc = idx == 1
                        isPcQuickMode = wantPc
                        if (wantPc) {
                            options = options.withPcStreamEngine(options.pcStreamEngine)
                        } else {
                            options = options.withStreamProfile(XyStreamProfile.AUTO).copy(
                                pcConnectMode = false,
                                gpuProfile = XyGpuProfile.AUTO,
                                dynamicResolution = true,
                                consoleAdmin = false,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            XySectionLabel(
                if (isPcQuickMode) xy("Identitas PC · Direct Stream 1:1 (Tanpa Akun RDP)", "PC Identity · 1:1 Direct Stream (No RDP Account)")
                else xy("Alamat Host / Server RDP", "RDP Host / Server Address")
            )
            XyCard {
                XyField(
                    value = label,
                    onValueChange = { label = it },
                    label = if (isPcQuickMode) xy("Label PC (opsional)", "PC Label (optional)")
                    else xy("Nama perangkat", "Device name"),
                    hint = if (isPcQuickMode) xy("mis. PC Gaming / Rig Utama", "e.g. Gaming PC / Main Rig")
                    else xy("mis. PC kantor", "e.g. Office PC"),
                )
                Spacer(Modifier.height(14.dp))
                XyField(
                    value = host,
                    onValueChange = { host = it },
                    label = if (isPcQuickMode) {
                        xy("ID PC (10 Digit / XY-ID)", "PC ID (10-Digit / XY-ID)")
                    } else {
                        xy("Host / IP (port opsional)", "Host / IP (optional port)")
                    },
                    hint = if (isPcQuickMode) {
                        xy("323-223-5826 atau XY-C0A8-0132", "323-223-5826 or XY-C0A8-0132")
                    } else {
                        xy("pc.tailnet.ts.net atau [2001:db8::1]:3390", "pc.example.com or [2001:db8::1]:3390")
                    },
                    keyboardType = KeyboardType.Uri,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
                Spacer(Modifier.height(10.dp))
                XyPillButton(
                    text = if (isPcQuickMode) {
                        xy("Pindai PC Otomatis di Wi-Fi (LAN)", "Auto-Scan PCs on Wi-Fi (LAN)")
                    } else {
                        xy("Pindai Host RDP di Jaringan Lokal (LAN)", "Scan RDP Hosts on Local Network (LAN)")
                    },
                    onClick = { triggerLanScan() },
                    icon = XyIcons.Wifi,
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isPcQuickMode) {
                    Spacer(Modifier.height(14.dp))
                    XyField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = xy("PIN / Kunci Sesi XyDeskHost (opsional)", "XyDeskHost Session PIN / Key (optional)"),
                        hint = xy("PIN 6 digit dari XyDeskHost.exe (bukan akun Windows)", "6-digit PIN from XyDeskHost.exe (not Windows account)"),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                    )
                }
                val resolvedPreview = remember(host) { parsePcIdOrEndpoint(host) }
                val encodedPcId = remember(resolvedPreview) {
                    resolvedPreview?.let { encodeIpv4ToPcId(it.host) }
                }
                if (resolvedPreview != null && host.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append(xy("Target: ", "Target: "))
                            append(resolvedPreview.host).append(':').append(resolvedPreview.port)
                            if (encodedPcId != null) {
                                append("  ·  ID PC: ").append(encodedPcId)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isPcQuickMode) {
                XySectionLabel(
                    xy(
                        "Agen PC Native (XyDeskHost.exe · Tahap Pengembangan)",
                        "Native PC Agent (XyDeskHost.exe · In Development)",
                    ),
                )
                XyCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            xy("XyDeskHost.exe + .dll + QUIC + E2EE", "XyDeskHost.exe + .dll + QUIC + E2EE"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Box(
                            Modifier
                                .clip(XyPill)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, XyPill)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                xy("TAHAP PENGEMBANGAN", "EXPERIMENTAL"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        xy(
                            "Berbeda dari RDP standar: Koneksi PC tidak memakai kredensial akun/domain Windows dan mengunci resolusi + DPI 1:1 mengikuti monitor fisik PC (DXGI Desktop Duplication). Seluruh paket dienkripsi End-to-End (TLS 1.3 + Pinning Keystore) dengan jalur Ultra-Low Latency untuk game FPS.",
                            "Unlike standard RDP: PC Connection does not use Windows account/domain credentials and locks resolution + DPI 1:1 to the physical PC monitor (DXGI Desktop Duplication). All traffic is End-to-End Encrypted (TLS 1.3 + Keystore Pinning) with an Ultra-Low Latency path for FPS games.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton(
                            text = xy("Salin Perintah Setup PC", "Copy PC Setup Command"),
                            onClick = {
                                val cmd = "irm https://rdp.xydesk.my.id/host | iex"
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("xydesk-host-cmd", cmd))
                                XyNoticeBus.post(
                                    xyNow(
                                        "Perintah PowerShell disalin! Jalankan di PowerShell Admin pada PC Anda.",
                                        "PowerShell command copied! Run it in Admin PowerShell on your PC.",
                                    ),
                                )
                            },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        XyPillButton(
                            text = xy("Info XyDeskHost", "XyDeskHost Info"),
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://rdp.xydesk.my.id/host")),
                                    )
                                }
                            },
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                XySectionLabel(
                    xy(
                        "Mesin Direct PC Stream (Eksklusif Koneksi PC)",
                        "Direct PC Stream Engine (PC Connect Exclusive)",
                    ),
                )
                XyCard {
                    Text(
                        xy("Arsitektur Pipeline Direct Stream", "Direct Stream Pipeline Architecture"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        xy(
                            "Sistem streaming khusus Koneksi PC (tidak tersedia di mode RDP Desktop). Mengunci monitor fisik 1:1 dengan antrean frame nol.",
                            "Dedicated streaming pipeline for PC Connect (unavailable in RDP Desktop mode). Locks 1:1 to the physical monitor with zero frame queue.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        XyPcStreamEngine.entries.forEach { engine ->
                            val selected = options.pcStreamEngine == engine
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.medium,
                                    )
                                    .clickable {
                                        options = options.withPcStreamEngine(engine)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(xy(engine.title, engine.titleEn), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        xy(engine.detail, engine.detailEn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .clip(XyPill)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, XyPill)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = engine.badge,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        xy("Target Kecepatan Frame (Direct Pacing)", "Target Frame Rate (Direct Pacing)"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    val fpsChoices = listOf(30, 60, 90, 120)
                    XySegmented(
                        options = fpsChoices.map { "$it FPS" },
                        selectedIndex = fpsChoices.indexOf(options.pcTargetFps).coerceAtLeast(1),
                        onSelect = { idx ->
                            options = options.copy(pcTargetFps = fpsChoices[idx], pcConnectMode = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        xy("Target Bitrate Video: {0} Mbps", "Target Video Bitrate: {0} Mbps", options.pcBitrateMbps),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    XySlider(
                        value = options.pcBitrateMbps.toFloat(),
                        onValueChange = { options = options.copy(pcBitrateMbps = it.toInt().coerceIn(5, 80)) },
                        valueRange = 5f..80f,
                        steps = 14,
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        xy("Binding Hardware GPU Encoder Host (NVENC / AMF / QuickSync)", "Host Hardware GPU Encoder Binding (NVENC / AMF / QuickSync)"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        xy(
                            "Eksklusif Koneksi PC: ikat langsung pipeline encode ke arsitektur kartu grafis PC Anda.",
                            "PC Connect Exclusive: bind the encode pipeline directly to your PC graphics card architecture.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        XyGpuProfile.entries.forEach { gpu ->
                            val active = options.gpuProfile == gpu
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .border(
                                        1.dp,
                                        if (active) MaterialTheme.colorScheme.outlineVariant
                                        else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.medium,
                                    )
                                    .clickable { options = options.withGpuProfile(gpu).copy(pcConnectMode = true) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(xy(gpu.title, gpu.titleEn), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        xy(gpu.detail, gpu.detailEn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        xy(
                            "Info Layar: Resolusi dan DPI dikunci 1:1 mengikuti monitor konsol fisik PC (DXGI Desktop Duplication). Fitur multi-user virtual RDP, Dynamic Resolution DISP, dan RD Gateway dinonaktifkan pada mode ini.",
                            "Display Info: Resolution and DPI are locked 1:1 to the physical PC console monitor (DXGI Desktop Duplication). Virtual multi-user RDP, DISP Dynamic Resolution, and RD Gateway are disabled in this mode.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                XySectionLabel(xy("Input Game FPS, Transport & Wake-on-LAN PC", "FPS Game Input, Transport & PC Wake-on-LAN"))
                XyCard {
                    XyToggleRow(
                        title = xy("Mouse Relatif RawInput (Rotasi 360° Game FPS)", "RawInput Relative Mouse (360° FPS Camera)"),
                        subtitle = xy(
                            "Kirim delta pergerakan mouse murni tanpa batas tepi layar untuk game shooter/3D",
                            "Send pure relative mouse deltas without screen-edge clamping for shooter/3D games",
                        ),
                        checked = options.rawInputMouse,
                        onCheckedChange = { options = options.copy(rawInputMouse = it) },
                        leading = XyIcons.Sliders,
                    )
                    XyToggleRow(
                        title = xy("Transport QUIC / UDP Datagram (Zero HoL Blocking)", "QUIC / UDP Datagram Transport (Zero HoL Blocking)"),
                        subtitle = xy(
                            "Prioritaskan frame terbaru tanpa antrean TCP yang menahan paket saat jitter",
                            "Prioritize newest frames without TCP head-of-line blocking during jitter",
                        ),
                        checked = options.udpTransport,
                        onCheckedChange = { options = options.copy(udpTransport = it) },
                        leading = XyIcons.Wifi,
                    )
                    XyToggleRow(
                        title = xy("Audio Real-Time Latensi Rendah", "Low-Latency Real-Time Audio"),
                        subtitle = xy(
                            "Streaming suara game & desktop langsung ke HP",
                            "Stream game and desktop audio directly to your phone",
                        ),
                        checked = audioIndex == 0,
                        onCheckedChange = { audioIndex = if (it) 0 else 2 },
                        leading = XyIcons.Mic,
                    )
                    XyToggleRow(
                        title = xy("Clipboard Otomatis (Anti-Putus Teks Panjang)", "Auto Clipboard (Long-Text Anti-Disconnect)"),
                        subtitle = xy(
                            "Sinkronisasi teks & gambar otomatis dengan proteksi ukuran buffer aman",
                            "Two-way text & image sync with safe buffer ceiling protection",
                        ),
                        checked = options.clipboard,
                        onCheckedChange = { options = options.copy(clipboard = it) },
                        leading = XyIcons.Clip,
                    )
                    XyToggleRow(
                        title = xy("Mode Hemat Baterai & Suhu Dingin (VSYNC Pacing)", "Battery Saver & Cool Thermal Mode (VSYNC Pacing)"),
                        subtitle = xy(
                            "Gabungkan burst update per siklus layar agar HP tetap dingin dan irit baterai",
                            "Coalesce pixel bursts per VSYNC cycle to keep the phone cool and save battery",
                        ),
                        checked = options.batterySaver,
                        onCheckedChange = { options = options.copy(batterySaver = it) },
                        leading = XyIcons.Sliders,
                    )
                    Spacer(Modifier.height(10.dp))
                    XyField(
                        value = options.macAddress.orEmpty(),
                        onValueChange = { options = options.copy(macAddress = it.trim().ifEmpty { null }) },
                        label = xy("MAC Address PC untuk Wake-on-LAN (opsional)", "PC MAC Address for Wake-on-LAN (optional)"),
                        hint = "AA:BB:CC:DD:EE:FF",
                    )
                }
            } else {

            XySectionLabel(xy("Kredensial", "Credentials"))
            XyCard {
                if (!showCredentialForm) {
                    val activeAccountLabel = if (user.isNotBlank()) {
                        if (domain.isNotBlank()) "$domain\\$user" else user
                    } else {
                        xy("Tanpa Akun (Tanya saat Connect)", "No Account (Ask on Connect)")
                    }
                    val activeAccountSub = if (user.isNotBlank()) {
                        if (pass.isNotEmpty() && rememberPass) {
                            xy("Password tersimpan (AES-256-GCM) · Ketuk untuk ganti akun", "Password saved (AES-256-GCM) · Tap to switch account")
                        } else {
                            xy("Password ditanya saat connect · Ketuk untuk ganti akun", "Password asked on connect · Tap to switch account")
                        }
                    } else {
                        xy("Ketuk untuk memilih akun tersimpan atau tambah akun baru", "Tap to select a saved account or add a new account")
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .clickable { accountDropdownOpen = !accountDropdownOpen }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    xy("Akun Kredensial Aktif", "Active Credential Account"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    activeAccountLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    activeAccountSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (accountDropdownOpen) "▴" else "▾",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }

                    if (accountDropdownOpen) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                                .padding(vertical = 4.dp),
                        ) {
                            allAccounts.forEach { acc ->
                                val itemTitle = if (acc.domain.isNotBlank()) "${acc.domain}\\${acc.username}" else acc.username
                                val isSelected = acc.username.equals(user, ignoreCase = true) &&
                                    acc.domain.equals(domain, ignoreCase = true)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            user = acc.username
                                            domain = acc.domain
                                            if (acc.password.isNotEmpty()) {
                                                pass = acc.password
                                                rememberPass = true
                                            }
                                            accountDropdownOpen = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            itemTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            if (acc.password.isNotEmpty()) {
                                                xy("Akun tersimpan · Password siap pakai", "Saved account · Password ready")
                                            } else {
                                                xy("Akun tersimpan", "Saved account")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isSelected) {
                                        Text(
                                            xy("Dipilih", "Selected"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }

                            if (user.isNotBlank()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            accountDropdownOpen = false
                                            showCredentialForm = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        xy("✎ Ubah Detail / Password Akun Ini", "✎ Edit Current Account / Password"),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            }

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        user = ""
                                        pass = ""
                                        domain = ""
                                        accountDropdownOpen = false
                                        showCredentialForm = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    xy("+ Tambah Akun Baru", "+ Add New Account"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        user = ""
                                        pass = ""
                                        domain = ""
                                        accountDropdownOpen = false
                                        showCredentialForm = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    xy("Tanpa Akun (Tanya saat Connect)", "No Account (Ask on Connect)"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (isPcQuickMode && pass.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        XyField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = xy("Password Akses PC", "PC Access Password"),
                            hint = xy("Masukkan password PC dari XyDeskHost", "Enter PC password from XyDeskHost"),
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                        )
                    }
                } else {
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
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (allAccounts.isNotEmpty()) {
                            XyPillButton(
                                text = xy("Pilih dari Dropdown ({0})", "Pick from Dropdown ({0})", allAccounts.size),
                                onClick = {
                                    if (user.isBlank()) {
                                        val first = allAccounts.first()
                                        user = first.username
                                        domain = first.domain
                                        if (first.password.isNotEmpty()) pass = first.password
                                    }
                                    showCredentialForm = false
                                    accountDropdownOpen = true
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        XyPillButton(
                            text = xy("Simpan Akun ke Pilihan", "Save Account to Dropdown"),
                            onClick = {
                                val u = user.trim()
                                if (u.isNotEmpty()) {
                                    extraAccounts = listOf(SavedAccountOption(u, domain.trim(), pass)) + extraAccounts
                                }
                                showCredentialForm = false
                                accountDropdownOpen = false
                            },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Mode cepat menyembunyikan bagian ini; ada di tab Lanjutan.
            if (showAdvanced) {
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
                    val audioNote = when (XyAudioMode.entries[audioIndex]) {
                        XyAudioMode.DEVICE ->
                            xy(
                                "Mode Perangkat memutar suara PC lewat kanal audio RDP (tanpa host agent). " +
                                    "Kalau di PC 'Remote Audio' dimatikan demi VB-CABLE, pilih mode Remote.",
                                "Device mode plays PC sound over the RDP audio channel (no host agent). " +
                                    "If PC 'Remote Audio' is disabled for VB-CABLE, switch to Remote mode.",
                            )
                        XyAudioMode.REMOTE ->
                            xy(
                                "Mode Remote = suara PC lewat jembatan UDP :4433, jadi XyDeskRemoteHost.exe " +
                                    "harus jalan di PC. Pakai ini kalau kanal audio RDP diblokir/kosong.",
                                "Remote mode routes PC sound through the UDP :4433 bridge, so XyDeskRemoteHost.exe " +
                                    "must run on the PC. Use it when the RDP audio channel is blocked/empty.",
                            )
                        XyAudioMode.OFF ->
                            xy("Audio dimatikan total — jembatan UDP tidak dijalankan.", "Audio fully off — the UDP bridge stays down.")
                    }
                    Text(
                        audioNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val bridgeRelevant = XyAudioMode.entries[audioIndex] == XyAudioMode.REMOTE
                XyToggleRow(
                    title = xy("Audio PC via UDP (bridge)", "PC audio over UDP (bridge)"),
                    subtitle = if (bridgeRelevant) {
                        xy(
                            "Perlu XyDeskRemoteHost.exe di PC (port UDP 4433). Tanpa itu suara PC tidak sampai.",
                            "Needs XyDeskRemoteHost.exe on the PC (UDP 4433). Without it, PC sound never arrives.",
                        )
                    } else {
                        xy(
                            "Tidak dipakai: mode Perangkat memutar suara lewat kanal RDP.",
                            "Not used: Device mode plays sound through the RDP channel.",
                        )
                    },
                    checked = options.quicAudio && bridgeRelevant,
                    onCheckedChange = { v -> options = options.copy(quicAudio = v) },
                    leading = XyIcons.Volume,
                    enabled = bridgeRelevant,
                )
                XyToggleRow(
                    title = xy("Mikrofon", "Microphone"),
                    subtitle = xy("Kirim audio HP ke remote (butuh izin mikrofon)", "Send phone audio to remote (needs mic permission)"),
                    checked = options.microphone,
                    onCheckedChange = { options = options.copy(microphone = it) },
                    leading = XyIcons.Mic,
                )
                if (options.microphone) {
                    Spacer(Modifier.height(8.dp))
                    XySectionLabel(xy("Mic lanjutan (DSP di HP)", "Microphone DSP (on phone)"))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        XySliderRow(
                            label = xy("Gain mic", "Mic gain"),
                            value = options.micGainDb.toFloat(),
                            suffix = " dB",
                            range = -12f..24f,
                            steps = 6,
                        ) { options = options.copy(micGainDb = it.toInt()) }
                        XySliderRow(
                            label = xy("Noise gate", "Noise gate"),
                            value = options.micGateDb.toFloat(),
                            suffix = " dBFS",
                            range = -70f..0f,
                            steps = 6,
                        ) { options = options.copy(micGateDb = it.toInt()) }
                        XyToggleRow(
                            title = xy("Noise suppression", "Noise suppression"),
                            subtitle = xy("High-pass + expander buang desis/AC/angin", "High-pass + expander kills hiss, AC hum, wind"),
                            checked = options.micNoiseSuppression,
                            onCheckedChange = { options = options.copy(micNoiseSuppression = it) },
                            leading = XyIcons.Mic,
                        )
                        XyToggleRow(
                            title = xy("Auto gain (AGC)", "Auto gain (AGC)"),
                            subtitle = xy("Level mic HP disamakan otomatis saat bicara", "Phone mic level auto-levelled while talking"),
                            checked = options.micAgc,
                            onCheckedChange = { options = options.copy(micAgc = it) },
                            leading = XyIcons.Mic,
                        )
                        Text(
                            xy(
                                "Mic dirender ke endpoint 'CABLE Input / XyDesk Virtual Microphone' di PC; " +
                                    "kalau tidak ada, Host Agent pakai perangkat default.",
                                "The mic renders into the PC's 'CABLE Input / XyDesk Virtual Microphone' endpoint; " +
                                    "if missing, the Host Agent falls back to the default device.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

            if (showAdvanced) {
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
                    XyStreamProfile.AUTO,
                    XyStreamProfile.BALANCED,
                    XyStreamProfile.ULTRA_LOW_LATENCY,
                    XyStreamProfile.HIGH_VISUAL,
                    XyStreamProfile.DATA_SAVER,
                )
                streamPresets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { preset ->
                            XyPillButton(
                                text = xy(preset.title, preset.titleEn),
                                onClick = { options = options.withStreamProfile(preset) },
                                primary = options.streamProfile == preset,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (options.streamProfile == XyStreamProfile.CUSTOM) {
                    Text(
                        xy("Profil aktif: Kustom Manual", "Active profile: Custom Manual"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                XyToggleRow(
                    title = xy("Mode Hemat Baterai & Suhu Dingin (VSYNC Pacing)", "Battery Saver & Cool Thermal Mode (VSYNC Pacing)"),
                    subtitle = xy(
                        "Gabungkan burst update piksel per siklus layar agar HP tidak cepat panas dan hemat baterai",
                        "Coalesce pixel burst updates per screen cycle to keep the phone cool and save battery",
                    ),
                    checked = options.batterySaver,
                    onCheckedChange = { options = options.copy(batterySaver = it) },
                    leading = XyIcons.Sliders,
                )
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
                    title = xy("Jaringan terbatas (broadband-low / AVC420)", "Low-bandwidth mode (broadband-low / AVC420)"),
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
                        "Akselerasi hardware H.264 (AVC444 di jaringan normal, AVC420 di mode hemat)",
                        "H.264 hardware acceleration (AVC444 on normal links, AVC420 in low-bandwidth)",
                    ),
                    checked = options.h264,
                    onCheckedChange = {
                        options = options.copy(h264 = it, streamProfile = XyStreamProfile.CUSTOM)
                    },
                    leading = XyIcons.Grid,
                )
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
                Spacer(Modifier.height(8.dp))
                Text(
                    xy("Level kompresi paket RDP", "RDP packet compression level"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                XySegmented(
                    options = listOf(
                        xy("Mati (0)", "Off (0)"),
                        xy("Standar (1)", "Standard (1)"),
                        xy("Maksimum (2)", "Maximum (2)"),
                    ),
                    selectedIndex = options.compressionLevel.coerceIn(0, 2),
                    onSelect = {
                        options = options.copy(
                            compressionLevel = it,
                            streamProfile = XyStreamProfile.CUSTOM,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                    checked = options.windowDrag,
                    onCheckedChange = {
                        options = options.copy(windowDrag = it, streamProfile = XyStreamProfile.CUSTOM)
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
                XySecurityProtocol.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { proto ->
                            XyPillButton(
                                text = xy(proto.title, proto.titleEn),
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
                val tlsValues = listOf(-1, 0, 1, 2)
                XySegmented(
                    options = listOf(
                        xy("Auto", "Auto"),
                        xy("Legacy (0)", "Legacy (0)"),
                        xy("Standar (1)", "Standard (1)"),
                        xy("Ketat (2)", "Strict (2)"),
                    ),
                    selectedIndex = tlsValues.indexOf(options.tlsSecLevel).coerceAtLeast(0),
                    onSelect = { options = options.copy(tlsSecLevel = tlsValues[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                XyToggleRow(
                    title = xy("Sesi Konsol / Admin (/admin)", "Console / Admin Session (/admin)"),
                    subtitle = xy(
                        "Masuk ke sesi konsol fisik atau sesi administrator Windows Server",
                        "Connect to the physical console or Windows Server administrator session",
                    ),
                    checked = options.consoleAdmin,
                    onCheckedChange = { options = options.copy(consoleAdmin = it) },
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
                    value = options.macAddress.orEmpty(),
                    onValueChange = { options = options.copy(macAddress = it.trim().ifEmpty { null }) },
                    label = xy("MAC Address kartu jaringan PC (opsional)", "PC network card MAC Address (optional)"),
                    hint = "AA:BB:CC:DD:EE:FF",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyField(
                        value = options.wolBroadcast,
                        onValueChange = { options = options.copy(wolBroadcast = it.trim()) },
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
                if (!options.macAddress.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    XyPillButton(
                        text = xy("Kirim Tes Magic Packet Sekarang", "Send Test Magic Packet Now"),
                        onClick = {
                            val mac = options.macAddress.orEmpty()
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
                                        add(options.wolBroadcast.ifBlank { "255.255.255.255" })
                                        if (!endpointHost.isNullOrBlank()) add(endpointHost)
                                    }.distinct()
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            targets.forEach { target ->
                                                WakeOnLan.sendMagicPacket(mac, target, options.wolPort)
                                            }
                                        }
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
                    value = options.sshHost.orEmpty(),
                    onValueChange = { options = options.copy(sshHost = it.trim().ifEmpty { null }) },
                    label = xy("SSH Jump Host", "SSH Jump Host"),
                    hint = "ssh.kantor.com",
                )
                if (!options.sshHost.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyField(
                            value = options.sshUser.orEmpty(),
                            onValueChange = { options = options.copy(sshUser = it.trim().ifEmpty { null }) },
                            label = xy("SSH User", "SSH User"),
                            hint = "root",
                            modifier = Modifier.weight(1.3f),
                        )
                        XyField(
                            value = options.sshPort.toString(),
                            onValueChange = {
                                val p = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 22
                                options = options.copy(sshPort = p.coerceIn(1, 65535))
                            },
                            label = xy("SSH Port", "SSH Port"),
                            hint = "22",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(0.7f),
                        )
                    }
                    val parsedEndpoint = parseRdpEndpoint(host)
                    val sshCmd = WakeOnLan.buildSshTunnelCommand(
                        targetHost = parsedEndpoint?.host ?: "127.0.0.1",
                        targetPort = parsedEndpoint?.port ?: 3389,
                        options = options,
                    )
                    if (!sshCmd.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            sshCmd,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        XyPillButton(
                            text = xy("Salin Perintah SSH", "Copy SSH Command"),
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("SSH Tunnel", sshCmd))
                                wolStatusText = xyNow("Perintah SSH disalin ke clipboard.", "SSH command copied to clipboard.")
                            },
                            primary = false,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
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

    if (lanScanModalOpen) {
        XyOverlay(
            title = if (isPcQuickMode) {
                xy("Pindai PC di Jaringan Wi-Fi (LAN)", "Scan PCs on Wi-Fi Network (LAN)")
            } else {
                xy("Pindai Host RDP di Jaringan Lokal", "Scan RDP Hosts on Local Network")
            },
            onDismiss = { lanScanModalOpen = false },
        ) {
            Text(
                text = if (!lanSelfIp.isNullOrBlank()) {
                    xy("IP HP Anda: {0} · Memindai port 3389 di subnet lokal", "Your Phone IP: {0} · Scanning port 3389 on local subnet", lanSelfIp!!)
                } else {
                    xy("Pastikan HP dan PC berada di jaringan Wi-Fi / Hotspot yang sama.", "Make sure your phone and PC are on the same Wi-Fi / Hotspot network.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lanScanning) {
                Text(
                    text = xy("Memindai 254 alamat lokal secara paralel...", "Scanning 254 local addresses in parallel..."),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (lanFoundHosts.isEmpty()) {
                Text(
                    text = xy(
                        "Belum ditemukan PC aktif di subnet ini. Pastikan PC menyala dan XyDeskHost / Remote Desktop (Port 3389) sudah aktif.",
                        "No active PC found on this subnet yet. Ensure the PC is powered on and XyDeskHost / Remote Desktop (Port 3389) is enabled.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lanFoundHosts.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clickable {
                                    host = if (isPcQuickMode) item.pcId else "${item.ip}:${item.port}"
                                    if (label.isBlank()) {
                                        label = if (isPcQuickMode) "PC ${item.pcId.takeLast(4)}" else "RDP ${item.ip}"
                                    }
                                    lanScanModalOpen = false
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (isPcQuickMode) "ID PC: ${item.pcId}" else "${item.ip}:${item.port}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = if (isPcQuickMode) {
                                        xy("Endpoint: {0}:{1} · Ketuk untuk pakai ID ini", "Endpoint: {0}:{1} · Tap to use this ID", item.ip, item.port)
                                    } else {
                                        xy("ID PC: {0} · Ketuk untuk pakai IP ini", "PC ID: {0} · Tap to use this IP", item.pcId)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${item.rttMs} ms",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = xy("Pindai Ulang", "Rescan"),
                    onClick = { triggerLanScan() },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Tutup", "Close"),
                    onClick = { lanScanModalOpen = false },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
            "Path HP: $path",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            xy(
                "Cara transfer file di PC: Buka File Explorer Windows -> This PC -> klik drive 'XyDesk on Android' (atau ketik \\\\tsclient\\XyDesk di address bar) untuk salin/tempel file dua arah.",
                "How to transfer files on PC: Open Windows File Explorer -> This PC -> open 'XyDesk on Android' drive (or type \\\\tsclient\\XyDesk in the address bar) for two-way file copy/paste.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
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

/** Baris slider berlabel untuk pengaturan DSP mic. */
@Composable
private fun XySliderRow(
    label: String,
    value: Float,
    suffix: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${value.toInt()}$suffix",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        XySlider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}
