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

// A quiet WebSocket is not a dead socket: OkHttp ping failures invoke onFailure.
// Real-time messages still arrive over WS; HTTP is only a consistency fallback.
internal fun chatHistoryPollDelayMs(socketConnected: Boolean): Long =
    if (socketConnected) 300_000L else 10_000L
