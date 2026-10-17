package id.xydesk.remote.ui.ssh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.xy

/**
 * Ronde 55 — Layar Terminal SSH-2 & Linux VPS Native di XyDesk Remote.
 * Bisa digunakan untuk:
 *  - Linux Polos (Tanpa XFCE4) dari Buat RDP Cloud (`xykal/XyRDP`)
 *  - Linux Desktop (pendamping SSH port 22)
 *  - VPS Linux beli sendiri (Ubuntu, Debian, CentOS, AlmaLinux, Rocky, Alpine, dll.)
 */
@Composable
fun XySshTerminalScreen(
    initialHost: String = "",
    initialPort: Int = 22,
    initialUser: String = "xyadmin",
    initialPassword: String = "",
    initialLabel: String = "",
    autoConnectOnStart: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var savedProfiles by remember { mutableStateOf(XySshProfileStore.list(context)) }
    var host by remember { mutableStateOf(initialHost.ifBlank { savedProfiles.firstOrNull()?.host.orEmpty() }) }
    var portText by remember {
        mutableStateOf(
            (if (initialHost.isNotBlank()) initialPort else (savedProfiles.firstOrNull()?.port ?: 22)).toString(),
        )
    }
    var username by remember {
        mutableStateOf(
            if (initialHost.isNotBlank()) initialUser.ifBlank { "root" }
            else savedProfiles.firstOrNull()?.username?.ifBlank { "root" } ?: "root",
        )
    }
    var password by remember {
        mutableStateOf(
            if (initialHost.isNotBlank()) initialPassword
            else savedProfiles.firstOrNull()?.password.orEmpty(),
        )
    }
    var label by remember {
        mutableStateOf(
            if (initialLabel.isNotBlank()) initialLabel
            else savedProfiles.firstOrNull()?.label.orEmpty(),
        )
    }
    var showPassword by remember { mutableStateOf(false) }
    var showConfigForm by remember { mutableStateOf(!autoConnectOnStart || initialHost.isBlank()) }

    var isConnected by remember { mutableStateOf(false) }
    var statusLabel by remember { mutableStateOf("Siap menghubungkan ke Linux VPS / SSH Server") }
    var hostKeyFp by remember { mutableStateOf("") }
    var terminalBuffer by remember {
        mutableStateOf(
            "=== XyDesk Remote · Native SSH-2 Terminal ===\n" +
                "Protokol : SSH-2.0 (DH-Group14-SHA256 · AES-128-CTR · HMAC-SHA256)\n" +
                "Target   : Linux Polos XyRDP / Ubuntu / Debian / VPS Server\n\n",
        )
    }
    var activeSession by remember { mutableStateOf<XySshSession?>(null) }
    var cmdInput by remember { mutableStateOf("") }
    val cmdHistory = remember { mutableStateListOf<String>() }
    var historyCursor by remember { mutableIntStateOf(-1) }

    val termScroll = rememberScrollState()

    fun appendTerminal(chunk: String) {
        mainHandler.post {
            val combined = terminalBuffer + chunk
            terminalBuffer = if (combined.length > 48_000) {
                combined.takeLast(40_000)
            } else {
                combined
            }
        }
    }

    fun connectNow() {
        val cleanHost = host.trim()
        val cleanPort = portText.trim().toIntOrNull()?.coerceIn(1, 65535) ?: 22
        val cleanUser = username.trim().ifBlank { "root" }
        if (cleanHost.isEmpty()) {
            Toast.makeText(context, "Masukkan IP atau Hostname VPS Linux terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        activeSession?.disconnect()
        val prof = XySshProfile(
            id = "ssh-${cleanHost.lowercase()}-$cleanPort-$cleanUser",
            label = label.trim().ifBlank { "$cleanUser@$cleanHost:$cleanPort" },
            host = cleanHost,
            port = cleanPort,
            username = cleanUser,
            password = password,
        )
        XySshProfileStore.upsert(context, prof)
        savedProfiles = XySshProfileStore.list(context)
        showConfigForm = false
        appendTerminal("\n$ ssh -p $cleanPort $cleanUser@$cleanHost\n")

        val session = XySshSession(
            host = cleanHost,
            port = cleanPort,
            username = cleanUser,
            password = password,
            onOutput = { out -> appendTerminal(out) },
            onStateChange = { connected, stText, fp ->
                mainHandler.post {
                    isConnected = connected
                    statusLabel = stText
                    if (fp.isNotBlank()) hostKeyFp = fp
                }
            },
        )
        activeSession = session
        session.start()
    }

    LaunchedEffect(Unit) {
        if (autoConnectOnStart && initialHost.isNotBlank() && initialPassword.isNotBlank()) {
            connectNow()
        }
    }

    LaunchedEffect(terminalBuffer.length) {
        termScroll.animateScrollTo(termScroll.maxValue)
    }

    DisposableEffect(Unit) {
        onDispose {
            activeSession?.disconnect()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    activeSession?.disconnect()
                    onBack()
                }) {
                    Icon(
                        imageVector = XyIcons.ChevronLeft,
                        contentDescription = "Kembali",
                        tint = cs.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B)),
                        )
                        Text(
                            text = xy("Terminal SSH & Linux VPS", "SSH & Linux VPS Terminal"),
                            style = ty.titleMedium,
                            color = cs.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = statusLabel,
                        style = ty.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                XyPillButton(
                    text = if (showConfigForm) xy("Tutup Form", "Hide Form") else xy("Server / IP", "Server / IP"),
                    primary = false,
                    compact = true,
                    onClick = { showConfigForm = !showConfigForm },
                )
                if (isConnected) {
                    Spacer(Modifier.width(6.dp))
                    XyPillButton(
                        text = xy("Putus", "Disconnect"),
                        primary = false,
                        compact = true,
                        onClick = { activeSession?.disconnect() },
                    )
                }
            }

            // Panel Konfigurasi Server SSH / VPS (Bisa dilipat saat terminal aktif)
            if (showConfigForm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    XyCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = XyIcons.Terminal,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = xy("Koneksi Linux VPS / Server SSH-2", "Linux VPS / SSH-2 Server Connection"),
                                    style = ty.titleMedium,
                                    color = cs.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = xy(
                                    "Mendukung Linux Polos (Tanpa XFCE4) dari XyRDP maupun VPS Linux beli sendiri (Ubuntu, Debian, AlmaLinux, CentOS, Alpine).",
                                    "Supports Plain Linux (No XFCE4) from XyRDP as well as any self-hosted Linux VPS (Ubuntu, Debian, AlmaLinux, CentOS, Alpine).",
                                ),
                                style = ty.bodySmall,
                                color = cs.onSurfaceVariant,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text(xy("Host / IP VPS", "VPS Host / IP")) },
                                    placeholder = { Text("100.x.y.z / vps.domain.id") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.7f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cs.primary,
                                        unfocusedBorderColor = cs.outlineVariant,
                                    ),
                                )
                                OutlinedTextField(
                                    value = portText,
                                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                                    label = { Text("Port") },
                                    placeholder = { Text("22") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(0.3f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cs.primary,
                                        unfocusedBorderColor = cs.outlineVariant,
                                    ),
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username") },
                                    placeholder = { Text("root / xyadmin") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.45f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cs.primary,
                                        unfocusedBorderColor = cs.outlineVariant,
                                    ),
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password SSH") },
                                    singleLine = true,
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Text(
                                            text = if (showPassword) xy("Sembunyi", "Hide") else xy("Lihat", "Show"),
                                            style = ty.labelSmall,
                                            color = cs.primary,
                                            modifier = Modifier
                                                .clickable { showPassword = !showPassword }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    },
                                    modifier = Modifier.weight(0.55f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cs.primary,
                                        unfocusedBorderColor = cs.outlineVariant,
                                    ),
                                )
                            }

                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                label = { Text(xy("Nama Server (Opsional)", "Server Label (Optional)")) },
                                placeholder = { Text("VPS Ubuntu Production / Linux Polos XyRDP") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = cs.primary,
                                    unfocusedBorderColor = cs.outlineVariant,
                                ),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                XyPillButton(
                                    text = if (isConnected) xy("Hubungkan Ulang SSH", "Reconnect SSH") else xy("Hubungkan ke Terminal SSH", "Connect to SSH Terminal"),
                                    icon = XyIcons.Terminal,
                                    onClick = { connectNow() },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (savedProfiles.isNotEmpty()) {
                        XyCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = xy("Server VPS / SSH Tersimpan", "Saved VPS / SSH Servers"),
                                    style = ty.labelMedium,
                                    color = cs.onSurfaceVariant,
                                )
                                savedProfiles.take(6).forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cs.surfaceVariant.copy(alpha = 0.45f))
                                            .clickable {
                                                host = item.host
                                                portText = item.port.toString()
                                                username = item.username
                                                password = item.password
                                                label = item.label
                                                connectNow()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = XyIcons.Terminal,
                                            contentDescription = null,
                                            tint = cs.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.label,
                                                style = ty.bodyMedium,
                                                color = cs.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "${item.username}@${item.host}:${item.port}",
                                                style = ty.bodySmall,
                                                color = cs.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                XySshProfileStore.remove(context, item.id)
                                                savedProfiles = XySshProfileStore.list(context)
                                            },
                                        ) {
                                            Icon(
                                                imageVector = XyIcons.Trash,
                                                contentDescription = "Hapus",
                                                tint = cs.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Info Enkripsi Host Key Fingerprint jika ada
            if (hostKeyFp.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1520))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = XyIcons.Lock,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "SSH-2 E2EE · $hostKeyFp",
                        style = ty.labelSmall,
                        color = Color(0xFF93C5FD),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Baris Tombol Pintasan Perintah Cepat (Macro Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0D12))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val quickMacros = listOf(
                    "Spek VPS" to "uname -sr && uptime -p && free -h && df -h /\n",
                    "Proses (Top)" to "top -bn1 | head -n 20\n",
                    "IP & Port" to "ip -br a && ss -tulpn | head -n 20\n",
                    "Folder Persist" to "ls -la /persist ~\n",
                    "Ctrl+C" to "\u0003",
                    "Ctrl+D" to "\u0004",
                    "Tab" to "\t",
                )
                for ((title, seq) in quickMacros) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF161B22))
                            .border(1.dp, Color(0xFF2D3542), RoundedCornerShape(7.dp))
                            .clickable {
                                if (seq.endsWith("\n")) {
                                    activeSession?.sendCommand(seq.removeSuffix("\n"))
                                } else {
                                    activeSession?.sendRawData(seq.toByteArray(Charsets.UTF_8))
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = title,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF161B22))
                        .border(1.dp, Color(0xFF2D3542), RoundedCornerShape(7.dp))
                        .clickable {
                            terminalBuffer = ""
                            activeSession?.sendCommand("clear")
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "Bersihkan",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF161B22))
                        .border(1.dp, Color(0xFF2D3542), RoundedCornerShape(7.dp))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("SSH Terminal Log", terminalBuffer))
                            Toast.makeText(context, "Log terminal disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "Salin Log",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Layar Output Terminal Monospace
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF06080B))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(termScroll),
            ) {
                SelectionContainer {
                    Text(
                        text = terminalBuffer,
                        color = Color(0xFFD1FAE5),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Input Perintah Shell Interaktif
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$",
                    color = Color(0xFF22C55E),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
                OutlinedTextField(
                    value = cmdInput,
                    onValueChange = { cmdInput = it },
                    placeholder = {
                        Text(
                            text = xy("Ketik perintah Linux (mis. ls -la, htop, docker ps)…", "Type Linux command (e.g. ls -la, htop, docker ps)…"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val toSend = cmdInput
                            cmdInput = ""
                            if (toSend.isNotBlank()) {
                                cmdHistory.add(0, toSend)
                                historyCursor = -1
                            }
                            activeSession?.sendCommand(toSend)
                        },
                    ),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outlineVariant,
                    ),
                )
                IconButton(
                    onClick = {
                        if (cmdHistory.isNotEmpty()) {
                            historyCursor = (historyCursor + 1).coerceAtMost(cmdHistory.lastIndex)
                            cmdInput = cmdHistory[historyCursor]
                        }
                    },
                ) {
                    Icon(
                        imageVector = XyIcons.ArrowUp,
                        contentDescription = "Perintah Sebelumnya",
                        tint = cs.onSurfaceVariant,
                    )
                }
                XyPillButton(
                    text = xy("Kirim", "Send"),
                    compact = true,
                    onClick = {
                        val toSend = cmdInput
                        cmdInput = ""
                        if (toSend.isNotBlank()) {
                            cmdHistory.add(0, toSend)
                            historyCursor = -1
                        }
                        activeSession?.sendCommand(toSend)
                    },
                )
            }
        }
    }
}
