package id.xydesk.remote.core

import android.content.Context

/**
 * Opsi sesi per-perangkat. Semua nilai di sini dipetakan ke argumen FreeRDP
 * lewat query URI (lihat [RdpUri]) — jadi tiap toggle di UI benar-benar
 * mengubah perilaku native, bukan dekorasi.
 */
enum class XyAudioMode(
    val wire: Int,
    val title: String,
    val detail: String,
    val titleEn: String = title,
    val detailEn: String = detail,
) {
    DEVICE(
        0, "Putar di perangkat ini", "Audio dari remote keluar di speaker HP",
        "Play on this device", "Remote audio comes out of the phone speaker",
    ),
    REMOTE(
        1, "Putar di komputer remote", "Audio tetap di server (hemat bandwidth)",
        "Play on the remote computer", "Audio stays on the server (saves bandwidth)",
    ),
    OFF(
        2, "Matikan audio", "Tidak ada kanal audio",
        "Mute audio", "No audio channel at all",
    ),
}

/** RDP Gateway (RD Gateway). Port default 443. */
data class XyGateway(
    val host: String,
    val port: Int = 443,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
) {
    val id: String get() = "$host:$port"
}

data class RdpOptions(
    val audioMode: XyAudioMode = XyAudioMode.DEVICE,
    val microphone: Boolean = false,
    val clipboard: Boolean = true,
    val localDrive: Boolean = false,
    val camera: Boolean = false,
    /** RDP-UDP (multitransport, FEC). Lebih halus di jaringan mobile. */
    val udpTransport: Boolean = true,
    val networkAutoDetect: Boolean = true,
    /** RemoteFX/H.264 (GFX AVC444) untuk desktop yang berubah cepat. */
    val h264: Boolean = true,

    /**
     * `/dynamic-resolution` — nyalakan kanal DISP supaya ukuran desktop
     * remote bisa diubah saat sesi sudah jalan (tanpa reconnect).
     * Default nyala; tanpa ini server mengunci resolusi saat connect.
     */
    val dynamicResolution: Boolean = true,
    val gateway: XyGateway? = null,
) {
    fun write(context: Context, deviceId: String) {
        val sp = context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        sp.putInt("$deviceId.audio", audioMode.ordinal)
        sp.putBoolean("$deviceId.mic", microphone)
        sp.putBoolean("$deviceId.clipboard", clipboard)
        sp.putBoolean("$deviceId.drive", localDrive)
        sp.putBoolean("$deviceId.camera", camera)
        sp.putBoolean("$deviceId.udp", udpTransport)
        sp.putBoolean("$deviceId.netauto", networkAutoDetect)
        sp.putBoolean("$deviceId.h264", h264)
        sp.putBoolean("$deviceId.dynres", dynamicResolution)
        val g = gateway
        if (g == null || g.host.isBlank()) {
            sp.remove("$deviceId.ghost").remove("$deviceId.gport")
                .remove("$deviceId.guser").remove("$deviceId.gpass")
                .remove("$deviceId.gdomain")
        } else {
            sp.putString("$deviceId.ghost", g.host)
            sp.putInt("$deviceId.gport", g.port)
            sp.putString("$deviceId.guser", g.username.orEmpty())
            sp.putString("$deviceId.gpass", g.password.orEmpty())
            sp.putString("$deviceId.gdomain", g.domain.orEmpty())
        }
        sp.apply()
    }

    companion object {
        private const val FILE = "xydesk.rdp.options"

        /** Bersihkan semua opsi milik satu perangkat (dipakai saat device dihapus). */
        fun clear(context: Context, deviceId: String) {
            val editor = context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            listOf(
                "audio", "mic", "clipboard", "drive", "camera", "udp",
                "netauto", "h264", "dynres", "ghost", "gport", "guser", "gpass", "gdomain",
            ).forEach { editor.remove("$deviceId.$it") }
            editor.apply()
        }

        fun of(context: Context, deviceId: String): RdpOptions {
            val sp = context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val audio = XyAudioMode.entries.getOrElse(sp.getInt("$deviceId.audio", 0)) {
                XyAudioMode.DEVICE
            }
            val gatewayHost = sp.getString("$deviceId.ghost", "").orEmpty()
            val gateway = if (gatewayHost.isBlank()) {
                null
            } else {
                XyGateway(
                    host = gatewayHost,
                    port = sp.getInt("$deviceId.gport", 443),
                    username = sp.getString("$deviceId.guser", "")?.ifBlank { null },
                    password = sp.getString("$deviceId.gpass", "")?.ifBlank { null },
                    domain = sp.getString("$deviceId.gdomain", "")?.ifBlank { null },
                )
            }
            return RdpOptions(
                audioMode = audio,
                microphone = sp.getBoolean("$deviceId.mic", false),
                clipboard = sp.getBoolean("$deviceId.clipboard", true),
                localDrive = sp.getBoolean("$deviceId.drive", false),
                camera = sp.getBoolean("$deviceId.camera", false),
                udpTransport = sp.getBoolean("$deviceId.udp", true),
                networkAutoDetect = sp.getBoolean("$deviceId.netauto", true),
                h264 = sp.getBoolean("$deviceId.h264", true),
                dynamicResolution = sp.getBoolean("$deviceId.dynres", true),
                gateway = gateway,
            )
        }
    }
}
