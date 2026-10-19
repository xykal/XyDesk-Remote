package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class HudKeyComboTest {
    @Test fun comboIsReleasedInReverseAndDuplicatesDoNotStick() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        assertTrue(dispatchHudKeyCombo(listOf(1, 2, 1, 3)) { key, down -> events.add(key to down) })
        assertEquals(listOf(1 to true, 2 to true, 3 to true, 3 to false, 2 to false, 1 to false), events)
    }
    @Test fun partialFailureStillAttemptsEveryRelevantRelease() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        assertFalse(dispatchHudKeyCombo(listOf(1, 2, 3)) { key, down ->
            events.add(key to down)
            if (key == 2 && down) throw IllegalStateException("dispatch failed")
        })
        assertEquals(listOf(1 to true, 2 to true, 2 to false, 1 to false), events)
    }
    @Test fun oneReleaseFailureDoesNotSkipOtherKeys() {
        val released = mutableListOf<Int>()
        assertFalse(dispatchHudKeyCombo(listOf(1, 2)) { key, down ->
            if (!down) { released.add(key); if (key == 2) throw IllegalStateException() }
        })
        assertEquals(listOf(2, 1), released)
    }
}
