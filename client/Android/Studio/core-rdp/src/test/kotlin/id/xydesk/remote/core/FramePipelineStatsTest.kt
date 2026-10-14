package id.xydesk.remote.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Jam disuntik supaya pengujuran deterministik: now maju manual dalam
 * milidetik (dikali 1_000_000 jadi nanodetik seperti System.nanoTime).
 */
class FramePipelineStatsTest {

    private var nowMs = 0L
    private val stats = FramePipelineStats { nowMs * 1_000_000L }

    @Test
    fun `belum ada sampel menghasilkan nilai kosong`() {
        val sample = stats.snapshot()
        assertEquals(-1, sample.updateIntervalMs)
        assertEquals(-1, sample.pipelineLatencyMs)
        assertEquals(-1f, sample.frameCopyMs, 0.001f)
        assertEquals(-1f, sample.queueDepth, 0.001f)
        assertEquals(0, sample.queuePeak)
    }

    @Test
    fun `interval update adalah jarak antar kedatangan`() {
        nowMs = 0
        stats.recordUpdate(0)
        nowMs = 16
        stats.recordUpdate(0)
        // Satu jarak (0 -> 16ms) = sampel pertama EMA.
        assertEquals(16, stats.snapshot().updateIntervalMs)
    }

    @Test
    fun `interval update di-EMA bukan rata-rata polos`() {
        nowMs = 0
        stats.recordUpdate(0)
        nowMs = 16
        stats.recordUpdate(0)
        nowMs = 20
        stats.recordUpdate(0)
        // EMA: 16*0.65 + 4*0.35 = 11.8 -> 12
        assertEquals(12, stats.snapshot().updateIntervalMs)
    }

    @Test
    fun `durasi salin sub-milidetik tidak hilang`() {
        nowMs = 0
        stats.recordUpdate(500_000L) // 0.5 ms dalam ns
        val sample = stats.snapshot()
        assertEquals(0.5f, sample.frameCopyMs, 0.05f)
    }

    @Test
    fun `latensi pipeline dari update pertama batch sampai flush`() {
        nowMs = 100
        stats.recordUpdate(0)          // batch dibuka di t=100
        nowMs = 108
        stats.recordUpdate(0)          // update kedua menumpuk
        nowMs = 112
        stats.recordFlush(batchStartNs = 100 * 1_000_000L, updatesInBatch = 2)
        val sample = stats.snapshot()
        assertEquals(12, sample.pipelineLatencyMs)
        assertEquals(2f, sample.queueDepth, 0.001f)
        assertEquals(2, sample.queuePeak)
    }

    @Test
    fun `rata-rata dan puncak antrean dihitung per jendela snapshot`() {
        // Flush pertama: 1 update.
        nowMs = 0
        stats.recordUpdate(0)
        nowMs = 8
        stats.recordFlush(0, 1)
        // Flush kedua: 3 update menumpuk.
        nowMs = 20
        stats.recordUpdate(0)
        nowMs = 24
        stats.recordUpdate(0)
        nowMs = 28
        stats.recordUpdate(0)
        nowMs = 30
        stats.recordFlush(20 * 1_000_000L, 3)
        val first = stats.snapshot()
        assertEquals(2f, first.queueDepth, 0.001f) // (1+3)/2
        assertEquals(3, first.queuePeak)

        // Snapshot mereset jendela: flush berikutnya mulai dari nol lagi.
        nowMs = 40
        stats.recordUpdate(0)
        nowMs = 44
        stats.recordFlush(40 * 1_000_000L, 1)
        val second = stats.snapshot()
        assertEquals(1f, second.queueDepth, 0.001f)
        assertEquals(1, second.queuePeak)
    }

    @Test
    fun `ema latensi tetap berjalan lintas snapshot`() {
        nowMs = 1_000
        stats.recordUpdate(0)
        nowMs = 1_010
        stats.recordFlush(1_000 * 1_000_000L, 1)
        stats.snapshot() // jendela pertama: latensi 10ms

        nowMs = 2_000
        stats.recordUpdate(0)
        nowMs = 2_020
        stats.recordFlush(2_000 * 1_000_000L, 1)
        // EMA dari 10 dan 20: 10*0.65 + 20*0.35 = 13.
        assertEquals(13, stats.snapshot().pipelineLatencyMs)
    }

    @Test
    fun `batchStart nol dilewati tanpa merusak antrean`() {
        nowMs = 0
        stats.recordUpdate(0)
        nowMs = 5
        stats.recordFlush(0, 1) // tanpa penanda batch
        val sample = stats.snapshot()
        assertEquals(-1, sample.pipelineLatencyMs)
        assertEquals(1f, sample.queueDepth, 0.001f)
    }
}
