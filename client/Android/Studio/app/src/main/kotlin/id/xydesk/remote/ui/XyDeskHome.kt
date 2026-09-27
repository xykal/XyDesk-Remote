@file:OptIn(ExperimentalMaterial3Api::class)

package id.xydesk.remote.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import id.xydesk.remote.core.ConnectionLog
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.security.CrashLog
import id.xydesk.remote.sessions.SessionsRepository
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyLogo
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XyRow
import id.xydesk.remote.ui.components.XySectionLabel
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.components.XyWordmark
import kotlinx.coroutines.launch

/** Seksi di drawer samping. */
internal enum class XySection(val title: String) {
    PERANGKAT("Perangkat"),
    TAMPILAN("Tampilan"),
    KREDENSIAL("Kredensial"),
    UMUM("Umum"),
    KEAMANAN("Keamanan"),
    TENTANG("Tentang"),
}

/** Layar yang sedang tampil. */
private sealed interface XyRoute {
    data object Devices : XyRoute
    data class EditDevice(val profile: ConnectionProfile?) : XyRoute
    data object Section : XyRoute
}

/**
 * Home XyDesk: daftar perangkat + drawer seksi.
 *
 * Semua info yang ditampilkan sengaja tipis: nama perangkat, alamat, akun.
 * Preview pakai wallpaper motif OS perangkat (digambar prosedural, tanpa aset).
 */
