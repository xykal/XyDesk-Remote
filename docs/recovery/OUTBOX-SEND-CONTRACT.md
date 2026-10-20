# Durable send boundary — backend source, Android outbox NOT delivered

This is an additive, chat-only contract on `agent/restore-v123-baseline`.
No native/RDP/Direct/audio protocol, application version, release tag or baseline
connection/render input changes. This source has NOT been deployed in this step.
The production Worker remains `68b7fc17-ed1a-4083-babc-529d417d6228`.

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

## Client work still required

Encrypted AtomicFile + Android Keystore storage in noBackupFilesDir, bounded queue,
no bearer tokens in queue, durable write BEFORE send, exact immutable payload,
verified numeric account scope and fail-closed handling of token/account changes.
An offline entry whose numeric identity is not yet verified must remain tied to
its original credential fingerprint, not automatically migrate by username after
credential rotation. Background scheduling/retry/backoff, restored pending UI,
manual recovery and receipt-only handling remain to be implemented and tested.
Neither this backend work nor the existing text cache completes full history/media
sync, protected portable backup, or the original recovery acceptance requirements.

## Validation

143/143 local Worker tests; five protected-baseline guards preserve ten original
9f64 connection/native/build inputs. Added coverage includes concurrent sends,
HTTP-to-WS retry, account isolation, payload conflicts, current edits/tombstones,
trimmed receipt-only replies, rollback on receipt/index failure, expiry/bounded GC,
quota failures and capability identity/namespace forwarding. CI now runs ALL
`cloudflare/*.test.mjs`, not only `worker.test.mjs`. No live authenticated messages,
new deployment, signed APK, device/runtime acceptance, namespace reset or paid
upgrade was performed for this step.
