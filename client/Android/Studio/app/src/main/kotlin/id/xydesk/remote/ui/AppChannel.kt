package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.xyGlass
import java.security.MessageDigest

/**
 * Kanal distribusi build.
 *
 * Memastikan APK yang berjalan ditandatangani penerbit resmi. Kalau tidak,
 * [AppChannelGate] menutup aplikasi dan mengarahkan ke situs resmi.
 *
 * Aturan aman yang dipegang fungsi ini: **hanya memblokir kalau sidik jari
 * benar-benar terbaca dan berbeda**. Kalau sidik jari tidak bisa dibaca sama
 * sekali (ROM aneh, API berubah, permission dibatasi), aplikasi tetap jalan —
 * pengguna sah tidak boleh terkunci karena pembacaan gagal.
 */
internal object AppChannel {

    /** Sumber unduhan resmi. */
    const val OFFICIAL_URL = "https://rdp.xydesk.my.id/"

    /**
     * True kalau build ini resmi (atau kalau statusnya tidak bisa dipastikan).
     *
     * Build debug dikecualikan supaya pengembangan lokal dan job CI `assembleDebug`
     * tidak ikut terkunci; pengecualiannya dibaca dari APK yang sedang berjalan,
     * bukan dari konstanta waktu kompilasi.
     */
    fun official(context: Context): Boolean {
        if (isDebuggable(context)) return true
        val actual = signerFingerprint(context) ?: return true
        if (actual.size != SignerMark.MARK.size) return true
        // Pembanding murni (byte dibaca tanpa tanda) — lihat SignerMark.
        return SignerMark.matches(actual)
    }

    private fun isDebuggable(context: Context): Boolean = runCatching {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(true)

    /** SHA-256 DER sertifikat penanda tangan, atau null bila tidak terbaca. */
    private fun signerFingerprint(context: Context): ByteArray? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return@runCatching null
            val signers = runCatching { signing.apkContentsSigners }.getOrNull()
                ?: runCatching { signing.signingCertificateHistory }.getOrNull()
            signers?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        }
        signature?.toByteArray()?.let(::sha256)
    }.getOrNull()

    private fun sha256(input: ByteArray): ByteArray? = runCatching {
        MessageDigest.getInstance("SHA-256").digest(input)
    }.getOrNull()
}

/**
 * Penutup aplikasi untuk build yang tidak resmi.
 *
 * Digambar paling atas dan menyerap semua sentuhan, jadi tidak ada jalan masuk
 * ke layar mana pun. Satu-satunya aksi yang tersedia adalah membuka situs
 * resmi atau menutup aplikasi.
 */
@Composable
internal fun AppChannelGate(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val blocked = remember { !AppChannel.official(context) }
    if (!blocked) return

    // Tombol kembali ditelan: kalau tidak, satu ketukan kembali melewati
    // penutup ini dan masuk ke layar di bawahnya.
    BackHandler { }

    Box(
        modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(XyGateBackground)
            // Menyerap seluruh sentuhan, termasuk yang jatuh di luar kartu.
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .xyGlass(shape = RoundedCornerShape(22.dp), opacity = 1.5f, strength = 1f)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = XyIcons.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(34.dp),
            )
            Text(
                xy("APLIKASI GRATIS KOK DI BAJAK ISH ISH", "IT'S A FREE APP AND IT'S STILL PIRATED"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp,
            )
            Text(
                xy(
                    "JANGAN GITU ATUH. Build ini bukan keluaran resmi XyDesk — sudah gratis, dibajak pula. Padahal tinggal unduh yang asli. Sekalian lebih aman: build modifikasi bisa ikut membawa pulang password PC-mu.",
                    "COME ON NOW. This build is not an official XyDesk release — it is already free, and it still got pirated. Just download the real one. It is also safer: a modified build can walk away with your PC password.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            XyPillButton(
                text = xy("Unduh yang resmi", "Download the official app"),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AppChannel.OFFICIAL_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                icon = XyIcons.Download,
                modifier = Modifier.fillMaxWidth(),
            )
            XyPillButton(
                text = xy("Tutup aplikasi", "Close app"),
                onClick = onExit,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Latar penutup. Tidak transparan supaya layar di bawahnya tidak terbaca. */
private val XyGateBackground = androidx.compose.ui.graphics.Color(0xFF08080A)
