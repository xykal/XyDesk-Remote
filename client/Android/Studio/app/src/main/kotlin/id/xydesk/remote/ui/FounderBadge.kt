package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.rdp.XyRdpClient
import id.xydesk.remote.ui.rdp.XyRdpConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ronde 54 — Sistem Identitas, Emblem Beranimasi Epik & Gaya Bubble Khusus Founder (`xykal`),
 * serta Helper Login GitHub 1-Klik via In-App Browser (Chrome Custom Tabs dengan menu `⋮`).
 */

private const val FOUNDER_PREFS = "xydesk.founder.identity"
private const val KEY_GH_LOGIN = "verified_github_login"
private const val KEY_GH_AVATAR = "verified_github_avatar"

internal val FOUNDER_GITHUB_LOGINS = setOf("xykal")

internal fun isFounderAccount(nameOrLogin: String?): Boolean {
    if (nameOrLogin.isNullOrBlank()) return false
    val normalized = nameOrLogin.trim().removePrefix("@").lowercase(Locale.ROOT)
    return normalized in FOUNDER_GITHUB_LOGINS ||
        normalized == "xykal (founder)" ||
        normalized.startsWith("xykal ")
}

internal fun saveVerifiedGitHubLogin(context: Context, login: String?, avatarUrl: String? = null) {
    val clean = login?.trim().orEmpty()
    val appCtx = context.applicationContext
    val resolvedAvatar = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (clean.isNotEmpty()) "https://github.com/$clean.png?size=160" else null
    val edit = appCtx.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_GH_LOGIN, clean)
    if (resolvedAvatar != null) {
        edit.putString(KEY_GH_AVATAR, resolvedAvatar)
    }
    edit.apply()

    if (clean.isNotEmpty()) {
        val chatPrefs = appCtx.getSharedPreferences("xydesk.global.chat", Context.MODE_PRIVATE)
        val currentNick = chatPrefs.getString("nickname", null)
        if (currentNick.isNullOrBlank() || currentNick.startsWith("Tamu-") || isFounderAccount(clean)) {
            chatPrefs.edit().putString("nickname", clean).apply()
        }
    }
}

internal fun getVerifiedGitHubLogin(context: Context): String? {
    val stored = context.applicationContext
        .getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_GH_LOGIN, null)
        ?.trim()
    return stored?.takeIf { it.isNotEmpty() }
}

internal fun getVerifiedGitHubAvatar(context: Context): String? {
    val stored = context.applicationContext
        .getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_GH_AVATAR, null)
        ?.trim()
    if (!stored.isNullOrEmpty()) return stored
    val login = getVerifiedGitHubLogin(context) ?: return null
    return "https://github.com/$login.png?size=160"
}

internal fun getVerifiedGitHubAvatarUrl(context: Context): String? = getVerifiedGitHubAvatar(context)

/**
 * Membuka URL di dalam In-App Browser (Chrome Custom Tabs) yang memiliki tombol tutup `X`
 * dan menu titik tiga `⋮` di pojok kanan atas serta berbagi sesi login dengan browser utama.
 */
