# Account-scoped Android outbox controls — source, not installed-phone acceptance

Source ccdb6f4 extends the encrypted send queue without changing the deployed
Worker, canonical GitHub login, native/RDP connection inputs, manifest or dependencies.
Production remains the canonical-login deployment c532dfc8-8abd-4c9b-b7e4-d90157da999c.

## Controls

Chat Global has an account-keyed **Antrean kirim / Send queue** dialog. It lists only
pending entries that match the original credential fingerprint or a verified
immutable provider ID. It never lists other-account payloads by matching username.
Only text preview/metadata is retained in dialog state; base64 media and crypto
envelopes are not decoded/exported by the dialog. Disk/crypto/JSON work is off-main;
metadata bursts coalesce for 100ms. A 1Hz countdown runs only while a visible dialog
has waiting rows; that countdown makes NO network requests or disk reads.

- **Periksa identitas akun:** authenticated capability-only lookup, no send. This
  makes already-bound SAME-ID queues accessible after credential rotation even when
  all rows are paused. It does NOT migrate an unbound entry by username or send a
  message simply because the identity check succeeded.
- **Coba lagi:** fresh capability/owner verification, durable metadata update, then
  schedule a retry. Original nonce, exact payload, creation time and attempt history
  remain unchanged. Recheck current row after network/account races; terminal states
  never become pending again. This button does not mean the server accepted a send.
- **Salin teks:** re-read the current pending record and verify current account/
  credential at action time. Own ordinary text only, never attachment data, cipher
  envelope or view-once caption. Android 13+ sensitive-clipboard flag suppresses OS
  preview. Sending that copied text in the composer is an explicit NEW user action,
  not an automatic retry or silent new nonce.
- **Batalkan:** confirm that only future attempts are cancelled, durably write a
  terminal record and drop its local pending payload. It cannot recall a request
  already received by the server. Late acceptance cannot resurrect cancelled data.
  Cancelling from the existing timeline's delete-for-me action also waits for the
  durable result; on disk failure the row stays visible, with an error instead of a
  false cancellation. Receipt-confirmed rows can still be hidden locally, not unsent.

## Retry timing and compatibility

Manual retry cannot override normal backoff, an independent server Retry-After
floor, the seven-day replay expiry, permanent conflict or invalid device time.
A fresh credential/verified identity is NOT permission to bypass server cooldown.
Auth-paused retries also retain this floor. A future device timestamp is not silently
rewritten. Expired/permanent rows remain available for permitted copy/cancel.

Encrypted records add optional `retry_not_before`, default zero for old records;
no destructive migration, key/format rotation or queue reset. Store updates prevent
lowering this floor. Background scheduling preserves it across auth pauses and
credential rotation, and may verify a still-unknown bound account when due without
releasing that account's payload before provider-ID verification.

Unreadable directories/records are errors, not fabricated empty queues. Missing key,
ciphertext corruption and failed writes retain fail-closed behavior and no data wipe.
The directory reader now reports an unreadable existing folder instead of treating
listFiles failure as an empty directory.

## Validation and limits

17 new pure control-policy tests plus one real AES-GCM old-schema compatibility
and monotonic-floor test join the prior 18 outbox tests (36 queue tests total).
Coverage: exact nonce/payload/time retention, even 1ms cooldown, auth cooldown,
verified rotation without bypass, expiry/clock/conflict, account isolation,
unbound quarantine, view-once copy denial, no attachment/envelope export, terminal
races and failed durable cancellation retaining original ciphertext/payload.
154 Worker tests remain passing. Six source guards (the five previous baseline
checks plus one queue-control guard); the existing exact outbox-only manifest
exception remains unchanged. See QUEUE-CONTROLS-VALIDATION.json for final CI evidence.
Final Android/core/app JVM + Kotlin gate SUCCESS: 39431730480 at ccdb6f4.
Web gate SUCCESS: 39431731182.
An initial source gate failed on a missing TextButton import; it was fixed before
acceptance. Do not cite that failed gate as a passed Android test.

No new backend deployment, live chat test post, privilege grant, OAuth secret
rotation, signed APK/release or physical phone acceptance in this step. UI layout,
TalkBack/font scaling, real Clipboard/Keystore/storage pressure, account-switch and
process-death behavior still need device testing. Unbound entries after credential
rotation remain intentionally quarantined; manual reassignment/recovery of those
entries is NOT implemented. Full history/media sync, protected portable backup,
provisioning, real Windows controller, secure BOTH Direct and original runtime
recovery acceptance are still separate unfinished requirements. OAuth secret
rotation and real owner sign-in/app return also remain pending.
