package id.xydesk.remote.ui

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** A canonical mutation invalidates every older queued cache snapshot for that account. */
internal class ChatCacheWriteOrder {
    private val epochs = ConcurrentHashMap<String, AtomicLong>()
    fun advance(account: String): Long = epochs.computeIfAbsent(account) { AtomicLong() }.incrementAndGet()
    fun isCurrent(account: String, epoch: Long): Boolean = epochs[account]?.get() == epoch
}