internal fun openInAppCustomTab(context: Context, url: String) {
    runCatching {
        val pm = context.packageManager
        val preferredBrowsers = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
        )
        val chosenPackage = preferredBrowsers.firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            val extras = Bundle().apply {
                putBinder("android.support.customtabs.extra.SESSION", null)
                putInt("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF09090B.toInt())
                putInt("android.support.customtabs.extra.SECONDARY_TOOLBAR_COLOR", 0xFF121216.toInt())
                putInt("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
                putInt("android.support.customtabs.extra.SHARE_STATE", 1)
                putBoolean("android.support.customtabs.extra.ENABLE_URLBAR_HIDING", false)
            }
            putExtras(extras)
            if (chosenPackage != null) {
                setPackage(chosenPackage)
            }
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}

internal object GitHubDirectOAuthController {
    var isOpen by mutableStateOf(false)
        private set
    var activeSessionId by mutableStateOf("")
        private set
    var authRevision by mutableStateOf(0)
        private set
    private var onSuccessCallback: ((token: String, login: String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    fun open(
        onSuccess: (token: String, login: String) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        onSuccessCallback = onSuccess
        onErrorCallback = onError
        activeSessionId = java.util.UUID.randomUUID().toString().replace("-", "").take(22)
        isOpen = true
    }

    fun dismiss(reason: String? = null) {
        isOpen = false
        val errCb = onErrorCallback
        onSuccessCallback = null
        onErrorCallback = null
        if (!reason.isNullOrBlank()) {
            errCb?.invoke(reason)
        }
    }

    fun complete(token: String, login: String) {
        isOpen = false
        authRevision += 1
        val okCb = onSuccessCallback
        onSuccessCallback = null
        onErrorCallback = null
        okCb?.invoke(token, login)
    }

    suspend fun onCodeReceived(
        context: Context,
        code: String,
        state: String = "",
    ) {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val sid = activeSessionId
                val u = "https://api.xydeskremote.biz.id/api/github/oauth/exchange" +
                    "?code=" + java.net.URLEncoder.encode(cleanCode, "UTF-8") +
                    "&state=" + java.net.URLEncoder.encode(state, "UTF-8") +
                    "&session_id=" + java.net.URLEncoder.encode(sid, "UTF-8") +
                    "&format=json"
                val conn = (URL(u).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
                    )
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                try {
                    if (conn.responseCode in 200..299) {
                        JSONObject(conn.inputStream.bufferedReader().readText())
                    } else null
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        } ?: return
        val tok = res.optString("token").trim()
        val lgn = res.optString("login").trim()
        val nm = res.optString("name", lgn).trim()
        val av = res.optString("avatar_url").trim()
        if (tok.isNotEmpty() && lgn.isNotEmpty()) {
            onDeepLinkReceived(
                context = context,
                token = tok,
                login = lgn,
                displayName = nm,
                avatarUrl = av,
            )
        }
    }

    suspend fun onDeepLinkReceived(
        context: Context,
        token: String,
        login: String,
        displayName: String = "",
        avatarUrl: String = "",
    ) {
        val appCtx = context.applicationContext
        val resolvedAvatar = avatarUrl.ifBlank { "https://github.com/$login.png?size=160" }
        withContext(Dispatchers.IO) {
            runCatching {
                CredentialVault(appCtx).put(XyRdpConfig.VAULT_PAT_ID, token)
                saveVerifiedGitHubLogin(appCtx, login, resolvedAvatar)
                val api = CommunityJokesApi(appCtx)
                val targetName = displayName.ifBlank { login }
                if (api.displayName().isNullOrBlank() || isFounderAccount(login)) {
                    api.setDisplayName(targetName)
                }
                syncGitHubAvatarIfNeeded(appCtx, login, api)
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                appCtx,
                xyNow("Berhasil terhubung ke GitHub sebagai @$login!", "Connected to GitHub as @$login!"),
                Toast.LENGTH_SHORT,
            ).show()
            complete(token, login)
        }
    }
}

/**
 * Ronde 57 — Alur Login GitHub Sinkron dengan Chrome / Browser Utama (Chrome Custom Tabs):
 * Membuka langsung halaman OAuth GitHub di Chrome Custom Tabs yang berbagi sesi login
 * GitHub & Google dengan browser Chrome pengguna (tanpa kode 8 digit, mendukung tombol
 * "Continue with Google" & sesi GitHub yang sudah login di Chrome), lalu otomatis kembali
 * ke XyDesk Remote begitu tombol hijau "Authorize" diklik.
 */
internal suspend fun performGitHubInAppBrowserLogin(
    context: Context,
    scope: String = "repo workflow read:user",
    onCodeReady: (userCode: String, verifyUrl: String) -> Unit = { _, _ -> },
    onSuccess: (token: String, login: String) -> Unit = { _, _ -> },
    onError: (String) -> Unit = {},
) {
    withContext(Dispatchers.Main) {
        GitHubDirectOAuthController.open(
            onSuccess = onSuccess,
            onError = onError,
        )
    }
}

@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GitHubDirectOAuthModalHost() {
    if (!GitHubDirectOAuthController.isOpen) return
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val scope = rememberCoroutineScope()
    val sessionId = GitHubDirectOAuthController.activeSessionId.ifBlank {
        remember { java.util.UUID.randomUUID().toString().replace("-", "").take(22) }
    }
    val chromeAuthUrl = remember(sessionId) {
        "https://api.xydeskremote.biz.id/api/github/oauth/login?apk=1&session_id=$sessionId"
    }

    var currentUrl by remember(chromeAuthUrl) { mutableStateOf(chromeAuthUrl) }
    var statusLabel by remember {
        mutableStateOf(
            xyNow(
                "Membuka Chrome (Sinkron Sesi GitHub & Google) — klik tombol hijau 'Authorize'",
                "Opening Chrome (Synced GitHub & Google Session) — tap the green 'Authorize' button",
            ),
        )
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var hiddenHandshakeCode by remember { mutableStateOf("") }
    var useWebRedirectMode by remember { mutableStateOf(true) }
    var showEmbeddedWebView by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    fun buildSeamlessOAuthBridgeScript(code: String): String {
        val safe = code.replace("'", "")
        return """
            (function() {
              try {
                var path = window.location.pathname || '';
                if (path === '/login/device' || path === '/login/device/') {
                  if (!document.getElementById('xy-seamless-oauth-style')) {
                    var st = document.createElement('style');
                    st.id = 'xy-seamless-oauth-style';
                    st.innerHTML = 'input[id^="user-code"], .js-user-code-field, input[name*="user_code"] { opacity: 0.01 !important; }';
                    document.head.appendChild(st);
                  }
                  var code = '$safe';
                  var clean = code.replace(/[^A-Za-z0-9]/g, '');
                  if (!clean || window.__xyAutoAuthDone === clean) return 'WAIT_REDIRECT';
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
                    window.__xyAutoAuthDone = clean;
                    var btn = document.querySelector('form button[type="submit"], form input[type="submit"], button.btn-primary');
                    if (btn && !btn.disabled) {
                      btn.click();
                    } else if (boxes[0] && boxes[0].form) {
                      boxes[0].form.submit();
                    }
                    return 'AUTO_SUBMITTED';
                  }
                  var single = document.querySelector('input[name="user_code"], input#user_code');
                  if (single) {
                    single.value = code;
                    single.dispatchEvent(new Event('input', { bubbles: true }));
                    window.__xyAutoAuthDone = clean;
                    var sBtn = document.querySelector('form button[type="submit"], form input[type="submit"], button.btn-primary');
                    if (sBtn) sBtn.click();
                    else if (single.form) single.form.submit();
                    return 'AUTO_SUBMITTED_SINGLE';
                  }
                }
              } catch (e) {}
              return 'IDLE';
            })();
        """.trimIndent()
    }

    suspend fun finishLoginSuccess(token: String, login: String, displayName: String = "", avatarUrl: String = "") {
        if (completed) return
        completed = true
        val resolvedAvatar = avatarUrl.ifBlank { "https://github.com/$login.png?size=160" }
        withContext(Dispatchers.IO) {
            runCatching {
                CredentialVault(appCtx).put(XyRdpConfig.VAULT_PAT_ID, token)
                saveVerifiedGitHubLogin(appCtx, login, resolvedAvatar)
                val api = CommunityJokesApi(appCtx)
                val targetName = displayName.ifBlank { login }
                if (api.displayName().isNullOrBlank() || isFounderAccount(login)) {
                    api.setDisplayName(targetName)
                }
                syncGitHubAvatarIfNeeded(appCtx, login, api)
            }
        }
        Toast.makeText(
            appCtx,
            xyNow("Berhasil terhubung ke GitHub sebagai @$login!", "Connected to GitHub as @$login!"),
            Toast.LENGTH_SHORT,
        ).show()
        GitHubDirectOAuthController.complete(token, login)
    }

    LaunchedEffect(sessionId) {
        openInAppCustomTab(context, chromeAuthUrl)
        val deadline = System.currentTimeMillis() + 10 * 60 * 1000L
        val pollUrls = listOf(
            "https://api.xydeskremote.biz.id/api/github/oauth/poll?session_id=$sessionId",
            "https://xyrdp-dash.vercel.app/api/auth/apk-poll?session_id=$sessionId",
        )
        while (isActive && !completed && System.currentTimeMillis() < deadline) {
            delay(1_200L)
            val hit = withContext(Dispatchers.IO) {
                var found: JSONObject? = null
                for (u in pollUrls) {
                    val res = runCatching {
                        val conn = (URL(u).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
                            )
                            connectTimeout = 5_000
                            readTimeout = 5_000
                        }
                        if (conn.responseCode == 200) {
                            val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            if (obj.optBoolean("ready", false) && obj.optString("token", "").isNotBlank()) obj else null
                        } else null
                    }.getOrNull()
                    if (res != null) {
                        found = res
                        break
                    }
                }
                found
            }
            if (hit != null) {
                val tok = hit.optString("token", "").trim()
                val lgn = hit.optString("login", "").trim()
                val nm = hit.optString("name", "").trim()
                val av = hit.optString("avatar_url", "").trim()
                if (tok.isNotEmpty() && lgn.isNotEmpty()) {
                    finishLoginSuccess(tok, lgn, nm, av)
                    return@LaunchedEffect
                }
            }
        }
    }

    LaunchedEffect(showEmbeddedWebView, useWebRedirectMode) {
        if (!showEmbeddedWebView) return@LaunchedEffect
        if (useWebRedirectMode) {
            statusLabel = xyNow(
                "OAuth Web GitHub: Login & klik tombol hijau 'Authorize'",
                "GitHub Web OAuth: Sign in & tap the green 'Authorize' button",
            )
            webViewRef?.loadUrl(chromeAuthUrl)
            return@LaunchedEffect
        }
        val start = githubDeviceStart(GITHUB_OAUTH_CLIENT_ID)
        if (start == null) {
            useWebRedirectMode = true
            return@LaunchedEffect
        }
        val code = start.optString("user_code", "")
        val deviceCode = start.optString("device_code", "")
        val intervalMs = (start.optInt("interval", 5).coerceIn(3, 12)) * 1000L
        val deadline = System.currentTimeMillis() + start.optInt("expires_in", 900) * 1000L
        hiddenHandshakeCode = code
        webViewRef?.evaluateJavascript(buildSeamlessOAuthBridgeScript(code), null)

        while (isActive && !completed && System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            if (hiddenHandshakeCode.isNotBlank()) {
                webViewRef?.evaluateJavascript(buildSeamlessOAuthBridgeScript(hiddenHandshakeCode), null)
            }
            val poll = githubDevicePoll(GITHUB_OAUTH_CLIENT_ID, deviceCode) ?: continue
            val tok = poll.optString("access_token", "")
            val err = poll.optString("error", "")
            if (tok.isNotBlank()) {
                statusLabel = xyNow("Menyinkronkan profil, repo & workflow GitHub…", "Syncing GitHub profile, repo & workflow…")
                val user = syncGitHubProfileAndAvatar(appCtx, tok) ?: XyRdpClient.whoami(tok)
                if (!user.isNullOrBlank()) {
                    finishLoginSuccess(tok, user)
                    return@LaunchedEffect
                }
            } else if (err == "access_denied") {
                GitHubDirectOAuthController.dismiss(
                    xyNow("Otorisasi GitHub dibatalkan.", "GitHub authorization cancelled."),
                )
                return@LaunchedEffect
            }
        }
    }

    if (!showEmbeddedWebView) {
        Dialog(
            onDismissRequest = { GitHubDirectOAuthController.dismiss() },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0E0E14),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                tonalElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GitHubLogoIcon(size = 20.dp, tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = xyNow("Sinkron Login GitHub via Chrome", "Sync GitHub Sign-In via Chrome"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                text = xyNow("Otomatis memakai sesi GitHub & Google di browser Anda", "Uses your existing GitHub & Google session in Chrome"),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA1A1AA),
                            )
                        }
                    }

                    Text(
                        text = xyNow(
                            "Halaman OAuth GitHub telah dibuka di Chrome Custom Tabs. Jika Anda sudah login GitHub atau Google di Chrome, cukup klik tombol hijau 'Authorize' — aplikasi akan otomatis tersambung tanpa mengetik kode.",
                            "GitHub OAuth opened in Chrome Custom Tabs. If you're already signed in to GitHub or Google in Chrome, just tap the green 'Authorize' button — the app connects automatically with no code required.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD4D4D8),
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable { openInAppCustomTab(context, chromeAuthUrl) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GitHubLogoIcon(size = 16.dp, tint = Color(0xFF09090B))
                            Text(
                                text = xyNow("Buka Chrome / Browser (Authorize)", "Open Chrome / Browser (Authorize)"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF09090B),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .clickable { showEmbeddedWebView = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = xyNow("Browser Internal", "Built-in Browser"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE4E4E7),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable { GitHubDirectOAuthController.dismiss() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = xyNow("Tutup", "Close"),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFA1A1AA),
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Dialog(
        onDismissRequest = {
            GitHubDirectOAuthController.dismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 18.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF09090B),
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Browser In-App (Tombol Tutup X + URL + Reload + Menu Titik Tiga ⋮)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121216))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { GitHubDirectOAuthController.dismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = xyNow("GitHub OAuth Authorize", "GitHub OAuth Authorize"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color(0xFFA1A1AA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { menuExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⋮", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(xyNow("Muat Ulang Halaman", "Reload Page")) },
                                onClick = {
                                    menuExpanded = false
                                    webViewRef?.reload()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (useWebRedirectMode) xyNow("Mode OAuth Standar (Full Workflow)", "Standard OAuth Mode (Full Workflow)")
                                        else xyNow("Mode OAuth Web Redirect", "Web Redirect OAuth Mode"),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    useWebRedirectMode = !useWebRedirectMode
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(xyNow("Buka di Browser Eksternal ↗", "Open in External Browser ↗")) },
                                onClick = {
                                    menuExpanded = false
                                    openInAppCustomTab(context, currentUrl)
                                },
                            )
                        }
                    }
                }

                // Banner instruksi singkat (tanpa kode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18181F))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GitHubLogoIcon(size = 16.dp, tint = Color.White)
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE4E4E7),
                        modifier = Modifier.weight(1f),
                    )
                }

                // WebView OAuth Authorize
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = settings.userAgentString + " XyDeskRemote/1.1.20"
                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                ): Boolean {
                                    val u = request?.url?.toString().orEmpty()
                                    if (u.startsWith("xydesk://github-auth") || u.startsWith("intent://github-auth")) {
                                        val parsed = Uri.parse(u.replaceFirst("intent://", "xydesk://"))
                                        val tok = parsed.getQueryParameter("token").orEmpty()
                                        val lgn = parsed.getQueryParameter("login").orEmpty()
                                        val nm = parsed.getQueryParameter("name").orEmpty()
                                        val av = parsed.getQueryParameter("avatar").orEmpty()
                                        val cd = parsed.getQueryParameter("code").orEmpty()
                                        val st = parsed.getQueryParameter("state").orEmpty()
                                        if (tok.isNotBlank() && lgn.isNotBlank()) {
                                            scope.launch { finishLoginSuccess(tok, lgn, nm, av) }
                                        } else if (cd.isNotBlank()) {
                                            scope.launch {
                                                GitHubDirectOAuthController.onCodeReceived(context, cd, st)
                                            }
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(
                                    view: android.webkit.WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    if (hiddenHandshakeCode.isNotBlank()) {
                                        view?.evaluateJavascript(buildSeamlessOAuthBridgeScript(hiddenHandshakeCode), null)
                                    }
                                }

                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val u = url.orEmpty()
                                    if (u.isNotBlank()) currentUrl = u
                                    if (hiddenHandshakeCode.isNotBlank()) {
                                        view?.evaluateJavascript(buildSeamlessOAuthBridgeScript(hiddenHandshakeCode), null)
                                        view?.postDelayed({
                                            view.evaluateJavascript(buildSeamlessOAuthBridgeScript(hiddenHandshakeCode), null)
                                        }, 180L)
                                    }
                                    // Deteksi callback sukses pada Mode OAuth Web Redirect (xyrdp-dash.vercel.app)
                                    if (u.startsWith("https://xyrdp-dash.vercel.app") && !u.contains("/api/auth/")) {
                                        val cookies = android.webkit.CookieManager.getInstance()
                                            .getCookie("https://xyrdp-dash.vercel.app").orEmpty()
                                        if (cookies.contains("ghs=")) {
                                            scope.launch(Dispatchers.IO) {
                                                runCatching {
                                                    val conn = (URL("https://xyrdp-dash.vercel.app/api/me").openConnection() as HttpURLConnection).apply {
                                                        requestMethod = "GET"
                                                        setRequestProperty("Cookie", cookies)
                                                        setRequestProperty("Accept", "application/json")
                                                        connectTimeout = 10_000
                                                        readTimeout = 10_000
                                                    }
                                                    if (conn.responseCode == 200) {
                                                        val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                                                        val lgn = obj.optString("login", "").trim()
                                                        val nm = obj.optString("name", "").trim()
                                                        val av = obj.optString("avatar", "").trim()
                                                        if (lgn.isNotEmpty()) {
                                                            withContext(Dispatchers.Main) {
                                                                finishLoginSuccess("ghs_cookie:$cookies", lgn, nm, av)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            loadUrl("https://github.com/login/device")
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        webViewRef = view
                        if (hiddenHandshakeCode.isNotBlank()) {
                            view.evaluateJavascript(buildSeamlessOAuthBridgeScript(hiddenHandshakeCode), null)
                        }
                    },
                )
            }
        }
    }
}

private suspend fun syncGitHubAvatarIfNeeded(context: Context, githubLogin: String, api: CommunityJokesApi) {
    withContext(Dispatchers.IO) {
        runCatching {
            val avatarFile = File(context.filesDir, "profile_avatar.webp")
            if (avatarFile.exists() && avatarFile.length() > 512L) return@runCatching
            val conn = (URL("https://github.com/$githubLogin.png?size=200").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching
                val out = java.io.ByteArrayOutputStream()
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    android.graphics.Bitmap.CompressFormat.WEBP
                }
                bmp.compress(fmt, 88, out)
                val webpBytes = out.toByteArray()
                if (webpBytes.isNotEmpty() && webpBytes.size <= 200_000) {
                    avatarFile.writeBytes(webpBytes)
                    runCatching { api.uploadAvatar(webpBytes, "image/webp") }
                }
            }
        }
    }
}

internal fun founderBorderBrush(phase: Float = 0f): Brush {
    val shift = phase * 360f
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFFAF5FF),
            Color(0xFFE9D5FF),
            Color(0xFFC084FC),
            Color(0xFFA855F7),
            Color(0xFF7E22CE),
            Color(0xFFD8B4FE),
            Color(0xFFFAF5FF),
        ),
        start = Offset(shift, 0f),
        end = Offset(shift + 240f, 180f),
    )
}

internal fun founderBgBrush(phase: Float = 0f): Brush {
    val offset = phase * 220f
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF16062B),
            Color(0xFF2E1065),
            Color(0xFF3B0764),
            Color(0xFF4C1D95),
            Color(0xFF190733),
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + 280f, 120f),
    )
}

/**
 * Menghasilkan Shape kustom untuk bubble chat berdasarkan pilihan gaya shape:
 * - "rounded"  : Klasik membulat asimetris pada ekor bubble
 * - "sharp"    : Ujung tajam/lancip tegas (Cyber Blade)
 * - "morph"    : Sudut organik yang terus bertransformasi halus (Liquid Morph)
 * - "abstract" : Potongan poligon asimetris futuristik (HUD / Mecha Cut)
 * - "cloud"    : Lekukan lembut organik (Soft Pill / Cloud Sculpt)
 */
internal fun resolveCustomBubbleShape(
    shapeStyle: String = "",
    isOwn: Boolean = false,
    shapePreset: String = "",
    isMine: Boolean = false,
    morphPhase: Float = 0f,
    compact: Boolean = false,
): Shape {
    val activeStyle = shapePreset.ifBlank { shapeStyle }
    val own = isOwn || isMine
    val baseRadiusDp = if (compact) 13f else 17f
    return when (activeStyle.lowercase(Locale.ROOT)) {
        "sharp" -> androidx.compose.foundation.shape.GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val cut = (minOf(w, h) * 0.18f).coerceIn(6f, 18f)
            if (own) {
                moveTo(cut, 0f)
                lineTo(w, 0f)
                lineTo(w, h - cut * 0.35f)
                lineTo(w - cut * 0.9f, h)
                lineTo(0f, h)
                lineTo(0f, cut)
                close()
            } else {
                moveTo(0f, 0f)
                lineTo(w - cut, 0f)
                lineTo(w, cut)
                lineTo(w, h)
                lineTo(cut * 0.9f, h)
                lineTo(0f, h - cut * 0.35f)
                close()
            }
        }
        "abstract" -> androidx.compose.foundation.shape.GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val c1 = (minOf(w, h) * 0.22f).coerceIn(8f, 22f)
            val c2 = (minOf(w, h) * 0.10f).coerceIn(4f, 12f)
            if (own) {
                moveTo(c1, 0f)
                lineTo(w - c2, 0f)
                lineTo(w, c2)
                lineTo(w, h)
                lineTo(c2, h)
                lineTo(0f, h - c1)
                close()
            } else {
                moveTo(c2, 0f)
                lineTo(w - c1, 0f)
                lineTo(w, c1)
                lineTo(w, h - c2)
                lineTo(w - c2, h)
                lineTo(0f, h)
                close()
            }
        }
        "morph" -> {
            val wave1 = (sin(morphPhase * 2f * PI.toFloat()) * 6f)
            val wave2 = (cos(morphPhase * 2f * PI.toFloat()) * 6f)
            val tl = (baseRadiusDp + wave1).coerceIn(6f, 26f).dp
            val tr = (baseRadiusDp - wave2).coerceIn(6f, 26f).dp
            val bl = if (own) (baseRadiusDp + wave2).coerceIn(6f, 26f).dp else 4.dp
            val br = if (own) 4.dp else (baseRadiusDp - wave1).coerceIn(6f, 26f).dp
            RoundedCornerShape(topStart = tl, topEnd = tr, bottomEnd = br, bottomStart = bl)
        }
        "cloud" -> {
            val r = (baseRadiusDp + 7f).dp
            val tail = if (compact) 6.dp else 8.dp
            RoundedCornerShape(
                topStart = r,
                topEnd = r,
                bottomEnd = if (own) tail else r,
                bottomStart = if (own) r else tail,
            )
        }
        else -> {
            val r = baseRadiusDp.dp
            val tail = if (compact) 4.dp else 5.dp
            RoundedCornerShape(
                topStart = r,
                topEnd = r,
                bottomEnd = if (own) tail else r,
                bottomStart = if (own) r else tail,
            )
        }
    }
}

