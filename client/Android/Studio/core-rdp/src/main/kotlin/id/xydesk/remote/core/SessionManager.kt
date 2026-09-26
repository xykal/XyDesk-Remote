package id.xydesk.remote.core

import android.content.Context
import android.util.Log
import com.freerdp.freerdpcore.application.GlobalApp
import com.freerdp.freerdpcore.application.SessionState as CoreSession
import com.freerdp.freerdpcore.services.LibFreeRDP
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * M1 — SessionManager: satu pintu untuk seluruh lifecycle sesi RDP XyDesk.
 *
 * Membungkus inti FreeRDP ([GlobalApp], [LibFreeRDP]) dengan:
 *  - state machine eksplisit ([SessionState] via [state])
 *  - callback prompt (sertifikat, credential) dengan safe-default
 *    (tanpa [Listener] = ditolak — aman default, PLAN §5.2)
 *  - input forwarding (cursor/key/unicode/clipboard) untuk HUD (M2)
 *  - [GraphicsSink]: event grafik/pointer diteruskan ke surface XyDesk (M2)
 *  - [telemetry]: sampel 500ms untuk panel Stats HUD (M2)
 *  - pre-flight TCP sebelum connect (M1.2b): kegagalan cepat dengan
 *    diagnosa "unreachable" (RDP off / Windows Home / firewall)
 *
 * Thread:
 *  - [state] thread-safe (StateFlow) — baca dari mana saja,
 *    subscribe di main (UI/Compose).
 *  - Prompt ([Listener.onCertificatePrompt], [Listener.onCredentialsPrompt])
 *    dipanggil di thread RDP native dan BLOCKING sampai `reply(...)`
 *    dipanggil (semantik sama dengan dialog upstream `SessionDialogs`).
 *    UI memanggil `reply` dari thread mana pun.
 *  - `connect()` block terjadi di worker internal, bukan di caller.
 */
class SessionManager(context: Context) {

    interface Listener {
        fun onStateChanged(state: SessionState) {}

        /**
         * Sertifikat TLS server perlu persetujuan. Blok sampai [reply] dipanggil.
         * Default (tanpa listener / timeout): DENY.
         */
        fun onCertificatePrompt(info: CertificateInfo, reply: (Int) -> Unit) {}

        /**
         * Server minta credential (NLA/CredSSP). Blok sampai [reply] dipanggil.
         * [username]/[domain] berisi prefill dari profil.
         */
        fun onCredentialsPrompt(
            username: String?,
            domain: String?,
            reply: (username: String?, domain: String?, password: String?) -> Unit,
        ) {}

        /** Clipboard teks dari remote (dipanggil di thread RDP — post ke main). */
        fun onRemoteClipboardText(text: String) {}
    }

