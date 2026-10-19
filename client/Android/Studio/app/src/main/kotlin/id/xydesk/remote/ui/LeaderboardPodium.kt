package id.xydesk.remote.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cosmetic rank only: the existing leaderboard is not tamper-proof proof of playtime. */
internal fun podiumRanks(entries: List<XyLeaderboardEntry>): Map<String, Int> {
    val eligible = entries.filter { it.rank in 1..3 && it.totalSeconds > 0 }
    val uniqueRanks = eligible.groupBy { it.rank }.filterValues { it.size == 1 }.values.flatten()
    return uniqueRanks.filter { normalizedChatAccount(it.githubLogin).isNotEmpty() }
        .groupBy { normalizedChatAccount(it.githubLogin) }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single().rank }
}

/** Shared public snapshot: no network per bubble, no manual role grant, expires when stale. */
internal object LeaderboardPodium {
    private val mutableRanks = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ranks = mutableRanks.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var expiration: Job? = null
    private var revision = 0L

    @Synchronized
    fun publish(entries: List<XyLeaderboardEntry>) {
        mutableRanks.value = podiumRanks(entries)
        val current = ++revision
        expiration?.cancel()
        expiration = scope.launch {
            delay(10 * 60_000L)
            synchronized(this@LeaderboardPodium) {
                if (revision == current) mutableRanks.value = emptyMap()
            }
        }
    }
}
