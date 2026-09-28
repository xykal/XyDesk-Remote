Start: 2026-09-28

## 2026-09-28 — hari kerja ke-1
- Done: audited Android controller/editor/theme paths; fixed the live HUD size draft, removed white HUD plates, themed editor chrome, added slider semantics and regression tests, and added About attribution. Wrote `docs/AUDIT-2026-09-28.md` and `docs/DESIGN.md` first.
- CI: Kotlin compile + JVM unit tests passed in run `36496196802`; per-ABI debug build passed in run `36496505029` (artifact `xydesk-remote-debug-per-abi`). No local build was run.
- Still pending: device touch/visual validation and clipboard behavior on a real RDP host. The branch build is debug-signed, not a production-release APK; no release was published.
- Next: install/test on device using a suitable debug install path, then decide whether to merge and cut a release. Runtime slider behavior on hardware remains unverified.
