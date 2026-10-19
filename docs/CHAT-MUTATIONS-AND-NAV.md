# Chat edits, deletion placeholders, direct navigation and notifications

## Current implementation (candidate/device and deployment verification still required)

- A direct Chat Global bottom-navigation destination with its own unread indicator.
  Feed retains its separate social-notification indicator. Chat itself remains a
  full-screen conversation so its composer is not covered by the floating dock.
- Text-only editing from the long-press menu, optimistic text update, edited label,
  visible syncing status and explicit retry after a failed/unconfirmed mutation.
  A failure restores the previous content only if that operation still owns the
  local row; a newer authoritative revision wins over a late failure.
- Delete-for-everyone replaces the row with "Pesan ini dihapus" rather than hiding
  its ID. Confirmed deletion clears text, media, old envelope/signature, quote
  preview, pin and affected unread notification. Delete-for-me remains local hiding.
- Server author ownership comes from GitHub's verified stable subject ID; body
  nickname/login/client ID and old shared-key message signatures are not authority.
  Legacy rows lacking a verified author ID cannot be safely claimed for mutation.
- Authenticated HTTP edit/delete operations use expected revisions, transactions
  and payload-bound operation IDs. Old unauthenticated WS delete is rejected.
  Conflicts/expired auth/provider failures are not reported as successful edits.
- History/cache/WS merge rules retain newer revisions, protect in-flight local UI,
  and prevent stale snapshots from rehydrating deleted attachments or quote text.
  Translation responses for superseded text/revision are ignored. Unacknowledged
  optimistic mutations are not saved as confirmed offline history. Canonical mutation
  ACKs trigger immediate asynchronous cache persistence instead of the normal debounce;
  per-account write epochs reject obsolete queued snapshots. Cached same-ID revisions
  are reconciled for multiple chat windows. This is not a guarantee against OS/storage failure.
- Chat messages/mentions/replies/announcements have separate conversation labels
  and notification groups. Community mentions/replies get independent channels;
  feed/social and app updates remain separate. Existing channel IDs/settings are
  preserved where possible; the read-all action explicitly says all chat. In-app
  community filters also separate replies/mentions and likes/reactions, show counts
  and category chips; banner labels include the source (chat/community/app).

## Responsiveness and limitations

Optimistic UI does not wait for network/provider/translation. **Zero-ms network
latency is not possible or claimed.** No on-device frame-time benchmark is available.
The failed edit/delete retry state is session-local, not a durable offline mutation
outbox. Media/caption editing and portable chat backups remain outside this batch.
Old recipients, downloads, screenshots, exported media or device backups cannot
be remotely erased by a placeholder. Existing insecure message signatures are not
relabeled as E2E encryption or as proof of sender identity.

## Rollout and draft checks

Worker source and Android client must both be updated for usable owner-bound
mutations. **The Worker is not deployed by this work.** An older server returns
legacy/unowned messages, which do not receive unsafe edit/delete controls. Do not
claim that edits now work against production until authenticated integration tests
and an explicitly authorized backend rollout are complete.

Release sequence: source gates → signed candidate build → signature/ABI/binary checks
→ release draft for the owner's device testing. Device tests require a candidate;
they must not become a circular prerequisite for building that candidate.

Source gates: Kotlin/JVM, Worker routes, revision/replay/ownership tests and app compile.
Binary gates: signed release binaries, signature continuity and existing ABI validation.
Draft/device test matrix (not yet performed):
- Two-account/two-device edit, delete, stale history and reconnect behavior.
- Offline/provider rejection/401/403/409/503 and delayed-success/failure ordering.
- Exact tombstone display after cold start and quoted/pinned/media cleanup.
- Long-press access in full-screen and floating chat; small-screen dock entry.
- Separate notification channels, permission-denied behavior and correct tap route;
  a deletion must not clear unrelated chat/community notifications.
- No public publication, production backend deployment or automatic main merge implied.

32-bit device-specific installation diagnosis is deferred by the user. The user
has reaffirmed authorization for a release draft once ready; no repeated permission
request is needed. Known unready Direct/true-gamepad features must stay disabled.
