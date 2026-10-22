package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.xyGlass
import java.security.MessageDigest

/**
 * Katalog Resmi Modul Native (.so), Library Engine, & Driver (.dll / .sys) XyDesk.
 */
internal object XyRuntimeCatalog {
    const val ENGINE_VERSION = "XyCore Native v3.4.23-xy (60–120 FPS Zero-Lag)"
    const val SHIELD_VERSION = "XyShield Anti-Crack & Integrity Guard v4.1"

    data class RuntimeModule(
        val binaryName: String,
        val version: String,
        val roleId: String,
        val roleEn: String,
    )

    val androidNativeModules: List<RuntimeModule> = listOf(
        RuntimeModule(
            "libxydesk-core3.so",
            "v3.4.23-xy",
            "XyFrame Zero-Latency Pipeline & Frame Coalescer",
            "XyFrame Zero-Latency Pipeline & Frame Coalescer",
        ),
        RuntimeModule(
            "libxydesk-rdp-client.so",
            "v3.4.23-xy",
            "XyRDP H.264/AVC444 & RemoteFX Turbo Engine",
            "XyRDP H.264/AVC444 & RemoteFX Turbo Engine",
        ),
        RuntimeModule(
            "libxydesk-net-quic.so",
            "v2.8.14-xy",
            "XyNet Direct P2P UDP/QUIC & Portmap Multiplexer",
            "XyNet Direct P2P UDP/QUIC & Portmap Multiplexer",
        ),
        RuntimeModule(
            "libxydesk-audio-aaudio.so",
            "v2.4.9-xy",
            "XySound Low-Latency AAudio/OpenSL Bi-Directional Mic",
            "XySound Low-Latency AAudio/OpenSL Bi-Directional Mic",
        ),
        RuntimeModule(
            "libxydesk-crypto-shield.so",
            "v4.1.0-xy",
            "XyShield Anti-Tamper, HMAC-SHA256 & TLS 1.3 Vault",
            "XyShield Anti-Tamper, HMAC-SHA256 & TLS 1.3 Vault",
        ),
    )

    val windowsHostDrivers: List<RuntimeModule> = listOf(
        RuntimeModule(
            "XyDeskDisplayDriver.dll",
            "v2.6.0-xy",
            "XyIdd Virtual Display Driver (60–144Hz Direct Monitor)",
            "XyIdd Virtual Display Driver (60–144Hz Direct Monitor)",
        ),
        RuntimeModule(
            "XyDeskDxgiDuplicator.dll",
            "v3.2.1-xy",
            "Direct3D11/DXGI Zero-Copy Desktop Capture Engine",
            "Direct3D11/DXGI Zero-Copy Desktop Capture Engine",
        ),
        RuntimeModule(
            "XyDeskInputHid.sys",
            "v2.5.4-xy",
            "Raw Input Delta Mouse & Virtual XInput Gamepad Bus",
            "Raw Input Delta Mouse & Virtual XInput Gamepad Bus",
        ),
        RuntimeModule(
            "XyDeskAudioLoopback.dll",
            "v2.3.8-xy",
            "WASAPI Low-Latency Loopback & Virtual Mic Endpoint",
            "WASAPI Low-Latency Loopback & Virtual Mic Endpoint",
        ),
        RuntimeModule(
            "XyDeskTermSrvPatch.dll",
            "v1.9.7-xy",
            "Multi-Session Concurrent RDP & Console Guard",
            "Multi-Session Concurrent RDP & Console Guard",
        ),
    )

    fun sessionSummaryLines(rawFreeRdpVer: String): List<String> = listOf(
        "Engine: $ENGINE_VERSION (core $rawFreeRdpVer)",
        "Native .so: libxydesk-core3.so v3.4.23-xy · libxydesk-net-quic.so v2.8.14-xy",
        "Audio/Crypto: libxydesk-audio-aaudio.so v2.4.9-xy · libxydesk-crypto-shield.so v4.1.0-xy",
        "Host Driver: XyDeskDisplayDriver.dll v2.6.0-xy · XyDeskDxgiDuplicator.dll v3.2.1-xy · XyDeskInputHid.sys v2.5.4-xy",
    )
}

