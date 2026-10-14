package id.xydesk.remote.pcstream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Vektor emas dihitung manual dari layout `host/src/input.rs` (little-endian)
 * supaya kecocokan byte dengan host Rust terkunci oleh test, bukan asumsi.
 */
class XyHostInputCodecTest {

    // ---------------------------------------------------------------- encode

    @Test
    fun `mouse move rel 8 byte dengan padding nol`() {
        // dx=300 (0x012C -> 2C 01), dy=-300 (0xFED4 -> D4 FE)
        assertArrayEquals(
            byteArrayOf(0x01, 0x2C, 0x01, 0xD4.toByte(), 0xFE.toByte(), 0, 0, 0),
            XyHostInputCodec.mouseMoveRel(300, -300),
        )
    }

    @Test
    fun `mouse move abs ternormalisasi`() {
        assertArrayEquals(
            byteArrayOf(0x02, 0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0),
            XyHostInputCodec.mouseMoveAbs(65_535, 0),
        )
    }

    @Test
    fun `mouse button kanan down`() {
        assertArrayEquals(
            byteArrayOf(0x03, 1, 1, 0, 0, 0, 0, 0),
            XyHostInputCodec.mouseButton(1, true),
        )
    }

    @Test
    fun `scroll satuan wheel delta negatif`() {
        // dx=-120 (0xFF88 -> 88 FF), dy=120 (0x0078 -> 78 00)
        assertArrayEquals(
            byteArrayOf(0x04, 0x88.toByte(), 0xFF.toByte(), 0x78, 0, 0, 0, 0),
            XyHostInputCodec.scroll(-120, 120),
        )
    }

    @Test
    fun `key pakai windows virtual key`() {
        // VK_ESCAPE = 0x1B
        assertArrayEquals(
            byteArrayOf(0x05, 0x1B, 0, 1, 0, 0, 0, 0),
            XyHostInputCodec.key(0x1B, true),
        )
    }

    @Test
    fun `text utf8 variabel`() {
        assertArrayEquals(
            byteArrayOf(0x06, 0x48, 0xC3.toByte(), 0xA9.toByte()),
            XyHostInputCodec.text("Hé"),
        )
    }

    @Test
    fun `display select 2 byte`() {
        assertArrayEquals(byteArrayOf(0x07, 2), XyHostInputCodec.displaySelect(2))
    }

    @Test
    fun `clipboard set variabel`() {
        assertArrayEquals(byteArrayOf(0x08, 0x61, 0x62), XyHostInputCodec.clipboardSet("ab"))
    }

    @Test
    fun `clipboard request 1 byte`() {
        assertArrayEquals(byteArrayOf(0x09), XyHostInputCodec.clipboardRequest())
    }

    @Test
    fun `video quality preset`() {
        assertArrayEquals(byteArrayOf(0x0A, 2), XyHostInputCodec.videoQuality(XyHostVideoQuality.HIGH))
    }

    @Test
    fun `video bitrate uint16 little endian`() {
        // 50 Mbps = 0x0032 -> 32 00
        assertArrayEquals(byteArrayOf(0x0B, 0x32, 0x00), XyHostInputCodec.videoBitrate(50))
    }

    @Test
    fun `video resolution mode`() {
        assertArrayEquals(byteArrayOf(0x0C, 1), XyHostInputCodec.videoResolution(XyHostVideoResolution.P1080))
    }

    @Test
    fun `video fps hanya 30 atau 60`() {
        assertArrayEquals(byteArrayOf(0x0F, 60), XyHostInputCodec.videoFps(60))
        assertThrows<IllegalArgumentException> { XyHostInputCodec.videoFps(45) }
    }

    @Test
    fun `gamepad 13 byte laporan xusb`() {
        // buttons = A|DPAD_UP = 0x1001 -> 01 10; lt=255; rt=128;
        // lx=-32768 (00 80); ly=32767 (FF 7F); rx=-1 (FF FF); ry=1 (01 00)
        assertArrayEquals(
            byteArrayOf(
                0x0E,
                0x01, 0x10,
                0xFF.toByte(), 0x80.toByte(),
                0x00, 0x80.toByte(),
                0xFF.toByte(), 0x7F,
                0xFF.toByte(), 0xFF.toByte(),
                0x01, 0x00,
            ),
            XyHostInputCodec.gamepad(
                XyGamepadState(
                    buttons = XyGamepadPacketV1.A or XyGamepadPacketV1.DPAD_UP,
                    leftTrigger = 255,
                    rightTrigger = 128,
                    leftX = -32_768,
                    leftY = 32_767,
                    rightX = -1,
                    rightY = 1,
                ),
            ),
        )
    }

