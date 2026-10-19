/*
 * XyDesk Remote — native session lifecycle helpers.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.freerdp.freerdpcore.services;

import java.util.function.BooleanSupplier;

/** Native memory must outlive its worker, even if the releasing Java thread is interrupted. */
final class NativeWorkerTermination
{
    private NativeWorkerTermination() {}

    static void releaseAfterTerminal(Object monitor, BooleanSupplier active,
                                     Runnable requestStop, Runnable release)
    {
        boolean interrupted = false;
        try
        {
            synchronized (monitor)
            {
                if (active.getAsBoolean())
                    requestStop.run();
                while (active.getAsBoolean())
                {
                    try
                    {
                        monitor.wait();
                    }
                    catch (InterruptedException e)
                    {
                        // Interruption is NOT a native terminal acknowledgement.
                        interrupted = true;
                    }
                }
            }
            release.run();
        }
        finally
        {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
