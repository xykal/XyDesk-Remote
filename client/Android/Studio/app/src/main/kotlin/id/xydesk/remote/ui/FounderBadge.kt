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
    val changedAccount = normalizedChatAccount(previous) != normalizedChatAccount(clean)
    val appCtx = context.applicationContext
    val resolvedAvatar = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (clean.isNotEmpty()) "https://github.com/$clean.png?size=160" else null
    val edit = appCtx.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_GH_LOGIN, clean)
    if (clean.isEmpty()) edit.remove(KEY_GH_AVATAR)
    else if (resolvedAvatar != null) edit.putString(KEY_GH_AVATAR, resolvedAvatar)
    edit.apply()

    if (clean.isNotEmpty()) {
        val chatPrefs = appCtx.getSharedPreferences("xydesk.global.chat", Context.MODE_PRIVATE)
        val currentNick = chatPrefs.getString("nickname", null)
        if (changedAccount || currentNick.isNullOrBlank() || currentNick.startsWith("Tamu-") || isFounderAccount(clean)) {
            chatPrefs.edit().putString("nickname", clean).apply()
        }
    } else if (changedAccount) {
        appCtx.getSharedPreferences("xydesk.global.chat", Context.MODE_PRIVATE).edit().remove("nickname").apply()
    }
    if (changedAccount) id.xydesk.remote.push.ChatPushNotifier.onAccountChanged(appCtx)
    GlobalChatOutbox.resume(appCtx)
}

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
        val deadline = System.currentTimeMillis() + 10 * 60 * 1000L
        val pollUrls = listOf(
            "https://api.xydeskremote.biz.id/api/github/oauth/poll?session_id=$sessionId",
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

internal fun founderBorderBrush(phase: Float = 0f): Brush {
    val shift = phase * 360f
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFF8F8F8),
            Color(0xFFE0E0E0),
            Color(0xFFA4A4A4),
            Color(0xFF808080),
            Color(0xFF515151),
            Color(0xFFC7C7C7),
            Color(0xFFF8F8F8),
        ),
        start = Offset(shift, 0f),
        end = Offset(shift + 240f, 180f),
    )
}

internal fun founderBgBrush(phase: Float = 0f): Brush {
    val offset = phase * 220f
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF0F0F0F),
            Color(0xFF232323),
            Color(0xFF212121),
            Color(0xFF393939),
            Color(0xFF111111),
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
    val baseRadiusDp = if (compact) 14f else 18f
    return when (activeStyle.lowercase(Locale.ROOT)) {
        "sharp", "sharp_edge" -> androidx.compose.foundation.shape.GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val cut = (minOf(w, h) * 0.18f).coerceIn(6f, 18f)
            if (own) {
                // Ujung kanan-atas tajam mengarah tepat ke foto profil pengirim di atas
                moveTo(cut, 0f)
                lineTo(w, 0f)
                lineTo(w, h - cut)
                lineTo(w - cut, h)
                lineTo(cut * 0.6f, h)
                lineTo(0f, h - cut * 0.6f)
                lineTo(0f, cut)
                close()
            } else {
                // Ujung kiri-atas tajam mengarah tepat ke foto profil pengirim di atas
                moveTo(0f, 0f)
                lineTo(w - cut, 0f)
                lineTo(w, cut)
                lineTo(w, h - cut * 0.6f)
                lineTo(w - cut * 0.6f, h)
                lineTo(cut, h)
                lineTo(0f, h - cut)
                close()
            }
        }
        "abstract", "abstract_cyber" -> androidx.compose.foundation.shape.GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val c1 = (minOf(w, h) * 0.20f).coerceIn(7f, 20f)
            val c2 = (minOf(w, h) * 0.09f).coerceIn(4f, 10f)
            if (own) {
                moveTo(c1, 0f)
                lineTo(w, 0f)
                lineTo(w, h - c1)
                lineTo(w - c1, h)
                lineTo(c2, h)
                lineTo(0f, h - c2)
                lineTo(0f, c1)
                close()
            } else {
                moveTo(0f, 0f)
                lineTo(w - c1, 0f)
                lineTo(w, c1)
                lineTo(w, h - c2)
                lineTo(w - c2, h)
                lineTo(c1, h)
                lineTo(0f, h - c1)
                close()
            }
        }
        "morph", "morph_wave" -> {
            val wave1 = (sin(morphPhase * 2f * PI.toFloat()) * 4f)
            val wave2 = (cos(morphPhase * 2f * PI.toFloat()) * 4f)
            val tl = if (own) (baseRadiusDp + wave1).coerceIn(10f, 24f).dp else 2.dp
            val tr = if (own) 2.dp else (baseRadiusDp - wave2).coerceIn(10f, 24f).dp
            val bl = (baseRadiusDp + wave2).coerceIn(10f, 24f).dp
            val br = (baseRadiusDp - wave1).coerceIn(10f, 24f).dp
            RoundedCornerShape(topStart = tl, topEnd = tr, bottomEnd = br, bottomStart = bl)
        }
        "cloud", "cloud_soft" -> {
            val r = (baseRadiusDp + 4f).dp
            val sharpTop = 3.dp
            RoundedCornerShape(
                topStart = if (own) r else sharpTop,
                topEnd = if (own) sharpTop else r,
                bottomEnd = r,
                bottomStart = r,
            )
        }
        else -> {
            val r = (if (compact) 13f else 17f).dp
            val tailCorner = (if (compact) 3.5f else 4.5f).dp
            RoundedCornerShape(
                topStart = if (own) r else tailCorner,
                topEnd = if (own) tailCorner else r,
                bottomEnd = r,
                bottomStart = r,
            )
        }
    }
}

