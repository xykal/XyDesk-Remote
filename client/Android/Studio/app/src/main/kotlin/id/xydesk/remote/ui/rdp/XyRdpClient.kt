package id.xydesk.remote.ui.rdp

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.net.URL

/** Hasil pembacaan `rdp-status.json` (atau `out/rdp-status.json`) dari branch `status` fork pengguna. */
internal data class XyRdpAccess(
    val runId: String,
    val runAttempt: Int,
    val active: Boolean,
    val hostname: String,
    val osName: String,
    val startedAt: String,
    val expiresAt: String,
    val expiresAtMillis: Long,
    val heartbeatAt: String,
    val heartbeatAgeSec: Long?,
    val rdpUser: String,
    val mode: String,
    val tunnelStatus: String,
    val tunnelAddress: String,
    val tunnelHost: String,
    val tunnelPort: Int,
    val selftest: String,
    val outside: String,
    val tailscaleStatus: String,
    val tailscaleIp: String,
    val portmapStatus: String,
    val portmapHost: String,
    val portmapPort: Int,
    val rustdeskId: String,
    val initialCpuThreads: Int,
    val initialRamGb: Double,
    val initialDiskFreeGb: Double,
    /** Statistik live dari loop keepalive (null bila belum terpublikasi). */
    val stats: XyRdpStats?,
    val linuxMode: String = "desktop",
)

internal data class XyRdpStats(
    val ramTotalGb: Double,
    val ramUsedGb: Double,
    val ramFreeGb: Double,
    val diskTotalGb: Double,
    val diskFreeGb: Double,
    val cpuPercent: Int,
    val uptimeMinutes: Int,
    val updatedAt: String,
)

internal data class XyRdpRun(
    val id: Long,
    val number: Long,
    val attempt: Int,
    val status: String,
    val conclusion: String?,
    val url: String,
)

internal data class XyRdpStepProgress(
    val currentStep: String?,
    val failedStep: String?,
    val completedSteps: Int,
    val totalSteps: Int,
    val steps: List<Pair<String, String>>,
)

internal data class XyRdpSetupStatus(
    val forkExists: Boolean,
    val workflowActive: Boolean,
    val actionsEnabled: Boolean,
    val configuredSecrets: Set<String>,
    val missingSecrets: List<String>,
    val secretsVerified: Boolean = false,
) {
    val isReadyToStart: Boolean
        get() = forkExists && workflowActive && actionsEnabled && secretsVerified && missingSecrets.isEmpty()
}

internal enum class XyRdpPhase {
    IDLE,
    QUEUED,
    PREPARING,
    READY,
    FAILED,
    CANCELLED,
    STOPPED,
}

/**
 * Status gabungan yang sudah divalidasi silang antara Workflow Run (otoritatif),
 * daftar Job Steps, dan `rdp-status.json` di branch `status`.
 * Mencegah bug klasik di mana sisa file `rdp-status.json` dari run lama dianggap aktif,
 * atau run yang sedang jalan gagal mendeteksi IP Tailscale/Portmap.
 */
internal data class XyRdpValidatedSession(
    val phase: XyRdpPhase,
    val phaseLabel: String,
    val run: XyRdpRun?,
    val progress: XyRdpStepProgress?,
    val access: XyRdpAccess?,
    val isVerifiedLive: Boolean,
    val remainingSeconds: Long?,
    val validatedHost: String?,
    val validatedPort: Int,
    val validatedTransport: String?,
    val validationNote: String?,
)

/**
 * Klien GitHub untuk fitur Buat RDP. Semua panggilan memakai PAT milik
 * pengguna sendiri (scope `repo` + `workflow`) dan hanya menyentuh API publik
 * GitHub — tidak ada server perantara, tidak ada akun pusat.
 */
internal object XyRdpClient {
    private const val API = "https://api.github.com"
    private const val DASH_API = "https://xyrdp-dash.vercel.app/api"

    private val OPTIONAL_STEP_REGEX = Regex(
        "^(Pulihkan sesi sebelumnya|Pulihkan simpanan sesi|Verifikasi dependensi|Toolkit grafis opsional|Setup grafis|Grafis OpenGL|Runtime All-Software|Runtime GTA SA|Installer game opsional|Aplikasi ekstra|Optimasi storage|Setup tampilan|Storage boost|Simpan sesi|Upload simpanan sesi|Estafet sesi berikutnya|Finalize)",
        RegexOption.IGNORE_CASE,
    )

