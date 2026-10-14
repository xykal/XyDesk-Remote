package id.xydesk.remote.pcstream

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Codec protokol biner data channel `input` milik **host XyDesk produksi**
 * (repo saudara `xykal/XyDesk`, `host/src/input.rs` + `docs/PROTOCOL.md`).
 *
 * XyDesk-Remote menjadi klien kedua bagi host yang sudah berjalan, jadi
 * protokol yang diikuti adalah milik host — bukan prototype `XYGP` v1
 * (channel `xydesk-control-v1`) yang dibangun di v1.0.6 dan belum pernah
 * dipakai host mana pun. Bit tombol [XyGamepadPacketV1] kebetulan identik
 * dengan layout laporan XUSB, jadi [XyGamepadState] bisa dikonversi apa
 * adanya ke opcode `0x0E`.
 *
 * Layout (little-endian), mengikuti dekoder host:
 *
 * ```text
 * 0x01 MOUSE_MOVE_REL  dx:i16 dy:i16           — 8 byte tetap (padding nol)
 * 0x02 MOUSE_MOVE_ABS  x:u16 y:u16             — 8 byte tetap (0..65535 ternormalisasi)
 * 0x03 MOUSE_BUTTON    btn:u8 down:u8          — 8 byte tetap (0=kiri..4=x2)
 * 0x04 SCROLL          dx:i16 dy:i16           — 8 byte tetap (satuan WHEEL_DELTA=120)
 * 0x05 KEY             vk:u16 down:u8          — 8 byte tetap (Windows Virtual-Key)
 * 0x06 TEXT            utf8 (sisa pesan)       — variabel
 * 0x07 DISPLAY_SELECT  index:u8                — 2 byte
 * 0x08 CLIPBOARD_SET   utf8 (sisa pesan)       — variabel
 * 0x09 CLIPBOARD_REQ   —                        — 1 byte
 * 0x0A VIDEO_QUALITY   quality:u8              — 2 byte (0=auto 1=medium 2=high 3=ultra)
 * 0x0B VIDEO_BITRATE   mbps:u16le              — 3 byte (0=auto, 1..50)
 * 0x0C VIDEO_RESOLUTION mode:u8                — 2 byte (0=720p 1=1080p 2=native)
 * 0x0E GAMEPAD         XUSB report 12 byte     — 13 byte total
 * 0x0F VIDEO_FPS       fps:u8                  — 2 byte (30 atau 60)
 * 0x10 VIRTUAL_DISPLAY w:u16le h:u16le hz:u8    — 7 byte (mode Virtual Display
 *                     count:u8                    Adapter; host tidak memasang
 *                                                 driver dari sesi remote)
 * ```
 *
 * Murni (tanpa Android/WebRTC) supaya byte-layout-nya bisa dibekukan lewat
 * unit test JVM — kecocokan bit dengan host Rust diverifikasi lewat vektor
 * emas, bukan lewat "kelihatannya sama".
 */
object XyHostInputCodec {

    const val TAG_MOUSE_MOVE_REL: Int = 0x01
    const val TAG_MOUSE_MOVE_ABS: Int = 0x02
    const val TAG_MOUSE_BUTTON: Int = 0x03
    const val TAG_SCROLL: Int = 0x04
    const val TAG_KEY: Int = 0x05
    const val TAG_TEXT: Int = 0x06
    const val TAG_DISPLAY_SELECT: Int = 0x07
    const val TAG_CLIPBOARD_SET: Int = 0x08
    const val TAG_CLIPBOARD_REQ: Int = 0x09
    const val TAG_VIDEO_QUALITY: Int = 0x0A
    const val TAG_VIDEO_BITRATE: Int = 0x0B
    const val TAG_VIDEO_RESOLUTION: Int = 0x0C
    const val TAG_GAMEPAD: Int = 0x0E
    const val TAG_VIDEO_FPS: Int = 0x0F
    const val TAG_VIRTUAL_DISPLAY: Int = 0x10

    /** Panjang pesan event generasi pertama (dokumen host: "8 byte tetap"). */
    private const val FIXED_EVENT_BYTES = 8

    /** Total byte pesan virtual display: opcode + w/h u16le + hz + count. */
    const val VIRTUAL_DISPLAY_BYTES = 7

    /** Total byte pesan gamepad: opcode + laporan XUSB 12 byte. */
    const val GAMEPAD_MESSAGE_BYTES = 13

    /** Batas unit UTF-16 satu pesan teks di sisi host (lebih dari itu dibuang). */
    const val MAX_TEXT_UNITS = 4_096