/**
 * Menggambar elemen-elemen melayang beranimasi (Multi-Layer AI Floating Elements + Aurora Wave + Shooting Star)
 * di dalam bubble chat.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBubbleFloatingElements(
    elementStyle: String = "",
    preset: String = "",
    accentColor: Color = Color(0xFFC7C7C7),
    secondaryColor: Color = Color(0xFF808080),
    phase: Float = 0f,
) {
    val rawStyle = preset.ifBlank { elementStyle }.trim().lowercase(Locale.ROOT)
    if (rawStyle == "none") return
    val style = when (rawStyle) {
        "sparkles", "crystal", "crystals", "" -> "crystals"
        "bubbles", "bubble", "orbs", "plasma" -> "bubbles"
        "stars", "star", "aurora", "galaxy" -> "stars"
        "cyber", "matrix", "neon" -> "cyber"
        "hearts", "heart", "love" -> "hearts"
        "sakura", "petals" -> "sakura"
        "fireflies", "firefly" -> "fireflies"
        else -> "crystals"
    }
    val w = size.width
    val h = size.height
    if (w <= 2f || h <= 2f) return

    // 1. Pita Gelombang Aurora / Plasma Halus di Latar Bubble
    val wavePhase = phase * 2f * PI.toFloat()
    val auroraPath = Path().apply {
        val yBase = h * 0.68f
        val amp = (h * 0.16f).coerceIn(4.dp.toPx(), 18.dp.toPx())
        moveTo(0f, h)
        lineTo(0f, yBase + sin(wavePhase) * amp)
        val steps = 6
        for (i in 1..steps) {
            val x0 = w * ((i - 0.5f) / steps)
            val x1 = w * (i.toFloat() / steps)
            val y0 = yBase + sin(wavePhase + i * 0.95f) * amp
            val y1 = yBase + cos(wavePhase + i * 1.15f) * (amp * 0.85f)
            quadraticTo(x0, y0, x1, y1)
        }
        lineTo(w, h)
        close()
    }
    drawPath(
        path = auroraPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.16f),
                secondaryColor.copy(alpha = 0.08f),
                Color.Transparent,
            ),
            startY = h * 0.35f,
            endY = h,
        ),
    )

    // 2. Efek Bintang Jatuh / Meteor Diagonal Berkala
    val meteorProgress = ((phase * 1.85f) % 1f)
    if (meteorProgress in 0.08f..0.78f) {
        val norm = (meteorProgress - 0.08f) / 0.70f
        val headX = w * (0.12f + norm * 0.78f)
        val headY = h * (0.14f + norm * 0.56f)
        val tailLenX = (w * 0.20f).coerceAtMost(38.dp.toPx())
        val tailLenY = tailLenX * 0.45f
        val meteorAlpha = sin(norm * PI.toFloat()).coerceIn(0f, 1f)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    accentColor.copy(alpha = 0.42f * meteorAlpha),
                    Color.White.copy(alpha = 0.85f * meteorAlpha),
                ),
                start = Offset(headX - tailLenX, headY - tailLenY),
                end = Offset(headX, headY),
            ),
            start = Offset(headX - tailLenX, headY - tailLenY),
            end = Offset(headX, headY),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.90f * meteorAlpha),
            radius = 2.2.dp.toPx(),
            center = Offset(headX, headY),
        )
    }

    // 3. Partikel 3D Parallax Melayang Sesuai Preset
    val seeds = listOf(
        Triple(0.10f, 0.22f, 0.95f),
        Triple(0.24f, 0.74f, 1.15f),
        Triple(0.38f, 0.28f, 0.80f),
        Triple(0.52f, 0.68f, 1.25f),
        Triple(0.66f, 0.20f, 1.00f),
        Triple(0.78f, 0.72f, 1.30f),
        Triple(0.88f, 0.30f, 1.05f),
        Triple(0.18f, 0.50f, 0.75f),
        Triple(0.60f, 0.46f, 0.90f),
        Triple(0.92f, 0.58f, 0.85f),
    )

    seeds.forEachIndexed { idx, (bx, by, scale) ->
        val angle = (phase * 2f * PI.toFloat()) + idx * 0.92f
        val dx = cos(angle + idx * 0.4f) * (8.dp.toPx() * scale)
        val driftUp = ((phase + idx * 0.13f) % 1f)
        val rawY = h * ((by - driftUp * 0.32f + 1f) % 1f)
        val dy = sin(angle * 1.4f) * (5.dp.toPx() * scale)
        val cx = (w * bx + dx).coerceIn(5f, (w - 5f).coerceAtLeast(5f))
        val cy = (rawY + dy).coerceIn(5f, (h - 5f).coerceAtLeast(5f))
        val pulse = (0.50f + 0.50f * ((sin(angle * 1.5f) + 1f) * 0.5f)).coerceIn(0.28f, 1f)
        val tint = if (idx % 2 == 0) accentColor else secondaryColor

        when (style) {
            "crystals" -> {
                val r = 5.4.dp.toPx() * scale
                // Halo cahaya di belakang kristal
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.32f * pulse), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r * 1.8f,
                    ),
                    radius = r * 1.8f,
                    center = Offset(cx, cy),
                )
                val diamond = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.72f, cy)
                    lineTo(cx, cy + r)
                    lineTo(cx - r * 0.72f, cy)
                    close()
                }
                drawPath(path = diamond, color = tint.copy(alpha = 0.44f * pulse))
                drawPath(
                    path = diamond,
                    color = Color.White.copy(alpha = 0.62f * pulse),
                    style = Stroke(width = 1.dp.toPx()),
                )
                // Kilau silang di inti kristal
                val sparkR = r * 0.55f * pulse
                drawLine(
                    color = Color.White.copy(alpha = 0.75f * pulse),
                    start = Offset(cx - sparkR, cy),
                    end = Offset(cx + sparkR, cy),
                    strokeWidth = 0.9.dp.toPx(),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.75f * pulse),
                    start = Offset(cx, cy - sparkR),
                    end = Offset(cx, cy + sparkR),
                    strokeWidth = 0.9.dp.toPx(),
                )
            }
            "bubbles" -> {
                val r = 7.8.dp.toPx() * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.20f * pulse),
                            tint.copy(alpha = 0.38f * pulse),
                            secondaryColor.copy(alpha = 0.14f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.50f * pulse),
                    radius = r * 0.78f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.9.dp.toPx()),
                )
                // Titik kilau spekular 3D di pojok kiri atas gelembung
                drawCircle(
                    color = Color.White.copy(alpha = 0.82f * pulse),
                    radius = 1.6.dp.toPx() * scale,
                    center = Offset(cx - r * 0.28f, cy - r * 0.28f),
                )
            }
            "stars" -> {
                val r = 6.2.dp.toPx() * scale * pulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.38f * pulse), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r * 1.7f,
                    ),
                    radius = r * 1.7f,
                    center = Offset(cx, cy),
                )
                val star = Path().apply {
                    moveTo(cx, cy - r)
                    quadraticTo(cx, cy, cx + r, cy)
                    quadraticTo(cx, cy, cx, cy + r)
                    quadraticTo(cx, cy, cx - r, cy)
                    quadraticTo(cx, cy, cx, cy - r)
                    close()
                }
                drawPath(path = star, color = tint.copy(alpha = 0.68f * pulse))
                drawCircle(color = Color.White.copy(alpha = 0.90f * pulse), radius = 1.5.dp.toPx(), center = Offset(cx, cy))
            }
            "cyber" -> {
                val s = 6.2.dp.toPx() * scale
                // Jejak partikel matriks vertikal
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, tint.copy(alpha = 0.55f * pulse), Color.White.copy(alpha = 0.80f * pulse)),
                        startY = cy - s * 1.8f,
                        endY = cy + s * 0.5f,
                    ),
                    start = Offset(cx, cy - s * 1.8f),
                    end = Offset(cx, cy + s * 0.5f),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = tint.copy(alpha = 0.52f * pulse),
                    topLeft = Offset(cx - s * 0.5f, cy - s * 0.5f),
                    size = androidx.compose.ui.geometry.Size(s, s),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            "hearts" -> {
                val r = 5.2.dp.toPx() * scale * (0.85f + 0.2f * pulse)
                val heart = Path().apply {
                    moveTo(cx, cy + r * 0.75f)
                    cubicTo(cx - r * 1.2f, cy + r * 0.1f, cx - r * 0.95f, cy - r * 0.85f, cx, cy - r * 0.25f)
                    cubicTo(cx + r * 0.95f, cy - r * 0.85f, cx + r * 1.2f, cy + r * 0.1f, cx, cy + r * 0.75f)
                    close()
                }
                drawPath(path = heart, color = Color(0xFFA1A1A1).copy(alpha = 0.52f * pulse))
                drawPath(
                    path = heart,
                    color = Color.White.copy(alpha = 0.45f * pulse),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            }
            "sakura" -> {
                val r = 4.8.dp.toPx() * scale
                val petal = Path().apply {
                    moveTo(cx, cy - r)
                    quadraticTo(cx + r * 1.1f, cy - r * 0.25f, cx, cy + r)
                    quadraticTo(cx - r * 1.1f, cy - r * 0.25f, cx, cy - r)
                    close()
                }
                drawPath(path = petal, color = Color(0xFFC5C5C5).copy(alpha = 0.52f * pulse))
            }
            "fireflies" -> {
                val r = 7.2.dp.toPx() * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE9E9E9).copy(alpha = 0.72f * pulse),
                            tint.copy(alpha = 0.34f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color(0xFFFDFDFD).copy(alpha = 0.92f * pulse),
                    radius = 1.6.dp.toPx() * scale,
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
    val infinite = if (id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current) rememberInfiniteTransition(label = "founder_crown_anim") else null
    val pulse = if (infinite != null) {
        val animated by infinite.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "crown_pulse",
        )
        animated
    } else 1f
    val orbit = if (infinite != null) {
        val animated by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "crown_orbit",
        )
        animated
    } else 0f

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // Halo aura ungu royal amethyst berdenyut di belakang mahkota
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFA4A4A4).copy(alpha = 0.48f * pulse.coerceIn(0.8f, 1.15f)),
                    Color(0xFF515151).copy(alpha = 0.22f),
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
                Color(0xFFF8F8F8),
                Color(0xFFE0E0E0),
                Color(0xFFA4A4A4),
                Color(0xFF656565),
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
            color = Color(0xFFF8F8F8).copy(alpha = 0.88f),
            style = Stroke(width = w * 0.045f, join = StrokeJoin.Round),
        )

        // Permata Amethyst Ungu Neon di tengah mahkota
        drawCircle(
            color = Color(0xFF474747),
            radius = w * 0.078f,
            center = Offset(w * 0.50f, h * 0.56f),
        )
        drawCircle(
            color = Color(0xFFF8F8F8),
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
        drawCircle(color = Color(0xFFF8F8F8), radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.13f))
        drawCircle(color = Color(0xFFE0E0E0), radius = w * 0.055f, center = Offset(w * 0.13f, h * 0.30f))
        drawCircle(color = Color(0xFFE0E0E0), radius = w * 0.055f, center = Offset(w * 0.87f, h * 0.30f))

        val sx = cx + cos(orbit) * (w * 0.44f)
        val sy = cy + sin(orbit) * (h * 0.38f)
        drawCircle(color = Color(0xFFF8F8F8), radius = w * 0.048f, center = Offset(sx, sy))
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
    val infinite = if (id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current) rememberInfiniteTransition(label = "founder_badge_shimmer") else null
    val shimmer = if (infinite != null) {
        val animated by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer_sweep",
        )
        animated
    } else 0f
    val glowAlpha = if (infinite != null) {
        val animated by infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow_alpha",
        )
        animated
    } else 0.8f

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
                            Color(0xFFF8F8F8).copy(alpha = 0.28f),
                            Color(0xFFC7C7C7).copy(alpha = 0.18f),
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
                            Color(0xFF808080).copy(alpha = 0.52f * glowAlpha),
                            Color(0xFF474747).copy(alpha = 0.34f),
                            Color(0xFF232323).copy(alpha = 0.45f),
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
            color = Color(0xFFF8F8F8),
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
            drawPath(star, color = Color(0xFFE0E0E0))
        }
    }
}

/**
 * Menggambar sambaran Petir Elektrik Bercabang (Royal Amethyst Cyber Thunderstorm)
 * khusus di dalam Bubble Founder (`@xykal`).
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFounderLightningStorm(
    phase: Float,
    boltPulse: Float,
) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return

    // Kilatan guntur latar (Thunder Flash) saat petir menyambar
    val strikeCycle1 = ((phase * 3.0f) % 1f)
    val strikeIntensity1 = if (strikeCycle1 < 0.45f) {
        sin((strikeCycle1 / 0.45f) * PI.toFloat()).coerceIn(0f, 1f)
    } else 0.18f

    val strikeCycle2 = (((phase + 0.48f) * 2.5f) % 1f)
    val strikeIntensity2 = if (strikeCycle2 < 0.50f) {
        sin((strikeCycle2 / 0.50f) * PI.toFloat()).coerceIn(0f, 1f)
    } else 0.15f

    // Helper menggambar 1 batang petir 3-lapis (Outer Violet Glow + Mid Electric Magenta + Core White-Hot)
    fun drawElectricBolt(points: List<Offset>, intensity: Float, widthScale: Float = 1f) {
        if (points.size < 2 || intensity <= 0.02f) return
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        // Lapisan 1: Outer Electric Violet Halo
        drawPath(
            path = path,
            color = Color(0xFF808080).copy(alpha = (0.46f * intensity).coerceIn(0f, 1f)),
            style = Stroke(width = 5.5.dp.toPx() * widthScale, cap = StrokeCap.Round, join = StrokeJoin.Miter),
        )
        // Lapisan 2: Mid Neon Amethyst-Fuchsia Plasma
        drawPath(
            path = path,
            color = Color(0xFFA9A9A9).copy(alpha = (0.78f * intensity).coerceIn(0f, 1f)),
            style = Stroke(width = 2.5.dp.toPx() * widthScale, cap = StrokeCap.Round, join = StrokeJoin.Miter),
        )
        // Lapisan 3: Core White-Hot Lightning
        drawPath(
            path = path,
            color = Color(0xFFF8F8F8).copy(alpha = (0.96f * intensity).coerceIn(0f, 1f)),
            style = Stroke(width = 1.1.dp.toPx() * widthScale, cap = StrokeCap.Round, join = StrokeJoin.Miter),
        )
    }

    val jitter1 = sin(phase * 18f) * 3.5.dp.toPx()
    val jitter2 = cos(phase * 22f) * 3.0.dp.toPx()

    // Petir Utama #1 (Menyambar dari kanan-atas ke tengah-kiri)
    val mainBolt1 = listOf(
        Offset(w * 0.88f, h * 0.04f),
        Offset(w * 0.74f + jitter1, h * 0.26f),
        Offset(w * 0.80f - jitter2, h * 0.42f),
        Offset(w * 0.62f + jitter1, h * 0.66f),
        Offset(w * 0.67f, h * 0.82f),
        Offset(w * 0.52f - jitter1, h * 0.96f),
    )
    drawElectricBolt(mainBolt1, strikeIntensity1, 1.0f)

    // Cabang Petir #1A & #1B
    val fork1A = listOf(
        mainBolt1[1],
        Offset(w * 0.60f - jitter2, h * 0.34f),
        Offset(w * 0.48f + jitter1, h * 0.46f),
    )
    val fork1B = listOf(
        mainBolt1[3],
        Offset(w * 0.78f + jitter2, h * 0.78f),
        Offset(w * 0.86f, h * 0.90f),
    )
    drawElectricBolt(fork1A, strikeIntensity1 * 0.85f, 0.65f)
    drawElectricBolt(fork1B, strikeIntensity1 * 0.75f, 0.58f)

    // Petir Utama #2 (Menyambar dari kiri-atas ke tengah-bawah)
    val mainBolt2 = listOf(
        Offset(w * 0.14f, h * 0.06f),
        Offset(w * 0.26f - jitter2, h * 0.30f),
        Offset(w * 0.19f + jitter1, h * 0.48f),
        Offset(w * 0.34f - jitter1, h * 0.74f),
        Offset(w * 0.28f + jitter2, h * 0.94f),
    )
    drawElectricBolt(mainBolt2, strikeIntensity2, 0.92f)

    // Cabang Petir #2A
    val fork2A = listOf(
        mainBolt2[1],
        Offset(w * 0.40f + jitter1, h * 0.38f),
        Offset(w * 0.46f, h * 0.54f),
    )
    drawElectricBolt(fork2A, strikeIntensity2 * 0.80f, 0.60f)

    // Busur Listrik Horisontal Halus (Electric Plasma Arc) yang merambat di tepi atas/bawah
    val arcX = w * ((phase * 1.4f) % 1f)
    val arcPoints = listOf(
        Offset((arcX - w * 0.18f).coerceAtLeast(0f), h * 0.12f),
        Offset((arcX - w * 0.09f).coerceIn(0f, w), h * 0.06f + jitter1 * 0.6f),
        Offset(arcX.coerceIn(0f, w), h * 0.15f - jitter2 * 0.6f),
        Offset((arcX + w * 0.10f).coerceAtMost(w), h * 0.08f),
    )
    drawElectricBolt(arcPoints, 0.55f * boltPulse, 0.55f)

    // Percikan Simpul Listrik (Electric Node Sparks)
    val sparkNodes = listOf(mainBolt1[1], mainBolt1[3], mainBolt2[1], mainBolt2[3])
    sparkNodes.forEachIndexed { idx, pt ->
        val nodeAlpha = if (idx < 2) strikeIntensity1 else strikeIntensity2
        if (nodeAlpha > 0.22f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f * nodeAlpha),
                        Color(0xFFA9A9A9).copy(alpha = 0.45f * nodeAlpha),
                        Color.Transparent,
                    ),
                    center = pt,
                    radius = 9.dp.toPx(),
                ),
                radius = 9.dp.toPx(),
                center = pt,
            )
        }
    }
}

/**
 * Kontainer Bubble Chat Epik Beranimasi Khusus Founder (`@xykal`) berwarna **Ungu Royal Amethyst**
 * dengan **Efek Sambaran Petir Elektrik Bercabang (Cyber Thunderstorm)** super keren.
 */