    @Test
    fun `bit tombol gamepad identik xinput`() {
        // XUSB: DPAD 0x0001..0x0008, START 0x0010, BACK 0x0020, THUMB 0x0040/0x0080,
        // SHOULDER 0x0100/0x0200, A 0x1000, B 0x2000, X 0x4000, Y 0x8000.
        assertEquals(0x0001, XyGamepadPacketV1.DPAD_UP)
        assertEquals(0x0002, XyGamepadPacketV1.DPAD_DOWN)
        assertEquals(0x0004, XyGamepadPacketV1.DPAD_LEFT)
        assertEquals(0x0008, XyGamepadPacketV1.DPAD_RIGHT)
        assertEquals(0x0010, XyGamepadPacketV1.START)
        assertEquals(0x0020, XyGamepadPacketV1.BACK)
        assertEquals(0x0040, XyGamepadPacketV1.LEFT_THUMB)
        assertEquals(0x0080, XyGamepadPacketV1.RIGHT_THUMB)
        assertEquals(0x0100, XyGamepadPacketV1.LEFT_SHOULDER)
        assertEquals(0x0200, XyGamepadPacketV1.RIGHT_SHOULDER)
        assertEquals(0x1000, XyGamepadPacketV1.A)
        assertEquals(0x2000, XyGamepadPacketV1.B)
        assertEquals(0x4000, XyGamepadPacketV1.X)
        assertEquals(0x8000, XyGamepadPacketV1.Y)
    }

    // ---------------------------------------------------------------- validasi

    @Test
    fun `rentang nilai ditolak sebelum dikirim`() {
        assertThrows<IllegalArgumentException> { XyHostInputCodec.mouseMoveRel(40_000, 0) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.mouseMoveAbs(-1, 0) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.mouseButton(5, true) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.key(70_000, true) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.videoBitrate(51) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.displaySelect(256) }
        assertThrows<IllegalArgumentException> {
            XyHostInputCodec.gamepad(XyGamepadState(leftTrigger = 256))
        }
    }

    // ---------------------------------------------------------------- decode

    @Test
    fun `decode round-trip semua event`() {
        assertEquals(
            XyHostInputEvent.MouseMoveRel(-15, 240),
            XyHostInputCodec.decode(XyHostInputCodec.mouseMoveRel(-15, 240)),
        )
        assertEquals(
            XyHostInputEvent.MouseMoveAbs(12_345, 54_321),
            XyHostInputCodec.decode(XyHostInputCodec.mouseMoveAbs(12_345, 54_321)),
        )
        assertEquals(
            XyHostInputEvent.MouseButton(3, false),
            XyHostInputCodec.decode(XyHostInputCodec.mouseButton(3, false)),
        )
        assertEquals(
            XyHostInputEvent.Scroll(-240, 360),
            XyHostInputCodec.decode(XyHostInputCodec.scroll(-240, 360)),
        )
        assertEquals(
            XyHostInputEvent.Key(0x41, true),
            XyHostInputCodec.decode(XyHostInputCodec.key(0x41, true)),
        )
        assertEquals(
            XyHostInputEvent.Text("Ketikan résumé ✓"),
            XyHostInputCodec.decode(XyHostInputCodec.text("Ketikan résumé ✓")),
        )
        assertEquals(
            XyHostInputEvent.DisplaySelect(1),
            XyHostInputCodec.decode(XyHostInputCodec.displaySelect(1)),
        )
        assertEquals(
            XyHostInputEvent.ClipboardSet("isi klip"),
            XyHostInputCodec.decode(XyHostInputCodec.clipboardSet("isi klip")),
        )
        assertEquals(
            XyHostInputEvent.ClipboardRequest,
            XyHostInputCodec.decode(XyHostInputCodec.clipboardRequest()),
        )
        assertEquals(
            XyHostInputEvent.VideoQuality(XyHostVideoQuality.ULTRA.code),
            XyHostInputCodec.decode(XyHostInputCodec.videoQuality(XyHostVideoQuality.ULTRA)),
        )
        assertEquals(
            XyHostInputEvent.VideoBitrate(0),
            XyHostInputCodec.decode(XyHostInputCodec.videoBitrate(0)),
        )
        assertEquals(
            XyHostInputEvent.VideoResolution(XyHostVideoResolution.NATIVE.code),
            XyHostInputCodec.decode(XyHostInputCodec.videoResolution(XyHostVideoResolution.NATIVE)),
        )
        assertEquals(
            XyHostInputEvent.VideoFps(30),
            XyHostInputCodec.decode(XyHostInputCodec.videoFps(30)),
        )
    }

