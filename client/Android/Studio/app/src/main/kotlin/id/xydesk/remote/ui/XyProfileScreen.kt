package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyDialog
import id.xydesk.remote.ui.components.XyGlassCard
import id.xydesk.remote.ui.components.XyGlassRow
import id.xydesk.remote.ui.components.XyGlassSectionLabel
import id.xydesk.remote.ui.components.XyGlassTile
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyLogo
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyTileGrid
import id.xydesk.remote.ui.components.XyTopBar
import kotlinx.coroutines.launch
import id.xydesk.remote.ui.theme.XyThemeState

/**
 * Layar Profil — dashboard kartu.
 *
 * Enam seksi yang dulu berdesakan di nav bawah sekarang jadi ubin di grid,
 * dikelompokkan dengan judul seksi supaya hierarkinya jelas tanpa perlu
 * menggulir jauh. Gaya kartunya memakai `XyGlass`,
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
    val profileScope = androidx.compose.runtime.rememberCoroutineScope()
    // Cek pembaruan manual dari Profil (melengkapi cek otomatis 12 jam di Beranda).
    var updateChecking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var otaSheetOpen by remember { mutableStateOf(false) }
    var otaProgress by remember { mutableStateOf<Int?>(null) }
    var otaStatusText by remember { mutableStateOf<String?>(null) }
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
            actions = {
                XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = "Menu")
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Kartu Utama Profil (Ronde 54: terpadu, bersih, tidak menumpuk) ----
            CommunityAccountCard(
                versionName = versionName,
                deviceCount = deviceCount,
            )
            CloudSyncCard()

            // ---- seksi sebagai grid kartu ----
            XyGlassSectionLabel(xy("Pengaturan", "Settings"))
            XyTileGrid(columns = 2, spacing = 10.dp) {
                XySection.entries.forEach { section ->
                    // sectionTitle() @Composable; dipanggil di sini, di luar
                    // lambda item {} yang disimpan lalu dipanggil belakangan.
                    val title = sectionTitle(section)
                    val subtitle = sectionSubtitle(section)
                    item {
                        XyGlassTile(
                            icon = sectionIcon(section),
                            title = title,
                            subtitle = subtitle,
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
                onClick = onShowLog,
            )
            XyGlassRow(
                icon = XyIcons.Heart,
                title = xy("Masukan & saran", "Feedback & suggestions"),
                onClick = onOpenFeedback,
            )
            XyGlassRow(
                icon = XyIcons.Refresh,
                title = if (updateChecking) xy("Memeriksa…", "Checking…")
                else xy("Pusat Pembaruan Aplikasi", "App Update Center"),
                onClick = {
                    otaSheetOpen = true
                    if (!updateChecking) {
                        profileScope.launch {
                            updateChecking = true
                            val info = AppUpdate.fetch(context)
                            val current = runCatching {
                                context.packageManager
                                    .getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull()
                            updateChecking = false
                            if (info != null && current != null && AppUpdate.isNewer(info.version, current)) {
                                updateAvailable = info
                            }
                        }
                    }
                },
            )

            // ---- identitas app: footer ringkas, bukan kartu (rapi & clean) ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                XyLogo(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    buildString {
                        append(xy("Versi", "Version"))
                        append(" ")
                        append(versionName ?: "-")
                        append("  ·  ")
                        append(xy("{0} perangkat tersimpan", "{0} saved devices", deviceCount))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (otaSheetOpen) {
        XyOverlay(
            title = xy("Pusat Pembaruan Langsung", "Direct Update Center"),
            onDismiss = { otaSheetOpen = false },
        ) {
            Text(
                text = "APK: v${versionName ?: "-"}  ·  Paket Data OTA: ${AppUpdate.installedDataVersion(context)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = otaStatusText ?: xy(
                    "Untuk update ringan (konfigurasi, runner OS, stiker, patch protokol) gunakan Download Data di bawah tanpa perlu instal ulang APK. Untuk rilis besar gunakan Unduh APK.",
                    "For lightweight updates (configs, OS runners, stickers, protocol patches) use Download Data below without reinstalling the APK. For major releases use Download APK.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            otaProgress?.let { pct ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Text(
                        "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            XyPillButton(
                text = xy("Download Data Update (OTA Ringan In-App)", "Download Data Update (Lightweight In-App OTA)"),
                onClick = {
                    profileScope.launch {
                        otaProgress = 8
                        AppUpdate.downloadAndApplyDataPack(context) { pct, lbl ->
                            otaProgress = pct
                            otaStatusText = lbl
                        }.onFailure { err ->
                            otaStatusText = err.message
                        }
                    }
                },
                primary = true,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = xy("Unduh APK (Update Besar)", "Download APK (Major Update)"),
                    onClick = {
                        openInAppCustomTab(context, "https://api.xydeskremote.biz.id/XyDesk-Remote64bit.apk")
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Tutup", "Close"),
                    onClick = { otaSheetOpen = false },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    updateMessage?.let { msg ->
        XyDialog(
            title = xy("Pembaruan", "Updates"),
            body = msg,
            confirmLabel = xy("Tutup", "Close"),
            onConfirm = { updateMessage = null },
            onDismiss = { updateMessage = null },
        )
    }
    updateAvailable?.let { info ->
        XyDialog(
            title = xy("Versi {0} tersedia", "Version {0} available", info.version),
            body = if (info.changelog.isBlank()) {
                xy("Pembaruan baru siap diunduh.", "A new update is ready to download.")
            } else {
                info.changelog.take(600)
            },
            confirmLabel = xy("Unduh sekarang", "Download now"),
            onConfirm = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                if (info.url.isNotBlank()) info.url
                                else "https://www.xydeskremote.biz.id/",
                            ),
                        ),
                    )
                }
                updateAvailable = null
            },
            dismissLabel = xy("Nanti", "Later"),
            onDismiss = { updateAvailable = null },
        )
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

/** Keterangan singkat tiap seksi (ronde 45) supaya kartu tidak polos judul. */
@Composable
private fun sectionSubtitle(section: XySection): String = when (section) {
    XySection.PERANGKAT -> xy("Daftar & koneksi PC", "PC list & connections")
    XySection.TAMPILAN -> xy("Tema & gaya menu", "Theme & menu style")
    XySection.KREDENSIAL -> xy("Akun & kredensial RDP", "Accounts & RDP credentials")
    XySection.UMUM -> xy("Bahasa & perilaku app", "Language & app behavior")
    XySection.KEAMANAN -> xy("Kunci perangkat & sesi", "Device & session locks")
    XySection.TENTANG -> xy("Versi & kredit", "Version & credits")
}

