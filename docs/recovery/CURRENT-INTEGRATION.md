# Recovery integration in progress — not a final release

Base: exact owner-selected v1.1.23 Build87 / 9f64b47, confirmed in publish logs.

Ported separately from later source: chat owner mutation/reconciliation, cache,
text archive, global-chat navigation/notification separation, and Buat RDP preflight.
Ten critical connection/render/native/build files still match the owner baseline
byte-for-byte; four source regression checks enforce this in CI. No AdMob removal,
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
Kotlin/core/app unit-test gate and Windows-host cross-compilation passed CI run
39318872335 at 660a1be (the later 108th test covers a Worker-only correction). Live quota recovery, authenticated two-account chat, physical
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
also remembered per account. Latest Android compile is being repeated after these
changes; the earlier successful gate is not claimed to cover them. Preserve original draft/artifacts.
