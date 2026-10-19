package id.xydesk.remote.ui

/** An explicit empty server snapshot must clear old cached confirmed messages.
 * Preserve local sends and recent arrivals while an HTTP snapshot was in flight.
 * Missing/invalid `messages` is NOT an empty snapshot and must not invoke this rule.
 */
internal fun keepAfterEmptyChatSnapshot(id: String, at: Long, snapshotStartedAt: Long): Boolean =
    id.startsWith("local-") || at >= snapshotStartedAt - 4_000L
