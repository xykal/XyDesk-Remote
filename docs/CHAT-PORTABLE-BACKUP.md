# Portable text archive and cloud launch preflight

## Implemented source, runtime/SAF verification still required

Chat Settings offers owner-selected SAF export/import (`.xychat`) with an explicit
passphrase (12+ characters). The format `XYCBK001` uses random 16-byte salt,
PBKDF2-HMAC-SHA1 (1,400,000 iterations; API 23 compatibility), a 256-bit key,
random 12-byte nonce and AES-GCM authenticated encryption. Magic and normalized
verified GitHub login are bound as AAD. No token, passphrase or plaintext export
is stored in preferences; cancellation/disposal clears mutable working buffers.
Java/Compose strings cannot guarantee complete memory erasure.

Only up to 80 confirmed cached/current text rows are archived. Excludes pending
sends, local IDs, view-once rows, media payloads and signature claims. Tombstones
are scrubbed before serialization. This is a bounded archive, not complete chat
history, a durable outbox, or WhatsApp/E2EE parity. Account rename requires the
original login for file decryption; a forgotten passphrase cannot be recovered.

Imports are bounded to 1,000,052 bytes before KDF/JSON processing, with at most
80 rows. Imported sender/owner/signature claims are not trusted. Imported rows
are kept in a distinct account-bound, Android Keystore-encrypted no-backup cache
and shown **read-only**, never merged into live chat or uploaded. This avoids
reintroducing deleted content into the live timeline. Old files can still contain
pre-deletion text; users must delete obsolete exported copies themselves.
A provider may leave an incomplete file after failed export; UI reports failure.

Required candidate tests: API 23/provider interoperability, file picker cancel,
process death, account switch, wrong password/account, corrupted/oversized files,
low disk/full provider, re-opening imported archive and logout isolation.

## Buat RDP Cloud

Launch preflight validates duration, supported runner, machine/username syntax
and optional Portmap host/port before API mutations. Readable, parsed Secrets
inventory is required; permission failure no longer means “ready”. Dashboard
inspection failure does not silently fall through to unrelated API calls.
Fork sync is non-destructive; conflicts stop launch with an actionable error,
never force-reset user work. Automatic sync is performed once per launch.
Redirects are disabled on authenticated GitHub/dashboard requests.

Confirmation explains quota/provider policy and that workflow backup can fail.
Stop/cancel never claims it has saved every change. This work does not execute
cloud runs, create user resources or change XyRDP VERSION/workflow contracts.
