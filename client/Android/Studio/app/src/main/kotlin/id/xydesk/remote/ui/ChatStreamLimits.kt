package id.xydesk.remote.ui

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Enforce while reading, not just Content-Length (which may be missing or false). */
internal fun InputStream.readChatBytesAtMost(limit: Int): ByteArray {
    require(limit > 0)
    val out = ByteArrayOutputStream(minOf(limit, 8_192))
    val buffer = ByteArray(8_192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) {
            val single = read()
            if (single < 0) break
            if (out.size() >= limit) throw IOException("Chat image exceeds byte limit")
            out.write(single)
        } else {
            if (count > limit - out.size()) throw IOException("Chat image exceeds byte limit")
            out.write(buffer, 0, count)
        }
    }
    return out.toByteArray()
}
