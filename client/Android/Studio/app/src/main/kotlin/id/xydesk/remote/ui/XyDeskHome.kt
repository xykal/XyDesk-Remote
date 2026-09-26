@file:OptIn(ExperimentalMaterial3Api::class)

package id.xydesk.remote.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.R
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.sessions.SessionsRepository
import id.xydesk.remote.core.ConnectionLog
import id.xydesk.remote.security.CrashLog
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XySectionTitle
import id.xydesk.remote.ui.components.XyWordmark
import id.xydesk.remote.ui.components.XyMenuItem
import kotlinx.coroutines.launch

/**
 * M1.2 — layar home: favorit + form koneksi + pintu Cloud RDP.
 *
 * Data: [SessionsRepository] (Room + CredentialVault). Connect = layar
 * sesi XyDesk M2 (Compose + HUD); core SessionActivity M0 tetap
 * tersedia lewat "Form klasik".
 */
private enum class XySection { KONEKSI, CLOUD, PENGATURAN, KEAMANAN, TENTANG }

/**
 * M3-UI — shell XyDesk: drawer brand + seksi (Koneksi, Cloud,
 * Pengaturan, Keamanan, Tentang). Desain system XyDesk (violet).
 */
@Composable
fun XyDeskHome(
    onOpenCloudRdp: () -> Unit,
    onOpenClassicForm: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SessionsRepository(context.applicationContext) }
    val favoritesFlow = remember { repo.favorites() }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var section by remember { mutableStateOf(XySection.KONEKSI) }
    var showForm by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf(CrashLog.last(context.applicationContext)) }
    var showCrashDialog by remember { mutableStateOf(false) }
    val bootTail = remember { ConnectionLog.tailFromFile(context.applicationContext, 14) }
    val bootSuspect = bootTail.isNotEmpty() && bootTail.last().let { last ->
        !last.contains("session.connect kembali") &&
            !last.contains("-> Connected") &&
            !last.contains("-> Disconnected") &&
            !last.contains("intent tanpa profil")
    }
    var showBootDialog by remember { mutableStateOf(false) }
    var backArmedAt by remember { mutableStateOf(0L) }
    val onMenu: () -> Unit = { scope.launch { drawerState.open() } }

    // Back: drawer kebuka -> tutup; seksi lain -> Koneksi;
    // Koneksi: 2x tekan dalam 2 detik baru keluar
    BackHandler {
        if (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) {
            scope.launch { drawerState.close() }
        } else if (section != XySection.KONEKSI) {
            section = XySection.KONEKSI
        } else {
            val now = System.currentTimeMillis()
            if (now - backArmedAt < 2_000L) {
                onExit()
            } else {
                backArmedAt = now
                Toast.makeText(context, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var autoDisc by remember { mutableStateOf(prefs.autoDisconnect) }

    fun connectTo(profile: ConnectionProfile) {
        scope.launch { repo.touch(profile) }
        // M2: jalur sesi XyDesk (surface + HUD Compose)
        context.startActivity(XyDeskSessionActivity.connectIntent(profile))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.xy_logo_img),
                        contentDescription = "XyDesk",
                        modifier = Modifier.size(44.dp),
                    )
                    Column {
                        XyWordmark(fontSize = 22.sp)
                        Text(
                            "XyVerse • Remote Desktop",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(8.dp))
                XyMenuItem(Icons.Default.List, "Koneksi", section == XySection.KONEKSI) {
                    section = XySection.KONEKSI
                    scope.launch { drawerState.close() }
                }
                XyMenuItem(Icons.Default.Send, "Cloud RDP", section == XySection.CLOUD) {
                    section = XySection.CLOUD
                    scope.launch { drawerState.close() }
                }
                XyMenuItem(Icons.Default.Settings, "Pengaturan", section == XySection.PENGATURAN) {
                    section = XySection.PENGATURAN
                    scope.launch { drawerState.close() }
                }
                XyMenuItem(Icons.Default.Lock, "Keamanan", section == XySection.KEAMANAN) {
                    section = XySection.KEAMANAN
                    scope.launch { drawerState.close() }
                }
                XyMenuItem(Icons.Default.Info, "Tentang", section == XySection.TENTANG) {
                    section = XySection.TENTANG
                    scope.launch { drawerState.close() }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "XyDesk Remote • v${runCatching {
                            context.packageManager
                                .getPackageInfo(context.packageName, 0).versionName
                        }.getOrDefault("?")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        },
    ) {
        when (section) {
            XySection.KONEKSI -> {
                Scaffold(
                    topBar = {
                        XyTopBar(onMenu = onMenu, title = "Koneksi")
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showForm = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Koneksi baru")
                        }
                    },
                ) { padding ->
                    crashLog?.let { log ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable { showCrashDialog = true }
                                .padding(12.dp),
                        ) {
                            Text(
                                "Terjadi error sebelumnya — ketuk untuk lihat log",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    if (bootSuspect) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { showBootDialog = true }
                                .padding(12.dp),
                        ) {
                            Text(
                                "Sesi terakhir terhenti di tengah jalan — ketuk untuk lihat log boot",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada favorit", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tekan + untuk koneksi baru, atau\nCloud RDP buat mesin dari GitHub",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(favorites, key = { it.id }) { profile ->
                    FavoriteRow(
                        profile = profile,
                        onConnect = { connectTo(profile) },
                        onDelete = { scope.launch { repo.remove(profile.id) } },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = onOpenCloudRdp) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cloud RDP (GitHub)")
                        }
                        TextButton(onClick = onOpenClassicForm) {
                            Text("Form klasik (M0)")
                        }
                    }
                }
            }
        }
        }
            }

            XySection.CLOUD -> SectionWithTopBar(onMenu, "Cloud RDP") {
                XyCloudSection(onOpenCloudRdp, onOpenClassicForm)
            }

            XySection.PENGATURAN -> SectionWithTopBar(onMenu, "Pengaturan") {
                XySettingsScreen(
                themeMode = themeMode,
                onThemeMode = { v ->
                    themeMode = v
                    prefs.themeMode = v
                    (context as? Activity)?.recreate()
                },
                autoDisconnect = autoDisc,
                onAutoDisconnect = { v ->
                    autoDisc = v
                    prefs.autoDisconnect = v
                },
            )
            }

            XySection.KEAMANAN -> SectionWithTopBar(onMenu, "Keamanan") {
                XySecurityScreen({})
            }

            XySection.TENTANG -> SectionWithTopBar(onMenu, "Tentang") {
                XyAboutScreen()
            }
        }
    }

    if (showBootDialog) {
        AlertDialog(
            onDismissRequest = { showBootDialog = false },
            title = { Text("Log boot sesi terakhir") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    bootTail.forEach { ln ->
                        Text(ln, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        "\nKalau ini muncul setelah crash: screenshot dialog ini " +
                            "kirim ke developer. Baris TERAKHIR = langkah yang " +
                            "sedang jalan saat app mati.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ConnectionLog.clear(context.applicationContext)
                    showBootDialog = false
                }) { Text("Hapus log") }
            },
            dismissButton = {
                TextButton(onClick = { showBootDialog = false }) { Text("Tutup") }
            },
        )
    }
    if (showCrashDialog) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("Log error terakhir") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    crashLog.orEmpty().lineSequence().forEach { ln ->
                        Text(ln, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    CrashLog.clear(context.applicationContext)
                    crashLog = null
                    showCrashDialog = false
                }) { Text("Hapus log") }
            },
            dismissButton = {
                TextButton(onClick = { showCrashDialog = false }) { Text("Tutup") }
            },
        )
    }
    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text("Koneksi baru") },
            text = {
                ConnectFormFields(
                    onSaved = { profile, rememberPassword ->
                        showForm = false
                        scope.launch { repo.save(profile, rememberPassword) }
                        connectTo(profile)
                    },
                )
            },
            confirmButton = {}, // tombol aksi ada di dalam form
            dismissButton = {
                TextButton(onClick = { showForm = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun FavoriteRow(
    profile: ConnectionProfile,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.label ?: profile.host,
                    style = MaterialTheme.typography.titleMedium,
                )
                val sub = buildString {
                    append(profile.host).append(':').append(profile.port)
                    if (!profile.username.isNullOrBlank()) {
                        append("  •  ").append(profile.username)
                    }
                }
                Text(text = sub, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus favorit")
            }
        }
    }
}