    // ------------------------------------------------------------------
    // Encoder (client -> host)
    // ------------------------------------------------------------------

    /** Gerak mouse relatif dalam piksel — mode trackpad/FPS. */
    fun mouseMoveRel(dx: Int, dy: Int): ByteArray {
        require(dx in -32_768..32_767 && dy in -32_768..32_767) { "delta must fit int16" }
        return fixed(TAG_MOUSE_MOVE_REL) {
            putShort(dx.toShort())
            putShort(dy.toShort())
        }
    }

    /** Gerak mouse absolut ternormalisasi 0..65535 pada area capture aktif. */
    fun mouseMoveAbs(x: Int, y: Int): ByteArray {
        require(x in 0..65_535 && y in 0..65_535) { "abs position must fit uint16 0..65535" }
        return fixed(TAG_MOUSE_MOVE_ABS) {
            putShort(x.toShort())
            putShort(y.toShort())
        }
    }

    /** Tombol mouse: 0=kiri, 1=kanan, 2=tengah, 3=x1, 4=x2. */
    fun mouseButton(button: Int, down: Boolean): ByteArray {
        require(button in 0..4) { "mouse button index must be 0..4" }
        return fixed(TAG_MOUSE_BUTTON) {
            put(button.toByte())
            put((if (down) 1 else 0).toByte())
        }
    }

    /** Scroll dalam satuan WHEEL_DELTA (120 = satu "klik" roda). */
    fun scroll(dx: Int, dy: Int): ByteArray {
        require(dx in -32_768..32_767 && dy in -32_768..32_767) { "scroll must fit int16" }
        return fixed(TAG_SCROLL) {
            putShort(dx.toShort())
            putShort(dy.toShort())
        }
    }

    /** Tombol keyboard dengan Windows Virtual-Key code. */
    fun key(vk: Int, down: Boolean): ByteArray {
        require(vk in 0..65_535) { "vk must fit uint16" }
        return fixed(TAG_KEY) {
            putShort(vk.toShort())
            put((if (down) 1 else 0).toByte())
        }
    }

    /**
     * Teks bebas (keyboard virtual) — host meng-inject sebagai unicode.
     * Di-potong di [MAX_TEXT_UNITS] unit UTF-16, meniru batas host
     * (`text_batches`) supaya pesan panjang tidak dibuang seluruhnya.
     */
    fun text(value: String): ByteArray = variable(TAG_TEXT, clampTextUnits(value))

    /** Pilih monitor host untuk capture (indeks sistem; 0 = primer). */
    fun displaySelect(index: Int): ByteArray {
        require(index in 0..255) { "display index must fit uint8" }
        return exact(TAG_DISPLAY_SELECT) { put(index.toByte()) }
    }

    /** Tulis teks ke papan klip host (dua arah, bukan injeksi keyboard). */
    fun clipboardSet(value: String): ByteArray = variable(TAG_CLIPBOARD_SET, value)

    /** Minta host membalas isi papan klipnya sebagai [clipboardSet]. */
    fun clipboardRequest(): ByteArray = exact(TAG_CLIPBOARD_REQ) { }

    /** Preset kualitas video. */
    fun videoQuality(quality: XyHostVideoQuality): ByteArray =
        exact(TAG_VIDEO_QUALITY) { put(quality.code.toByte()) }

    /** Target bitrate Mbps (0 = auto). */
    fun videoBitrate(mbps: Int): ByteArray {
        require(mbps in 0..50) { "bitrate must be 0..50 Mbps" }
        return exact(TAG_VIDEO_BITRATE) {
            putShort(mbps.toShort())
        }
    }

    /** Mode resolusi kiriman host (batas gambar, bukan ubah resolusi Windows). */
    fun videoResolution(mode: XyHostVideoResolution): ByteArray =
        exact(TAG_VIDEO_RESOLUTION) { put(mode.code.toByte()) }

    /** Batas FPS (hanya 30 atau 60). */
    fun videoFps(fps: Int): ByteArray {
        require(fps == 30 || fps == 60) { "fps must be 30 or 60" }
        return exact(TAG_VIDEO_FPS) { put(fps.toByte()) }
    }