/**
 * Menggambar elemen-elemen melayang beranimasi (Floating Animated Elements) di dalam bubble chat.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBubbleFloatingElements(
    elementStyle: String = "",
    preset: String = "",
    accentColor: Color = Color(0xFFD8B4FE),
    secondaryColor: Color = Color(0xFFA855F7),
    phase: Float = 0f,
) {
    val rawStyle = preset.ifBlank { elementStyle }.lowercase(Locale.ROOT)
    val style = when (rawStyle) {
        "sparkles" -> "crystals"
        "plasma" -> "orbs"
        else -> rawStyle
    }
    if (style == "none" || style.isBlank()) return
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f) return

    val seeds = listOf(
        Triple(0.14f, 0.24f, 0.85f),
        Triple(0.34f, 0.72f, 1.10f),
        Triple(0.56f, 0.20f, 0.75f),
        Triple(0.76f, 0.64f, 1.25f),
        Triple(0.88f, 0.28f, 0.95f),
        Triple(0.24f, 0.48f, 0.65f),
    )

    seeds.forEachIndexed { idx, (bx, by, scale) ->
        val angle = (phase * 2f * PI.toFloat()) + idx * 1.15f
        val dx = cos(angle) * (6.dp.toPx() * scale)
        val dy = sin(angle * 1.3f) * (5.dp.toPx() * scale)
        val cx = (w * bx + dx).coerceIn(4f, w - 4f)
        val cy = (h * by + dy).coerceIn(4f, h - 4f)
        val pulse = (0.45f + 0.55f * ((sin(angle) + 1f) * 0.5f)).coerceIn(0.2f, 1f)
        val tint = if (idx % 2 == 0) accentColor else secondaryColor

        when (style) {
            "crystals" -> {
                val r = 4.2.dp.toPx() * scale
                val diamond = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.68f, cy)
                    lineTo(cx, cy + r)
                    lineTo(cx - r * 0.68f, cy)
                    close()
                }
                drawPath(path = diamond, color = tint.copy(alpha = 0.28f * pulse))
                drawPath(
                    path = diamond,
                    color = Color.White.copy(alpha = 0.34f * pulse),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            }
            "orbs" -> {
                val r = 7.dp.toPx() * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.36f * pulse),
                            tint.copy(alpha = 0.10f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f * pulse),
                    radius = 1.4.dp.toPx() * scale,
                    center = Offset(cx, cy),
                )
            }
            "stars" -> {
                val r = 4.5.dp.toPx() * scale * pulse
                val star = Path().apply {
                    moveTo(cx, cy - r)
                    quadraticTo(cx, cy, cx + r, cy)
                    quadraticTo(cx, cy, cx, cy + r)
                    quadraticTo(cx, cy, cx - r, cy)
                    quadraticTo(cx, cy, cx, cy - r)
                    close()
                }
                drawPath(path = star, color = tint.copy(alpha = 0.48f * pulse))
                drawCircle(color = Color.White.copy(alpha = 0.65f * pulse), radius = 1.dp.toPx(), center = Offset(cx, cy))
            }
            "cyber" -> {
                val s = 4.5.dp.toPx() * scale
                drawRoundRect(
                    color = tint.copy(alpha = 0.32f * pulse),
                    topLeft = Offset(cx - s * 0.5f, cy - s * 0.5f),
                    size = androidx.compose.ui.geometry.Size(s, s),
                    cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            "sakura" -> {
                val r = 3.8.dp.toPx() * scale
                val petal = Path().apply {
                    moveTo(cx, cy - r)
                    quadraticTo(cx + r, cy - r * 0.3f, cx, cy + r)
                    quadraticTo(cx - r, cy - r * 0.3f, cx, cy - r)
                    close()
                }
                drawPath(path = petal, color = tint.copy(alpha = 0.34f * pulse))
            }
            "fireflies" -> {
                val r = 5.2.dp.toPx() * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFEF08A).copy(alpha = 0.45f * pulse),
                            tint.copy(alpha = 0.18f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/**
 * Emblem Mahkota Bersayap Sovereign Royal Amethyst (Sovereign Winged Crown + Orbiting Amethyst Sparks)
 * yang beranimasi secara real-time di atas Canvas.
 */