@Composable
internal fun FounderAnimatedBubbleBox(
    shape: Shape,
    floatingElementStyle: String = "crystals",
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val infinite = if (id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current) rememberInfiniteTransition(label = "founder_bubble_fx") else null
    val phase = if (infinite != null) {
        val animated by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "bubble_phase",
        )
        animated
    } else 0f
    val pulse = if (infinite != null) {
        val animated by infinite.animateFloat(
            initialValue = 0.62f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bubble_pulse",
        )
        animated
    } else 1f

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0C0C0C),
                        Color(0xFF1E1E1E),
                        Color(0xFF393939),
                        Color(0xFF474747),
                        Color(0xFF212121),
                        Color(0xFF0F0F0F),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(420f * (0.65f + 0.35f * sin(phase * 2f * PI.toFloat())), 340f),
                ),
                shape = shape,
            )
            .border(
                width = 1.6.dp,
                brush = founderBorderBrush(phase),
                shape = shape,
            )
            .drawWithContent {
                val w = size.width
                val h = size.height
                val orbAngle = phase * 2f * PI.toFloat()
                val orb1X = w * (0.78f + 0.14f * cos(orbAngle))
                val orb1Y = h * (0.26f + 0.14f * sin(orbAngle))
                val orb2X = w * (0.22f + 0.14f * sin(orbAngle))
                val orb2Y = h * (0.76f + 0.12f * cos(orbAngle))

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA4A4A4).copy(alpha = 0.36f * pulse),
                            Color(0xFF656565).copy(alpha = 0.15f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(orb1X, orb1Y),
                        radius = w * 0.55f,
                    ),
                    radius = w * 0.55f,
                    center = Offset(orb1X, orb1Y),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA9A9A9).copy(alpha = 0.28f * pulse),
                            Color(0xFF515151).copy(alpha = 0.12f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(orb2X, orb2Y),
                        radius = w * 0.48f,
                    ),
                    radius = w * 0.48f,
                    center = Offset(orb2X, orb2Y),
                )

                // Efek Utama Bubble Founder: Sambaran Petir Elektrik Bercabang (Royal Amethyst Lightning Storm)
                drawFounderLightningStorm(phase = phase, boltPulse = pulse)

                drawContent()

                // Sapuan kilau kaca spekular diagonal ungu-putih kristal
                val sweepX = (phase * 1.65f - 0.32f) * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFF8F8F8).copy(alpha = 0.18f),
                            Color(0xFFA9A9A9).copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        start = Offset(sweepX - w * 0.24f, 0f),
                        end = Offset(sweepX + w * 0.24f, h),
                    ),
                )
            },
        content = content,
    )
}

