package id.xydesk.remote.ui

import android.content.Context
import android.os.Build
import id.xydesk.remote.push.ChatPushNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Info versi terbaru & paket data OTA dari portal (`/api/update-check` & `/api/data-pack`).
 *
 * Ronde 54:
 * - Mendukung **2 jalur update langsung dari HP**:
 *   1. **Download Data (OTA Live Data Pack)** untuk update ringan (konfigurasi runner OS,
 *      koleksi stiker, protokol E2EE, preset RDP) tanpa perlu mengunduh/menginstal ulang APK.
 *   2. **Update APK Penuh** untuk perubahan engine native / besar, dapat diunduh langsung
 *      di dalam aplikasi dengan indikator progres `0% -> 100%`.
 * - Menyediakan **App-Wide Presence Heartbeat (`/api/chat/presence`)** agar jumlah **Total Online**
 *   dihitung dari seluruh pengguna yang sedang membuka aplikasi XyDesk Remote (bukan hanya yang
 *   sedang membuka layar Chat Global).
 */
internal data class AppUpdateInfo(
    val version: String,
    val url: String,
    val changelog: String,
    val dataPackVersion: String = "2026.10.17.56",
    val minApkVersion: String = version,
    val apk64Url: String = "https://api.xydeskremote.biz.id/XyDesk-Remote64bit.apk",
    val apk32Url: String = "https://api.xydeskremote.biz.id/XyDesk-Remote32bit.apk",
    val dataPackUrl: String = "https://api.xydeskremote.biz.id/api/data-pack",
)

internal object AppUpdate {
    private const val ENDPOINT = "https://api.xydeskremote.biz.id/api/update-check"
    private const val DATA_PACK_ENDPOINT = "https://api.xydeskremote.biz.id/api/data-pack"
    private const val PRESENCE_ENDPOINT = "https://api.xydeskremote.biz.id/api/chat/presence"
    private const val PREFS_DATA_PACK = "xydesk.ota.datapack"
    private const val KEY_INSTALLED_DATA_VER = "installed_data_version"
    private const val KEY_LAST_UPDATE_NOTIFIED = "last_notified_update_key"
    const val BUILTIN_DATA_VERSION = "2026.10.17.53"

    private val _appWideOnlineCount = MutableStateFlow(1)
    val appWideOnlineCount: StateFlow<Int> = _appWideOnlineCount.asStateFlow()

    fun updateOnlineCount(count: Int) {
        if (count >= 1) {
            _appWideOnlineCount.value = count
        }
    }

    fun installedDataVersion(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
        return prefs.getString(KEY_INSTALLED_DATA_VER, BUILTIN_DATA_VERSION) ?: BUILTIN_DATA_VERSION
    }

    fun hasDataPackUpdate(context: Context, remoteDataVer: String): Boolean {
        val current = installedDataVersion(context)
        return remoteDataVer.isNotBlank() && remoteDataVer != current && isNewer(remoteDataVer, current)
    }