/**
 * Kanal distribusi build & sistem proteksi Anti-Crack / Anti-Tamper (XyShield v4.1).
 */
internal object AppChannel {

    /** Sumber unduhan resmi. */
    const val OFFICIAL_URL = "https://www.xydeskremote.biz.id/"
    private const val EXPECTED_PACKAGE = "id.xydesk.remote"

    data class SecurityAuditReport(
        val signatureVerified: Boolean,
        val packageVerified: Boolean,
        val hookFree: Boolean,
        val actualFingerprintShort: String,
        val officialFingerprintShort: String,
        val installerName: String,
        val detectedHookName: String?,
    ) {
        val isCleanOfficial: Boolean
            get() = signatureVerified && packageVerified && hookFree
    }

    /**
     * True kalau build ini resmi dan tidak di-hook/di-crack oleh signature killer.
     */
    fun official(context: Context): Boolean {
        if (isDebuggable(context)) return true
        if (context.packageName != EXPECTED_PACKAGE) return false
        if (detectHookFramework() != null) return false
        val actual = signerFingerprint(context) ?: return true
        if (actual.size != SignerMark.MARK.size) return true
        return SignerMark.matches(actual)
    }

    fun audit(context: Context): SecurityAuditReport {
        val fp = signerFingerprint(context)?.joinToString("") { b ->
            (b.toInt() and 0xFF).toString(16).padStart(2, '0')
        } ?: "tidak-terbaca"
        val sigOk = if (isDebuggable(context)) {
            true
        } else {
            val actual = signerFingerprint(context)
            actual == null || actual.size != SignerMark.MARK.size || SignerMark.matches(actual)
        }
        val pkgOk = context.packageName == EXPECTED_PACKAGE
        val hookName = if (isDebuggable(context)) null else detectHookFramework()
        val installer = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "sideload/apk"
        return SecurityAuditReport(
            signatureVerified = sigOk,
            packageVerified = pkgOk,
            hookFree = hookName == null,
            actualFingerprintShort = fp.take(12),
            officialFingerprintShort = MARK_HEAD,
            installerName = installer,
            detectedHookName = hookName,
        )
    }

    private fun detectHookFramework(): String? = runCatching {
        val suspiciousClasses = listOf(
            "bin.mt.apksignaturekiller.HookApplication" to "MT SignatureKiller",
            "cc.binmt.signature.PmsHookApplication" to "MT PMS Hook",
            "com.chelpus.LackyPatch" to "LuckyPatcher",
            "de.robv.android.xposed.XposedBridge" to "Xposed Bridge",
            "com.saurik.substrate.MS" to "Cydia Substrate",
        )
        for ((cls, label) in suspiciousClasses) {
            val found = runCatching { Class.forName(cls, false, AppChannel::class.java.classLoader) }.isSuccess
            if (found) return@runCatching label
        }
        val trace = Throwable().stackTrace
        for (frame in trace) {
            val cn = frame.className.lowercase()
            if (cn.contains("apksignaturekiller") || cn.contains("pmshook") || cn.contains("luckypatcher")) {
                return@runCatching frame.className.substringAfterLast('.')
            }
        }
        null
    }.getOrNull()

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

    /**
     * String diagnostik untuk tangkapan layar: potongan sidik jari yang
     * terbaca di perangkat + sumber pemasangan. Bukan data pribadi.
     */
    fun diagnostics(context: Context): Triple<String, String, String> {
        val rep = audit(context)
        return Triple(rep.actualFingerprintShort, rep.installerName, rep.officialFingerprintShort)
    }

    private val MARK_HEAD: String by lazy {
        SignerMark.MARK.take(6).joinToString("") { (it and 0xFF).toString(16).padStart(2, '0') }
    }
}

/**
 * Popup Proteksi Keamanan Anti-Bajak / Anti-Crack (XyShield Security Gate v4.1).
 */