/**
 * Kontainer Bubble Chat Beranimasi Khusus untuk Role `XYTEAM` (Cyber Cyan-Indigo Grid)
 * dan `TESTER` (Bio-Lab Emerald Matrix).
 */
@Composable
internal fun XyRoleAnimatedBubbleBox(
    role: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isTeam = role.equals("XYTEAM", ignoreCase = true)
    val infinite = if (id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current) rememberInfiniteTransition(label = "role_bubble_fx") else null
    val phase = if (infinite != null) {
        val animated by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "role_phase",
        )
        animated
    } else 0f
    val bgColors = if (isTeam) {
        listOf(Color(0xFF131313), Color(0xFF222222), Color(0xFF2E2E2E), Color(0xFF171717))
    } else {
        listOf(Color(0xFF151515), Color(0xFF252525), Color(0xFF333333), Color(0xFF191919))
    }
    val borderColors = if (isTeam) {
        listOf(Color(0xFF9C9C9C), Color(0xFF959595), Color(0xFFA1A1A1), Color(0xFF9C9C9C))
    } else {
        listOf(Color(0xFF9D9D9D), Color(0xFF808080), Color(0xFFBDBDBD), Color(0xFF9D9D9D))
    }
    val accent = if (isTeam) Color(0xFF9C9C9C) else Color(0xFF9D9D9D)
    val secondary = if (isTeam) Color(0xFF959595) else Color(0xFF808080)

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = Brush.linearGradient(colors = bgColors), shape = shape)
            .border(width = 1.2.dp, brush = Brush.linearGradient(colors = borderColors), shape = shape)
            .drawWithContent {
                drawBubbleFloatingElements(
                    preset = if (isTeam) "cyber" else "bubbles",
                    accentColor = accent,
                    secondaryColor = secondary,
                    phase = phase,
                )
                drawContent()
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
// BADGE KHUSUS XYTEAM & TESTER + CLICKABLE BLUE URL LINK RENDERER (RONDE 61/70)
// ============================================================================

internal val XYTEAM_DEFAULT_LOGINS = emptySet<String>()
internal val XYTESTER_DEFAULT_LOGINS = emptySet<String>()

internal object XySpecialRoleRegistry {
    private const val ROLE_KEY_PREFIX = "staff_role_v2_"

    var roleRevision by mutableStateOf(0)
        private set

    private fun normalizeValidRoleTarget(nameOrLogin: String?): String {
        if (nameOrLogin.isNullOrBlank()) return ""
        val key = nameOrLogin.trim().removePrefix("@").lowercase(Locale.ROOT)
        if (key.isEmpty() || key.startsWith("user-") || key.startsWith("tamu-") || key == "komunitas" || key == "anonim") {
            return ""
        }
        return key
    }

    fun getRole(context: Context, nameOrLogin: String?): String {
        val key = normalizeValidRoleTarget(nameOrLogin)
        if (key.isEmpty() || key in FOUNDER_GITHUB_LOGINS) return ""
        val prefs = context.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString("$ROLE_KEY_PREFIX$key", null)?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return if (saved == "XYTEAM" || saved == "TESTER" || saved == "BOTH") saved else ""
    }

    fun setRole(context: Context, nameOrLogin: String?, role: String) {
        val key = normalizeValidRoleTarget(nameOrLogin)
        if (key.isEmpty()) return
        val cleanRole = role.trim().uppercase(Locale.ROOT)
        val edit = context.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE).edit()
        if (cleanRole == "XYTEAM" || cleanRole == "TESTER" || cleanRole == "BOTH") {
            edit.putString("$ROLE_KEY_PREFIX$key", cleanRole)
        } else {
            edit.remove("$ROLE_KEY_PREFIX$key")
        }
        edit.apply()
        roleRevision += 1
    }
}