    /**
     * Minta host mengaktifkan/mengubah mode Virtual Display Adapter (opcode
     * `0x10`, 7 byte) untuk mesin headless: `w:u16le h:u16le hz:u8 count:u8`.
     * Host hanya mengubah mode bila adapter sudah terpasang — memasang driver
     * butuh UAC lokal; status dikembalikan lewat teks `virtualDisplay`.
     */
    fun virtualDisplay(width: Int, height: Int, refreshHz: Int = 60, count: Int = 1): ByteArray {
        require(width in 640..7680) { "width must be 640..7680" }
        require(height in 480..4320) { "height must be 480..4320" }
        require(refreshHz in 24..240) { "refresh must be 24..240 Hz" }
        require(count in 1..4) { "count must be 1..4" }
        return ByteBuffer.allocate(VIRTUAL_DISPLAY_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(TAG_VIRTUAL_DISPLAY.toByte())
            putShort(width.toShort())
            putShort(height.toShort())
            put(refreshHz.toByte())
            put(count.toByte())
        }.array()
    }

    /**
     * Laporan gamepad: opcode `0x0E` + laporan XUSB 12 byte. [XyGamepadState]
     * sudah memakai bit tombol XInput, jadi tidak ada pemetaan ulang — hanya
     * re-ensapsulasi dari format XYGP 28 byte ke laporan XUSB host.
     */
    fun gamepad(state: XyGamepadState): ByteArray {
        require(state.buttons in 0..0xFFFF) { "buttons must fit uint16" }
        require(state.leftTrigger in 0..255 && state.rightTrigger in 0..255) {
            "trigger values must be in 0..255"
        }
        require(
            state.leftX in -32_768..32_767 && state.leftY in -32_768..32_767 &&
                state.rightX in -32_768..32_767 && state.rightY in -32_768..32_767,
        ) { "axis values must fit int16" }
        return ByteBuffer.allocate(GAMEPAD_MESSAGE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(TAG_GAMEPAD.toByte())
            putShort(state.buttons.toShort())
            put(state.leftTrigger.toByte())
            put(state.rightTrigger.toByte())
            putShort(state.leftX.toShort())
            putShort(state.leftY.toShort())
            putShort(state.rightX.toShort())
            putShort(state.rightY.toShort())
        }.array()
    }

    // ------------------------------------------------------------------
    // Decoder (cermin perilaku host; untuk uji byte-layout + tooling)
    // ------------------------------------------------------------------

