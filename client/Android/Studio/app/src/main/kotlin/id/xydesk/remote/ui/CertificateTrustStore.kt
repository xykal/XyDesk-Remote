package id.xydesk.remote.ui

import android.content.Context

/**
 * M2.5 — "Percaya & ingat" sertifikat server.
 *
 * Menyimpan fingerprint SHA-256 per `host:port` di SharedPreferences.
 *  - Fingerprint sama pada koneksi berikutnya = auto-approve (tanpa dialog)
 *  - Fingerprint BERBEDA dengan yang tersimpan = dialog dengan warning
 *    kuat (kemungkinan MITM / server ganti cert)
 *
 * Safe default tetap terjaga: TIDAK ada auto-approve tanpa fingerprint
 * yang pernah disimpan pengguna secara eksplisit.
 */
class CertificateTrustStore(context: Context) {

    private val sp =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Fingerprint tersimpan untuk host:port, null jika belum pernah dipercaya. */
    fun fingerprint(host: String, port: Int): String? = sp.getString(key(host, port), null)

    fun trust(host: String, port: Int, fingerprint: String) {
        sp.edit().putString(key(host, port), fingerprint).apply()
    }

    fun remove(host: String, port: Int) {
        sp.edit().remove(key(host, port)).apply()
    }

    companion object {
        private const val NAME = "xydesk.certtrust"
        private fun key(host: String, port: Int) = "fp.$host:$port"
    }
}