internal fun isXyTeamAccount(context: Context, nameOrLogin: String?, githubLogin: String? = null): Boolean {
    val ghKey = githubLogin?.trim()?.removePrefix("@").orEmpty()
    val r1 = if (ghKey.isNotEmpty()) XySpecialRoleRegistry.getRole(context, ghKey) else ""
    val r2 = if (ghKey.isEmpty()) XySpecialRoleRegistry.getRole(context, nameOrLogin) else ""
    return r1 == "XYTEAM" || r1 == "BOTH" || r2 == "XYTEAM" || r2 == "BOTH"
}

internal fun isXyTesterAccount(context: Context, nameOrLogin: String?, githubLogin: String? = null): Boolean {
    val ghKey = githubLogin?.trim()?.removePrefix("@").orEmpty()
    val r1 = if (ghKey.isNotEmpty()) XySpecialRoleRegistry.getRole(context, ghKey) else ""
    val r2 = if (ghKey.isEmpty()) XySpecialRoleRegistry.getRole(context, nameOrLogin) else ""
    return r1 == "TESTER" || r1 == "BOTH" || r2 == "TESTER" || r2 == "BOTH"
}

internal fun resolveSpecialUserRole(
    nameOrLogin: String?,
    githubLogin: String? = null,
    context: Context? = null,
): String? {
    for (raw in listOf(githubLogin, nameOrLogin)) {
        if (raw.isNullOrBlank()) continue
        val key = raw.trim().removePrefix("@").lowercase(Locale.ROOT)
        if (key in FOUNDER_GITHUB_LOGINS) return "FOUNDER"
    }
    if (context != null) {
        if (isXyTeamAccount(context, nameOrLogin, githubLogin)) return "XYTEAM"
        if (isXyTesterAccount(context, nameOrLogin, githubLogin)) return "TESTER"
    }
    return null
}

/**
 * Badge Khusus "Pembuat" ala TikTok (Merah-Rose #FE2C55) untuk komentar oleh kreator postingan.
 */
@Composable
internal fun XyCreatorBadge(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF6F6F6F).copy(alpha = 0.16f))
            .border(0.75.dp, Color(0xFF6F6F6F).copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Pembuat",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF868686),
            maxLines = 1,
        )
    }
}