    private val HOSTNAME_REGEX = Regex(
        "^(?=.{1,253}\$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}\$",
    )

    private fun isDashCookieToken(token: String): Boolean =
        token.startsWith("ghs_cookie:")

    private fun dashCall(
        cookieToken: String,
        path: String,
        method: String = "GET",
        body: String? = null,
    ): Pair<Int, String> = runCatching {
        val rawCookies = cookieToken.removePrefix("ghs_cookie:").trim()
        val conn = URL("$DASH_API$path").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = method
            conn.setRequestProperty("Cookie", if (rawCookies.contains("ad_verified=")) rawCookies else "$rawCookies; ad_verified=1")
            conn.setRequestProperty("X-Ad-Verified", "1")
            conn.setRequestProperty("X-Xy-Ad", "1")
            conn.setRequestProperty("Accept", "application/json")
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

    private fun call(
        token: String,
        path: String,
        method: String = "GET",
        body: String? = null,
    ): Pair<Int, String> = runCatching {
        val conn = URL("$API$path").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = method
            if (token.isNotBlank() && !isDashCookieToken(token)) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
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

    /** Login pemilik PAT / OAuth Session; null bila token tidak sah. */
    suspend fun whoami(token: String): String? = withContext(Dispatchers.IO) {
        if (isDashCookieToken(token)) {
            val (code, text) = dashCall(token, "/me")
            if (code != 200) return@withContext null
            return@withContext runCatching { JSONObject(text).optString("login") }
                .getOrNull()?.takeIf { it.isNotBlank() }
        }
        val (code, text) = call(token, "/user")
        if (code != 200) return@withContext null
        runCatching { JSONObject(text).optString("login") }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    suspend fun forkExists(token: String, login: String): Boolean =
        withContext(Dispatchers.IO) {
            if (isDashCookieToken(token)) {
                val (code, text) = dashCall(token, "/me")
                if (code == 200) {
                    val exists = runCatching { JSONObject(text).optBoolean("repo_exists", false) }.getOrDefault(false)
                    if (exists) return@withContext true
                }
            }
            call(token, "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}").first == 200
        }

    suspend fun createFork(token: String): Boolean = withContext(Dispatchers.IO) {
        if (isDashCookieToken(token)) {
            val (code, _) = dashCall(token, "/setup", "POST", """{"name":"${XyRdpConfig.FORK_REPO_NAME}","private":false}""")
            return@withContext code in 200..204
        }
        val code = call(token, "/repos/${XyRdpConfig.TEMPLATE_REPO}/forks", "POST", "{}").first
        code in 200..202 || code == 403 // 403 = fork sudah ada / rate limit lunak
    }

    /**
     * Sinkronkan fork `XyRDP` milik pengguna dengan branch `main` dari `xykal/XyRDP`
     * supaya script & workflow di fork pengguna selalu versi terbaru.
     */
    suspend fun syncForkWithUpstream(token: String, login: String): Boolean =
        withContext(Dispatchers.IO) {
            if (isDashCookieToken(token)) return@withContext true
            if (login.equals("xykal", ignoreCase = true)) return@withContext true
            val (code, _) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/merge-upstream",
                "POST",
                JSONObject().put("branch", "main").toString(),
            )
            if (code in 200..204) return@withContext true
            val (upCode, upText) = call(
                token,
                "/repos/${XyRdpConfig.TEMPLATE_REPO}/git/ref/heads/main",
            )
            if (upCode == 200) {
                val upSha = runCatching {
                    JSONObject(upText).optJSONObject("object")?.optString("sha").orEmpty()
                }.getOrDefault("")
                if (upSha.length >= 40) {
                    val patchBody = JSONObject()
                        .put("sha", upSha)
                        .put("force", true)
                        .toString()
                    val (patchCode, _) = call(
                        token,
                        "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/git/refs/heads/main",
                        "PATCH",
                        patchBody,
                    )
                    if (patchCode in 200..204) return@withContext true
                }
            }
            code == 409
        }

    /** Fork baru mematikan Actions; nyalakan permissions + workflow template lewat API. */
    suspend fun enableWorkflow(token: String, login: String): Boolean =
        withContext(Dispatchers.IO) {
            if (isDashCookieToken(token)) {
                val (code, _) = dashCall(token, "/setup", "POST", """{"name":"${XyRdpConfig.FORK_REPO_NAME}","private":false}""")
                return@withContext code in 200..204
            }
            runCatching {
                call(
                    token,
                    "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/permissions",
                    "PUT",
                    """{"enabled":true,"allowed_actions":"all"}""",
                )
                call(
                    token,
                    "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/permissions/workflow",
                    "PUT",
                    """{"default_workflow_permissions":"write","can_approve_pull_request_reviews":false}""",
                )
            }
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

    /**
     * Periksa kelengkapan setup repo fork (Actions aktif, workflow aktif, dan Secrets
     * wajib `RDP_PASSWORD` + `TAILSCALE_AUTH_KEY` / `PORTMAP_OVPN_CONFIG` sudah ada).
     */
    suspend fun inspectSetup(token: String, login: String, aksesMode: String): XyRdpSetupStatus =
        withContext(Dispatchers.IO) {
            if (isDashCookieToken(token)) {
                val (code, text) = dashCall(token, "/me")
                if (code == 200) {
                    val obj = runCatching { JSONObject(text) }.getOrNull()
                    if (obj != null) {
                        val exists = obj.optBoolean("repo_exists", false)
                        val actEnabled = obj.optBoolean("actions_enabled", true)
                        val secArr = obj.optJSONArray("secrets")
                        val secretNames = mutableSetOf<String>()
                        if (secArr != null) {
                            for (i in 0 until secArr.length()) {
                                val s = secArr.optString(i).trim()
                                if (s.isNotEmpty()) secretNames.add(s)
                            }
                        }
                        val required = listOf(
                            "RDP_PASSWORD",
                            if (aksesMode == "portmap") "PORTMAP_OVPN_CONFIG" else "TAILSCALE_AUTH_KEY",
                        )
                        return@withContext XyRdpSetupStatus(
                            forkExists = exists,
                            workflowActive = exists && actEnabled,
                            actionsEnabled = actEnabled,
                            configuredSecrets = secretNames,
                            missingSecrets = required.filter { it !in secretNames },
                            secretsVerified = secArr != null,
                        )
                    }
                }
                error("Status dashboard belum terverifikasi (HTTP $code). Login ulang atau coba lagi; sesi tidak dikirim.")
            }
            val exists = forkExists(token, login)
            if (!exists) {
                return@withContext XyRdpSetupStatus(
                    forkExists = false,
                    workflowActive = false,
                    actionsEnabled = false,
                    configuredSecrets = emptySet(),
                    missingSecrets = listOf("RDP_PASSWORD", if (aksesMode == "portmap") "PORTMAP_OVPN_CONFIG" else "TAILSCALE_AUTH_KEY"),
                )
            }
            val (wfCode, wfText) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/workflows/${XyRdpConfig.WORKFLOW_FILE}",
            )
            val wfActive = wfCode == 200 && runCatching {
                JSONObject(wfText).optString("state") == "active"
            }.getOrDefault(false)

            val (permCode, permText) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/permissions",
            )
            val actionsEnabled = if (permCode == 200) {
                runCatching { JSONObject(permText).optBoolean("enabled", true) }.getOrDefault(true)
            } else wfActive

            val (secCode, secText) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/secrets?per_page=100",
            )
            val secretNames = mutableSetOf<String>()
            var secretsVerified = false
            if (secCode == 200) {
                runCatching {
                    val arr = JSONObject(secText).optJSONArray("secrets")
                    if (arr != null) {
                        secretsVerified = true
                        for (i in 0 until arr.length()) {
                            val name = arr.optJSONObject(i)?.optString("name").orEmpty()
                            if (name.isNotBlank()) secretNames.add(name)
                        }
                    }
                }
            }
            val hasAnyTunnelSecret = "TAILSCALE_AUTH_KEY" in secretNames || "PORTMAP_OVPN_CONFIG" in secretNames
            val required = buildList {
                add("RDP_PASSWORD")
                if (!hasAnyTunnelSecret) {
                    add(if (aksesMode == "portmap") "PORTMAP_OVPN_CONFIG" else "TAILSCALE_AUTH_KEY")
                }
            }
            val missing = if (secCode == 200) {
                required.filter { it !in secretNames }
            } else emptyList()

            XyRdpSetupStatus(
                forkExists = true,
                workflowActive = wfActive,
                actionsEnabled = actionsEnabled,
                configuredSecrets = secretNames,
                missingSecrets = missing,
                secretsVerified = secretsVerified,
            )
        }