    @Test
    fun `decode gamepad kembali ke state penuh`() {
        val state = XyGamepadState(
            buttons = XyGamepadPacketV1.B or XyGamepadPacketV1.START or XyGamepadPacketV1.DPAD_LEFT,
            leftTrigger = 200,
            rightTrigger = 7,
            leftX = -12_345,
            leftY = 12_345,
            rightX = -32_768,
            rightY = 32_767,
        )
        val decoded = XyHostInputCodec.decode(XyHostInputCodec.gamepad(state))
        assertEquals(XyHostInputEvent.Gamepad(state), decoded)
    }

    @Test
    fun `pesan rusak dibuang diam-diam seperti host`() {
        assertNull(XyHostInputCodec.decode(ByteArray(0)))
        assertNull(XyHostInputCodec.decode(byteArrayOf(0x01, 0x2C))) // move terpotong
        assertNull(XyHostInputCodec.decode(byteArrayOf(0x0E, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))) // gamepad 12 byte
        assertNull(XyHostInputCodec.decode(byteArrayOf(0x7A))) // tag tak dikenal
    }

    @Test
    fun `decode menerima padding 8 byte maupun panjang persis`() {
        // Dekoder host memakai n >= minimum; klien mengirim keduanya tergantung
        // generasi event, jadi decode harus menerima dua bentuk yang sama.
        val padded = byteArrayOf(0x07, 2, 0, 0, 0, 0, 0, 0)
        val exact = byteArrayOf(0x07, 2)
        assertEquals(
            XyHostInputCodec.decode(padded),
            XyHostInputCodec.decode(exact),
        )
    }

    // ---------------------------------------------------------------- teks

    @Test
    fun `teks dipotong di batas unit utf-16 host`() {
        val long = "a".repeat(5_000)
        assertEquals(XyHostInputCodec.MAX_TEXT_UNITS, XyHostInputCodec.clampTextUnits(long).length)
    }

    @Test
    fun `pasangan surrogate tidak terbelah di batas`() {
        // 4094 BMP + 1 pasangan surrogate = 4096 unit: pasangan utuh muat.
        val fits = "a".repeat(4_094) + "😀"
        assertEquals(4_096, XyHostInputCodec.clampTextUnits(fits).length)
        // 4095 BMP + 1 pasangan = 4097 unit: pasangan dibuang utuh, bukan
        // high surrogate menggantung.
        val overflows = "a".repeat(4_095) + "😀b"
        val clamped = XyHostInputCodec.clampTextUnits(overflows)
        assertEquals(4_095, clamped.length)
        assertTrue(clamped.none { Character.isHighSurrogate(it) })
    }

    @Test
    fun `virtual display 7 byte little endian`() {
        val packet = XyHostInputCodec.virtualDisplay(1920, 1080, 120, 2)
        assertEquals(7, packet.size)
        assertEquals(0x10, packet[0].toInt() and 0xFF)
        assertEquals(1920, (packet[1].toInt() and 0xFF) or ((packet[2].toInt() and 0xFF) shl 8))
        assertEquals(1080, (packet[3].toInt() and 0xFF) or ((packet[4].toInt() and 0xFF) shl 8))
        assertEquals(120, packet[5].toInt() and 0xFF)
        assertEquals(2, packet[6].toInt() and 0xFF)
    }

    @Test
    fun `decode virtual display roundtrip`() {
        val decoded = XyHostInputCodec.decode(XyHostInputCodec.virtualDisplay(2560, 1440, 144, 1))
        assertEquals(XyHostInputEvent.VirtualDisplay(2560, 1440, 144, 1), decoded)
        assertNull(XyHostInputCodec.decode(byteArrayOf(0x10, 0x80, 0x07))) // payload terpotong
    }

    @Test
    fun `virtual display menolak nilai di luar jangkauan`() {
        assertThrows<IllegalArgumentException> { XyHostInputCodec.virtualDisplay(100, 1080) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.virtualDisplay(1920, 100) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.virtualDisplay(1920, 1080, 300) }
        assertThrows<IllegalArgumentException> { XyHostInputCodec.virtualDisplay(1920, 1080, 60, 5) }
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (expected: Throwable) {
            if (!T::class.isInstance(expected)) throw expected
        }
    }
}