@Composable
internal fun FounderCrownEmblem(
    size: Dp = 15.dp,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "founder_crown_anim")
    val pulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "crown_pulse",
    )
    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "crown_orbit",
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // Halo aura ungu royal amethyst berdenyut di belakang mahkota
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFC084FC).copy(alpha = 0.48f * pulse.coerceIn(0.8f, 1.15f)),
                    Color(0xFF7E22CE).copy(alpha = 0.22f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = w * 0.54f * pulse,
            ),
            radius = w * 0.54f * pulse,
            center = Offset(cx, cy),
        )

        val amethystBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFAF5FF),
                Color(0xFFE9D5FF),
                Color(0xFFC084FC),
                Color(0xFF9333EA),
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )

        // Sayap kiri & kanan tipis kerajaan
        val leftWing = Path().apply {
            moveTo(w * 0.22f, h * 0.68f)
            lineTo(w * 0.03f, h * 0.42f)
            lineTo(w * 0.18f, h * 0.48f)
            lineTo(w * 0.07f, h * 0.24f)
            lineTo(w * 0.28f, h * 0.44f)
            close()
        }
        val rightWing = Path().apply {
            moveTo(w * 0.78f, h * 0.68f)
            lineTo(w * 0.97f, h * 0.42f)
            lineTo(w * 0.82f, h * 0.48f)
            lineTo(w * 0.93f, h * 0.24f)
            lineTo(w * 0.72f, h * 0.44f)
            close()
        }
        drawPath(path = leftWing, brush = amethystBrush, style = Fill)
        drawPath(path = rightWing, brush = amethystBrush, style = Fill)

        // Mahkota utama 5-puncak Sovereign
        val crown = Path().apply {
            moveTo(w * 0.18f, h * 0.76f)
            lineTo(w * 0.13f, h * 0.32f)
            lineTo(w * 0.34f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.66f, h * 0.52f)
            lineTo(w * 0.87f, h * 0.32f)
            lineTo(w * 0.82f, h * 0.76f)
            close()
        }
        drawPath(path = crown, brush = amethystBrush, style = Fill)
        drawPath(
            path = crown,
            color = Color(0xFFFAF5FF).copy(alpha = 0.88f),
            style = Stroke(width = w * 0.045f, join = StrokeJoin.Round),
        )

        // Permata Amethyst Ungu Neon di tengah mahkota
        drawCircle(
            color = Color(0xFF6B21A8),
            radius = w * 0.078f,
            center = Offset(w * 0.50f, h * 0.56f),
        )
        drawCircle(
            color = Color(0xFFFAF5FF),
            radius = w * 0.034f,
            center = Offset(w * 0.48f, h * 0.54f),
        )

        // Pita dasar mahkota
        drawLine(
            brush = amethystBrush,
            start = Offset(w * 0.17f, h * 0.86f),
            end = Offset(w * 0.83f, h * 0.86f),
            strokeWidth = h * 0.10f,
            cap = StrokeCap.Round,
        )

        // Permata puncak + partikel bintang ungu mengorbit
        drawCircle(color = Color(0xFFFAF5FF), radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.13f))
        drawCircle(color = Color(0xFFE9D5FF), radius = w * 0.055f, center = Offset(w * 0.13f, h * 0.30f))
        drawCircle(color = Color(0xFFE9D5FF), radius = w * 0.055f, center = Offset(w * 0.87f, h * 0.30f))

        val sx = cx + cos(orbit) * (w * 0.44f)
        val sy = cy + sin(orbit) * (h * 0.38f)
        drawCircle(color = Color(0xFFFAF5FF), radius = w * 0.048f, center = Offset(sx, sy))
    }
}

