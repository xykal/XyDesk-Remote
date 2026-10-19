## Owner-test draft — not a final/public release

This draft is for installation and runtime testing by the owner. Signed Android
assets must pass pinned signer continuity, APK/ELF ABI, ZIP and hash validation.
The Windows host must pass x64 GUI PE/import checks. Those checks do not prove
Windows startup, microphone routing, game compatibility, or device installation.
The Windows EXE is not claimed Authenticode-signed.

### Implemented source intended for testing
- Optimistic owner-verified chat edit/delete with persistent deletion placeholders,
  revision-safe reconciliation, account-bound local cache and independent notifications.
- Bottom-navigation Global Chat entry.
- Password-protected portable text archive (up to 80 confirmed messages; no media,
  view-once or pending messages), restored only to a separate read-only archive.
- Safer Buat RDP launch preflight and non-destructive fork synchronization.
- Named custom keyboard stick presets, stable existing modes and shared-key release.
- Ordinary RDP native microphone session control, subject to OS permissions and
  remote endpoint/runtime testing; not an instant in-session mic mute guarantee.

### Explicitly incomplete / unavailable
- Secure Direct transport on BOTH endpoints is **not implemented**. Legacy raw UDP
  stays OFF by default and must not be exposed publicly. No secure Direct claim.
- Custom sticks emulate keyboard/mouse, not a Windows virtual analog controller.
  Genuine virtual gamepad/driver integration is **not implemented**.
- Full durable offline outbox, complete history pagination/sync and media backup
  are **not delivered** by the bounded text archive.
- Android/Windows hardware and two-device chat tests remain owner-test items.
  ARM32 install diagnosis is deferred; synthetic fixtures are not install proof.
- Chat storage availability is constrained by the Cloudflare account quota. A
  confirmed free-tier rows-read exhaustion was observed on 2026-10-19, including
  the previous deployed version. A new deployment cannot reset that quota.
  Structured 503/cooldown and read coalescing do not constitute restored storage.

Do not publish this draft as final or represent all requested features as complete.
Do not silently purchase driver licenses or paid Cloudflare service. Old backups
can retain pre-deletion text; deleting a local archive does not delete exported files.