    private val appContext = context.applicationContext
    private val worker: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "xydesk-rdp") }
    /** Serializes native connect-start against release/free of the same instance. */
    private val lifecycleLock = Any()

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Tahap koneksi (di-update dari worker + event listener; baca di UI). */
    private val _stage = MutableStateFlow(Stage.IDLE)
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    private var watchdogToken = 0

    @Volatile private var core: CoreSession? = null
    @Volatile private var listener: Listener? = null
    @Volatile private var sink: GraphicsSink? = null
    @Volatile private var released = false
    @Volatile private var lastProfile: ConnectionProfile? = null

    // Telemetry (M2)
    private val frameCounter = AtomicInteger(0)
    @Volatile private var resolution = intArrayOf(0, 0)

    /** Versi FreeRDP native (untuk about/diagnostics). */
    fun freeRdpVersion(): String = LibFreeRDP.getVersion()

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /** Sink grafik — wajib di-set SEBELUM [connect] agar tidak ada frame yang hilang. */
    fun setGraphicsSink(sink: GraphicsSink?) {
        this.sink = sink
    }

    /** Instance native sesi aktif, 0L jika tidak ada. */
    fun instance(): Long = core?.getInstance() ?: 0L

    // ------------------------------------------------------------------
    // Telemetry (M2)
    // ------------------------------------------------------------------

    /**
     * Sampel telemetri tiap [TELEMETRY_INTERVAL_MS] — dikoleksi oleh UI
     * (collectAsState) selama composition hidup. `fps` = update grafik
     * per detik (window 500ms, diekstrapolasi 2x).
     */
    val telemetry: Flow<TelemetrySample> = flow {
        while (true) {
            val frames = frameCounter.getAndSet(0)
            val r = resolution
            emit(TelemetrySample(state.value, r[0], r[1], frames * 2))
            delay(TELEMETRY_INTERVAL_MS)
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Mulai koneksi (idempotent: dipanggil saat sudah Connecting/Connected
     * = diabaikan dengan log).
     *
     * Pre-flight: probe TCP ke [ConnectionProfile.host]:port. Gagal probe
     * = Error("unreachable") tanpa menyentuh native (diagnosa cepat:
     * RDP off / Windows Home / firewall / host salah).
     */
    fun connect(profile: ConnectionProfile) {
        val session = synchronized(lifecycleLock) {
            if (released) return
            val cur = _state.value
            if (cur is SessionState.Connected || cur is SessionState.Connecting ||
                cur is SessionState.Authenticating
            ) {
                Log.w(TAG, "connect() diabaikan, state=$cur")
                return
            }
            val uri = RdpUri.build(profile)
            // Never log the RDP URI: its query may contain the plaintext password.
            ConnectionLog.add("CM: native createSession mulai (${profile.host}:${profile.port})")
            val created = GlobalApp.createSession(uri, appContext)
            val inst = created.getInstance()
            ConnectionLog.add("CM: createSession ok inst=$inst")
            created.setUIEventListener(uiListenerFor(inst))
            GlobalApp.registerSessionListener(inst, coreListenerFor(inst))
            core = created
            lastProfile = profile
            transition(SessionState.Connecting)
            setStage(Stage.PROBE)
            startWatchdog(inst)
            logConnectStart(profile)
            created
        }
        val inst = session.getInstance()
        worker.execute {
            val reachable = tcpReachable(profile.host, profile.port, TCP_PROBE_TIMEOUT_MS)
            if (!isCurrent(inst)) return@execute
            ConnectionLog.add(
                if (reachable) "TCP ${profile.host}:${profile.port} OK"
                else "TCP ${profile.host}:${profile.port} GAGAL"
            )
            if (!reachable) {
                transition(
                    SessionState.Error(
                        ERROR_UNREACHABLE,
                        "Tidak bisa menghubungi ${profile.host}:${profile.port}. Kemungkinan: " +
                            "RDP nonaktif, firewall memblokir, nama host salah, atau target " +
                            "Windows Home (tidak punya server RDP). Coba Cloud RDP.",
                    )
                )
                return@execute
            }
            try {
                synchronized(lifecycleLock) {
                    if (!isCurrent(inst) || released) return@execute
                    setStage(Stage.HANDSHAKE)
                    ConnectionLog.add("CM: worker: session.connect mulai (native parse args + spawn connect thread)")
                    session.connect(appContext) // native worker starts before lock is released
                    ConnectionLog.add("CM: worker: session.connect kembali — connect thread jalan")
                }
            } catch (t: Throwable) {
                ConnectionLog.add("CM: worker: session.connect EXCEPTION: ${t.javaClass.name}: ${t.message}")
                Log.w(TAG, "connect() exception", t)
                if (isCurrent(inst)) {
                    transition(SessionState.Error("connect_exception", t.message ?: "exception"))
                }
            }
        }
    }

    /** Batalkan koneksi yang sedang berjalan (tanpa menunggu timeout server). */
    fun cancelConnection() {
        stopWatchdog()
        val inst = core?.getInstance() ?: return
        if (!LibFreeRDP.cancelConnection(inst)) {
            Log.w(TAG, "cancelConnection() gagal (state mungkin sudah terminal)")
        }
        // Jaga-jaga: kalau event failure tidak datang, jangan stuck di Connecting
        mainHandler.postDelayed({
            val s = _state.value
            if (isCurrent(inst) && (s is SessionState.Connecting || s is SessionState.Authenticating)) {
                transition(SessionState.Error("cancelled", "Koneksi dibatalkan"))
            }
        }, CANCEL_FALLBACK_MS)
    }

    /** Ajaikan disconnect normal (dari sisi kita). */
    fun disconnect() {
        stopWatchdog()
        val inst = core?.getInstance() ?: return
        transition(SessionState.Disconnecting)
        if (!LibFreeRDP.disconnect(inst)) {
            // native menolak — anggap sudah putus
            if (isCurrent(inst)) transition(SessionState.Disconnected)
        }
    }

    /**
     * Lepas sesi + resource. Panggil saat Activity selesai (onDestroy).
     * Sesudah ini [SessionManager] tidak bisa dipakai lagi.
     */
    fun release() {
        synchronized(lifecycleLock) {
            if (released) return
            released = true
            stopWatchdog()
            val inst = core?.getInstance()
            core = null
            sink = null
            listener = null
            if (inst != null && inst != 0L) {
                GlobalApp.unregisterSessionListener(inst)
                try {
                    LibFreeRDP.disconnect(inst)
                } catch (t: Throwable) {
                    Log.w(TAG, "disconnect on release gagal", t)
                }
                // Free only after any in-flight session.connect() call has
                // crossed the same lock and registered the native worker.
                GlobalApp.freeSession(inst)
            }
            worker.shutdownNow()
        }
        if (_state.value is SessionState.Connected || _state.value is SessionState.Connecting ||
            _state.value is SessionState.Authenticating || _state.value is SessionState.Disconnecting
        ) {
            transition(SessionState.Idle)
        }
    }

    // ------------------------------------------------------------------
    // Input forwarding (dipakai HUD M2; pass-through tipis ke native)
    // ------------------------------------------------------------------

    fun sendCursorEvent(x: Int, y: Int, flags: Int): Boolean {
        val inst = core?.getInstance() ?: return false
        return LibFreeRDP.sendCursorEvent(inst, x, y, flags)
    }

    fun sendKeyEvent(keycode: Int, down: Boolean): Boolean {
        val inst = core?.getInstance() ?: return false
        return LibFreeRDP.sendKeyEvent(inst, keycode, down)
    }

    fun sendUnicodeKeyEvent(code: Int, down: Boolean): Boolean {
        val inst = core?.getInstance() ?: return false
        return LibFreeRDP.sendUnicodeKeyEvent(inst, code, down)
    }

    /** Ketik string sebagai unicode key events (satu char = down+up). */
    fun sendText(text: String) {
        val inst = core?.getInstance() ?: return
        for (ch in text) {
            LibFreeRDP.sendUnicodeKeyEvent(inst, ch.code, true)
            LibFreeRDP.sendUnicodeKeyEvent(inst, ch.code, false)
        }
    }

    fun sendClipboardData(data: String): Boolean {
        val inst = core?.getInstance() ?: return false
        return LibFreeRDP.sendClipboardData(inst, data)
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private fun setStage(st: Stage) {
        if (_stage.value != st) {
            _stage.value = st
            ConnectionLog.add("stage -> $st")
        }
    }

    private fun startWatchdog(inst: Long) {
        val token = ++watchdogToken
        mainHandler.postDelayed({
            if (token != watchdogToken) return@postDelayed
            val s = _state.value
            if (isCurrent(inst) && (s is SessionState.Connecting || s is SessionState.Authenticating)) {
                stopWatchdog()
                ConnectionLog.add("WATCHDOG: koneksi menggantung >${CONNECT_WATCHDOG_MS}ms")
                transition(
                    SessionState.Error(
                        "connect_timeout",
                        "Koneksi menggantung lebih dari ${CONNECT_WATCHDOG_MS / 1000} detik. " +
                            "Kemungkinan: firewall memblokir setelah TCP, server lambat, atau " +
                            "NLA/TLS tidak selesai. Tekan Detail untuk log.",
                    )
                )
            }
        }, CONNECT_WATCHDOG_MS)
    }

    private fun stopWatchdog() {
        watchdogToken++
    }

    private fun logConnectStart(p: ConnectionProfile) {
        ConnectionLog.add("connect mulai -> ${p.host}:${p.port} user=${p.username ?: "(kosong)"}")
    }

    private fun transition(s: SessionState) {
        val prev = _state.value
        _state.value = s
        if (prev != s) {
            Log.v(TAG, "state: $prev -> $s")
            ConnectionLog.add("state: $prev -> $s${(s as? SessionState.Error)?.let { " [${it.code}] ${it.message}" } ?: ""}")
            listener?.onStateChanged(s)
        }
    }

    private fun isCurrent(inst: Long): Boolean = core?.getInstance() == inst

    /** Probe TCP sederhana (juga gagal saat DNS/resolver gagal). */
    private fun tcpReachable(host: String, port: Int, timeoutMs: Long): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs.toInt())
        }
        true
    } catch (t: Throwable) {
        false
    }

    private fun coreListenerFor(inst: Long): GlobalApp.SessionEventListener =
        object : GlobalApp.SessionEventListener {
            // Callback sudah di-dispatch ke main thread oleh GlobalApp
            override fun onConnectionSuccess() {
                if (!isCurrent(inst)) return
                stopWatchdog()
                setStage(Stage.READY)
                ConnectionLog.add("koneksi SUKSES")
                transition(SessionState.Connected)
            }

            override fun onConnectionFailure() {
                if (!isCurrent(inst)) return
                stopWatchdog()
                ConnectionLog.add("koneksi GAGAL (event native)")
                val p = lastProfile
                val msg = if (p != null) {
                    "Gagal koneksi ke ${p.host}:${p.port} — cek kredensial, " +
                        "firewall, dan pastikan RDP aktif di host"
                } else {
                    "Gagal koneksi"
                }
                transition(SessionState.Error("connect_failed", msg))
            }

            override fun onDisconnected() {
                if (!isCurrent(inst)) return
                stopWatchdog()
                setStage(Stage.IDLE)
                ConnectionLog.add("disconnect (event native)")
                transition(SessionState.Disconnected)
            }
        }

    /**
     * Implementasi UIEventListener inti. Event grafik/pointer diteruskan
     * ke [sink] (surface XyDesk, M2); prompt = safe-default.
     */
    private fun uiListenerFor(inst: Long): LibFreeRDP.UIEventListener =
        object : LibFreeRDP.UIEventListener {

            override fun OnSettingsChanged(width: Int, height: Int, bpp: Int) {
                resolution = intArrayOf(width, height)
                Log.v(TAG, "settings ${width}x${height} @${bpp}bpp")
            }

            override fun OnAuthenticate(
                username: StringBuilder,
                domain: StringBuilder,
                password: StringBuilder,
            ): Boolean {
                val l = listener
                if (l == null) {
                    Log.w(TAG, "NLA prompt tanpa listener — tolak (safe default)")
                    return false
                }
                if (!isCurrent(inst)) return false
                setStage(Stage.AUTH)
                ConnectionLog.add("prompt NLA/credential (${username.toString()})")
                transition(SessionState.Authenticating)
                val latch = CountDownLatch(1)
                var ok = false
                l.onCredentialsPrompt(
                    username.toString(),
                    domain.toString(),
                ) { u, d, p ->
                    if (u != null) {
                        username.setLength(0); username.append(u)
                    }
                    if (d != null) {
                        domain.setLength(0); domain.append(d)
                    }
                    if (p != null) {
                        password.setLength(0); password.append(p)
                    }
                    // reply tanpa satu pun nilai = user batal -> tolak
                    ok = (u != null || d != null || p != null)
                    latch.countDown()
                }
                latch.await(PROMPT_TIMEOUT_SEC, TimeUnit.SECONDS)
                return ok
            }

            override fun OnGatewayAuthenticate(
                username: StringBuilder,
                domain: StringBuilder,
                password: StringBuilder,
            ): Boolean = OnAuthenticate(username, domain, password)

            override fun OnVerifiyCertificateEx(
                host: String,
                port: Long,
                commonName: String,
                subject: String,
                issuer: String,
                fingerprint: String,
                flags: Long,
            ): Int = certificatePrompt(
                CertificateInfo(host, port.toInt(), commonName, subject, issuer, fingerprint, flags)
            )

            override fun OnVerifyChangedCertificateEx(
                host: String,
                port: Long,
                commonName: String,
                subject: String,
                issuer: String,
                fingerprint: String,
                oldSubject: String,
                oldIssuer: String,
                oldFingerprint: String,
                flags: Long,
            ): Int {
                // same prompt; FLAG_CHANGED sudah di-set inti — UI menampilkan
                // warning "sertifikat berubah" + fingerprint lama vs baru.
                return certificatePrompt(
                    CertificateInfo(host, port.toInt(), commonName, subject, issuer,
                                    fingerprint, flags)
                )
            }

            override fun OnExperimentalFeature(feature: Int): Boolean = true

            override fun OnGraphicsUpdate(x: Int, y: Int, width: Int, height: Int) {
                frameCounter.incrementAndGet()
                sink?.onGraphicsUpdate(x, y, width, height)
            }

            override fun OnGraphicsResize(width: Int, height: Int, bpp: Int) {
                resolution = intArrayOf(width, height)
                sink?.onGraphicsResize(width, height, bpp)
            }

            override fun OnRemoteClipboardChanged(data: String) {
                listener?.onRemoteClipboardText(data)
            }

            override fun OnRemoteClipboardImageChanged(data: ByteArray?) {
                // M3: file/image clipboard
            }

            override fun OnPointerSet(
                pixels: IntArray?,
                width: Int,
                height: Int,
                hotX: Int,
                hotY: Int,
            ) {
                sink?.onPointerSet(pixels, width, height, hotX, hotY)
            }

            override fun OnPointerSetNull() {
                sink?.onPointerSetNull()
            }

            override fun OnPointerSetDefault() {
                sink?.onPointerSetDefault()
            }

            override fun OnRailWindowUpdate(windowId: Long, width: Int, height: Int, pixels: IntArray?) {}
            override fun OnRailWindowMove(
                windowId: Long,
                x: Int,
                y: Int,
                w: Int,
                h: Int,
            ) {}
            override fun OnRailWindowHide(windowId: Long) {}
            override fun OnRailWindowDestroy(windowId: Long) {}
            override fun OnRailSessionEnd() {}
            override fun OnRailMonitoredDesktop(windowIds: LongArray?, activeWindowId: Long) {}
        }

    /** Prompt sertifikat — blocking (thread RDP), safe default = DENY. */
    private fun certificatePrompt(info: CertificateInfo): Int {
        ConnectionLog.add("prompt sertifikat ${info.host}:${info.port} fp=${info.fingerprint.take(32)}… flags=${info.flags}")
        val l = listener
        if (l == null) {
            Log.w(TAG, "cert prompt untuk ${info.host}:${info.port} tanpa listener — DENY")
            return CertificateInfo.VERIFY_DENY
        }
        val latch = CountDownLatch(1)
        var result = CertificateInfo.VERIFY_DENY
        l.onCertificatePrompt(info) {
            result = it
            latch.countDown()
        }
        latch.await(PROMPT_TIMEOUT_SEC, TimeUnit.SECONDS)
        return result
    }

    /** Tahap koneksi — untuk progres UI + diagnosa. */
    enum class Stage { IDLE, PROBE, HANDSHAKE, AUTH, READY }

    companion object {
        private const val TAG = "XyDeskSession"
        private const val PROMPT_TIMEOUT_SEC = 120L
        private const val CANCEL_FALLBACK_MS = 5_000L
        private const val TCP_PROBE_TIMEOUT_MS = 2_500L
        private const val TELEMETRY_INTERVAL_MS = 500L
        private const val CONNECT_WATCHDOG_MS = 30_000L

        /** Code [SessionState.Error] untuk probe TCP gagal (host tak terjangkau). */
        const val ERROR_UNREACHABLE = "unreachable"

        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    }
}