/**
 * Badge Khusus "XYTEAM" (Tim Inti XyVerse) — Gradasi Cyber Sapphire-Cyan + Perisai Berlapis + Bintang Inti.
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
                        Color(0xFF141414),
                        Color(0xFF4F4F4F),
                        Color(0xFF656565),
                        Color(0xFF515151),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFBEBEBE), Color(0xFF9C9C9C), Color(0xFF959595), Color(0xFFA1A1A1)),
                ),
                shape = RoundedCornerShape(50),
            )
            .padding(
                horizontal = if (compact) 6.5.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.size(if (compact) 11.dp else 13.5.dp)) {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(w * 0.50f, h * 0.06f)
                lineTo(w * 0.90f, h * 0.22f)
                lineTo(w * 0.82f, h * 0.66f)
                lineTo(w * 0.50f, h * 0.94f)
                lineTo(w * 0.18f, h * 0.66f)
                lineTo(w * 0.10f, h * 0.22f)
                close()
            }
            drawPath(
                shield,
                brush = Brush.verticalGradient(listOf(Color(0xFF9C9C9C), Color(0xFF656565))),
                style = Fill,
            )
            val star = Path().apply {
                moveTo(w * 0.50f, h * 0.22f)
                lineTo(w * 0.59f, h * 0.42f)
                lineTo(w * 0.78f, h * 0.45f)
                lineTo(w * 0.63f, h * 0.58f)
                lineTo(w * 0.67f, h * 0.76f)
                lineTo(w * 0.50f, h * 0.66f)
                lineTo(w * 0.33f, h * 0.76f)
                lineTo(w * 0.37f, h * 0.58f)
                lineTo(w * 0.22f, h * 0.45f)
                lineTo(w * 0.41f, h * 0.42f)
                close()
            }
            drawPath(star, color = Color(0xFFF7F7F7), style = Fill)
        }
        Text(
            text = if (compact) "XYTEAM" else "XYTEAM · CORE DEV",
            fontSize = if (compact) 8.5.sp else 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFF7F7F7),
            maxLines = 1,
        )
    }
}

/**
 * Badge Khusus "TESTER" (QA & Beta Tester Terverifikasi) — Gradasi Bio-Emerald Neon + Hexagon Petir Verified.
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
                        Color(0xFF222222),
                        Color(0xFF525252),
                        Color(0xFF6A6A6A),
                        Color(0xFF666666),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFBDBDBD), Color(0xFF9D9D9D), Color(0xFFA0A0A0), Color(0xFFD8D8D8)),
                ),
                shape = RoundedCornerShape(50),
            )
            .padding(
                horizontal = if (compact) 6.5.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.size(if (compact) 11.dp else 13.5.dp)) {
            val w = size.width
            val h = size.height
            val hex = Path().apply {
                moveTo(w * 0.50f, h * 0.06f)
                lineTo(w * 0.88f, h * 0.28f)
                lineTo(w * 0.88f, h * 0.72f)
                lineTo(w * 0.50f, h * 0.94f)
                lineTo(w * 0.12f, h * 0.72f)
                lineTo(w * 0.12f, h * 0.28f)
                close()
            }
            drawPath(
                hex,
                brush = Brush.verticalGradient(listOf(Color(0xFF9D9D9D), Color(0xFF666666))),
                style = Fill,
            )
            val bolt = Path().apply {
                moveTo(w * 0.56f, h * 0.18f)
                lineTo(w * 0.30f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.54f)
                lineTo(w * 0.42f, h * 0.84f)
                lineTo(w * 0.70f, h * 0.46f)
                lineTo(w * 0.50f, h * 0.46f)
                close()
            }
            drawPath(bolt, color = Color(0xFFF7F7F7), style = Fill)
        }
        Text(
            text = if (compact) "TESTER" else "TESTER · QA VERIFIED",
            fontSize = if (compact) 8.5.sp else 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFF7F7F7),
            maxLines = 1,
        )
    }
}

/**
 * Badge Prestasi Otomatis (Auto-Earned Challenge Badges) berdasarkan tantangan aktivitas nyata
 * pengguna di XyDesk Remote. Tidak ada badge gratis tanpa menyelesaikan tantangan.
 */
internal data class XyAutoAchievementSpec(
    val id: String,
    val shortLabel: String,
    val fullLabel: String,
    val bgColors: List<Color>,
    val borderColor: Color,
    val textColor: Color,
    val iconColor: Color,
)

internal data class XyBadgeChallengeItem(
    val id: String,
    val badgeTitle: String,
    val targetLabel: String,
    val progressLabel: String,
    val progressFraction: Float,
    val unlocked: Boolean,
    val rewardDesc: String,
)

internal enum class XyAnimatedAvatarStyle(
    val id: String,
    val label: String,
    val desc: String,
) {
    NONE("none", "Standar", "Tanpa animasi"),
    ORBITAL_CROWN("orbital_crown", "Orbital Crown", "Cincin ganda & mahkota berputar"),
    PULSE_RINGS("pulse_rings", "Pulse Matrix", "Gelombang energi & cincin radar"),
    STARLIGHT_AURA("starlight_aura", "Starlight Aura", "Kilau bintang & sapuan cahaya"),
    VORTEX_HALO("vortex_halo", "Vortex Halo", "Pusaran geometris berlawanan arah"),
    CUSTOM_GIF("custom_gif", "GIF / Animasi Custom", "Foto profil GIF bergerak dari galeri"),
}

internal object XyAnimatedAvatarManager {
    private const val PREFS_NAME = "xydesk_founder_style"
    private const val KEY_ANIM_STYLE = "profile_anim_avatar_style_v1"
    private const val CUSTOM_GIF_FILE = "xy_custom_avatar_anim.gif"

    var revision by mutableStateOf(0)
        private set

