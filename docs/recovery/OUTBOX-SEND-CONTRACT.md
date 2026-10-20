# Durable send boundary — deployed backend; Android source integrated

This is an additive, chat-only contract on `agent/restore-v123-baseline`.
No native/RDP/Direct/audio protocol, application version, release tag or baseline
connection/render input changes. Backend deployed as `f203a85d-65a4-4b85-b6c0-2c2e2740ac87`; Android source is
integrated but phone/positive-authenticated acceptance is still pending.
See ANDROID-OUTBOX.md and OUTBOX-VALIDATION.json, including rollout-probe remediation.

## Negotiation and identity

A future Android queue MUST authenticate `GET /api/chat/send-capabilities` before
any automatic replay. The edge forwards this to `/send-capabilities` on the
**original** `xydesk-global-chat-v1` object. A new edge cannot advertise support on
behalf of an old object. Missing/404/503/wrong protocol means keep pending, NOT
legacy send or HTTP-to-WS fallback. This endpoint verifies provider identity but
needs no history/SQL reads. Its response includes `send_protocol: 1`, immutable
numeric `author_id`, verified `github_login`, and `replay_max_age_ms: 604800000`.
No username, founder badge, display name or client-supplied ID authenticates anyone.

## Send envelope and atomic acceptance

`POST /api/chat/send` and WS `type: msg` share the SAME persistence function.
Durable clients supply, in addition to the immutable message payload:

- `outbox_v: 1`;
- `outbox_author_id`: the expected verified numeric provider ID;
- `outbox_created_at`: original creation epoch milliseconds, never refreshed;
- `client_msg_id`: `local-` plus a UUID, retained unchanged across attempts.

Account mismatch is 409; guest is 401; malformed time/nonce or unsupported protocol
is 400; expiry is 410. Durable replay is accepted for seven days, with at most five
minutes of future clock skew. The client must retain expired/blocked local content
for explicit user recovery, not silently delete it or automatically reset its nonce.

One storage transaction writes a cryptographically random canonical message ID
(time prefix preserves existing history key ordering), an author+nonce receipt and
an expiry index. Same author+nonce with changed text/media/view-once/reply/envelope
or creation time is 409. Different verified authors can use the same nonce without
sharing identity. Verified login and derived translations/transport fields are not
client intent; provider rename does not invent a new author.

A retry returns the CURRENT message revision/deletion tombstone. If history already
trimmed it, return `message: null`, `receipt_only: true`, and the receipt. NEVER
recreate history from queued plaintext. Only the winning insert broadcasts and
requests push; retries return acceptance privately. WS `send_ack` is explicit;
client `socket.send()` success is NOT server acceptance. Storage failures remain
503 + retry guidance (or a correlated WS failure), not success/empty history.

Receipts contain identity/IDs/time and a SHA-256 intent hash, not a copy of text,
media or encryption envelopes. Seven-day receipts are garbage-collected in batches
of at most 32 per newly accepted message, in a transaction, without deleting
canonical history. Expiry is checked before storage, so GC cannot permit stale
durable replay. GC failure cannot revoke an already committed acceptance.

Legacy authenticated UUID sends also share receipts while retained; legacy clients
have NO durable replay guarantee after expiry. Anonymous/non-UUID legacy sends keep
their pre-existing behavior, never a promise of durable safety.

## Client integration and remaining acceptance

The client-side encrypted queue, background scheduling, pending restoration, bounded
retry, account/credential binding and notification replies are now integrated in
source. See ANDROID-OUTBOX.md for implementation, exact bounds and limitations.
Manual recovery of unbound entries after credential rotation, genuine phone and
positive authenticated production tests remain pending. Neither this queue nor the
existing text cache completes full history/media sync or protected portable backup.

## Validation

148/148 Worker tests; five protected-baseline guards preserve nine original files
and the original manifest bytes outside one exact checked additive JobService block. Added coverage includes concurrent sends,
HTTP-to-WS retry, account isolation, payload conflicts, current edits/tombstones,
trimmed receipt-only replies, rollback on receipt/index failure, expiry/bounded GC,
quota failures and capability identity/namespace forwarding. CI now runs ALL
`cloudflare/*.test.mjs`, not only `worker.test.mjs`. No live authenticated messages, signed APK, device/runtime acceptance, namespace
reset or paid upgrade. Worker deployments and one remediated anonymous rollout probe
are documented in ANDROID-OUTBOX.md; do not describe that probe as a rejected send.
