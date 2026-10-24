package id.xydesk.remote.ui

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.util.Locale

/**
 * Database lokal SQLite: sumber riwayat permanen Chat Global & Masukan.
 *
 * Keputusan produk: server (Firestore / Durable Object) hanya RELAY —
 * pesan ditahan sebentar untuk sinkronisasi antarperangkat lalu dibersihkan
 * berkala di server. Riwayat jangka panjang hidup di tabel ini, per akun
 * GitHub (scope). Cadangan terenkripsi 1 MB (GlobalChatOfflineCache) tetap
 * ada sebagai snapshot cepat; tabel ini yang menyimpan riwayat penuh.
 */
internal object ChatLocalArchive {
    private const val DB_NAME = "xydesk_chat_local.db"
    private const val DB_VERSION = 1
    private const val MAX_MESSAGES_PER_SCOPE = 50_000

    private class Helper(context: Context) :
        SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE chat_messages (" +
                    "scope TEXT NOT NULL, id TEXT NOT NULL, at INTEGER NOT NULL, " +
                    "revision INTEGER NOT NULL, mine INTEGER NOT NULL, body TEXT NOT NULL, " +
                    "PRIMARY KEY (scope, id))",
            )
            db.execSQL("CREATE INDEX idx_chat_messages_scope_at ON chat_messages(scope, at)")
            db.execSQL(
                "CREATE TABLE feedback_local (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, " +
                    "category TEXT NOT NULL, message TEXT NOT NULL, destination TEXT NOT NULL)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    @Volatile private var helper: Helper? = null

    private fun db(context: Context): SQLiteDatabase {
        val h = helper ?: synchronized(this) {
            helper ?: Helper(context).also { helper = it }
        }
        return h.writableDatabase
    }

    private fun scopeOf(login: String): String =
        login.trim().removePrefix("@").lowercase(Locale.ROOT)

    private fun encodeRow(msg: ChatMessage): String = JSONObject().apply {
        put("id", msg.id); put("client_msg_id", msg.clientMessageId); put("name", msg.name)
        put("github_login", msg.githubLogin); put("author_id", msg.authorId)
        put("revision", msg.revision); put("edited_at", msg.editedAt)
        put("deleted", msg.deleted); put("deleted_at", msg.deletedAt)
        put("text", msg.text.take(4_000)); put("at", msg.at); put("country", msg.country)
        put("translated", msg.translated.take(4_000)); put("target_lang", msg.targetLang)
        put("source_lang", msg.sourceLang); put("is_founder", msg.isFounder)
        put("is_announcement", msg.isAnnouncement); put("reply_to_id", msg.replyToId)
        put("reply_to_name", msg.replyToName); put("reply_to_text", msg.replyToText.take(500))
        put("reply_depth", msg.replyDepth)
        put("attachment_type", msg.attachmentType); put("attachment_name", msg.attachmentName.take(256))
        put("attachment_mime", msg.attachmentMime); put("attachment_size", msg.attachmentSize)
        put("avatar_url", msg.avatarUrl)
    }.toString()

    private fun decodeRow(body: String, mine: Boolean): ChatMessage? = runCatching {
        val row = JSONObject(body)
        val msg = ChatMessage(
            id = row.optString("id"), name = row.optString("name").take(100),
            githubLogin = row.optString("github_login").take(100),
            country = row.optString("country").take(8), flag = "",
            text = row.optString("text").take(4_000),
            translated = row.optString("translated").take(4_000),
            targetLang = row.optString("target_lang").take(32),
            sourceLang = row.optString("source_lang").take(32),
            at = row.optLong("at", 0), mine = mine,
            isFounder = row.optBoolean("is_founder"),
            isAnnouncement = row.optBoolean("is_announcement"),
            replyToId = row.optString("reply_to_id"), replyToName = row.optString("reply_to_name"),
            replyToText = row.optString("reply_to_text").take(500),
            replyDepth = row.optInt("reply_depth", 0),
            attachmentType = row.optString("attachment_type"),
            attachmentName = row.optString("attachment_name").take(256),
            attachmentMime = row.optString("attachment_mime"),
            attachmentSize = row.optInt("attachment_size", 0),
            avatarUrl = row.optString("avatar_url"),
            authorId = row.optLong("author_id", 0),
            revision = row.optLong("revision", 0),
            editedAt = row.optLong("edited_at", 0),
            deleted = row.optBoolean("deleted"), deletedAt = row.optLong("deleted_at", 0),
        )
        if (msg.deleted) msg.deletedPlaceholder() else msg
    }.getOrNull()

    /** Simpan/segarkan pesan terkonfirmasi. Blocking: panggil dari IO. */
    fun upsertAll(context: Context, login: String, messages: List<ChatMessage>) {
        val scope = scopeOf(login)
        if (scope.isBlank()) return
        val rows = messages.filter {
            !it.viewOnce && !it.mutationPending && !it.id.startsWith("local-") && it.id.isNotBlank()
        }
        if (rows.isEmpty()) return
        val database = db(context)
        database.beginTransaction()
        try {
            for (msg in rows) {
                val values = ContentValues().apply {
                    put("scope", scope); put("id", msg.id); put("at", msg.at)
                    put("revision", msg.revision); put("mine", if (msg.mine) 1 else 0)
                    put("body", encodeRow(msg))
                }
                database.insertWithOnConflict(
                    "chat_messages", null, values, SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            // Jaga ukuran: simpan 50 ribu pesan terbaru per akun.
            val cursor = database.rawQuery(
                "SELECT COUNT(*) FROM chat_messages WHERE scope = ?", arrayOf(scope),
            )
            val count = cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
            if (count > MAX_MESSAGES_PER_SCOPE) {
                val excess = count - MAX_MESSAGES_PER_SCOPE
                database.execSQL(
                    "DELETE FROM chat_messages WHERE scope = ? AND id IN " +
                        "(SELECT id FROM chat_messages WHERE scope = ? ORDER BY at ASC LIMIT ?)",
                    arrayOf(scope, scope, excess.toString()),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** Riwayat lokal terbaru (urutan lama→baru). Blocking: panggil dari IO. */
    fun readRecent(context: Context, login: String, limit: Int = 2_000): List<ChatMessage> {
        val scope = scopeOf(login)
        if (scope.isBlank()) return emptyList()
        val out = ArrayList<ChatMessage>()
        db(context).rawQuery(
            "SELECT body, mine FROM chat_messages WHERE scope = ? ORDER BY at DESC LIMIT ?",
            arrayOf(scope, limit.coerceIn(1, 10_000).toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                decodeRow(cursor.getString(0), cursor.getInt(1) == 1)?.let { out.add(it) }
            }
        }
        out.reverse()
        return out
    }

    fun clearAccount(context: Context, login: String) {
        val scope = scopeOf(login)
        if (scope.isBlank()) return
        db(context).delete("chat_messages", "scope = ?", arrayOf(scope))
    }

    /** Salinan lokal tiap masukan yang dibagikan/disalin pengguna. Blocking: IO. */
    fun storeFeedback(context: Context, category: String, message: String, destination: String) {
        if (message.isBlank()) return
        val values = ContentValues().apply {
            put("at", System.currentTimeMillis())
            put("category", category.take(64))
            put("message", message.take(8_000))
            put("destination", destination.take(32))
        }
        db(context).insert("feedback_local", null, values)
    }
}
