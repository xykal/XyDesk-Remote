/*
 * XyDesk Remote — native session lifecycle helpers.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.freerdp.freerdpcore.application;

import java.util.Map;
import java.util.function.LongConsumer;

/** Registry removal commits only after native release returns successfully. */
final class SessionReleaseTransaction
{
    private SessionReleaseTransaction() {}

    /** sessions must be a synchronized map; never hold its global monitor during native teardown. */
    static <T> void release(Map<Long, T> sessions, long instance, LongConsumer releaseNative)
    {
        final T owner = sessions.get(instance);
        if (owner == null)
            return;
        // Serialize releases of this owner, without blocking callbacks for other sessions.
        synchronized (owner)
        {
            if (sessions.get(instance) != owner)
                return;
            releaseNative.accept(instance);
            synchronized (sessions)
            {
                // A replacement owner must not be removed by an old release.
                if (sessions.get(instance) == owner)
                    sessions.remove(instance);
            }
        }
    }
}