/** Pil kecil berteks untuk deret info (versi, jumlah perangkat). */
@Composable
private fun ProfileChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Kartu cadangan cloud untuk setelan & kontrol kustom (permintaan pemilik:
 * setelan bertahan melewati pasang ulang — pulihkan akun lewat kode
 * pemulihan, lalu "Pulihkan dari Cloud"). Kata sandi, sertifikat, token, dan
 * riwayat clipboard tidak pernah ikut diunggah (lihat [CloudSync]).
 */
@Composable
private fun CloudSyncCard() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val stampPrefs = remember {
        context.getSharedPreferences("xydesk.app", android.content.Context.MODE_PRIVATE)
    }

    XyGlassCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                xy("Sinkron Cloud", "Cloud sync"),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                xy(
                    "Setelan & kontrol dicadangkan tanpa kata sandi/token. Habis pasang ulang? Pakai kode pemulihan lalu ketuk \"Pulihkan dari Cloud\".",
                    "Settings & controls backed up without passwords/tokens. After reinstalling, enter your recovery code and tap \"Restore from cloud\".",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stampPrefs.getString("cloud_sync_last", null)?.let { stamp ->
                Text(
                    xy("Sinkron terakhir: ", "Last sync: ") + stamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = if (busy) xy("Memproses…", "Working…") else xy("Unggah sekarang", "Upload now"),
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                val at = CloudSync.upload(context)
                                stampPrefs.edit().putString("cloud_sync_last", at).apply()
                                message = xyNow("Cadangan berhasil diunggah.", "Backup uploaded.")
                                isError = false
                            } catch (error: Exception) {
                                message = xyNow("Gagal mengunggah. Periksa jaringan.", "Upload failed. Check your connection.")
                                isError = true
                            } finally {
                                busy = false
                            }
                        }
                    },
                    compact = true,
                    enabled = !busy,
                )
                XyPillButton(
                    text = xy("Pulihkan dari Cloud", "Restore from cloud"),
                    onClick = { confirmRestore = true },
                    primary = false,
                    compact = true,
                    enabled = !busy,
                )
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmRestore) {
        XyDialog(
            title = xy("Pulihkan setelan?", "Restore settings?"),
            body = xy(
                "Setelan dan kontrol lokal akan diganti dengan cadangan cloud. Sesi yang berjalan perlu dibuka ulang.",
                "Local settings and controls will be replaced with the cloud backup. Open sessions must be reopened.",
            ),
            confirmLabel = xy("Pulihkan", "Restore"),
            onConfirm = {
                confirmRestore = false
                scope.launch {
                    busy = true
                    message = null
                    try {
                        val blob = CloudSync.download(context)
                        if (blob == null) {
                            message = xyNow("Belum ada cadangan di cloud untuk akun ini.", "No cloud backup found for this account.")
                            isError = true
                        } else {
                            val count = CloudSync.restore(context, blob)
                            message = xyNow(
                                "$count setelan dipulihkan, termasuk akun feed-mu — postingan lama bisa diedit/dihapus lagi. " +
                                    "Buka ulang aplikasi agar semuanya terpakai.",
                                "$count values restored, including your feed account — old posts can be edited/deleted again. " +
                                    "Reopen the app to apply everything.",
                            )
                            isError = false
                        }
                    } catch (error: Exception) {
                        message = xyNow("Gagal memulihkan. Periksa jaringan.", "Restore failed. Check your connection.")
                        isError = true
                    } finally {
                        busy = false
                    }
                }
            },
            dismissLabel = xy("Batal", "Cancel"),
            onDismiss = { confirmRestore = false },
        )
    }
}

