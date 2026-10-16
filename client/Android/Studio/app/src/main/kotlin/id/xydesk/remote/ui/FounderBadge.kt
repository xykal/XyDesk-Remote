package id.xydesk.remote.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Ronde 48 — Identitas & Badge Khusus Founder (`xykal`).
 *
 * Ditampilkan otomatis di:
 * - Kartu Akun GitHub di layar Buat RDP (`RdpFreeScreen`)
 * - Kartu Profil & Akun Komunitas (`XyProfileScreen`)
 * - Setiap pesan & header di Chat Global (`GlobalChatScreen`)
 * - Setiap postingan & komentar di Feed Komunitas (`FunHubCard`)
 */

private const val FOUNDER_PREFS = "xydesk.founder.identity"
private const val KEY_GH_LOGIN = "verified_github_login"

internal val FOUNDER_GITHUB_LOGINS = setOf("xykal")

internal fun isFounderAccount(nameOrLogin: String?): Boolean {
    if (nameOrLogin.isNullOrBlank()) return false
    val normalized = nameOrLogin.trim().removePrefix("@").lowercase(Locale.ROOT)
    return normalized in FOUNDER_GITHUB_LOGINS ||
        normalized == "xykal (founder)" ||
        normalized.startsWith("xykal ")
}

internal fun saveVerifiedGitHubLogin(context: Context, login: String?) {
    val clean = login?.trim().orEmpty()
    context.applicationContext
        .getSharedPreferences(FOUNDER_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GH_LOGIN, clean)
        .apply()

    // Jika yang login adalah Founder (xykal), sinkronkan juga nickname Chat Global
    // supaya saat buka Chat Global langsung memakai identitas Founder.
    if (isFounderAccount(clean)) {
        val chatPrefs = context.applicationContext.getSharedPreferences("xydesk.global.chat", Context.MODE_PRIVATE)
        val currentNick = chatPrefs.getString("nickname", null)
        if (currentNick.isNullOrBlank() || currentNick.startsWith("Tamu-")) {
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

internal fun founderBorderBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFE082),
        Color(0xFFFFB300),
        Color(0xFFFF6F00),
        Color(0xFFFFD54F),
    ),
)

internal fun founderBgBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF3E2723).copy(alpha = 0.92f),
        Color(0xFF4E342E).copy(alpha = 0.92f),
        Color(0xFF311B92).copy(alpha = 0.88f),
    ),
)

/**
 * Badge visual "👑 FOUNDER" bergaya emas-royal dengan border berkilau.
 */
@Composable
internal fun XyFounderBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(if (compact) 50 else 10)
    Row(
        modifier = modifier
            .clip(shape)
            .background(founderBgBrush(), shape)
            .border(width = 1.dp, brush = founderBorderBrush(), shape = shape)
            .padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (subtitle != null && !compact) "👑 FOUNDER · $subtitle" else "👑 FOUNDER",
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (compact) 9.5.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = Color(0xFFFFE082),
        )
    }
}
