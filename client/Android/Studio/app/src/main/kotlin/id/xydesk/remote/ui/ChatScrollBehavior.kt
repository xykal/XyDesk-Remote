package id.xydesk.remote.ui

import androidx.compose.foundation.lazy.LazyListState

internal fun shouldAnimateChatScroll(distance: Int, motionEnabled: Boolean): Boolean = motionEnabled && distance in -12..12

/** Far jumps do not animate/decode every intervening media row. */
internal suspend fun LazyListState.scrollChatTo(index: Int, motionEnabled: Boolean) {
    val target = index.coerceAtLeast(0)
    if (shouldAnimateChatScroll(target - firstVisibleItemIndex, motionEnabled)) animateScrollToItem(target)
    else scrollToItem(target)
}