/**
 * Kartu akun komunitas.
 *
 * Tanpa login: otomatis jadi tamu dengan ID unik (XY-XXXXXX) yang bisa
 * dipulihkan setelah pasang ulang lewat kode pemulihan. Login GitHub (PAT di
 * fitur RDP Gratis) otomatis mengisi nama tampilan. Nama bisa dikustom.
 */
@Composable
private fun CommunityAccountCard(
    versionName: String? = null,
    deviceCount: Int = 0,
) {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var name by remember { mutableStateOf(api.displayName()) }
    var editing by remember { mutableStateOf(false) }
    var showRecoverySection by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var restoreDraft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val accountId = remember { api.friendlyAccountId() }

    val avatarFile = remember { java.io.File(context.filesDir, "xy_avatar.webp") }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(avatarFile) {
        val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (avatarFile.exists()) {
                    android.graphics.BitmapFactory.decodeFile(avatarFile.absolutePath)
                } else null
            }.getOrNull()
        }
        avatarBitmap = bmp
    }
    var avatarBusy by remember { mutableStateOf(false) }
    var githubBusy by remember { mutableStateOf(false) }
    var githubWaitingCode by remember { mutableStateOf<String?>(null) }
    var githubWaitingUrl by remember { mutableStateOf<String?>(null) }
    val authRev = GitHubDirectOAuthController.authRevision
    var githubUser by remember(authRev) { mutableStateOf(getVerifiedGitHubLogin(context)) }
    LaunchedEffect(authRev) {
        val currentGh = getVerifiedGitHubLogin(context)
        if (!currentGh.isNullOrBlank()) {
            githubUser = currentGh
            if (name.isNullOrBlank()) {
                name = api.displayName() ?: currentGh
            }
        }
    }
    val isFounder = isFounderAccount(githubUser) || isFounderAccount(name)
    val pickAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                avatarBusy = true
                try {
                    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val src = raw?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                    if (src == null) {
                        message = xyNow("Gambar tidak terbaca.", "Couldn't read the image.")
                    } else {
                        val side = 512
                        val scale = minOf(1f, side / maxOf(src.width, src.height).toFloat())
                        val bmp = if (scale < 1f) {
                            android.graphics.Bitmap.createScaledBitmap(
                                src, (src.width * scale).toInt().coerceAtLeast(1),
                                (src.height * scale).toInt().coerceAtLeast(1), true,
                            )
                        } else src
                        val stream = java.io.ByteArrayOutputStream()
                        val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) {
                            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            android.graphics.Bitmap.CompressFormat.WEBP
                        }
                        bmp.compress(fmt, 90, stream)
                        val bytes = stream.toByteArray()
                        if (bytes.size > 1_200_000) {
                            message = xyNow("Foto terlalu besar. Coba gambar lain.", "Photo too large. Try another image.")
                        } else {
                            avatarFile.writeBytes(bytes)
                            avatarBitmap = bmp
                            runCatching { api.uploadAvatar(bytes, "image/webp") }
                            message = xyNow("Foto profil HD berhasil disimpan.", "HD profile photo saved.")
                        }
                    }
                } finally {
                    avatarBusy = false
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val savedGh = getVerifiedGitHubLogin(context)
        val gh = savedGh ?: runCatching {
            val pat = id.xydesk.remote.security.CredentialVault(context.applicationContext)
                .get(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID)
            if (pat != null) id.xydesk.remote.ui.rdp.XyRdpClient.whoami(pat) else null
        }.getOrNull()
        if (gh != null) {
            githubUser = gh
            saveVerifiedGitHubLogin(context, gh)
            if (name == null || api.displayName() == null) {
                runCatching { api.setDisplayName(gh) }
                name = api.displayName() ?: gh
            }
            // Muat otomatis foto profil GitHub bila belum ada foto profil lokal
            if (avatarBitmap == null) {
                val ghBmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val urlStr = getVerifiedGitHubAvatarUrl(context)?.takeIf { it.isNotBlank() }
                            ?: "https://github.com/$gh.png?size=160"
                        val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 6000
                        conn.readTimeout = 6000
                        conn.instanceFollowRedirects = true
                        if (conn.responseCode in 200..299) {
                            val bytes = conn.inputStream.use { it.readBytes() }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } else null
                    }.getOrNull()
                }
                if (ghBmp != null) avatarBitmap = ghBmp
            }
        }
    }

    val activeUsageSecs = remember { recordAndGetLocalUsageSeconds(context, 0L) }
    val sessionsPlayed = remember { getLocalUsageSessionCount(context) }
    val favStickersCount = remember { CustomStickerVault.loadFavoriteKeys(context).size }

    XyGlassCard(padding = 16.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Avatar + tombol kamera di sudut kanan bawah avatar
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clickable(enabled = !avatarBusy) { pickAvatar.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                        .let { mod ->
                            if (isFounder) mod.border(2.dp, founderBorderBrush(), RoundedCornerShape(28.dp)) else mod
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = avatarBitmap
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = xy("Foto profil", "Profile photo"),
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Text(
                            (name?.take(1) ?: githubUser?.take(1) ?: "T").uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = XyIcons.Camera,
                        contentDescription = xy("Ganti foto", "Change photo"),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = name ?: githubUser ?: xy("Akun $accountId", "Account $accountId"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    XyUserRoleBadges(
                        name = name,
                        githubLogin = githubUser,
                        compact = true,
                    )
                }
                Text(
                    text = buildString {
                        if (!githubUser.isNullOrBlank()) {
                            append("@$githubUser  ·  ")
                        }
                        append("ID $accountId")
                        if (!versionName.isNullOrBlank()) {
                            append("  ·  v$versionName")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            // Deret ikon aksi di pojok kanan kartu profil (Ganti nama, Pemulihan)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                XyIconPill(
                    XyIcons.Edit,
                    { editing = !editing; draft = name.orEmpty() },
                    active = editing,
                    contentDescription = xy("Ganti nama", "Change name"),
                )
                XyIconPill(
                    XyIcons.Key,
                    {
                        showRecoverySection = !showRecoverySection
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(api.restoreCode()))
                        message = xyNow("Kode pemulihan akun disalin ke clipboard.", "Account recovery code copied to clipboard.")
                    },
                    active = showRecoverySection,
                    contentDescription = xy("Pemulihan Akun", "Account Recovery"),
                )
            }
        }

        // Baris Status & Tombol Login GitHub 1-Klik via In-App Browser (Chrome Custom Tab)
        val ghUser = githubUser
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GitHubLogoIcon(size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f)) {
                Text(
                    if (ghUser != null) xy("GitHub: @$ghUser", "GitHub: @$ghUser")
                    else xy("Hubungkan GitHub", "Connect GitHub"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    if (ghUser != null) xy("Sinkron RDP Cloud & Chat Global aktif", "Cloud RDP & Global Chat sync active")
                    else xy("1-klik login & Authorize otomatis", "1-tap sign in & auto Authorize"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (ghUser != null) {
                XyPillButton(
                    text = xy("Keluar", "Sign out"),
                    onClick = {
                        scope.launch {
                            runCatching {
                                id.xydesk.remote.security.CredentialVault(context.applicationContext)
                                    .remove(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID)
                            }
                            githubUser = null
                            saveVerifiedGitHubLogin(context, null)
                            message = xyNow("Akun GitHub dilepas.", "GitHub account removed.")
                        }
                    },
                    primary = false,
                    compact = true,
                )
            } else {
                XyPillButton(
                    text = if (githubBusy) xy("Menghubungkan…", "Connecting…") else xy("Masuk GitHub", "Sign in"),
                    onClick = {
                        githubBusy = true
                        scope.launch {
                            performGitHubInAppBrowserLogin(
                                context = context,
                                onCodeReady = { _, _ -> },
                                onSuccess = { _, user ->
                                    githubBusy = false
                                    githubWaitingCode = null
                                    githubWaitingUrl = null
                                    githubUser = user
                                    name = api.displayName() ?: user
                                    message = xyNow("Terhubung sebagai @$user!", "Connected as @$user!")
                                },
                                onError = { err ->
                                    githubBusy = false
                                    message = err
                                },
                            )
                        }
                    },
                    primary = true,
                    compact = true,
                )
            }
        }

        // Statistik Aktivitas Profil (Flat, Rapi, Tanpa Card Bertumpuk)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatLeaderboardDuration(activeUsageSecs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = xy("Waktu Aktif", "Active Time"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$sessionsPlayed",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = xy("Sesi Main", "Sessions"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$deviceCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = xy("Perangkat", "Devices"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$favStickersCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = xy("Stiker Fav", "Fav Stickers"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (editing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                                xy("Nama tampilan baru (maks 24)", "New display name (max 24)"),
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
                                    editing = false
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

        if (showRecoverySection) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                                xy("Tempel kode pemulihan akun lama…", "Paste old account recovery code…"),
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
                            showRecoverySection = false
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
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Client ID OAuth App GitHub untuk login Device Flow (tanpa tempel token —
 * user cukup mengetik kode 8 karakter di github.com/login/device).
 *
 * KOSONG = mode Personal Access Token lama. Untuk mengaktifkan: buat OAuth
 * App di GitHub (Settings → Developer settings → OAuth Apps), tidak perlu
 * secret/redirect, lalu tempel Client ID-nya di sini.
 */
internal const val GITHUB_OAUTH_CLIENT_ID = "Ov23liBsgZ5xJKgzHGtl"

internal suspend fun githubDeviceStart(clientId: String): org.json.JSONObject? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val body = "client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8") +
                "&scope=" + java.net.URLEncoder.encode("repo workflow read:user", "UTF-8")
            val conn = java.net.URL("https://github.com/login/device/code")
                .openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) return@runCatching null
            org.json.JSONObject(conn.inputStream.bufferedReader().readText())
        }.getOrNull()
    }

internal suspend fun githubDevicePoll(clientId: String, deviceCode: String): org.json.JSONObject? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val body = "client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8") +
                "&device_code=" + java.net.URLEncoder.encode(deviceCode, "UTF-8") +
                "&grant_type=" + java.net.URLEncoder.encode(
                    "urn:ietf:params:oauth:grant-type:device_code",
                    "UTF-8",
                )
            val conn = java.net.URL("https://github.com/login/oauth/access_token")
                .openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) return@runCatching null
            org.json.JSONObject(conn.inputStream.bufferedReader().readText())
        }.getOrNull()
    }

