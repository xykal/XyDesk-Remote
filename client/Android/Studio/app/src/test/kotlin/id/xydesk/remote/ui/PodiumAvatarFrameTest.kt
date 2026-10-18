package id.xydesk.remote.ui

import androidx.compose.ui.Modifier
import org.junit.Assert.assertSame
import org.junit.Test

class PodiumAvatarFrameTest {
    @Test fun missingOrOutsidePodiumRankLeavesModifierUntouched() {
        listOf(null, -1, 0, 4, Int.MAX_VALUE).forEach { rank ->
            assertSame(Modifier, Modifier.podiumFrame(rank))
        }
    }
}