/**
 * Form koneksi. [onSaved] dipanggil dengan profil valid + flag ingat-password.
 */
@Composable
private fun ConnectFormFields(
    onSaved: (profile: ConnectionProfile, rememberPassword: Boolean) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("3389") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var rememberPass by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host / IP / tailnet (wajib)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit).take(5) },
            label = { Text("Port (default 3389)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Domain (opsional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label (opsional, mis. 'Kantor')") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberPass, onCheckedChange = { rememberPass = it })
            Text("Ingat password (tersimpan terenkripsi di Keystore)")
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    val h = host.trim()
                    val p = port.toIntOrNull() ?: 3389
                    when {
                        h.isEmpty() -> {
                            error = "Host wajib diisi"
                            return@Button
                        }
                        p !in 1..65535 -> {
                            error = "Port tidak valid (1-65535)"
                            return@Button
                        }
                        else -> {
                            error = null
                        }
                    }
                    val profile = try {
                        ConnectionProfile(
                            host = h,
                            port = p,
                            username = user.trim().ifEmpty { null },
                            password = pass.ifEmpty { null },
                            domain = domain.trim().ifEmpty { null },
                            label = label.trim().ifEmpty { null },
                        )
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                        return@Button
                    }
                    onSaved(profile, rememberPass)
                },
            ) { Text("Simpan & Connect") }
        }
    }
}

/** Top bar brand XyDesk (bukan bawaan Material). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XyTopBar(onMenu: () -> Unit, title: String) {
    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.xy_logo_img),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
    )
}

/** Seksi Cloud RDP: kartu pintu ke Cloud RDP + form klasik. */
@Composable
private fun XyCloudSection(
    onOpenCloudRdp: () -> Unit,
    onOpenClassicForm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        XySectionTitle("Mesin cloud (GitHub)")
        XyCard {
            Text(
                "Buat dan jalankan Windows dari repo GitHub Anda, lalu " +
                    "langsung connect lewat XyDesk.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenCloudRdp) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cloud RDP (GitHub)")
                }
                TextButton(onClick = onOpenClassicForm) {
                    Text("Form klasik (M0)")
                }
            }
        }
    }
}

/** Pembungkus seksi: top bar brand (hamburger + judul) + konten. */
@Composable
private fun SectionWithTopBar(
    onMenu: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        XyTopBar(onMenu = onMenu, title = title)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
