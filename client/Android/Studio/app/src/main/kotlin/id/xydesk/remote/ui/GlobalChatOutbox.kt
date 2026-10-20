package id.xydesk.remote.ui

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

internal data class OutboxSnapshot(val binding: String, val entries: List<OutboxEntry>, val issue: String?, val readFailed: Boolean = false, val verifiedOwner: Long = 0)
internal data class OutboxAcceptance(val login: String, val binding: String, val localId: String, val receipt: JSONObject, val message: JSONObject?)
private data class OutboxSession(val login: String, val token: String, val binding: String)
private data class OutboxResponse(val status: Int, val body: JSONObject, val retrySeconds: Long)

/** No token is serialized. All network/disk work is off the main thread. */
object GlobalChatOutbox {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableRevision = MutableStateFlow(0L)
    internal val revision = mutableRevision.asStateFlow()
    private val mutableAccepted = MutableSharedFlow<OutboxAcceptance>(extraBufferCapacity = 2)
    internal val accepted = mutableAccepted.asSharedFlow()
    @Volatile private var singleton: ChatOutboxStore? = null
    @Volatile private var identity: Pair<String, Long>? = null
    private const val JOB_ID = 0x58594348

    @Synchronized private fun store(context: Context): ChatOutboxStore = singleton ?: ChatOutboxStore(
        AndroidChatOutboxDisk(context.applicationContext), ::chatOutboxKey).also { singleton = it }
    @Synchronized private fun changed() { mutableRevision.value += 1 }
    private fun session(context: Context): OutboxSession? {
        val login = getVerifiedGitHubLogin(context)?.takeIf { it.isNotBlank() } ?: return null
        val token = GlobalChatRepository.token(context)?.takeIf { it.isNotBlank() } ?: return null
        return OutboxSession(login, token, ChatOutboxPolicy.fingerprint(token))
    }
    private fun current(context: Context, before: OutboxSession): Boolean = session(context)?.let {
        it.binding == before.binding && normalizedChatAccount(it.login) == normalizedChatAccount(before.login)
    } == true
    private fun owner(binding: String): Long = identity?.takeIf { it.first == binding }?.second ?: 0

    internal fun snapshot(context: Context): OutboxSnapshot {
        val who = session(context) ?: return OutboxSnapshot("", emptyList(), null)
        return try {
            val all = store(context).entries()
            val visible = all.filter { ChatOutboxPolicy.belongsTo(it, who.binding, owner(who.binding)) }
            val pending = visible.filter { it.state == "pending" }
            val held = all.any { it.state == "pending" && it.ownerId == 0L && it.credentialFingerprint != who.binding &&
                normalizedChatAccount(it.login) == normalizedChatAccount(who.login) }
            val issue = when {
                pending.any { it.nextAttemptAt == Long.MAX_VALUE } -> "Ada pesan tertahan (${pending.first { it.nextAttemptAt == Long.MAX_VALUE }.issue}). Salin untuk pemulihan; tidak dikirim ulang otomatis."
                pending.isNotEmpty() -> "Antrean tersimpan: ${pending.size} pesan · ${pending.first().issue.ifBlank { "menunggu konfirmasi server" }}"
                held -> "Antrean lama belum dapat dipastikan identitasnya setelah token berubah; tidak dikirim otomatis."
                else -> null
            }
            OutboxSnapshot(who.binding, visible, issue, verifiedOwner = owner(who.binding))
        } catch (_: Exception) { OutboxSnapshot(who.binding, emptyList(), "Antrean lokal belum bisa dibuka. Data tidak dihapus; jangan hapus data aplikasi.", readFailed = true) }
    }

    internal fun enqueue(context: Context, expectedLogin: String, expectedBinding: String, body: JSONObject, at: Long) {
        val who = session(context) ?: error("Login kembali sebelum menyimpan kiriman.")
        check(expectedBinding.isNotBlank() && who.binding == expectedBinding &&
            normalizedChatAccount(who.login) == normalizedChatAccount(expectedLogin)) { "Akun berubah; kiriman tidak diteruskan." }
        val entry = OutboxEntry(body.getString("client_msg_id"), who.login, who.binding, at, body.toString(), owner(who.binding))
        store(context).enqueue(entry) // verified durable commit BEFORE any network release
        changed()
        schedule(context, 0)
        resume(context)
    }

    private fun requireSession(context: Context, expectedLogin: String, expectedBinding: String): OutboxSession {
        val who = session(context) ?: error("Login GitHub kembali diperlukan.")
        check(expectedBinding.isNotBlank() && who.binding == expectedBinding &&
            normalizedChatAccount(who.login) == normalizedChatAccount(expectedLogin)) { "Akun berubah; tindakan dibatalkan." }
        return who
    }

