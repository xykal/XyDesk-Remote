package com.freerdp.freerdpcore.application;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SessionReleaseTransactionTest
{
    private Map<Long, Object> registry() { return Collections.synchronizedMap(new HashMap<>()); }

    @Test public void ownershipExistsUntilNativeReleaseCompletes()
    {
        Map<Long, Object> sessions = registry(); Object owner = new Object(); sessions.put(7L, owner);
        SessionReleaseTransaction.release(sessions, 7L, instance -> {
            assertEquals(7L, instance); assertSame(owner, sessions.get(instance));
        });
        assertFalse(sessions.containsKey(7L));
    }

    @Test public void failureDoesNotDropNativeOwnership()
    {
        Map<Long, Object> sessions = registry(); Object owner = new Object(); sessions.put(7L, owner);
        RuntimeException error = new RuntimeException("Free failed");
        try {
            SessionReleaseTransaction.release(sessions, 7L, instance -> { throw error; });
            fail("Expected failure");
        } catch (RuntimeException actual) { assertSame(error, actual); }
        assertSame(owner, sessions.get(7L));
    }

    @Test public void absentAndRepeatedReleaseCannotDoubleFree()
    {
        Map<Long, Object> sessions = registry(); AtomicInteger frees = new AtomicInteger();
        SessionReleaseTransaction.release(sessions, 7L, instance -> frees.incrementAndGet());
        assertEquals(0, frees.get()); sessions.put(7L, new Object());
        SessionReleaseTransaction.release(sessions, 7L, instance -> frees.incrementAndGet());
        SessionReleaseTransaction.release(sessions, 7L, instance -> frees.incrementAndGet());
        assertEquals(1, frees.get());
    }

    @Test public void replacementOwnerSurvivesOldRelease()
    {
        Map<Long, Object> sessions = registry(); Object replacement = new Object(); sessions.put(7L, new Object());
        SessionReleaseTransaction.release(sessions, 7L, instance -> sessions.put(instance, replacement));
        assertSame(replacement, sessions.get(7L));
    }

    @Test(timeout = 5000) public void simultaneousReleaseFreesExactlyOnceAndDoesNotBlockRegistryCallbacks() throws Exception
    {
        Map<Long, Object> sessions = registry(); Object owner = new Object(); sessions.put(7L, owner);
        AtomicInteger frees = new AtomicInteger(); AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1), allowFree = new CountDownLatch(1), registryRead = new CountDownLatch(1);
        Runnable release = () -> {
            try {
                SessionReleaseTransaction.release(sessions, 7L, instance -> {
                    frees.incrementAndGet(); entered.countDown();
                    try { assertTrue(allowFree.await(2, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                });
            } catch (Throwable error) { failure.set(error); }
        };
        Thread first = new Thread(release), second = new Thread(release);
        first.setDaemon(true); second.setDaemon(true); first.start();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS)); second.start();
            Thread callback = new Thread(() -> { sessions.get(7L); registryRead.countDown(); });
            callback.setDaemon(true); callback.start();
            assertTrue("Registry must remain readable during teardown", registryRead.await(1, TimeUnit.SECONDS));
        } finally {
            allowFree.countDown(); first.join(2000); second.join(2000);
        }
        assertFalse(first.isAlive()); assertFalse(second.isAlive()); assertNull(failure.get());
        assertEquals(1, frees.get()); assertFalse(sessions.containsKey(7L));
    }
}
