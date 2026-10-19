package id.xydesk.remote.core

import org.junit.Assert.*
import org.junit.Test

class SessionMicrophoneControlTest {
    @Test fun disablingChangesNativeArgumentsWithoutMutatingProfile() {
        val policy = SessionMicrophoneControl()
        val saved = RdpOptions(microphone = true)
        policy.apply("a", saved)
        policy.plan("a", 7, false)
        val changed = policy.apply("a", saved).withCapturePermissions(true, true).forCaptureVisibility(true)
        assertTrue(saved.microphone)
        assertFalse(changed.microphone)
        assertFalse(RdpUri.queryParams(ConnectionProfile(host = "192.168.1.2"), changed).toMap().containsKey("microphone"))
    }
    @Test fun enablingCannotBypassPermissionVisibilityOrPcProfile() {
        val policy = SessionMicrophoneControl()
        policy.plan("a", 7, true)
        val desired = policy.apply("a", RdpOptions(microphone = false))
        assertTrue(desired.microphone)
        assertFalse(desired.withCapturePermissions(false, true).microphone)
        assertFalse(desired.forCaptureVisibility(false).microphone)
        assertFalse(desired.copy(pcConnectMode = true).withCapturePermissions(true, true).microphone)
    }
    @Test fun pendingRestartIsBoundToOldOwnerAndConsumedOnce() {
        val policy = SessionMicrophoneControl()
        val op = policy.plan("a", 7, false)
        assertNull(policy.take(8))
        assertTrue(policy.isPending())
        assertEquals(op, policy.take(7))
        assertFalse(policy.isPending())
        assertNull(policy.take(7))
        assertTrue(policy.isCurrent(op))
    }
    @Test fun cancelInvalidatesAlreadyPostedRestartAndPendingActivation() {
        val policy = SessionMicrophoneControl()
        val op = policy.plan("a", 7, true)
        policy.take(7)
        policy.cancel(suppressActivation = true)
        assertFalse(policy.isCurrent(op))
        assertFalse(policy.apply("a", RdpOptions()).microphone)
    }
    @Test fun replacementOperationInvalidatesOldRestart() {
        val policy = SessionMicrophoneControl()
        val first = policy.plan("a", 7, true)
        val second = policy.plan("a", 7, false)
        assertFalse(policy.isCurrent(first))
        assertTrue(policy.isCurrent(second))
        assertEquals(second, policy.take(7))
    }
    @Test fun profileSwitchDropsSessionOverrideAndInvalidatesRestart() {
        val policy = SessionMicrophoneControl()
        val op = policy.plan("a", 7, true)
        assertFalse(policy.apply("b", RdpOptions(microphone = false)).microphone)
        assertFalse(policy.isCurrent(op))
        assertFalse(policy.isPending())
        assertFalse(policy.apply("a", RdpOptions(microphone = false)).microphone)
    }
    @Test fun ordinaryNewConnectPreservesCommittedSessionPreference() {
        val policy = SessionMicrophoneControl()
        policy.plan("a", 7, true)
        policy.take(7)
        policy.cancel()
        assertTrue(policy.apply("a", RdpOptions(microphone = false)).microphone)
        policy.cancel(suppressActivation = true) // A committed foreground intent is not a stale prompt.
        assertTrue(policy.apply("a", RdpOptions(microphone = false)).microphone)
    }
    @Test fun newAcceptedAttemptInvalidatesOldPermissionGenerationEvenIfNativeHandleIsReused() {
        val policy = SessionMicrophoneControl()
        val oldPromptGeneration = policy.generation()
        policy.cancel() // Every newly accepted connection advances this token, independent of native address reuse.
        assertNotEquals(oldPromptGeneration, policy.generation())
    }
    @Test(expected = IllegalArgumentException::class) fun zeroInstanceCannotOwnRestart() {
        SessionMicrophoneControl().plan("a", 0, true)
    }
}
