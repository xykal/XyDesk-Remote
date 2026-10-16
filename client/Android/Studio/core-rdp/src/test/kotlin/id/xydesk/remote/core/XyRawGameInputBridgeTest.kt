package id.xydesk.remote.core

import org.junit.Assert.assertEquals
import org.junit.Test

class XyRawGameInputBridgeTest {

    @Test
    fun `encodeMouseRel menghasilkan 12 byte little endian presisi`() {
        val pkt = XyRawGameInputBridge.encodeMouseRel(dx = 250, dy = -180)
        assertEquals(12, pkt.size)
        assertEquals('X'.code.toByte(), pkt[0])
        assertEquals('Y'.code.toByte(), pkt[1])
        assertEquals('I'.code.toByte(), pkt[2])
        assertEquals('1'.code.toByte(), pkt[3])
        assertEquals(XyRawGameInputBridge.CMD_MOUSE_REL, pkt[4])
        assertEquals(250, XyRawGameInputBridge.readInt16Le(pkt, 6))
        assertEquals(-180, XyRawGameInputBridge.readInt16Le(pkt, 8))
    }

    @Test
    fun `encodeMouseButton dan encodeKeyVk sesuai spesifikasi XYI1`() {
        val btn = XyRawGameInputBridge.encodeMouseButton(button = 1, down = true)
        assertEquals(XyRawGameInputBridge.CMD_MOUSE_BTN, btn[4])
        assertEquals(1.toByte(), btn[5])
        assertEquals(1, XyRawGameInputBridge.readInt16Le(btn, 6))

        val key = XyRawGameInputBridge.encodeKeyVk(vk = 0x57, down = true, scanCode = 0x11)
        assertEquals(XyRawGameInputBridge.CMD_KEY_VK, key[4])
        assertEquals(1.toByte(), key[5])
        assertEquals(0x57, XyRawGameInputBridge.readInt16Le(key, 6))
        assertEquals(0x11, XyRawGameInputBridge.readInt16Le(key, 8))
    }

    @Test
    fun `encodeSubscribe XYV1 menyematkan fps quality dan scale`() {
        val sub = XyDirectConsoleStreamClient.encodeSubscribe(
            enable = true,
            fps = 120,
            quality = 75,
            scalePct = 85,
        )
        assertEquals(12, sub.size)
        assertEquals('X'.code.toByte(), sub[0])
        assertEquals('Y'.code.toByte(), sub[1])
        assertEquals('V'.code.toByte(), sub[2])
        assertEquals('1'.code.toByte(), sub[3])
        assertEquals(XyDirectConsoleStreamClient.CMD_SUBSCRIBE, sub[4])
        assertEquals(120, sub[5].toInt() and 0xFF)
        assertEquals(75, sub[6].toInt() and 0xFF)
        assertEquals(85, sub[7].toInt() and 0xFF)
    }
}
