package id.xydesk.remote.pcstream

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Endpoint media PC-stream produksi di atas [XyHubSignalingSession]:
 * PeerConnection (video RECV_ONLY + mic nonaktif ala klien referensi
 * `RtcSession.kt`), data channel `input` dengan `DataChannel.Init()` default
 * (ordered/reliable — sesuai dekoder host `input.rs`), dan pengiriman input
 * lewat [XyHostInputCodec].
 *
 * ```text
 * start(hostId, pin, name) ── onboarding + pair (lihat XyHubSignalingSession)
 *   └─ onPairAccepted → ICE (STUN cloudflare + TURN /turn-ice) → offer
 *        host answer/ICE balik → media + channel input terbuka
 * sendInputPacket(bytes) → channel `input` → host XyDesk
 * ```
 *
 * Seluruh callback listener dipanggil di **thread utama** supaya UI Compose
 * tinggal memakainya. Kelas ini tipis di atas komponen yang sudah teruji
 * (session/codec/transport) — bagian WebRTC-nya sendiri tidak bisa diuji JVM
 * (native), jadi mengikuti pola [PcStreamViewer] yang sudah dipakai modul ini.
 */
class XyHubStreamViewer(
    context: Context,
    private val eglContext: EglBase.Context,
    private val listener: Listener,
    baseUrl: String = XyHubAuth.DEFAULT_BASE_URL,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** ID perangkat klien untuk sesi ini (sekali pakai, seperti klien referensi). */
    val deviceId: String = XyHubIds.generateClientId()

    private val session = XyHubSignalingSession(
        onboarding = XyHubAuth(baseUrl),
        transportFactory = { XyHubOkHttpTransport() },
        listener = SessionListener(),
        deviceId = deviceId,
    )

    private val resourceLock = ReentrantLock()
    private val videoLock = Any()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val remoteDescriptionSet = AtomicBoolean(false)

    @Volatile private var peerConnectionFactory: PeerConnectionFactory? = null
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var inputChannel: DataChannel? = null
    @Volatile private var remoteVideoTrack: VideoTrack? = null
    @Volatile private var micAudioSource: org.webrtc.AudioSource? = null
    @Volatile private var hostId: String = ""
    private var videoSink: VideoSink? = null
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    /** Event viewer untuk UI sesi. Semua callback di thread utama. */
    interface Listener {
        /** Passthrough status sesi signaling (termasuk CONNECTING/PAIRING/…). */
        fun onViewerState(state: XyHubSessionState, detail: String?)

        /** Pairing diterima host — offer terkirim, menunggu answer. */
        fun onPairAccepted(hostId: String)

        /** Pairing ditolak (PIN salah / host offline) — boleh ulangi. */
        fun onPairRejected(reason: String?)

        /** Rem anti-penebak aktif. */
        fun onPairBlocked(retryInSeconds: Long)

        /** Track video host siap dirender (atau null saat lepas). */
        fun onVideoTrack(track: VideoTrack?)

        /** Channel `input` terbuka/tertutup — input bisa dikirim saat terbuka. */
        fun onInputChannelOpen(open: Boolean)

        /** Status media ICE (true = tersambung, false = putus). */
        fun onMediaConnected(connected: Boolean)

        /** Pesan teks JSON dari host di channel input (meta hardware/displays/gamepad). */
        fun onHostText(text: String)

        fun onViewerFailure(error: Throwable)

        fun onSignalingClosed(code: Int, reason: String)
    }

    /** Mulai onboarding + pairing + negosiasi media. Sekali per viewer. */
    suspend fun start(hostId: String, pin: String, name: String?, refreshToken: String? = null) {
        check(started.compareAndSet(false, true)) { "Viewer sudah pernah start" }
        check(!closed.get()) { "Viewer sudah ditutup" }
        this.hostId = XyHubIds.requireHostId(hostId)
        session.start(this.hostId, pin, name, refreshToken)
    }

    /** Ulangi pairing setelah penolakan — tanpa onboarding ulang. */
    fun retryPair(pin: String, name: String?) {
        session.pair(hostId, pin, name)
    }

    /**
     * Kirim satu paket input hasil [XyHostInputCodec] (biner, channel `input`).
     * `false` bila channel belum terbuka — pemanggil boleh mengabaikan (input
     * adalah snapshot state, bukan antrean).
     */
    fun sendInputPacket(packet: ByteArray): Boolean {
        if (closed.get() || packet.isEmpty() || packet.size > MAX_INPUT_PACKET_BYTES) return false
        val channel = inputChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return try {
            channel.send(DataChannel.Buffer(ByteBuffer.wrap(packet), true))
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Pasang / lepas renderer video tanpa renegosiasi. */
    fun setVideoSink(sink: VideoSink?) {
        synchronized(videoLock) {
            val safeSink = if (closed.get()) null else sink
            val track = remoteVideoTrack
            val old = videoSink
            if (old === safeSink) return
            if (track != null && old != null) track.removeSink(old)
            videoSink = safeSink
            if (track != null && safeSink != null) track.addSink(safeSink)
        }
    }

    /** Bye ke host + tutup signaling + lepas WebRTC. */
    fun stop() {
        if (!closed.compareAndSet(false, true)) return
        try {
            session.signaling?.bye(hostId)
        } catch (_: IllegalStateException) {
            // Belum pernah terdaftar — tidak ada yang perlu diucapkan.
        }
        session.stop("viewer-closed")
        releaseResources()
        main.post {
            listener.onInputChannelOpen(false)
            listener.onVideoTrack(null)
        }
    }

    override fun close() = stop()

    // ── Negosiasi media ─────────────────────────────────────────────────────

    private fun negotiate(iceServers: List<XyHubIceServer>) {
        resourceLock.withLock {
            if (closed.get()) return
            try {
                XyHubWebRtc.ensureInitialized(applicationContext)
                val factory = PeerConnectionFactory.builder()
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
                    .createPeerConnectionFactory()
                peerConnectionFactory = factory

                val servers = mutableListOf(
                    PeerConnection.IceServer.builder(DEFAULT_STUN_URL).createIceServer(),
                )
                iceServers.forEach { server ->
                    if (server.urls.isNotEmpty()) {
                        servers += PeerConnection.IceServer.builder(server.urls)
                            .setUsername(server.username.orEmpty())
                            .setPassword(server.credential.orEmpty())
                            .createIceServer()
                    }
                }
                val configuration = PeerConnection.RTCConfiguration(servers).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    iceTransportsType = PeerConnection.IceTransportsType.ALL
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                }
                val peer = factory.createPeerConnection(configuration, PeerObserver())
                    ?: throw IOException("PeerConnection gagal dibuat")
                peerConnection = peer
                peer.setAudioPlayout(true)

                // Susunan transceiver meniru klien referensi RtcSession.kt supaya
                // offer berbentuk sama di mata host produksi.
                peer.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
                ) ?: throw IOException("Transceiver video gagal dibuat")
                val micSource = factory.createAudioSource(MediaConstraints())
                micAudioSource = micSource
                val mic = factory.createAudioTrack("xy-mic", micSource).apply { setEnabled(false) }
                peer.addTransceiver(
                    mic,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV),
                )

                val channel = peer.createDataChannel(INPUT_CHANNEL_LABEL, DataChannel.Init())
                    ?: throw IOException("Channel input gagal dibuat")
                attachInputChannel(channel)

                createAndSendOffer(peer)
            } catch (failure: Throwable) {
                fail(failure)
            }
        }
    }

    private fun createAndSendOffer(peer: PeerConnection) {
        peer.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                peer.setLocalDescription(NoopSdpObserver, description)
                session.signaling?.offer(hostId, description.type.canonicalForm(), description.description)
            }

            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) = fail(IOException("Menawar WebRTC gagal: $error"))
            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

    private fun applyRemoteAnswer(sdpType: String, sdp: String) {
        val peer = peerConnection ?: return
        if (!remoteDescriptionSet.compareAndSet(false, true)) return
        peer.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onSetSuccess() {
                val queued = synchronized(pendingRemoteCandidates) {
                    val copy = pendingRemoteCandidates.toList()
                    pendingRemoteCandidates.clear()
                    copy
                }
                queued.forEach { candidate -> peer.addIceCandidate(candidate) }
            }

            override fun onCreateFailure(error: String) = Unit
            override fun onSetFailure(error: String) =
                fail(IOException("Menerapkan jawaban host gagal: $error"))
        }, SessionDescription(SessionDescription.Type.fromCanonicalForm(sdpType), sdp))
    }

    private fun addRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        val peer = peerConnection ?: return
        val index = sdpMLineIndex ?: return
        val wrapped = IceCandidate(sdpMid, index, candidate)
        val addNow = synchronized(pendingRemoteCandidates) {
            if (remoteDescriptionSet.get()) {
                true
            } else {
                pendingRemoteCandidates += wrapped
                false
            }
        }
        if (addNow) peer.addIceCandidate(wrapped)
    }

    private fun attachInputChannel(channel: DataChannel) {
        inputChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                postMain { listener.onInputChannelOpen(channel.state() == DataChannel.State.OPEN) }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                if (!buffer.binary) {
                    postMain { listener.onHostText(String(data, Charsets.UTF_8)) }
                }
                // Biner dari host (wallpaper/clipboard) belum dipakai jalur ini.
            }
        })
    }

    private fun attachRemoteVideoTrack(track: VideoTrack) {
        synchronized(videoLock) {
            if (closed.get() || remoteVideoTrack === track) return
            remoteVideoTrack?.let { previous -> videoSink?.let(previous::removeSink) }
            remoteVideoTrack = track
            videoSink?.let(track::addSink)
        }
        postMain { listener.onVideoTrack(track) }
    }

    private fun releaseResources() {
        resourceLock.withLock {
            inputChannel?.unregisterObserver()
            inputChannel?.close()
            inputChannel?.dispose()
            inputChannel = null
            peerConnection?.close()
            peerConnection = null
            micAudioSource?.dispose()
            micAudioSource = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        }
    }

    private fun fail(failure: Throwable) {
        if (closed.get()) return
        postMain { listener.onViewerFailure(failure) }
    }

    private inline fun postMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    /** Terjemahan event sesi → listener viewer (semua di thread utama). */
    private inner class SessionListener : XyHubSignalingSession.Listener {
        override fun onSessionState(state: XyHubSessionState, detail: String?) {
            postMain { listener.onViewerState(state, detail) }
        }

        override fun onPairAccepted(hostId: String, iceServers: List<XyHubIceServer>) {
            postMain { listener.onPairAccepted(hostId) }
            negotiate(iceServers)
        }

        override fun onPairRejected(reason: String?) {
            postMain { listener.onPairRejected(reason) }
        }

        override fun onPairBlocked(retryInSeconds: Long) {
            postMain { listener.onPairBlocked(retryInSeconds) }
        }

        override fun onRemoteAnswer(sdpType: String, sdp: String) {
            applyRemoteAnswer(sdpType, sdp)
        }

        override fun onRemoteIce(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
            addRemoteCandidate(candidate, sdpMid, sdpMLineIndex)
        }

        override fun onHostBye() {
            stop()
            postMain { listener.onSignalingClosed(1000, "host-bye") }
        }

        override fun onSignalingFailure(error: Throwable) {
            postMain { listener.onViewerFailure(error) }
        }

        override fun onSignalingClosed(code: Int, reason: String) {
            postMain { listener.onSignalingClosed(code, reason) }
        }
    }

    private inner class PeerObserver : PeerConnection.Observer {
        override fun onSignalingChange(signalingState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
            when (iceConnectionState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> postMain { listener.onMediaConnected(true) }

                PeerConnection.IceConnectionState.DISCONNECTED -> postMain { listener.onMediaConnected(false) }
                PeerConnection.IceConnectionState.FAILED -> fail(IOException("Koneksi ICE host gagal"))
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            session.signaling?.ice(hostId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit

        override fun onAddStream(stream: org.webrtc.MediaStream) = Unit

        override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit

        override fun onDataChannel(channel: DataChannel) {
            // Klien pembuat offer — host tidak perlu menawarkan channel.
            channel.close()
            channel.dispose()
        }

        override fun onRenegotiationNeeded() = Unit

        override fun onTrack(transceiver: RtpTransceiver) {
            if (transceiver.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                (transceiver.receiver.track() as? VideoTrack)?.let(::attachRemoteVideoTrack)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> postMain { listener.onMediaConnected(true) }
                PeerConnection.PeerConnectionState.DISCONNECTED -> postMain { listener.onMediaConnected(false) }
                PeerConnection.PeerConnectionState.FAILED -> fail(IOException("Peer connection host gagal"))
                else -> Unit
            }
        }

        override fun onAddTrack(receiver: org.webrtc.RtpReceiver, mediaStreams: Array<org.webrtc.MediaStream>) {
            (receiver.track() as? VideoTrack)?.let(::attachRemoteVideoTrack)
        }
    }

    companion object {
        /** Label persis yang ditunggu host produksi (`host/src/input.rs`). */
        const val INPUT_CHANNEL_LABEL = "input"

        /** Batas aman paket input (variabel TEXT/CLIPBOARD 4096 unit UTF-16 → maksimum ~16 KB). */
        const val MAX_INPUT_PACKET_BYTES = 16 * 1024

        /** STUN default klien referensi (Api.turnIce menambah TURN bila tersedia). */
        const val DEFAULT_STUN_URL = "stun:stun.cloudflare.com:3478"
    }
}

/** Inisialisasi native WebRTC sekali per proses (aman dipanggil lintas instance). */
internal object XyHubWebRtc {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions(),
            )
            initialized = true
        }
    }
}

/** Observer SDP tanpa aksi — dipakai untuk setLocalDescription. */
internal object NoopSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String) = Unit
    override fun onSetFailure(error: String) = Unit
}
