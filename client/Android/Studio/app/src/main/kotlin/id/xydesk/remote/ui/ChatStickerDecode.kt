package id.xydesk.remote.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val stickerDecodeSlots = Semaphore(2)

@Composable
internal fun rememberChatSticker(context: Context, id: String, data: String): Pair<String, Bitmap>? = key(id, data) {
    produceState<Pair<String, Bitmap>?>(null, id, data) {
        value = withContext(Dispatchers.IO) {
            stickerDecodeSlots.withPermit { CustomStickerVault.decodeAndCacheFromChatPayload(context.applicationContext, data) }
        }
    }.value
}
