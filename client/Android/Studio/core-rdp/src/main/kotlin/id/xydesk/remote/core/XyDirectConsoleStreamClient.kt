package id.xydesk.remote.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Klien Engine Streaming Konsol Mandiri XyDesk (`XYV1` + `XYA1` + `XYI1` di UDP :4433).
 *
 * Berbeda dengan sesi RDP (`termsrv.dll` yang membuat `SM_REMOTESESSION != 0`),
 * engine ini menangkap langsung desktop konsol fisik Windows (`SM_REMOTESESSION == 0`)
 * dari `XyDeskRemoteHost-Standalone.exe`, mengenkode frame JPEG berkecepatan tinggi,
 * dan mengalirkan audio WASAPI Loopback (`XYA1`) + input hardware `SendInput`
 * (`XYI1` `MOUSEEVENTF_MOVE`) di port UDP 4433 tanpa protokol RDP sama sekali.
 */
class XyDirectConsoleStreamClient(
    private val host: String,
    private val port: Int = 4433,
    private val targetFps: Int = 120,
    private val jpegQuality: Int = 72,
    private val scalePct: Int = 85,
    private val enableAudio: Boolean = true,
    private val onFrame: (
        bitmap: Bitmap,
        width: Int,
        height: Int,
        hostFps: Int,
        isConsoleNonRdp: Boolean,
    ) -> Unit,
    private val onState: (connected: Boolean, detail: String) -> Unit = { _, _ -> },
) : AutoCloseable {

    data class DiscoveredHost(
        val hostname: String,
        val ip: String,
        val rdpPort: Int,
        val quicPort: Int,
        val gpu: String,
        val encoder: String,
        val pcId: String,
        val consoleStreamReady: Boolean,
    )

    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var videoSocket: DatagramSocket? = null
    private var rxThread: Thread? = null

    val inputBridge = XyRawGameInputBridge(host = host, port = port)
    private var audioBridge: QuicAudioBridge? = null

    fun start() {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        if (cleanHost.isEmpty()) {
            main.post { onState(false, "Host kosong") }
            return
        }
        if (!running.compareAndSet(false, true)) return
        inputBridge.start()
        if (enableAudio) {
            audioBridge = QuicAudioBridge(
                host = cleanHost,
                port = port,
                enableSpeaker = true,
                enableMic = false,
            ).also { it.start() }
        }
        rxThread = Thread({ runVideoLoop(cleanHost) }, "xydesk-console-video-rx").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        val unsub = encodeSubscribe(enable = false, fps = targetFps, quality = jpegQuality, scalePct = scalePct)
        runCatching {
            val sock = videoSocket
            val addr = InetAddress.getByName(host.trim().removePrefix("[").removeSuffix("]"))
            sock?.send(DatagramPacket(unsub, unsub.size, addr, port))
        }
        runCatching { videoSocket?.close() }
        videoSocket = null
        rxThread?.interrupt()
        rxThread = null
        inputBridge.stop()
        audioBridge?.stop()
        audioBridge = null
    }

    override fun close() = stop()

    private fun runVideoLoop(cleanHost: String) {
        try {
            val addr = InetAddress.getByName(cleanHost)
            val sock = DatagramSocket()
            sock.receiveBufferSize = 2 * 1024 * 1024
            sock.trafficClass = 0x10
            sock.soTimeout = 450
            videoSocket = sock

            val subPktBytes = encodeSubscribe(
                enable = true,
                fps = targetFps.coerceIn(30, 120),
                quality = jpegQuality.coerceIn(40, 92),
                scalePct = scalePct.coerceIn(50, 100),
            )
            val subPkt = DatagramPacket(subPktBytes, subPktBytes.size, addr, port)
            runCatching { sock.send(subPkt) }
            var lastSubMs = SystemClock.elapsedRealtime()
            var connectedAnnounced = false

            val rxBuf = ByteArray(MAX_UDP_PACKET)
            val rxPkt = DatagramPacket(rxBuf, rxBuf.size)

            var activeFrameId = -1
            var expectedChunks = 0
            var receivedChunks = 0
            var frameWidth = 0
            var frameHeight = 0
            var frameFlags = 0
            var frameHostFps = 0
            val chunkBuffers = arrayOfNulls<ByteArray>(MAX_CHUNKS_PER_FRAME)
            val chunkLengths = IntArray(MAX_CHUNKS_PER_FRAME)
            var reassemblyBuffer = ByteArray(256 * 1024)

            val decodeOpts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }

            main.post { onState(false, "Menghubungi Engine Konsol UDP :$port...") }

            while (running.get() && !Thread.currentThread().isInterrupted) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastSubMs >= SUB_INTERVAL_MS) {
                    lastSubMs = now
                    runCatching { sock.send(subPkt) }
                }

                try {
                    sock.receive(rxPkt)
                } catch (_: SocketTimeoutException) {
                    continue
                }

                val len = rxPkt.length
                if (len <= HEADER_BYTES) continue
                if (rxBuf[0] != 'X'.code.toByte() ||
                    rxBuf[1] != 'Y'.code.toByte() ||
                    rxBuf[2] != 'V'.code.toByte() ||
                    rxBuf[3] != '1'.code.toByte()
                ) {
                    continue
                }

                val frameId = readUInt16Le(rxBuf, 4)
                val chunkIdx = readUInt16Le(rxBuf, 6)
                val chunkCount = readUInt16Le(rxBuf, 8)
                val width = readUInt16Le(rxBuf, 10)
                val height = readUInt16Le(rxBuf, 12)
                val flags = rxBuf[14].toInt() and 0xFF
                val fpsActual = rxBuf[15].toInt() and 0xFF

                if (chunkCount !in 1..MAX_CHUNKS_PER_FRAME || chunkIdx >= chunkCount) continue

                if (frameId != activeFrameId) {
                    activeFrameId = frameId
                    expectedChunks = chunkCount
                    receivedChunks = 0
                    frameWidth = width
                    frameHeight = height
                    frameFlags = flags
                    frameHostFps = fpsActual
                    for (i in 0 until chunkCount) {
                        chunkLengths[i] = 0
                    }
                }

                val payloadLen = len - HEADER_BYTES
                if (chunkLengths[chunkIdx] == 0) {
                    var slot = chunkBuffers[chunkIdx]
                    if (slot == null || slot.size < payloadLen) {
                        slot = ByteArray(payloadLen.coerceAtLeast(1400))
                        chunkBuffers[chunkIdx] = slot
                    }
                    System.arraycopy(rxBuf, HEADER_BYTES, slot, 0, payloadLen)
                    chunkLengths[chunkIdx] = payloadLen
                    receivedChunks++
                }

                if (receivedChunks == expectedChunks) {
                    var totalBytes = 0
                    for (i in 0 until expectedChunks) {
                        totalBytes += chunkLengths[i]
                    }
                    if (totalBytes > 0) {
                        if (reassemblyBuffer.size < totalBytes) {
                            reassemblyBuffer = ByteArray(totalBytes + 32 * 1024)
                        }
                        var pos = 0
                        for (i in 0 until expectedChunks) {
                            val clen = chunkLengths[i]
                            val cbuf = chunkBuffers[i] ?: continue
                            System.arraycopy(cbuf, 0, reassemblyBuffer, pos, clen)
                            pos += clen
                        }
                        val bmp = runCatching {
                            BitmapFactory.decodeByteArray(reassemblyBuffer, 0, totalBytes, decodeOpts)
                        }.getOrNull()
                        if (bmp != null) {
                            val nonRdp = (frameFlags and FLAG_CONSOLE_NON_RDP) != 0
                            val w = if (frameWidth > 0) frameWidth else bmp.width
                            val h = if (frameHeight > 0) frameHeight else bmp.height
                            val fps = if (frameHostFps > 0) frameHostFps else targetFps
                            if (!connectedAnnounced) {
                                connectedAnnounced = true
                                main.post {
                                    onState(
                                        true,
                                        if (nonRdp) "Konsol Fisik Aktif (SM_REMOTESESSION=0)"
                                        else "Stream Aktif",
                                    )
                                }
                            }
                            main.post {
                                onFrame(bmp, w, h, fps, nonRdp)
                            }
                        }
                    }
                    activeFrameId = -1
                }
            }
        } catch (e: Throwable) {
            main.post { onState(false, e.message ?: "Gagal membuka soket UDP") }
        } finally {
            runCatching { videoSocket?.close() }
            videoSocket = null
        }
    }

    companion object {
        const val HEADER_BYTES = 16
        const val SUBSCRIBE_BYTES = 12
        const val CMD_SUBSCRIBE: Byte = 0x01
        const val CMD_UNSUBSCRIBE: Byte = 0x00
        const val FLAG_CONSOLE_NON_RDP = 0x01
        private const val MAX_UDP_PACKET = 1500
        private const val MAX_CHUNKS_PER_FRAME = 256
        private const val SUB_INTERVAL_MS = 1_500L

        fun encodeSubscribe(
            enable: Boolean,
            fps: Int = 120,
            quality: Int = 72,
            scalePct: Int = 85,
        ): ByteArray = ByteBuffer.allocate(SUBSCRIBE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('X'.code.toByte())
            put('Y'.code.toByte())
            put('V'.code.toByte())
            put('1'.code.toByte())
            put(if (enable) CMD_SUBSCRIBE else CMD_UNSUBSCRIBE)
            put(fps.coerceIn(15, 120).toByte())
            put(quality.coerceIn(30, 95).toByte())
            put(scalePct.coerceIn(40, 100).toByte())
            putInt(0)
        }.array()

        internal fun readUInt16Le(buf: ByteArray, offset: Int): Int {
            val lo = buf[offset].toInt() and 0xFF
            val hi = buf[offset + 1].toInt() and 0xFF
            return (hi shl 8) or lo
        }

        /**
         * Pindai jaringan lokal / Tailscale di UDP :4433 untuk menemukan
         * `XyDeskRemoteHost-Standalone.exe` yang aktif.
         */
        fun discoverHosts(timeoutMs: Int = 900, port: Int = 4433): List<DiscoveredHost> {
            val found = linkedMapOf<String, DiscoveredHost>()
            runCatching {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    sock.soTimeout = 220
                    val probe = "XYDESK_QUIC_DISCOVER_V1".toByteArray(Charsets.US_ASCII)
                    val bcast = InetAddress.getByName("255.255.255.255")
                    runCatching { sock.send(DatagramPacket(probe, probe.size, bcast, port)) }

                    val deadline = SystemClock.elapsedRealtime() + timeoutMs
                    val rxBuf = ByteArray(1024)
                    val rxPkt = DatagramPacket(rxBuf, rxBuf.size)
                    while (SystemClock.elapsedRealtime() < deadline) {
                        try {
                            sock.receive(rxPkt)
                            val text = String(rxBuf, 0, rxPkt.length, Charsets.UTF_8)
                            val obj = JSONObject(text)
                            if (obj.optString("proto") == "XYDESK-QUIC-1") {
                                val ip = obj.optString("ip").ifBlank {
                                    rxPkt.address?.hostAddress.orEmpty()
                                }
                                if (ip.isNotBlank()) {
                                    found[ip] = DiscoveredHost(
                                        hostname = obj.optString("hostname", "PC"),
                                        ip = ip,
                                        rdpPort = obj.optInt("rdp_port", 3389),
                                        quicPort = obj.optInt("quic_port", 4433),
                                        gpu = obj.optString("gpu", "GPU"),
                                        encoder = obj.optString("encoder", "Auto"),
                                        pcId = obj.optString("pc_id", ""),
                                        consoleStreamReady = obj.optBoolean("console_stream", true),
                                    )
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                            // lanjut sampai deadline
                        }
                    }
                }
            }
            return found.values.toList()
        }
    }
}
