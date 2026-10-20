# Recovery integration in progress — not a final release

Latest additive backup hardening: BACKUP-HARDENING.md, source0f29e6d; workspace
/home/user/XyDesk-RecoveryNext. Android JVM/Kotlin and Web gates SUCCESS; no signed/device acceptance.

Latest Android queue controls: see QUEUE-CONTROLS.md and QUEUE-CONTROLS-VALIDATION.json.
Source ccdb6f4 adds own-account inspector, capability-only identity recheck, SAME-ID
retry, non-view-once text copy, durable cancellation and independent Retry-After floor.
Current workspace /home/user/XyDesk-QueueUI; no backend deployment or new signed APK.
Six source guards include original connection checks + one queue-control guard.
Actual phone/credential-switch/storage/clipboard and original recovery acceptance
remain pending. Unbound credential-rotation recovery is still intentionally blocked.

Latest GitHub login correction: see GITHUB-LOGIN-API-FIX.md and GITHUB-LOGIN-VALIDATION.json.
Worker c532dfc8-8abd-4c9b-b7e4-d90157da999c is deployed; default + login alias now
use the canonical API OAuth callback, not RdpFree. Source 7abb70d; actual owner
sign-in/app-return acceptance and historical secret rotation remain pending.
Previous OAuth workspace /home/user/XyDesk-Login. The outbox phase below is historical
implementation/deployment evidence; latest live history is still HTTP200/99 rows.

Latest Android outbox source: see ANDROID-OUTBOX.md and OUTBOX-VALIDATION.json.
Source 429af46 includes private encrypted durable queue + JobScheduler + notification
replies; positive authenticated/physical-device acceptance remains pending.
Previous outbox workspace /home/user/XyDesk-Outbox, branch agent/restore-v123-baseline.
Worker f203a85d-65a4-4b85-b6c0-2c2e2740ac87 deployed with original assets/config/data.
Latest global history is HTTP 200/99 rows, no paid upgrade; earlier quota 503 evidence
below is historical. One accidentally accepted rollout probe was exactly cleaned;
no owner message reset/deletion, temporary cleanup invocation retired. Worker tests
148 pass; five guards allow only one exact additive JobService manifest declaration.
Single-directory sticker fix 52dcd09 and its green gate remain included.
No signed recovery APK installed or final release; all original runtime acceptance
and remaining features still open. Community evidence in COMMUNITY-VALIDATION.json
is historical and is superseded for deployment state by OUTBOX-VALIDATION.json.

Base: exact owner-selected v1.1.23 Build87 / 9f64b47, confirmed in publish logs.

Ported separately from later source: chat owner mutation/reconciliation, cache,
text archive, global-chat navigation/notification separation, and Buat RDP preflight.
Nine critical connection/render/native/build files still match the owner baseline
byte-for-byte; the manifest has one checked additive JobService block, with all
other bytes matching the original locked hash. Five source guards enforce this. No AdMob removal,
native stripping, lifecycle-connect deferral or renderer rewrite is mixed in.

Source-level likely black-screen path found in rc.3: onCreate queues a connection
until STARTED, then onStart retries while lifecycle can still be CREATED and queues
again. No later queue drain means manager stays Idle, so ConnectingScreen never
appears. The baseline flow does not have that gate. This is not a claim of completed
device reproduction or hardware validation.

Additional backend/client recovery corrections:
- WebSocket history errors become explicit sanitized events, not swallowed errors.
- Connection errors show status/auth failures and respect bounded Retry-After.
- A quiet healthy WS no longer turns into 5-second history polling after 45s.
  Real-time WS events remain immediate; HTTP is a 5-minute consistency fallback.
- Pin reads coalesce for 30s, invalidate after writes/deletes/mutations and do not
  fabricate a successful unpin when storage fails. This removes a per-poll read
  left uncached by the previous history-only quota mitigation.

Local checks: 108 Worker tests and 4 protected-baseline/source guards pass. Android
Kotlin/core/app unit-test gate passed CI run 39320409340 at 2e91f36 after the
account/socket corrections. Earlier Windows-host cross-compilation was separately
verified in successful run 39318872335 at 660a1be; no host source changed since. Live quota recovery, authenticated two-account chat, physical
RDP/PC Stream frames/mic and all original required feature acceptance remain pending.
Genuine Windows controller, secure BOTH Direct endpoints, complete durable outbox/
full-history/media sync and full provisioning must not be marked delivered by the
bounded archive/preflight. No new release/tag, Android production deployment or paid upgrade
has been performed for Android. Worker-only quota/error mitigation was deployed
as 6c7ca142-511c-4653-9d8b-bd4dd54b642b; assets/config remained 5990eb9 with
zero asset updates and unchanged namespaces/migrations. Live health is 200 but
history still 503/chat_storage_quota_exceeded/Retry-After 300. This is not
functional chat recovery. The deployed Worker includes the post-commit pin-read correction (test 108).
A live WS probe now receives history_unavailable with sanitized quota/503/retry300
rather than silently showing an empty history. Positive authenticated chat is untested.

Follow-up source correction: track socketConnected independently of the HTTP/UI
connected indicator, restart polling delays on account/transport change, and reject
in-flight responses/events after account switch/disposal. Hidden/view-once state is
also remembered per account. Latest Android gate passed at 2e91f36 (39320409340). Windows read-only diagnostic
script parse/execution/schema passed on the Windows CI runner in 39324792675 at
4ebaa91. Owner target diagnostics/runtime tests are still pending; no new signed
recovery APK was assembled and no release/tag was created. Preserve original draft/artifacts.
