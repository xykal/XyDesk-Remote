package id.xydesk.remote.ui

/**
 * Sidik jari SHA-256 sertifikat penerbit resmi, tersimpan terurai,
 * plus pembanding murni yang bisa diuji unit tanpa Android.
 *
 * Nilai MARK sama dengan yang dilaporkan `SIGNATURE-VERIFICATION.txt`
 * di setiap rilis (sudah publik, bukan rahasia baru).
 */
internal object SignerMark {
    val MARK = intArrayOf(
        0x84, 0x29, 0xF6, 0x89, 0xE7, 0xC3, 0xC6, 0x0A,
        0xA4, 0x4C, 0xB9, 0x24, 0x20, 0x6D, 0x56, 0xD5,
        0xA2, 0xDC, 0xCB, 0xD6, 0xC8, 0x8C, 0xB9, 0x00,
        0x83, 0x5C, 0x19, 0x01, 0xAD, 0x21, 0x96, 0x52,
    )

    /**
     * True bila `digest` (byte SHA-256, bertanda) sama dengan MARK.
     *
     * BUG LAMA yang diperbaiki di sini: dulu byte dibandingkan lewat
     * `byte.toInt()` yang memperluas tanda (0x84 jadi 0xFFFFFF84) sehingga
     * build resmi pun dianggap tidak cocok. Sekarang setiap byte dibaca
     * tanpa tanda (`and 0xFF`) sebelum XOR.
     */
    fun matches(digest: ByteArray): Boolean {
        if (digest.size != MARK.size) return false
        var diff = 0
        for (i in MARK.indices) diff = diff or ((digest[i].toInt() and 0xFF) xor MARK[i])
        return diff == 0
    }
}
