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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
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
import id.xydesk.remote.ui.components.XyOverlay
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

    // Foto profil: cache lokal + unggah ke worker biar muncul di feed.
    val avatarFile = remember { java.io.File(context.filesDir, "xy_avatar.webp") }
    var avatarBitmap by remember {
        mutableStateOf(
            runCatching {
                if (avatarFile.exists()) {
                    android.graphics.BitmapFactory.decodeFile(avatarFile.absolutePath)
                } else null
            }.getOrNull(),
        )
    }
    var avatarBusy by remember { mutableStateOf(false) }
    var githubPatDraft by remember { mutableStateOf("") }
    var githubOpen by remember { mutableStateOf(false) }
    var githubBusy by remember { mutableStateOf(false) }
    var githubUser by remember { mutableStateOf<String?>(null) }
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
                        val side = 256
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
                        bmp.compress(fmt, 85, stream)
                        val bytes = stream.toByteArray()
                        if (bytes.size > 200_000) {
                            message = xyNow("Foto terlalu besar. Coba gambar lain.", "Photo too large. Try another image.")
                        } else {
                            avatarFile.writeBytes(bytes)
                            avatarBitmap = bmp
                            runCatching { api.uploadAvatar(bytes, "image/webp") }
                            message = xyNow("Foto profil disimpan.", "Profile photo saved.")
                        }
                    }
                } finally {
                    avatarBusy = false
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        githubUser = runCatching {
            val pat = id.xydesk.remote.security.CredentialVault(context.applicationContext)
                .get(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID)
            if (pat != null) id.xydesk.remote.ui.rdp.XyRdpClient.whoami(pat) else null
        }.getOrNull()
    }

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
                val bmp = avatarBitmap
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = xy("Foto profil", "Profile photo"),
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Text(
                        (name?.take(1) ?: "T").uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
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
            XyPillButton(
                text = if (avatarBusy) xy("Menyimpan…", "Saving…") else xy("Ganti foto", "Change photo"),
                onClick = {
                    pickAvatar.launch("image/*")
                },
                primary = false,
                compact = true,
                enabled = !avatarBusy,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ghUser = githubUser
            if (ghUser != null) {
                XyPillButton(
                    text = xy("GitHub: $ghUser", "GitHub: $ghUser"),
                    onClick = {},
                    primary = false,
                    compact = true,
                    enabled = false,
                )
                XyPillButton(
                    text = xy("Keluar", "Sign out"),
                    onClick = {
                        scope.launch {
                            runCatching {
                                id.xydesk.remote.security.CredentialVault(context.applicationContext)
                                    .remove(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID)
                            }
                            githubUser = null
                            message = xyNow("Akun GitHub dilepas.", "GitHub account removed.")
                        }
                    },
                    primary = false,
                    compact = true,
                )
            } else {
                XyPillButton(
                    text = xy("Login pakai GitHub", "Sign in with GitHub"),
                    onClick = { githubOpen = true },
                    primary = false,
                    compact = true,
                )
            }
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

    if (githubOpen) {
        XyOverlay(
            title = xy("Login pakai GitHub", "Sign in with GitHub"),
            onDismiss = { if (!githubBusy) githubOpen = false },
        ) {
            if (GITHUB_OAUTH_CLIENT_ID.isNotBlank()) {
                GitHubDeviceFlowPanel(
                    onCancel = { githubOpen = false },
                    onSuccess = { token, user ->
                        val saved = runCatching {
                            id.xydesk.remote.security.CredentialVault(context.applicationContext)
                                .put(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID, token)
                        }.getOrDefault(false)
                        if (saved) {
                            githubUser = user
                            githubOpen = false
                            if (api.displayName() == null) {
                                runCatching { api.setDisplayName(user) }
                                name = api.displayName() ?: user
                            }
                            message = xyNow("Masuk sebagai $user.", "Signed in as $user.")
                        } else {
                            message = xyNow(
                                "Keystore tidak tersedia di perangkat ini.",
                                "Keystore unavailable on this device.",
                            )
                        }
                    },
                    onError = { message = it },
                )
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    xy(
                        "Buat Personal Access Token (scope: read:user) di GitHub, tempel di sini. " +
                            "Token disimpan aman di Keystore perangkat dan hanya dipakai untuk nama profilmu.",
                        "Create a Personal Access Token (scope: read:user) on GitHub and paste it here. " +
                            "The token stays in device Keystore and is only used for your profile name.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                XyPillButton(
                    text = xy("Buka github.com/settings/tokens", "Open github.com/settings/tokens"),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/settings/tokens/new?description=XyDesk+Remote&scope=read:user"),
                                ),
                            )
                        }
                    },
                    primary = false,
                    compact = true,
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = githubPatDraft,
                    onValueChange = { githubPatDraft = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (githubPatDraft.isEmpty()) {
                            Text(
                                xy("Tempel token (ghp_… / github_pat_…)", "Paste token (ghp_… / github_pat_…)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        text = xy("Batal", "Cancel"),
                        onClick = { githubOpen = false },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        text = if (githubBusy) xy("Memeriksa…", "Checking…") else xy("Masuk", "Sign in"),
                        onClick = {
                            scope.launch {
                                githubBusy = true
                                try {
                                    val token = githubPatDraft.trim()
                                    val user = id.xydesk.remote.ui.rdp.XyRdpClient.whoami(token)
                                    if (user == null) {
                                        message = xyNow("Token tidak valid atau scope kurang (read:user).", "Invalid token or missing scope (read:user).")
                                    } else {
                                        val saved = runCatching {
                                            id.xydesk.remote.security.CredentialVault(context.applicationContext)
                                                .put(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID, token)
                                        }.getOrDefault(false)
                                        if (saved) {
                                            githubUser = user
                                            githubOpen = false
                                            githubPatDraft = ""
                                            if (api.displayName() == null) {
                                                runCatching { api.setDisplayName(user) }
                                                name = api.displayName() ?: user
                                            }
                                            message = xyNow("Masuk sebagai $user.", "Signed in as $user.")
                                        } else {
                                            message = xyNow("Keystore tidak tersedia di perangkat ini.", "Keystore unavailable on this device.")
                                        }
                                    }
                                } finally {
                                    githubBusy = false
                                }
                            }
                        },
                        enabled = !githubBusy && githubPatDraft.trim().isNotEmpty(),
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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
private const val GITHUB_OAUTH_CLIENT_ID = ""

private suspend fun githubDeviceStart(clientId: String): org.json.JSONObject? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val body = "client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8") +
                "&scope=read:user"
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

private suspend fun githubDevicePoll(clientId: String, deviceCode: String): org.json.JSONObject? =
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
 * Login GitHub Device Flow: tampilkan kode sekali pakai, buka
 * github.com/login/device, lalu jajak akses sampai user menyetujui.
 */
@Composable
private fun GitHubDeviceFlowPanel(
    onCancel: () -> Unit,
    onSuccess: (token: String, user: String) -> Unit,
    onError: (String) -> Unit,
) {
    var busy by remember { mutableStateOf(true) }
    var userCode by remember { mutableStateOf("") }
    var verifyUri by remember { mutableStateOf("https://github.com/login/device") }
    var statusText by remember { mutableStateOf("") }
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        busy = true
        val start = githubDeviceStart(GITHUB_OAUTH_CLIENT_ID)
        if (start == null) {
            busy = false
            statusText = xyNow(
                "Gagal menghubungi GitHub. Coba lagi.",
                "Could not reach GitHub. Try again.",
            )
            return@LaunchedEffect
        }
        userCode = start.optString("user_code", "")
        verifyUri = start.optString("verification_uri", "https://github.com/login/device")
        val intervalMs = ((start.optInt("interval", 5) + 2).coerceIn(5, 30)) * 1000L
        val deviceCode = start.optString("device_code", "")
        val deadline = System.currentTimeMillis() + start.optInt("expires_in", 900) * 1000L
        busy = false
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(intervalMs)
            val poll = githubDevicePoll(GITHUB_OAUTH_CLIENT_ID, deviceCode) ?: continue
            if (poll.optString("access_token").isNotEmpty()) {
                busy = true
                val token = poll.optString("access_token")
                val user = id.xydesk.remote.ui.rdp.XyRdpClient.whoami(token)
                busy = false
                if (user != null) onSuccess(token, user)
                else {
                    onError(
                        xyNow(
                            "Login gagal: profil tidak terbaca.",
                            "Sign-in failed: profile unreadable.",
                        ),
                    )
                }
                return@LaunchedEffect
            }
            when (poll.optString("error", "")) {
                "expired_token" -> {
                    statusText = xyNow(
                        "Kode kedaluwarsa — buka ulang dialog login.",
                        "Code expired — reopen the sign-in dialog.",
                    )
                    return@LaunchedEffect
                }
                "access_denied" -> {
                    statusText = xyNow(
                        "Login ditolak di GitHub.",
                        "Sign-in denied on GitHub.",
                    )
                    return@LaunchedEffect
                }
                else -> Unit // authorization_pending / slow_down → terus jajak
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            xy(
                "Tanpa tempel token: ketik kode ini di github.com/login/device lalu setujui akses. " +
                    "XyDesk hanya meminta izin baca profil (nama tampilanmu).",
                "No token pasting: type this code at github.com/login/device and approve. " +
                    "XyDesk only asks for read-profile permission (your display name).",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (busy && userCode.isEmpty()) "…" else userCode,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 3.sp,
            )
        }
        XyPillButton(
            text = xy("Buka github.com/login/device", "Open github.com/login/device"),
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(verifyUri),
                        ),
                    )
                }
            },
            primary = false,
            compact = true,
        )
        if (statusText.isNotEmpty()) {
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        XyPillButton(
            text = xy("Tutup", "Close"),
            onClick = onCancel,
            primary = false,
            compact = true,
            enabled = !busy,
        )
    }
}