/**
 * Badge visual "SOVEREIGN FOUNDER" berwarna **Ungu Royal Amethyst** super keren
 * (Amethyst Crystal Shimmer + Celestial Purple Sparks + Crest Mahkota Bersayap).
 */
@Composable
internal fun XyFounderBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    subtitle: String? = null,
) {
    val infinite = rememberInfiniteTransition(label = "founder_badge_shimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_sweep",
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(founderBgBrush(shimmer), shape)
            .border(
                width = if (compact) 1.25.dp else 1.5.dp,
                brush = founderBorderBrush(shimmer),
                shape = shape,
            )
            .drawWithContent {
                drawContent()
                // Sapuan kilau cahaya diagonal ungu kristal (specular amethyst sweep)
                val sweepX = (shimmer * 1.6f - 0.3f) * size.width
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFAF5FF).copy(alpha = 0.28f),
                            Color(0xFFD8B4FE).copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        start = Offset(sweepX - size.width * 0.28f, 0f),
                        end = Offset(sweepX + size.width * 0.28f, size.height),
                    ),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            }
            .padding(
                start = if (compact) 5.dp else 7.dp,
                end = if (compact) 8.dp else 11.dp,
                top = if (compact) 2.5.dp else 4.dp,
                bottom = if (compact) 2.5.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.5.dp else 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 16.dp else 20.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA855F7).copy(alpha = 0.52f * glowAlpha),
                            Color(0xFF6B21A8).copy(alpha = 0.34f),
                            Color(0xFF2E1065).copy(alpha = 0.45f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            FounderCrownEmblem(size = if (compact) 12.dp else 14.5.dp)
        }
        Text(
            text = if (subtitle != null && !compact) "SOVEREIGN FOUNDER · $subtitle" else "FOUNDER",
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (compact) 9.sp else 10.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.85.sp,
            color = Color(0xFFFAF5FF),
        )
        // Bintang kristal ungu di sisi kanan badge
        Canvas(modifier = Modifier.size(if (compact) 8.5.dp else 10.5.dp)) {
            val r = size.minDimension * 0.48f * glowAlpha
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val star = Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx, cy, cx + r, cy)
                quadraticTo(cx, cy, cx, cy + r)
                quadraticTo(cx, cy, cx - r, cy)
                quadraticTo(cx, cy, cx, cy - r)
                close()
            }
            drawPath(star, color = Color(0xFFE9D5FF))
        }
    }
}