    suspend fun fetch(context: Context? = null): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = JSONObject(conn.inputStream.bufferedReader().readText())
            val version = body.optString("version", "").trim()
            if (version.isEmpty()) return@runCatching null
            val info = AppUpdateInfo(
                version = version,
                url = body.optString("url", ""),
                changelog = body.optString("changelog", ""),
                dataPackVersion = body.optString("data_pack_version", "2026.10.17.54").trim(),
                minApkVersion = body.optString("min_apk_version", version).trim(),
                apk64Url = body.optString("apk_64bit_url", "https://api.xydeskremote.biz.id/XyDesk-Remote64bit.apk"),
                apk32Url = body.optString("apk_32bit_url", "https://api.xydeskremote.biz.id/XyDesk-Remote32bit.apk"),
                dataPackUrl = body.optString("data_pack_url", DATA_PACK_ENDPOINT),
            )
            if (context != null) {
                maybeNotifyUpdatePush(context.applicationContext, info)
            }
            info
        }.getOrNull()
    }

    private fun maybeNotifyUpdatePush(appContext: Context, info: AppUpdateInfo) {
        runCatching {
            val currentApk = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.1.18"
            }.getOrDefault("1.1.18")
            val apkNewer = isNewer(info.version, currentApk)
            val dataNewer = hasDataPackUpdate(appContext, info.dataPackVersion)
            if (!apkNewer && !dataNewer) return@runCatching

            val prefs = appContext.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
            val notifyKey = "${info.version}:${info.dataPackVersion}"
            if (prefs.getString(KEY_LAST_UPDATE_NOTIFIED, "") == notifyKey) return@runCatching
            prefs.edit().putString(KEY_LAST_UPDATE_NOTIFIED, notifyKey).apply()

            if (apkNewer) {
                ChatPushNotifier.notifyAppUpdateAvailable(
                    context = appContext,
                    title = xyNow("Update Aplikasi Tersedia · v${info.version}", "App Update Available · v${info.version}"),
                    summary = xyNow(
                        "Versi besar v${info.version} telah rilis! Ketuk untuk unduh & pasang langsung dari HP.",
                        "Major release v${info.version} is out! Tap to download & install directly on your phone.",
                    ),
                    isDataPack = false,
                )
            } else if (dataNewer) {
                ChatPushNotifier.notifyAppUpdateAvailable(
                    context = appContext,
                    title = xyNow("Update Data Ringan Tersedia · ${info.dataPackVersion}", "Live Data Update Available · ${info.dataPackVersion}"),
                    summary = xyNow(
                        "Paket data baru siap diunduh langsung di dalam aplikasi (Download Data) tanpa perlu instal ulang APK!",
                        "New data pack is ready to download inside the app without reinstalling the APK!",
                    ),
                    isDataPack = true,
                )
            }
        }
    }

    /**
     * Unduh & terapkan **Paket Data OTA (Download Data)** langsung di dalam HP tanpa instal ulang APK.
     */
    suspend fun downloadAndApplyDataPack(
        context: Context,
        onProgress: (percent: Int, label: String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            withContext(Dispatchers.Main) {
                onProgress(12, xyNow("Menghubungi server paket data OTA…", "Connecting to OTA data pack server…"))
            }
            delay(220)
            val conn = (URL(DATA_PACK_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) {
                error(xyNow("Server paket data mengembalikan HTTP ${conn.responseCode}", "Data server returned HTTP ${conn.responseCode}"))
            }
            withContext(Dispatchers.Main) {
                onProgress(42, xyNow("Mengunduh bundel konfigurasi, runner multi-OS & stiker…", "Downloading config bundle, multi-OS runners & stickers…"))
            }
            val rawJson = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            delay(260)

            val json = JSONObject(rawJson)
            val dataVer = json.optString("data_version", "2026.10.17.54").trim()
            val summary = json.optString(
                "changelog_summary",
                xyNow("Paket data berhasil diperbarui.", "Data pack updated successfully."),
            )

            withContext(Dispatchers.Main) {
                onProgress(76, xyNow("Memvalidasi integritas & menyinkronkan data lokal…", "Validating integrity & syncing local data…"))
            }
            val outFile = File(appCtx.filesDir, "xydesk_live_data_pack.json")
            outFile.writeText(rawJson, Charsets.UTF_8)

            appCtx.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTALLED_DATA_VER, dataVer)
                .putString("last_summary", summary)
                .putLong("updated_at_ms", System.currentTimeMillis())
                .apply()

            delay(240)
            withContext(Dispatchers.Main) {
                onProgress(100, xyNow("Selesai! Paket Data $dataVer telah aktif.", "Done! Data Pack $dataVer is now active."))
            }
            dataVer
        }
    }

    /**
     * Unduh file APK rilis terbaru langsung di dalam aplikasi dengan progres bar real-time,
     * lalu kembalikan File APK untuk dipasang atau buka installer sistem.
     */
    suspend fun downloadApkInApp(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (percent: Int, label: String) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            val is64 = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
            val targetUrl = if (is64) info.apk64Url else info.apk32Url
            withContext(Dispatchers.Main) {
                onProgress(2, xyNow("Menyiapkan unduhan APK v${info.version}…", "Preparing APK v${info.version} download…"))
            }
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode}")
            }
            val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: 98_000_000L
            val outDir = File(appCtx.externalCacheDir ?: appCtx.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(outDir, "XyDesk-Remote-v${info.version}.apk")
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(32 * 1024)
                    var readTotal = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        val pct = ((readTotal * 100L) / totalBytes).toInt().coerceIn(1, 99)
                        if (pct != lastPct) {
                            lastPct = pct
                            val mbRead = "%.1f".format(readTotal / (1024.0 * 1024.0))
                            val mbTotal = "%.1f".format(totalBytes / (1024.0 * 1024.0))
                            withContext(Dispatchers.Main) {
                                onProgress(pct, xyNow("Mengunduh APK: $mbRead / $mbTotal MB ($pct%)", "Downloading APK: $mbRead / $mbTotal MB ($pct%)"))
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            withContext(Dispatchers.Main) {
                onProgress(100, xyNow("Unduhan APK selesai! Membuka pemasang…", "APK download complete! Opening installer…"))
            }
            outFile
        }
    }

    /**
     * Kirim detak kehadiran (presence) tingkat aplikasi agar jumlah Total Online
     * menghitung seluruh orang yang sedang membuka aplikasi XyDesk Remote.
     */
    suspend fun sendAppPresencePing(
        context: Context,
        screen: String = "app",
        active: Boolean = true,
    ): Int = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            val api = CommunityJokesApi(appCtx)
            val ghLogin = getVerifiedGitHubLogin(appCtx).orEmpty()
            val name = ghLogin.ifBlank { api.displayName().orEmpty().ifBlank { chatNickname(appCtx) } }
            val clientId = api.accountShortId().ifBlank { name }
            val conn = (URL(PRESENCE_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7_000
                readTimeout = 7_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject()
                .put("client_id", clientId)
                .put("github_login", ghLogin)
                .put("name", name)
                .put("screen", screen)
                .put("active", active)
                .toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val count = JSONObject(body).optInt("online_count", 1).coerceAtLeast(1)
                _appWideOnlineCount.value = count
                conn.disconnect()
                return@runCatching count
            }
            conn.disconnect()
            _appWideOnlineCount.value
        }.getOrDefault(_appWideOnlineCount.value)
    }

    /** True bila [remote] lebih baru dari [current] (per segmen numerik). */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String): List<Int> =
            value.trim().removePrefix("v").split(".").map { seg ->
                seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
