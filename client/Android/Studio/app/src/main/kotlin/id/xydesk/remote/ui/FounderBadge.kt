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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.rdp.XyRdpClient
import id.xydesk.remote.ui.rdp.XyRdpConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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

/**
 * Membuka URL di dalam In-App Browser (Chrome Custom Tabs) yang memiliki tombol tutup `X`
 * dan menu titik tiga `⋮` di pojok kanan atas serta berbagi sesi login dengan browser utama.
 */
internal fun openInAppCustomTab(context: Context, url: String) {
    runCatching {
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

/**
 * Alur Login GitHub 1-Klik via In-App Browser (Chrome Custom Tab):
 * 1. Meminta sesi OAuth GitHub secara otomatis di latar belakang.
 * 2. Menyalin kode verifikasi ke clipboard + langsung membuka In-App Browser (dengan titik tiga `⋮`)
 *    ke halaman otorisasi GitHub sehingga pengguna tinggal klik Continue / Authorize.
 * 3. Melakukan polling otomatis sampai pengguna selesai menekan Authorize, lalu menyimpan token,
 *    username `@github`, dan foto profil GitHub.
 */
internal suspend fun performGitHubInAppBrowserLogin(
    context: Context,
    scope: String = "repo workflow read:user",
    onCodeReady: (userCode: String, verifyUrl: String) -> Unit = { _, _ -> },
    onSuccess: (token: String, login: String) -> Unit = { _, _ -> },
    onError: (String) -> Unit = {},
) {
    val appCtx = context.applicationContext
    try {
        val start = githubDeviceStart(GITHUB_OAUTH_CLIENT_ID)
        if (start == null) {
            withContext(Dispatchers.Main) {
                onError(xyNow("Gagal memulai sesi login GitHub.", "Failed to start GitHub login session."))
            }
            return
        }
        val userCode = start.optString("user_code", "")
        val verificationUri = start.optString("verification_uri", "https://github.com/login/device")
        val deviceCode = start.optString("device_code", "")
        val intervalSec = start.optInt("interval", 5).coerceIn(3, 15)
        val expiresInSec = start.optInt("expires_in", 900)
        val targetUrl = if (verificationUri.contains("?")) {
            "$verificationUri&user_code=${Uri.encode(userCode)}"
        } else {
            "$verificationUri?user_code=${Uri.encode(userCode)}"
        }

        withContext(Dispatchers.Main) {
            runCatching {
                val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("GitHub Authorize Code", userCode))
            }
            onCodeReady(userCode, targetUrl)
            Toast.makeText(
                appCtx,
                xyNow(
                    "Kode $userCode disalin otomatis. Tinggal tempel/klik Authorize di browser!",
                    "Code $userCode copied automatically. Just paste/tap Authorize in browser!",
                ),
                Toast.LENGTH_LONG,
            ).show()
            openInAppCustomTab(context, targetUrl)
        }

        var waitSec = intervalSec.coerceAtLeast(4)
        val deadline = System.currentTimeMillis() + (expiresInSec * 1000L)
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            delay(waitSec * 1000L)
            val poll = githubDevicePoll(GITHUB_OAUTH_CLIENT_ID, deviceCode) ?: continue
            val token = poll.optString("access_token", "")
            val err = poll.optString("error", "")
            when {
                token.isNotBlank() -> {
                    val user = XyRdpClient.whoami(token)
                    if (!user.isNullOrBlank()) {
                        CredentialVault(appCtx).put(XyRdpConfig.VAULT_PAT_ID, token)
                        val avatarUrl = "https://github.com/$user.png?size=160"
                        saveVerifiedGitHubLogin(appCtx, user, avatarUrl)
                        runCatching {
                            val api = CommunityJokesApi(appCtx)
                            if (api.displayName().isNullOrBlank() || isFounderAccount(user)) {
                                api.setDisplayName(user)
                            }
                            syncGitHubAvatarIfNeeded(appCtx, user, api)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                appCtx,
                                xyNow("Berhasil terhubung sebagai @$user!", "Connected as @$user!"),
                                Toast.LENGTH_SHORT,
                            ).show()
                            onSuccess(token, user)
                        }
                        return
                    }
                }
                err == "slow_down" -> waitSec += 5
                err == "authorization_pending" || err.isBlank() -> Unit
                else -> {
                    withContext(Dispatchers.Main) {
                        onError(
                            xyNow(
                                "Otorisasi GitHub dibatalkan atau kedaluwarsa.",
                                "GitHub authorization cancelled or expired.",
                            ),
                        )
                    }
                    return
                }
            }
        }
        withContext(Dispatchers.Main) {
            onError(xyNow("Waktu otorisasi GitHub habis. Silakan coba lagi.", "GitHub authorization timed out. Please try again."))
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        withContext(Dispatchers.Main) {
            onError(t.message ?: xyNow("Gagal menghubungkan ke GitHub.", "Failed to connect to GitHub."))
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
            Color(0xFFFFFBEB),
            Color(0xFFFDE047),
            Color(0xFFF59E0B),
            Color(0xFFE11D48),
            Color(0xFFC084FC),
            Color(0xFFFBBF24),
            Color(0xFFFFFBEB),
        ),
        start = Offset(shift, 0f),
        end = Offset(shift + 240f, 180f),
    )
}

internal fun founderBgBrush(phase: Float = 0f): Brush {
    val offset = phase * 220f
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF09080F),
            Color(0xFF1A102B),
            Color(0xFF2B1610),
            Color(0xFF130D24),
            Color(0xFF09080F),
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + 280f, 120f),
    )
}

