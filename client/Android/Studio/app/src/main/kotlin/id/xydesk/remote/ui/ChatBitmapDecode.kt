package id.xydesk.remote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withPermit

internal fun decodeBoundedChatBitmap(bytes: ByteArray, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = chatImageSampleSize(bounds.outWidth, bounds.outHeight, maxEdge) ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** Decode off-main and limit temporary bitmap allocations across visible messages. */
@androidx.compose.runtime.Composable
internal fun rememberChatBitmap(messageId: String, data: String): Bitmap? =
    androidx.compose.runtime.key(messageId, data) {
        val state = androidx.compose.runtime.produceState<Bitmap?>(null, messageId, data) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                chatBitmapSlots.withPermit {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    decodeBase64Bitmap(data)
                }
            }
        }
        state.value
    }

private val chatBitmapSlots = kotlinx.coroutines.sync.Semaphore(2)