/**
 * Kontainer Bubble Chat Epik Beranimasi Khusus Founder (`@xykal`) berwarna **Ungu Royal Amethyst**.
 * Menampilkan inti Royal Amethyst (`#1A0633 -> #3B0764 -> #4C1D95 -> #240946`) dengan tepi
 * sapuan ungu-lavender bergerak, kristal amethyst melayang, dan kilau cahaya diagonal real-time.
 */
@Composable
internal fun FounderAnimatedBubbleBox(
    shape: Shape,
    floatingElementStyle: String = "crystals",
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "founder_bubble_fx")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bubble_phase",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bubble_pulse",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1A0633),
                        Color(0xFF2E1065),
                        Color(0xFF3B0764),
                        Color(0xFF4C1D95),
                        Color(0xFF1E083A),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 1.4.dp,
                brush = founderBorderBrush(phase),
                shape = shape,
            )
            .drawWithContent {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA855F7).copy(alpha = 0.22f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.85f, h * 0.22f),
                        radius = w * 0.48f,
                    ),
                    radius = w * 0.48f,
                    center = Offset(w * 0.85f, h * 0.22f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFC084FC).copy(alpha = 0.16f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.18f, h * 0.80f),
                        radius = w * 0.42f,
                    ),
                    radius = w * 0.42f,
                    center = Offset(w * 0.18f, h * 0.80f),
                )

                // Elemen kristal/partikel ungu melayang di dalam bubble Founder
                drawBubbleFloatingElements(
                    elementStyle = floatingElementStyle.ifBlank { "crystals" },
                    accentColor = Color(0xFFD8B4FE),
                    secondaryColor = Color(0xFFA855F7),
                    phase = phase,
                )

                drawContent()

                // Sapuan kilau diagonal tipis ungu lavender
                val sweepX = (phase * 1.5f - 0.25f) * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFAF5FF).copy(alpha = 0.13f),
                            Color(0xFFD8B4FE).copy(alpha = 0.09f),
                            Color.Transparent,
                        ),
                        start = Offset(sweepX - w * 0.22f, 0f),
                        end = Offset(sweepX + w * 0.22f, h),
                    ),
                )
            },
        content = content,
    )
}

