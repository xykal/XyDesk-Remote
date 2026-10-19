package com.freerdp.freerdpcore.services;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class NativeWorkerTerminationTest
{
    @Test public void inactiveWorkerReleasesWithoutRequestingStop()
    {
        AtomicInteger releases = new AtomicInteger();
        NativeWorkerTermination.releaseAfterTerminal(new Object(), () -> false,
            () -> fail("No active worker"), releases::incrementAndGet);
        assertEquals(1, releases.get());
    }

    @Test(timeout = 5000) public void interruptionCannotReleaseAnActiveWorker() throws Exception
    {
        Object monitor = new Object();
        AtomicBoolean active = new AtomicBoolean(true), freed = new AtomicBoolean(), restored = new AtomicBoolean();
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                NativeWorkerTermination.releaseAfterTerminal(monitor, active::get, stopped::countDown,
                    () -> { assertFalse(active.get()); freed.set(true); });
                restored.set(Thread.currentThread().isInterrupted());
            } catch (Throwable error) { failure.set(error); }
        });
        thread.setDaemon(true); thread.start();
        try {
            assertTrue(stopped.await(2, TimeUnit.SECONDS));
            thread.interrupt();
            synchronized (monitor) { assertFalse(freed.get()); }
        } finally {
            synchronized (monitor) { active.set(false); monitor.notifyAll(); }
            thread.join(2000);
        }
        assertFalse(thread.isAlive()); assertNull(failure.get());
        assertTrue(freed.get()); assertTrue(restored.get());
    }

    @Test(timeout = 5000) public void notificationWithoutTerminalStateDoesNotPermitFree() throws Exception
    {
        Object monitor = new Object();
        AtomicBoolean active = new AtomicBoolean(true), freed = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch stopped = new CountDownLatch(1), rechecked = new CountDownLatch(1);
        Thread thread = new Thread(() -> NativeWorkerTermination.releaseAfterTerminal(monitor,
            () -> { if (reads.incrementAndGet() >= 3) rechecked.countDown(); return active.get(); },
            stopped::countDown, () -> freed.set(true)));
        thread.setDaemon(true); thread.start();
        try {
            assertTrue(stopped.await(2, TimeUnit.SECONDS));
            synchronized (monitor) { monitor.notifyAll(); }
            assertTrue(rechecked.await(2, TimeUnit.SECONDS));
            assertFalse(freed.get());
        } finally {
            synchronized (monitor) { active.set(false); monitor.notifyAll(); }
            thread.join(2000);
        }
        assertFalse(thread.isAlive()); assertTrue(freed.get());
    }

    @Test public void failedStopRequestCannotFreeNativeMemory()
    {
        AtomicBoolean freed = new AtomicBoolean();
        RuntimeException error = new RuntimeException("Stop failed");
        try {
            NativeWorkerTermination.releaseAfterTerminal(new Object(), () -> true,
                () -> { throw error; }, () -> freed.set(true));
            fail("Expected failure");
        } catch (RuntimeException actual) { assertSame(error, actual); }
        assertFalse(freed.get());
    }

    @Test public void initiallyInterruptedInactiveThreadKeepsInterruptStatus() throws Exception
    {
        AtomicBoolean restored = new AtomicBoolean(), freed = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            Thread.currentThread().interrupt();
            NativeWorkerTermination.releaseAfterTerminal(new Object(), () -> false,
                () -> fail("Not active"), () -> freed.set(true));
            restored.set(Thread.currentThread().isInterrupted());
        });
        thread.setDaemon(true); thread.start(); thread.join(2000);
        assertFalse(thread.isAlive()); assertTrue(freed.get()); assertTrue(restored.get());
    }
}
