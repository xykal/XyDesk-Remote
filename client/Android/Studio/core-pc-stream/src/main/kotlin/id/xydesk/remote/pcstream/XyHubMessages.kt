package id.xydesk.remote.pcstream

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Protokol teks hub signaling **host XyDesk produksi** (`signal.xydesk.my.id`,
 * repo saudara `xykal/XyDesk`: `cloudflare/src/worker.js` + `hub.js`, klien
 * referensi `android-native/.../net/Signaling.kt`, `docs/PROTOCOL.md`).
 *
 * XyDesk-Remote berperan sebagai **client**. Dua hal mengikat desain ini:
 *
 *  - Server selalu menimpa `from` dengan ID pengirim yang terautentikasi,
 *    jadi identitas peer di pesan masuk bisa dipercaya tanpa verifikasi.
 *  - Arah relay dibatasi per tipe (`relayAllowed` di hub.js): client hanya
 *    boleh mengirim `pair`/`offer`, host hanya menjawab `pair-response`/
 *    `answer`; `ice`/`bye`/`error` sah dua arah.
 *
 * Lapisan ini murni (org.json saja, tanpa jaringan/coroutine) supaya format
 * kawatnya dibekukan lewat unit test JVM — bukan lewat "kelihatannya sama".
 * Transport WebSocket diabstraksi di [XyHubSignalClient].
 */
sealed interface XyHubOutbound {

    /** Ubah pesan menjadi JSON teks siap kirim. */
    fun encode(): String

    /** Registrasi setelah WebSocket terbuka; [deviceId] wajib sama dengan query `/ws`. */
    data class Hello(val deviceId: String) : XyHubOutbound {
        override fun encode(): String = JSONObject()
            .put("type", "hello")
            .put("to", deviceId)
            .put("reason", "client")
            .toString()
    }

    /**
     * Minta pairing ke host. PIN diverifikasi oleh host (bukan hub), dan rem
     * anti-penebak (`pair-terkunci`) sudah diproses hub sebelum pesan diteruskan.
     */
    data class Pair(val hostId: String, val pin: String, val name: String?) : XyHubOutbound {
        override fun encode(): String = JSONObject()
            .put("type", "pair")
            .put("to", hostId)
            .put("pin", pin)
            .apply { if (!name.isNullOrBlank()) put("name", name) }
            .put("platform", "android")
            .toString()
    }

    /** SDP offer — client selalu peng-initiator media. [sdpType] normalnya `"offer"`. */
    data class Offer(val hostId: String, val sdpType: String, val sdp: String) : XyHubOutbound {
        override fun encode(): String = JSONObject()
            .put("type", "offer")
            .put("to", hostId)
            .put("sdp", JSONObject().put("type", sdpType).put("sdp", sdp))
            .toString()
    }

    /**
     * Kandidat ICE trickle. [sdpMid]/[sdpMLineIndex] opsional mengikuti nilai
     * yang diberikan WebRTC; `candidate` kosong = akhir kandidat.
     */
    data class Ice(
        val hostId: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
    ) : XyHubOutbound {
        override fun encode(): String {
            val payload = JSONObject().put("candidate", candidate)
            if (!sdpMid.isNullOrBlank()) payload.put("sdpMid", sdpMid)
            if (sdpMLineIndex >= 0) payload.put("sdpMLineIndex", sdpMLineIndex)
            return JSONObject()
                .put("type", "ice")
                .put("to", hostId)
                .put("candidate", payload)
                .toString()
        }
    }

    /** Akhiri sesi media dengan host; host menganggap klien pergi dan boleh melayani klien lain. */
    data class Bye(val hostId: String) : XyHubOutbound {
        override fun encode(): String = JSONObject()
            .put("type", "bye")
            .put("to", hostId)
            .toString()
    }

    /** Minta daftar perangkat; hub produksi selalu menjawab `devices` kosong (privasi). */
    data object ListDevices : XyHubOutbound {
        override fun encode(): String = JSONObject().put("type", "list").toString()
    }

    /** Tanya status online maksimal [XyHubProtocol.PRESENCE_LIMIT] ID host (jawaban ya/tidak per ID). */
    data class Presence(val ids: List<String>) : XyHubOutbound {
        override fun encode(): String = JSONObject()
            .put("type", "presence")
            .put("ids", JSONArray(ids))
            .toString()
    }

    /** Keep-alive level aplikasi; hub membalas [XyHubInbound.Pong]. */
    data object Ping : XyHubOutbound {
        override fun encode(): String = JSONObject().put("type", "ping").toString()
    }

    /** Balasan bila hub (atau peer) mengirim ping level aplikasi. */
    data object Pong : XyHubOutbound {
        override fun encode(): String = JSONObject().put("type", "pong").toString()
    }
}

/** Pesan masuk dari hub yang relevan bagi client. Tipe lain → [XyHubInbound.Unknown]. */
sealed interface XyHubInbound {

    /** Registrasi diterima; `from` = ID sendiri. */
    data class Welcome(val from: String) : XyHubInbound

    /** Jawaban pairing dari host. [reason] bebas teks dari host saat [accepted] `false`. */
    data class PairResponse(val from: String, val accepted: Boolean, val reason: String?) : XyHubInbound

    /** SDP answer dari host. [sdpType] default `"answer"` bila host tidak mengisinya. */
    data class Answer(val from: String, val sdpType: String, val sdp: String) : XyHubInbound

