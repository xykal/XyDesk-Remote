package id.xydesk.remote.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Ronde 48–52 — Identitas & Badge Khusus Founder (`xykal`) + Ikon Vektor GitHub.
 *
 * Didesain ulang di Ronde 52:
 * - Tidak lagi memakai emoji `👑` atau warna cokelat tua; kini memakai emblem
 *   vektor **Royal Crown + Verified Shield** yang digambar di atas `Canvas`
 *   dengan palet **Midnight Obsidian (`#0B1120` → `#1E1B4B`) + Champagne Gold
 *   (`#FDE68A` → `#F59E0B`)**.
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
    val edit = appCtx.getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_GH_LOGIN, clean)
    if (avatarUrl != null) {
        edit.putString(KEY_GH_AVATAR, avatarUrl.trim())
    }
    edit.apply()

    // Sinkronkan nickname Chat Global & nama profil bila pengguna baru login GitHub
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
    return stored?.takeIf { it.isNotEmpty() }
}

internal fun founderBorderBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFDE68A),
        Color(0xFFF59E0B),
        Color(0xFFFEF08A),
        Color(0xFFD97706),
    ),
)

internal fun founderBgBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF0B1120),
        Color(0xFF172554),
        Color(0xFF1E1B4B),
    ),
)

/**
 * Emblem mahkota vektor emas presisi (tanpa emoji OS).
 */
@Composable
internal fun FounderCrownEmblem(
    size: Dp = 13.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val goldBrush = Brush.linearGradient(
            colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFFBBF24)),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )
        val crown = Path().apply {
            moveTo(w * 0.14f, h * 0.76f)
            lineTo(w * 0.08f, h * 0.30f)
            lineTo(w * 0.33f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.67f, h * 0.52f)
            lineTo(w * 0.92f, h * 0.30f)
            lineTo(w * 0.86f, h * 0.76f)
            close()
        }
        drawPath(path = crown, brush = goldBrush, style = Fill)
        // Pita dasar mahkota
        drawLine(
            color = Color(0xFFFEF3C7),
            start = Offset(w * 0.14f, h * 0.86f),
            end = Offset(w * 0.86f, h * 0.86f),
            strokeWidth = h * 0.11f,
            cap = StrokeCap.Round,
        )
        // Permata puncak
        drawCircle(color = Color(0xFFFEF9C3), radius = w * 0.065f, center = Offset(w * 0.50f, h * 0.14f))
        drawCircle(color = Color(0xFFFDE68A), radius = w * 0.055f, center = Offset(w * 0.08f, h * 0.28f))
        drawCircle(color = Color(0xFFFDE68A), radius = w * 0.055f, center = Offset(w * 0.92f, h * 0.28f))
    }
}

/**
 * Badge visual "FOUNDER" bergaya Obsidian-Gold modern dengan ikon mahkota vektor
 * dan tanda centang terverifikasi.
 */
@Composable
internal fun XyFounderBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(founderBgBrush(), shape)
            .border(width = if (compact) 1.dp else 1.25.dp, brush = founderBorderBrush(), shape = shape)
            .padding(
                start = if (compact) 4.dp else 6.dp,
                end = if (compact) 7.dp else 10.dp,
                top = if (compact) 2.dp else 3.5.dp,
                bottom = if (compact) 2.dp else 3.5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 15.dp else 18.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF59E0B).copy(alpha = 0.32f),
                            Color(0xFF78350F).copy(alpha = 0.20f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            FounderCrownEmblem(size = if (compact) 10.5.dp else 12.5.dp)
        }
        Text(
            text = if (subtitle != null && !compact) "FOUNDER · $subtitle" else "FOUNDER",
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (compact) 9.sp else 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp,
            color = Color(0xFFFEF3C7),
        )
    }
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
        // Kepala + telinga Octocat
        val head = Path().apply {
            // Telinga kiri
            moveTo(w * 0.24f, h * 0.36f)
            lineTo(w * 0.20f, h * 0.16f)
            lineTo(w * 0.37f, h * 0.25f)
            // Dahi
            cubicTo(w * 0.45f, h * 0.22f, w * 0.55f, h * 0.22f, w * 0.63f, h * 0.25f)
            // Telinga kanan
            lineTo(w * 0.80f, h * 0.16f)
            lineTo(w * 0.76f, h * 0.36f)
            // Pipi kanan
            cubicTo(w * 0.86f, h * 0.46f, w * 0.85f, h * 0.64f, w * 0.68f, h * 0.71f)
            // Badan bawah
            cubicTo(w * 0.73f, h * 0.76f, w * 0.73f, h * 0.86f, w * 0.73f, h * 0.92f)
            lineTo(w * 0.34f, h * 0.92f)
            cubicTo(w * 0.34f, h * 0.86f, w * 0.34f, h * 0.76f, w * 0.39f, h * 0.71f)
            // Pipi kiri
            cubicTo(w * 0.15f, h * 0.64f, w * 0.14f, h * 0.46f, w * 0.24f, h * 0.36f)
            close()
        }
        drawPath(path = head, color = tint, style = Fill)
        // Ekor melengkung di kiri bawah
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
