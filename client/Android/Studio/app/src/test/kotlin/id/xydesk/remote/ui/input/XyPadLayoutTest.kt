package id.xydesk.remote.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XyPadLayoutTest {
    private val w = 2400
    private val h = 1080
    private val cw = 300f
    private val ch = 200f

    @Test
    fun draggingNeverPushesAClusterOffScreen() {
        val start = XyPadPos(0.5f, 0.5f)
        val farRight = XyPadLayout.drag(start, 99_999f, 0f, w, h, cw, ch)
        val (right, top) = XyPadLayout.topLeftPx(farRight, w, h, cw, ch)
        assertEquals(w - cw, right, 0.5f)
        assertTrue(top >= 0f && top <= h - ch)

        val farUpLeft = XyPadLayout.drag(start, -99_999f, -99_999f, w, h, cw, ch)
        val (lx, ly) = XyPadLayout.topLeftPx(farUpLeft, w, h, cw, ch)
        assertEquals(0f, lx, 0.5f)
        assertEquals(0f, ly, 0.5f)
    }

    @Test
    fun dragMovesByExactlyTheDeltaInTheMiddleOfTheScreen() {
        val start = XyPadLayout.topLeftPx(XyPadPos(0.5f, 0.5f), w, h, cw, ch)
        val moved = XyPadLayout.drag(XyPadPos(0.5f, 0.5f), 40f, -25f, w, h, cw, ch)
        val after = XyPadLayout.topLeftPx(moved, w, h, cw, ch)
        assertEquals(start.first + 40f, after.first, 1f)
        assertEquals(start.second - 25f, after.second, 1f)
    }

    @Test
    fun fractionRoundTripIsStable() {
        val original = XyPadPos(0.31f, 0.77f)
        val px = XyPadLayout.topLeftPx(original, w, h, cw, ch)
        val back = XyPadLayout.fromTopLeftPx(px.first, px.second, w, h, cw, ch)
        assertEquals(original.x, back.x, 1e-3f)
        assertEquals(original.y, back.y, 1e-3f)
    }

    @Test
    fun defaultLayoutKeepsEveryClusterFullyOnScreen() {
        XyPadLayout.defaults().forEach { (cluster, pos) ->
            val (x, y) = XyPadLayout.topLeftPx(pos, w, h, cw, ch)
            assertTrue("$cluster x", x >= 0f && x <= w - cw)
            assertTrue("$cluster y", y >= 0f && y <= h - ch)
        }
    }

    @Test
    fun defaultTopBarDoesNotCollideWithTheCenteredSpotifyPlayer() {
        // Pemutar Spotify mengambang di tengah-atas: kira-kira 240dp lebar,
        // dipusatkan, mulai ~10dp dari atas.
        val bar = XyPadLayout.defaults().getValue(XyPadCluster.TOP_BAR)
        val (bx, by) = XyPadLayout.topLeftPx(bar, w, h, 620f, 110f)
        val spotifyX = w / 2f - 360f
        val overlaps = XyPadLayout.overlaps(bx, by, 620f, 110f, spotifyX, 30f, 720f, 110f)
        assertFalse("TOP_BAR menutupi pemutar Spotify", overlaps)
    }

    @Test
    fun overlapDetectionIsSymmetricAndIgnoresTouchingEdges() {
        assertTrue(XyPadLayout.overlaps(0f, 0f, 10f, 10f, 5f, 5f, 10f, 10f))
        assertTrue(XyPadLayout.overlaps(5f, 5f, 10f, 10f, 0f, 0f, 10f, 10f))
        // Hanya bersentuhan di tepi tidak dihitung sebagai tumpang tindih.
        assertFalse(XyPadLayout.overlaps(0f, 0f, 10f, 10f, 10f, 0f, 10f, 10f))
        assertFalse(XyPadLayout.overlaps(0f, 0f, 10f, 10f, 50f, 50f, 10f, 10f))
    }

    @Test
    fun layoutCodecSurvivesRoundTripAndRejectsGarbage() {
        val layout = XyPadLayout.defaults()
        val encoded = XyPadLayout.encode(layout)
        val decoded = XyPadLayout.decode(encoded)
        assertNotNull(decoded)
        assertEquals(layout.keys, decoded!!.keys)
        layout.forEach { (k, v) ->
            assertEquals(v.x, decoded.getValue(k).x, 1e-3f)
            assertEquals(v.y, decoded.getValue(k).y, 1e-3f)
        }

        assertNull(XyPadLayout.decode(null))
        assertNull(XyPadLayout.decode(""))
        assertNull(XyPadLayout.decode("BUKAN_KLASTER:0.1,0.2"))
        // Entri rusak dilewati, bukan membuat seluruh tata letak gagal.
        val partial = XyPadLayout.decode("DPAD:0.1,0.2|RUSAK|ABXY:0.3")
        assertEquals(setOf(XyPadCluster.DPAD), partial?.keys)
    }

    @Test
    fun resolveFillsMissingClustersFromDefaultsAndIgnoresUnknown() {
        val stored = mapOf(XyPadCluster.DPAD to XyPadPos(0.5f, 0.5f))
        val resolved = XyPadLayout.resolve(stored)
        assertEquals(XyPadPos(0.5f, 0.5f), resolved.getValue(XyPadCluster.DPAD))
        assertEquals(
            XyPadLayout.defaults().getValue(XyPadCluster.LEFT_STICK),
            resolved.getValue(XyPadCluster.LEFT_STICK),
        )
        assertEquals(XyPadLayout.defaults(), XyPadLayout.resolve(null))
    }

    @Test
    fun scaleIsClampedToAUsableRange() {
        assertEquals(XyPadLayout.MIN_SCALE, XyPadLayout.clampScale(0.1f), 0f)
        assertEquals(XyPadLayout.MAX_SCALE, XyPadLayout.clampScale(9f), 0f)
        assertEquals(1f, XyPadLayout.clampScale(1f), 0f)
    }
}