@Composable
fun XyDeskHome(onExit: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionsRepository(context.applicationContext) }
    val favoritesFlow = remember { repo.favorites() }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val appPrefs = remember { AppPrefs(context) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var section by remember { mutableStateOf(XySection.PERANGKAT) }
    var route by remember { mutableStateOf<XyRoute>(XyRoute.Devices) }
    var crashLog by remember { mutableStateOf(CrashLog.last(context.applicationContext)) }
    var showCrash by remember { mutableStateOf(false) }
    var backArmedAt by remember { mutableStateOf(0L) }
    val bootTail = remember { ConnectionLog.tailFromFile(context.applicationContext, 20) }
    var showBoot by remember { mutableStateOf(false) }

    fun connect(profile: ConnectionProfile) {
        scope.launch { repo.touch(profile) }
        context.startActivity(XyDeskSessionActivity.connectIntent(context, profile))
    }

    fun submit(profile: ConnectionProfile, rememberPassword: Boolean, startNow: Boolean) {
        scope.launch {
            val saved = runCatching { repo.save(profile, rememberPassword) }
            if (saved.isFailure) {
                Toast.makeText(
                    context,
                    "Profil tidak tersimpan: ${saved.exceptionOrNull()?.message ?: "kesalahan penyimpanan"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
            route = XyRoute.Devices
            if (startNow) connect(profile)
        }
    }

    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            route != XyRoute.Devices -> route = XyRoute.Devices
            section != XySection.PERANGKAT -> section = XySection.PERANGKAT
            else -> {
                val now = System.currentTimeMillis()
                if (now - backArmedAt < 2_000L) onExit()
                else {
                    backArmedAt = now
                    Toast.makeText(context, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    XyLogo(modifier = Modifier.size(30.dp))
                    Column {
                        XyWordmark(fontSize = 21.sp)
                        Text(
                            "XyVerse • Remote Desktop",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                XySection.entries.forEach { item ->
                    XyRow(
                        title = item.title,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        leading = when (item) {
                            XySection.PERANGKAT -> XyIcons.Monitor
                            XySection.TAMPILAN -> XyIcons.Fit
                            XySection.KREDENSIAL -> XyIcons.Lock
                            XySection.UMUM -> XyIcons.Sliders
                            XySection.KEAMANAN -> XyIcons.Info
                            XySection.TENTANG -> XyIcons.Info
                        },
                        leadingTint = if (item == section) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            section = item
                            route = XyRoute.Devices
                            scope.launch { drawerState.close() }
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "v${appVersion(context)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val current = route) {
                is XyRoute.EditDevice -> AddDeviceScreen(
                    existing = current.profile,
                    savedUsers = favorites.mapNotNull { it.username }.distinct(),
                    onCancel = { route = XyRoute.Devices },
                    onSubmit = { profile, remember, startNow -> submit(profile, remember, startNow) },
                )

                XyRoute.Devices -> if (section == XySection.PERANGKAT) {
                    DevicesScreen(
                        favorites = favorites,
                        crashLog = crashLog,
                        bootTail = bootTail,
                        onMenu = { scope.launch { drawerState.open() } },
                        onAdd = { route = XyRoute.EditDevice(null) },
                        onEdit = { route = XyRoute.EditDevice(it) },
                        onConnect = { connect(it) },
                        onDelete = { profile ->
                            scope.launch {
                                repo.remove(profile.id)
                                id.xydesk.remote.core.RdpOptions.clear(context, profile.id)
                                DisplayPrefs.clear(context, profile.id)
                            }
                        },
                        onShowCrash = { showCrash = true },
                        onShowBoot = { showBoot = true },
                        onClearCrash = {
                            CrashLog.clear(context.applicationContext)
                            crashLog = null
                        },
                        crashLogText = crashLog,
                        onDismissCrash = { showCrash = false },
                        onDismissBoot = { showBoot = false },
                    )
                } else {
                    SectionScreen(
                        section = section,
                        favorites = favorites,
                        onMenu = { scope.launch { drawerState.open() } },
                        appPrefs = appPrefs,
                        onEditDevice = { route = XyRoute.EditDevice(it) },
                        onClearAllCredentials = {
                            scope.launch {
                                repo.clear()
                                Toast.makeText(context, "Kredensial & perangkat dihapus", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShowLog = { showBoot = true },
                    )
                }

                XyRoute.Section -> Unit
            }
        }
    }

    if (showCrash) {
        InfoDialog(
            title = "Log error terakhir",
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
            title = "Log sesi terakhir",
            body = body,
            onDismiss = { showBoot = false },
        )
    }
}

@Composable
private fun DevicesScreen(
    favorites: List<ConnectionProfile>,
    crashLog: String?,
    bootTail: List<String>,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ConnectionProfile) -> Unit,
    onConnect: (ConnectionProfile) -> Unit,
    onDelete: (ConnectionProfile) -> Unit,
    onShowCrash: () -> Unit,
    onShowBoot: () -> Unit,
    onClearCrash: () -> Unit,
    crashLogText: String?,
    onDismissCrash: () -> Unit,
    onDismissBoot: () -> Unit,
) {
    if (crashLog != null) {
        Banner(
            text = "Terjadi error sebelumnya — ketuk untuk lihat log",
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
            text = "Sesi terakhir terhenti di tengah jalan — ketuk untuk lihat log",
            onClick = onShowBoot,
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    XyTopBar(
        title = "Perangkat",
        onBack = null,
        actions = {
            XyIconPill(XyIcons.Menu, onMenu, contentDescription = "Menu")
            XyIconPill(XyIcons.Plus, onAdd, active = true, contentDescription = "Tambah perangkat")
        },
    )

    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            XyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Belum ada perangkat", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tambahkan PC Windows atau server dengan RDP aktif. " +
                        "Bisa lewat IP lokal, alamat publik, atau nama tailnet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                XyPillButton("Tambah perangkat", onAdd, icon = XyIcons.Plus)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(favorites, key = { it.id }) { profile ->
            DeviceCard(
                profile = profile,
                onConnect = { onConnect(profile) },
                onEdit = { onEdit(profile) },
                onDelete = { onDelete(profile) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DeviceCard(
    profile: ConnectionProfile,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Art preview milik app (bukan wallpaper RDP/OS): geometris, diturunkan
    // dari nama perangkat + jenis OS sebagai penanda kecil di pojok.
    val wall = XyWall.WIN11.forDevice(profile.label ?: profile.host)
    val previewSeed = (profile.label ?: profile.host) + "|" + profile.host
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onConnect),
    ) {
        // Preview desktop penuh (bukan banner gepeng): wallpaper mengisi
        // seluruh kotak membulat, ditutup scrim gelap di bawah supaya teks
        // tetap terbaca, plus strip taskbar tipis sebagai penanda OS.
        Box(
            Modifier
                .fillMaxWidth()
                .height(172.dp)
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
                    buildString {
                        append(profile.host).append(':').append(profile.port)
                        if (!profile.username.isNullOrBlank()) append("  ·  ").append(profile.username)
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
            XyPillButton("Connect", onConnect, compact = true)
            Spacer(Modifier.weight(1f))
            XyIconPill(XyIcons.Gear, onEdit, size = 40.dp, contentDescription = "Ubah")
            XyIconPill(XyIcons.Trash, onDelete, size = 40.dp, contentDescription = "Hapus")
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

/** Dipakai activity untuk memaksa ulang komposisi saat mode tema berubah. */
internal fun recreateActivity(context: Context) {
    (context as? Activity)?.recreate()
}
