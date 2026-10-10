package id.xydesk.remote.ui.rdp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.xy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layar "RDP Gratis": buka kunci (iklan + gabung saluran) → hubungkan akun
 * GitHub pengguna sendiri → dashboard fork XyRDP mereka (trigger sesi 6 jam,
 * baca alamat tunnel/IP tanpa Tailscale) → sambung lewat formulir RDP biasa.
 */
@Composable
internal fun CreateRdpScreen(
    onDismiss: () -> Unit,
    onOpenAddDevice: () -> Unit,
) {
    val context = LocalContext.current
    val unlock = remember { XyRdpUnlock(context) }
    val vault = remember { CredentialVault(context.applicationContext) }

    var step by remember {
        mutableStateOf(if (vault.has(XyRdpConfig.VAULT_PAT_ID)) "dash" else "unlock")
    }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // ---- state dashboard
    var login by remember { mutableStateOf<String?>(null) }
    var run by remember { mutableStateOf<XyRdpRun?>(null) }
    var access by remember { mutableStateOf<XyRdpAccess?>(null) }
    var user by remember { mutableStateOf("runner") }
    var password by remember { mutableStateOf("") }

    // ---- iklan rewarded
    var rewarded by remember { mutableStateOf<RewardedAd?>(null) }
    var adReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        MobileAds.initialize(context) {}
        RewardedAd.load(
            context,
            XyRdpConfig.ADMOB_REWARDED_UNIT,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    adReady = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    adReady = false
                }
            },
        )
    }

    // ---- polling status saat sesi berjalan
    LaunchedEffect(login, step) {
        val token = vault.get(XyRdpConfig.VAULT_PAT_ID) ?: return@LaunchedEffect
        val who = login ?: return@LaunchedEffect
        while (step == "dash") {
            val r = XyRdpClient.latestRun(token, who)
            run = r
            if (r != null && (r.status == "queued" || r.status == "in_progress")) {
                access = XyRdpClient.access(token, who)
                delay(20_000)
            } else {
                access = XyRdpClient.access(token, who)
                delay(60_000)
            }
        }
    }

    fun say(text: String) {
        message = text
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                xy("RDP GRATIS 6 JAM", "FREE 6-HOUR RDP"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            XyPillButton(xy("Tutup", "Close"), onDismiss, primary = false, compact = true)
        }
        Text(
            xy(
                "Buat PC Windows 6 jam lewat GitHub Actions milikmu sendiri: app membantu fork template XyRDP ke akun GitHub-mu, menyalakan workflow, dan membaca alamat sesi. Semua berjalan di akunmu — bukan akun XyDesk.",
                "Spin up a 6-hour Windows PC through your own GitHub Actions: the app helps fork the XyRDP template into your GitHub account, enable the workflow, and read the session address. Everything runs on your account — not XyDesk's.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        message?.let { m ->
            Text(m, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }

        if (step == "unlock") {
            Text(
                xy("BUKA FITUR (sekali saja)", "UNLOCK (one time)"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            XyPillButton(
                if (unlock.adWatched) {
                    xy("Iklan sudah ditonton ✓", "Ad already watched ✓")
                } else if (adReady) {
                    xy("Nonton iklan (1x)", "Watch ad (1x)")
                } else {
                    xy("Memuat iklan…", "Loading ad…")
                },
                {
                    val activity = context as? Activity
                    val ad = rewarded
                    if (activity == null || ad == null) {
                        say(xy("Iklan belum siap; coba sebentar lagi.", "Ad not ready yet; try again shortly."))
                        return@XyPillButton
                    }
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewarded = null
                            adReady = false
                            RewardedAd.load(
                                context,
                                XyRdpConfig.ADMOB_REWARDED_UNIT,
                                AdRequest.Builder().build(),
                                object : RewardedAdLoadCallback() {
                                    override fun onAdLoaded(loaded: RewardedAd) {
                                        rewarded = loaded
                                        adReady = true
                                    }

                                    override fun onAdFailedToLoad(e: LoadAdError) {
                                        adReady = false
                                    }
                                },
                            )
                        }
                    }
                    ad.show(
                        activity,
                        OnUserEarnedRewardListener {
                            unlock.adWatched = true
                            say(xy("Terima kasih! Syarat iklan selesai.", "Thanks! Ad requirement done."))
                        },
                    )
                },
                primary = false,
                compact = true,
                enabled = !unlock.adWatched,
                modifier = Modifier.fillMaxWidth(),
            )
            XyPillButton(
                if (unlock.joined) {
                    xy("Sudah gabung saluran ✓", "Already joined ✓")
                } else {
                    xy("Gabung saluran / grup", "Join channel / group")
                },
                {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(XyRdpConfig.CHANNEL_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    unlock.joined = true
                    say(xy("Anggap join — selamat datang!", "Consider yourself joined — welcome!"))
                },
                primary = false,
                compact = true,
                enabled = !unlock.joined,
                modifier = Modifier.fillMaxWidth(),
            )
            if (unlock.unlocked) {
                XyPillButton(
                    xy("Lanjut: hubungkan akun GitHub", "Continue: link GitHub account"),
                    { step = "dash" },
                    primary = true,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (step == "dash" && login == null) {
            GitHubLoginBlock(
                vault = vault,
                busy = busy,
                onBusy = { busy = it },
                onSay = ::say,
                onLoggedIn = { name ->
                    login = name
                    say(xy("Halo {0}! Fork siap dipakai.", "Hello {0}! Your fork is ready.", name))
                },
            )
        }

        val token = vault.get(XyRdpConfig.VAULT_PAT_ID)
        val who = login
        if (step == "dash" && who != null && token != null) {
            DashboardBlock(
                login = who,
                token = token,
                run = run,
                access = access,
                busy = busy,
                onBusy = { busy = it },
                onSay = ::say,
                user = user,
                onUser = { user = it },
                password = password,
                onPassword = { password = it },
                onCopy = { text ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("XyDesk RDP", text))
                    say(xy("Disalin: {0}", "Copied: {0}", text))
                },
                onOpenAddDevice = onOpenAddDevice,
            )
        }
    }
}

@Composable
private fun GitHubLoginBlock(
    vault: CredentialVault,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onSay: (String) -> Unit,
    onLoggedIn: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pat by remember { mutableStateOf("") }

    Text(
        xy("AKUN GITHUB-MU", "YOUR GITHUB ACCOUNT"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        xy(
            "Buat Personal Access Token (classic) dengan scope repo + workflow di github.com/settings/tokens, lalu tempel di sini. Token disimpan TERENKRIPSI di HP-mu dan hanya dipakai untuk memanggil API GitHub atas nama kamu.",
            "Create a Personal Access Token (classic) with repo + workflow scopes at github.com/settings/tokens, then paste it here. The token is stored ENCRYPTED on your phone and only used to call the GitHub API on your behalf.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    )
    XyField(
        value = pat,
        onValueChange = { pat = it },
        label = xy("Token (ghp_…)", "Token (ghp_…)"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XyPillButton(
            xy("Buat token", "Create token"),
            {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            primary = false,
            compact = true,
            modifier = Modifier.weight(1f),
        )
        XyPillButton(
            xy("Simpan & masuk", "Save & sign in"),
            {
                if (pat.isBlank()) {
                    onSay(xy("Tempel token dulu.", "Paste your token first."))
                    return@XyPillButton
                }
                onBusy(true)
                scope.launch {
                    val name = XyRdpClient.whoami(pat.trim())
                    onBusy(false)
                    if (name == null) {
                        onSay(xy("Token ditolak GitHub. Cek scope repo+workflow.", "GitHub rejected the token. Check repo+workflow scopes."))
                    } else {
                        vault.put(XyRdpConfig.VAULT_PAT_ID, pat.trim())
                        onLoggedIn(name)
                    }
                }
            },
            primary = true,
            compact = true,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DashboardBlock(
    login: String,
    token: String,
    run: XyRdpRun?,
    access: XyRdpAccess?,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onSay: (String) -> Unit,
    user: String,
    onUser: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenAddDevice: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val forkRepo = "$login/${XyRdpConfig.FORK_REPO_NAME}"

    Text(
        xy("DASBOARD {0}", "{0}'S DASHBOARD", login.uppercase()),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        xy(
            "Alamat sesi adalah IP publik tunnel (bore/ngrok) + port — TIDAK butuh Tailscale di HP. RustDesk ditampilkan sebagai cadangan.",
            "The session address is a public tunnel IP (bore/ngrok) + port — no Tailscale needed on the phone. RustDesk is shown as a fallback.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    )

    run?.let { r ->
        Text(
            xy(
                "Run #{0}: {1}{2}",
                "Run #{0}: {1}{2}",
                r.number,
                r.status,
                r.conclusion?.let { " → $it" }.orEmpty(),
            ),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
        )
    }

    access?.let { a ->
        if (a.tunnelAddress.isNotBlank() && a.tunnelPort > 0) {
            Text(
                xy("Alamat sesi (IP, tanpa Tailscale): {0}", "Session address (IP, no Tailscale): {0}", a.tunnelAddress),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val health = listOfNotNull(
                a.selftest.takeIf { it.isNotBlank() }?.let { "selftest=$it" },
                a.outside.takeIf { it.isNotBlank() }?.let { "luar=$it" },
            ).joinToString(" · ")
            if (health.isNotEmpty()) {
                Text(health, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            XyPillButton(
                xy("Salin host:port", "Copy host:port"),
                { onCopy(a.tunnelAddress) },
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (a.tailscaleIp.isNotBlank()) {
            Text(
                xy("Tunnel belum siap; Tailscale: {0}:3389", "Tunnel not ready; Tailscale: {0}:3389", a.tailscaleIp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        if (a.rustdeskId.isNotBlank()) {
            Text(
                xy("Cadangan RustDesk ID: {0}", "RustDesk ID fallback: {0}", a.rustdeskId),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XyPillButton(
            xy("Siapkan fork", "Prepare fork"),
            {
                onBusy(true)
                scope.launch {
                    val exists = XyRdpClient.forkExists(token, login)
                    if (!exists && !XyRdpClient.createFork(token)) {
                        onBusy(false)
                        onSay(xy("Gagal membuat fork. Cek token/scope.", "Could not create the fork. Check token/scopes."))
                        return@launch
                    }
                    val enabled = XyRdpClient.enableWorkflow(token, login)
                    onBusy(false)
                    onSay(
                        if (enabled) {
                            xy("Fork siap & workflow aktif.", "Fork ready & workflow enabled.")
                        } else {
                            xy(
                                "Fork ada, tapi workflow belum aktif — buka tab Actions di fork-mu sekali lalu 'Enable'.",
                                "Fork exists but workflow is not enabled — open the Actions tab on your fork once and tap 'Enable'.",
                            )
                        },
                    )
                }
            },
            primary = false,
            compact = true,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
        XyPillButton(
            xy("Mulai sesi 6 jam", "Start 6-hour session"),
            {
                onBusy(true)
                scope.launch {
                    val ok = XyRdpClient.dispatch(token, login)
                    onBusy(false)
                    onSay(
                        if (ok) {
                            xy("Sesi dimulai! Status muncul otomatis di sini (±2-4 menit).", "Session started! Status appears here automatically (±2-4 min).")
                        } else {
                            xy("Gagal trigger workflow — pastikan workflow aktif & set secret RDP_PASSWORD dulu.", "Failed to trigger the workflow — make sure it is enabled and set the RDP_PASSWORD secret first.")
                        },
                    )
                }
            },
            primary = true,
            compact = true,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
    }
    XyPillButton(
        xy("Set secret RDP_PASSWORD", "Set RDP_PASSWORD secret"),
        {
            onSay(xy("Buka halaman secrets fork-mu, tambah RDP_PASSWORD (min 8 karakter).", "Open your fork's secrets page and add RDP_PASSWORD (min 8 chars)."))
        },
        primary = false,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        xy("SAMBUNG LEWAT XYDESK", "CONNECT VIA XYDESK"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
    )
    XyField(
        value = user,
        onValueChange = onUser,
        label = xy("Username Windows", "Windows username"),
    )
    XyPillButton(
        xy("Buka formulir koneksi", "Open connection form"),
        onOpenAddDevice,
        primary = false,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        xy(
            "Isi Host & Port dari alamat sesi di atas, username '{0}', dan password = secret RDP_PASSWORD-mu. Layar hitam sesaat wajar saat VM menyiapkan desktop; bila bertahan, pakai jalur RustDesk.",
            "Fill Host & Port from the session address above, username '{0}', and password = your RDP_PASSWORD secret. A brief black screen is normal while the VM prepares the desktop; if it persists, use the RustDesk path.",
            user,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    )
}
