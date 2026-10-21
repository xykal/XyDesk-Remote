package id.xydesk.remote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freerdp.freerdpcore.application.GlobalApp
import id.xydesk.remote.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Penyimpanan reaktif untuk Preview Sesi di Home agar mengikuti Wallpaper /
 * Tangkapan Layar Desktop asli yang dipakai di PC / RDP pengguna.
 */
object SessionPreviewStore {
    val revision = mutableIntStateOf(0)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<String, Bitmap>()
    private val diskCheckedKeys = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val lastCaptureMsByHost = ConcurrentHashMap<String, Long>()
    @Volatile private var remoteWallSyncAttempted = false

    private fun bumpRevisionOnMain() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            revision.intValue++
        } else {
            mainHandler.post { revision.intValue++ }
        }
    }

    private fun normalizeKey(raw: String?): String {
        val clean = raw?.trim()?.lowercase().orEmpty()
            .substringBefore(':')
            .replace(Regex("[^a-z0-9._-]"), "_")
        return if (clean.isNotBlank()) clean else "default_host"
    }

    private fun previewDir(context: Context): File =
        File(context.applicationContext.filesDir, "session_previews").apply { mkdirs() }

    /** Cek cepat apakah bitmap bukan layar hitam kosong saat sesi baru handshake. */
    private fun isMeaningfulDesktopFrame(bmp: Bitmap): Boolean {
        if (bmp.isRecycled || bmp.width < 64 || bmp.height < 64) return false
        return runCatching {
            synchronized(bmp) {
                if (bmp.isRecycled) return@runCatching false
                var nonBlackSamples = 0
                val stepX = (bmp.width / 6).coerceAtLeast(1)
                val stepY = (bmp.height / 6).coerceAtLeast(1)
                for (gx in 1..5) {
                    for (gy in 1..5) {
                        val px = bmp.getPixel(
                            (gx * stepX).coerceIn(0, bmp.width - 1),
                            (gy * stepY).coerceIn(0, bmp.height - 1),
                        )
                        val r = (px shr 16) and 0xFF
                        val g = (px shr 8) and 0xFF
                        val b = px and 0xFF
                        if (r + g + b > 28) {
                            nonBlackSamples++
                            if (nonBlackSamples >= 3) return@runCatching true
                        }
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    /**
     * Dinonaktifkan sesuai permintaan pemilik: preview kartu di Beranda hanya
     * menampilkan Wallpaper murni (bukan tangkapan layar jendela sesi aktif).
     */
    fun saveDesktopSnapshotThrottled(
        context: Context,
        hostKey: String?,
        bitmap: Bitmap?,
        minIntervalMs: Long = 10_000L,
    ) {
        // Sengaja tidak menyimpan screenshot layar sesi agar preview di Home
        // selalu menampilkan Wallpaper bersih, bukan isi jendela yang sedang dibuka.
    }

    /**
     * Pastikan wallpaper remote tersinkron tanpa memotret layar sesi aktif.
     */
    fun captureFromActiveRdpSessions(context: Context, force: Boolean = false) {
        ensureRemoteWallpaperSynced(context)
    }

    /**
     * Ambil bitmap WALLPAPER murni untuk perangkat (murni dari RAM di UI thread,
     * dan memicu pembacaan file wallpaper di disk secara asinkron agar UI 0ms bebas lag).
     */
    fun getPreviewBitmap(context: Context, host: String?, label: String?): Bitmap? {
        val hostKey = normalizeKey(host)
        val labelKey = normalizeKey(label)
        val isXyRdp = label?.contains("xyrdp", ignoreCase = true) == true ||
            label?.contains("rdpfree", ignoreCase = true) == true ||
            host?.startsWith("100.") == true ||
            host?.contains("bore.pub", ignoreCase = true) == true ||
            host?.contains("ngrok", ignoreCase = true) == true

        memoryCache["wall_$hostKey"]?.takeIf { !it.isRecycled }?.let { return it }
        memoryCache["wall_$labelKey"]?.takeIf { !it.isRecycled }?.let { return it }
        if (isXyRdp) {
            memoryCache["xyrdp_wallpaper"]?.takeIf { !it.isRecycled }?.let { return it }
        }

        if (diskCheckedKeys.add(hostKey)) {
            val appCtx = context.applicationContext
            ioScope.launch {
                val dir = previewDir(appCtx)
                // Bersihkan file screenshot layar lama ($hostKey.jpg / latest_active.jpg)
                runCatching { File(dir, "latest_active.jpg").delete() }
                runCatching { File(dir, "$hostKey.jpg").delete() }
                val candidates = buildList {
                    add("wall_$hostKey" to File(dir, "wall_$hostKey.jpg"))
                    add("wall_$labelKey" to File(dir, "wall_$labelKey.jpg"))
                    if (isXyRdp) {
                        add("xyrdp_wallpaper" to File(dir, "xyrdp_wallpaper.jpg"))
                    }
                }
                for ((cacheKey, file) in candidates) {
                    if (file.exists() && file.length() > 512L) {
                        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                        if (decoded != null) {
                            memoryCache[cacheKey] = decoded
                            bumpRevisionOnMain()
                            break
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Sinkronkan wallpaper yang dipakai di XyRDP (dari URL wallpaper kustom atau
     * assets/wallpaper.jpg di repositori XyRDP) agar preview di Home sesuai dengan
     * wallpaper desktop RDP-nya bahkan sebelum sesi pertama dibuka.
     */
    fun ensureRemoteWallpaperSynced(context: Context) {
        if (remoteWallSyncAttempted) return
        remoteWallSyncAttempted = true
        val appCtx = context.applicationContext
        ioScope.launch {
            runCatching {
                val dir = previewDir(appCtx)
                val targetFile = File(dir, "xyrdp_wallpaper.jpg")
                val prefs = appCtx.getSharedPreferences("xyrdp_settings", Context.MODE_PRIVATE)
                val customWallUrl = prefs.getString("wallpaper_url", null)?.trim().orEmpty()
                val ghLogin = runCatching { getVerifiedGitHubLogin(appCtx) }.getOrNull()?.trim().orEmpty()

                val urlsToTry = buildList {
                    if (customWallUrl.startsWith("http://") || customWallUrl.startsWith("https://")) {
                        add(customWallUrl)
                    }
                    if (ghLogin.isNotEmpty()) {
                        add("https://raw.githubusercontent.com/$ghLogin/XyRDP/main/assets/wallpaper.jpg")
                        add("https://raw.githubusercontent.com/$ghLogin/XyRDP/main/assets/wallpaper.png")
                    }
                    add("https://raw.githubusercontent.com/xykal/XyRDP/main/assets/wallpaper.jpg")
                }

                for (urlStr in urlsToTry) {
                    val conn = (URL(urlStr).openConnection() as? HttpURLConnection) ?: continue
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 10_000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "XyDesk-Remote")
                    try {
                        if (conn.responseCode in 200..299) {
                            val bytes = conn.inputStream.use { it.readBytes() }
                            if (bytes.size > 1024) {
                                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (decoded != null && decoded.width >= 64) {
                                    val targetW = 640
                                    val targetH = ((decoded.height.toFloat() / decoded.width.toFloat()) * targetW)
                                        .toInt()
                                        .coerceIn(320, 420)
                                    val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                                    FileOutputStream(targetFile).use { out ->
                                        scaled.compress(Bitmap.CompressFormat.JPEG, 86, out)
                                    }
                                    memoryCache["xyrdp_wallpaper"] = scaled
                                    bumpRevisionOnMain()
                                    break
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        }
    }

    /**
     * Ambil file Wallpaper desktop asli dari PC / RDP via paket ringan UDP :4433
     * (mengambil file TranscodedWallpaper / wallpaper.jpg, BUKAN screenshot layar sesi).
     */
    fun fetchHostWallpaperIfAvailable(context: Context, host: String?) {
        val rawHost = host?.trim()?.substringBefore(':').orEmpty()
        if (rawHost.isEmpty() || rawHost == "localhost" || rawHost == "127.0.0.1") return
        val hostKey = normalizeKey(rawHost)
        val now = SystemClock.elapsedRealtime()
        val prev = lastCaptureMsByHost["wall_$hostKey"] ?: 0L
        if (now - prev < 60_000L) return
        lastCaptureMsByHost["wall_$hostKey"] = now

        val appCtx = context.applicationContext
        ioScope.launch {
            runCatching {
                java.net.DatagramSocket().use { sock ->
                    sock.soTimeout = 1800
                    val addr = java.net.InetAddress.getByName(rawHost)
                    val req = "XYDESK_QUIC_WALLPAPER_V1".toByteArray(Charsets.UTF_8)
                    sock.send(java.net.DatagramPacket(req, req.size, addr, 4433))
                    val buf = ByteArray(65507)
                    val pkt = java.net.DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    if (pkt.length > 512 &&
                        buf[0] == 0x58.toByte() &&
                        buf[1] == 0x59.toByte() &&
                        buf[2] == 0x57.toByte() &&
                        buf[3] == 0x4C.toByte()
                    ) {
                        val decoded = BitmapFactory.decodeByteArray(buf, 4, pkt.length - 4)
                        if (decoded != null && decoded.width >= 64) {
                            val dir = previewDir(appCtx)
                            val outFile = File(dir, "wall_$hostKey.jpg")
                            FileOutputStream(outFile).use { out ->
                                decoded.compress(Bitmap.CompressFormat.JPEG, 86, out)
                            }
                            memoryCache["wall_$hostKey"] = decoded
                            bumpRevisionOnMain()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wallpaper desktop untuk preview perangkat + latar layar koneksi.
 *
 * Windows 11 memakai foto asli (drawable-nodpi/xy_win11_wall.jpg) supaya
 * preview perangkat Windows terasa seperti desktop yang benar-benar dilihat
 * user. Sisanya digambar prosedural (Canvas): bobot APK nol dan tidak ada
 * urusan lisensi gambar.
 */
enum class XyWall(val label: String) {
    WIN11("Windows 11"),
    WIN10("Windows 10"),
    MACOS("macOS"),
    NEUTRAL("Netral"),
}

/** Tebak motif dari nama/label perangkat. */
fun XyWall.forDevice(label: String?): XyWall {
    val l = label?.lowercase().orEmpty()
    return when {
        l.contains("mac") || l.contains("osx") || l.contains("macbook") -> XyWall.MACOS
        l.contains("win 10") || l.contains("win10") || l.contains("windows 10") -> XyWall.WIN10
        l.contains("win") || l.contains("11") -> XyWall.WIN11
        else -> this
    }
}

@Composable
fun XyWallpaper(
    wall: XyWall,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp,
    dim: Float = 0f,
) {
    val blurModifier = if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= 31) {
        Modifier.blur(blurRadius)
    } else {
        Modifier
    }
    Box(modifier.fillMaxSize().then(blurModifier)) {
        if (wall == XyWall.WIN11) {
            Image(
                painter = painterResource(R.drawable.xy_win11_wall),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (wall) {
                XyWall.WIN11 -> {
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x33000000), Color(0x14000000), Color(0x66000000),
                            ),
                        )
                    )
                }

                XyWall.WIN10 -> {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF17364F), Color(0xFF10283C), Color(0xFF0C1D2C)),
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.6f, h),
                        )
                    )
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2E79B8), Color(0x00153248)),
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.75f, h * 0.9f),
                        )
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF55A8DC), Color(0x00000000)),
                            center = Offset(w * 0.3f, h * 0.3f),
                            radius = h * 0.55f,
                        ),
                        topLeft = Offset(-w * 0.1f, -h * 0.05f),
                        size = Size(w, h),
                    )
                }

                XyWall.MACOS -> {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A1E5C), Color(0xFF6A3E9E),
                                Color(0xFFB85C88), Color(0xFFE08A5A),
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                        )
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFC48A), Color(0x00000000)),
                            center = Offset(w * 0.62f, h * 0.72f),
                            radius = h * 0.5f,
                        ),
                        topLeft = Offset(w * 0.16f, h * 0.36f),
                        size = Size(w * 0.9f, h * 0.8f),
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF4A6BE0), Color(0x00000000)),
                            center = Offset(w * 0.24f, h * 0.26f),
                            radius = h * 0.44f,
                        ),
                        topLeft = Offset(-w * 0.2f, -h * 0.1f),
                        size = Size(w * 0.8f, h * 0.8f),
                    )
                }

                XyWall.NEUTRAL -> {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF262C33), Color(0xFF171C22)),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                        )
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF424A54), Color(0x00000000)),
                            center = Offset(w * 0.4f, h * 0.3f),
                            radius = h * 0.6f,
                        ),
                        topLeft = Offset(-w * 0.2f, -h * 0.2f),
                        size = Size(w * 1.2f, h),
                    )
                }
            }
            if (dim > 0f) {
                drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f)))
            }
        }
    }
}