    /**
     * Mulai sesi dengan input dari pengaturan layar Buat RDP.
     * Otomatis menyinkronkan fork dengan `xykal/XyRDP:main` terbaru sebelum dispatch,
     * dan memastikan `runner = ubuntu-24.04` bila `linux_mode == polos`.
     */
    suspend fun dispatch(token: String, login: String, inputs: JSONObject = JSONObject()): Boolean =
        withContext(Dispatchers.IO) {
            val validation = validateRdpLaunch(
                inputs.optString("durasi_menit", "360"), inputs.optString("runner", "windows-2022"),
                inputs.optString("hostname", "rdpfree"), inputs.optString("rdp_user", "xyadmin"),
                inputs.optString("akses", "tailscale"), inputs.optString("portmap_host"), inputs.optString("portmap_port"))
            require(validation.isEmpty()) { validation.joinToString("\n") }
            val chosenRunner = inputs.optString("runner", "windows-2022").trim().ifEmpty { "windows-2022" }
            if (chosenRunner.startsWith("windows", ignoreCase = true)) {
                inputs.put("runner", chosenRunner)
                inputs.put("linux_mode", "desktop")
            } else {
                inputs.put("runner", chosenRunner)
                val lMode = if (inputs.optString("linux_mode") == "polos") "polos" else "desktop"
                inputs.put("linux_mode", lMode)
            }
            if (isDashCookieToken(token)) {
                val startBody = JSONObject().apply {
                    put("durasi", inputs.optString("durasi_menit", "360"))
                    put("hostname", inputs.optString("hostname", "rdpfree"))
                    put("rdp_user", inputs.optString("rdp_user", "xyadmin"))
                    put("akses", inputs.optString("akses", "tailscale"))
                    put("runner", inputs.optString("runner", "windows-2022"))
                    put("linux_mode", inputs.optString("linux_mode", "desktop"))
                    put("win10", inputs.optString("win10", "ya"))
                    put("grafis", inputs.optString("grafis", "software"))
                    put("xydesk", inputs.optString("xydesk", "ya"))
                    put("microphone", inputs.optString("microphone", "ya"))
                    put("samp", inputs.optString("samp", "ya"))
                    put("ekstra", inputs.optString("ekstra", "ya"))
                    put("storage_boost", inputs.optString("storage_boost", "ya"))
                    put("debloat", inputs.optString("debloat", "ringan"))
                    put("perpanjang_otomatis", inputs.optString("perpanjang_otomatis", "ya"))
                }
                val (code, _) = dashCall(token, "/start", "POST", startBody.toString())
                return@withContext code in 200..204
            }
            // Anti-Suspend Pacing: beri jeda sopan antar mutasi API GitHub agar akun baru aman dari flag bot
            runCatching { syncForkWithUpstream(token, login) }
            kotlinx.coroutines.delay(500L)
            runCatching { enableWorkflow(token, login) }
            kotlinx.coroutines.delay(450L)
            val isRailwayHost = inputs.optString("portmap_host").trim()
                .let { it.endsWith(".rlwy.net", ignoreCase = true) || it.endsWith(".railway.app", ignoreCase = true) }
            val setup = runCatching { inspectSetup(token, login, inputs.optString("akses", "tailscale")) }.getOrNull()
            if (setup != null && setup.secretsVerified) {
                val realMissing = if (isRailwayHost) {
                    setup.missingSecrets.filter { it == "RDP_PASSWORD" }
                } else {
                    setup.missingSecrets
                }
                if (realMissing.isNotEmpty()) {
                    error("Setup belum lengkap: ${realMissing.joinToString()}. Buka tab Secrets & Akses.")
                }
            }
            val body = JSONObject().apply {
                put("ref", "main")
                put("inputs", inputs)
            }
            val code = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/workflows/${XyRdpConfig.WORKFLOW_FILE}/dispatches",
                "POST",
                body.toString(),
            ).first
            code in 200..204
        }

    /** Batalkan run yang sedang berjalan (tombol Stop di layar Buat RDP). */
    suspend fun cancelRun(token: String, login: String, runId: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (isDashCookieToken(token)) {
                val (code, _) = dashCall(token, "/stop", "POST", JSONObject().put("run_id", runId).toString())
                return@withContext code in 200..204
            }
            call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/runs/$runId/cancel",
                "POST",
            ).first in 200..204
        }

    /** Id run terbaru (untuk cancel); null bila belum pernah ada run. */
    suspend fun latestRunId(token: String, login: String): Long? =
        latestRun(token, login)?.id

    /**
     * Ambil run untuk workflow `rdp-6h.yml` (memprioritaskan run yang sedang `in_progress`/`queued`
     * supaya tidak tertutup oleh run workflow validasi lain).
     */
    suspend fun latestRun(token: String, login: String): XyRdpRun? =
        withContext(Dispatchers.IO) {
            val (code, text) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/workflows/${XyRdpConfig.WORKFLOW_FILE}/runs?per_page=10",
            )
            val fallbackPair = if (code != 200) {
                call(token, "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/runs?per_page=10")
            } else code to text
            if (fallbackPair.first != 200) return@withContext null
            val runs = runCatching { JSONObject(fallbackPair.second).optJSONArray("workflow_runs") }.getOrNull()
                ?: return@withContext null
            if (runs.length() == 0) return@withContext null

            var chosen: JSONObject? = null
            for (i in 0 until runs.length()) {
                val r = runs.optJSONObject(i) ?: continue
                val st = r.optString("status")
                if (st in setOf("in_progress", "queued", "waiting", "requested", "pending")) {
                    chosen = r
                    break
                }
            }
            if (chosen == null) chosen = runs.optJSONObject(0)
            val runObj = chosen ?: return@withContext null
            XyRdpRun(
                id = runObj.optLong("id"),
                number = runObj.optLong("run_number"),
                attempt = runObj.optInt("run_attempt", 1).coerceAtLeast(1),
                status = runObj.optString("status"),
                conclusion = runObj.optString("conclusion", "").takeIf { it.isNotBlank() && it != "null" },
                url = runObj.optString("html_url"),
            )
        }

    /**
     * Ambil detail progres step dari job workflow yang sedang jalan / terakhir jalan.
     */
    suspend fun fetchRunStepProgress(token: String, login: String, run: XyRdpRun): XyRdpStepProgress? =
        withContext(Dispatchers.IO) {
            val (code, text) = call(
                token,
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/actions/runs/${run.id}/attempts/${run.attempt}/jobs?per_page=50",
            )
            if (code != 200) return@withContext null
            runCatching {
                val jobs = JSONObject(text).optJSONArray("jobs") ?: return@runCatching null
                val stepList = mutableListOf<Triple<String, String, String?>>()
                for (i in 0 until jobs.length()) {
                    val job = jobs.optJSONObject(i) ?: continue
                    if (job.optString("conclusion") == "skipped") continue
                    val steps = job.optJSONArray("steps") ?: continue
                    for (j in 0 until steps.length()) {
                        val s = steps.optJSONObject(j) ?: continue
                        val name = s.optString("name")
                        val status = s.optString("status")
                        val conclusion = s.optString("conclusion", "").takeIf { it.isNotBlank() && it != "null" }
                        if (name.isNotBlank()) {
                            stepList.add(Triple(name, status, conclusion))
                        }
                    }
                }
                val currentStep = stepList.firstOrNull { it.second == "in_progress" }?.first
                val fatalFailure = stepList.withIndex().firstOrNull { (idx, item) ->
                    val concl = item.third
                    if (concl != "failure" && concl != "timed_out") return@firstOrNull false
                    if (OPTIONAL_STEP_REGEX.containsMatchIn(item.first)) return@firstOrNull false
                    val laterActive = stepList.drop(idx + 1).any { nxt ->
                        (nxt.second == "in_progress" || nxt.third == "success") &&
                            !nxt.first.startsWith("Simpan sesi", ignoreCase = true) &&
                            !nxt.first.startsWith("Upload simpanan", ignoreCase = true) &&
                            !nxt.first.startsWith("Finalize", ignoreCase = true) &&
                            !nxt.first.startsWith("Post ", ignoreCase = true)
                    }
                    !laterActive
                }?.value?.first
                val completed = stepList.count { it.second == "completed" }
                XyRdpStepProgress(
                    currentStep = currentStep,
                    failedStep = fatalFailure,
                    completedSteps = completed,
                    totalSteps = stepList.size,
                    steps = stepList.map { it.first to (it.third ?: it.second) },
                )
            }.getOrNull()
        }

    /**
     * Status sesi dari branch `status` fork pengguna.
     * Membaca `/contents/rdp-status.json?ref=status` (standar `publish-status.ps1` XyRDP)
     * dan otomatis fallback ke `/contents/out%2Frdp-status.json?ref=status`.
     */
    suspend fun access(token: String, login: String): XyRdpAccess? =
        withContext(Dispatchers.IO) {
            val paths = listOf(
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/contents/rdp-status.json?ref=${XyRdpConfig.STATUS_REF}",
                "/repos/$login/${XyRdpConfig.FORK_REPO_NAME}/contents/out%2Frdp-status.json?ref=${XyRdpConfig.STATUS_REF}",
            )
            for (p in paths) {
                val (code, text) = call(token, p)
                if (code == 200) {
                    val content = runCatching { JSONObject(text).optString("content") }.getOrNull()
                        ?: continue
                    val json = runCatching {
                        String(Base64.decode(content.replace("\n", "").replace("\r", ""), Base64.DEFAULT))
                            .removePrefix("\uFEFF")
                    }.getOrNull() ?: continue
                    val parsed = parseAccess(json)
                    if (parsed != null) return@withContext parsed
                }
            }
            null
        }

    /**
     * Gabungkan & validasi silang `latestRun`, `stepProgress`, dan `access` (`rdp-status.json`)
     * persis mengikuti kontrak otoritatif `session-status.ts` di XyRDP.
     */
    suspend fun pollValidatedSession(
        token: String,
        login: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): XyRdpValidatedSession = withContext(Dispatchers.IO) {
        val run = latestRun(token, login)
        val access = access(token, login)
        val progress = if (run != null) fetchRunStepProgress(token, login, run) else null
        evaluateSession(run, access, progress, nowMillis)
    }

    internal fun evaluateSession(
        run: XyRdpRun?,
        access: XyRdpAccess?,
        progress: XyRdpStepProgress?,
        nowMillis: Long = System.currentTimeMillis(),
    ): XyRdpValidatedSession {
        val pendingStates = setOf("queued", "waiting", "requested", "pending")
        val isRunInProgress = run?.status == "in_progress"
        val isRunPending = run != null && run.status in pendingStates

        val runIdMatches = run != null && access != null && (
            access.runId.isBlank() ||
                access.runId == run.id.toString()
            ) && (access.runAttempt <= 0 || access.runAttempt == run.attempt)

        val notExpired = access != null && (access.expiresAtMillis <= 0L || access.expiresAtMillis > nowMillis)
        val fatalStep = progress?.failedStep

        val isCurrentLive = isRunInProgress &&
            runIdMatches &&
            access != null &&
            access.active &&
            notExpired &&
            fatalStep == null

        var validHost: String? = null
        var validPort = 3389
        var transport: String? = null

        if (isCurrentLive && access != null) {
            if (access.mode == "portmap") {
                val pmHost = access.portmapHost.ifBlank { access.tunnelHost }
                val pmPort = if (access.portmapPort > 0) access.portmapPort else access.tunnelPort
                if ((access.portmapStatus == "ok" || access.tunnelStatus == "ok") &&
                    HOSTNAME_REGEX.matches(pmHost) && pmPort in 1..65535
                ) {
                    validHost = pmHost
                    validPort = pmPort
                    transport = "portmap"
                }
            } else {
                val tsIp = access.tailscaleIp.ifBlank { access.tunnelHost }
                if (isValidTailscaleIpv4(tsIp)) {
                    validHost = tsIp
                    validPort = if (access.tunnelPort in 1..65535) access.tunnelPort else 3389
                    transport = "tailscale"
                } else if (access.tunnelHost.isNotBlank() && access.tunnelPort in 1..65535) {
                    validHost = access.tunnelHost
                    validPort = access.tunnelPort
                    transport = access.mode.ifBlank { "tunnel" }
                }
            }
        }

        val phase = when {
            run == null -> XyRdpPhase.IDLE
            run.status == "completed" -> when (run.conclusion) {
                "success" -> XyRdpPhase.STOPPED
                "cancelled" -> XyRdpPhase.CANCELLED
                else -> XyRdpPhase.FAILED
            }
            fatalStep != null -> XyRdpPhase.FAILED
            isRunPending -> XyRdpPhase.QUEUED
            isCurrentLive && validHost != null -> XyRdpPhase.READY
            isRunInProgress -> XyRdpPhase.PREPARING
            else -> XyRdpPhase.STOPPED
        }

        val phaseLabel = when (phase) {
            XyRdpPhase.IDLE -> "Belum ada sesi aktif"
            XyRdpPhase.QUEUED -> "Menunggu runner GitHub Actions…"
            XyRdpPhase.PREPARING -> {
                val step = progress?.currentStep
                val ratio = if ((progress?.totalSteps ?: 0) > 0) {
                    " (${progress?.completedSteps}/${progress?.totalSteps})"
                } else ""
                if (!step.isNullOrBlank()) "Menyiapkan sesi$ratio: $step"
                else "Workflow berjalan — menyiapkan koneksi RDP$ratio…"
            }
            XyRdpPhase.READY -> "ONLINE & TERVALIDASI — Siap Disambungkan"
            XyRdpPhase.FAILED -> if (!fatalStep.isNullOrBlank()) {
                "Gagal pada tahap: $fatalStep"
            } else {
                "Workflow gagal — periksa Secrets atau log GitHub Actions"
            }
            XyRdpPhase.CANCELLED -> "Sesi dibatalkan oleh pengguna"
            XyRdpPhase.STOPPED -> "Sesi telah berakhir / nonaktif"
        }

        val remainingSec = if (isCurrentLive && access != null && access.expiresAtMillis > nowMillis) {
            (access.expiresAtMillis - nowMillis) / 1000L
        } else null

        val validationNote = when {
            phase == XyRdpPhase.READY && validHost != null -> {
                val hb = access?.heartbeatAgeSec
                val hbTxt = if (hb != null && hb >= 0) " · Heartbeat ${hb}d lalu" else ""
                "Terverifikasi cocok dengan Run #${run?.number} ($transport → $validHost:$validPort)$hbTxt"
            }
            isRunInProgress && access != null && !runIdMatches ->
                "Mengabaikan file status lama dari run sebelumnya — menunggu run #${run?.number} mempublikasikan IP baru…"
            ! isRunInProgress && access?.active == true ->
                "File status di branch masih tertulis aktif, tetapi workflow GitHub sudah berhenti (sesi sudah mati)."
            else -> null
        }

        return XyRdpValidatedSession(
            phase = phase,
            phaseLabel = phaseLabel,
            run = run,
            progress = progress,
            access = if (isCurrentLive) access else null,
            isVerifiedLive = phase == XyRdpPhase.READY && validHost != null,
            remainingSeconds = remainingSec,
            validatedHost = validHost,
            validatedPort = validPort,
            validatedTransport = transport,
            validationNote = validationNote,
        )
    }

    internal fun isValidTailscaleIpv4(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false
        return nums[0] == 100 && nums[1] in 64..127
    }

    private fun parseIsoMillis(iso: String): Long {
        val clean = iso.trim()
        if (clean.isEmpty()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        )
        for (p in patterns) {
            runCatching {
                val sdf = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val dt = sdf.parse(clean)
                if (dt != null) return dt.time
            }
        }
        return 0L
    }

    /** Parser murni — mendukung format `akses.tailscale`, `akses.portmap`, maupun `akses.tunnel`. */
    fun parseAccess(json: String, nowMillis: Long = System.currentTimeMillis()): XyRdpAccess? = runCatching {
        val root = JSONObject(json)
        val akses = root.optJSONObject("akses") ?: return@runCatching null
        val mode = akses.optString("mode").ifBlank { "tailscale" }
        val tunnel = akses.optJSONObject("tunnel")
        val tailscale = akses.optJSONObject("tailscale")
        val portmap = akses.optJSONObject("portmap")
        val rustdesk = akses.optJSONObject("rustdesk")
        val hw = root.optJSONObject("hardware")
        val st = root.optJSONObject("statistik")
        val stats = if (st != null) {
            XyRdpStats(
                ramTotalGb = st.optDouble("ram_total_gb", 0.0),
                ramUsedGb = st.optDouble("ram_used_gb", 0.0),
                ramFreeGb = st.optDouble("ram_free_gb", 0.0),
                diskTotalGb = st.optDouble("disk_total_gb", 0.0),
                diskFreeGb = st.optDouble("disk_free_gb", 0.0),
                cpuPercent = st.optInt("cpu_percent", 0),
                uptimeMinutes = st.optInt("uptime_menit", 0),
                updatedAt = st.optString("updated_at"),
            )
        } else null

        val tsIp = tailscale?.optString("ip").orEmpty().ifBlank { root.optString("tailscale_ip") }
        val tsStatus = tailscale?.optString("status").orEmpty()
        val pmHost = portmap?.optString("host").orEmpty()
        val pmPort = portmap?.optInt("port", 0) ?: 0
        val pmStatus = portmap?.optString("status").orEmpty()

        val rawTunnelHost = tunnel?.optString("host").orEmpty()
        val rawTunnelPort = tunnel?.optInt("port", 0) ?: 0
        val rawTunnelAddr = tunnel?.optString("address").orEmpty()

        val effectiveHost = when {
            rawTunnelHost.isNotBlank() -> rawTunnelHost
            mode == "portmap" && pmHost.isNotBlank() -> pmHost
            tsIp.isNotBlank() -> tsIp
            else -> ""
        }
        val effectivePort = when {
            rawTunnelPort > 0 -> rawTunnelPort
            mode == "portmap" && pmPort > 0 -> pmPort
            effectiveHost.isNotBlank() -> root.optInt("rdp_port", 3389).takeIf { it > 0 } ?: 3389
            else -> 0
        }
        val effectiveAddr = when {
            rawTunnelAddr.isNotBlank() -> rawTunnelAddr
            effectiveHost.isNotBlank() && effectivePort > 0 -> "$effectiveHost:$effectivePort"
            else -> ""
        }
        val effectiveStatus = when {
            tunnel?.optString("status")?.isNotBlank() == true -> tunnel.optString("status")
            mode == "portmap" -> pmStatus
            else -> tsStatus
        }

        val expiresAt = root.optString("expires_at")
        val expiresAtMillis = parseIsoMillis(expiresAt)
        val heartbeatAt = root.optString("heartbeat_at").ifBlank { stats?.updatedAt.orEmpty() }
        val hbMillis = parseIsoMillis(heartbeatAt)
        val hbAgeSec = if (hbMillis > 0L && nowMillis >= hbMillis) (nowMillis - hbMillis) / 1000L else null

        XyRdpAccess(
            runId = root.opt("run_id")?.toString().orEmpty(),
            runAttempt = root.optString("run_attempt", "1").toIntOrNull() ?: 1,
            active = root.optBoolean("active", false),
            hostname = root.optString("hostname", "xyrdp"),
            osName = root.optString("os", "Windows Server"),
            startedAt = root.optString("started_at"),
            expiresAt = expiresAt,
            expiresAtMillis = expiresAtMillis,
            heartbeatAt = heartbeatAt,
            heartbeatAgeSec = hbAgeSec,
            rdpUser = root.optString("rdp_user", "xyadmin"),
            mode = mode,
            tunnelStatus = effectiveStatus,
            tunnelAddress = effectiveAddr,
            tunnelHost = effectiveHost,
            tunnelPort = effectivePort,
            selftest = tunnel?.optString("selftest").orEmpty(),
            outside = tunnel?.optString("outside").orEmpty(),
            tailscaleStatus = tsStatus,
            tailscaleIp = tsIp,
            portmapStatus = pmStatus,
            portmapHost = pmHost,
            portmapPort = pmPort,
            rustdeskId = rustdesk?.optString("id").orEmpty(),
            initialCpuThreads = hw?.optInt("cpu_threads", 0) ?: 0,
            initialRamGb = hw?.optDouble("ram_gb", 0.0) ?: 0.0,
            initialDiskFreeGb = hw?.optDouble("disk_free_gb", 0.0) ?: 0.0,
            stats = stats,
            linuxMode = root.optString(
                "linux_mode",
                if (root.optString("os").contains("Polos", ignoreCase = true) || root.optInt("rdp_port", 3389) == 22) "polos" else "desktop",
            ),
        )
    }.getOrNull()
}