/**
 * Ikon logo GitHub (Octocat siluet vektor di atas Canvas) untuk tombol Header & Login.
 */
@Composable
internal fun GitHubLogoIcon(
    size: Dp = 18.dp,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val head = Path().apply {
            moveTo(w * 0.24f, h * 0.36f)
            lineTo(w * 0.20f, h * 0.16f)
            lineTo(w * 0.37f, h * 0.25f)
            cubicTo(w * 0.45f, h * 0.22f, w * 0.55f, h * 0.22f, w * 0.63f, h * 0.25f)
            lineTo(w * 0.80f, h * 0.16f)
            lineTo(w * 0.76f, h * 0.36f)
            cubicTo(w * 0.86f, h * 0.46f, w * 0.85f, h * 0.64f, w * 0.68f, h * 0.71f)
            cubicTo(w * 0.73f, h * 0.76f, w * 0.73f, h * 0.86f, w * 0.73f, h * 0.92f)
            lineTo(w * 0.34f, h * 0.92f)
            cubicTo(w * 0.34f, h * 0.86f, w * 0.34f, h * 0.76f, w * 0.39f, h * 0.71f)
            cubicTo(w * 0.15f, h * 0.64f, w * 0.14f, h * 0.46f, w * 0.24f, h * 0.36f)
            close()
        }
        drawPath(path = head, color = tint, style = Fill)
        val tail = Path().apply {
            moveTo(w * 0.35f, h * 0.81f)
            cubicTo(w * 0.22f, h * 0.83f, w * 0.18f, h * 0.73f, w * 0.12f, h * 0.71f)
        }
        drawPath(
            path = tail,
            color = tint,
            style = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// ============================================================================
// BADGE KHUSUS XYTEAM & TESTER + CLICKABLE BLUE URL LINK RENDERER (RONDE 61)
// ============================================================================

internal val XYTEAM_DEFAULT_LOGINS = setOf("xykal", "xyverse", "xyteam", "projectkal")
internal val XYTESTER_DEFAULT_LOGINS = setOf("xytester", "betatester")

internal object XySpecialRoleRegistry {
    var roleRevision by mutableStateOf(0)
        private set

    fun getRole(context: Context, nameOrLogin: String?): String {
        if (nameOrLogin.isNullOrBlank()) return ""
        val key = nameOrLogin.trim().removePrefix("@").lowercase(Locale.ROOT)
        val prefs = context.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString("role_$key", null)
        if (!saved.isNullOrBlank()) return saved
        return when {
            key in XYTEAM_DEFAULT_LOGINS && key !in FOUNDER_GITHUB_LOGINS -> "XYTEAM"
            key in XYTESTER_DEFAULT_LOGINS -> "TESTER"
            key.contains("xyteam") || key.endsWith("_xy") -> "XYTEAM"
            key.contains("tester") || key.contains("qa_") -> "TESTER"
            else -> ""
        }
    }

    fun setRole(context: Context, nameOrLogin: String?, role: String) {
        if (nameOrLogin.isNullOrBlank()) return
        val key = nameOrLogin.trim().removePrefix("@").lowercase(Locale.ROOT)
        context.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("role_$key", role.uppercase(Locale.ROOT))
            .apply()
        roleRevision += 1
    }
}

internal fun isXyTeamAccount(context: Context, nameOrLogin: String?, githubLogin: String? = null): Boolean {
    val r1 = XySpecialRoleRegistry.getRole(context, githubLogin)
    val r2 = XySpecialRoleRegistry.getRole(context, nameOrLogin)
    return r1 == "XYTEAM" || r1 == "BOTH" || r2 == "XYTEAM" || r2 == "BOTH"
}

internal fun isXyTesterAccount(context: Context, nameOrLogin: String?, githubLogin: String? = null): Boolean {
    val r1 = XySpecialRoleRegistry.getRole(context, githubLogin)
    val r2 = XySpecialRoleRegistry.getRole(context, nameOrLogin)
    return r1 == "TESTER" || r1 == "BOTH" || r2 == "TESTER" || r2 == "BOTH"
}

/**
 * Badge Khusus "XYTEAM" (Tim Inti XyVerse) — Gradasi Cyber Indigo-Cyan + Perisai Bintang Vektor.
 */
@Composable
internal fun XyTeamBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E3A8A),
                        Color(0xFF0369A1),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF22D3EE)),
                ),
                shape = RoundedCornerShape(50),
            )
            .padding(
                horizontal = if (compact) 6.dp else 9.dp,
                vertical = if (compact) 2.dp else 3.5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
    ) {
        Canvas(modifier = Modifier.size(if (compact) 10.dp else 12.dp)) {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(w * 0.50f, h * 0.08f)
                lineTo(w * 0.88f, h * 0.24f)
                lineTo(w * 0.82f, h * 0.64f)
                lineTo(w * 0.50f, h * 0.92f)
                lineTo(w * 0.18f, h * 0.64f)
                lineTo(w * 0.12f, h * 0.24f)
                close()
            }
            drawPath(shield, color = Color(0xFF38BDF8), style = Fill)
            drawCircle(color = Color(0xFFF0F9FF), radius = w * 0.18f, center = Offset(w * 0.50f, h * 0.48f))
        }
        Text(
            text = "XYTEAM",
            fontSize = if (compact) 8.5.sp else 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFE0F2FE),
            maxLines = 1,
        )
    }
}