/**
 * Ambil profil lengkap GitHub (`/user`) dan sinkronkan login, nama tampilan,
 * serta avatar lokal bila pengguna belum memasang foto profil kustom.
 */
internal suspend fun syncGitHubProfileAndAvatar(
    context: android.content.Context,
    token: String,
): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val conn = java.net.URL("https://api.github.com/user")
            .openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        conn.setRequestProperty("User-Agent", "XyDeskRemote")
        if (conn.responseCode !in 200..299) return@runCatching null
        val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
        val login = json.optString("login", "").trim().takeIf { it.isNotEmpty() } ?: return@runCatching null
        val avatarUrl = json.optString("avatar_url", "").trim()
        saveVerifiedGitHubLogin(context, login, avatarUrl)
        val avatarFile = java.io.File(context.filesDir, "xy_avatar.webp")
        if (!avatarFile.exists() && avatarUrl.startsWith("https://")) {
            runCatching {
                val imgConn = java.net.URL(avatarUrl).openConnection() as java.net.HttpURLConnection
                imgConn.connectTimeout = 10_000
                imgConn.readTimeout = 10_000
                val raw = imgConn.inputStream.use { it.readBytes() }
                val src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                if (src != null) {
                    val bmp = android.graphics.Bitmap.createScaledBitmap(src, 256, 256, true)
                    val out = java.io.ByteArrayOutputStream()
                    val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        android.graphics.Bitmap.CompressFormat.WEBP
                    }
                    bmp.compress(fmt, 85, out)
                    val bytes = out.toByteArray()
                    if (bytes.size in 1..200_000) {
                        avatarFile.writeBytes(bytes)
                        runCatching {
                            CommunityJokesApi(context).uploadAvatar(bytes, "image/webp")
                        }
                    }
                }
            }
        }
        login
    }.getOrNull()
}