    /** Kandidat ICE dari host; `candidate` kosong = akhir kandidat. */
    data class IceCandidate(
        val from: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
    ) : XyHubInbound

    /** Host menutup sesi (atau media lepas). */
    data class Bye(val from: String) : XyHubInbound

    /** Kabar gagal: dari hub (`peer-offline`, `pair-terkunci`, …) atau relay error host. */
    data class HubError(val error: String, val reason: String?, val retryInSeconds: Long?) : XyHubInbound

    /** Jawaban [XyHubOutbound.Presence]: ID host yang online dari yang ditanyakan. */
    data class PresenceOnline(val online: List<String>) : XyHubInbound

    /** Jawaban [XyHubOutbound.ListDevices]; produksi selalu kosong. */
    data class Devices(val devices: List<String>) : XyHubInbound

    /** Ping level aplikasi — [XyHubSignalClient] membalas otomatis dengan pong. */
    data object Ping : XyHubInbound

    /** Balasan keep-alive. */
    data object Pong : XyHubInbound

    /** Tipe dikenali protokol tapi belum dipakai client — diabaikan, bukan error. */
    data class Unknown(val type: String) : XyHubInbound
}

/** Aturan ID perangkat hub (worker.js): client bebas `[A-Za-z0-9_-]{3,64}`, host selalu 9 digit. */
object XyHubIds {

    private val clientIdPattern = Regex("^[A-Za-z0-9_-]{3,64}$")
    private val hostIdPattern = Regex("^[0-9]{9}$")
    private val idRandom = SecureRandom()

    /** Buang spasi dan tanda hubung — klien referensi memakai input yang sama longgar. */
    fun normalize(value: String): String = value.replace(Regex("[\\s-]"), "")

    /** Validasi + kembalikan ID client, atau lempar [IllegalArgumentException]. */
    fun requireClientId(value: String): String {
        require(clientIdPattern.matches(value)) { "ID client hub tidak valid" }
        return value
    }

    /** Validasi + kembalikan ID host (9 digit, seperti klaim `/host-token`). */
    fun requireHostId(value: String): String {
        require(hostIdPattern.matches(value)) { "ID host hub harus 9 digit" }
        return value
    }

    /** ID client per-install: prefiks `xyr-` (XyDesk-Remote) + 12 heksadesimal. */
    fun generateClientId(): String {
        val bytes = ByteArray(6)
        idRandom.nextBytes(bytes)
        return "xyr-" + bytes.joinToString("") { "%02x".format(it) }
    }
}

/** Parser pesan masuk + batas protokol. */
object XyHubProtocol {

    /** Batas `presence` di hub: maksimal ID yang dijawab per permintaan. */
    const val PRESENCE_LIMIT = 50

    /**
     * Parse satu frame teks dari hub.
     *
     * @return `null` bila bukan JSON objek atau tanpa `type` (kerusakan kawat);
     *         [XyHubInbound.Unknown] bila tipe sah tapi tak dipakai client.
     */
    fun parseInbound(text: String): XyHubInbound? {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return null
        }
        val type = root.optString("type")
        if (type.isBlank() || type == "null") return null
        // Field wajib yang hilang = kawat rusak (bukan crash di thread transport).
        return try {
            parseTyped(root, type)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parseTyped(root: JSONObject, type: String): XyHubInbound = when (type) {
        "welcome" -> XyHubInbound.Welcome(from = root.requiredString("from"))
        "pair-response" -> XyHubInbound.PairResponse(
            from = root.requiredString("from"),
            accepted = root.optBoolean("accepted", false),
            reason = root.optionalString("reason"),
        )
        "answer" -> {
            val sdp = root.optJSONObject("sdp")
                ?: throw IllegalArgumentException("answer tanpa payload sdp")
            XyHubInbound.Answer(
                from = root.requiredString("from"),
                sdpType = sdp.optString("type").takeIf { it.isNotBlank() } ?: "answer",
                sdp = sdp.requiredString("sdp"),
            )
        }
        "ice" -> {
            val candidate = root.optJSONObject("candidate")
                ?: throw IllegalArgumentException("ice tanpa payload candidate")
            XyHubInbound.IceCandidate(
                from = root.requiredString("from"),
                candidate = candidate.optString("candidate"),
                sdpMid = candidate.optionalString("sdpMid"),
                sdpMLineIndex = candidate.optInt("sdpMLineIndex", -1).takeIf { it >= 0 },
            )
        }
        "bye" -> XyHubInbound.Bye(from = root.requiredString("from"))
        "error" -> XyHubInbound.HubError(
            error = root.requiredString("error"),
            reason = root.optionalString("reason"),
            retryInSeconds = root.optLong("retry_in", -1).takeIf { it >= 0 },
        )
        "presence" -> XyHubInbound.PresenceOnline(
            online = root.optJSONArray("online").toStringList(),
        )
        "devices" -> XyHubInbound.Devices(
            devices = root.optJSONArray("devices").toStringList(),
        )
        "ping" -> XyHubInbound.Ping
        "pong" -> XyHubInbound.Pong
        else -> XyHubInbound.Unknown(type)
    }

    private fun JSONObject.requiredString(key: String): String =
        optString(key).takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalArgumentException("Field hub wajib hilang: $key")

    private fun JSONObject.optionalString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() && it != "null" }?.let { add(it) }
            }
        }
    }
}
