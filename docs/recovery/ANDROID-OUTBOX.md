# Android chat outbox — source integrated, physical acceptance still required

Source `429af465c4ed8348908c9cee5c278d677af06282` builds on the exact owner-selected
9f64 connection/render baseline and the single-directory sticker correction.
This is NOT a new signed APK/final recovery release or proof of phone behavior.

Follow-up queue-management UI: see QUEUE-CONTROLS.md and QUEUE-CONTROLS-VALIDATION.json.
It adds explicit same-ID retry/copy/cancel and a persisted Retry-After floor, not
unbound-account reassignment or full history/media sync. Historical gate/source
identifiers below describe the initial queue integration.

## Implemented in source

- Text/media/view-once SEND payloads are committed before transport to per-message
  AtomicFiles under private `noBackupFilesDir/chat-outbox-v1`, encrypted with a
  dedicated Android Keystore AES-GCM key. Filename/version is authenticated as AAD.
  No bearer token is serialized. Original nonce, payload and creation time remain
  immutable across retry. Failed commit leaves the text draft and reports failure.
- Queue bounds: 32 pending messages, 24 MB aggregate disk budget, 3.2 MB per record,
  160 total pending/receipt/cancel records. Never evict unsent content. Only terminal
  receipt/cancel metadata may be reclaimed for new capacity. Corrupt/inaccessible
  ciphertext or missing keys fail closed; never silently clear/rekey old records.
- A newly offline/unverified entry is bound to the original credential fingerprint.
  Only authenticated server negotiation may bind its immutable numeric provider ID,
  durably BEFORE sending. Once bound it can resume with a rotated credential for the
  SAME verified ID. An old unbound entry is NOT migrated by username after rotation.
- Capability negotiation is made against the original GlobalChat object before any
  send. Expected author ID, protocol, nonce and receipt/current-row IDs are checked.
  No durable fallback through legacy HTTP/WS. Edge also gates durable forwarding,
  so old object code cannot silently accept a new durable envelope during rollout.
- Serial flush batches (at most three per turn), bounded exponential backoff and
  Retry-After; authentication/permanent/expiry failures pause without deleting data.
  Seven-day replay deadline is not extended by retry. One system-only JobService
  schedules network-constrained background attempts without an added dependency,
  foreground service, boot permission, paid upgrade or token in job extras.
- Startup/account login hooks resume pending work. Opening chat restores pending
  rows without treating them as sent. Pending-media parse is off the main thread.
  Notification inline replies also commit to this queue before showing a local echo.
- Only validated acceptance replaces queued payload with encrypted receipt metadata.
  View-once/media content is not exported into receipt journals or the text archive.
  Live current revisions/tombstones are reconciled; receipt-only ACK removes the
  pending placeholder, never recreates old text/media from the queued payload.
- Explicit local deletion cancels further attempts (already in-flight requests may
  already have reached the server; this is NOT an unsend guarantee). Cancellation
  and acceptance are terminal: late callbacks cannot restore a cancelled payload.

## Baseline exception is explicit, not hidden

Nine protected files remain byte-identical to 9f64. The app manifest has ONE exact
additive declaration for ChatOutboxJobService with signature-only BIND_JOB_SERVICE
permission. The baseline guard strips/checks ONLY that exact block, then compares
all remaining bytes with the original locked SHA-256. It does not replace the
original hash or allow arbitrary manifest edits. Connect/loading/render/session and
native build paths, original AdMob/dependencies and all old components are unchanged.

## Production deployment and rollout incident

Final Worker version `f203a85d-65a4-4b85-b6c0-2c2e2740ac87`: original main5990 assets,
config, namespace IDs and migration list retained; zero changed assets uploaded.
No global-object rename, storage reset or purchase. The temporary cleanup invocation
below has been retired; the final Worker exactly uses the committed Worker source.

The first post-deploy check reached old GlobalChat code: capability returned 404,
and ONE anonymous rejection probe was unexpectedly accepted. No further live chat
messages were sent. Later capability returned 401, demonstrating the new handler
was active. A tested one-time transactional cleanup matched ONLY the agent probe's
exact ID + UUID nonce + timestamp + text + absent verified author + unmodified row,
never owner messages. The probe disappeared (history 100 -> 99); cleanup invocation
was then removed by final deployment. No general cleanup/moderation endpoint or
privilege grant was added. The inactive exact-tuple helper/tests remain as audit
source. Existing push copies cannot be claimed erased by deleting the server row.

The deployment fix also rejects anonymous durable envelopes at the edge BEFORE
forwarding, and refuses forwarding to old objects lacking send capability. This is
not a claim that all legacy anonymous chat routes are authenticated. Future live
negative probes must be EMPTY/nonpublishing even if sent to old code.

Final read-only/empty-body smoke: health 200, official channels 200, global history
200 (99 rows), probe absent, anonymous capability 401, empty anonymous durable POST
401. The earlier free-quota 503 was not present in this observation; this is not a
paid upgrade or a promise that free quotas can never recur. No real authenticated
publication/receipt replay or two-account production test was performed.

## Still pending / limits

18 new outbox JVM tests exercise real AES-GCM round trips with injected test keys,
store recreation, AAD tamper, wrong/missing key, disk failure, capacity, immutable
intent, terminal cancellation, identity isolation/credential rotation, receipt-only
acceptance, forged ACKs and bounded retry. They are not Android Keystore/JobScheduler
or radio/OEM/physical-device tests. Worker suite has 148 tests; five source guards.
Final Android Kotlin/core/app JVM gate SUCCESS: 39376611693 at 429af46.
Web gate SUCCESS: 39376612784. See OUTBOX-VALIDATION.json for exact CI source/run
and live evidence.

Physical airplane-mode enqueue -> process death -> reopen/reconnect; authenticated
lost-ACK/retry exactly once; real account switching; media/view-once lifetime;
background policy/OS quota/storage-full/key invalidation; send sound and actual UI
performance must be exercised before acceptance. Android FORCE STOP suppresses jobs
until the app is opened. Jobs are not persisted through reboot; encrypted payloads
remain and startup reschedules them when the app is opened again.

Unbound entries after credential rotation are intentionally quarantined; an explicit
manual identity/recovery UX for those entries is NOT implemented. Expired/permanent
failures remain local for copy/cancel rather than silently becoming new messages.
No claim of complete full-history/media sync, portable backup, full provisioning,
real Windows controller, secure BOTH Direct endpoints or full recovery completion.