/** Latar gelap polos untuk layar app (bukan wallpaper). */
@Composable
fun XyBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF07080A)))
}

/**
 * Preview kartu sesi di Home:
 * Menampilkan wallpaper / tangkapan layar asli yang dipakai di PC / RDP pengguna
 * (dari sesi RDP aktif/terakhir, PC Stream P2P, atau wallpaper XyRDP).
 */
@Composable
fun DevicePreviewArt(
    seed: String,
    os: XyWall,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val labelPart = remember(seed) { seed.substringBefore('|').trim() }
    val hostPart = remember(seed) { seed.substringAfter('|', seed).trim() }
    val rev = SessionPreviewStore.revision.intValue

    LaunchedEffect(seed) {
        SessionPreviewStore.ensureRemoteWallpaperSynced(context)
        SessionPreviewStore.fetchHostWallpaperIfAvailable(context, hostPart)
    }

    val liveOrSavedBitmap = remember(seed, rev) {
        SessionPreviewStore.getPreviewBitmap(context, host = hostPart, label = labelPart)
    }

    val isXyRdpDevice = remember(labelPart, hostPart) {
        labelPart.contains("xyrdp", ignoreCase = true) ||
            hostPart.startsWith("100.") ||
            hostPart.contains("bore.pub", ignoreCase = true) ||
            hostPart.contains("ngrok", ignoreCase = true)
    }

    Box(
        modifier.fillMaxSize(),
    ) {
        if (liveOrSavedBitmap != null && !liveOrSavedBitmap.isRecycled) {
            Image(
                bitmap = liveOrSavedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Scrim halus di bagian bawah saja agar wallpaper PC/RDP tampil jelas
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x14000000), Color(0x10000000), Color(0x80000000)),
                        )
                    ),
            )
        } else if (isXyRdpDevice) {
            Image(
                painter = painterResource(R.drawable.xyrdp_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x18000000), Color(0x14000000), Color(0x88000000)),
                        )
                    ),
            )
        } else if (os == XyWall.WIN11 || os == XyWall.WIN10) {
            Image(
                painter = painterResource(R.drawable.xy_win11_wall),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x26000000), Color(0x1A000000), Color(0x88000000)),
                        )
                    ),
            )
        } else {
            XyWallpaper(wall = os, modifier = Modifier.fillMaxSize(), dim = 0.18f)
        }

        // Penanda jenis OS kecil di sudut kiri atas
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(26.dp),
        ) {
            val stroke = Stroke(width = 1.4f)
            when (os) {
                XyWall.MACOS -> {
                    drawRoundRect(
                        color = Color(0xFFB9C4CC),
                        topLeft = Offset(2f, 2f),
                        size = Size(size.width - 4f, size.height - 6f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width * 0.42f, size.height - 4f),
                        end = Offset(size.width * 0.58f, size.height - 4f),
                        strokeWidth = 1.4f,
                    )
                }

                XyWall.WIN10 -> {
                    drawRect(
                        color = Color(0xFFB9C4CC),
                        topLeft = Offset(2f, 4f),
                        size = Size(size.width - 4f, size.height - 9f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width * 0.35f, 4f),
                        end = Offset(size.width * 0.35f, size.height - 5f),
                        strokeWidth = 1.4f,
                    )
                }

                XyWall.WIN11 -> {
                    drawRoundRect(
                        color = Color(0xFFB9C4CC),
                        topLeft = Offset(2f, 4f),
                        size = Size(size.width - 4f, size.height - 9f),
                        cornerRadius = CornerRadius(2f, 2f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width * 0.66f, 4f),
                        end = Offset(size.width * 0.66f, size.height - 5f),
                        strokeWidth = 1.4f,
                    )
                }

                XyWall.NEUTRAL -> {
                    drawCircle(
                        color = Color(0xFFB9C4CC),
                        radius = size.minDimension * 0.36f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width / 2f, size.height * 0.14f),
                        end = Offset(size.width / 2f, size.height * 0.86f),
                        strokeWidth = 1.2f,
                    )
                }
            }
        }
    }
}
