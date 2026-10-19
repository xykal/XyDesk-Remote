# User feedback: analog, connection diagnostics, APK install, profiles and chat

## Source work in this iteration (runtime verification still required)

- Ordinary RDP on-screen left stick gains IJKL and NumPad 8/4/2/6 in addition
  to Pointer, WASD and Arrows; append-only saved mode ordinals. Right HUD pad
  remains mouse/camera mapping, not Windows analog-controller forwarding.
- Release old HUD stick keys before changing mode and when Activity stops.
  Host NumLock determines NumPad interpretation. Validate diagonal hold/release,
  mode switches and left/right multitouch on a candidate/device.
- Expected native cancellation after an explicit stop request becomes normal
  Disconnected **after terminal cleanup**. Real logon/security failures are not
  suppressed merely because a cancel was also requested.
- Generic logon failure no longer asserts a wrong password. Security-protocol
  negotiation gets an appended stable XyDesk diagnostic category 0x000C (not a
  FreeRDP numeric status). No existing diagnostic number is reassigned.
- Compatibility retry changes TCP/codec only: it no longer silently changes
  TLS security level or the user's requested security protocol/NLA.
- Shared-address 100.64.0.0/10 TCP failures explain overlay/VPN routing and ACLs
  if applicable, without assuming a particular VPN, Windows edition or password.
- Release APK validation checks ELF class, endianness, shared-object type and
  machine architecture of every packaged `.so`, rejects unexpected ABI folders
  and duplicate ZIP entries. Six synthetic fixtures exercise this check; these
  are NOT installable APKs or installation proof.

## Remaining user requests (not implemented by this iteration)

1. **All analog modes:** free-form per-game axis/key assignments and verified true
   analog gamepad forwarding. A gamepad-looking HUD does not create a controller
   device on Windows. Signed supported host device/driver, safe transport,
   real axis/trigger routing and hardware/game tests remain required. Never
   enable the unsafe legacy listener or bypass driver signature enforcement.
2. **32-bit installation:** user confirms install-time failure, but phone model,
   Android version, APK name/hash, exact Package Installer error and update-vs-
   fresh-install status are missing. ARMv7 32-bit is not Intel x86-32; MIN_API is
   currently 23. ABI checks alone cannot diagnose SDK, signing continuity,
   storage, download corruption or device-policy failures. Preserve user profiles;
   do not prescribe uninstall/data deletion as a first fix.
3. **Buat RDP:** simplify validated host/port/account entry, import/paste and
   profile edit flow. Distinguish saving an RDP connection from provisioning a
   Windows server. Do not promise free provisioning or send credentials to a
   new endpoint without an explicit supported flow.
4. **Global chat:** measurable offline-first loading, stable history pagination,
   reconnect reconciliation, durable outbox/idempotent ACKs, account isolation
   and portable backup/restore. Existing encrypted, account-scoped local cache
   is limited to text snapshots; its noBackupFilesDir/device Keystore design
   intentionally is **not** a cross-device backup. A portable backup needs an
   explicit protected export/restore design, deletion/retention semantics and
   exclusion of view-once media. Do not claim WhatsApp parity or E2E encryption.
5. **Draft testing:** signed release candidate precedes actual install/runtime
   checks. Record incomplete/disabled features honestly. Existing workflow
   publishes drafts only from eligible main/tag lineage; no automatic main merge,
   tag or bypass is authorized by this source-check iteration. Current version
   metadata unchanged. Confirm scope if making a deliberately partial candidate
   instead of waiting for every outstanding feature.

## Reading the submitted log

The relay had a successful session before a requested disconnect/native cancel.
A later relay attempt was rejected at logon. One shared-address target never
passed TCP; another passed TCP but failed security negotiation. A delayed engine
certificate line is not correlated strongly enough to prove the failed TCP
attempt reached TLS. Native signed pointer values are opaque handles, not by
 themselves proof of corruption. Requested 120 Hz selected 90 Hz because that
was the highest advertised mode; that is not a measurement of remote game FPS.
Old automatic UDP mic startup shown in the log is already disabled in current
source. No real user endpoint has been probed from the development sandbox.

## Verified source result — 1486fdb

- Kotlin/JVM tests and app Kotlin compilation: **SUCCESS**, [39256872114](https://github.com/xykal/XyDesk-Remote/actions/runs/39256872114). Nine new Kotlin tests exercise reported diagnostics/security preservation and extra stick modes.
- Worker/source checks: **SUCCESS**, [39256873849](https://github.com/xykal/XyDesk-Remote/actions/runs/39256873849).
- Local: twelve source guards, six synthetic APK/ELF checks, two AUDIN C mock tests **PASS**.
- Debug APK, release APK and publication jobs **SKIPPED**. No APK/EXE/tag/draft/deploy created. No claim that affected phones can now install.
