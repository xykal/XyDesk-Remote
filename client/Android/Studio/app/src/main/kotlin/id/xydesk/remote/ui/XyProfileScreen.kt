package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyGlassCard
import id.xydesk.remote.ui.components.XyGlassRow
import id.xydesk.remote.ui.components.XyGlassSectionLabel
import id.xydesk.remote.ui.components.XyGlassTile
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyLogo
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyTileGrid
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.components.XyWordmark
import kotlinx.coroutines.launch
import id.xydesk.remote.ui.theme.XyThemeState

/**
 * Layar Profil — dashboard kartu.
 *
 * Enam seksi yang dulu berdesakan di nav bawah sekarang jadi ubin di grid,
 * dikelompokkan dengan judul seksi supaya hierarkinya jelas tanpa perlu
 * menggulir jauh. Gaya kartunya sama dengan pemutar musik (lihat `XyGlass`),
 * sesuai permintaan pengguna agar seluruh app satu bahasa visual.
 */
@Composable
internal fun XyProfileScreen(
    onMenu: () -> Unit,
    onOpenSection: (XySection) -> Unit,
    appPrefs: AppPrefs,
    deviceCount: Int,
    onShowLog: () -> Unit,
    onOpenFeedback: () -> Unit,
) {
    val context = LocalContext.current
    // BuildConfig tidak dipakai di lapisan UI; versi dibaca dari PackageManager
    // supaya tetap benar tanpa menambah konfigurasi build.
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    // Mode tema sebagai state lokal supaya segmen langsung berpindah; nilai
    // sebenarnya tetap disimpan lewat XyThemeState.set (prefs + state global).
    var themeMode by remember {
        mutableIntStateOf(
            if (XyThemeState.mode == Int.MIN_VALUE) XyThemeState.FOLLOW_SYSTEM else XyThemeState.mode,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        XyTopBar(
            title = xy("Profil", "Profile"),
            actions = { XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = "Menu") },
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- akun komunitas (guest / GitHub) ----
            CommunityAccountCard()

            // ---- identitas app ----
            XyGlassCard(padding = 16.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        XyLogo(modifier = Modifier.size(26.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        XyWordmark(fontSize = 20.sp)
                        Text(
                            buildString {
                                append(xy("Versi", "Version"))
                                append(" ")
                                append(versionName ?: "-")
                                append("  ·  ")
                                append(
                                    xy(
                                        "{0} perangkat tersimpan",
                                        "{0} saved devices",
                                        deviceCount,
                                    ),
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- seksi sebagai grid kartu ----
            XyGlassSectionLabel(xy("Pengaturan", "Settings"))
            XyTileGrid(columns = 2, spacing = 10.dp) {
                XySection.entries.forEach { section ->
                    // sectionTitle() @Composable; dipanggil di sini, di luar
                    // lambda item {} yang disimpan lalu dipanggil belakangan.
                    val title = sectionTitle(section)
                    item {
                        XyGlassTile(
                            icon = sectionIcon(section),
                            title = title,
                            onClick = { onOpenSection(section) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- tema ----
            XyGlassSectionLabel(xy("Tampilan", "Appearance"))
            XyGlassCard(padding = 14.dp) {
                Text(
                    xy("Mode tema", "Theme mode"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    xy(
                        "Berlaku seketika di semua layar, tanpa memuat ulang app.",
                        "Applies instantly on every screen, without reloading the app.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                XySegmented(
                    options = listOf(
                        xy("Ikut sistem", "Follow system"),
                        xy("Gelap", "Dark"),
                        xy("Terang", "Light"),
                    ),
                    selectedIndex = themeMode,
                    onSelect = { index ->
                        themeMode = index
                        XyThemeState.set(appPrefs, index)
                    },
                )
            }

            // ---- lainnya ----
            XyGlassSectionLabel(xy("Lainnya", "More"))
            XyGlassRow(
                icon = XyIcons.Terminal,
                title = xy("Log koneksi", "Connection log"),
                subtitle = xy(
                    "20 baris terakhir dari log boot native.",
                    "Last 20 lines of the native boot log.",
                ),
                onClick = onShowLog,
            )
            XyGlassRow(
                icon = XyIcons.Heart,
                title = xy("Masukan & saran", "Feedback & suggestions"),
                subtitle = xy(
                    "Bagikan ke aplikasi mana pun, atau salin drafnya.",
                    "Share to any app, or copy the draft.",
                ),
                onClick = onOpenFeedback,
            )
        }
    }
}

/** Ikon per seksi — sama dengan pemetaan lama di nav bawah enam slot. */
private fun sectionIcon(section: XySection): ImageVector = when (section) {
    XySection.PERANGKAT -> XyIcons.Monitor
    XySection.TAMPILAN -> XyIcons.Fit
    XySection.KREDENSIAL -> XyIcons.Lock
    XySection.UMUM -> XyIcons.Sliders
    XySection.KEAMANAN -> XyIcons.Shield
    XySection.TENTANG -> XyIcons.Info
}

/**
 * Kartu akun komunitas.
 *
 * Tanpa login: otomatis jadi tamu dengan ID unik (XY-XXXXXX) yang bisa
 * dipulihkan setelah pasang ulang lewat kode pemulihan. Login GitHub (PAT di
 * fitur RDP Gratis) otomatis mengisi nama tampilan. Nama bisa dikustom.
 */
@Composable
private fun CommunityAccountCard() {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var name by remember { mutableStateOf(api.displayName()) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var restoreDraft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val accountId = remember { api.friendlyAccountId() }

    // Login GitHub (bila PAT tersimpan) otomatis jadi nama tampilan,
    // kecuali pengguna sudah memilih nama sendiri.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (name == null) {
            val pat = runCatching {
                id.xydesk.remote.security.CredentialVault(context.applicationContext)
                    .get(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID)
            }.getOrNull()
            if (pat != null) {
                val gh = runCatching { id.xydesk.remote.ui.rdp.XyRdpClient.whoami(pat) }.getOrNull()
                if (gh != null && api.displayName() == null) {
                    runCatching { api.setDisplayName(gh) }
                    name = api.displayName() ?: gh
                }
            }
        }
    }

    XyGlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (name?.take(1) ?: "T").uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name ?: xy("Tamu $accountId", "Guest $accountId"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "ID: $accountId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            XyIconPill(
                XyIcons.Clip,
                {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(api.restoreCode()))
                    message = xyNow("Kode pemulihan disalin. Simpan untuk pulihkan akun setelah pasang ulang.", "Recovery code copied. Keep it to restore this account after a reinstall.")
                },
                contentDescription = xy("Salin kode pemulihan", "Copy recovery code"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            XyPillButton(
                text = xy("Ganti nama", "Change name"),
                onClick = { editing = !editing; draft = name.orEmpty() },
                primary = false,
                compact = true,
            )
        }
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 24) draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                xy("Nama tampilan (maks 24)", "Display name (max 24)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                XyPillButton(
                    text = if (busy) "…" else xy("Simpan", "Save"),
                    onClick = {
                        if (!busy && draft.trim().isNotEmpty()) {
                            busy = true
                            scope.launch {
                                val ok = runCatching { api.setDisplayName(draft) }.getOrDefault(false)
                                message = if (ok) {
                                    name = api.displayName()
                                    xyNow("Nama tersimpan.", "Name saved.")
                                } else {
                                    xyNow("Gagal menyimpan. Cek jaringan / coba lagi.", "Couldn't save. Check network and retry.")
                                }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && draft.trim().isNotEmpty(),
                    compact = true,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(
                value = restoreDraft,
                onValueChange = { restoreDraft = it },
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { inner ->
                    if (restoreDraft.isEmpty()) {
                        Text(
                            xy("Kode pemulihan akun lama…", "Old account recovery code…"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            XyPillButton(
                text = xy("Pulihkan", "Restore"),
                onClick = {
                    val ok = api.restoreClientId(restoreDraft)
                    message = if (ok) {
                        editing = false
                        scope.launch {
                            name = runCatching { api.fetchServerDisplayName() }.getOrNull() ?: api.displayName()
                            name?.let { api.setDisplayNameLocally(it) }
                        }
                        xyNow("Akun dipulihkan. Nama lama dimuat ulang.", "Account restored. Reloading your old name.")
                    } else {
                        xyNow("Kode tidak valid. Tempel kode pemulihan yang dulu kamu salin.", "Invalid code. Paste the recovery code you copied before.")
                    }
                },
                primary = false,
                compact = true,
                enabled = restoreDraft.trim().isNotEmpty(),
            )
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
