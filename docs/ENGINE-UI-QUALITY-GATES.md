# Engine / UI quality coverage

This is a coverage ledger, not a statement that the application is finished.
Owner now authorizes compile/build and a draft release AFTER completion and verification. No premature release, production deployment or automatic merge is authorized.

| Area | Current work | Required evidence still missing |
|---|---|---|
| FreeRDP render/lifecycle | Pending dirty batches reset on instance/resize/release; obsolete bind/resize/input callbacks guarded | Reconnect, rotate, background/foreground and resize stress on a real RDP session; JNI lifetime/thread audit |
| HUD input | One queued combo transaction, reverse key release after failure; virtual pad press cleanup in finally; foreground/focus/transport input gate, stick cancellation reset, neutral on edit/pause/focus loss and fresh layout drag callbacks (latest CI pending) | Physical + IME + HUD modifier coexistence, stuck-key recovery, touch cancellation and accessibility |
| PC stream | Control channel state/send shares teardown lock; packet schema unchanged | Disconnect/send race stress with real WebRTC; loss, delay, bandwidth and queued-input behavior |
| Profile / identity | Account-keyed chat state, nickname/avatar cleanup, unread preferences partitioned | Switch/logout during network activity, cache restore and profile refresh on Android |
| Security | View-once local temporary hygiene, encrypted text snapshots, no fabricated read receipts | Host identity/auth validation, certificate-trust UX, threat-model review and device tests; local UI checks are not server auth |
| Animation | Existing user presets preserved; media work moved off render path | Measure recomposition/frame time; audit infinite animations offscreen, battery saver and system animator settings |
| UI consistency | Initial spacing/radius tokens and adaptive chat bubble widths | Full home/profile/settings/HUD/stream sweep; 320dp, split screen, RTL, large fonts, keyboard and light/dark screenshots |
| XyRDP / RdpFree | Audit scope only; template/workflow/product behavior left unchanged | Review credentials and failure/retry UX; coordinate host/protocol/product changes before implementation |
| Media / chat | Bounded cache/download/decode, off-main image/video/audio/sticker work | True metadata-first transport/lazy media, idempotent durable outbox, long-code file flow and memory/network measurements |
| Shared control | Not enabled or implemented | Authenticated host invitation/consent, view-only default, explicit input grant, revoke/disconnect policy and coordinated protocol/host work |

## Guardrails
- Gate success is compile/unit verification, not Android runtime/native correctness.
- Do not rewrite JNI render ownership to zero-copy without instrumented device evidence.
- Do not silently disable certificate verification to fix connection failures.
- Preserve wire compatibility and do not change VERSION or sibling-host contracts without coordination.
- APK size and performance improvements remain projections until explicitly approved validation is possible.
- Future release must be a draft, not a public automatic publish.
