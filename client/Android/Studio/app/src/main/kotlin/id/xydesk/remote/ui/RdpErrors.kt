package id.xydesk.remote.ui

/**
 * Peta kode error koneksi XyDesk (dan kata kunci pesan native) menjadi
 * penjelasan dua bahasa yang BISA DITINDAKLANJUTI user.
 *
 * Dulu overlay error cuma menempelkan pesan mentah + satu hint khusus
 * unreachable. Sekarang setiap kode [SessionState.Error.code] yang
 * diketahui punya penjelasan; pesan mentah tetap ditampilkan di bawahnya
 * supaya laporan bug tetap punya isi teknis.
 */
internal object RdpErrors {

    /**
     * Balikan pas (Indonesia, English), atau null kalau tidak ada yang
     * lebih berguna dari pesan mentah.
     */
    fun hint(code: String, message: String): Pair<String, String>? {
        val m = message.lowercase()
        return when {
            // Probe TCP gagal: RDP mati / firewall / host salah / Windows Home.
            code == "unreachable" -> Pair(
                "Host tidak menjawab di port RDP. Cek: RDP aktif (Windows Pro/Server), " +
                    "firewall, dan alamat/tailnet benar.",
                "The host did not answer on the RDP port. Check that RDP is enabled " +
                    "(Windows Pro/Server), the firewall, and the address/tailnet.",
            )

            // Watchdog: TCP mungkin nyambung tapi handshake/NLA tidak selesai.
            code == "connect_timeout" -> Pair(
                "Koneksi menggantung lebih dari 30 detik. Kemungkinan: firewall " +
                    "memblokir setelah TCP, server lambat, atau NLA/TLS tidak selesai.",
                "The connection stalled for over 30 seconds. Likely: a firewall blocks " +
                    "after TCP, a slow server, or NLA/TLS never finished.",
            )

            // Event native onConnectionFailure.
            code == "connect_failed" -> when {
                m.contains("credential") || m.contains("logon") || m.contains("auth") -> Pair(
                    "Kredensial ditolak server. Cek username, password, dan domain " +
                        "(bisa diisi kosong untuk PC rumah).",
                    "The server rejected the credentials. Check the username, password, " +
                        "and domain (it can be empty for home PCs).",
                )

                m.contains("certificate") || m.contains("tls") || m.contains("ssl") -> Pair(
                    "Negosiasi TLS gagal. Sertifikat server mungkin ditolak atau " +
                        "cipher tidak didukung.",
                    "TLS negotiation failed. The server certificate may have been " +
                        "denied, or the cipher is unsupported.",
                )

                else -> Pair(
                    "Server menolak/memutus saat handshake. Pastikan RDP aktif di " +
                        "host, lalu cek kredensial (NLA) dan jaringan.",
                    "The server refused or dropped the connection during handshake. " +
                        "Make sure RDP is enabled, then check credentials (NLA) and network.",
                )
            }

            // session.connect() melempar exception JVM/JNI.
            code == "connect_exception" -> Pair(
                "Mesin RDP gagal memulai koneksi (kesalahan internal). Log bisa " +
                    "dilihat lewat tombol Detail.",
                "The RDP engine failed to start the connection (internal error). " +
                    "See the log via Details.",
            )

            else -> when {
                m.contains("credential") || m.contains("logon") -> Pair(
                    "Kredensial ditolak server. Cek username, password, dan domain.",
                    "The server rejected the credentials. Check the username, password, and domain.",
                )

                m.contains("timeout") || m.contains("timed out") -> Pair(
                    "Server tidak merespons tepat waktu. Jaringan atau server sedang lambat.",
                    "The server did not respond in time. The network or server is slow.",
                )

                else -> null
            }
        }
    }
}
