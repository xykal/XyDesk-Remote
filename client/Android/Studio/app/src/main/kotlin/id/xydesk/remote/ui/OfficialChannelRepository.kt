package id.xydesk.remote.ui

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class OfficialChannelPost(val id: String, val text: String, val login: String, val role: String, val at: Long)
internal data class OfficialChannelPage(val posts: List<OfficialChannelPost>, val canPost: Boolean, val cursor: String?)
internal class ChannelRequestException(val status: Int, val retrySeconds: Long) : Exception(when (status) {
    401 -> "Login GitHub perlu diperbarui."
    403 -> "Kirim pesan hanya untuk admin, founder, dan tim yang diberi akses."
    409 -> "Konfirmasi kirim berbeda. Muat ulang channel sebelum mencoba kembali."
    429 -> "Terlalu banyak permintaan. Tunggu sebelum mencoba lagi."
    503 -> "Channel sementara belum tersedia. Pesan/draf tidak dihapus."
    else -> "Channel belum tersinkron (HTTP $status)."
})
internal object OfficialChannelRepository {
    private const val BASE = "https://api.xydeskremote.biz.id/api/chat/channels/"
    private fun post(row: JSONObject) = OfficialChannelPost(row.getString("id"), row.optString("text"), row.optString("github_login"), row.optString("author_role"), row.optLong("at"))
    fun load(context: Context, channelId: String, cursor: String?): OfficialChannelPage {
        val json = request(context, channelId, cursor, null)
        val arr = json.getJSONArray("posts")
        return OfficialChannelPage((0 until arr.length()).map { post(arr.getJSONObject(it)) }, json.optBoolean("can_post"), json.optString("next_cursor").takeIf { !json.isNull("next_cursor") && it.isNotBlank() })
    }
    fun publish(context: Context, channelId: String, text: String, nonce: String, expectedLogin: String): OfficialChannelPost =
        post(request(context, channelId, null, JSONObject().put("text", text).put("client_post_id", nonce).put("expected_login", expectedLogin)).getJSONObject("post"))
    private fun request(context: Context, channelId: String, cursor: String?, body: JSONObject?): JSONObject {
        require(officialChannels.any { it.id == channelId })
        val suffix = cursor?.let { "?cursor=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val connection = URL(BASE + channelId + "/posts" + suffix).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            GlobalChatRepository.token(context)?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw ChannelRequestException(status, (connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 0L).coerceIn(0L, 300L))
            return JSONObject(String(connection.inputStream.use { it.readChatBytesAtMost(1_048_576) }, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
