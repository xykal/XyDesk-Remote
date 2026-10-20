# Recovery candidate — NOT a final/public release

Owner-requested signed RELEASE draft, restored from exact v1.1.23 Build87 baseline (9f64b47) with independently tested additive fixes. Android package version remains 1.1.23 / 87 to preserve the locked baseline; recovery tag identifies candidate source, not a new Android version. Do not uninstall or clear data. Pinned official signer must match before installation.

Includes source fixes for canonical API GitHub OAuth, chat owner mutations/navigation/notifications, single-write sticker directory, durable account-scoped encrypted outbox with retry/copy/cancel, and limited encrypted text archive hardening. APK signature/hash verification and build success do NOT establish physical-device functionality.

## Unverified / incomplete — do not call complete
- Actual owner OAuth sign-in/app return; historical OAuth secret rotation still pending.
- Physical Connect/loading/render, RDP mic, account-switch/Keystore/process-death/storage/picker behavior.
- Full Buat RDP provisioning, full history/media/protected portable backup, safe recovery of unbound rotated-credential queues.
- Genuine Windows controller (no driver purchase/install authorized), authenticated BOTH Direct endpoints.
- Host runtime/mic/audio/game acceptance. Android raw Direct/input/audio bridge starts/discovery are blocked; native ordinary RDP AUDIN is separate. Host UDP defaults OFF (explicit legacy-private test flag only); no secure-Direct claim.

Host package contains one statically linked XyDeskRemoteHost.exe (also provided directly), no Standalone variant, DLL distribution or automatic setup scripts. No Windows code-signing claim. ARM32 installation remains deferred.

Draft only: do not publish as latest or replace public landing downloads. Existing baseline draft and prior assets retained. Use SHA256SUMS.txt and SIGNATURE-VERIFICATION.txt; only signed RELEASE APKs are deliverables, no debug APK.