    /** Does not send any queued message; used to reveal same-ID queues after credential rotation. */
    internal fun checkAccount(context: Context, expectedLogin: String, expectedBinding: String): Long {
        val who = requireSession(context, expectedLogin, expectedBinding)
        val result = request("send-capabilities", who.token)
        val cap = result.body
        check(current(context, who)) { "Akun berubah; identitas tidak diterapkan." }
        check(result.status == 200 && cap.optInt("send_protocol") == 1 && cap.optLong("author_id") > 0 &&
            cap.optLong("replay_max_age_ms") == ChatOutboxPolicy.MAX_AGE_MS &&
            cap.optString("github_login").equals(who.login, ignoreCase = true)) {
            "Identitas/protokol server belum terverifikasi. Periksa koneksi atau login kembali."
        }
        return cap.getLong("author_id").also { identity = who.binding to it; changed() }
    }

    internal fun retryPending(context: Context, id: String, expectedLogin: String, expectedBinding: String): String {
        val who = requireSession(context, expectedLogin, expectedBinding)
        val original = store(context).entries().firstOrNull { it.id == id } ?: error("Antrean sudah berubah.")
        check(ChatOutboxControlPolicy.retryState(original, who.binding, owner(who.binding), System.currentTimeMillis())
            in setOf(OutboxRetryState.READY, OutboxRetryState.NEEDS_LOGIN)) { "Status/akun berubah atau waktu tunggu belum selesai." }
        val verifiedOwner = checkAccount(context, expectedLogin, expectedBinding)
        requireSession(context, expectedLogin, expectedBinding)
        val result = store(context).update(id) { e ->
            if (e.state != "pending") e else ChatOutboxControlPolicy.retry(e, who.binding, verifiedOwner, System.currentTimeMillis())
        } ?: error("Antrean sudah berubah.")
        changed()
        if (result.state == "pending") { schedule(context, 0); resume(context) }
        return result.state
    }

    /** Durable result, not a fire-and-forget deletion promise. Null means no local record exists. */
    internal fun cancelPending(context: Context, id: String, expectedLogin: String, expectedBinding: String): OutboxEntry? {
        val who = requireSession(context, expectedLogin, expectedBinding)
        val result = store(context).update(id) { e -> ChatOutboxControlPolicy.cancel(e, who.binding, owner(who.binding)) }
        changed()
        return result
    }

    internal fun pendingText(context: Context, id: String, expectedLogin: String, expectedBinding: String): String {
        val who = requireSession(context, expectedLogin, expectedBinding)
        val entry = store(context).entries().firstOrNull { it.id == id } ?: error("Antrean sudah berubah.")
        val text = ChatOutboxControlPolicy.copyText(entry, who.binding, owner(who.binding))
        requireSession(context, expectedLogin, expectedBinding)
        return text
    }

    @JvmStatic fun resume(context: Context) {
        val app = context.applicationContext
        scope.launch {
            changed() // Refresh credential-scoped UI even when every pending row is paused.
            val delay = runCatching { flush(app) }.getOrElse { 300_000L }
            if (delay != null) schedule(app, delay)
        }
    }

