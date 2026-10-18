package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class LeaderboardPodiumTest {
    private fun entry(rank: Int, login: String, seconds: Long = 100) =
        XyLeaderboardEntry(rank = rank, name = "same display name", githubLogin = login, totalSeconds = seconds)

    @Test fun onlyTopThreeEarnBadgeAndDisplayNamesDoNotMatter() {
        val ranks = podiumRanks(listOf(entry(1, "Alice"), entry(2, "@bob"), entry(3, "carol"), entry(4, "dave")))
        assertEquals(mapOf("alice" to 1, "bob" to 2, "carol" to 3), ranks)
    }
    @Test fun promotionDemotionAndRemovalFollowLatestSnapshot() {
        assertEquals(1, podiumRanks(listOf(entry(1, "alice")))["alice"])
        assertEquals(3, podiumRanks(listOf(entry(3, "alice")))["alice"])
        assertFalse(podiumRanks(listOf(entry(4, "alice"))).containsKey("alice"))
        assertTrue(podiumRanks(emptyList()).isEmpty())
    }
    @Test fun anonymousZeroTimeAndAmbiguousRanksCannotClaimIdentityBadge() {
        assertTrue(podiumRanks(listOf(entry(1, ""), entry(2, "bob", 0))).isEmpty())
        assertTrue(podiumRanks(listOf(entry(1, "alice"), entry(1, "bob"))).isEmpty())
        assertTrue(podiumRanks(listOf(entry(1, "alice"), entry(2, "ALICE"))).isEmpty())
    }
}
