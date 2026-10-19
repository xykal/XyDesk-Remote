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
