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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
import id.xydesk.remote.ui.components.XyIcons
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    val previous = getVerifiedGitHubLogin(context)
    val changedAccount = !previous.orEmpty().equals(clean, ignoreCase = true)
    val appCtx = context.applicationContext
    val resolvedAvatar = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (clean.isNotEmpty()) "https://github.com/$clean.png?size=160" else null
    val edit = appCtx.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_GH_LOGIN, clean)
    if (clean.isEmpty()) edit.remove(KEY_GH_AVATAR)
    else if (resolvedAvatar != null) edit.putString(KEY_GH_AVATAR, resolvedAvatar)
    edit.apply()
}

internal fun normalizedChatAccount(value: String?): String =
    value.orEmpty().trim().removePrefix("@").lowercase()

@Composable
internal fun rememberVerifiedChatAccount(context: Context): String? {
    var login by remember(context) { mutableStateOf(getVerifiedGitHubLogin(context)) }
    androidx.compose.runtime.DisposableEffect(context) {
        val prefs = context.applicationContext.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_GH_LOGIN || key == null) login = getVerifiedGitHubLogin(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        login = getVerifiedGitHubLogin(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return login
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
        GitHubOAuthEndpoints.login(sessionId)
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
        val deadline = System.currentTimeMillis() + 15 * 60 * 1000L
        val pollUrls = listOf(
            "https://api.xydeskremote.biz.id/api/github/oauth/poll?session_id=$sessionId",
        )
        while (isActive && !completed && System.currentTimeMillis() < deadline) {
            // 3 detik: deep link tetap jalur utama; polling hanya cadangan.
            // Interval 1,2 detik membakar request Worker tanpa manfaat nyata.
            delay(3_000L)
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
        var intervalMs = (start.optInt("interval", 5).coerceIn(3, 12)) * 1000L
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
            } else if (err == "slow_down") {
                // GitHub meminta polling diperlambat; hormati agar device
                // code tidak terus ditembak pada interval lama.
                intervalMs += 5_000L
            } else if (err == "expired_token") {
                GitHubDirectOAuthController.dismiss(
                    xyNow(
                        "Sesi login GitHub kedaluwarsa sebelum otorisasi selesai. Buka login lagi — sesi baru akan dibuat.",
                        "The GitHub sign-in session expired before authorization finished. Reopen sign-in for a fresh session.",
                    ),
                )
                return@LaunchedEffect
            }
        }
    }

    if (!showEmbeddedWebView) {
        Dialog(
            onDismissRequest = { GitHubDirectOAuthController.dismiss() },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF101010),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                tonalElevation = 12.dp,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Tombol X kecil di pojok kanan atas untuk tutup tanpa menambah tombol di bawah
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { GitHubDirectOAuthController.dismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = XyIcons.Close,
                            contentDescription = xyNow("Tutup", "Close"),
                            tint = Color(0xFFA2A2A2),
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Ilustrasi Koneksi: [Ikon XyDesk] ── [Ikon Link] ── [Ikon GitHub]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF656565).copy(alpha = 0.18f))
                                    .border(1.dp, Color(0xFF9C9C9C).copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = XyIcons.Monitor,
                                    contentDescription = "XyDesk",
                                    tint = Color(0xFF9C9C9C),
                                    modifier = Modifier.size(26.dp),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(2.dp)
                                    .background(Color.White.copy(alpha = 0.22f)),
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF808080).copy(alpha = 0.16f))
                                    .border(1.dp, Color(0xFF9D9D9D).copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = XyIcons.Link,
                                    contentDescription = "Link",
                                    tint = Color(0xFF9D9D9D),
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(2.dp)
                                    .background(Color.White.copy(alpha = 0.22f)),
                            )

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.09f))
                                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                GitHubLogoIcon(size = 26.dp, tint = Color.White)
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = xyNow("Hubungkan XyDesk & GitHub", "Connect XyDesk & GitHub"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = xyNow(
                                    "Otorisasi satu klik melalui browser utama Anda. Cukup tekan tombol hijau 'Authorize' untuk masuk otomatis.",
                                    "One-click authorization via your main browser. Just tap the green 'Authorize' button to sign in automatically.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA2A2A2),
                                textAlign = TextAlign.Center,
                            )
                        }

                        // HANYA 1 Tombol Utama: Login di Browser (dengan Ikon)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .clickable { openInAppCustomTab(context, chromeAuthUrl) }
                                .padding(vertical = 13.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GitHubLogoIcon(size = 18.dp, tint = Color(0xFF09090B))
                                Text(
                                    text = xyNow("Lanjutkan di Browser", "Continue in Browser"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF09090B),
                                )
                                Icon(
                                    imageVector = XyIcons.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF09090B),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
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
                        androidx.compose.material3.Icon(
                            imageVector = id.xydesk.remote.ui.components.XyIcons.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
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
                            color = Color(0xFFA2A2A2),
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
                        .background(Color(0xFF191919))
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
                                }
                            }
                            loadUrl(if (useWebRedirectMode) chromeAuthUrl else "https://github.com/login/device")
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
// BADGE KHUSUS XYTEAM & TESTER + CLICKABLE BLUE URL LINK RENDERER (RONDE 61/70)
// ============================================================================

