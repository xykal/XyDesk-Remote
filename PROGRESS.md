Start: 2026-09-28

## 2026-09-28 — Android input/display fixes
- Merged via PR [#1](https://github.com/xykal/XyDesk-Remote/pull/1); main code commit `cbeea092`. Release version prepared as `0.5.10` / version code `27` in `cd9d47c`.
- CI compile + JVM unit tests passed on the release candidate: [branch gate 36501234993](https://github.com/xykal/XyDesk-Remote/actions/runs/36501234993), [main merge gate 36501553819](https://github.com/xykal/XyDesk-Remote/actions/runs/36501553819), and [version-bump gate 36501831764](https://github.com/xykal/XyDesk-Remote/actions/runs/36501831764). Debug ABI jobs were skipped for the release commits; no APK debug build was run in this pass. No local build was run.
- Fixes: buffer soft-keyboard composition until commit; draw round mouse glyphs; add an explicit text-to-Windows-clipboard action; wait for reported dimensions before claiming resolution success; clarify that remote DPI is a host-side DisplayScaleFactor request, not local zoom.
- Signed release `v0.5.10` is published: [GitHub Release](https://github.com/xykal/XyDesk-Remote/releases/tag/v0.5.10), [release workflow 36502440722](https://github.com/xykal/XyDesk-Remote/actions/runs/36502440722). Compile + JVM tests, R8 signed per-ABI build, `apksigner` checks, and publication passed; debug ABI job was skipped. All three APK assets are uploaded and return HTTP 200: arm64-v8a (83,931,907 bytes), armeabi-v7a (70,402,963), and x86_64 (89,456,599). No local build was run.
- Runtime acceptance is still pending on the user's Android device and Windows RDP host for Gboard typing, Windows right-click Paste, DPI application, and non-default resolutions. A queued DPI request is not proof the host applied it; host RDP policy may refuse scale/resolution requests. Clipboard payloads are never written to logs.
- Next: install the matching signed release APK and validate those behaviors on-device/host; record actual outcomes without guessing causes.

## 2026-09-29 — hari kerja ke-2
- Audited the new HUD/control report in `docs/AUDIT-2026-09-29-hud-clipboard.md`; identified that Add left the session in edit mode, editor completion was easy to miss, the edit grid was visually noisy, and scroll only emitted full notches.
- Implemented immediate button usability after Add, explicit auto-save/finish UI, scrollable editor with center-preserving resize, removed the white edit grid, and added a separate vertical swipe-scroll control with partial wheel-unit accumulation.
- Clarified that Android system clipboard copies already sync automatically when the per-device channel is enabled; Gboard private history may only commit IME text and cannot be read by the app without unsafe silent capture.
- Added JVM tests for wheel direction/accumulation, bounds/reset, and resize anchoring. Updated release metadata to `0.5.11` / version code `28`. No local build and no debug APK.
- Next: compile/JVM-test via GitHub Actions, publish signed per-ABI release `v0.5.11`, inspect assets, then request Android/Windows runtime acceptance.