/**
 * Ronde 52 — Login GitHub Instan Tanpa Ketik Kode:
 * Membuka halaman GitHub langsung di dalam panel `WebView` terintegrasi dan
 * menginjeksikan kode otorisasi secara otomatis via JavaScript begitu halaman
 * `/login/device` terbuka. Pengguna hanya perlu menekan tombol hijau
 * **"Authorize"** sekali tanpa perlu menyalin atau mengetik kode 8 digit.
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GitHubDeviceFlowPanel(
    onCancel: () -> Unit,
    onSuccess: (token: String, user: String) -> Unit,
    onError: (String) -> Unit,
) {
    var busy by remember { mutableStateOf(true) }
    var userCode by remember { mutableStateOf("") }
    var verifyUri by remember { mutableStateOf("https://github.com/login/device") }
    var statusText by remember { mutableStateOf(xyNow("Menyiapkan halaman otorisasi GitHub…", "Preparing GitHub authorization…")) }
    var showPatFallback by remember { mutableStateOf(false) }
    var patDraft by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    fun buildAutoFillScript(code: String): String {
        val safe = code.replace("'", "")
        return """
            (function() {
              try {
                var code = '$safe';
                var clean = code.replace(/[^A-Za-z0-9]/g, '');
                if (!clean || window.__xyCodeSubmitted === clean) return 'DONE';
                var boxes = document.querySelectorAll('input[id^="user-code"], input.js-user-code-field, input[name*="user_code"]');
                if (boxes && boxes.length >= 8) {
                  var idx = 0;
                  for (var i = 0; i < boxes.length && idx < clean.length; i++) {
                    var el = boxes[i];
                    if (el.type === 'hidden' || el.disabled || el.readOnly) continue;
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    if (setter) setter.call(el, clean[idx++]); else el.value = clean[idx++];
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                  }
                  window.__xyCodeSubmitted = clean;
                  var btn = document.querySelector('form button[type="submit"], form input[type="submit"], button.btn-primary');
                  if (btn && !btn.disabled) {
                    setTimeout(function() { btn.click(); }, 120);
                  }
                  return 'FILLED_MULTI';
                }
                var single = document.querySelector('input[name="user_code"], input#user_code');
                if (single) {
                  single.value = code;
                  single.dispatchEvent(new Event('input', { bubbles: true }));
                  window.__xyCodeSubmitted = clean;
                  if (single.form) single.form.submit();
                  return 'FILLED_SINGLE';
                }
              } catch (e) {}
              return 'WAIT';
            })();
        """.trimIndent()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        busy = true
        val start = githubDeviceStart(GITHUB_OAUTH_CLIENT_ID)
        if (start == null) {
            busy = false
            statusText = xyNow(
                "Gagal menghubungi GitHub. Periksa koneksi atau gunakan opsi Token di bawah.",
                "Could not reach GitHub. Check your connection or use the Token option below.",
            )
            return@LaunchedEffect
        }
        userCode = start.optString("user_code", "")
        verifyUri = start.optString("verification_uri", "https://github.com/login/device")
        val intervalMs = (start.optInt("interval", 5).coerceIn(3, 15)) * 1000L
        val deviceCode = start.optString("device_code", "")
        val deadline = System.currentTimeMillis() + start.optInt("expires_in", 900) * 1000L
        busy = false
        statusText = xyNow(
            "Silakan login (jika belum) lalu tekan tombol hijau 'Authorize' di bawah — tanpa perlu ketik kode!",
            "Sign in (if needed) and tap the green 'Authorize' button below — no code typing needed!",
        )
        webViewRef?.evaluateJavascript(buildAutoFillScript(userCode), null)

        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(intervalMs)
            webViewRef?.evaluateJavascript(buildAutoFillScript(userCode), null)
            val poll = githubDevicePoll(GITHUB_OAUTH_CLIENT_ID, deviceCode) ?: continue
            val tok = poll.optString("access_token")
            if (tok.isNotEmpty()) {
                busy = true
                statusText = xyNow("Menyinkronkan profil & avatar GitHub…", "Syncing GitHub profile & avatar…")
                val user = syncGitHubProfileAndAvatar(context, tok)
                    ?: id.xydesk.remote.ui.rdp.XyRdpClient.whoami(tok)
                busy = false
                if (user != null) {
                    onSuccess(tok, user)
                } else {
                    onError(xyNow("Login gagal: profil tidak terbaca.", "Sign-in failed: profile unreadable."))
                }
                return@LaunchedEffect
            }
            when (poll.optString("error", "")) {
                "expired_token" -> {
                    statusText = xyNow("Sesi login kedaluwarsa — tutup dan buka ulang.", "Sign-in session expired — reopen dialog.")
                    return@LaunchedEffect
                }
                "access_denied" -> {
                    statusText = xyNow("Login dibatalkan di GitHub.", "Sign-in cancelled on GitHub.")
                    return@LaunchedEffect
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GitHubLogoIcon(size = 20.dp, tint = MaterialTheme.colorScheme.primary)
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }

        if (!showPatFallback) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp),
                    ),
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = settings.userAgentString + " XyDeskRemote/1.1.17"
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (userCode.isNotBlank()) {
                                        view?.evaluateJavascript(buildAutoFillScript(userCode), null)
                                        view?.postDelayed({
                                            view.evaluateJavascript(buildAutoFillScript(userCode), null)
                                        }, 350L)
                                    }
                                }
                            }
                            loadUrl(verifyUri)
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        webViewRef = view
                        if (userCode.isNotBlank()) {
                            view.evaluateJavascript(buildAutoFillScript(userCode), null)
                        }
                    },
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    xy(
                        "Atau tempel Personal Access Token GitHub (scope: repo, workflow, read:user):",
                        "Or paste a GitHub Personal Access Token (scope: repo, workflow, read:user):",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                id.xydesk.remote.ui.components.XyField(
                    value = patDraft,
                    onValueChange = { patDraft = it.trim() },
                    label = "GitHub Token (ghp_…)",
                    hint = "ghp_xxxxxxxxxxxxxxxxxxxx",
                    isPassword = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        text = xy("Buat Token di GitHub ↗", "Create Token on GitHub ↗"),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/settings/tokens/new?description=XyDesk+Remote&scopes=repo,workflow,read:user"),
                                    ),
                                )
                            }
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        text = xy("Masuk dengan Token", "Sign in with Token"),
                        onClick = {
                            scope.launch {
                                busy = true
                                val tok = patDraft.trim()
                                val user = syncGitHubProfileAndAvatar(context, tok)
                                    ?: id.xydesk.remote.ui.rdp.XyRdpClient.whoami(tok)
                                busy = false
                                if (user != null) onSuccess(tok, user)
                                else statusText = xyNow("Token tidak valid.", "Invalid token.")
                            }
                        },
                        enabled = !busy && patDraft.length >= 10,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XyPillButton(
                text = if (showPatFallback) xy("Pakai Login Instan", "Use Instant Login")
                else xy("Opsi Token / Browser", "Token / Browser Option"),
                onClick = { showPatFallback = !showPatFallback },
                primary = false,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            XyPillButton(
                text = xy("Tutup", "Close"),
                onClick = onCancel,
                primary = false,
                compact = true,
            )
        }
    }
}

/**
 * Ronde 52 — Komponen Ikon Header Atas (Avatar Profil + Logo GitHub) yang
 * ditempatkan langsung di pojok kanan atas Header Beranda, Feed, dan Chat Global.
 *
 * Klik ikon Logo GitHub atau Avatar langsung membuka pop-up cepat untuk:
 * - Ganti Foto Profil
 * - Ganti Nama Tampilan
 * - Login / Keluar GitHub (tanpa ketik kode)
 */