    fun getSelectedStyle(context: Context): XyAnimatedAvatarStyle {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ANIM_STYLE, null)
        if (!raw.isNullOrBlank()) {
            return XyAnimatedAvatarStyle.values().firstOrNull { it.id == raw } ?: XyAnimatedAvatarStyle.NONE
        }
        return if (hasCustomGif(context)) XyAnimatedAvatarStyle.CUSTOM_GIF else XyAnimatedAvatarStyle.NONE
    }

    fun setSelectedStyle(context: Context, style: XyAnimatedAvatarStyle) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ANIM_STYLE, style.id)
            .apply()
        revision++
    }

    fun customGifFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, CUSTOM_GIF_FILE)

    fun hasCustomGif(context: Context): Boolean {
        val f = customGifFile(context)
        return f.exists() && f.length() > 64L
    }

    fun saveCustomGif(context: Context, bytes: ByteArray): Boolean = runCatching {
        if (bytes.size < 64) return false
        customGifFile(context).writeBytes(bytes)
        setSelectedStyle(context, XyAnimatedAvatarStyle.CUSTOM_GIF)
        revision++
        true
    }.getOrDefault(false)

    fun clearCustomGif(context: Context) {
        runCatching { customGifFile(context).delete() }
        if (getSelectedStyle(context) == XyAnimatedAvatarStyle.CUSTOM_GIF) {
            setSelectedStyle(context, XyAnimatedAvatarStyle.ORBITAL_CROWN)
        }
        revision++
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun CustomAnimatedGifAvatarView(
    gifFile: java.io.File,
    modifier: Modifier = Modifier,
) {
    val movie = remember(gifFile.absolutePath, gifFile.lastModified(), gifFile.length()) {
        runCatching {
            val bytes = gifFile.readBytes()
            android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    val bmpFallback = remember(gifFile.absolutePath, gifFile.lastModified(), gifFile.length()) {
        runCatching {
            android.graphics.BitmapFactory.decodeFile(gifFile.absolutePath)
        }.getOrNull()
    }
    val infinite = rememberInfiniteTransition(label = "customGifLoop")
    val animProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (movie?.duration()?.takeIf { it > 100 } ?: 1800), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gifTick",
    )

    if (movie != null && movie.width() > 0 && movie.height() > 0) {
        androidx.compose.foundation.Canvas(modifier = modifier) {
            val dur = movie.duration().takeIf { it > 0 } ?: 1000
            movie.setTime((animProgress * dur).toInt() % dur)
            val mw = movie.width().toFloat()
            val mh = movie.height().toFloat()
            val scale = maxOf(size.width / mw, size.height / mh)
            val dx = (size.width - mw * scale) / 2f
            val dy = (size.height - mh * scale) / 2f
            drawContext.canvas.nativeCanvas.apply {
                save()
                translate(dx, dy)
                scale(scale, scale)
                movie.draw(this, 0f, 0f)
                restore()
            }
        }
    } else if (bmpFallback != null) {
        androidx.compose.foundation.Image(
            bitmap = bmpFallback.asImageBitmap(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier,
        )
    }
}

internal fun evaluateBadgeChallenges(
    totalSeconds: Long,
    sessionsCount: Int,
    podiumRank: Int?,
    isFounder: Boolean = false,
): List<XyBadgeChallengeItem> {
    val hoursFloat = totalSeconds / 3600f
    // Founder tetap harus menyelesaikan tantangan — tidak auto-unlock semua badge
    fun calcProgress(targetSec: Long, targetSess: Int): Float {
        val pSec = (totalSeconds.toFloat() / targetSec.toFloat()).coerceIn(0f, 1f)
        val pSess = (sessionsCount.toFloat() / targetSess.toFloat()).coerceIn(0f, 1f)
        return maxOf(pSec, pSess)
    }
    val c1 = calcProgress(3_600L, 5)
    val c2 = calcProgress(14_400L, 12)
    val c3 = calcProgress(43_200L, 25)
    val c4 = calcProgress(86_400L, 50)
    val topUnlocked = (podiumRank != null && podiumRank in 1..10)
    val topProgress = when {
        topUnlocked -> 1f
        totalSeconds > 0L -> (totalSeconds.toFloat() / 14_400f).coerceIn(0.08f, 0.85f)
        else -> 0f
    }
    // Founder badge info tetap tampil terpisah — challenge progress murni dari aktivitas

    return listOf(
        XyBadgeChallengeItem(
            id = "cloud_runner",
            badgeTitle = "CLOUD RUNNER",
            targetLabel = "1 Jam Aktif atau 5 Sesi",
            progressLabel = "${totalSeconds / 60}/60 mnt · $sessionsCount/5 sesi",
            progressFraction = c1,
            unlocked = c1 >= 1f,
            rewardDesc = "Badge Otomatis Cloud Runner",
        ),
        XyBadgeChallengeItem(
            id = "gamer_elite",
            badgeTitle = "GAMER ELITE",
            targetLabel = "4 Jam Aktif atau 12 Sesi",
            progressLabel = "${"%.1f".format(Locale.US, hoursFloat)}/4.0 jam · $sessionsCount/12 sesi",
            progressFraction = c2,
            unlocked = c2 >= 1f,
            rewardDesc = "Badge Otomatis Gamer Elite",
        ),
        XyBadgeChallengeItem(
            id = "pro_streamer",
            badgeTitle = "PRO STREAMER",
            targetLabel = "12 Jam Aktif atau 25 Sesi",
            progressLabel = "${"%.1f".format(Locale.US, hoursFloat)}/12.0 jam · $sessionsCount/25 sesi",
            progressFraction = c3,
            unlocked = c3 >= 1f,
            rewardDesc = "Badge Otomatis Pro Streamer",
        ),
        XyBadgeChallengeItem(
            id = "legend_24h",
            badgeTitle = "LEGEND 24H+",
            targetLabel = "24 Jam Aktif atau 50 Sesi",
            progressLabel = "${"%.1f".format(Locale.US, hoursFloat)}/24.0 jam · $sessionsCount/50 sesi",
            progressFraction = c4,
            unlocked = c4 >= 1f,
            rewardDesc = "Badge Tertinggi Legend 24H+",
        ),
        XyBadgeChallengeItem(
            id = "top_leaderboard",
            badgeTitle = if (podiumRank != null && podiumRank in 1..10) "TOP #$podiumRank LEADERBOARD" else "TOP 1–10 PODIUM",
            targetLabel = "Masuk Peringkat 1–10 Leaderboard",
            progressLabel = if (topUnlocked) "Aktif (Rank #${podiumRank ?: 1})" else "Belum masuk Top 10",
            progressFraction = topProgress,
            unlocked = topUnlocked,
            rewardDesc = "Foto Profil Animasi Custom (GIF & Aura) + Bingkai Podium + Bubble Top Rank",
        ),
    )
}

internal fun resolveAutoAchievementBadges(
    totalSeconds: Long,
    sessionsCount: Int,
    hasVerifiedGithub: Boolean,
): List<XyAutoAchievementSpec> {
    val list = ArrayList<XyAutoAchievementSpec>(2)
    if (totalSeconds >= 86_400L || sessionsCount >= 50) {
        list.add(
            XyAutoAchievementSpec(
                id = "legend_24h",
                shortLabel = "LEGEND 24H+",
                fullLabel = "LEGEND MARATHON · 24+ JAM AKTIF",
                bgColors = listOf(Color(0xFF1C1C1C), Color(0xFF414141), Color(0xFF878787)),
                borderColor = Color(0xFFD7D7D7),
                textColor = Color(0xFFE9E9E9),
                iconColor = Color(0xFFBFBFBF),
            ),
        )
    } else if (totalSeconds >= 43_200L || sessionsCount >= 25) {
        list.add(
            XyAutoAchievementSpec(
                id = "pro_streamer",
                shortLabel = "PRO STREAMER",
                fullLabel = "PRO STREAMER · 12+ JAM REMOTE",
                bgColors = listOf(Color(0xFF262626), Color(0xFF515151), Color(0xFF656565)),
                borderColor = Color(0xFF9C9C9C),
                textColor = Color(0xFFEEEEEE),
                iconColor = Color(0xFFBEBEBE),
            ),
        )
    } else if (totalSeconds >= 14_400L || sessionsCount >= 12) {
        list.add(
            XyAutoAchievementSpec(
                id = "gamer_elite",
                shortLabel = "GAMER ELITE",
                fullLabel = "GAMER ELITE · 4+ JAM REMOTE",
                bgColors = listOf(Color(0xFF212121), Color(0xFF3F3F3F), Color(0xFF5B5B5B)),
                borderColor = Color(0xFFB8B8B8),
                textColor = Color(0xFFE8E8E8),
                iconColor = Color(0xFFD4D4D4),
            ),
        )
    } else if (totalSeconds >= 3_600L || sessionsCount >= 5) {
        list.add(
            XyAutoAchievementSpec(
                id = "cloud_runner",
                shortLabel = "CLOUD RUNNER",
                fullLabel = "CLOUD RUNNER · 1+ JAM AKTIF",
                bgColors = listOf(Color(0xFF363636), Color(0xFF525252), Color(0xFF6A6A6A)),
                borderColor = Color(0xFFBDBDBD),
                textColor = Color(0xFFEBEBEB),
                iconColor = Color(0xFF9D9D9D),
            ),
        )
    }
    return list
}

@Composable
internal fun XyAutoAchievementBadge(
    spec: XyAutoAchievementSpec,
    compact: Boolean = true,
) {
    val pillShape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(Brush.horizontalGradient(spec.bgColors), pillShape)
            .border(0.8.dp, spec.borderColor, pillShape)
            .padding(
                horizontal = if (compact) 6.dp else 9.dp,
                vertical = if (compact) 1.8.dp else 3.5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
    ) {
        Canvas(modifier = Modifier.size(if (compact) 9.5.dp else 12.dp)) {
            val w = size.width
            val h = size.height
            val diamond = Path().apply {
                moveTo(w * 0.50f, h * 0.08f)
                lineTo(w * 0.92f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.92f)
                lineTo(w * 0.08f, h * 0.50f)
                close()
            }
            drawPath(diamond, color = spec.iconColor, style = Fill)
        }
        Text(
            text = if (compact) spec.shortLabel else spec.fullLabel,
            fontSize = if (compact) 8.sp else 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = spec.textColor,
            maxLines = 1,
        )
    }
}

/**
 * Menampilkan deretan badge peran khusus & prestasi otomatis pengguna:
 * - FOUNDER (Ungu Royal @xykal)
 * - XYTEAM & TESTER
 * - Top 1–10 Leaderboard (`TOP #1` s/d `TOP #10`)
 * - Badge Prestasi Otomatis (`LEGEND 24H+`, `PRO STREAMER`, `GAMER ELITE`, `PIONEER`)
 */
@Composable
internal fun XyUserRoleBadges(
    name: String?,
    githubLogin: String? = null,
    compact: Boolean = true,
    durationRank: Int? = null,
) {
    val topTenRanks by LeaderboardPodium.topTen.collectAsState()
    val byIdentity by LeaderboardPodium.byIdentity.collectAsState()
    val normGh = normalizedChatAccount(githubLogin)
    val normName = normalizedChatAccount(name)
    val podiumRank = durationRank?.takeIf { it in 1..10 }
        ?: (if (normGh.isNotEmpty()) topTenRanks[normGh] else null)
        ?: (if (normName.isNotEmpty()) topTenRanks[normName] else null)
    val lbEntry = (if (normGh.isNotEmpty()) byIdentity[normGh] else null)
        ?: (if (normName.isNotEmpty()) byIdentity[normName] else null)

    val context = LocalContext.current
    val rev = XySpecialRoleRegistry.roleRevision
    val isFounder = remember(name, githubLogin) {
        isFounderAccount(githubLogin) || isFounderAccount(name)
    }
    val isTeam = remember(name, githubLogin, rev) {
        !isFounder && isXyTeamAccount(context, name, githubLogin)
    }
    val isTester = remember(name, githubLogin, rev) {
        !isFounder && isXyTesterAccount(context, name, githubLogin)
    }
    val autoBadges = remember(lbEntry, normGh, isFounder) {
        if (isFounder) emptyList()
        else resolveAutoAchievementBadges(
            totalSeconds = lbEntry?.totalSeconds ?: 0L,
            sessionsCount = lbEntry?.sessionsCount ?: 0,
            hasVerifiedGithub = normGh.isNotEmpty(),
        )
    }
    if (!isFounder && !isTeam && !isTester && podiumRank == null && autoBadges.isEmpty()) return
    if (compact) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isFounder) {
                XyFounderBadge(compact = true)
            } else {
                if (isTeam) XyTeamBadge(compact = true)
                if (isTester) XyTesterBadge(compact = true)
                if (podiumRank != null) LeaderboardDurationBadge(podiumRank, compact = true)
                autoBadges.firstOrNull()?.let { badge ->
                    XyAutoAchievementBadge(spec = badge, compact = true)
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (isFounder || isTeam || isTester) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isFounder) XyFounderBadge(compact = false)
                    if (isTeam) XyTeamBadge(compact = false)
                    if (isTester) XyTesterBadge(compact = false)
                }
            }
            if (podiumRank != null) LeaderboardDurationBadge(podiumRank, compact = false)
            autoBadges.forEach { badge ->
                XyAutoAchievementBadge(spec = badge, compact = false)
            }
        }
    }
}

