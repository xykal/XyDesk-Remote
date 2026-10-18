package id.xydesk.remote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal fun decodeBoundedChatBitmap(bytes: ByteArray, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = chatImageSampleSize(bounds.outWidth, bounds.outHeight, maxEdge) ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
