# Secure direct transport — approved direction, implementation pending

Owner approved changing BOTH endpoints for secure pairing/transport, accepting legacy incompatibility. Coordination notes posted before protocol work:
- Remote#8: https://github.com/xykal/XyDesk-Remote/issues/8#issuecomment-6260445101
- XyDesk#34: https://github.com/xykal/XyDesk/issues/34#issuecomment-6260445358
No sibling source/version/protocol constant changed here. This document is a design gate, not a secure transport implementation.

## Current containment
The Remote host's legacy JPEG/audio/input UDP listener is OFF by default. Owner-only private tests may explicitly pass `--legacy-private-udp`. This flag is NOT encryption, peer authentication or firewall enforcement; never expose it publicly. Ordinary host startup no longer writes Windows policy/privacy/firewall configuration, generates/modifies Windows passwords or registers legacy relay identity. Existing policies/credentials from older versions are not reverted or deleted silently.

## Preferred implementation to review and test
- Standard TLS via maintained/platform implementations (Android JSSE; Windows Schannel or audited TLS library), not a bespoke AEAD handshake. Prefer TLS1.3 where supported; if supporting older Windows, TLS1.2 must use approved forward-secret AEAD cipher suites, never plaintext or weak-cipher downgrade.
- Host identity is a real certificate/public key. Compare/pin SHA-256 of actual certificate DER against the host display through a trusted out-of-band step/QR. Never label a machine-ID hash as a certificate fingerprint. Host key storage must be access-controlled; no deterministic secrets or Windows account-password reuse.
- First pairing is a short-lived, cryptographically random, single-use capability issued after explicit host consent. Authenticate host BEFORE submitting capability. Paired client grant is scoped/revocable and stored securely. No clipboard auto-copy of secrets or raw token URLs/logs.
- Require connection auth before capture subscription, audio/mic transfer, keyboard/mouse, clipboard or gamepad. Default permission is view-only; explicit input/microphone grants and visible revoke/disconnect controls. Failed trust/pairing cannot fall back to legacy UDP.
- Bounded versioned frames with type/length validation and strict resource caps. Choose/announce exact marker/schema/port in coordination before implementation; none reserved or changed in this commit. Protect control freshness against stale/repeated input across reconnect generations.
- Consider independent authenticated control and media TLS connections bound to the same grant to reduce large-video head-of-line blocking of releases; hard cap every queue, drop stale video and measure latency. TCP latency/compatibility vs audited DTLS/WebRTC dependency size/complexity is an explicit trade-off, not a claim that TLS/TCP equals UDP120FPS.
- Host neutralizes held input on revoke, EOF, error, timeout and session replacement. Reconnect never replays old key-downs or enables mic without explicit live intent/permission. Client attempts neutral release but host-side expiry is still required when final packets cannot arrive.
- Native RDP and existing external hub remain separate; do not silently repurpose their IDs, microphone tags, DSP or endpoints.

## Required evidence before draft readiness
1. Malformed/oversized frame handling, auth-before-action, wrong/expired/replayed capability, wrong/pin-changed certificate, revoked grant, disallowed input/mic, no downgrade and bounded concurrent clients.
2. Record/transport failure, disconnect during held input, reconnect generation, actual mic start/stop/permission revocation, host endpoint selection and in-game input levels.
3. Native single-EXE linkage/dependency checks, supported Windows startup/shutdown, TLS interoperability with Android, weak network backpressure, render/audio latency and CPU/memory measurements.
4. Correctly signed APK/same signing identity, no debug APK, one host EXE, checksums, artifact inventory and draft-only release. No production deployment or automatic public publish.

Release remains blocked while secure transport is unimplemented. A successful syntax/JVM check is not native runtime or secure-handshake evidence; do not build a draft claiming these gates passed.
