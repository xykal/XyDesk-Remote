package id.xydesk.remote.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Public, account-free community jokes API. Only a random install ID is sent for spam/reaction limits. */
internal class CommunityJokesApi(context: Context) {
    private val appContext = context.applicationContext
    private val clientId: String by lazy { loadOrCreateClientId() }

    suspend fun latest(): List<CommunityJoke> = withContext(Dispatchers.IO) {
        val body = request(
            method = "GET",
            path = "/api/jokes",
            headers = mapOf("X-XyDesk-Client-ID" to clientId),
        )
        val items = body.optJSONArray("items") ?: JSONArray()
        buildList(items.length()) {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.let { add(parseJoke(it)) }
            }
        }
    }

    suspend fun submit(text: String): CommunityJoke = withContext(Dispatchers.IO) {
        val body = request(
            method = "POST",
            path = "/api/jokes",
            payload = JSONObject().put("client_id", clientId).put("text", text),
        )
        parseJoke(body.optJSONObject("joke") ?: error("Missing submitted joke"))
    }

    suspend fun react(jokeId: String, emoji: String): CommunityJoke = withContext(Dispatchers.IO) {
        val body = request(
            method = "POST",
            path = "/api/jokes/$jokeId/reaction",
            payload = JSONObject().put("client_id", clientId).put("emoji", emoji),
        )
        parseJoke(body.optJSONObject("joke") ?: error("Missing reacted joke"))
    }

    suspend fun report(jokeId: String): Boolean = withContext(Dispatchers.IO) {
        val body = request(
            method = "POST",
            path = "/api/jokes/$jokeId/report",
            payload = JSONObject().put("client_id", clientId),
        )
        body.optBoolean("hidden", false)
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = try {
            (URL("$ENDPOINT$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        } catch (_: Exception) {
            throw CommunityJokesException("network")
        }

        return try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (statusCode !in 200..299) {
                throw CommunityJokesException(body.optString("status").ifBlank { "http_$statusCode" })
            }
            body
        } catch (error: CommunityJokesException) {
            throw error
        } catch (_: Exception) {
            throw CommunityJokesException("network")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseJoke(value: JSONObject): CommunityJoke {
        val rawReactions = value.optJSONObject("reactions") ?: JSONObject()
        val reactions = REACTIONS.associateWith { emoji -> rawReactions.optInt(emoji, 0).coerceAtLeast(0) }
        return CommunityJoke(
            id = value.optString("id"),
            text = value.optString("text"),
            createdAtMillis = value.optLong("created_at", 0L),
            reactions = reactions,
            viewerReaction = value.optString("viewer_reaction").takeIf { it in REACTIONS },
        )
    }

    private fun loadOrCreateClientId(): String {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_CLIENT_ID, null)?.let { existing ->
            if (runCatching { UUID.fromString(existing) }.isSuccess) return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT_ID, generated).apply()
        return generated
    }

    companion object {
        private const val ENDPOINT = "https://rdp.xydesk.my.id"
        private const val PREFS_NAME = "xydesk.community.jokes"
        private const val KEY_CLIENT_ID = "anonymous_client_id"
        val REACTIONS = listOf("😂", "😭", "💀", "🔥")
    }
}

internal data class CommunityJoke(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val reactions: Map<String, Int>,
    val viewerReaction: String?,
)

internal class CommunityJokesException(val reason: String) : Exception(reason)
