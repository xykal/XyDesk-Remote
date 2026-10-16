package id.xydesk.remote.core

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Jembatan Input Game FPS Ultra-Low-Latency (`XYI1` via UDP :4433).
 *
 * Mengirim delta mouse relatif (`SendInput` `MOUSEEVENTF_MOVE` tanpa
 * `MOUSEEVENTF_ABSOLUTE`), tombol mouse, scroll, serta virtual-key langsung ke
 * `XyDeskRemoteHost-Standalone.exe` atau daemon UDP `XyRDP` di port 4433.
 *
 * Kenapa dibutuhkan untuk Game FPS:
 * - Jalur mouse RDP standar mengirim koordinat kursor absolut yang mentok di
 *   tepi layar HP atau membuat kamera game FPS berputar liar (`SetCursorPos`).
 * - Paket `TS_FP_RELPOINTER_EVENT` FreeRDP sering ditolak oleh `termsrv.dll`
 *   Windows bila `FreeRDP_HasRelativeMouseEvent` tidak dinegosiasikan.
 * - Dengan `XYI1` di UDP 4433, host Windows menginjeksi `SendInput`
 *   `MOUSEEVENTF_MOVE` murni (mickeys hardware relatif) dengan latensi < 1 ms,
 *   sehingga rotasi kamera 360° di game FPS (CS2, Valorant, GTA/SAMP, COD,
 *   Apex, Roblox, Minecraft) berjalan mulus dan presisi hingga 120–240 Hz.
 */