    /**
     * Dekode satu pesan biner. `null` bila tidak valid — host membuang pesan
     * rusak diam-diam dan dekoder klien meniru perilaku itu.
     */
    fun decode(data: ByteArray): XyHostInputEvent? {
        if (data.isEmpty()) return null
        fun le16(i: Int): Int =
            ((data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8))
        fun le16signed(i: Int): Int = (le16(i).toShort()).toInt()
        fun utf8(from: Int): String? =
            runCatching { String(data, from, data.size - from, Charsets.UTF_8) }.getOrNull()
        return when (data[0].toInt() and 0xFF) {
            TAG_MOUSE_MOVE_REL -> if (data.size >= 5) {
                XyHostInputEvent.MouseMoveRel(le16signed(1), le16signed(3))
            } else null
            TAG_MOUSE_MOVE_ABS -> if (data.size >= 5) {
                XyHostInputEvent.MouseMoveAbs(le16(1), le16(3))
            } else null
            TAG_MOUSE_BUTTON -> if (data.size >= 3) {
                XyHostInputEvent.MouseButton(data[1].toInt(), data[2] != 0.toByte())
            } else null
            TAG_SCROLL -> if (data.size >= 5) {
                XyHostInputEvent.Scroll(le16signed(1), le16signed(3))
            } else null
            TAG_KEY -> if (data.size >= 4) {
                XyHostInputEvent.Key(le16(1), data[3] != 0.toByte())
            } else null
            TAG_TEXT -> if (data.size >= 2) {
                utf8(1)?.let { XyHostInputEvent.Text(it) }
            } else null
            TAG_DISPLAY_SELECT -> if (data.size >= 2) {
                XyHostInputEvent.DisplaySelect(data[1].toInt() and 0xFF)
            } else null
            TAG_CLIPBOARD_SET -> if (data.size >= 1) {
                utf8(1)?.let { XyHostInputEvent.ClipboardSet(it) }
            } else null
            TAG_CLIPBOARD_REQ -> if (data.size >= 1) XyHostInputEvent.ClipboardRequest else null
            TAG_VIDEO_QUALITY -> if (data.size >= 2) {
                XyHostInputEvent.VideoQuality(data[1].toInt() and 0xFF)
            } else null
            TAG_VIDEO_BITRATE -> if (data.size >= 3) {
                XyHostInputEvent.VideoBitrate(le16(1))
            } else null
            TAG_VIDEO_RESOLUTION -> if (data.size >= 2) {
                XyHostInputEvent.VideoResolution(data[1].toInt() and 0xFF)
            } else null
            TAG_GAMEPAD -> if (data.size >= GAMEPAD_MESSAGE_BYTES) {
                XyHostInputEvent.Gamepad(
                    XyGamepadState(
                        buttons = le16(1),
                        leftTrigger = data[3].toInt() and 0xFF,
                        rightTrigger = data[4].toInt() and 0xFF,
                        leftX = le16signed(5),
                        leftY = le16signed(7),
                        rightX = le16signed(9),
                        rightY = le16signed(11),
                    ),
                )
            } else null
            TAG_VIDEO_FPS -> if (data.size >= 2) {
                XyHostInputEvent.VideoFps(data[1].toInt() and 0xFF)
            } else null
            TAG_VIRTUAL_DISPLAY -> if (data.size >= VIRTUAL_DISPLAY_BYTES) {
                XyHostInputEvent.VirtualDisplay(
                    width = le16(1),
                    height = le16(3),
                    refreshHz = data[5].toInt() and 0xFF,
                    count = data[6].toInt() and 0xFF,
                )
            } else null
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // helper
    // ------------------------------------------------------------------

    /** Event generasi pertama: selalu 8 byte, payload dipadding nol. */
    private inline fun fixed(tag: Int, body: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(FIXED_EVENT_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(tag.toByte())
            body()
        }.array()

    /** Event modern: panjang persis sesuai payload (tanpa padding). */
    private inline fun exact(tag: Int, body: ByteBuffer.() -> Unit): ByteArray {
        val probe = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        probe.body()
        val payload = probe.position()
        return ByteBuffer.allocate(1 + payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(tag.toByte())
            body()
        }.array()
    }

    private inline fun variable(tag: Int, value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return ByteArray(1 + bytes.size).also {
            it[0] = tag.toByte()
            bytes.copyInto(it, 1)
        }
    }

    /** Potong di batas unit UTF-16 tanpa membelah pasangan surrogate. */
    internal fun clampTextUnits(value: String): String {
        var units = 0
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            val isPair = Character.isHighSurrogate(ch) &&
                i + 1 < value.length && Character.isLowSurrogate(value[i + 1])
            // Pasangan surrogate utuh atau tidak sama sekali — membelahnya
            // meninggalkan high surrogate menggantung (meniru text_batches host).
            val chUnits = if (isPair) 2 else 1
            if (units + chUnits > MAX_TEXT_UNITS) break
            sb.append(ch)
            if (isPair) sb.append(value[i + 1])
            units += chUnits
            i += chUnits
        }
        return sb.toString()
    }
}

/** Tombol mouse host: 0=kiri, 1=kanan, 2=tengah, 3=x1, 4=x2. */
enum class XyHostMouseButton(val code: Int) {
    LEFT(0),
    RIGHT(1),
    MIDDLE(2),
    X1(3),
    X2(4),
}

/** Preset kualitas video host. */
enum class XyHostVideoQuality(val code: Int) {
    AUTO(0),
    MEDIUM(1),
    HIGH(2),
    ULTRA(3),
}

/** Mode resolusi kiriman host: 720p / 1080p / native (maks 4096×2160). */
enum class XyHostVideoResolution(val code: Int) {
    P720(0),
    P1080(1),
    NATIVE(2),
}

/** Dekode cermin dari [XyHostInputCodec] — satu event per pesan. */
sealed class XyHostInputEvent {
    data class MouseMoveRel(val dx: Int, val dy: Int) : XyHostInputEvent()
    data class MouseMoveAbs(val x: Int, val y: Int) : XyHostInputEvent()
    data class MouseButton(val button: Int, val down: Boolean) : XyHostInputEvent()
    data class Scroll(val dx: Int, val dy: Int) : XyHostInputEvent()
    data class Key(val vk: Int, val down: Boolean) : XyHostInputEvent()
    data class Text(val value: String) : XyHostInputEvent()
    data class DisplaySelect(val index: Int) : XyHostInputEvent()
    data class ClipboardSet(val value: String) : XyHostInputEvent()
    object ClipboardRequest : XyHostInputEvent()
    data class VideoQuality(val code: Int) : XyHostInputEvent()
    data class VirtualDisplay(
        val width: Int,
        val height: Int,
        val refreshHz: Int,
        val count: Int,
    ) : XyHostInputEvent()
    data class VideoBitrate(val mbps: Int) : XyHostInputEvent()
    data class VideoResolution(val code: Int) : XyHostInputEvent()
    data class Gamepad(val state: XyGamepadState) : XyHostInputEvent()
    data class VideoFps(val fps: Int) : XyHostInputEvent()
}
