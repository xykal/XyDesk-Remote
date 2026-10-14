package id.xydesk.remote.core

import kotlin.math.roundToInt

/**
 * Statistik pipeline frame sisi HP — tahap pengukuran untuk optimasi latensi
 * (issue #15 B.6: "perlu pengukuran dulu sebelum optimasi").
 *
 * Titik ukur yang bisa diamati dari Kotlin (thread RDP -> antrean coalescing
 * dirty-rect -> flush di main thread):
 *
 *  1. INTERVAL UPDATE — jarak antar `onGraphicsUpdate` (pacing kiriman frame
 *     dari host; naik-turunnya = jitter jaringan/host).
 *  2. LATENSI PIPELINE — dari tibanya update PERTAMA sebuah batch di thread
 *     RDP sampai batch itu di-flush ke `SessionView` di main thread. Termasuk
 *     waktu menunggu di antrean coalescing (± satu vsync). Bukan waktu decode
 *     H.264 native — itu terjadi sebelum callback dan butuh hook JNI
 *     (tahap berikutnya); salinan piksel `updateGraphics` dicatat sebagai
 *     proksi beban frame sisi HP.
 *  3. ANTREAN COALESCING — jumlah update yang tergabung dalam satu flush.
 *     1 = UI kejar kiriman host; >1 = beberapa update menumpuk karena main
 *     thread tertinggal (puncak = beban sesaat).
 *
 * Thread-safe: [recordUpdate] dipanggil dari thread RDP, [recordFlush] dari
 * main thread, [snapshot] dari coroutine telemetri. Jam bisa disuntik supaya
 * murni dan bisa diuji unit.
 */
class FramePipelineStats(private val clock: () -> Long = { System.nanoTime() }) {

    /** EMA jarak antar update (ms); -1 = belum ada sampel. */
    @Volatile private var intervalMs = -1f

    /** EMA latensi update pertama batch -> flush (ms). */
    @Volatile private var latencyMs = -1f

    /** EMA durasi salin piksel updateGraphics (ms). */
    @Volatile private var copyMs = -1f

    /** Akumulator jendela snapshot (direset [snapshot]). */
    private var flushCount = 0
    private var depthSum = 0L
    private var depthPeak = 0

    private var lastArrivalNs = 0L

    /**
     * Catat satu update yang tiba di thread RDP.
     *
     * @param copyDurationNs durasi salinan piksel `updateGraphics`
     *   (nanodetik; 0 = tidak diukur).
     */
    @Synchronized
    fun recordUpdate(copyDurationNs: Long) {
        val now = clock()
        if (lastArrivalNs != 0L) {
            intervalMs = emaMs(intervalMs, now - lastArrivalNs)
        }
        lastArrivalNs = now
        if (copyDurationNs > 0L) {
            copyMs = emaMs(copyMs, copyDurationNs)
        }
    }

    /**
     * Catat satu flush di main thread.
     *
     * @param batchStartNs waktu tiba update pertama batch (jam yang sama
     *   dengan [recordUpdate]; 0 = tidak ada penanda, latensi dilewati).
     * @param updatesInBatch jumlah update yang tergabung dalam flush ini.
     */
    @Synchronized
    fun recordFlush(batchStartNs: Long, updatesInBatch: Int) {
        if (batchStartNs > 0L) {
            latencyMs = emaMs(latencyMs, clock() - batchStartNs)
        }
        if (updatesInBatch > 0) {
            flushCount += 1
            depthSum += updatesInBatch
            if (updatesInBatch > depthPeak) depthPeak = updatesInBatch
        }
    }

    /**
     * Ambil agregat jendela + nilai EMA terkini, lalu reset penghitung
     * jendela (rata-rata/puncak antrean). EMA (interval, latensi, salin)
     * tetap berjalan lintas snapshot supaya angkanya stabil di layar.
     */
    @Synchronized
    fun snapshot(): FramePipelineSample {
        val sample = FramePipelineSample(
            updateIntervalMs = intervalMs.roundToInt(),
            pipelineLatencyMs = latencyMs.roundToInt(),
            frameCopyMs = copyMs,
            queueDepth = if (flushCount > 0) depthSum.toFloat() / flushCount else -1f,
            queuePeak = depthPeak,
        )
        flushCount = 0
        depthSum = 0
        depthPeak = 0
        return sample
    }

    /** EMA milidetik dengan alpha [ALPHA] (sama seperti [RttSmoother]). */
    private fun emaMs(prev: Float, deltaNs: Long): Float {
        val sampleMs = (deltaNs / 1_000_000.0).toFloat().coerceAtLeast(0.05f)
        if (prev < 0f) return sampleMs
        return prev * (1f - ALPHA) + sampleMs * ALPHA
    }

    private companion object {
        const val ALPHA = 0.35f
    }
}

/** Satu bacaan agregat pipeline frame untuk [TelemetrySample]. */
data class FramePipelineSample(
    /** Jarak antar update rata-rata EMA (ms); -1 = belum ada. */
    val updateIntervalMs: Int = -1,
    /** Latensi update pertama batch -> flush ke layar (ms); -1 = belum ada. */
    val pipelineLatencyMs: Int = -1,
    /** Durasi salin piksel per update EMA (ms); -1 = belum ada. */
    val frameCopyMs: Float = -1f,
    /** Rata-rata update per flush di jendela snapshot; -1 = belum ada flush. */
    val queueDepth: Float = -1f,
    /** Puncak update per flush di jendela snapshot; 0 = belum ada flush. */
    val queuePeak: Int = 0,
)
