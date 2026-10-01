package id.xydesk.remote.core

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Jembatan Audio & Mikrofon UDP Low-Latency (Port 4433) ke `xydesk_quic.dll`
 * pada Windows Host Agent.
 *
 * Mengatasi masalah ketika Windows RDP mematikan `Remote Audio` demi mengaktifkan
 * `VB-CABLE` (`CABLE Input` / `XyDesk Virtual Microphone`):
 *  1. `AudioLoopbackThread` di `xydesk_quic.dll` menangkap suara PC yang berdenyut
 *     di `CABLE Input` (WASAPI Loopback 24 kHz Stereo PCM) dan mengirim paket `XYA1`
 *     ke HP untuk diputar langsung via [AudioTrack].
 *  2. Bila opsi mikrofon aktif, [AudioRecord] di HP menangkap suara mic HP,
 *     memproses Noise Gate + Gain DSP, dan mengirim paket `XYM1` ke `xydesk_quic.dll`
 *     agar masuk ke `CABLE Input` -> `CABLE Output` (`XyDesk Virtual Microphone`).
 */
class QuicAudioBridge(
    private val host: String,
    private val port: Int = 4433,
    private val enableSpeaker: Boolean = true,
    private val enableMic: Boolean = false,
) {
    private val running = AtomicBoolean(false)
    @Volatile private var socket: DatagramSocket? = null
    private var rxThread: Thread? = null
    private var txThread: Thread? = null

    fun start() {
        if (!enableSpeaker && !enableMic) return
        if (!running.compareAndSet(false, true)) return

        rxThread = Thread({ runLoopbackReceiver() }, "xydesk-quic-audio-rx").apply {
            isDaemon = true
            start()
        }
        if (enableMic) {
            txThread = Thread({ runMicSender() }, "xydesk-quic-mic-tx").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { socket?.close() }
        socket = null
        rxThread?.interrupt()
        txThread?.interrupt()
        rxThread = null
        txThread = null
    }

    private fun runLoopbackReceiver() {
        var track: AudioTrack? = null
        try {
            val addr = InetAddress.getByName(host)
            val sock = DatagramSocket()
            sock.soTimeout = 600
            socket = sock

            val subBytes = "XYDESK_QUIC_AUDIO_SUB_V1".toByteArray(Charsets.US_ASCII)
            val subPkt = DatagramPacket(subBytes, subBytes.size, addr, port)

            if (enableSpeaker) {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(SAMPLE_RATE * 4 / 10)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
            }

            val rxBuf = ByteArray(1500)
            val rxPkt = DatagramPacket(rxBuf, rxBuf.size)
            var lastSubMs = 0L

            while (running.get()) {
                val now = System.currentTimeMillis()
                if (enableSpeaker && now - lastSubMs >= 2_000L) {
                    runCatching { sock.send(subPkt) }
                    lastSubMs = now
                }
                if (!enableSpeaker) {
                    Thread.sleep(250)
                    continue
                }
                try {
                    sock.receive(rxPkt)
                    val len = rxPkt.length
                    if (len > 8 &&
                        rxBuf[0] == 'X'.code.toByte() &&
                        rxBuf[1] == 'Y'.code.toByte() &&
                        rxBuf[2] == 'A'.code.toByte() &&
                        rxBuf[3] == '1'.code.toByte()
                    ) {
                        track?.write(rxBuf, 8, len - 8)
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal timeout; loop checks running & refreshes subscription
                }
            }
        } catch (_: Throwable) {
            // Host may not be running XyDeskRemoteHost.exe; fail silently
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun runMicSender() {
        var record: AudioRecord? = null
        try {
            val addr = InetAddress.getByName(host)
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(SAMPLE_RATE * 2 / 10)

            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) return
            record.startRecording()

            val frameSamples = 240 // 10 ms @ 24 kHz mono
            val pcmShorts = ShortArray(frameSamples)
            val pktBytes = ByteArray(8 + frameSamples * 2)
            pktBytes[0] = 'X'.code.toByte()
            pktBytes[1] = 'Y'.code.toByte()
            pktBytes[2] = 'M'.code.toByte()
            pktBytes[3] = '1'.code.toByte()
            var seq = 0
            var env = 0f

            while (running.get()) {
                val n = record.read(pcmShorts, 0, frameSamples)
                if (n <= 0) {
                    Thread.sleep(5)
                    continue
                }
                val sock = socket ?: continue
                pktBytes[4] = (seq and 0xFF).toByte()
                pktBytes[5] = ((seq shr 8) and 0xFF).toByte()
                pktBytes[6] = 240.toByte()
                pktBytes[7] = 1.toByte()
                seq = (seq + 1) and 0xFFFF

                val bb = ByteBuffer.wrap(pktBytes, 8, n * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until n) {
                    val raw = pcmShorts[i].toFloat() / 32768f
                    val a = abs(raw)
                    env = if (a > env) env * 0.8f + a * 0.2f else env * 0.995f + a * 0.005f
                    // Noise gate (-44 dBFS ~ 0.0063) + +4.5 dB gain boost
                    val gate = if (env < 0.0063f) (env / 0.0063f) else 1f
                    val boosted = max(-1f, min(1f, raw * gate * 1.65f))
                    bb.putShort((boosted * 32767f).toInt().toShort())
                }
                val totalLen = 8 + n * 2
                runCatching {
                    sock.send(DatagramPacket(pktBytes, totalLen, addr, port))
                }
            }
        } catch (_: Throwable) {
            // Permission or AudioRecord unavailable
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 24_000
    }
}
