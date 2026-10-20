package id.xydesk.remote.ui

import java.io.InputStream

/** Bounded SAF input; never spin on a broken provider, always wipe scratch data. */
internal object ChatBackupInput {
    fun read(input: InputStream): ByteArray {
        val buffer = ByteArray(ChatBackupCrypto.MAX_FILE + 1)
        try {
            var count = 0
            while (count < buffer.size) {
                val n = input.read(buffer, count, buffer.size - count)
                if (n < 0) break
                check(n > 0) { "Penyedia file tidak menghasilkan data." }
                count += n
            }
            require(count <= ChatBackupCrypto.MAX_FILE) { "File backup terlalu besar." }
            return buffer.copyOf(count)
        } finally { buffer.fill(0) }
    }
}