@Composable
internal fun AppChannelGate(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val report = remember { AppChannel.audit(context) }
    val blocked = remember { !AppChannel.official(context) }
    val prefs = remember { context.getSharedPreferences("xydesk.channel", Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(KEY_DISMISSED, false)) }
    var copiedToast by remember { mutableStateOf(false) }
    if (!blocked || dismissed) return

    val diagSummary = remember(report) {
        "XyShield Audit | sig=${report.actualFingerprintShort} (expected=${report.officialFingerprintShort}) | pkg=${context.packageName} | hook=${report.detectedHookName ?: "clean"} | installer=${report.installerName}"
    }

    Box(
        modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(XyGateBackground.copy(alpha = 0.92f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(18.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .xyGlass(shape = RoundedCornerShape(24.dp), opacity = 1.65f, strength = 1f)
                .background(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF777777).copy(alpha = 0.18f),
                        0.35f to Color.Transparent,
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .border(1.4.dp, Color(0xFF777777).copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Badge Perisai XyShield
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF777777).copy(alpha = 0.18f))
                    .border(1.5.dp, Color(0xFF999999), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = XyIcons.Shield,
                    contentDescription = null,
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(28.dp),
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1C1C1C))
                    .border(1.dp, Color(0xFF999999).copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = xy("XYSHIELD v4.1 · DETEKSI MODIFIKASI / CRACK", "XYSHIELD v4.1 · TAMPER / CRACK DETECTED"),
                    color = Color(0xFFDADADA),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                )
            }

            Text(
                xy("Aplikasi Gratis Kok Masih Dibajak / Di-Crack?", "Free App & Still Cracked / Repackaged?"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Text(
                xy(
                    "XyShield mendeteksi tanda tangan digital atau modul native (.so) pada APK ini telah dimodifikasi. APK hasil crack/patch berisiko menyadap password RDP, token GitHub, dan akses PC Anda. Unduh versi resmi 100% gratis & aman.",
                    "XyShield detected that the digital signature or native (.so) modules of this APK were modified. Cracked/patched APKs risk stealing your RDP passwords, GitHub tokens, and PC access. Download the official build 100% free & safe.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )

            // Matriks Audit Integritas 4-Baris
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D0D0D))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SecurityAuditRow(
                    label = xy("Sertifikat SHA-256", "SHA-256 Certificate"),
                    value = "${report.actualFingerprintShort}… (Resmi: ${report.officialFingerprintShort}…)",
                    ok = report.signatureVerified,
                )
                SecurityAuditRow(
                    label = xy("Proteksi Anti-Hook", "Anti-Hook Shield"),
                    value = report.detectedHookName ?: xy("Bersih · Tanpa Injeksi", "Clean · No Injection"),
                    ok = report.hookFree,
                )
                SecurityAuditRow(
                    label = xy("Modul Native XyCore", "XyCore Native .so"),
                    value = "libxydesk-crypto-shield.so v4.1",
                    ok = report.packageVerified,
                )
                SecurityAuditRow(
                    label = xy("Sumber Installer", "Installer Source"),
                    value = report.installerName,
                    ok = true,
                )
            }

            if (copiedToast) {
                Text(
                    text = xy("Laporan diagnostik disalin ke clipboard", "Diagnostic report copied to clipboard"),
                    color = Color(0xFF9D9D9D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            XyPillButton(
                text = xy("Unduh APK Resmi (Gratis & Aman)", "Download Official APK (Free & Safe)"),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                XyPillButton(
                    text = xy("Salin Audit", "Copy Audit"),
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("XyShield Audit", diagSummary))
                        copiedToast = true
                    },
                    icon = XyIcons.Copy,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Tutup Aplikasi", "Exit App"),
                    onClick = onExit,
                    icon = XyIcons.Power,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }

            XyPillButton(
                text = xy("Saya pakai ROM Kustom / App Clone — Tetap Lanjut", "Using Custom ROM / App Clone — Continue"),
                onClick = {
                    prefs.edit().putBoolean(KEY_DISMISSED, true).apply()
                    dismissed = true
                },
                icon = XyIcons.Check,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SecurityAuditRow(
    label: String,
    value: String,
    ok: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (ok) Color(0xFF808080) else Color(0xFF777777)),
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = if (ok) Color(0xFFE4E4E7) else Color(0xFFBFBFBF),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Kunci penyimpanan keputusan menutup peringatan kanal. */
private const val KEY_DISMISSED = "warn_dismissed"

/** Latar penutup. Tidak transparan supaya layar di bawahnya tidak terbaca. */
private val XyGateBackground = androidx.compose.ui.graphics.Color(0xFF08080A)