@Composable
internal fun XyHeaderAccountActions(
    onStateChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val api = remember(context) { CommunityJokesApi(context) }
    var quickOpen by remember { mutableStateOf(false) }
    var githubLoginOpen by remember { mutableStateOf(false) }
    var ghUser by remember { mutableStateOf(getVerifiedGitHubLogin(context)) }
    var displayName by remember { mutableStateOf(api.displayName() ?: ghUser ?: chatNickname(context)) }
    var nameDraft by remember { mutableStateOf(displayName) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var savingName by remember { mutableStateOf(false) }
    var avatarTick by remember { mutableIntStateOf(0) }
    val avatarFile = remember { java.io.File(context.filesDir, "xy_avatar.webp") }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val authRev = GitHubDirectOAuthController.authRevision

    androidx.compose.runtime.LaunchedEffect(avatarTick, quickOpen, ghUser, authRev) {
        ghUser = getVerifiedGitHubLogin(context)
        displayName = api.displayName() ?: ghUser ?: chatNickname(context)
        val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (avatarFile.exists()) android.graphics.BitmapFactory.decodeFile(avatarFile.absolutePath) else null
            }.getOrNull()
        }
        avatarBitmap = bmp
    }

    val pickHeaderAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                val src = raw?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (src != null) {
                    val side = 256
                    val scale = minOf(1f, side / maxOf(src.width, src.height).toFloat())
                    val bmp = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            src,
                            (src.width * scale).toInt().coerceAtLeast(1),
                            (src.height * scale).toInt().coerceAtLeast(1),
                            true,
                        )
                    } else src
                    val stream = java.io.ByteArrayOutputStream()
                    val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        android.graphics.Bitmap.CompressFormat.WEBP
                    }
                    bmp.compress(fmt, 85, stream)
                    val bytes = stream.toByteArray()
                    if (bytes.size <= 200_000) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            avatarFile.writeBytes(bytes)
                            runCatching { api.uploadAvatar(bytes, "image/webp") }
                        }
                        avatarBitmap = bmp
                        avatarTick += 1
                        statusMsg = xyNow("Foto profil diperbarui!", "Profile photo updated!")
                        onStateChanged()
                    }
                }
            }
        }
    }

    val founder = isFounderAccount(ghUser) || isFounderAccount(displayName)

    // 1) Ikon Logo GitHub di Header Atas
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                if (ghUser != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
            .border(
                width = 1.dp,
                color = if (ghUser != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = androidx.compose.foundation.shape.CircleShape,
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            onClick = { if (ghUser == null) githubLoginOpen = true else quickOpen = true },
            color = androidx.compose.ui.graphics.Color.Transparent,
            shape = androidx.compose.foundation.shape.CircleShape,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GitHubLogoIcon(
                    size = 18.dp,
                    tint = if (ghUser != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Titik indikator status login GitHub
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (ghUser != null) androidx.compose.ui.graphics.Color(0xFF10B981)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        ),
                )
            }
        }
    }

    // 2) Ikon Avatar Profil di Header Atas
    androidx.compose.material3.Surface(
        onClick = {
            nameDraft = displayName
            statusMsg = null
            quickOpen = true
        },
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier
            .size(34.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .let { mod ->
                if (founder) mod.border(1.5.dp, founderBorderBrush(), androidx.compose.foundation.shape.CircleShape)
                else mod.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    androidx.compose.foundation.shape.CircleShape,
                )
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bmp = avatarBitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Profil",
                    modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Text(
                    text = (displayName.take(1).ifBlank { "U" }).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }

    // Pop-up Header Cepat: Ganti Foto, Ganti Nama, & Status Login GitHub
    if (quickOpen) {
        XyOverlay(
            title = xy("Profil & Akun GitHub", "Profile & GitHub Account"),
            onDismiss = { quickOpen = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.Surface(
                        onClick = { pickHeaderAvatar.launch("image/*") },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(56.dp)
                            .let { m ->
                                if (founder) m.border(2.dp, founderBorderBrush(), androidx.compose.foundation.shape.CircleShape)
                                else m
                            },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val bmp = avatarBitmap
                            if (bmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    (displayName.take(1).ifBlank { "U" }).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            XyUserRoleBadges(
                                name = displayName,
                                githubLogin = ghUser,
                                compact = true,
                            )
                        }
                        Text(
                            if (ghUser != null) "GitHub: @$ghUser (Terhubung)"
                            else xy("GitHub belum terhubung", "GitHub not connected"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ghUser != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        XyPillButton(
                            text = xy("Ubah Foto Profil", "Change Profile Photo"),
                            onClick = { pickHeaderAvatar.launch("image/*") },
                            primary = false,
                            compact = true,
                        )
                    }
                }

                // Input Ganti Nama Cepat
                id.xydesk.remote.ui.components.XyField(
                    value = nameDraft,
                    onValueChange = { if (it.length <= 24) nameDraft = it },
                    label = xy("Nama Tampilan", "Display Name"),
                    hint = xy("Masukkan nama tampilan…", "Enter display name…"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        text = if (savingName) "…" else xy("Simpan Nama", "Save Name"),
                        onClick = {
                            val clean = nameDraft.trim()
                            if (clean.isNotEmpty() && !savingName) {
                                savingName = true
                                scope.launch {
                                    runCatching { api.setDisplayName(clean) }
                                    context.getSharedPreferences("xydesk.global.chat", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("nickname", clean).apply()
                                    displayName = api.displayName() ?: clean
                                    savingName = false
                                    statusMsg = xyNow("Nama tampilan disimpan!", "Display name saved!")
                                    onStateChanged()
                                }
                            }
                        },
                        compact = true,
                        enabled = !savingName && nameDraft.trim().isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    if (ghUser == null) {
                        XyPillButton(
                            text = xy("Login GitHub", "GitHub Sign In"),
                            onClick = {
                                quickOpen = false
                                githubLoginOpen = true
                            },
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        XyPillButton(
                            text = xy("Keluar GitHub", "Sign Out GitHub"),
                            onClick = {
                                runCatching {
                                    id.xydesk.remote.security.CredentialVault(context.applicationContext)
                                        .remove(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID)
                                }
                                saveVerifiedGitHubLogin(context, null, "")
                                ghUser = null
                                statusMsg = xyNow("Akun GitHub dilepas.", "GitHub account disconnected.")
                                onStateChanged()
                            },
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                statusMsg?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    LaunchedEffect(githubLoginOpen) {
        if (githubLoginOpen) {
            githubLoginOpen = false
            GitHubDirectOAuthController.open(
                onSuccess = { tok, user ->
                    runCatching {
                        id.xydesk.remote.security.CredentialVault(context.applicationContext)
                            .put(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID, tok)
                    }
                    saveVerifiedGitHubLogin(context, user)
                    ghUser = user
                    if (api.displayName().isNullOrBlank()) {
                        scope.launch {
                            runCatching { api.setDisplayName(user) }
                        }
                    }
                    displayName = api.displayName() ?: user
                    avatarTick += 1
                    onStateChanged()
                },
                onError = { statusMsg = it },
            )
        }
    }
}