/**
 * Emblem Mahkota Bersayap Sovereign (Sovereign Winged Crown + Orbiting Celestial Sparks)
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

        // Halo aura emas berdenyut di belakang mahkota
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFBBF24).copy(alpha = 0.42f * pulse.coerceIn(0.8f, 1.15f)),
                    Color(0xFFE11D48).copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = w * 0.54f * pulse,
            ),
            radius = w * 0.54f * pulse,
            center = Offset(cx, cy),
        )

        val goldBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFBEB),
                Color(0xFFFDE047),
                Color(0xFFF59E0B),
                Color(0xFFD97706),
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
        drawPath(path = leftWing, brush = goldBrush, style = Fill)
        drawPath(path = rightWing, brush = goldBrush, style = Fill)

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
        drawPath(path = crown, brush = goldBrush, style = Fill)
        drawPath(
            path = crown,
            color = Color(0xFFFFFBEB).copy(alpha = 0.85f),
            style = Stroke(width = w * 0.045f, join = StrokeJoin.Round),
        )

        // Permata Ruby di tengah mahkota
        drawCircle(
            color = Color(0xFFF43F5E),
            radius = w * 0.075f,
            center = Offset(w * 0.50f, h * 0.56f),
        )
        drawCircle(
            color = Color(0xFFFFFBEB),
            radius = w * 0.032f,
            center = Offset(w * 0.48f, h * 0.54f),
        )

        // Pita dasar mahkota
        drawLine(
            brush = goldBrush,
            start = Offset(w * 0.17f, h * 0.86f),
            end = Offset(w * 0.83f, h * 0.86f),
            strokeWidth = h * 0.10f,
            cap = StrokeCap.Round,
        )

        // Permata puncak + partikel bintang mengorbit
        drawCircle(color = Color(0xFFFFFBEB), radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.13f))
        drawCircle(color = Color(0xFFFDE047), radius = w * 0.055f, center = Offset(w * 0.13f, h * 0.30f))
        drawCircle(color = Color(0xFFFDE047), radius = w * 0.055f, center = Offset(w * 0.87f, h * 0.30f))

        val sx = cx + cos(orbit) * (w * 0.44f)
        val sy = cy + sin(orbit) * (h * 0.38f)
        drawCircle(color = Color(0xFFFFFBEB), radius = w * 0.048f, center = Offset(sx, sy))
    }
}

/**
 * Badge visual "SOVEREIGN FOUNDER" beranimasi penuh (Liquid Gold Shimmer + Celestial Sparks +
 * Crest Mahkota Bersayap). Tidak generik dan langsung menonjolkan pemilik utama (`@xykal`).
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
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_sweep",
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
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
                width = if (compact) 1.2.dp else 1.5.dp,
                brush = founderBorderBrush(shimmer),
                shape = shape,
            )
            .drawWithContent {
                drawContent()
                // Sapuan kilau cahaya diagonal (specular sweep)
                val sweepX = (shimmer * 1.6f - 0.3f) * size.width
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFFBEB).copy(alpha = 0.26f),
                            Color(0xFFFDE047).copy(alpha = 0.14f),
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
                            Color(0xFFF59E0B).copy(alpha = 0.42f * glowAlpha),
                            Color(0xFFE11D48).copy(alpha = 0.24f),
                            Color(0xFF1E1B4B).copy(alpha = 0.35f),
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
            color = Color(0xFFFFFBEB),
        )
        // Bintang vektor kilau di sisi kanan badge
        Canvas(modifier = Modifier.size(if (compact) 8.dp else 10.dp)) {
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
            drawPath(star, color = Color(0xFFFDE047))
        }
    }
}

/**
 * Kontainer Bubble Chat Epik Beranimasi Khusus Founder (`@xykal`).
 * Menampilkan inti Royal Obsidian-Crimson dengan tepi sapuan emas bergerak (Liquid Gold Plasma),
 * partikel bintang halus di latar bubble, dan kilau cahaya diagonal real-time.
 */
@Composable
internal fun FounderAnimatedBubbleBox(
    shape: Shape,
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
                        Color(0xFF0D0A14),
                        Color(0xFF191124),
                        Color(0xFF231612),
                        Color(0xFF120E1E),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 1.35.dp,
                brush = founderBorderBrush(phase),
                shape = shape,
            )
            .drawWithContent {
                // Partikel nebula & bintang emas halus di dalam bubble Founder
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF59E0B).copy(alpha = 0.14f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.85f, h * 0.22f),
                        radius = w * 0.45f,
                    ),
                    radius = w * 0.45f,
                    center = Offset(w * 0.85f, h * 0.22f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE11D48).copy(alpha = 0.11f * pulse),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.18f, h * 0.80f),
                        radius = w * 0.40f,
                    ),
                    radius = w * 0.40f,
                    center = Offset(w * 0.18f, h * 0.80f),
                )
                // Titik bintang emas berdenyut
                val spark1X = w * (0.15f + 0.70f * phase)
                val spark2X = w * (0.85f - 0.65f * phase)
                drawCircle(
                    color = Color(0xFFFDE047).copy(alpha = 0.35f * pulse),
                    radius = 2.2f,
                    center = Offset(spark1X, h * 0.24f),
                )
                drawCircle(
                    color = Color(0xFFFFFBEB).copy(alpha = 0.28f * pulse),
                    radius = 1.8f,
                    center = Offset(spark2X, h * 0.76f),
                )

                drawContent()

                // Sapuan kilau diagonal tipis
                val sweepX = (phase * 1.5f - 0.25f) * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFFBEB).copy(alpha = 0.10f),
                            Color(0xFFFDE047).copy(alpha = 0.06f),
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
