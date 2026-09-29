Start: 2026-09-28

## 2026-09-28 — Android input/display fixes
- Merged via PR [#1](https://github.com/xykal/XyDesk-Remote/pull/1); main code commit `cbeea092`. Release version prepared as `0.5.10` / version code `27` in `cd9d47c`.
- CI compile + JVM unit tests passed on the release candidate: [branch gate 36501234993](https://github.com/xykal/XyDesk-Remote/actions/runs/36501234993), [main merge gate 36501553819](https://github.com/xykal/XyDesk-Remote/actions/runs/36501553819), and [version-bump gate 36501831764](https://github.com/xykal/XyDesk-Remote/actions/runs/36501831764). Debug ABI jobs were skipped for the release commits; no APK debug build was run in this pass. No local build was run.
- Fixes: buffer soft-keyboard composition until commit; draw round mouse glyphs; add an explicit text-to-Windows-clipboard action; wait for reported dimensions before claiming resolution success; clarify that remote DPI is a host-side DisplayScaleFactor request, not local zoom.
- Still pending: signed release tag/build verification, then device/host acceptance for Gboard typing, Windows Paste, DPI and non-default resolutions. Host RDP policy may reject DISP scale/resolution requests; clipboard payloads are never written to logs.
- Next: finish the signed `v0.5.10` release workflow, inspect release assets, then test on the user's Android device and Windows RDP host.