/**
 * Badge Khusus "TESTER" (QA & Beta Tester Terverifikasi) — Gradasi Emerald-Teal + Emblem Verified Check.
 */
@Composable
internal fun XyTesterBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF062C21),
                        Color(0xFF065F46),
                        Color(0xFF0F766E),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF2DD4BF), Color(0xFF6EE7B7)),
                ),
                shape = RoundedCornerShape(50),
            )
            .padding(
                horizontal = if (compact) 6.dp else 9.dp,
                vertical = if (compact) 2.dp else 3.5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
    ) {
        Canvas(modifier = Modifier.size(if (compact) 10.dp else 12.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(color = Color(0xFF10B981), radius = w * 0.44f, center = Offset(w * 0.50f, h * 0.50f))
            val check = Path().apply {
                moveTo(w * 0.28f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.34f)
            }
            drawPath(
                check,
                color = Color(0xFFECFDF5),
                style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Text(
            text = "TESTER",
            fontSize = if (compact) 8.5.sp else 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFD1FAE5),
            maxLines = 1,
        )
    }
}

/**
 * Menampilkan deretan badge peran khusus pengguna: FOUNDER (Ungu Royal), XYTEAM, dan/atau TESTER.
 */
@Composable
internal fun XyUserRoleBadges(
    name: String?,
    githubLogin: String? = null,
    compact: Boolean = true,
) {
    val context = LocalContext.current
    val rev = XySpecialRoleRegistry.roleRevision
    val isFounder = remember(name, githubLogin) {
        isFounderAccount(githubLogin) || isFounderAccount(name)
    }
    val isTeam = remember(name, githubLogin, rev) {
        isXyTeamAccount(context, name, githubLogin)
    }
    val isTester = remember(name, githubLogin, rev) {
        isXyTesterAccount(context, name, githubLogin)
    }
    if (isFounder || isTeam || isTester) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isFounder) {
                XyFounderBadge(compact = compact)
            }
            if (isTeam) {
                XyTeamBadge(compact = compact)
            }
            if (isTester) {
                XyTesterBadge(compact = compact)
            }
        }
    }
}

private val XY_URL_REGEX = Regex(
    """((https?://|www\.)[^\s<>"]+|[a-zA-Z0-9.-]+\.(id|com|org|net|io|dev|app|me|xyz|co)(/[^\s<>"]*)?)""",
    RegexOption.IGNORE_CASE,
)

/**
 * Komponen Teks Pintar yang membuat SEMUA link/URL berwarna BIRU CERAH + bergaris bawah
 * dan langsung BISA DIKLIK (membuka In-App Browser Chrome Custom Tab), sementara klik/tekan
 * pada bagian teks biasa tetap meneruskan aksi normal/long-press.
 */
@Composable
internal fun XyClickableLinkText(
    text: String,
    color: Color,
    linkColor: Color = Color(0xFF38BDF8),
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    onNonLinkClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val annotated = remember(text, color, linkColor) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            addStyle(
                style = androidx.compose.ui.text.SpanStyle(color = color),
                start = 0,
                end = text.length,
            )
            XY_URL_REGEX.findAll(text).forEach { match ->
                val rawUrl = match.value
                val fullUrl = if (rawUrl.startsWith("http://", ignoreCase = true) ||
                    rawUrl.startsWith("https://", ignoreCase = true)
                ) {
                    rawUrl
                } else {
                    "https://$rawUrl"
                }
                addStyle(
                    style = androidx.compose.ui.text.SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ),
                    start = match.range.first,
                    end = match.range.last + 1,
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = fullUrl,
                    start = match.range.first,
                    end = match.range.last + 1,
                )
            }
        }
    }

    val hasLinks = remember(annotated) {
        annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty()
    }

    if (!hasLinks) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    } else {
        var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
        Text(
            text = annotated,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult = it },
            modifier = modifier.pointerInput(annotated, onNonLinkClick, onLongPress) {
                detectTapGestures(
                    onLongPress = { onLongPress?.invoke() },
                    onTap = { pos ->
                        val layout = layoutResult
                        if (layout != null) {
                            val offset = layout.getOffsetForPosition(pos)
                            val hit = annotated.getStringAnnotations("URL", offset, offset).firstOrNull()
                            if (hit != null) {
                                openInAppCustomTab(context, hit.item)
                                return@detectTapGestures
                            }
                        }
                        onNonLinkClick?.invoke()
                    },
                )
            },
        )
    }
}

