# Recovery integration in progress — not a final release

Base: exact owner-selected v1.1.23 Build87 / 9f64b47, confirmed in publish logs.

Ported separately from later source: chat owner mutation/reconciliation, cache,
text archive, global-chat navigation/notification separation, and Buat RDP preflight.
Ten critical connection/render/native/build files still match the owner baseline
byte-for-byte; three source regression checks enforce this in CI. No AdMob removal,
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

Local checks: 107 Worker tests and 3 protected-baseline guards pass. Full Android
compile pending CI. Live quota recovery, authenticated two-account chat, physical
RDP/PC Stream frames/mic and all original required feature acceptance remain pending.
Genuine Windows controller, secure BOTH Direct endpoints, complete durable outbox/
full-history/media sync and full provisioning must not be marked delivered by the
bounded archive/preflight. No new release/tag, production deployment or paid upgrade
has been performed for this integration. Preserve original draft/artifacts.