private val XY_URL_REGEX = Regex(
    """((https?://|www\.)[^\s<>"]+|[a-zA-Z0-9.-]+\.(id|com|org|net|io|dev|app|me|xyz|co)(/[^\s<>"]*)?)""",
    RegexOption.IGNORE_CASE,
)

private val MENTION_REGEX = Regex("@[\\p{L}][\\p{L}\\p{N}_.-]*")

/**
 * Komponen Teks Pintar yang membuat SEMUA link/URL berwarna BIRU CERAH + bergaris bawah
 * dan langsung BISA DIKLIK (membuka In-App Browser Chrome Custom Tab), sementara klik/tekan
 * pada bagian teks biasa tetap meneruskan aksi normal/long-press.
 */
@Composable
internal fun XyClickableLinkText(
    text: String,
    color: Color,
    linkColor: Color = Color(0xFF9C9C9C),
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    onNonLinkClick: (() -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val openChannel = LocalChannelNavigator.current
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
            channelTags(text).forEach { match ->
                if (XY_URL_REGEX.findAll(text).none { match.start in it.range || (match.end - 1) in it.range }) {
                    addStyle(androidx.compose.ui.text.SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), match.start, match.end)
                    addStringAnnotation("CHANNEL", match.channelId, match.start, match.end)
                }
            }
            MENTION_REGEX.findAll(text).forEach { match ->
                val mention = match.value
                if (XY_URL_REGEX.findAll(text).none { urlMatch -> match.range.first in urlMatch.range || match.range.last in urlMatch.range }) {
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = Color(0xFF60A5FA),
                            fontWeight = FontWeight.SemiBold,
                        ),
                        match.range.first,
                        match.range.last + 1,
                    )
                    addStringAnnotation("MENTION", mention.removePrefix("@"), match.range.first, match.range.last + 1)
                }
            }
        }
    }

    val hasLinks = remember(annotated) {
        annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty()
            || annotated.getStringAnnotations("CHANNEL", 0, annotated.length).isNotEmpty()
            || annotated.getStringAnnotations("MENTION", 0, annotated.length).isNotEmpty()
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
            modifier = modifier.pointerInput(annotated, onNonLinkClick, onLongPress, openChannel) {
                detectTapGestures(
                    onLongPress = { onLongPress?.invoke() },
                    onTap = { pos ->
                        val layout = layoutResult
                        if (layout != null) {
                            val offset = layout.getOffsetForPosition(pos)
                            val channel = annotated.getStringAnnotations("CHANNEL", offset, offset).firstOrNull()
                            if (channel != null) {
                                openChannel(channel.item)
                                return@detectTapGestures
                            }
                            val hit = annotated.getStringAnnotations("URL", offset, offset).firstOrNull()
                            if (hit != null) {
                                openInAppCustomTab(context, hit.item)
                                return@detectTapGestures
                            }
                            val mention = annotated.getStringAnnotations("MENTION", offset, offset).firstOrNull()
                            if (mention != null) {
                                onMentionClick?.invoke(mention.item)
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

