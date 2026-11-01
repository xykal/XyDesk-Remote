package id.xydesk.remote.pcstream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XyGamepadPacketTest {
    @Test
    fun encodesAndDecodesCompleteXInputStateInLittleEndian() {
        val state = XyGamepadState(
            buttons = XyGamepadPacketV1.A or XyGamepadPacketV1.LEFT_SHOULDER or XyGamepadPacketV1.DPAD_UP,
            leftTrigger = 255,
            rightTrigger = 19,
            leftX = -32_768,
            leftY = 32_767,
            rightX = -12_345,
            rightY = 23_456,
        )
        val packet = XyGamepadPacketV1.encode(
            sequence = 0xFFFF_FFFFL,
            timestampMs = 0xFEDC_BA98L,
            state = state,
        )

        assertEquals(XyGamepadPacketV1.BYTE_SIZE, packet.size)
        assertArrayEquals(byteArrayOf('X'.code.toByte(), 'Y'.code.toByte(), 'G'.code.toByte(), 'P'.code.toByte()), packet.copyOfRange(0, 4))
        val decoded = XyGamepadPacketV1.decode(packet)
        assertEquals(0xFFFF_FFFFL, decoded.sequence)
        assertEquals(0xFEDC_BA98L, decoded.timestampMs32)
        assertEquals(state, decoded.state)
    }

    @Test
    fun sequenceComparisonHandlesUnsignedWraparound() {
        assertTrue(XyGamepadPacketV1.isNewerSequence(0, 0xFFFF_FFFFL))
        assertTrue(XyGamepadPacketV1.isNewerSequence(4, 3))
        assertFalse(XyGamepadPacketV1.isNewerSequence(7, 7))
        assertFalse(XyGamepadPacketV1.isNewerSequence(0xFFFF_FFFFL, 0))
    }

    @Test
    fun rejectsMalformedPacketAndOutOfRangeValues() {
        val valid = XyGamepadPacketV1.encode(1, 1, XyGamepadState())
        val badMagic = valid.copyOf().also { it[0] = 0 }
        val badVersion = valid.copyOf().also { it[4] = 2 }
        val badSize = valid.copyOf().also { it[6] = 0 }

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            XyGamepadPacketV1.decode(valid.copyOf(valid.size - 1))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { XyGamepadPacketV1.decode(badMagic) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { XyGamepadPacketV1.decode(badVersion) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { XyGamepadPacketV1.decode(badSize) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            XyGamepadPacketV1.encode(0x1_0000_0000L, 0, XyGamepadState())
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            XyGamepadPacketV1.encode(0, 0, XyGamepadState(leftX = 32_768))
        }
    }
}