    internal fun schedule(context: Context, delay: Long) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, ChatOutboxJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(delay.coerceAtLeast(0))
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
        }
    }

    private fun request(path: String, token: String, body: JSONObject? = null): OutboxResponse {
        val conn = URL("https://api.xydeskremote.biz.id/api/chat/$path").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000; conn.readTimeout = 10_000
            conn.requestMethod = if (body == null) "GET" else "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val input = if (status in 200..299) conn.inputStream else conn.errorStream
            val raw = input?.use { String(it.readChatBytesAtMost(3_200_000), Charsets.UTF_8) }.orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            return OutboxResponse(status, parsed, conn.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(0, 86400) ?: 0)
        } finally { conn.disconnect() }
    }

    /** Serialized transport, but store lock is never held over network I/O (cancellation stays responsive). */
    internal suspend fun flush(context: Context): Long? = mutex.withLock {
        val who = session(context) ?: return@withLock null
        val now = System.currentTimeMillis()
        var pending = store(context).entries().filter { it.state == "pending" }
        if (pending.isEmpty()) return@withLock null
        fun due(e: OutboxEntry) = e.retryNotBefore <= now &&
            (e.nextAttemptAt <= now || e.issue == "authentication_required" && e.lastAttemptFingerprint != who.binding)
        if (pending.none { due(it) }) return@withLock nextDelay(pending, who)
        val capability = try { request("send-capabilities", who.token) }
            catch (_: Exception) { return@withLock 300_000L }
        coroutineContext.ensureActive()
        if (!current(context, who)) return@withLock null
        if (capability.status == 401 || capability.status == 403) {
            pending.filter { ChatOutboxPolicy.belongsTo(it, who.binding, owner(who.binding)) }.forEach { e ->
                store(context).update(e.id) { old -> if (old.state != "pending") old else old.copy(issue = "authentication_required",
                    nextAttemptAt = Long.MAX_VALUE, lastAttemptFingerprint = who.binding,
                    retryNotBefore = maxOf(old.retryNotBefore, now + capability.retrySeconds * 1000)) }
            }
            changed(); return@withLock null
        }
        val cap = capability.body
        val verified = cap.optLong("author_id")
        if (capability.status != 200 || cap.optInt("send_protocol") != 1 || verified <= 0 ||
            cap.optLong("replay_max_age_ms") != ChatOutboxPolicy.MAX_AGE_MS ||
            !cap.optString("github_login").equals(who.login, ignoreCase = true)) {
            pending.filter { ChatOutboxPolicy.belongsTo(it, who.binding, owner(who.binding)) }.forEach { e ->
                store(context).update(e.id) { old -> if (old.state != "pending") old else old.copy(
                    issue = "server belum mengonfirmasi protokol/identitas", nextAttemptAt = now + maxOf(300_000L, capability.retrySeconds * 1000)) }
            }
            changed(); return@withLock maxOf(300_000L, capability.retrySeconds * 1000)
        }
        identity = who.binding to verified; changed()
        pending = store(context).entries().filter { it.state == "pending" && ChatOutboxPolicy.belongsTo(it, who.binding, verified) }
        for (queued in pending.filter { due(it) }.take(3)) {
            coroutineContext.ensureActive()
            if (!current(context, who)) return@withLock null
            val entry = store(context).update(queued.id) { old ->
                if (old.state == "pending") old.copy(ownerId = verified) else old
            } ?: continue
            if (entry.state != "pending") continue
            if (System.currentTimeMillis() - entry.createdAt >= ChatOutboxPolicy.MAX_AGE_MS) {
                store(context).update(entry.id) { old -> if (old.state != "pending") old else old.copy(
                    issue = "outbox_expired", nextAttemptAt = Long.MAX_VALUE) }; changed(); continue
            }
            val payload = JSONObject(entry.payload).put("github_login", cap.getString("github_login"))
                .put("outbox_v", 1).put("outbox_author_id", verified).put("outbox_created_at", entry.createdAt)
            val result = try { request("send", who.token, payload) } catch (_: Exception) { null }
            coroutineContext.ensureActive()
            val receipt = result?.takeIf { it.status == 200 }?.let { ChatOutboxPolicy.receipt(it.body, entry, verified) }
            if (receipt != null) {
                // Replaces payload with metadata atomically. No view-once/media export or stale plaintext journal.
                val accepted = store(context).update(entry.id) { old -> if (old.state != "pending") old else old.copy(
                    state = "accepted", payload = "", receipt = receipt.toString(), issue = "", nextAttemptAt = 0) }
                if (accepted?.state == "accepted") {
                    if (current(context, who)) id.xydesk.remote.push.ChatPushNotifier.markSeen(receipt.getString("id"), receipt.optLong("at"), context)
                    mutableAccepted.emit(OutboxAcceptance(who.login, who.binding, entry.id,
                        receipt, result?.body?.optJSONObject("message")))
                }
            } else {
                val code = result?.body?.optString("code")?.take(80)?.ifBlank { null } ?: "menunggu jaringan/konfirmasi"
                val status = result?.status ?: 0
                val blocked = status in setOf(400, 401, 403, 409, 410, 413, 415, 422)
                store(context).update(entry.id) { old -> if (old.state != "pending") old else old.copy(
                    attempts = (old.attempts + 1).coerceAtMost(1000),
                    issue = if (status == 401 || status == 403) "authentication_required" else code,
                    lastAttemptFingerprint = who.binding,
                    retryNotBefore = maxOf(old.retryNotBefore, System.currentTimeMillis() + (result?.retrySeconds ?: 0) * 1000),
                    nextAttemptAt = if (blocked) Long.MAX_VALUE else System.currentTimeMillis() +
                        ChatOutboxPolicy.delayMs(old.attempts, result?.retrySeconds ?: 0)) }
            }
            changed()
        }
        nextDelay(store(context).entries().filter { it.state == "pending" }, who)
    }

    private fun nextDelay(entries: List<OutboxEntry>, who: OutboxSession): Long? = entries
        .filter {
            (ChatOutboxPolicy.belongsTo(it, who.binding, owner(who.binding)) ||
                owner(who.binding) == 0L && it.ownerId > 0) &&
                (it.nextAttemptAt != Long.MAX_VALUE || it.issue == "authentication_required" && it.lastAttemptFingerprint != who.binding)
        }
        .minOfOrNull {
            val eligible = if (it.issue == "authentication_required" && it.lastAttemptFingerprint != who.binding) it.retryNotBefore
                else maxOf(it.nextAttemptAt, it.retryNotBefore)
            (eligible - System.currentTimeMillis()).coerceAtLeast(1_000)
        }
}