class XyRawGameInputBridge(
    private val host: String,
    private val port: Int = 4433,
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private val lastAckElapsedMs = AtomicLong(0L)
    @Volatile private var remoteFlags: Int = 0
    @Volatile private var socket: DatagramSocket? = null
    private val queue = LinkedBlockingQueue<ByteArray>(512)
    private var txThread: Thread? = null
    private var rxThread: Thread? = null

    /**
     * `true` bila daemon UDP :4433 di PC merespons `XYIA` dalam 6 detik terakhir.
     * Saat aktif, sesi RDP/PC Stream dapat mengandalkan injeksi `SendInput`
     * hardware relatif di sisi host.
     */
    val isAlive: Boolean
        get() {
            val last = lastAckElapsedMs.get()
            return running.get() && last > 0L && (SystemClock.elapsedRealtime() - last) < ACK_TTL_MS
        }

    /** `true` bila host melaporkan sesi konsol fisik (`SM_REMOTESESSION == 0`). */
    val isConsoleNonRdp: Boolean
        get() = isAlive && (remoteFlags and FLAG_CONSOLE_NON_RDP) != 0

    fun start() {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        if (cleanHost.isEmpty()) return
        if (!running.compareAndSet(false, true)) return
        txThread = Thread({ runSender(cleanHost) }, "xydesk-raw-input-tx").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        queue.clear()
        runCatching { socket?.close() }
        socket = null
        txThread?.interrupt()
        rxThread?.interrupt()
        txThread = null
        rxThread = null
    }

    override fun close() = stop()

    /** Kirim gerak mouse relatif (`MOUSEEVENTF_MOVE`) untuk rotasi kamera 360° FPS. */
    fun sendMouseRel(dx: Int, dy: Int): Boolean {
        val clampedDx = dx.coerceIn(-32_768, 32_767)
        val clampedDy = dy.coerceIn(-32_768, 32_767)
        if (clampedDx == 0 && clampedDy == 0) return false
        return enqueue(encodeMouseRel(clampedDx, clampedDy))
    }

    /** Kirim gerak mouse absolut ternormalisasi `0..65535`. */
    fun sendMouseAbs(xNorm: Int, yNorm: Int): Boolean {
        return enqueue(
            encodeMouseAbs(
                xNorm.coerceIn(0, 65_535),
                yNorm.coerceIn(0, 65_535),
            ),
        )
    }

    /** Kirim tombol mouse (`0=kiri, 1=kanan, 2=tengah, 3=x1, 4=x2`). */
    fun sendMouseButton(button: Int, down: Boolean): Boolean {
        return enqueue(encodeMouseButton(button.coerceIn(0, 4), down))
    }

    /** Kirim roda scroll (`WHEEL_DELTA = 120` per takik). */
    fun sendMouseWheel(wheelDelta: Int): Boolean {
        if (wheelDelta == 0) return false
        return enqueue(encodeMouseWheel(wheelDelta.coerceIn(-32_768, 32_767)))
    }

    /** Kirim tombol keyboard Windows Virtual-Key (`VK_*`). */
    fun sendKeyVk(vk: Int, down: Boolean, scanCode: Int = 0): Boolean {
        if (vk <= 0) return false
        return enqueue(encodeKeyVk(vk, down, scanCode))
    }

    private fun enqueue(packet: ByteArray): Boolean {
        if (!running.get()) return false
        if (!queue.offer(packet)) {
            queue.poll()
            queue.offer(packet)
        }
        return true
    }

    private fun runSender(cleanHost: String) {
        try {
            val addr = InetAddress.getByName(cleanHost)
            val sock = DatagramSocket()
            sock.trafficClass = 0x10 // IPTOS_LOWDELAY
            sock.soTimeout = 400
            socket = sock

            rxThread = Thread({ runReceiver(sock) }, "xydesk-raw-input-rx").apply {
                isDaemon = true
                start()
            }

            // Kirim paket ping awal supaya status isAlive langsung terdeteksi.
            val pingBytes = encodePing()
            runCatching { sock.send(DatagramPacket(pingBytes, pingBytes.size, addr, port)) }
            var lastPingMs = SystemClock.elapsedRealtime()

            while (running.get() && !Thread.currentThread().isInterrupted) {
                val first = queue.poll(350, TimeUnit.MILLISECONDS)
                val now = SystemClock.elapsedRealtime()
                if (first != null) {
                    var toSend = first
                    // Gabungkan paket MOUSE_REL berurutan di antrean agar sapuan
                    // cepat 240Hz terkirim dalam 1 datagram tanpa antrean menumpuk.
                    if (first[4] == CMD_MOUSE_REL) {
                        var sumDx = readInt16Le(first, 6)
                        var sumDy = readInt16Le(first, 8)
                        while (true) {
                            val peek = queue.peek() ?: break
                            if (peek[4] != CMD_MOUSE_REL) break
                            queue.poll()
                            sumDx = (sumDx + readInt16Le(peek, 6)).coerceIn(-32_768, 32_767)
                            sumDy = (sumDy + readInt16Le(peek, 8)).coerceIn(-32_768, 32_767)
                        }
                        toSend = encodeMouseRel(sumDx, sumDy)
                    }
                    runCatching {
                        sock.send(DatagramPacket(toSend, toSend.size, addr, port))
                    }
                }
                if (now - lastPingMs >= PING_INTERVAL_MS) {
                    lastPingMs = now
                    runCatching {
                        sock.send(DatagramPacket(pingBytes, pingBytes.size, addr, port))
                    }
                }
            }
        } catch (_: Throwable) {
            // Host tidak mendukung UDP atau nama host tidak bisa di-resolve.
        } finally {
            runCatching { socket?.close() }
            socket = null
        }
    }

    private fun runReceiver(sock: DatagramSocket) {
        val buf = ByteArray(64)
        val pkt = DatagramPacket(buf, buf.size)
        while (running.get() && !sock.isClosed && !Thread.currentThread().isInterrupted) {
            try {
                sock.receive(pkt)
                if (pkt.length >= 4 &&
                    buf[0] == 'X'.code.toByte() &&
                    buf[1] == 'Y'.code.toByte() &&
                    buf[2] == 'I'.code.toByte() &&
                    buf[3] == 'A'.code.toByte()
                ) {
                    if (pkt.length >= 5) {
                        remoteFlags = buf[4].toInt() and 0xFF
                    }
                    lastAckElapsedMs.set(SystemClock.elapsedRealtime())
                }
            } catch (_: SocketTimeoutException) {
                // Lanjut tunggu paket berikutnya.
            } catch (_: Throwable) {
                break
            }
        }
    }

    companion object {
        const val PACKET_BYTES = 12
        const val CMD_MOUSE_REL: Byte = 0x01
        const val CMD_MOUSE_BTN: Byte = 0x02
        const val CMD_MOUSE_WHEEL: Byte = 0x03
        const val CMD_KEY_VK: Byte = 0x04
        const val CMD_PING: Byte = 0x05
        const val CMD_MOUSE_ABS: Byte = 0x06

        const val FLAG_RAW_INPUT_OK = 0x01
        const val FLAG_CONSOLE_NON_RDP = 0x02

        private const val PING_INTERVAL_MS = 2_000L
        private const val ACK_TTL_MS = 6_000L

        fun encodeMouseRel(dx: Int, dy: Int): ByteArray =
            ByteBuffer.allocate(PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('X'.code.toByte())
                put('Y'.code.toByte())
                put('I'.code.toByte())
                put('1'.code.toByte())
                put(CMD_MOUSE_REL)
                put(0)
                putShort(dx.coerceIn(-32_768, 32_767).toShort())
                putShort(dy.coerceIn(-32_768, 32_767).toShort())
                putShort(0)
            }.array()

        fun encodeMouseAbs(xNorm: Int, yNorm: Int): ByteArray =
            ByteBuffer.allocate(PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('X'.code.toByte())
                put('Y'.code.toByte())
                put('I'.code.toByte())
                put('1'.code.toByte())
                put(CMD_MOUSE_ABS)
                put(0)
                putShort(xNorm.coerceIn(0, 65_535).toShort())
                putShort(yNorm.coerceIn(0, 65_535).toShort())
                putShort(0)
            }.array()

        fun encodeMouseButton(button: Int, down: Boolean): ByteArray =
            ByteBuffer.allocate(PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('X'.code.toByte())
                put('Y'.code.toByte())
                put('I'.code.toByte())
                put('1'.code.toByte())
                put(CMD_MOUSE_BTN)
                put(button.coerceIn(0, 4).toByte())
                putShort((if (down) 1 else 0).toShort())
                putShort(0)
                putShort(0)
            }.array()

        fun encodeMouseWheel(wheelDelta: Int): ByteArray =
            ByteBuffer.allocate(PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('X'.code.toByte())
                put('Y'.code.toByte())
                put('I'.code.toByte())
                put('1'.code.toByte())
                put(CMD_MOUSE_WHEEL)
                put(0)
                putShort(wheelDelta.coerceIn(-32_768, 32_767).toShort())
                putShort(0)
                putShort(0)
            }.array()

        fun encodeKeyVk(vk: Int, down: Boolean, scanCode: Int = 0): ByteArray =
            ByteBuffer.allocate(PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('X'.code.toByte())
                put('Y'.code.toByte())
                put('I'.code.toByte())
                put('1'.code.toByte())
                put(CMD_KEY_VK)
                put((if (down) 1 else 0).toByte())
                putShort((vk and 0xFFFF).toShort())
                putShort((scanCode and 0xFFFF).toShort())
                putShort(0)
            }.array()

        fun encodePing(): ByteArray =
            ByteBuffer.allocate(PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('X'.code.toByte())
                put('Y'.code.toByte())
                put('I'.code.toByte())
                put('1'.code.toByte())
                put(CMD_PING)
            }.array()

        internal fun readInt16Le(buf: ByteArray, offset: Int): Int {
            val lo = buf[offset].toInt() and 0xFF
            val hi = buf[offset + 1].toInt() and 0xFF
            return ((hi shl 8) or lo).toShort().toInt()
        }
    }
}
