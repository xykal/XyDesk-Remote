package id.xydesk.remote.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AudioBridgeDiagnosticsTest {
    private fun packet(): ByteArray = ByteArray(16).also {
        "XYST1".toByteArray().copyInto(it)
        it[5] = 7; it[6] = 200.toByte()
        ByteBuffer.wrap(it, 8, 8).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).putInt(240)
    }
    @Test fun readsExistingUnsignedCountersAndFlags() {
        val report = parseHostAudioReport(packet(), 16, 100)!!
        assertEquals(7, report.flags)
        assertEquals(200, report.micLevel)
        assertEquals(4294967295L, report.audioPackets)
        assertEquals(240L, report.micFrames)
    }
    @Test fun malformedVersionAndBoundsNeverBecomeReports() {
        val bytes = packet()
        assertNull(parseHostAudioReport(bytes, 15, 0))
        assertNull(parseHostAudioReport(bytes, 17, 0))
        assertNull(parseHostAudioReport(bytes, 16, -1))
        bytes[4] = '2'.code.toByte()
        assertNull(parseHostAudioReport(bytes, 16, 0))
    }
    @Test fun oldOrBackwardClockReportNotPresentedAsCurrent() {
        val report = parseHostAudioReport(packet(), 16, 100)!!
        assertTrue(report.isFresh(5100))
        assertFalse(report.isFresh(5101))
        assertFalse(report.isFresh(99))
    }
    @Test fun endpointDetailBoundedAndBadPrefixRejected() {
        val bytes = packet() + "x".repeat(1000).toByteArray()
        assertEquals(496, parseHostAudioReport(bytes, bytes.size, 0)!!.detail.length)
        bytes[0] = 0
        assertNull(parseHostAudioReport(bytes, bytes.size, 0))
    }

}
