package id.xydesk.remote.ui

internal data class ChatHistoryFailure(val message: String, val retrySeconds: Long)

internal fun chatHistoryFailure(status: Int, code: String, retryAfter: String?): ChatHistoryFailure {
    val message = when (code) {
        "chat_storage_quota_exceeded" -> "Kuota penyimpanan chat sementara habis. Riwayat lokal tetap tersedia; tunggu pemulihan layanan."
        "chat_migration_pending" -> "Layanan chat sedang diperbarui. Perubahan belum terkonfirmasi; coba lagi nanti."
        else -> "Riwayat belum tersinkron (HTTP $status). Pesan tersimpan tidak dihapus; layanan akan dicoba lagi."
    }
    val seconds = (retryAfter?.toLongOrNull() ?: if (status == 503) 300L else 10L).coerceIn(10L, 600L)
    return ChatHistoryFailure(message, seconds)
}

internal fun chatSocketFailure(status: Int, retryAfter: String?): ChatHistoryFailure {
    val message = when (status) {
        401, 403 -> "Autentikasi chat ditolak. Perbarui login; koneksi belum tersedia."
        429 -> "Layanan chat membatasi permintaan. Koneksi akan dicoba lagi setelah jeda."
        503 -> "Backend chat belum tersedia. Koneksi dan sinkronisasi belum berhasil."
        else -> "Koneksi chat terputus atau gagal. Pesan lokal tetap disimpan."
    }
    val fallback = when (status) { 401, 403 -> 60L; 429, 503 -> 300L; else -> 10L }
    return ChatHistoryFailure(message, (retryAfter?.toLongOrNull() ?: fallback).coerceIn(10L, 600L))
}

// Ronde 85 — Realtime super-cepat: polling 3.5s saat WS live, 6s saat WS putus (bukan 5 menit).
// WS tetap sumber utama instant (<100ms), HTTP hanya safety-net biar gak ketinggalan chat baru.
internal fun chatHistoryPollDelayMs(socketConnected: Boolean): Long =
    if (socketConnected) 3_500L else 6_000L
