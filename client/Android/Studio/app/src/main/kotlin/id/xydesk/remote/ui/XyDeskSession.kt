package id.xydesk.remote.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.xydesk.remote.cloud.CloudRdpActivity
import id.xydesk.remote.core.CertificateInfo
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.SessionManager
import id.xydesk.remote.core.SessionState
import id.xydesk.remote.core.TelemetrySample
import kotlin.math.roundToInt

private data class CertPrompt(
    val info: CertificateInfo,
    val oldFingerprint: String?,
    val reply: (Int) -> Unit,
)
private data class NlaPrompt(
    val user: String?,
    val domain: String?,
    val reply: (String?, String?, String?) -> Unit,
)

/**
 * M2 — layar sesi XyDesk: surface RDP (view inti via [AndroidView]) + HUD.
 *
 * - Top bar: judul host, stats ringkas (fps/resolusi/zoom), tombol panel
 * - Panel samping: Stats realtime + kontrol (zoom, touch pointer, keyboard, disconnect)
 * - Dialog: trust sertifikat (safe default: timeout = tolak), NLA, error
 *   (dengan hint Windows Home + pintu Cloud RDP), konfirmasi disconnect
 * - Back: prompt wajib dijawab; Connected = konfirmasi; Connecting = batalkan
 */
@Composable
fun XyDeskSessionScreen(
    profile: ConnectionProfile,
    manager: SessionManager,
    controller: SessionSurfaceController,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val state by manager.state.collectAsState(initial = SessionState.Idle)
    val telemetry by manager.telemetry.collectAsState(initial = TelemetrySample.EMPTY)
    val prefs = remember { ConnectionPrefs(context) }
    val trustStore = remember { CertificateTrustStore(context) }
    var certPrompt by remember { mutableStateOf<CertPrompt?>(null) }
    var nlaPrompt by remember { mutableStateOf<NlaPrompt?>(null) }
    var showPanel by remember { mutableStateOf(prefs.isPanelShown(profile.id)) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(prefs.getZoom(profile.id)) }
    var bound by remember { mutableStateOf(false) }
    val freeRdpVersion = remember { manager.freeRdpVersion() }

    // Listener: prompt dipanggil (blocking) di thread RDP -> state -> dialog
    LaunchedEffect(Unit) {
        manager.setListener(object : SessionManager.Listener {
            override fun onCertificatePrompt(info: CertificateInfo, reply: (Int) -> Unit) {
                val stored = trustStore.fingerprint(info.host, info.port)
                if (stored != null && stored == info.fingerprint) {
                    // fingerprint sudah pernah dipercaya pengguna — auto-approve
                    reply(CertificateInfo.VERIFY_ACCEPT)
                    return
                }
                certPrompt = CertPrompt(info, stored, reply)
            }

            override fun onCredentialsPrompt(
                username: String?,
                domain: String?,
                reply: (String?, String?, String?) -> Unit,
            ) {
                nlaPrompt = NlaPrompt(username, domain, reply)
            }

            override fun onRemoteClipboardText(text: String) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("rdp", text))
            }
        })
    }

    LaunchedEffect(Unit) {
        controller.onZoomChanged = { z ->
            zoom = z
            prefs.setZoom(profile.id, z)
        }
        // catch-up bila surface sudah terbentuk sebelum view tree siap
        controller.setInstance(manager.instance())
    }

    // bind surface (input + ukuran layar) sekali saat Connected
    LaunchedEffect(state) {
        if (state is SessionState.Connected && !bound) {
            bound = true
            controller.bind(manager.instance())
            controller.applyZoom(zoom)
        }
    }

    // layar tetap menyala selama sesi aktif
    LaunchedEffect(state) {
        val act = context as? Activity ?: return@LaunchedEffect
        if (state is SessionState.Connected) {
            act.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            act.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // back handling (delegasi dari XyDeskSessionActivity)
    LaunchedEffect(state, certPrompt != null, nlaPrompt != null, confirmDisconnect, showPanel) {
        (context as? XyDeskSessionActivity)?.backHandler = {
            when {
                // prompt sertifikat/NLA = harus dijawab eksplisit
                certPrompt != null || nlaPrompt != null -> true
                confirmDisconnect -> {
                    confirmDisconnect = false
                    true
                }
                state is SessionState.Connected -> {
                    confirmDisconnect = true
                    true
                }
                state is SessionState.Connecting || state is SessionState.Authenticating -> {
                    manager.cancelConnection()
                    true
                }
                showPanel -> {
                    showPanel = false
                    true
                }
                else -> false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // surface sesi (SessionView + touch pointer + keyboard inti)
        AndroidView(
            factory = { ctx -> controller.buildViewTree(ctx) },
            modifier = Modifier.fillMaxSize(),
        )

        // top bar HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = "Tutup")
            }
            Text(
                text = profile.label ?: "${profile.host}:${profile.port}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            if (state is SessionState.Connected) {
                Text(
                    text = "${telemetry.fps} upd/s · ${telemetry.width}\u00d7${telemetry.height}" +
                        " · ${(zoom * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = {
                showPanel = !showPanel
                prefs.setPanelShown(profile.id, showPanel)
            }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Panel")
            }
        }

        if (showPanel && state is SessionState.Connected) {
            SessionSidePanel(
                telemetry = telemetry,
                zoom = zoom,
                freeRdpVersion = freeRdpVersion,
                controller = controller,
                onDisconnect = { manager.disconnect() },
                onScreenshot = {
                    val act = context as? Activity ?: return@SessionSidePanel
                    val uri: Uri = controller.captureScreenshot(act) ?: return@SessionSidePanel
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    act.startActivity(Intent.createChooser(send, "Bagikan screenshot"))
                },
            )
        }

        if (state is SessionState.Connecting || state is SessionState.Authenticating) {
            ConnectingOverlay(
                isAuthenticating = state is SessionState.Authenticating,
                onCancel = { manager.cancelConnection() },
            )
        }

        if (state is SessionState.Disconnected) {
            DisconnectedOverlay(
                onReconnect = {
                    bound = false
                    manager.connect(profile)
                },
                onExit = onExit,
            )
        }
    }

    val err = state as? SessionState.Error

    // dialog: prioritas cert > NLA > error > konfirmasi disconnect
    val active = certPrompt != null || nlaPrompt != null
    certPrompt?.let { p ->
        CertificateDialog(
            info = p.info,
            oldFingerprint = p.oldFingerprint,
            onReply = { code ->
                p.reply(code)
                certPrompt = null
            },
            onTrustRemember = {
                trustStore.trust(p.info.host, p.info.port, p.info.fingerprint)
                p.reply(CertificateInfo.VERIFY_ACCEPT)
                certPrompt = null
            },
        )
    }
    if (!active) nlaPrompt?.let { p ->
        NlaDialog(p) { u, d, pw ->
            p.reply(u, d, pw)
            nlaPrompt = null
        }
    }
    if (!active) err?.let { e ->
        AlertDialog(
            onDismissRequest = { onExit() },
            title = { Text("Koneksi gagal") },
            text = {
                Column {
                    Text(e.message)
                    if (e.code == SessionManager.ERROR_UNREACHABLE) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Kalau host-nya Windows Home, server RDP memang tidak " +
                                "tersedia — pakai Cloud RDP untuk membuat mesin dari GitHub.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onExit() }) { Text("Tutup") }
            },
            dismissButton = {
                if (e.code == SessionManager.ERROR_UNREACHABLE) {
                    TextButton(onClick = {
                        context.startActivity(Intent(context, CloudRdpActivity::class.java))
                    }) { Text("Cloud RDP") }
                }
            },
        )
    }
    if (!active && err == null && confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Sesi masih aktif") },
            text = { Text("Disconnect sekarang atau kembali lagi nanti?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    manager.disconnect()
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun BoxScope.SessionSidePanel(
    telemetry: TelemetrySample,
    zoom: Float,
    freeRdpVersion: String,
    controller: SessionSurfaceController,
    onDisconnect: () -> Unit,
    onScreenshot: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(212.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("STATS", style = MaterialTheme.typography.labelLarge)
        StatRow("Status", telemetry.state::class.simpleName ?: "-")
        StatRow(
            "Resolusi",
            if (telemetry.width > 0) "${telemetry.width}\u00d7${telemetry.height}" else "-",
        )
        StatRow("Aktivitas gambar", "${telemetry.fps}/s")
        StatRow("Zoom", "${(zoom * 100f).roundToInt()}%")
        StatRow("FreeRDP", freeRdpVersion)
        Spacer(Modifier.height(10.dp))
        Text("KONTROL", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.zoomIn() }) { Text("Zoom +") }
            OutlinedButton(onClick = { controller.zoomOut() }) { Text("Zoom -") }
        }
        OutlinedButton(
            onClick = { controller.toggleTouchPointer() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Touch pointer") }
        OutlinedButton(
            onClick = { controller.toggleKeyboard() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Keyboard") }
        OutlinedButton(
            onClick = onScreenshot,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Screenshot") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
        ) { Text("Disconnect") }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ConnectingOverlay(isAuthenticating: Boolean, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                if (isAuthenticating) "Menunggu autentikasi (NLA)…" else "Menghubungkan…",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCancel) { Text("Batalkan") }
        }
    }
}

@Composable
private fun DisconnectedOverlay(onReconnect: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sesi terputus", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onReconnect) { Text("Sambungkan lagi") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onExit) { Text("Kembali ke home") }
        }
    }
}

@Composable
private fun CertificateDialog(
    info: CertificateInfo,
    oldFingerprint: String?,
    onReply: (Int) -> Unit,
    onTrustRemember: () -> Unit,
) {
    AlertDialog(
        // tidak bisa di-dismiss dengan back/tap luar — harus pilih
        onDismissRequest = {},
        title = {
            Text(
                if (info.isChanged) "Sertifikat server berubah"
                else "Percaya sertifikat server?"
            )
        },
        text = {
            Column {
                Text("${info.host}:${info.port}")
                if (info.isGateway) {
                    Text("Jenis: RDP Gateway", style = MaterialTheme.typography.bodySmall)
                }
                if (info.isMismatch) {
                    Text(
                        "PERINGATAN: nama sertifikat tidak cocok dengan host",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (info.isChanged) {
                    Text(
                        "PERINGATAN: inti mendeteksi sertifikat berubah — " +
                            "mungkin salah server atau MITM.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (oldFingerprint != null && oldFingerprint != info.fingerprint) {
                    Text(
                        "PERINGATAN: sertifikat BERUBAH dari yang pernah Anda percaya.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tersimpan : $oldFingerprint",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Subject: ${info.subject}", style = MaterialTheme.typography.bodySmall)
                Text("Issuer: ${info.issuer}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Fingerprint SHA-256:", style = MaterialTheme.typography.bodySmall)
                Text(
                    info.fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = onTrustRemember) { Text("Percaya & ingat") }
                TextButton(onClick = { onReply(CertificateInfo.VERIFY_ACCEPT) }) {
                    Text("Percaya (sekali)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onReply(CertificateInfo.VERIFY_DENY) }) { Text("Tolak") }
        },
    )
}

@Composable
private fun NlaDialog(
    p: NlaPrompt,
    onReply: (String?, String?, String?) -> Unit,
) {
    var user by remember { mutableStateOf(p.user ?: "") }
    var domain by remember { mutableStateOf(p.domain ?: "") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Masuk ke server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Server meminta kredensial (NLA/CredSSP).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = user.isNotEmpty() || pass.isNotEmpty(),
                onClick = {
                    onReply(
                        user.ifBlank { null },
                        domain.ifBlank { null },
                        pass.ifBlank { null },
                    )
                },
            ) { Text("Masuk") }
        },
        dismissButton = {
            TextButton(onClick = { onReply(null, null, null) }) { Text("Batal") }
        },
    )
}
