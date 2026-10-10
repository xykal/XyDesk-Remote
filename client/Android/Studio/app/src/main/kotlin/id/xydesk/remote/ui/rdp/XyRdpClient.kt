package id.xydesk.remote.ui.rdp

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Hasil pembacaan `out/rdp-status.json` dari fork pengguna. */
internal data class XyRdpAccess(
    val mode: String,
    val tunnelStatus: String,
    val tunnelAddress: String,
    val tunnelHost: String,
    val tunnelPort: Int,
    val selftest: String,
    val outside: String,
    val tailscaleIp: String,
    val rustdeskId: String,
)

internal data class XyRdpRun(
    val number: Long,
    val status: String,
    val conclusion: String?,
    val url: String,
)

/**
 * Klien GitHub untuk fitur create-RDP. Semua panggilan memakai PAT milik
 * pengguna sendiri (scope `repo` + `workflow`) dan hanya menyentuh API publik
 * GitHub — tidak ada server perantara, tidak ada akun pusat.
 */
internal object XyRdpClient {
    private const val API = "https://api.github.com"

    private fun call(
        token: String,
        path: String,
        method: String = "GET",
        body: String? = null,
    ): Pair<Int, String> = runCatching {
        val conn = URL("$API$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "XyDesk-Remote-Android")
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            code to text
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(0 to "")

    /** Login pemilik PAT; null bila token tidak sah. */
    suspend fun whoami(token: String): String? = withContext(Dispatchers.IO) {
        val (code, text) = call(token, "/user")
        if (code != 200) return@withContext null
        runCatching { JSONObject(text).optString("login") }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    suspend fun forkExists(token: String, login: String): Boolean =
        withContext(Dispatchers.IO) {
            call(token, "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}").first == 200
        }

    suspend fun createFork(token: String): Boolean = withContext(Dispatchers.IO) {
        val code = call(token, "/repos/${XyRdpConfig.TEMPLATE_REPO}/forks", "POST", "{}").first
        code in 200..202 || code == 403 // 403 = fork sudah ada / rate limit lunak
    }

    /** Fork baru mematikan Actions; nyalakan workflow template lewat API. */
    suspend fun enableWorkflow(token: String, login: String): Boolean =
        withContext(Dispatchers.IO) {
            val (code, text) = call(token, "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/workflows")
            if (code != 200) return@withContext false
            val list = runCatching { JSONObject(text).optJSONArray("workflows") }.getOrNull()
                ?: return@withContext false
            for (i in 0 until list.length()) {
                val wf = list.optJSONObject(i) ?: continue
                val path = wf.optString("path")
                if (!path.endsWith(XyRdpConfig.WORKFLOW_FILE)) continue
                if (wf.optString("state") == "active") return@withContext true
                val id = wf.optLong("id")
                val en = call(
                    token,
                    "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/workflows/$id/enable",
                    "PUT",
                ).first
                return@withContext en in 200..204
            }
            false
        }

    /** Mulai sesi 6 jam: akses RustDesk + tunnel IP (tanpa wajib Tailscale). */
    suspend fun dispatch(token: String, login: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("ref", "main")
                put(
                    "inputs",
                    JSONObject().apply {
                        put("akses", "keduanya")
                        put("storage_boost", "ya")
                    },
                )
            }
            val code = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/workflows/${XyRdpConfig.WORKFLOW_FILE}/dispatches",
                "POST",
                body.toString(),
            ).first
            code in 200..204
        }

    suspend fun latestRun(token: String, login: String): XyRdpRun? =
        withContext(Dispatchers.IO) {
            val (code, text) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/runs?per_page=3",
            )
            if (code != 200) return@withContext null
            val runs = runCatching { JSONObject(text).optJSONArray("workflow_runs") }.getOrNull()
                ?: return@withContext null
            val first = runs.optJSONObject(0) ?: return@withContext null
            XyRdpRun(
                number = first.optLong("run_number"),
                status = first.optString("status"),
                conclusion = first.optString("conclusion", "").takeIf { it.isNotBlank() },
                url = first.optString("html_url"),
            )
        }

    /** Status sesi dari branch `status` fork pengguna (konten base64). */
    suspend fun access(token: String, login: String): XyRdpAccess? =
        withContext(Dispatchers.IO) {
            val (code, text) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/contents/out%2Frdp-status.json?ref=${XyRdpConfig.STATUS_REF}",
            )
            if (code != 200) return@withContext null
            val content = runCatching { JSONObject(text).optString("content") }.getOrNull()
                ?: return@withContext null
            val json = runCatching {
                String(Base64.decode(content.replace("\n", ""), Base64.DEFAULT))
            }.getOrNull() ?: return@withContext null
            parseAccess(json)
        }

    /** Parser murni — bisa diuji unit tanpa jaringan. */
    fun parseAccess(json: String): XyRdpAccess? = runCatching {
        val root = JSONObject(json)
        val akses = root.optJSONObject("akses") ?: return@runCatching null
        val tunnel = akses.optJSONObject("tunnel")
        val tailscale = akses.optJSONObject("tailscale")
        val rustdesk = akses.optJSONObject("rustdesk")
        XyRdpAccess(
            mode = akses.optString("mode"),
            tunnelStatus = tunnel?.optString("status").orEmpty(),
            tunnelAddress = tunnel?.optString("address").orEmpty(),
            tunnelHost = tunnel?.optString("host").orEmpty(),
            tunnelPort = tunnel?.optInt("port", 0) ?: 0,
            selftest = tunnel?.optString("selftest").orEmpty(),
            outside = tunnel?.optString("outside").orEmpty(),
            tailscaleIp = tailscale?.optString("ip").orEmpty(),
            rustdeskId = rustdesk?.optString("id").orEmpty(),
        )
    }.getOrNull()
}
