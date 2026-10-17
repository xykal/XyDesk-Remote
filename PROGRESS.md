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
- Branch compile + JVM-test gate passed: [run 36507438735](https://github.com/xykal/XyDesk-Remote/actions/runs/36507438735). Debug, release-build, and publish jobs were skipped; no APK was built locally or on this branch run.
- Merged as PR [#2](https://github.com/xykal/XyDesk-Remote/pull/2) at `e8e2939b`. Main compile/JVM gate [36507773288](https://github.com/xykal/XyDesk-Remote/actions/runs/36507773288) passed; debug per-ABI job was skipped.
- Signed release `v0.5.11` is published: [release page](https://github.com/xykal/XyDesk-Remote/releases/tag/v0.5.11), [workflow 36508018994](https://github.com/xykal/XyDesk-Remote/actions/runs/36508018994). Kotlin/JVM gate, R8 signed per-ABI build, `apksigner`, and publication passed; debug ABI was skipped. arm64-v8a, armeabi-v7a, and x86_64 APK links were checked and returned HTTP 200. No local/debug APK build.
- Next: install the matching signed release APK and test HUD actions, saved layout, smooth swipe scroll, and normal Android clipboard → Windows Paste on the actual phone/host. Gboard private history needs system clipboard or explicit text-send; do not claim it auto-syncs.
- New 2026-09-29 audit: `docs/AUDIT-2026-09-29-ime-feedback.md` (19 lines). Confirmed soft-IME composing text was withheld until commit; confirmed automatic system clipboard paths in both directions, with failure visibility missing; verified no feedback/share route and only three proposed ideas.
- Fixed the IME path to stream minimal composing deltas, correct autocorrection without replaying the shared prefix, deduplicate finish/commit, and handle deletion/surrogate pairs. Added JVM regression cases for streaming, autocorrect, finish, cancellation, deletion, and Unicode.
- Added bilingual feedback popup and explicit Android share chooser. It shares only app identity, category, and user-entered text; no silent upload, persistence, diagnostics, credentials, host, or clipboard data. Added a share-draft privacy test.
- Preserved automatic phone↔PC system clipboard flows and added generic, content-free error notices for failed sends/copy-to-phone. No clipboard payload or exception details are logged; device/host acceptance is pending.
- `IDEAS.md` now has 10 proposed feature ideas and 10 proposed non-feature improvements. Candidate metadata is v0.5.12 / version code 29.
- The first branch CI gate [36515902459](https://github.com/xykal/XyDesk-Remote/actions/runs/36515902459) caught a stale Java test assertion after `finishComposingText()` became `void`; fixed in commit `40c04a2`. Branch compile + all JVM tests passed in [36516153858](https://github.com/xykal/XyDesk-Remote/actions/runs/36516153858), with debug and release jobs skipped.
- Merged PR [#3](https://github.com/xykal/XyDesk-Remote/pull/3) by rebase at `afea8875`. Main compile/JVM gate [36516413853](https://github.com/xykal/XyDesk-Remote/actions/runs/36516413853) passed; debug, release, and publish jobs were skipped.
- Signed release `v0.5.12` is published: [release page](https://github.com/xykal/XyDesk-Remote/releases/tag/v0.5.12), [workflow 36516653249](https://github.com/xykal/XyDesk-Remote/actions/runs/36516653249). Compile/JVM gate, R8 signed per-ABI builds, `apksigner` verification, and publication passed; debug ABI build was skipped. Assets: arm64-v8a (83,948,291 bytes), armeabi-v7a (70,419,347), x86_64 (89,472,983). No local build or tests.
- Runtime acceptance is still pending on kall's Android device and Windows RDP host for live Gboard typing/corrections, automatic clipboard both ways, and feedback share flow.
- Continued from draft diff `034b7f4` and completed `docs/AUDIT-2026-09-29.md`: identified and fixed the root cause of broken added HUD keys/combos (`SessionSurfaceController.sendVirtualKey` bypassed `KeyboardMapper` and sent Android `KEYCODE_*` integers as Windows `VK_*` codes), fixed HUD layout reset when dragging multiple buttons (`rememberUpdatedState` on `onDrag`/`onPhase`/`onEdit` + touch-slop gesture gating only for layout mode and `SCROLL_SLIDER`), synced direct-touch cursor coordinates for HUD click/scroll actions, hardened the O(1) circular native event queue against teardown races, applied single-field `host[:port]` parsing, `XyDesk` remote drive label, collapsible certificate details, Opt-in `lowBandwidth` (`broadband-low` + `AVC420`), and 20+20 items in `IDEAS.md`. Prepared candidate metadata `0.5.13` / version code `30`.
- Implemented follow-up improvements for `v0.5.13`: fixed `ImeCompositionBuffer.isPrintableImeTextEvent` and `SessionView.sendKeyEvent` so digits (`0-9`), spaces, symbols, and non-composing letters from soft keyboards are never dropped and appear immediately; made `SessionPanel` and modal overlays isolate focus/keystrokes/gestures (`setOverlayActive`, scrim pointer-consumption, hierarchical Back navigation, pinned header/tabs, card sections); upgraded top-bar `HudMappingBanner` (`+ Tambah`, `Reset`, `Selesai`) and categorized `HudKeyPicker` (`Kombinasi`, `F1 - F12`, `Single Key`, `Numpad`, `Huruf A-Z`, `Modifier`, `Mouse & Scroll`) with search; added `HudAction.ONE_SHOT` (`Sekali pakai`), HUD layout JSON export/import, automatic cellular/metered `lowBandwidth` detection (`RdpOptions.resolveForActiveNetwork`), and expanded `IDEAS.md` to 26 feature ideas + 26 non-feature improvements.
- Merged PR [#5](https://github.com/xykal/XyDesk-Remote/pull/5) at `71ce776`; branch gate [36599118284](https://github.com/xykal/XyDesk-Remote/actions/runs/36599118284) and `main` gate [36599740420](https://github.com/xykal/XyDesk-Remote/actions/runs/36599740420) passed.
- Signed release `v0.5.13` (`VERSION_CODE=30`) is published: [release page](https://github.com/xykal/XyDesk-Remote/releases/tag/v0.5.13), [workflow 36599745113](https://github.com/xykal/XyDesk-Remote/actions/runs/36599745113). Compile/JVM gate, R8 signed per-ABI builds, `apksigner` verification, and publication passed; debug ABI job was skipped. Assets verified (HTTP 200): `app-arm64-v8a-release.apk` (83,981,059 bytes), `app-armeabi-v7a-release.apk` (70,452,115 bytes), `app-x86_64-release.apk` (89,505,751 bytes).
- Audited and fixed all post-`v0.5.13` device-testing findings for `v0.5.14` (`VERSION_CODE=31`): separated scrim backdrop `Box` from foreground `Column` (`.pointerInput(Unit) {}`) in `SessionPanel`, `HudKeyPicker`, `HudKeyEditor`, and `XyOverlay` so tapping tabs/controls inside the sidebar never closes the panel; restored Gboard/Samsung Keyboard native clipboard bar (`InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE` in `SessionView.java`), made phone↔PC clipboard sync fully automatic (`syncPhoneClipboardToRemote` on clip change, window focus, resume, and session connect + `onImeSnippetCommitted`) and removed manual clipboard sidebar buttons; eliminated the white hexagonal artifact inside circular HUD buttons (`HudKeyGlyph` text labels for mouse buttons, stroke-only `XyIcons`, removed `.shadow(...)` on translucent circles); removed the `"Keyboard HP dibuka"` toast on keyboard button tap; unified Dark UI (`LocalContentColor`, window system-bar sync, default `DARK` mode, distinct `primaryContainer`); added `XyDialog` confirmation & validation modals across all destructive/state-changing flows; and added 12 Microsoft Remote Desktop differentiators in `IDEAS.md`.
- Merged PR [#6](https://github.com/xykal/XyDesk-Remote/pull/6) at `8cafd04`; branch gate [36616431671](https://github.com/xykal/XyDesk-Remote/actions/runs/36616431671) and `main` gate [36616802358](https://github.com/xykal/XyDesk-Remote/actions/runs/36616802358) passed.
- Signed release `v0.5.14` (`VERSION_CODE=31`) is published: [release page](https://github.com/xykal/XyDesk-Remote/releases/tag/v0.5.14), [workflow 36616831176](https://github.com/xykal/XyDesk-Remote/actions/runs/36616831176). Compile/JVM gate, R8 signed per-ABI builds, `apksigner` verification, and publication passed; debug ABI job was skipped. Assets verified (HTTP 200): `app-arm64-v8a-release.apk` (83,981,059 bytes), `app-armeabi-v7a-release.apk` (70,452,115 bytes), `app-x86_64-release.apk` (89,505,751 bytes).
- Implemented the 4-stage core feature expansion (12 differentiator features + Streaming, Latency, and Security engine) in PR [#7](https://github.com/xykal/XyDesk-Remote/pull/7) (branch CI gate [36626613805](https://github.com/xykal/XyDesk-Remote/actions/runs/36626613805) green) and merged to `main` at `a275554`.
- Built and deployed the official XyDesk Remote landing & download portal (`web/`, live at `https://rdp.xydesk.my.id` and `https://xydesk-remote.vercel.app`) with a monochrome editorial UI, locked download section (`Ver 1.0.0` Launching Countdown popup `2026-10-10T10:00:00+07:00`), complete pre-launch pages (`privacy.html`, `terms.html`, `licenses.html`, `status.html`, `/api/health`), and automated GitHub Actions release/tag/artifact/run pruning (`.github/workflows/cleanup.yml` and `publish-release` cleanup in `.github/workflows/build-apk.yml`).
- Built and verified signed release `v0.5.15` (`VERSION_CODE=32`) in [workflow 36629495990](https://github.com/xykal/XyDesk-Remote/actions/runs/36629495990) (`app-arm64-v8a-release.apk` 84,063,127B, `app-armeabi-v7a-release.apk` 70,534,183B, `app-x86_64-release.apk` 89,587,819B) and locked it in **Draft (`draft: true`)** state on GitHub Releases ahead of `Ver 1.0.0` launch.
- Implemented `v0.5.16` (`VERSION_CODE=33`, [workflow 36640449873](https://github.com/xykal/XyDesk-Remote/actions/runs/36640449873)) and `v0.5.17` (`VERSION_CODE=34`, [workflow 36645417167](https://github.com/xykal/XyDesk-Remote/actions/runs/36645417167)): added `SYSTEM_ALERT_WINDOW` (*Display over other apps* / AppsPerms) declaration and `XyFloatingOverlay` live floating mini-monitor for OEM ROMs that block native PiP; replaced the raw credential form in `AddDeviceScreen` with a collapsible **Account Credential Dropdown** (`Pilih Akun / + Tambah Akun Baru`); increased Home desktop preview height to `212.dp`; added the `+` Bubble Menu (`Koneksi PC [Tahap Pengembangan]` vs `Koneksi RDP`), vendor-specific Host GPU Encoder profiles (`NVIDIA NVENC`, `AMD AMF`, `Intel QuickSync`, `CPU Fallback`), VSYNC dirty-rect coalescing & `batterySaver` frame pacing, and Windows Host helper (`https://rdp.xydesk.my.id/host`).
- Implemented `v0.5.18` (`VERSION_CODE=35`): fixed `+` (`Koneksi PC` / `Koneksi RDP`) immediate Back navigation when no fields are edited; made `Koneksi PC` completely separate from `Koneksi RDP` (no Windows credentials, no resolution or DPI tweaking, native PC resolution/DPI lock); hardened native `CLIPRDR` (`android_event.c`, `android_cliprdr.c`) and Kotlin clipboard/paste pipelines (`SessionManager`, `XyDeskSessionActivity`, `SessionSurfaceController`) so copying large text/`.txt` files never disconnects the session; fixed modal dialog backdrops (`XyDialog`, `XyOverlay`, `AddDeviceScreen`, `Screens.kt`) so the screen behind a modal never turns solid black; redesigned the session status overlay into a vertical top-to-bottom frameless/backgroundless stack (`FPS`, `Latency`, `Resolusi`, `Encode`, `Relay`, `Network`); added a 1-tap Floating Window (`Jendela Mengambang`) trigger button on the HUD rail and removed the `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` redirect (tries `AppsPerms` overlay types directly + `XyDraggableFloatingMiniWindow` fallback).

## 2026-10-03 — hari kerja ke-3
- Audit dicatat di `docs/AUDIT-2026-10-03.md`. Mode Otomatis RDP sekarang memilih 1280x720; preset resolusi manual tetap ada, dan PC Connect tidak lagi meminta ukuran virtual pada URI awal.
- Ditambahkan tes regresi untuk resolusi 720p, mode Follow, preset manual, dan PC Connect. Batas Android diturunkan dari API 29 ke API 24 (Android 7.0+) dengan ABI armeabi-v7a tetap tersedia; pembuatan notification channel dijaga untuk API 26+.
- Build guide, `SECURITY.md`, README attribution, dan backlog smoke test Android 24/36 diperbarui. `git diff --check` dan pemeriksaan statis konfigurasi lulus; Gradle/unit/native build dan uji perangkat tidak dijalankan lokal.
- Next: jalankan gate CI lewat PR, lalu uji APK pada Android API 24 dan 36 serta verifikasi teks terhadap host Windows nyata.








## 2026-10-03 — website, SEO, telemetri opt-in, dan rilis v1.0.1
- Audit awal menemukan countdown kedaluwarsa yang tidak membuka unduhan, status fallback yang usang, dan `/api/health` live lama mengembalikan HTML. Rilis publik `v1.0.0` saat itu memuat APK app `0.5.34` / version code `51` dan total 72 unduhan APK (unduhan ulang dihitung, bukan pengguna unik).
- Perubahan source untuk SEO, halaman unduhan, status berbasis `release-state.json`, Worker/API sesi, disclosure privasi, dan panduan host yang meminta unduh-lalu-tinjau (bukan pipe skrip remote ke eksekusi) dikomit sebagai `3bc7c07` dan dipush ke `main`/`feat/website-seo-downloads`. Google Search Console verification file tetap identik byte-for-byte.
- Tag `v1.0.1` menunjuk commit `3bc7c0735626c68fcc3fcf118ffbd18b2013b5ae`. GitHub Release publik berhasil diterbitkan dari Build APK run [#214](https://github.com/xykal/XyDesk-Remote/actions/runs/37133369522). Gate Kotlin/JVM, `compileReleaseKotlin`, build R8 bertanda tangan untuk tiga ABI, verifikasi `apksigner`, build Windows Host Agent, unggah, dan publikasi berhasil; debug APK dilewati sesuai `[skip-debug]`.
- APK yang dipublikasikan: arm64-v8a **84,375,480 byte** (SHA-256 `fb84baf78de344d40c7275d02ea35ee0b769c89f9641da2a5c4e856700c92abe`), armeabi-v7a **70,373,106 byte** (`3c635fdae6f55a36fa7519d0539936d43c1ec8cfaa74383671ca3ddb579f984d`), x86_64 **89,774,137 byte** (`4773aced78b46a06f2a79db639086dcf24daf1717519d750a597646b67bcdb95`). Ketiganya cocok dengan SHA256SUMS.txt dan digest aset GitHub. Rilis memuat Android `0.5.35` / version code `52`, minimum Android 7.0 / API 24.
- Laporan VirusTotal berjalan sebagai job informatif non-blocking pada saat catatan ini diperbarui; belum ada hasil scan yang diklaim. Uji runtime APK pada perangkat Android/host Windows nyata tetap belum dilakukan.
- Metadata website di `web/release-state.json`, ukuran APK, dan status run #214 sudah divalidasi lokal. 21 Worker test lulus; Wrangler dry-run, HTML SEO utama, JSON, `git diff --check`, safe-host-setup scan, dan perbandingan Google verification byte lulus. Cloudflare `whoami` berhasil.
- Metadata/dokumen website telah dikomit sebagai `c70d488` dan dipush ke `feat/website-seo-downloads` serta `main`; commit status `34d03e3` juga dipush. GitHub Actions Web Worker checks #9 dan Build APK #216 lulus.
- Cloudflare Worker/site pertama kali dideploy dengan version `1374c19e-2ea5-4699-935e-bac0f7a9f26c`. Live robots.txt, sitemap.xml, Search Console file (53 byte, persis sama), release-state v1.0.1, `/api/health`, `/api/status`, dan `/api/active-sessions` lulus. Pada 16:27:54Z endpoint sesi melaporkan 1 lease opt-in (perkiraan sesi, bukan orang unik); tidak ada POST sintetis. `/api/stats` memberi 503 dan tiga alias APK memberi 502; checksum langsung bekerja. Permintaan APK hanya HEAD, bukan unduhan isi.
- Koreksi Worker ada di commit `96bf8f6`, sudah dipush ke `main`/`feat/website-seo-downloads`, dan gate Build APK #217 serta Web Worker checks #11 lulus. Worker corrective version `b10c38ae-b4a6-4fee-9591-feda783a9594` sudah dideploy. Tiga APK alias kini HEAD-redirect ke URL aset v1.0.1 yang tepat tanpa mengunduh body; SEO, Search Console, release metadata, health/status, checksum, dan session endpoint lulus. `/api/stats` tetap 503.
- Pada 16:38:11Z, API melaporkan 1 lease sesi opt-in (perkiraan sesi, bukan orang unik); tidak ada POST sintetis. GitHub Releases API menghitung **80 unduhan aset APK** saat itu (1.0.0: 73; 1.0.1: 7; unduhan ulang ikut dihitung). Nilai tersebut dihitung langsung dari GitHub, belum tampil melalui Worker `/api/stats`.
- Source fix mencoba token `GH_TOKEN`, lalu `GH_PAT`, lalu API publik; Worker tetap tidak mendapat metadata unduhan. Menunggu token GitHub **read-only** terpisah dengan `Contents: read` untuk memperbarui secret `GH_PAT` via Wrangler dan memverifikasi `/api/stats`. Tidak memakai token tulis untuk secret Worker. Uji runtime APK pada perangkat Android/host Windows nyata belum dilakukan; hasil VirusTotal belum dikonfirmasi pada catatan ini.

## 2026-10-04 — Android follow-up v1.0.2 (dirilis)
- User memberi CrashLog dari Oppo A3s (Android 8/ColorOS sekitar 6): `NoSuchMethodError: FileObserver.<init>(File, int)` saat `GlobalApp.onCreate` → `PrintJobMonitor`. Root cause startup crash terkonfirmasi: overload FileObserver(File, int) tidak tersedia sebelum API 29. Perbaikan pada `PrintJobMonitor.java` kini memakai overload kompatibel `FileObserver(String, int)``. Guard terpisah untuk `MotionEvent.getClassification()` API 29 juga tetap ada sebagai hardening, bukan penyebab startup.
- User menyebut teks remote burik saat ukuran normal/fit dan membaik ketika zoom; muncul di RDP maupun PC Connect. Kemungkinan resampling/scaling tampilan, belum terbukti; screenshot serta resolusi remote tetap diminta.
- UX request: sederhanakan app secara umum; Add Device sudah punya Quick/Advanced. Perubahan lokal mengelompokkan Backup/Update/Feedback di drawer menjadi Alat & Bantuan yang bisa dibuka, dan menambahkan Ruang Santai opsional di beranda dengan meme teks/jokes satire politik nonpartisan, dapat disembunyikan dari Pengaturan.
- Opsi refresh 30/60/90/120 kini meminta mode panel Android yang didukung (juga diterapkan saat pilihan PC Connect diubah dalam sesi). HUD mengukur invalidasi UI terkoaleskan, bukan FPS host; ini tak dapat melampaui panel 60 Hz atau memaksa FPS host/jaringan.
- Nama aset release yang diminta: `XyDesk-armeabi-v7a.apk`, `XyDesk-arm64-v8a.apk`, `XyDesk-x86_64.apk`; workflow mengganti nama output menurut ABI. Launcher label tetap `XyDesk Remote`. App version `1.0.2` / code `53`; tag `v1.0.2` sudah dipublikasikan dan job VirusTotal dilewati sesuai permintaan.
- Commit `52df63b` dipush ke `main` dan `feat/website-seo-downloads`. Build APK run [#219](https://github.com/xykal/XyDesk-Remote/actions/runs/37149309858) sukses: gate unit test/compile Kotlin dan Windows Host Agent lulus; build R8 bertanda tangan untuk tiga ABI, pemeriksaan `apksigner`, unggah aset, dan publikasi lulus. Debug ABI dilewati via `[skip-debug]`; job VirusTotal dilewati sesuai permintaan.
- Rilis publik `v1.0.2` (`1.0.2`, version code `53`) tersedia di [GitHub Releases](https://github.com/xykal/XyDesk-Remote/releases/tag/v1.0.2). Aset APK: `XyDesk-armeabi-v7a.apk` (70,373,102 byte), `XyDesk-arm64-v8a.apk` (84,375,476 byte), dan `XyDesk-x86_64.apk` (89,774,133 byte). `SHA256SUMS.txt` diterbitkan dan hash-nya cocok dengan digest aset GitHub.
- Uji runtime pada Oppo A3s/Android 8 belum dilakukan; jangan anggap crash sudah tervalidasi pada perangkat tersebut. Keluhan teks buram juga belum dikonfirmasi lewat perbandingan di perangkat/host nyata. Penyederhanaan UI ini masih pass pertama, belum mencakup semua layar. Tidak ada website/Worker deployment.

## 2026-10-06 — v1.0.6: merge PC stream, rilis draft, dan deploy Worker
- PR [#11](https://github.com/xykal/XyDesk-Remote/pull/11) (PC/Game Stream prototype + release hardening) di-merge ke `main` sebagai `65f5021`. Rilis `v1.0.6` sebelumnya gagal karena tag di-push sebelum PR merge: step "Ensure release tag points to a commit on main" menolak `ee2c004` yang belum ada di `main`. Tag `v1.0.6` kemudian dipindah (annotated) ke commit `main`.
- Tiga cacat diperbaiki sebelum rilis berhasil: (1) tag tidak menunjuk `main`; (2) `release.properties` masih `1.0.5`/`57` sehingga tag `v1.0.6` akan memuat APK berlabel rilis lama -> dinaikkan ke `1.0.6`/`58` (`7932c9f`); (3) commit `ee2c004` mengubah mode 23 file dari `100755` ke `100644` tanpa mengubah isi (blob identik), termasuk `client/Android/Studio/gradlew`, sehingga `./gradlew` gagal dengan exit code 126 di job Release -> dipulihkan di `63e0541`.
- `:core-pc-stream:testDebugUnitTest` ditambahkan ke gate compile dan job debug (`1f0fb15`) karena 5 unit test modul baru tidak pernah dieksekusi CI. Perubahan itu membuka cacat tersembunyi: `PcSignalClientTest` memakai `com.sun.net.httpserver` yang tidak ada di classpath compile unit test Android, sehingga `:core-pc-stream:compileDebugUnitTestKotlin` gagal ("Unresolved reference 'com'"). Stub endpoint ditulis ulang dengan `java.net.ServerSocket` tanpa mengubah assertion (`79e445a`).
- Run Build APK [#243](https://github.com/xykal/XyDesk-Remote/actions/runs/37372213215): job `Release per-ABI (R8 + signed) + GitHub Release` dan `Publish GitHub Release` **sukses**; log build mengonfirmasi `VERSION_NAME 1.0.6` / `VERSION_CODE 58`. Rilis `v1.0.6` terbit sebagai **draft** dengan 9 aset: `XyDesk-arm64-v8a.apk` (96.932.781 byte), `XyDesk-armeabi-v7a.apk` (77.456.361), `XyDesk-x86_64.apk` (106.214.227), `SHA256SUMS.txt`, `SIGNATURE-VERIFICATION.txt`, dan 4 artefak Host Agent Windows. Ukuran APK naik dibanding v1.0.2 karena AAR WebRTC (`io.github.webrtc-sdk:android:150.7871.01`) ikut ter-bundle; ini belum dianalisis dampaknya dan belum diuji di perangkat.
- Gate compile Kotlin di `main` (`79e445a`) **hijau**: [run #244](https://github.com/xykal/XyDesk-Remote/actions/runs/37424252919) — `Unit test JVM (core-rdp + core-pc-stream + IME + app UI)` sukses, `Compile Kotlin` sukses, nol step gagal. Web Worker checks #37 sukses.
- Kegagalan beruntun antara 19:57-20:50 UTC 2026-10-05 bukan salah kode: GitHub berstatus "Partial System Outage" dengan insiden "Incident with Actions" (critical, mulai 19:11 UTC) dan job mati dengan anotasi "The job was not acquired by Runner of type hosted even after multiple attempts".
- Worker produksi di-deploy lewat `wrangler deploy` (bukan GitHub Actions) ke `rdp.xydesk.my.id`, version `cf86fc80-04e6-404c-a5e6-6629a68c692f`, membawa migrasi Durable Objects `v3` (`PcSignalRoom`) dan binding `PC_SIGNAL_ROOMS`. 36 unit test Worker lulus lokal; `wrangler deploy --dry-run` membaca 17 aset.
- Smoke test produksi lulus: `/robots.txt`, `/sitemap.xml`, `/release-state.json`, `/api/health`, `/api/status`, `/api/active-sessions` = 200; `/api/stats` kini **200** (`total_apk_downloads: 433`, `release_count: 3`, sumber GitHub Releases API publik) sehingga **tidak** perlu menaruh token GitHub di secret Worker; file verifikasi Google identik byte-for-byte (SHA-256 `4260082018a2ef1a21869796cd61a1bc27b7ed5034e42df0d8dc610adeadcc84`, 53 byte). Alur sinyal PC diuji end-to-end: `POST /api/pc/rooms` -> `POST .../join` -> `POST .../signal` (offer, `seq: 1`) -> `GET .../signal` sebagai viewer menerima event host; tanpa bearer token = 401.
- Belum diverifikasi: APK v1.0.6 pada perangkat Android nyata dan host Windows nyata (termasuk apakah PeerConnection WebRTC benar-benar tersambung, karena baru jalur signaling yang diuji), hasil VirusTotal, dan publikasi draft menjadi rilis publik. `web/release-state.json` masih menunjuk `v1.0.2` dan belum diperbarui ke `v1.0.6`.

## 2026-10-06 — v1.0.7: gamepad virtual (joystick) untuk jalur RDP & PC/Game Stream
- Gamepad virtual di layar sesi ditambahkan: 2 joystick analog, D-pad, ABXY, LB/RB, LT/RT, START/BACK (`client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyVirtualGamepad.kt`, 269 baris). Saklar `PAD` di pojok kiri atas layar sesi; preferensi baru `SessionPrefs.virtualPadEnabled` (default mati).
- Satu inti dipakai dua jalur agar tidak ada dua implementasi: `core-pc-stream/XyVirtualPad.kt` (murni Kotlin, tanpa Android — deadzone radial + rescaling, penskalaan sumbu int16, trigger 0..255, D-pad dari sumbu, tracker state + sequence uint32 wraparound) menghasilkan `XyGamepadState` yang dipakai `app/ui/input/XyGamepadActionMapper.kt` (jalur RDP) dan `core-pc-stream/XyPcStreamGamepadSink.kt` (jalur PC/Game Stream lewat `PcStreamViewer.sendGamepadState`).
- Semantik jalur RDP sengaja identik dengan gamepad fisik yang sudah ada di `XyDeskSessionActivity`: A=klik kiri, B=klik kanan, X=Enter, Y=Esc, LEFT_THUMB=klik tengah, stik 18 unit/defleksi, shoulder & trigger scroll 120, stik kanan 48. Tombol edge-triggered supaya menahan tombol tidak membanjiri host; joystick mengirim (0,0) saat lepas dan `releaseVirtualPad()` mengirim state kosong saat pad ditutup agar tidak ada tombol tersangkut.
- **Jalur PC-stream baru sampai transport**: belum ada layar sesi PC Stream yang membuat/masuk room, jadi sink belum punya host hidup. Keputusan injeksi sisi Windows (ViGEmBus vs driverless) tetap terbuka di issue #12.
- 13 unit test baru: `XyVirtualPadTest` (6) dan `XyGamepadActionMapperTest` (7). Keduanya dijalankan lokal dengan Kotlin 2.2.21 `-jvm-target 11` -> `OK (9 tests)` (termasuk 3 test paket yang sudah ada) dan `OK (7 tests)`. Kode Compose tidak bisa dikompilasi lokal; CI yang menjadi kompilatornya.
- Gate CI di branch lewat `workflow_dispatch` (build-apk.yml tidak punya trigger `pull_request`): compile pertama **gagal** dengan `XyDeskSessionActivity.kt:448 Argument type mismatch: actual type is 'BoxScope', but 'Context' was expected` karena `SessionPrefs(this)` dipakai di dalam blok `Box`; diperbaiki dengan meng-hoist `sessionPrefs` ke `onCreate`. Setelah itu gate hijau dan `Debug per-ABI` (armv7+arm64+x86_64) sukses. PR [#13](https://github.com/xykal/XyDesk-Remote/pull/13) di-merge ke `main` (`22cb7b5`).
- Dua cacat CI ditemukan saat merilis v1.0.7 dan keduanya diperbaiki: (1) job `publish-release` tidak punya step `actions/checkout` (hanya download artifact) sehingga `ci/verify_release_apk_hashes.py` tidak ditemukan dan job mati dengan exit code 2; (2) build rilis kedua dengan cache key yang sama **selalu** gagal di superbuild native — `[1/26] Performing update step for 'openh264'` -> `fatal: not a git repository: '.git'` — karena cache hanya menyimpan `freeRDPCore/.cxx` sedangkan source ExternalProject di-clone ke `freeRDPCore/src/main/cpp/external` di luar path cache. Buktinya ada di log: run pertama "Cache not found ... freerdp-native-cxx-v1-0ccf0e72" -> sukses -> "Cache saved"; run kedua "Cache restored from key: freerdp-native-cxx-v1-0ccf0e72" -> gagal. `cpp/external` kini ikut di-cache, ada langkah penjaga yang membuang `.cxx` bila source tidak ikut terpulihkan, dan cache key dinaikkan ke `v2` agar entri baru yang lengkap benar-benar tersimpan.
- Run Build APK [#257](https://github.com/xykal/XyDesk-Remote/actions/runs/37436338621) (tag `v1.0.7`) **sukses penuh**, termasuk dua step verifikasi baru yang pertama kali dieksekusi di runner: "Verifikasi integritas ZIP & libcrypto per ABI" dan "Verifikasi APK = SHA256SUMS = laporan signer". Rilis `v1.0.7` terbit sebagai **draft** (id 404511515) dengan 9 aset: `XyDesk-arm64-v8a.apk` 96.932.781 byte, `XyDesk-armeabi-v7a.apk` 77.456.361, `XyDesk-x86_64.apk` 106.214.227, `SHA256SUMS.txt`, `SIGNATURE-VERIFICATION.txt`, dan 4 artefak Host Agent Windows.
- Isi APK diverifikasi langsung, bukan hanya dari hash: `XyDesk-armeabi-v7a.apk` diunduh dari aset rilis, SHA-256 `1d311d1b736fc536a88ba5f455cce89ee0cec77a77b6ac5a83a16c0436b05a9a` cocok dengan entri `SHA256SUMS.txt` v1.0.7; `classes.dex` memuat string `virtual_pad_enabled` serta nama enum baru `LEFT_THUMB` dan `RIGHT_SHOULDER` (nama kelas `XyVirtualGamepad`/`XyGamepadActionMapper` tidak muncul karena di-obfuscate R8, sebagaimana diharapkan pada build rilis); `AndroidManifest.xml` biner memuat `versionName 1.0.7`.
- Tidak ada perubahan di `cloudflare/` maupun `web/` antara `v1.0.6` dan `v1.0.7` (diperiksa lewat API compare: 12 file berubah, semuanya di `.github/`, `PROGRESS.md`, dan `client/Android/Studio/`), sehingga **tidak ada Worker/website yang perlu di-deploy** pada rilis ini.
- Belum diverifikasi: perilaku pad di perangkat Android nyata (termasuk multi-touch saat joystick dan tombol ditekan bersamaan), dan jalur PC-stream end-to-end. Draft `v1.0.6` dan `v1.0.7` masih belum dipublikasikan; `web/release-state.json` masih menunjuk `v1.0.2`.

## 2026-10-06 — v1.0.8: gamepad virtual bisa digeser + panel sesi jadi popup blur
- Masukan uji coba pengguna v1.0.7: posisi pad tidak bisa diatur, bertabrakan dengan overlay lain, dan bar shoulder di tengah-atas menutupi pemutar Spotify yang juga mengambang di `Alignment.TopCenter` (`SessionControls.kt:521`, `top = 10.dp`, `zIndex 22f`).
- Gamepad dipecah jadi lima klaster yang masing-masing punya posisi dalam pecahan ukuran layar (`TOP_BAR`, `DPAD`, `LEFT_STICK`, `ABXY`, `RIGHT_STICK`). Mode tata letak dibuka lewat tombol ✎ atau tahan lama tombol `PAD`: tiap klaster diberi tepi dan bisa digeser, ada pengatur skala 70%-140% dan tombol Reset. Posisi & skala disimpan per perangkat (`{deviceId}.padlayout`, `virtual_pad_scale`) dengan codec teks `NAMA:x,y|...`.
- Matematika tata letak ditaruh di `app/ui/input/XyPadLayout.kt` (murni Kotlin, 9 unit test baru): penjepitan supaya klaster tidak pernah keluar layar, deteksi tumpang tindih, codec tahan entri rusak, clamp skala, dan posisi bawaan yang menjauhi overlay yang sudah ada.
- `SessionPanel` berubah dari drawer kanan (`widthIn(max = 360.dp)`, sudut 6dp, `CenterEnd`) menjadi popup di tengah layar (94% x 90%, maks 720dp, sudut 22dp, tepi halus). Permukaan remote diblur 28dp selama overlay terbuka memakai state `controlsOverlayOpen` yang sudah ada lewat `onOverlayActiveChange`. Blur dipasang di surface, bukan di panel, karena `Modifier.blur` memblur konten yang ditempeli; scrim ditipiskan ke 0.34 dan dipertahankan sebagai fallback karena `Modifier.blur` hanya aktif di Android 12+ (API 31).
- Gate CI hijau di branch `ux/pad-layout-macos-panel` lewat `workflow_dispatch` (`:app:compileDebugKotlin` lolos), PR [#14](https://github.com/xykal/XyDesk-Remote/pull/14) di-merge (`e52662e`). Rilis `v1.0.8` (`1.0.8`/`60`) terbit sebagai **draft**; `classes.dex` APK armv7 (77.472.745 byte) memuat `virtual_pad_scale`, `padlayout`, dan `virtual_pad_enabled`, serta `versionName 1.0.8` di manifest.
- Build ini **cache miss** pada key `freerdp-native-cxx-v2-...` lalu menyimpannya ("Cache saved with key"). Jadi jalur cache-hit dengan `cpp/external` di dalamnya belum teruji; build rilis berikutnya adalah ujian pertamanya.
- Roadmap masukan lanjutan (player liquid glass, rekaman PC + audio + kursor, web ketinggalan rilis, host agent satu exe, latensi) dicatat di issue #15. Rekaman saat ini (`RemoteScreenRecorder`) menulis frame desktop hasil decode ke MP4 via MediaCodec+MediaMuxer **tanpa track audio dan tanpa kursor**.

## 2026-10-06 — v1.0.9: stick analog WASD/panah, overlay pad dihapus, rekaman PC + audio, host agent satu exe
- Branch `ux/stick-wasd-scroll-pill` (31 commit di depan `main`). Gate CI hijau terakhir [run 37506364987](https://github.com/xykal/XyDesk-Remote/actions/runs/37506364987) pada `d631cde`: `:app:compileDebugKotlin` lolos dengan 0 error, `:app`/`:core-rdp`/`:core-pc-stream` `testDebugUnitTest` jalan, langkah mingw hijau.
- **Stick analog kini satu logika untuk dua jalur.** `XyStickMode` (`POINTER`/`WASD`/`ARROWS`) dibaca lewat `stickModeProvider` per gerakan (bukan saat komposisi), dan joystick HUD memakai `XyGamepadActionMapper` yang sama dengan gamepad fisik sehingga deadzone, threshold, dan deteksi tepi identik. Cabang HUD sengaja **tidak** diberi penjaga "nilai bukan nol": HUD mengirim `(0,0)` saat dilepas dan itulah yang melepas tombol arah. `lastHudStickState` terpisah dari state gamepad fisik supaya keduanya tidak saling menimpa deteksi tepi.
- **Overlay gamepad lama dihapus** (`XyVirtualGamepad.kt`, 398 baris). Semua tombol kini lewat seksi "Tombol" di panel sesi; preferensi `virtualPadEnabled`/`virtualPadScale`/`virtualPadLayout` yang tersimpan tidak lagi berpengaruh. Penghapusannya sempat merusak struktur kelas (satu `}` ikut terhapus di `e4ae98d`, 84 error "unresolved reference" yang menipu) dan diperbaiki di `fa5b1a5`.
- **Rekaman PC**: sumber audio adalah audio PC (RDP/QUIC, PCM16 stereo 24 kHz lewat tap `QuicAudioBridge`), kursor server digambar ke frame dengan kompensasi hotspot, indikator REC berkedip dengan `mm:ss`, dan hasilnya **satu MP4** di `Movies/XyDesk`. Merge dilakukan sebagai remux pasca-rekam (bukan menambah track ke `MediaMuxer` yang sudah jalan); kalau remux gagal, fallback ke MP4 + `.m4a` terpisah. Batas yang diketahui: decoder yang tersendat tetap terlihat di hasil rekaman, karena yang direkam adalah frame yang berhasil didekode HP.
- **Pil scroll** bulat penuh dan otomatis kembali ke tengah saat dilepas; panel sesi pakai accordion (`PanelSection`); bottom nav monokrom; player mengambang bergaya kaca dan kini **tidak bergantung Spotify** (aplikasi sumber apa pun yang memutar).
- **Latensi**: profil `AUTO` sebelumnya justru mematikan `asyncUpdate`/`asyncChannels`; keduanya kini `true` (`RdpOptions`, 4 assertion baru di `RdpOptionsTest`). Angka latensi nyata belum diukur.
- **Host agent satu exe**: `XyDeskRemoteHost-Standalone.exe` (798K, `-static`, tanpa DLL) dibangun di gate maupun release, masuk ZIP agent dan jadi aset rilis sendiri. Exe lama + kedua DLL tetap dibangun. Build pertamanya gagal link (`undefined reference to __imp_xydesk_host_inspect_system`) karena header memilih `__declspec(dllimport)` tanpa makro `XYDESK_QUIC_EXPORTS`/`XYDESK_HOST_CORE_EXPORTS` yang selama ini disuplai otomatis oleh CMake untuk target `SHARED`; diperbaiki di `d631cde`. Exe ini masih **console-only** — UI dan login layar PC belum dikerjakan.
- **Worker portal**: `getReleaseMetadata` kini menggabungkan rilis publik terbaru dari GitHub di atas `/release-state.json`. Belum di-deploy, dan tidak terlihat efeknya selama rilis masih draft (aset draft tidak bisa diunduh publik).
- Rilis `v1.0.9` (`1.0.9`/`61`) diterbitkan sebagai **draft** (`draft: true` memang hardcoded di langkah `softprops/action-gh-release`). Uji runtime di perangkat Android/host Windows nyata belum dilakukan.

### v1.0.9 — hasil build rilis (terverifikasi dari artefak, bukan dari log saja)
- Run rilis [37507188022](https://github.com/xykal/XyDesk-Remote/actions/runs/37507188022): `Gate compile Kotlin` **success**, `Release per-ABI (R8 + signed)` **success**, `Publish GitHub Release` **success**, `Debug per-ABI` **skipped**. Push ke `main` memakai `[skip-debug]` sehingga job debug di `main` juga **skipped** — tidak ada APK debug yang terakit, sesuai permintaan.
- Percobaan pertama run ini **gagal di langkah "Ensure release tag points to a commit on main"**. Penyebabnya urutan, bukan kode: tag `v1.0.9` terdorong lebih dulu sementara push ke `main` ditolak (clone `--depth 1` tidak punya `f25a343` secara lokal sehingga git tidak bisa membuktikan fast-forward; `git fetch --unshallow` menyelesaikannya). Setelah `main` = `b4c6d6b`, job yang gagal di-re-run dan lolos.
- Rilis `v1.0.9` (id `405028433`) berstatus **draft** (`draft: true`), 10 aset: `XyDesk-arm64-v8a.apk` 96.949.165 B, `XyDesk-armeabi-v7a.apk` 77.472.745 B, `XyDesk-x86_64.apk` 106.230.611 B, `XyDesk-Remote-Host-Agent-win64.zip` 765.748 B, `XyDeskRemoteHost-Standalone.exe` 817.026 B, `XyDeskRemoteHost.exe` 253.305 B, `xydesk_host_core.dll` 376.997 B, `xydesk_quic.dll` 629.486 B, `SHA256SUMS.txt`, `SIGNATURE-VERIFICATION.txt`.
- APK armv7 diunduh dan dibedah: SHA-256 `8db06ff6eb643df8f700172ae5cd0da28cd21d0410d1b77c747bf56bf0693ef1` **cocok** dengan `SHA256SUMS.txt`; `AndroidManifest.xml` (diparse dari AXML biner) berisi `package id.xydesk.remote`, `versionName 1.0.9`, `versionCode 61`, `compileSdkVersion 37`; `classes.dex` memuat string mode stick baru (Indonesia & Inggris) dan `Movies/XyDesk/`, serta **tidak lagi memuat** `XyVirtualGamepad`.
- Ukuran APK identik dengan v1.0.8 sampai satuan byte (dan v1.0.6 = v1.0.7) karena zipalign bekerja dalam kelipatan 16.384 B — delta v1.0.7→v1.0.8 persis 16.384 B di ketiga ABI. Hash-nya berbeda, jadi ini bukan build basi.
- ZIP agent dibuka: 6 berkas, termasuk `XyDeskRemoteHost-Standalone.exe`. SHA-256 keempat biner cocok dengan `SHA256SUMS.txt`. Import table PE dibaca langsung: exe tunggal hanya mengimpor `ADVAPI32/KERNEL32/USER32/WS2_32/dxgi/msvcrt/ole32` (semua DLL sistem), tanpa `xydesk_quic.dll`, `xydesk_host_core.dll`, maupun runtime mingw; exe lama masih mengimpor kedua DLL itu.
- Yang belum: exe tunggal tetap **console-only** (UI + login layar PC belum), Worker portal belum di-deploy, angka latensi belum diukur, dan uji runtime di perangkat nyata belum dilakukan.

## 2026-10-06 — v1.1.0: panel grid drill-in, rekaman 60 fps adaptif, host agent ber-UI

- Branch `ux/dashboard-glass-nav`, 12 commit di depan `main`. Gate CI hijau terakhir [run 37534648353](https://github.com/xykal/XyDesk-Remote/actions/runs/37534648353) pada `81cff51`: Kotlin 0 error, langkah mingw **hijau** (bukan dilewati), `:app`/`:core-rdp`/`:core-pc-stream` `testDebugUnitTest` jalan.
- **Navigasi bawah 3 slot** (`HOME`/`FEED`/`PROFILE`); sisanya pindah ke drawer. Feed jadi layar sendiri (`XyFeedScreen`), Profil jadi layar sendiri, dan kartu feed duplikat di Beranda dihapus total (tidak ada `showFunHub`/`SHOW_FUN_HUB` tersisa). Tema kembali monokrom (`XyAccent 0xFFF2F2F4`, `XyPrimaryContainer 0xFF232327`, light `primary 0xFF16161A`); warna status dipertahankan.
- **Primitif kaca baru** `components/XyGlass.kt`: `xyGlass`, `XyGlassCard`, `XyGlassTile`, `XyGlassRow`, `XyGlassSectionLabel`, `XyTileGrid`. Ini permukaan berlapis, bukan `Modifier.blur` — blur hanya ada di API 31 sementara `MIN_API=24`, dan blur pada `SurfaceView` tidak berlaku.
- **Panel sesi: grid kotak dengan drill-in.** 15 seksi level atas di empat tab kini digambar sebagai dua kolom kotak; mengetuk satu kotak membuka seksi itu penuh lebar dengan tombol "Semua seksi". Seksi direkam sebagai data (`PanelSectionScope`) lalu hanya isi seksi terbuka yang disusun. Dua seksi bersyarat (`if (pcConnectMode)` di ScreenTab, `if (coreInfo.isNotEmpty())` di SessionTab) ikut pindah ke dalam builder apa adanya; dua seksi bersarang tetap accordion.
- **Rekaman 60 fps adaptif.** `FRAME_RATE=30` diganti `TARGET_FRAME_RATE=60` + `FALLBACK_FRAME_RATE=30`. Dua lapis: kueri kemampuan `MediaCodecList` **dan** `runCatching { configure(...) }`, karena `createEncoderByType()` bisa memilih codec berbeda dari yang dikueri. Bitrate diskalakan terhadap 30 fps, atap `MAX_BIT_RATE` 16 Mbps tidak berubah. **Belum teruji di perangkat nyata** — yang terbukti baru kompilasi.
- **Kegagalan koneksi punya kode.** `core/RdpFailure.kt` memetakan pesan FreeRDP ke 12 kode (`0x00`–`0x0B`) plus `formatCode`, `formatConnectionId`, `detailLine`; `SessionState.Error` menambah `errorCode`/`connectionId`. `LibFreeRDP` hanya menyediakan `getLastErrorString(long)` tanpa kode numerik, jadi angka ini taksonomi XyDesk, bukan kode FreeRDP.
- **Nama APK** jadi `XyDesk-Remote-<abi>.apk` (langkah penamaan, loop signing, loop SHA256, argumen `verify_android_apks.py`), plus tiga alias portal ber-`abi`. URL baseline v1.0.2 dan `web/release-state.json` **sengaja tidak diubah** supaya tautan v1.0.2 yang sudah tersebar tetap hidup.
- **Masukan** punya dua tujuan — SHARE (chooser Android) dan COPY (clipboard) — plus pintu masuk di layar Profil. Sengaja tidak ada tujuan "email ke pengembang": repo tidak menyimpan alamat dukungan (`mailto:`/`support@`/`/api/feedback` = 0 hasil) dan mengarang alamat akan mengirim masukan ke tempat yang salah.
- **Host agent kini punya UI.** `main_host.cpp` menambahkan jendela Win32: info host, status QUIC + jumlah paket yang disegarkan tiap detik, tombol "Terapkan kebijakan"/"Salin ID PC"/"Berhenti"/"Keluar", kotak log, dan ikon tray (menutup jendela menyembunyikan ke tray, bukan mematikan agent). `--no-ui` mengembalikan perilaku konsol. Bukti ter-link: `XyDeskRemoteHost.exe` 253 KB → **698 KB**, standalone 817 KB → **956 KB**.
- **Layar login Windows**: agent melaporkan keadaan sesi (`WTSQuerySessionInformation`) dan nama desktop input (`OpenInputDesktop`); kalau desktop input `Winlogon`, UI menyatakan layar login/kunci aktif dan bahwa jalur masuknya RDP dari HP. Ini **hanya mendeteksi dan melaporkan** — agent berjalan di sesi pengguna biasa dan tidak membuka kunci, tidak menyuntik kredensial, dan tidak memanggil `SendSAS`.
- Baris link exe host di job Gate **dan** Release diberi `-luser32 -lgdi32 -lshell32 -lwtsapi32`; sebelumnya baris exe tidak menautkan `user32` sama sekali.
- **Empat kegagalan gate diperbaiki di branch ini**, semuanya dari edit mekanis: (1) impor `getValue` hilang di `XyGlass.kt`; (2) `PanelTabTiles` disisipkan di antara `@Composable` dan fungsinya sehingga anotasi tertukar; (3) `FeedbackDestination` terkena kesalahan yang sama persis, plus `areSizeAndRateSupported` butuh `Double` bukan `Int`; (4) parameter `openKey`/`onOpenKey` tersisip setelah `) {` karena nomor baris 1-based dipakai sebagai indeks 0-based, lalu `openSection` dideklarasikan di `SessionControls` padahal call site ada di `SessionPanel`. Kesalahan (2) dan (3) identik, jadi sekarang ada pemeriksaan: setiap baris `@Composable` harus diikuti deklarasi fungsi. Judul seksi yang terduplikasi akibat indeks `-1` di pengurai koma **tidak tertangkap** penghitung kurung kurawal — hanya terlihat dengan membaca 15 kepala `section()` satu per satu.
- Tiga penyesuaian header mingw-w64 vs SDK Microsoft: enumerator `WTS_CONNECTSTATE_CLASS` tanpa akhiran `State` (switch memakai angka karena nilainya stabil di ABI), `PWTS_CONNECTSTATE_CLASS` tidak ada, dan `IDI_APPLICATION`/`IDC_ARROW` melebar ke `MAKEINTRESOURCEA` karena build tidak mendefinisikan `UNICODE`.
- Yang belum: uji runtime di perangkat Android dan host Windows nyata, pengukuran latensi, dan Worker portal masih belum di-deploy. Selama rilis masih draft, aset tidak bisa diunduh publik sehingga portal tetap menyajikan v1.0.2.

## 2026-10-07 — kontrol musik penuh + playlist + pustaka, dan panel transfer file HP ↔ PC

- **Kontrol pemutar jadi lengkap**, bukan cuma putar/jeda. `SpotifyMediaBridge` sekarang menerbitkan posisi + durasi + kecepatan, antrian lagu, mode acak/ulang, dan daftar semua sesi media yang bisa dipilih pengguna (sebelumnya sumber dipilih otomatis dan tidak bisa diganti — inilah sebabnya "connect SoundCloud dll" tidak mungkin). Metode baru: `seekTo`, `seekBy`, `stop`, `setShuffle`, `cycleRepeat`, `playQueueItem`, `selectSource`, `playFromMediaId`.
- **Scrub bar bisa digeser.** `PlaybackState.position` adalah posisi saat pemutar terakhir melapor, jadi selama lagu berjalan posisinya diekstrapolasi dari `lastPositionUpdateTime` + `playbackSpeed` dan diterbitkan lewat `StateFlow` terpisah tiap 500 ms (`positionMs`). Dipisah dari `playback` supaya pembaruan jam tidak ikut mengecilkan bitmap sampul dua kali per detik. `seekTo` dikirim hanya saat jari lepas — mengirimnya tiap gerakan membanjiri pemutar.
- **Temuan penting: acak & mode ulang tidak ada di SDK publik.** `MediaController.getShuffleMode/getRepeatMode`, `TransportControls.setShuffleMode/setRepeatMode`, `PlaybackState.SHUFFLE_MODE_*`/`REPEAT_MODE_*`, dan `MediaController.Callback.onShuffleModeChanged/onRepeatModeChanged` semuanya `@hide` — diverifikasi dengan `javap` langsung terhadap `android.jar` API 35. `getQueueIndex()` juga `@hide`. Karena itu `androidx.media:media:1.7.0` ditambahkan ke `app/build.gradle` dan sebuah `MediaControllerCompat` dibangun dari session token yang sama khusus untuk dua kontrol itu; lagu aktif di antrian dikenali dengan mencocokkan `METADATA_KEY_MEDIA_ID` terhadap `queueItem.description.mediaId`.
- **Pustaka musik** (`MediaLibraryBrowser.kt`) lewat `MediaBrowserService`: layanan ditemukan dari `PackageManager` (bukan daftar aplikasi yang di-hardcode), pohon foldernya dijelajahi apa adanya, dan lagu diputar dengan `playFromMediaId`. `<queries><intent>` untuk action `android.media.browse.MediaBrowserService` ditambahkan ke manifest — tanpa itu, mulai Android 11 daftarnya selalu kosong walau aplikasinya terpasang. Batasannya dinyatakan di UI, tidak disembunyikan: aplikasi boleh menolak koneksi (Spotify tidak membuka browser publik), dan setiap penolakan punya pesan sendiri.
- **Tab panel ke-5: Musik** — Sumber & izin · Kontrol penuh · Antrian/playlist · Pustaka. Tetap memakai pola yang diminta pengguna: grid kotak yang isinya melebar di bawah kotaknya, bukan halaman baru dengan tombol kembali. `PanelSectionGrid`/`PanelSectionScope`/`PanelSection`/`PanelHint` dinaikkan dari `private` ke `internal` supaya bisa dipakai dari file lain.
- **Panel transfer file HP ↔ PC** di tab Sesi. Memakai kanal RDPDR yang sudah ada (`RdpUri` memetakan folder HP jadi drive `XyDesk`), jadi tidak ada protokol atau server baru — yang sebelumnya hilang hanya UI-nya. Kirim: pemilih dokumen Android → salin ke folder bersama dengan progres. Terima: daftar file yang ditaruh PC → simpan ke `Download/XyDesk` lewat MediaStore (API 29+, dengan handshake `IS_PENDING` dan penghapusan baris kalau gagal), atau dibagikan langsung dari folder bersama di Android 7–9. Status drive dibaca ulang tiap seksi dibuka karena `localDrive` adalah opsi per-perangkat dan mati di PC Connect; kedua keadaan itu dijelaskan, bukan disembunyikan.
- **Nama file kiriman dibersihkan.** `FileTransferPlan` mengganti karakter yang ditolak NTFS (`\ / : * ? " < > |` + karakter kontrol), membuang titik di akhir, dan mencari slot kosong (`foto.jpg` → `foto (2).jpg`) dengan perbandingan tanpa memedulikan besar-kecil huruf karena NTFS begitu. Tanpa ini salinan gagal diam-diam atau menimpa file pengguna di PC.
- **Verifikasi lokal yang sebenarnya, bukan CI saja.** Kotlin compiler 2.2.21 + plugin Compose yang cocok + `android.jar` API 35 + AAR Compose 1.11.2/Material3 1.4.0/androidx.media 1.7.0 (versi dibaca dari `compose-bom:2026.05.01` milik proyek, bukan dikira-kira) diunduh, lalu seluruh sumber modul dikompilasi. Compile inilah yang menemukan ketiga `@hide` di atas. Sisa error yang tertinggal hanya di baris `SessionControls.kt` yang tidak diubah ronde ini (183, 236–243) karena classpath ad-hoc tidak memuat kelas `androidx.lifecycle` — bukan cacat kode. `MediaTransportMath` dan `FileTransferPlan` ditulis sebagai logika murni dan 17 test JUnit-nya dijalankan langsung di JVM: **OK (17 tests)**.
- Enam ikon baru digambar sendiri mengikuti set garis yang ada: `Shuffle`, `Repeat`, `RepeatOne`, `Queue`, `Rewind`, `Forward`, `Stop`, `Library`, `Refresh`, `Upload`, `Download`, `File`.
- Yang belum: semua ini **terbukti terkompilasi, belum teruji di perangkat nyata** — perilaku tiap pemutar (Spotify, SoundCloud, pemutar lokal) terhadap antrian, pustaka, dan acak/ulang berbeda-beda dan hanya bisa dipastikan dengan mencoba di HP. Transfer file juga belum dicoba terhadap host Windows nyata.
- **Gate pertama ronde ini merah** ([run 37650262743](https://github.com/xykal/XyDesk-Remote/actions/runs/37650262743)): `:app:compileDebugKotlin` gagal dengan 5 error, semuanya di `SessionFileTransfer.kt` — `root.listFiles()` padahal `root` bertipe `TransferRoot` (harusnya `root.dir`), dan `copyWithProgress(job, ...)` padahal fungsinya menerima `jobId: Long`. Penyebab lolosnya bukan kompilasi lokal yang tidak jalan, tapi **filter grep saya sendiri**: daftar file yang diperiksa memuat `SessionTransferPanel` dan `FileTransferPlan` namun tidak `SessionFileTransfer`, jadi error yang sudah ada di log tersaring keluar. Prosedurnya diubah: compile lokal kini mencetak jumlah error per file tanpa filter, lalu tiap file ronde ini di-assert nol.
- Sekalian dibuktikan bahwa 11 error `SessionControls.kt` di compile lokal memang kekurangan jar, bukan cacat kode: setelah `lifecycle-common-jvm-2.9.4.jar` ditambahkan ke classpath, total error turun 1691 → 1386 dan `SessionControls.kt` jadi 0. Kesepuluh file ronde ini semuanya 0 error.

## 2026-10-07 — v1.1.1: draft rilis berisi ronde UI 3-4 + musik penuh + transfer file

- `VERSION_NAME` 1.1.0 → **1.1.1**, `VERSION_CODE` 62 → **63**. Draft `v1.1.0` dibiarkan utuh supaya masih bisa dibandingkan; draft baru dibangun dari `main` yang sudah memuat: panel inline-expand + kaca metalik, paritas preview gamepad + 18 tombol, pil scroll + scroll halus, screenshot ke `Pictures/XyDesk`, kontrol musik penuh + playlist + pustaka, dan panel transfer file HP ↔ PC.

## 2026-10-07 — kanal distribusi, notifikasi sesi, dan README yang dirapikan

- **`ui/AppChannel.kt`** memastikan APK yang berjalan ditandatangani penerbit resmi; kalau tidak, `AppChannelGate` menutup seluruh UI dan hanya menyisakan dua tombol (buka situs resmi / tutup app). Dipasang sebagai anak terakhir `Box` akar di `XyDeskHomeActivity` dan `XyDeskSessionActivity`, `zIndex(100f)`, menyerap semua sentuhan, dan menelan tombol kembali.
- Aturan amannya: **memblokir hanya bila sidik jari benar-benar terbaca DAN berbeda**. Kalau tidak terbaca sama sekali (ROM aneh, API berubah), app tetap jalan — pengguna sah tidak boleh terkunci karena pembacaan gagal. Sidik jari diverifikasi sendiri dari APK rilis v1.1.1 (`META-INF/XYDESK-R.RSA` → sertifikat `CN=XyDesk Remote Release`, SHA-256 `8429f689…ad219652`), cocok dengan `SIGNATURE-VERIFICATION.txt` yang sudah diterbitkan — jadi nilai itu bukan bocoran baru.
- Pengecualian debug dibaca dari `ApplicationInfo.FLAG_DEBUGGABLE` APK yang sedang berjalan, bukan konstanta waktu kompilasi, supaya `assembleDebug` di CI dan pengembangan lokal tidak ikut terkunci.
- **Jujur soal batasnya:** pemeriksaan sisi klien selalu bisa dibongkar orang yang serius. Ini pencegah dan pelindung pengguna (APK modifikasi klien RDP memang vektor pencurian kredensial), bukan DRM yang tidak tembus.
- Sesuai permintaan, mekanismenya **tidak didokumentasikan** di README/AGENTS — yang disembunyikan adalah penjelasannya, bukan perilakunya: pengguna tetap melihat pesan yang jelas.
- **Notifikasi sesi dirombak** (`XySessionService`): `setColorized(true)` + warna aksen (hanya berlaku untuk foreground service, jadi kartu bawaan Android yang putih/abu tidak lagi muncul), `largeIcon` dari `xy_mark_white.png` (PNG, bukan vektor — `BitmapFactory` tidak membaca vektor), kronometer lama sesi, subteks versi, `BigTextStyle`, dan `VISIBILITY_PRIVATE` + `publicVersion` supaya host yang sedang disambungkan tidak tampil di layar kunci.
- **README ditulis ulang**: fokus ke apa fungsi app dan langkah pemakaian (PC → pasang → tambah perangkat → sambungkan → tabel "yang mau dilakukan"), plus fitur terbaru (transfer file, musik, rekaman, gamepad) dan troubleshooting singkat. Dwibahasa dipertahankan.
- PR #10 (banner v1.0.3) dibiarkan terbuka — asetnya untuk versi yang tidak pernah rilis; penggantinya dibuat tanpa nomor versi supaya cocok untuk rilis mana pun.
- **Banner pembaruan tanpa nomor versi** dibuat (pilihan pengguna dari 2 opsi): monokrom metalik mengikuti tema app — HP menampilkan sesi Windows terhubung ke PC lewat benang cahaya, di atas lantai grid gelap. Dipotong/pad jadi tiga rasio: `banner-update.png` (master 2048), `banner-update-2x1.png/jpg` (1024×512 untuk header rilis & OG), `banner-update-square.png` (1024×1024, dipad bukan dipotong supaya HP & PC tidak kepotong). Tidak ada angka versi supaya cocok untuk rilis mana pun.

## Ronde 5b — telemetri RTT live + redesain portal (2026-10-07)

- **Latensi (#3):** ternyata pill telemetri sudah ada, tetapi sampling RTT-nya memakai `InetAddress.isReachable` yang di Android tanpa root hampir selalu gagal (ICMP dilarang, fallback port 7 tertutup) sehingga angka membeku pada probe saat konek. Diganti probe TCP nyata (handshake = 1 RTT) ke `host:port` layanan tiap 5 detik, dihaluskan EMA lewat objek murni baru `RttSmoother` (+ `RttSmootherTest`). Target <100 ms tetap tergantung jaringan pengguna; kini angkanya hidup dan jujur.
- **Portal (#5):** `web/index.html` — gambar hero AI monokrom (`hero-xydesk.jpg`, satu keluarga dengan banner) sebagai latar intro dengan gradasi `rgba(8,8,10,…) -> var(--bg)` menyatu ke konten; strip statistik **tepat di bawah CTA** (Total Unduhan · User Online · Rilis Publik) diisi `/api/stats` + `/api/active-sessions` (hanya rilis publik; draft tidak dihitung); palet dark disamakan dengan APK (`#08080A` / `#F2F2F4`); tile glass responsif (1 kolom <640px); OG image diganti banner 2:1 baru (1024×512).
- **Versi** dinaikkan ke **1.1.2 (64)** untuk draft pengujian berikutnya yang memuat anti-bajak + notifikasi + telemetri baru.

### Status akhir ronde 5b
- Gate pertama untuk `15f6b1c` **gagal** karena uji `RttSmootherTest` saya sendiri salah (ekspektasi `next(20,-5)==1` padahal EMA=13; plus risiko truncation float32). Diperbaiki: `roundToInt` di `RttSmoother` + uji dikoreksi, diverifikasi lokal `OK (5 tests)` sebelum push ulang. Gate + Debug per-ABI untuk `12f8a90` **success**.
- Tag `v1.1.2` → draft release id `406243857` (10 aset, nama `XyDesk-Remote-<abi>.apk`), **tetap draft** sampai pengguna selesai testing. Diverifikasi dari APK armv7: string popup anti-bajak ("BAJAK", "JANGAN GITU") dan kanal notifikasi `xydesk_sesi_aktif` ada di dex; manifest `1.1.2`. Nama kelas tak terlihat karena R8 (minify release) — wajar.
- Portal live `rdp.xydesk.my.id` (Version `69bbc44a…`): hero + strip statistik berfungsi; `/api/stats` tadinya 503 karena secret `GH_TOKEN` worker kedaluwarsa — diperbarui via `wrangler secret put`; kini live: 527 unduhan / 3 rilis publik / 1 sesi aktif saat dicek.

## Ronde 6 — UI monokrom, lega, popup halus, tahan DPI (2026-10-09)
- **Monokrom penuh (chrome app):** `XyOk/XyWarn/XyDanger` + error/tertiary kedua skema jadi grayscale; pill telemetri (RTT hijau/kuning/merah → putih/abu bertingkat); tab aktif mint → putih-hitam terbalik; tint kebiruan `0xFFF4F8FC`/`0xB8DCE6F2` → abu netral. Wallpaper preset (meniru Win/macOS) sengaja dibiarkan berwarna karena konten, bukan chrome. HUD tidak disentuh sesuai aturan lama.
- **Popup halus:** `XyOverlay` kini animasi masuk/keluar (scale 0.88→1 + fade, scrim menggelap) dengan `origin` = posisi tombol pemicu (TransformOrigin); `SessionPanel` membesar dari handle kanan-atas; seksi grid melar mulus via `animateContentSize`.
- **Lebih lega:** padding/spasi dinaikkan di overlay (16→20, sudut 8→16, maxWidth 380→440), panel (16/12→20/16, spacedBy 9→12, maxWidth 720→760), grid seksi (10→12, isi 12→16/14, 7→10), dialog heightIn 380→420.
- **DPI:** semua ukuran dp/sp (density-independent); pill telemetri kolom vertikal sehingga tidak overflow di layar sempit; panel/overlay berbasis weight/fillMax+widthIn sehingga mengikuti lebar layar.

## Ronde 7 — penarikan v1.1.2 + perbaikan kritis (2026-10-09)
- **Release v1.1.2 DIHAPUS** atas perintah pengguna (bug blocker): popup anti-bajak muncul justru pada build resmi. Akar: pembanding sidik jari memakai `byte.toInt()` yang memperluas tanda (0x84 → 0xFFFFFF84) sehingga XOR tidak pernah 0. Logika diekstrak ke `SignerMark` (murni) + `SignerMarkTest` (4 uji, dijalankan lokal OK) — uji ini gagal pada kode lama.
- **Insiden revert shallow:** `git revert` pada clone `--depth 1` yang parentnya tidak ada menganggap parent = pohon kosong → commit revert menghapus SELURUH repo dan sempat ter-push + ter-deploy. Dipulihkan lewat revert-of-revert, lalu portal dikembalikan bedah ke keadaan v1.0.2 (`git checkout bc08999 -- web PROGRESS.md`). Aturan baru: jangan pernah `git revert` commit yang parentnya shallow; `fetch --depth` dulu.
- **Panel lebih fokus:** tab MUSIK dikeluarkan dari panel sesi (pemutar punya popup terapung sendiri yang lengkap); `MusicTab` dibiarkan untuk pemutar terapung.
- **Stik bisa dibaca:** knob joystick HUD kini menampilkan label arah sesuai mode (W/A/S/D atau panah) di tepinya.
- **Rounded full:** sudut ubin kaca 14→20, overlay 16→24, panel 22→28.
- **Ditunda (butuh keputusan/log):** host agent 1-exe + stream mandiri; sumber musik online (SoundCloud/yt-dlp — perlu metode yang aman & legal); keluhan install Android 7/16 dan crash transfer file butuh teks error/logcat persis dari tester.

## Ronde 8 — paksa sembunyikan status bar (keluhan tester)
- Masalah: di beberapa HP, sesudah notification shade ditarik lalu dilepas, status bar (jam/baterai) tetap tampil dan tidak sembunyi lagi.
- Perbaikan di `XyDeskSessionActivity`: `onResume` & `onWindowFocusChanged(true)` kini selalu memanggil `hideSystemBars()`; tambahan `installSystemBarsWatch()` — listener `WindowInsetsCompat` yang mendeteksi bar terlihat saat activity memegang fokus lalu menjadwalkan sembunyi paksa 700 ms (tidak melawan saat shade benar-benar terbuka karena fokus lepas).

## Ronde 7b — mode tuntas: fitur & perbaikan besar (2026-10-09)
- **Transfer file anti-crash:** callback picker & tombol Segarkan di `SessionTransferPanel` kini membungkus seluruh alur dengan `runCatching`; kegagalan provider/izin muncul sebagai pesan, bukan mematikan app.
- **Kursor lebih halus:** `smoothPointerDelta` (EMA, fungsi murni + 4 uji unit) dipasang di `movePointer` sebelum sensitivitas/akselerasi — keluhan sapuan "muter-muter" di game.
- **Preset "Gamepad Lengkap":** satu ketuk memberi dua stik analog, D-Pad, A/B/X/Y, L1/R1/L2/R2, Start & Select (dipetakan sama dengan gamepad fisik). Anggota enum ditambahkan DI AKHIR supaya ordinal profil tersimpan tidak bergeser.
- **Wallpaper tidak lagi hitam:** scrim Win11 diringankan (0x59/0x26/0x8C → 0x33/0x14/0x66), gradien Win10 & Netral dinaikkan ke biru/graft yang terbaca.
- **Menu panel lebih bersih:** tiap tab kini hanya menampilkan seksi inti; 6 seksi jarang dipakai pindah ke ubin "Lanjutan (N seksi lagi)" yang bisa dibuka/tutup di tempat.
- **Musik online (SoundCloud):** pencarian + pemutaran trek publik langsung di app — scraper `client_id` ala pemutar sumber terbuka, endpoint api-v2, stream progresif MP3; pemutar bawaan `MediaPlayer` + MediaSessionCompat + notifikasi MediaStyle (kontrol kunci layar). UI di pemutar mengambang sesi (dan tab Musik bila diaktifkan lagi). Parser murni + 9 uji unit (`org.json` ditambahkan khusus test).
- **Fitur khas: Riwayat clipboard** — 10 teks terakhir yang disalin di HP, persisten, ketuk untuk menyalin ulang (kanal clipboard sesi meneruskannya ke PC otomatis), hapus per entri atau semua.

## Ronde 9 — persiapan review live (2026-10-10)
- **PR #10 DITUTUP** (review): banner ungu + glow neon melanggar aturan monokrom ronde 6 dan berlabel versi kedaluwarsa "v1.0.3"; tidak ada kode yang merujuk asetnya. Alasan ditulis sebagai komentar PR.
- **Statistik:** label "UI/s" diganti **FPS** (laju penyegaran tampilan app) hanya di UI app; portal tidak disentuh.
- **Editor kontrol lebih lengkap:** tombol kombinasi kini bisa diracik bebas (Ctrl/Shift/Alt/Win dicampur + tombol utama dari huruf/F/angka/simbol/numpad, label otomatis); tombol KEY bisa ganti tombol utama langsung dari editor.
- **Codec & koneksi lemah:** dijawab — encode bukan cuma AVC420: preset stream (Otomatis/Responsif AVC420/Seimbang AVC444/Visual Tajam/Hemat Kuota/Kustom) sudah ada di pengaturan perangkat + deteksi seluler otomatis; telemetri menampilkan codec aktif.
- **Versi 1.1.3 (65):** build berikutnya release-draft resmi (workflow memaksa draft:true), bukan debug.

## Ronde 10 — popup tidak lagi mengunci + migrasi domain + unduhan unik (2026-10-10)
- **Gate anti-bajak jadi nag-mode:** pemilik dua kali terkunci oleh pengunci penuh (perangkat/launcher tertentu menandatangani ulang APK saat pasang — app clone/dual-apps/ROM khusus — sehingga sidik jari runtime berbeda walau file resmi). Popup kini menyindir + menawarkan unduhan resmi tapi BISA DITUTUP ("Aku pengguna sah — lanjutkan"), dan mencetak diagnostik (potongan sidik jari terbaca + paket pemasang) supaya kasus berikutnya terdiagnosis dari satu screenshot.
- **Domain baru:** worker kini melayani juga `xydeskremote.biz.id`, `www.`, `api.`, `wss.`, `cpc.xydeskremote.biz.id` (zona sudah aktif di Cloudflare; record dibuat otomatis oleh custom_domain). App & halaman web pindah ke kanonikal baru; domain lama tetap hidup agar perangkat lama tidak putus.
- **robots.txt** diperbarui (Sitemap kanonikal baru).
- **Unduhan unik:** KV `UNIQUE_DOWNLOADS` mencatat hash(IP+UA+file) sekali per pengunduh; `/api/stats` menambah `unique_apk_downloaders` dan portal menampilkan angka unik itu sebagai Total Unduhan.
- **Konstanta app:** OFFICIAL_URL→www baru; status/jokes/session→api baru; PcSignalClient DEFAULT_BASE_URL→cpc baru.

## Ronde 11 — Create RDP dari app (XyRDP) + AdMob unlock (2026-10-10)
- **Fitur "RDP Gratis 6 Jam"** di layar perangkat: unlock = 1 iklan rewarded AdMob (unit milik pemilik) + gabung saluran; lalu tempel PAT GitHub (disimpan TERENKRIPSI di CredentialVault AES-256-GCM); dashboard per-user: fork template `xykal/XyRDP` ke akun pengguna, enable workflow via API, dispatch sesi (akses=keduanya: RustDesk + tunnel IP tanpa Tailscale), polling `out/rdp-status.json` branch `status`, tampil alamat host:port + selftest/uji-luar + cadangan RustDesk/Tailscale, salin, lalu buka formulir koneksi RDP biasa. Tidak ada akun pusat — semua di akun pengguna masing-masing.
- Parser status murni + 3 uji unit.
- Masuk lewat baris "RDP Gratis 6 Jam" di bubble tambah perangkat.

## Rilis v1.1.4 (66) — build untuk live review (2026-10-10)
- Berisi: gate nag-mode + diagnostik, create-RDP XyRDP + AdMob unlock, domain biz.id, unduhan unik, musik online, riwayat clipboard, gamepad lengkap, editor kombinasi bebas, FPS, smoothing, wallpaper, menu inti.
- Dibangun hanya setelah konfirmasi eksplisit pemilik ("gasken").

## v1.1.5 (67) — nag kanal ditutup permanen (2026-10-10)
- PENYEBAB keluhan pemilik: keputusan "Aku pengguna sah — lanjutkan" hanya disimpan
  di memori (remember), jadi nag muncul lagi setiap buka app walau sudah ditutup.
- PERBAIKAN: keputusan disimpan permanen di SharedPreferences ("xydesk.channel").
  Sekali ditutup, tidak pernah muncul lagi (kecuali data app dihapus).
- Diagnostik tetap dicetak di kartu: sidik jari runtime + pemasang. Kalau sidik jari
  != 8429f689… berarti perangkat menandatangani ulang saat pasang (app clone/dual
  apps/ROM) — bukan file APK-nya yang salah.

## Wallpaper layar sesi tidak lagi hitam (2026-10-10)
- Keluhan pemilik: "wallpaper hitam". Akar masalah: ConnectingScreen dan
  DisconnectedScreen menggambar wallpaper dengan lapisan dim 0.78/0.84
  (hampir pekat). Ditipiskan ke 0.35/0.40 — wallpaper terbaca jelas,
  teks putih tetap kontras.

## Ronde 12 — UI matte total + gamepad WASD + panel lega (2026-10-10)
Keluhan pemilik setelah uji v1.1.6 dan semua jawabannya:
1. "Jangan pakai glassmorphism/liquid glass, bikin pusing" -> xyGlass ditulis ulang
   jadi permukaan matte (solid 0.97 + tepi 1dp), kilau/sapuan/bevel dihapus;
   popup musik ikut diratakan; blur permukaan saat panel terbuka dihapus.
2. "Popup menu sesak" -> ubin panel dikecilkan (padding 14->10, ikon 38->30,
   jarak 11->9; baris daftar 12/9).
3. "Joystick kok gerakin mouse? mana tulisan WASD?" -> default stick mode pindah
   POINTER -> WASD (kunci SP di-v2 supaya instalasi lama ikut pindah); huruf
   W/A/S/D di tepi stik sudah otomatis tampil di mode WASD.
4. "Ikon gamepad jangan punya orang" -> XyIcons.Gamepad digambar sendiri
   (badan+grip+D-Pad+2 tombol), dipakai untuk kontrol joystick.
5. "R1/R2 dll bikin kayak di stick" -> pelat L1/R1 jadi bentuk bumper (lebar,
   sudut atas membulat) dan L2/R2 jadi trigger (tinggi, sudut bawah membulat).
6. "Fitur create-RDP mana?" -> kartu tetap "RDP Gratis 6 Jam" di puncak daftar
   perangkat (sebelumnya tersembunyi di dalam bubble "+").

## Ronde 13 — UI bersih, gamepad mandiri, musik ungu, rail minim (2026-10-10)
1. Unlock RDP Gratis sementara = gabung saluran saja (iklan AdMob belum mengisi;
   adWatched tetap tersimpan untuk diaktifkan lagi nanti).
2. Toggle: area sentuh 52x38dp (track tetap 46x28) — lebih presisi.
3. Notifikasi print FreeRDP + pemutar musik kini berlogo XyDesk (xydesk_notif /
   ic_launcher_monochrome), bukan ikon sistem/pengembang lain.
4. Seksi "Tampilan" (setting resolusi/orientasi/skala per perangkat) dibuang dari
   drawer — kebanyakan settingan bikin bingung; pengaturan tetap ada di panel sesi.
5. Rail sesi diminimalkan: hanya panah-atas (bubble popup: scroll, overlay,
   monitor, BERANDA, putuskan) + keyboard di paling bawah.
6. "Ke beranda" baru: sesi TIDAK diputus (moveTaskToBack), buka lagi dari kartu
   Sesi aktif/notifikasi.
7. Gamepad preset kini rupa sendiri (pelat gelap solid khas gamepad, bukan rupa
   overlay), stik kiri WASD berlabel + diagonal, stik kanan = mouse.
8. Musik: liquid glass UNGU glossy dipertahankan sebagai pengecualian resmi
   (xyPurpleGlass) di pemutar mengambang + kartu pencarian; tab Player/Antrian/
   Pustaka + backend SoundCloud yang sudah tersambung tetap.
9. Teks panel dipangkas (subtitle gamepad, hint kirim teks, dll).
CATATAN: build/tag DITAHAN — menunggu konfirmasi pemilik.

## Ronde 14 — akun komunitas + feed lengkap (2026-10-10)
- Worker: reaksi diperluas 8 -> 24 emoji (superset lama), komentar per post
  (maks 50, 5 terbaru ikut di feed), display name per pemasangan (rute /me
  GET/PUT + /api/jokes/me), author_name + comments_total di payload feed,
  prune ikut membersihkan komentar. 42 test hijau.
- App: CommunityJokesApi (komentar, /me, nama tampilan, ID ramah XY-XXXXXX,
  kode pemulihan = client id, cache feed lokal), FunHubCard cache-first
  (tanpa layar "Memuat…"), reaksi cepat 6 + tombol "+" grid 24 emoji tanpa
  scroll, UI komentar (buka/tulis/kirim), nama author tampil.
- Profil: kartu akun — tamu otomatis ber-ID, login GitHub (PAT RDP Gratis)
  otomatis jadi nama, nama bisa dikustom (maks 24), salin kode pemulihan,
  pulihkan akun setelah pasang ulang (nama lama ikut kembali dari server).
CATATAN: build/tag tetap DITAHAN menunggu konfirmasi.

## Ronde 15 — kredensial diingat per host (2026-10-10)
- Permintaan pemilik: sekali input password untuk satu host, koneksi berikutnya
  ke host+user yang sama otomatis terisi; hilang hanya jika data app dihapus /
  app di-uninstall.
- CredentialVault.hostCredKey(host, port, user) = kunci baru "hostcred:...".
- SessionsRepository: setiap simpan dengan password ikut menulis hostcred;
  load fallback ke hostcred bila password per-id tidak ada.
- AddDeviceScreen: prefill saat edit + auto-isi saat host/user diketik dan
  cocok dengan kredensial tersimpan.
- SoundCloud: tetap memakai client_id web + api-v2 (sudah tersambung, tanpa
  akun). Skrip sc-api-auth.mjs = registrasi aplikasi OAuth resmi; tidak
  dibutuhkan untuk pemutaran trek publik.

## Ronde 16 — logo baru, SEO, menu drawer, combo bebas, notif tombol (2026-10-10)
- Logo pemilik (monitor+kursor+tap) dipakai di SEMUA: adaptive icon foreground,
  legacy mipmap per density, monochrome notifikasi, freeRDPCore notif, web
  favicon + OG image 1200x630; warna latar adaptive = #10041A.
- Tiga seksi "visual Windows" (ukuran tampilan, skala DPI, resolusi) DIHAPUS
  dari panel sesi atas permintaan pemilik (diduga biang wallpaper hitam).
- Notifikasi sesi: tombol jadi 4 — Buka, Beranda (tanpa putus), Musik
  (play/pause), Putuskan.
- Menu sesi 2 varian: popup tengah / drawer samping (chip toggle di header
  panel, tersimpan di AppPrefs.menuDrawer).
- Editor kombinasi BEBAS: 2-5 tombol apa saja (modifier+huruf+F+numpad) bebas
  urutan; chip terpilih bisa dihapus.
- Web: favicon/logo baru, meta keywords, OG 1200x630, polesan responsif
  (fluid type, hover lift, focus-visible, reduced motion), sitemap lastmod baru.
CATATAN: build APK tetap DITAHAN menunggu konfirmasi.

## Ronde 17 — input fisik & anti mouse-double (2026-10-10)
- Engine input fisik SUDAH ada: keyboard OTG/BT (jalur InputSink), mouse
  (hover/click/scroll di SessionView), gamepad (dispatchKeyEvent +
  dispatchGenericMotionEvent), deteksi perangkat + banner.
- "Mouse double" = dua kursor: overlay kursor lokal app + kursor remote di
  stream saat mouse fisik terpasang. FIX: state externalMouseConnectedState;
  overlay pointer disembunyikan otomatis saat mouse eksternal terdeteksi.

## Ronde 18 — GAS rilis (2026-10-10)
- MIN_API 23 (Android 6) — semua API 26+ sudah di-guard; compile/target 37
  (Android 16) tetap.
- Versi 1.1.8 (code 69).
- Nama aset rilis: XyDesk-Remote32bit.apk / XyDesk-Remote64bit.apk /
  XyDesk-Remote64bit-x86.apk; workflow rename setelah verifikasi tanda
  tangan; ci/verify_release_apk_hashes.py & alias worker mengikuti nama baru
  (BIT_NAMES, urutan 64bit-x86 dicek lebih dulu).
- CreateRdpScreen.kt dead code dihapus (digantikan RdpFreeScreen);
  res/drawable-nodpi/xy_mark_black.png tak terpakai dihapus.
- OG banner baru 1200x630: gradient radial, orbs bokeh, streaks, partikel,
  cincin glow, logo transparan, tipografi dua warna, pill domain.
- Web: popup hitung mundur "Rilis Update Besok" — efek shine, orbs pulse,
  partikel melayang, kartu glow, countdown jam/menit/detik, target tetap
  2026-10-11T09:00+07:00, dismissal persist di localStorage, hormat
  prefers-reduced-motion.
- EXE gabungan/UI/streaming sendiri: sudah ada sejak dulu (main_host Win32
  UI + tray, XyDeskRemoteHost-Standalone.exe single-binary, xydesk_quic
  streaming + core-pc-stream di Android) — rilis = uji perangkat nyata.
- Fix minSdk 23 native: rdpecam NDK Camera2 (API 24+) kini dimuat runtime
  lewat dlopen (camera2ndk_dyn.h); link langsung camera2ndk/mediandk
  dihapus; kanal mati aman di perangkat Android 6.

## Ronde 20 — Layout grid, turbo, telemetri (2026-10-12)
- Editor tata letak HUD/gamepad: snap grid 12dp + magnet sejajar ke tepi/tengah
  tombol lain + garis bantu ungu putus-putus saat digeser (HudControlMath:
  snapHudPosition/hudAlignmentGuides/hudPlateSizeDp, + unit test).
- Turbo tombol HUD: opsi "tahan = ketik berulang" (KEY/COMBO/MACRO, 100ms),
  ikut tersimpan di JSON layout.
- Pill telemetri: kolom TRANSPORT (UDP putih / TCP kuning).
- XyPadLayout dikonfirmasi kode lawas tak terpakai (gamepad = HudKey pad=true)
  — snap cukup di HudKeyLayer.
- Tanpa build/tag sesuai instruksi pemilik; hanya gate compile.

## Ronde 24 — Sapu bersih sisa kode musik + verifikasi rekaman audio+kursor (2026-10-14)
- Audit permintaan pemilik: (1) rekaman PC + audio & kursor, (2) player musik
  dihapus. Dua-duanya sudah dikerjakan sebelumnya: rekaman audio+kursor
  ter-wire penuh (QuicAudioBridge.tap -> SessionManager.setAudioPcmTap ->
  RemoteAudioRecorder; pointerBox di-update tiap frame; commits 12db8a5,
  1b78190, 6e36e05), musik dihapus total 2026-10-13 (5d2cc5a). Tersisa
  jejak mati yang this-pass bersihkan:
- Dihapus: 4 cabang ikon seksi mati di SessionControls (sumber-musik,
  kontrol-pemutar, antrian-musik, pustaka-musik); 8 ikon XyIcons tanpa
  pemakaian (Shuffle, Repeat, RepeatOne, Queue, Rewind, Forward, Stop,
  Library); Modifier.xyPurpleGlass (dibuat khusus pemutar musik, 0
  pemakaian); komentar basi XyGlass; variabel spotifyX di XyPadLayoutTest
  -> centerTopX. Refresh/Users/Sliders dipertahankan (masih dipakai).
- README: baris "Kontrol musik HP" + bullet Media dihapus; daftar tab panel
  dikoreksi jadi Layar/Input/Tombol/Sesi; baris perekaman kini menyebut
  audio PC + kursor ikut terekam.
- Tanpa build/tag; hanya gate compile. Issue #15 B.1 (player musik) dan
  B.3 (rekaman) ditutup lewat komentar issue — B.1 selesai lewat keputusan
  penghapusan, B.3 sudah implement.

## Ronde 25 — Tahap 1 (issue #15 B.4): sinkronkan portal web ke rilis publik terbaru (2026-10-14)
- Rilis publik terbaru yang bisa ditaut = v1.1.8 (v1.1.12 masih DRAFT, asetnya
  404 untuk publik). Worker live sebenarnya sudah dinamis (API GitHub rilis
  terpublikasi, cache 5 menit) — yang diperbaiki adalah fallback + teks statis:
- release-state.json (fallback saat API GitHub tidak bisa dihubungi): v1.0.2 ->
  v1.1.8 lengkap (tag, SHA sumber 853d01b, published_at, app_build 1.1.8/69,
  8 aset dengan sha256 dari API GitHub, checksums_url v1.1.8, minimum_android
  6.0/API 23 sesuai minSdk 23 sejak v1.1.8).
- host.html: tautan catatan rilis v1.0.2 -> v1.1.8.
- index.html: "Android 7+ · tiga ABI" -> "Android 6+"; ukuran kartu APK basi
  (84.4/70.4/89.8 MB = ukuran v1.0.2) -> ukuran v1.1.8 (99.0/79.5/108.3 MB,
  ID+EN); default i18n versi pra-render dinamis v1.0.2 (53) -> v1.1.8 (69).
- Blok ci di release-state.json kini jujur: tidak ada run CI tag terpisah yang
  tercatat untuk v1.1.8, tautan menunjuk halaman rilisnya.
- 48 test Worker lulus lokal (node --test). TANPA deploy Worker (manual-only
  via workflow_dispatch DEPLOY) dan TANPA build APK sesuai instruksi pemilik:
  build menyusul setelah semua tahap selesai.

## Ronde 26 — Tahap 2 (issue #15 B.6): instrumentasi latensi pipeline frame (2026-10-14)
- Tahap pengukuran SEBELUM optimasi, sesuai catatan issue: ukur dulu, baru
  putuskan apa yang dioptimasi. Semua di Kotlin, TANPA perubahan native
  (hook JNI waktu decode native menyusul sebagai tahap berikutnya).
- Kelas baru FramePipelineStats (core-rdp, murni + jam disuntik, thread-safe,
  EMA alpha 0.35 seperti RttSmoother) mengukur tiga hal:
  (1) interval antar onGraphicsUpdate di thread RDP (pacing/jitter host),
  (2) latensi pipeline update pertama batch -> flush ke SessionView di main
  thread (termasuk antrean coalescing dirty-rect), (3) kedalaman antrean
  coalescing: rata-rata + puncak update per flush (>1 = UI tertinggal).
  Durasi salin piksel updateGraphics dicatat sebagai proksi beban frame HP.
- TelemetrySample +5 field; SessionManager.frameStats dibaca flow telemetry
  tiap 500ms; SessionSurfaceController mencatat (batchStartNs/pendingUpdates
  di bawah dirtyLock, waktu salin pakai System.nanoTime hanya saat statistik
  terpasang); XyDeskSessionActivity menghubungkan controller <-> manager.
- Pill telemetri +2 baris: PIPELINE (ms; putih <1 vsync, kuning <2, oranye
  lebih) dan ANTREAN "rata-rata / puncak" (kuning saat puncak >1). "Salin
  info teknis" kini menyertakan RTT + interval + latensi pipeline + salin
  frame + antrean supaya laporan bug latensi langsung bisa dianalisis.
- +8 unit test FramePipelineStatsTest (interval EMA, salin sub-ms, latensi
  batch, rata-rata/puncak antrean + reset jendela, EMA lintas snapshot,
  penanda batch 0 dilewati).
- TANPA build APK sesuai instruksi pemilik; hanya gate compile + unit test.

## Ronde 27 — Tahap 3a (issue #12): codec protokol `input` host XyDesk (2026-10-14)
- Audit lintas repo menemukan arsitektur sebenarnya: host streaming produksi
  adalah XyDesk (repo saudara, Rust) — sudah punya injeksi XInput/ViGEm
  (host/src/xinput.rs), signaling WebSocket signal.xydesk.my.id (docs/
  PROTOCOL.md), dan klien referensi android-native. Prototype PC-stream di
  repo ini (XYGP 28 byte + channel `xydesk-control-v1` + Worker /api/pc/rooms)
  TIDAK pernah dipakai host mana pun dan layoutnya beda dari host produksi.
- Keputusan arah (sesuai opsi issue #12 "komponen yang bisa dipakai bersama"):
  XyDesk-Remote menjadi klien KEDUA bagi host XyDesk yang sudah berjalan —
  mengikuti protokol host yang terdokumentasi, tanpa mengubah kontrak lintas
  repo. Prototype XYGP tetap dorman (tidak dihapus) sampai jalur ini terbukti.
- Baru: XyHostInputCodec (core-pc-stream, murni Kotlin) — codec biner data
  channel `input` host: 14 opcode (MOUSE_MOVE_REL/ABS, MOUSE_BUTTON, SCROLL,
  KEY, TEXT, DISPLAY_SELECT, CLIPBOARD_SET/REQ, VIDEO_QUALITY/BITRATE/
  RESOLUTION/FPS, GAMEPAD). Event generasi pertama dipadding 8 byte sesuai
  dokumen host; event modern panjang persis; GAMEPAD = 13 byte (opcode +
  laporan XUSB 12 byte) — bit tombol XyGamepadPacketV1 terverifikasi identik
  XInput jadi XyGamepadState dikonversi 1:1. Teks dipotong di 4096 unit
  UTF-16 tanpa membelah pasangan surrogate (meniru text_batches host).
  Dekoder cermin mengembalikan null untuk pesan rusak (host membuang diam-diam).
- +21 unit test XyHostInputCodecTest termasuk vektor emas byte-level per
  opcode (dikunci terhadap layout input.rs), round-trip semua event, validasi
  rentang, dan pasangan surrogate di batas potong.
- Tahap berikutnya (3b): klien signaling hub (docs/PROTOCOL.md) + layar sesi
  PC-stream (pairing PIN, renderer video, overlay kontrol) di atas codec ini.
- TANPA build APK sesuai instruksi pemilik; hanya gate compile + unit test.

## Ronde 28 — Tahap 3b (issue #12): klien signaling hub produksi (2026-10-14)
- Eksplorasi lintas repo selesai; kontrak lengkap dibaca dari sumber (bukan
  hanya docs): worker.js (/ws /signal-token /host-token /issue, CORS, Origin),
  bound_ticket.js (tiket v2 terikat deviceId+role client, 5 menit), authstore.js
  (POST /auth/guest -> {token, refresh, guest:true}, JWT guest 15 menit,
  rate-limit 429 + retry_in), hub.js 1-474 (hello/welcome, relay dari selalu
  ditimpa server, relayAllowed per arah: pair/offer client->host,
  pair-response/answer host->client, ice/bye/error dua arah; presence maks 50
  dengan jawaban online:[...]; ping -> pong level aplikasi; PAIR_BRAKE rem
  anti-penebak pair-terkunci + retry_in; satu host satu client, ikatan dari
  answer host), dan klien referensi android-native net/Signaling.kt (token via
  query param /ws, bukan header).
- Jalur token CLIENT (yang dulu misteri) kini jelas: guest login ->
  POST /auth/guest {refresh?} -> JWT -> GET /signal-token?id=<deviceId>
  (Bearer) -> tiket "v2." 5 menit -> GET /ws?id&role=client&token. Login penuh
  OTP/Google tidak diperlukan untuk klien remote.
- Baru di core-pc-stream (3 file, tanpa dependensi baru — org.json +
  HttpURLConnection seperti PcSignalClient):
  - XyHubMessages.kt: XyHubOutbound (hello/pair/offer/ice/bye/list/presence/
    ping/pong, encode org.json) + XyHubInbound terketik (welcome/
    pair-response/answer/ice/bye/error/presence/devices/ping/pong/unknown) +
    XyHubIds (host = 9 digit sesuai /host-token, client = [A-Za-z0-9_-]{3,64}
    sesuai worker.js, generateClientId "xyr-"+12 hex) + XyHubProtocol.parseInbound
    yang tangguh (kawat rusak -> null, bukan crash thread transport; tipe masa
    depan -> Unknown).
  - XyHubAuth.kt: guestLogin (baru/perbarui via refresh; toString merahasiakan
    token), signalToken (GET + Bearer; validasi "v2."; 429 membawa retry_in),
    buildWebSocketUrl (https->wss, http-loopback->ws), normalizeBaseUrl
    ketat ala PcSignalClient.
  - XyHubSignalClient.kt: mesin status IDLE->CONNECTING->REGISTERED->CLOSED/
    FAILED di atas XyHubTransport (interface tunggal-sambungan; implementasi
    produksi OkHttp ditambah di app pada tahap UI). Hello otomatis saat onOpen,
    relay ditolak sebelum welcome (cermin "hello wajib sebelum relay"), ping
    dibalas pong otomatis, semua transisi lewat satu kunci (callback datang
    dari thread transport).
- +39 unit test JVM (XyHubMessagesTest vektor emas format kawat dua arah +
  escape; XyHubAuthTest lawan stub ServerSocket dengan query-string, termasuk
  429/retry_in, 401, tiket tak dikenali, validasi baseUrl; XyHubSignalClientTest
  transport palsu: siklus penuh, auto-pong, penerusan terketik, frame rusak
  tidak fatal, close 1000/"selesai", pemotongan presence 50).
- Tahap berikutnya (3c): UI sesi (pairing PIN -> video -> overlay) dialirkan ke
  XyHostInputCodec via XyHubSignalClient + implementasi transport OkHttp.
- TANPA build APK sesuai instruksi pemilik; hanya gate compile + unit test.

## Ronde 29 — Tahap 3c bagian 1 (issue #12): transport produksi + TURN + orkestrator sesi (2026-10-14)
- Kontrak 3c dirampungkan dari sumber: RtcSession.kt (klien referensi
  android-native) — channel `input` dibuat `DataChannel.Init()` default
  (ordered/reliable, bukan unordered seperti prototype), transceiver video
  RECV_ONLY + mic track SEND_RECV nonaktif, STUN stun.cloudflare.com:3478
  ditambah TURN dari Api.turnIce; worker.js — Origin hanya diperiksa untuk
  browser (native tanpa Origin diizinkan), /turn-ice?id=&role=client&token=
  memakai token perangkat sama seperti /ws, respons {iceServers:[{urls:string|
  array, username?, credential?}], ttl} dan 503 turn-not-configured berarti
  lanjut STUN saja.
- Dep baru core-pc-stream: okhttp 4.12.0 (klien WS yang sama dipakai app
  referensi; JAR murni, tetap jalan di unit test JVM).
- Baru XyHubOkHttpTransport: bungkus tipis OkHttp WebSocket — tanpa header
  Origin, pingInterval 20 detik (OkHttp otomatis membalas ping level WS hub
  hibernation), onClosing membalas close agar jabat tangan RFC 6455 selesai,
  satu instance satu siklus koneksi sesuai kontrak XyHubTransport.
- XyHubAuth: + XyHubIceServer (credential di-redact dari toString) +
  turnIceServers() — 503/5xx → daftar kosong (TURN opsional, seperti klien
  referensi), 403/429 → XyHubHttpException; urls string/array keduanya
  didukung; kini mengimplementasikan interface XyHubOnboarding agar sesi
  bisa diuji dengan onboarding palsu.
- Baru XyHubSignalingSession: orkestrator penuh jalur produksi —
  guestLogin(refresh?) → signalToken → turnIceServers (gagal → lanjut STUN,
  detail "turn-unavailable") → buildWebSocketUrl → connect → hello otomatis
  → welcome → pair otomatis → pair-response. Penolakan (PIN salah,
  peer-offline) TIDAK menutup sesi: pair() ulang tanpa onboarding ulang; rem
  anti-penebak pair-terkunci dilaporkan dengan retry_in; answer/ice/bye host
  diteruskan ke listener media; bye host menutup signaling (close 1000
  "host-bye"); guestRefreshToken tersimpan untuk pembaruan sesi. Tidak
  menyentuh WebRTC — wiring media menyusul di atasnya (3c-2).
- +24 unit test JVM: XyHubOkHttpTransportTest lawan server WS stub in-process
  (jabat tangan Upgrade diverifikasi — path+query, header WS, ABSEN Origin;
  frame teks dua arah termasuk unmasking frame klien; close dari kedua arah
  dengan balasan otomatis), XyHubSignalingSessionTest (jalur bahagia, refresh,
  PIN salah ulang, rem 300 dtk, peer-offline, onboarding gagal, TURN 403
  lanjut, media diteruskan, bye menutup, start dua kali, stop), XyHubAuthTest
  +3 untuk /turn-ice (kredensial urls array+string, 503 kosong, 403 lempar).
- Berikutnya (3c-2): wiring WebRTC (PeerConnection + transceiver + channel
  `input` → XyHostInputCodec) di atas XyHubSignalingSession, lalu UI sesi
  (pairing PIN → video → overlay XyPad).
- TANPA build APK sesuai instruksi pemilik; hanya gate compile + unit test.

## Ronde 30 — Tahap 3c bagian 2 (issue #12): wiring WebRTC + UI sesi PC-stream (2026-10-14)
- XyHubStreamViewer (core-pc-stream): endpoint media produksi di atas
  XyHubSignalingSession — PeerConnection UNIFIED_PLAN (video RECV_ONLY +
  mic track "xy-mic" SEND_RECV nonaktif, susunan transceiver meniru klien
  referensi RtcSession.kt supaya offer berbentuk sama di mata host),
  channel `input` dengan DataChannel.Init() default (ordered/reliable sesuai
  dekoder host input.rs), ICE STUN cloudflare + TURN dari /turn-ice, kandidat
  remote di-buffer sampai answer diterapkan, pesan teks host (meta) diteruskan,
  seluruh callback listener di-post ke thread utama. sendInputPacket() satu
  pintu untuk semua opcode XyHostInputCodec. Stop: bye ke host → tutup
  signaling → dispose WebRTC. Inisialisasi native sekali per proses
  (XyHubWebRtc). Tidak diuji JVM (WebRTC native) — tipis di atas komponen
  teruji, mengikuti pola PcStreamViewer.
- Dep: webrtc-sdk kini `api` (tipe WebRTC muncul di API publik modul — app
  perlu SurfaceViewRenderer/EglBase/VideoTrack).
- UI app: PcStreamSessionActivity — layar pairing (ID host 9 digit + PIN
  6 digit, filter digit; status per state sesi; penolakan PIN bisa diulang
  tanpa onboarding ulang via retryPair; rem anti-penebak menampilkan sisa
  waktu) → layar video (SurfaceViewRenderer di AndroidView + sink dikelola
  viewer) + overlay input dasar: area sentuh = touchpad (geser kursor, tap
  klik kiri, double-tap klik ganda, tekan-tahan klik kanan), strip scroll
  kanan (satuan WHEEL_DELTA 120), tombol mouse tekan-tahan, dialog kirim
  teks (opcode TEXT), keyboard fisik → Windows Virtual-Key (huruf/angka/
  enter/backspace/delete/esc/tab/panah/spasi). Overlay = fondasi fungsional
  menunggu revisi UI pemilik; wiring virtual pad XyPad menyusul.
- Entry point: baris baru "PC Stream — Host XyDesk (ID & PIN)" di bubble
  pemilih jenis koneksi (DevicesScreen) → langsung buka activity; tema ikut
  setelan app (XyDeskTheme); manifest mendaftarkan activity dengan pola
  configChanges sama seperti sesi RDP.
- TANPA build APK sesuai instruksi pemilik; hanya gate compile + unit test.

## Ronde 31 — dock melayang + diagnosis install + koordinasi virtual display

- **Bottom nav jadi dock melayang** (`XyBottomNav.kt` ditulis ulang): kapsul
  rounded 30dp melayang dengan margin 20dp samping / 12dp bawah, hormati
  gesture bar (`navigationBarsPadding`), bayangan lembut 16dp, pil aktif
  berisi ikon+label (label expand/shrink), ikon aktif scale 1.14 dengan pegas
  memantul (magnify ala dock macOS). `XyBottomNavHairline` dihapus (fungsi +
  call site `XyDeskHome.kt`) — kapsul sudah dipisah bayangan, garis tidak
  relevan lagi. Konten tetap `weight(1f)` di atas nav sehingga tidak tertutup.
- **Diagnosis "APK tidak valid"** (tanpa perubahan kode — CI sudah benar):
  rilis di-re-sign apksigner **v1+v2+v3** (v4 sengaja off, tak perlu untuk
  sideload). Penyebab riil di sisi user: signature mismatch (wajib uninstall
  versi lama), salah ABI (32bit=armv7, 64bit=arm64, **64bit-x86=x86_64 untuk
  cloud PC China/emulator**), Play Protect/ROM China memblokir sideload,
  unduhan korup (cek SHA-256), Android < 6 (minSdk 23).
- **FreeRDP tuning**: dievaluasi — `RdpUri` sudah mengirim setting performa
  inti (UDP/multitransport, gfx AVC444/AVC420, async-update/channels,
  compression, network auto-detect, sound quality high, dynamic resolution).
  Flag cache tambahan (bitmap/glyph/offscreen) **sengaja tidak ditambahkan**:
  nama flag CLI native tidak bisa diverifikasi di sandbox dan flag yang tidak
  dikenal membuat FreeRDP menolak koneksi — lebih aman diuji manual saat
  build akhir.
- **Koordinasi host**: issue [XyDesk#181](https://github.com/xykal/XyDesk/issues/181)
  (label `koordinasi`) — Virtual Display Adapter IddCx untuk cloud PC/VPS
  headless (QianPC dll), ala StarDesk/Parsec VDD.

## Ronde 32 — wiring XyPad → gamepad opcode 0x0E (3c tuntas)

- **`PcStreamPadOverlay.kt` (baru, 420 baris)**: virtual pad on-screen untuk
  sesi PC Stream — 5 klaster (TOP_BAR pill LT/LB/ST/BK/RB/RT/☰/×, D-pad,
  stik kiri, stik kanan, ABXY) posisinya via `XyPadLayout` (pecahan layar),
  tersimpan per host di `SessionPrefs.virtualPadLayout`. Mode atur (☰)
  mematikan input sesaat supaya klaster bisa digeser drag; posisi disimpan
  saat mode ditutup. Stik: knob kembali ke pusat saat lepas; **Y di-flip di
  `PadStick`** (layar y+ = bawah → XUSB y+ = atas, konfirmasi via
  `host/src/input.rs` + `xinput.rs`: laporan diteruskan apa adanya ke
  ViGEm). Snapshot state penuh tiap perubahan (`consumeDirty`), state netral
  dikirim saat overlay ditutup/dihancurkan supaya host melepas tombol.
- **`PcStreamSessionActivity.kt`**: property `activeHostId` (kunci prefs
  layout), tombol `PAD` di bar kontrol, overlay ditampilkan di atas
  touchpad; kirim `XyHostInputCodec.gamepad(state)` (opcode 0x0E, 13 byte)
  lewat `sendInputPacket` hanya saat channel `input` terbuka.
- **Kontrak diverifikasi ke host**: `xykal/XyDesk` `host/src/input.rs`
  (decode 0x0E → `xinput::submit`) + `xinput.rs` (ViGEm/physical) — layout
  byte identik dengan `XyHostInputCodec` (vektor emas test host: buttons
  0x1000, lt 0, rt 255, lx 32767, ly -1).
- Dependabot: **tidak ada PR terbuka** di XyDesk maupun XyDesk-Remote.

## Ronde 32 (lanjutan) — rilis v1.1.13 + deploy Worker produksi

- **Dependabot**: tidak ada PR terbuka di XyDesk maupun XyDesk-Remote (dicek
  via API) — tidak ada yang perlu di-merge.
- **Rilis**: `VERSION_NAME=1.1.13(74)` → gate CI 456✅ → tag `v1.1.13`
  (commit e261480) → CI 457✅ build RELEASE (R8 + signing v1+v2+v3) →
  draft 11 aset → **publish** dengan catatan rilis lengkap (termasuk catatan
  uninstall-dulu + pilihan ABI untuk cloud PC x86_64):
  https://github.com/xykal/XyDesk-Remote/releases/tag/v1.1.13
- **Worker produksi** `xydesk-remote-web` ter-deploy 2× (kode+assets, lalu
  release-state baru) ke 6 domain: rdp.xydesk.my.id, xydeskremote.biz.id,
  www/api/wss/cpc.xydeskremote.biz.id — Durable Object `PcSignalRoom`
  (signaling PC Stream) aktif. Live release-state = v1.1.13(74).
  Deploy pakai wrangler 4.151.0 + Node 22 (tarball /tmp — sandbox hanya
  Node 20; wrangler terbaru menuntut ≥22).
- Draft lama v1.1.12 dibiarkan utuh (belum dipublikasikan; bisa dihapus
  manual karena isinya subset v1.1.13).

## Ronde 33 — opcode 0x10 VIRTUAL_DISPLAY (XyDesk#181 tahap 1)

Investigasi repo host menemukan infrastruktur VDD **sudah lengkap** di
`xykal/XyDesk` (`virtual_display.rs`: deteksi adapter, matriks 720p..8K
30..240Hz, EDID kustom XYD, sync option.txt/vdd_settings.xml, install via
xydesk-vdd-ctl, `set_virtual_display_mode`; `packaging/windows/drivers/`
berisi IddSampleDriver + vdd_ctl). Gap sebenarnya: **klien remote tidak
bisa meminta mode VDD dari dalam sesi** — hanya via CLI/panel lokal.

- **Host (PR xykal/XyDesk#182, branch feat/virtual-display-opcode)**:
  opcode baru `0x10 VIRTUAL_DISPLAY w:u16le h:u16le hz:u8 count:u8` (7 byte,
  hz 0=60, count 0=1) → `input.rs` tag+enum+decode+test vektor;
  `input_dispatch.rs` handler spawn_blocking → `set_virtual_display_mode`,
  balasan JSON `{"type":"virtualDisplay","status":"applied|notInstalled|
  error",...}` + meta baru + keyframe saat sukses; PROTOCOL.md seksi baru.
  **Kebijakan keamanan: tidak memasang driver dari sesi remote** (UAC lokal;
  belum terpasang → status `notInstalled`). rustfmt diverifikasi lokal
  (rustup + rustfmt di sandbox).
- **Klien (repo ini)**: `XyHostInputCodec.virtualDisplay(w,h,hz,count)` +
  decoder `XyHostInputEvent.VirtualDisplay` + 3 test (layout LE 7 byte,
  roundtrip + tolak out-of-range); tombol **VD** di bar kontrol PC-stream →
  dialog preset (720p60..4K60) → kirim; `onHostText` mem-parse balasan
  `virtualDisplay` → notice 6 detik di atas layar.

## Ronde 33 (hasil akhir)

- CI XDR **461✅** (fix: `0x80.toByte()` di test byteArrayOf).
- Host Check ✅ di branch (fmt+clippy Linux, clippy Windows) setelah fix arm
  exhaustive `inject` untuk `VirtualDisplay` (bukan injeksi → `true`).
- **PR xykal/XyDesk#182 MERGED** (merge commit 7e27245) — opcode 0x10
  VIRTUAL_DISPLAY resmi jadi kontrak dua sisi. Komentar koordinasi #181:
  6176562679.
- Catatan rilis: tombol VD klien TIDAK ada di v1.1.13 (dibuat setelah
  rilis) — masuk build berikutnya.

## Ronde 34 — revisi UI pemilik: list/grid, bersih-bersih teks, kontrol global

- **List perangkat dua bentuk** (permintaan pemilik): grid tetap kartu besar
  (preview desktop 212dp); mode list kini bar ramping — preview kecil
  (104×64dp) di kiri, nama + keterangan 1 baris, aksi ikon (WoL/atur/hapus).
  `DeviceCard` ber-param `compact`; toggle grid/list yang sudah ada yang
  memilih bentuk.
- **Bersih-bersih teks**: 60 `subtitle` penjelasan dihapus dari 6 layar
  (Screens, AddDeviceScreen, SessionControls, XyProfileScreen,
  SessionKeyLayer, XyDeskHome) — judul saja; yang dipertahankan: "Ingat
  password" (info keamanan AES-GCM), detail aksi tombol HUD (memang
  penjelas pilihan), dan 1 baris cara seret cluster. Paragraf panjang card
  dihapus (Streaming & Latensi, Masalah koneksi, Tombol bawaan); banner
  "Koneksi PC beta" + empty state dipendekkan; detail InputMode dipendek
  jadi 1 baris.
- **Kontrol sesi GLOBAL** (SessionPrefs): `hudKeys`/`customHudKeys`/
  `hudButtonsVisible`/`hudProfile`/`virtualPadLayout` pindah ke store global
  `xydesk.input` — atur sekali, semua koneksi (termasuk RDP baru) ikut;
  nilai per-perangkat lama dimigrasi otomatis saat pertama dibaca.
  Toggle settings "Tombol tengah"/"Tombol ganti mode input" dihapus (tak
  berlaku lagi).
- **Default HUD baru**: klik kiri, klik kanan, scroll naik, scroll turun,
  Copy (Ctrl+C), Paste (Ctrl+V) — tengah & tombol mode dibuang dari preset
  awal (ronde 34, permintaan pemilik).
- **Kredensial global**: `CredentialVault.LAST_USERNAME_KEY/LAST_PASSWORD_KEY`
  — username selalu, password saat "ingat"; AddDeviceScreen prefill urutan:
  profil lama → ingatan per host → kredensial global terakhir.
- **Insiden git**: HEAD lokal tertinggal ronde 32 (snapshot) padahal commit
  ronde 33 ada di remote — pulihkan via fetch + `git reset origin/main`
  (working tree utuh, 10 file revisi ini satu-satunya diff). `core.fileMode
  false` supaya exec-bit upstream tak ikut ter-commit.

## Ronde 35 — driver XyDeskVDD milik sendiri (issue XyDesk#181)

"Gas semua, sertifikat belakangan, SmartScreen gpp" — driver display
virtual IddCx UMDF2 milik XyDesk sendiri, bukan lagi biner pinjam MTT/ge9.

Semua di repo saudara `xykal/XyDesk` (PR #183 + #184 #185 #187 #188 #189
#190 #191 #192, semua merge-commit ke main):

- **A. Source** `packaging/windows/vdd-src/XyDeskVDD/`: fork ge9/
  IddSampleDriver (MIT), EDID sample diganti EDID XYD 256-byte (identik
  `build_xydesk_edid()` host, checksum dihitung ulang); `loadOptions()`
  bool + dua path `C:\XyDeskVDD\option.txt` → fallback warisan
  `C:\IddSampleDriver\option.txt`; INF tulis ulang UTF-8 CRLF (HWID
  `Root\XyDeskVDD`, service/UMDF structure persis asli, DestinationDirs
  `12,UMDF`, `UmdfExtensions=IddCx0102`).
- **B. Dist** `packaging/windows/drivers/XyDeskVDD/`: install.bat (jalur
  utama `xydesk-vdd-ctl install --dir` → fallback certutil+pnputil+
  SetupAPI XyPnp), uninstall.bat (remove-device + delete-driver DriverStore
  + hapus cer by subject), option.txt, vdd_settings.xml, README.txt.
- **C. Workflow `vdd-build.yml`** dispatch-only: `windows-2022`, WDK
  10.0.22621 via wdksetup resmi fwlink 2249371 (winget tidak ada di runner
  Server; choco WDK tertinggal 19041 = mismatch SDK), msbuild
  `/p:SignMode=Off /p:WindowsTargetPlatformVersion=10.0.22621.0` — cat
  tetap di-generate package targets (inf2cat manual tidak perlu), lalu
  New-SelfSignedCertificate `CN=XyDeskVDD Code Signing` + signtool SHA256
  (dll + cat), artifact `XyDeskVDD-driver-win64`. **Pelajaran PowerShell**:
  em-dash UTF-8 tanpa BOM = parse error di PS 5.1 (script PS = ASCII
  saja); `.FullName` di variabel string = kosong diam-diam.
- **Biner rilis pertama** (run 38647763444) di-commit ke dist: DLL 109440
  byte + cat + cer + INF stamped (`DriverVer 10/14/2026,22.32.22.280`).
  Cat meng-hash INF+DLL → jangan edit INF dist terpisah dari cat.
- **D. Host Rust**: `locate_vdd_ctl`/`locate_install_bat` prioritas
  `drivers\XyDeskVDD\` (+ PF path); `sync_host_driver_configs` tulis
  option.txt ke `C:\XyDeskVDD` juga.
- **E. `install-all-drivers.bat`**: XyDeskVDD dulu, IddSampleDriver hanya
  fallback bila biner XyDeskVDD tidak ada.
- **F. Auto-ensure headless**: `input_dispatch::serve` spawn_blocking
  `ensure_display()` sekali di awal sesi — best-effort, TANPA install/UAC
  dari sesi remote.
- **G. `xydesk_vdd_ctl.cpp`**: `cmdInstall` kenal XyDeskVDD.inf prioritas
  pertama; `locateDriverDir` + kandidat `drivers\XyDeskVDD`; `writeDriverConfigs`
  buat+ACL `C:\XyDeskVDD` dan tulis option.txt ke 3 lokasi.
- Host Check 38643928278 hijau (fmt+clippy Linux, cargo check Windows) —
  2 iterasi: `let_underscore_future` di spawn_blocking → panggil tanpa
  `let`; rustfmt pecah chain join.

Bundling host otomatis (build.yml step copy `packaging/windows/drivers/*`
sudah ada). Yang belum: uji manual di mesin Windows nyata + sertifikat
code signing Microsoft/EV (diterima self-signed dulu).

## Ronde 36 — fix crash produksi + anti-black-screen (belum dirilis)

Laporan pemilik 2026-10-15: crash `invalid weight; must be greater than zero`
(produksi 10-11 & 10-15, dua build beda) + "koneksi suka menggantung alhasil
blackscreen". Build rilis DITAHAN atas permintaan (gate debug tetap jalan).

- **Crash weight(0f)**: `SessionTransferPanel.ProgressBar` memakai
  `weight(fraction)` — `weight(0f)` saat doneBytes=0 (awal transfer) melempar
  IllegalArgumentException di Choreographer.doFrame. Komentar lama "weight 0f
  valid" salah. Fix: `fillMaxWidth(fraction)` (valid untuk 0). Pelajaran:
  `RowScope.weight()` menuntut > 0 — fraksi dinamis pakai `fillMaxWidth`.
- **Anti-black-screen PC stream** (`PcStreamSessionActivity`):
  - Watchdog frame pertama 15 dtk (sink gate menandai frame; meta tiap detik
    jadi jalur cadangan) → overlay "Tidak ada gambar dari host" + tombol
    SAMBUNG ULANG (restart sesi, kredensial in-memory saja).
  - `onHostText` kini membaca `meta.capture` host: hint penyebab spesifik
    (backend-error / armed-no-frame / idle / blackFrames) — telemetry ini
    sudah dikirim host tiap detik tapi klien sebelumnya mengabaikannya.
  - Watchdog pairing 25 dtk: host diam → pesan jelas + tombol Coba Lagi
    hidup (retryPair tanpa onboarding ulang).
  - Bar status: indikator TANPA GAMBAR.
- Gate: 38677381455 merah (xy() @Composable dari fungsi biasa di
  updateCaptureHint) → fix xyNow → 38677646510 hijau (b705df0).
- Insiden .git antar-sesi lagi: HEAD lokal balik ke ronde 32 padahal remote
  sudah 9d0817d — pulih dengan re-add remote (config tidak persisten) +
  fetch + `git reset origin/main`; diff bersih 2 file, tidak ada pekerjaan
  yang hilang.

## Ronde 37 — RDP anti-black-screen tahap 2, log singkat, kredensial, feed bottom sheet, web

Permintaan pemilik 2026-10-15 (9 item, "UI harus bersih"). Build rilis tetap
ditahan; gate debug jalan.

- **RDP blackscreen tahap 2** (`XyDeskSession`): jalur lama cuma cek sekali
  (`blackScreenChecked`) sehingga bila grafis fallback ikut hitam, layar diam
  gelap selamanya. Kini maksimal 2x reconnect otomatis (`blackScreenReconnects`,
  reset penanda tiap reconnect supaya siklus deteksi hidup), lalu
  `blackScreenFatal` → layar status jelas: "Layar tetap hitam" + penjelasan
  singkat + tombol Coba lagi / Keluar sesi. fps>0 memulihkan grafis penuh dan
  menghapus status fatal.
- **Log error singkat & jelas** (`RdpErrors.shortTitle`): judul satu baris —
  "Password salah", "Username atau password salah" (FreeRDP tidak membedakan
  keduanya), "Host tidak menjawab", "Host tidak ditemukan", "Koneksi kehabisan
  waktu", "Sertifikat/TLS gagal", "Akun terkunci", "Password kedaluwarsa",
  "Ditolak server", "Gagal koneksi". Judul overlay error RDP kini memakai ini;
  hint panjang tetap jadi badan + Detail.
- **Popup ubah kredensial**: `RdpErrors.isCredentialError` → tombol "Ubah
  username & password" di overlay error → `CredentialsRetryDialog` (prefill
  dari profil, domain opsional, ingat password) → simpan lewat
  SessionsRepository.save → sambung ulang otomatis.
- **Profil lebih clean + cepat**: kartu identitas app (logo/versi) diturunkan
  jadi footer ringkas satu baris; decode avatar pindah ke background thread
  (LaunchedEffect + Dispatchers.IO) sehingga layar profil terbuka seketika.
- **Feed ala TikTok** (`FunHubCard` + worker):
  - Komentar kini di `ModalBottomSheet`: daftar komentar (LazyColumn, waktu
    relatif) + kolom tulis menempel di bawah; dibuka ulang tiap mau komen.
  - Aksi postingan (edit/hapus/laporkan) dipadatkan jadi satu titik "···" di
    kartu → bottom sheet aksi. Konfirmasi hapus postingan tetap dialog (aman
    dari salah ketuk).
  - **Hapus komentar sendiri**: worker menyimpan `author_hash` per komentar,
    view mengirim `is_own`, endpoint baru POST `/api/jokes/<id>/comment-delete`
    (403 untuk bukan penulis, decrement count). Routing outer worker diperluas.
  - Test baru: delete hanya oleh penulis + flag is_own; 49/49 hijau.
  - Bonus: `web-checks` lama merah karena assert teks "Matikan sakelar" tidak
    cocok lagi dengan teks Screens.kt → disesuaikan ("Sakelar ini dapat
    dimatikan kapan saja"); worker-tests hijau lagi.
- **Web — menu popup & drawer**: nav desktop diringkas jadi Unduh + tombol
  "Menu ▾" (popup dropdown: Cek APK, Tentang, Fitur, Changelog, Host Agent,
  Status, Lisensi) + lang/theme; drawer mobile dapat link "Cek APK untuk HP-mu"
  paling atas; host.html/status.html juga dapat link ABI (nav + drawer).
- **Web — engine cek ABI per HP** (`abi.html`, baru): deteksi via
  `userAgentData.getHighEntropyValues("architecture"/"bitness")` dengan fallback
  parse UA (arm64/aarch64, armv7, x86_64); penanganan jujur untuk UA modern
  tanpa arsitektur (Firefox) → saran manual, x86 32-bit → universal, non-Android
  → info; badge kepercayaan; tombol unduh ABI yang cocok + cadangan universal;
  tabel semua varian + ukuran (di-refresh dari release-state.json, fallback
  hard-coded v1.1.13). Deteksi 100% lokal di browser. sitemap.xml ditambah.
- **UI host modern?** (pertanyaan pemilik): sudah ada — panel native di repo
  saudara `xykal/XyDesk` `packaging/native-host/main.cpp` (≈3.8k baris):
  jendela berlapis WS_EX_LAYERED + UpdateLayeredWindow, sudut membulat radius
  kustom + bayangan lembut (bukan kotak Win32), tombol hover/pressed/disabled,
  onboarding/session view/updater — diuji CI lewat native-panel-layout-test +
  check_panel_shape.py (bukti piksel). Yang di repo ini (`host-agent`
  XyDesk-Remote) masih panel Win32 klasik sederhana (STATIC/BUTTON/EDIT + tray)
  — jalan, tapi bukan yang modern.
- Gate: 38680420983 merah (`ModalBottomSheet` = API experimental Material3,
  perlu OptIn) → fix `@OptIn(ExperimentalMaterial3Api::class)` di kedua sheet →
  38680642830 hijau (c43e602). Rilis tetap ditahan ([skip-debug]).

## Ronde 37 — GAS: rilis v1.1.14(75) + deploy Worker + web

"Kalo selesai udh semuanya, gas aja" — seluruh rantai rilis dijalankan.

- **Versi**: `VERSION_NAME=1.1.14(75)` (`ab9491d`), gate hijau (run 38681318244).
- **Rilis**: tag `v1.1.14` → CI 38681666768 RELEASE sukses (~32 mnt, R8 +
  signing v1+v2+v3) → 11 aset draft → **publish** dengan catatan lengkap:
  https://github.com/xykal/XyDesk-Remote/releases/tag/v1.1.14
- **Worker produksi** `xydesk-remote-web` versi `ca12b70f-589a-4b57-b2b2-35c3d0c83132`
  ter-deploy (kode r37 + 21 aset web) ke 6 domain (rdp.xydesk.my.id,
  xydeskremote.biz.id + www/api/wss/cpc). Wrangler 4.151.0 + Node 22.14 di /tmp.
- **Live diverifikasi**:
  - `/release-state.json` = v1.1.14(75); `/api/health` = v1.1.14; alias
    `/arm64-v8a.apk` dst → 302 ke aset v1.1.14 yang benar.
  - `/abi` (halaman engine ABI baru) 200 dengan engine
    getHighEntropyValues + fallback UA; index live memuat menu popup
    (`menu-btn`/`menu-pop`) + link ABI.
  - **Uji end-to-end hapus komentar di produksi**: post QA → komentar oleh
    klien lain → hapus oleh bukan penulis = **403** → hapus oleh penulis =
    **deleted** → post QA dibersihkan. `is_own` terlihat di feed.
- Draft v1.1.12 lama dibiarkan utuh (bisa dihapus manual, isinya subset).
- Yang belum: uji runtime di HP nyata untuk fitur r37 (bottom sheet feed,
  popup kredensial, layar black-screen fatal).

## Ronde 38 — bersih-bersih + papan umpan balik report/ask.xydeskremote.biz.id

Permintaan pemilik 2026-10-15: bersihkan workspace, bikin halaman web
report/bug/saran dengan domain sendiri (user posting, founder/dev membalas),
plus info WhatsApp 2 bahasa.

- **Bersih-bersih**: .git lokal ke-reset lagi ke ronde 32 (antara-turn) —
  dipulihkan dengan prosedur standar; konten 100% sinkron origin/main. Sisa
  diff hanya noise mode-bit file script (diabaikan). Workspace lain sudah
  bersih (2 repo + uploads kosong).
- **Papan umpan balik** (feedback board):
  - Durable Object baru `FeedbackBoard` (migration v4, binding
    `FEEDBACK_BOARD`): thread {type bug/idea/question/other, text ≤2000,
    nama opsional, status open/progress/done, hidden}, balasan ≤1000 dengan
    flag staff, rate limit 30s submit / 10s reply, maks 400 thread + 60
    balasan (prune terlama).
  - API: GET/POST `/api/feedback`, POST `/api/feedback/<id>/{reply,status,hide}`.
    Auth staff = header `x-xydesk-staff-token` diverifikasi di tepi worker
    (secret `FEEDBACK_STAFF_TOKEN`), lalu disuntik sebagai flag internal yang
    tidak bisa dipalsukan dari luar. Status & hide staff-only (403).
  - Halaman `web/feedback.html` (bilingual ID/EN via toggle): form + filter
    tipe + thread expand + balasan + "Mode Dev/Founder" (token disimpan di
    localStorage) → balasan berbadge "XyDesk Team", ubah status, sembunyikan
    spam. Escape semua teks user (XSS-safe).
  - **Domain baru**: `report.xydeskremote.biz.id` + alias
    `ask.xydeskremote.biz.id` (custom domain worker; root di-rewrite ke
    feedback.html). Navigasi semua halaman (menu popup index, drawer index/
    host/status/abi) + sitemap menautkan papan ini.
  - Test baru (submit/list/reply/403 non-staff/staff reply+status+hide/rate
    limit) → 50/50 hijau. Catatan insiden: secret pertama ter-upload kosong
    (baris .env menempel tanpa newline) → forbidden; .env diperbaiki + secret
    di-set ulang.
  - Deploy `30a867f9` (8 domain) + secret. **E2E produksi lulus**: staff
    reply "XyDesk Team" ✓, status progress ✓, hide ✓. CI d65ae16 hijau
    (Build APK + Web Worker checks).
- Token staff disimpan di `.env` (FEEDBACK_STAFF_TOKEN) untuk sesi berikutnya;
  diberikan ke pemilik lewat chat untuk dipakai di Mode Dev halaman.

## Ronde 39 — splash logo XyVerse + identitas feed awet reinstall

Permintaan pemilik 2026-10-15: splash pakai logo pengembang (XyVerse), logo
push pakai logo XyDesk Remote, dan postingan feed harus bisa dihapus.

- **Splash** (`Splash.kt`): logo utama XyDesk Remote tetap di tengah; footer
  teks "XyVerse Technology Global" diganti **logo XyVerse resmi** dari brand
  kit (Google Drive `XYVERSE_BRAND_KIT_FILE_ID` → `secrets/XyVerse_Brand_Kit.zip`,
  `01_logo_utama/logo_utama_horizontal_transparan_{putih,hitam}.png` di-resize
  1160×344 → `drawable-nodpi/xyverse_logo_h_{white,black}.png`) dengan caption
  "Pengembang · Developer". Brand kit zip kini tersimpan di `~/secrets/`.
- **Logo notifikasi/push**: sudah benar sejak ronde sebelumnya — small icon
  `ic_launcher_monochrome` + large icon `xy_mark_white` (logo XyDesk Remote),
  tidak ada yang perlu diubah untuk notifikasi sesi.
- **Hapus postingan feed — akar masalah**: tombol Hapus hanya muncul untuk
  postingan milik sendiri (`is_own` dari client ID anonim). Setelah
  uninstall-install ulang, client ID baru → semua postingan lama tidak lagi
  dianggap milik sendiri. Fix:
  - `CloudSync`: prefs `xydesk.community.jokes` ikut dicadangkan
    (kecuali `feed_cache_v1` yang besar) → setelah pasang ulang + Pulihkan
    dari Cloud, identitas feed lama kembali otomatis.
  - `CommunityJokesApi.clientId` jadi getter dinamis (SharedPreferences
    memory-cached) → setelah restore, instance API langsung memakai ID lama
    tanpa restart.
  - Pesan restore kini menyebut akun feed ikut pulih.
  - Jalur manual yang sudah ada: kode pemulihan akun feed (= client ID) bisa
    ditempel di kartu akun komunitas untuk memulihkan identitas lama tanpa
    cloud.
- Catatan: semua ini masuk build BERIKUTNYA (v1.1.15+); di v1.1.14 yang
  terpasang sekarang, identitas lama hanya bisa kembali lewat kode pemulihan
  feed (kalau pernah disalin sebelum uninstall).
- Gate: 38692294340 hijau (41d22da). Rilis tetap menunggu perintah.

## Ronde 39 — GAS: rilis v1.1.15(76)

"gas update terbarunya bre" — seluruh rantai rilis dijalankan.

- **Versi**: `VERSION_NAME=1.1.15(76)` (`1711348`), gate hijau (38727949640).
- **Rilis**: tag `v1.1.15` → CI 38728330885 RELEASE sukses → 11 aset draft →
  **publish**: https://github.com/xykal/XyDesk-Remote/releases/tag/v1.1.15
- **Worker** versi `a10afba6` ter-deploy (8 domain) dengan release-state
  v1.1.15(76). Live diverifikasi: /release-state.json = v1.1.15(76),
  /api/health = v1.1.15, alias APK 302 → aset v1.1.15.
- Catatan rilis menegaskan: dari v1.1.13/1.1.14 update langsung tanpa
  uninstall; hanya build pra-v1.1.13 perlu uninstall sekali (signature).
- Draft v1.1.12 lama masih utuh (bisa dihapus manual).

## Ronde 40 — widget quick-connect + notifikasi koneksi putus

"lanjut" setelah v1.1.15 → backlog info WA v4. Dua fitur Android dikerjakan.

**Widget Quick Connect (homescreen, 1-tap connect):**
- `XyQuickConnectWidgetProvider` (RemoteViews klasik, tanpa Glance): receiver
  APPWIDGET_UPDATE, label perangkat dari prefs `xydesk.widgets` (mapping
  appWidgetId → profileId + label), tap → PendingIntent ke HomeActivity
  dengan `EXTRA_CONNECT_PROFILE_ID`.
- `XyWidgetConfigActivity` (exported, dipanggil launcher saat widget dipasang):
  daftar favorit Compose → pilih perangkat → simpan mapping → render widget
  → RESULT_OK. Tidak ada perangkat = pesan arahan buka app dulu.
- `HomeActivity.handleWidgetConnectIntent` (onCreate + onNewIntent): load
  profil by id dari SessionsRepository → `authenticateBeforeConnect` (jalur
  kunci perangkat sama seperti connect normal) →
  `XyDeskSessionActivity.connectIntent`. Perangkat sudah dihapus → banner.
- Layout `widget_quick_connect.xml` + bg `widget_quick_connect_bg.xml` (kartu
  gelap sudut bulat) + `xml/widget_quick_connect_info.xml` (4×1 cell,
  resizeMode horizontal). Manifest: activity + receiver + meta-data.

**Notifikasi koneksi putus tak terduga (backlog):**
- `DisconnectNotifier.kt`: channel `xydesk.disconnect` (IMPORTANCE_DEFAULT),
  notif id 2001, tap → buka app (SKIP_AUTO_RESUME), ikon & pola sama seperti
  notif sesi XySessionService (monochrome + xy_mark_white).
- `XyDeskSessionScreen`: post notif HANYA kalau sesi tadinya connected lalu
  mati final (Disconnected/Error) BUKAN karena user disconnect, bukan sedang
  auto-reconnect/ubah-resolusi, DAN app sedang background (lifecycle < RESUMED
  atau ON_PAUSE). ON_RESUME → notif dibatalkan; back dari layar status
  (isFinishing) tidak memicu notif.

Housekeeping: HEAD sandbox tertinggal ronde 32 + remote hilang → fetch +
reset index ke origin/main (225db88) + restore exec-bit file script; hanya
perubahan ronde 40 yang tersisa di working tree.

Rilis menunggu perintah pemilik (kandidat v1.1.16).

## Ronde 41 — revisi UI: menu utama 2 gaya + polish

Permintaan pemilik: UI lebih clean/terstruktur, menu utama punya 2 gaya —
popup dan sidebar kanan. Scope semua layar. CATATAN BARU DARI PEMILIK:
- Workspace dibersihkan (folder proyek lama /home/user/XyDesk dihapus,
  uploads/env.txt & secrets tetap).
- Rilis selanjutnya (v1.1.16+) WAJIB DRAFT dulu; publish hanya setelah
  konfirmasi pemilik. NO BUILD/NO RILIS sampai disuruh.

Perubahan:
- `AppPrefs.homeMenuStyle` (0=popup tengah default, 1=sidebar kanan) +
  `XyMenuStyleState` (pola XyThemeState) supaya ganti gaya langsung aktif.
- `XyDeskHome`: drawer kiri lama diganti `XyHomeMenuShell` (scrim + animasi
  per gaya: popup fade+scale di tengah / sidebar slide dari kanan, radius
  24dp) + `XyHomeMenuBody` (konten sama untuk 2 gaya) + `XyHomeMenuItem`
  (kotak ikon rounded, state terpilih jelas). Seksi Tampilan kembali masuk
  menu (ronde 13 dulu dibuang; dashboard Profil sudah lama menampilkan
  semua seksi).
- Seksi Tampilan: kartu "Gaya menu utama" (XySegmented Popup/Sidebar kanan).
- Polish semua layar lewat komponen inti: XyCard kini punya shadow lembut,
  XyTopBar padding lebih lega, bubble popover "+" radius 18dp + border +
  shadow biar konsisten.
- Gate: commit ini. TANPA build rilis/tag/draft (menunggu perintah).

### Ronde 41 — lanjutan (2026-10-16, "gas aja breku")

Permintaan: enak dipandang + super cepat; tampilan perangkat di Home HANYA
list ringkas dan kartu besar 1 kolom — grid 2 kolom dihapus.

- DevicesScreen: blok grid `chunked(2)` DIHAPUS. Toggle sekarang memilih
  antara list ringkas (compact) dan KARTU BESAR satu kolom full-width
  (preview 212dp). Key pref "device_grid_mode" dipertahankan supaya
  pilihan user lama tidak hilang, maknanya kini "kartu besar".
- Radius kartu perangkat: 6dp -> 10dp (list) / 16dp (kartu besar), lebih
  lembut dan konsisten dengan design system.
- Performa: LiveSessionsCard hanya update state saat daftar sesi benar
  berubah (instance Live stabil di registry) — polling 1.5s tidak lagi
  memicu recomposition sia-sia; grid 2 kolom hilang = komposisi lebih
  ringan (satu kartu per baris LazyColumn).
- Gate: commit ini. Masih TANPA build rilis (menunggu perintah pemilik).

## Ronde 42 — update gede part 1: custom FreeRDP engine tuning + bump engine

Permintaan pemilik: update besar semuanya (engine, UI, UX, backend), apalagi
FreeRDP di-custom lebih lanjut.

**Custom FreeRDP (jalur aman: URI→argumen native, tree C tetap bersih):**
Parameter baru terverifikasi ada di tree vendored 3.32 (`client/common/cmdline.h`):
- `/video` — Video Optimized Remoting: playback video di remote jadi halus.
- `/frame-ack:N` — buffer frame server; kecil = latensi turun.
- `/max-loop-time:N` ms — siklus loop engine; kecil = input lebih responsif.
- `/gfx:AVC...,small-cache` — cache bitmap kecil, hemat kuota.
Implementasi: `RdpOptions` (+persist/of/write/KEYS), `RdpUri.queryParams`,
UI seksi "Engine Performa Lanjutan" di AddDeviceScreen (toggle + segmented),
4 unit test baru di RdpUriTest (default, flag aktif, tanpa-h264, range guard).

**Engine bump:**
- Compose BOM 2026.05.01 → 2026.09.00 (Compose 1.11 → 1.12.1; 1.12 butuh
  compileSdk 37 + AGP 9 — sudah dipenuhi).
- + androidx.lifecycle:lifecycle-runtime-compose:2.10.0 (pasangan 1.12+).
- Migrasi 3 pemakaian LocalLifecycleOwner ke androidx.lifecycle.compose
  (ui.platform versi lama deprecated).

**Backend:** worker sudah punya cache header layered (max-age=300,
s-maxage=900) — tidak ada perubahan dibutuhkan ronde ini; perubahan backend
besar ditunda ke ronde berikutnya (D1/R2 dsb. kalau diminta).

Gate: commit ini. TANPA build rilis — menunggu perintah pemilik.

### Ronde 42 — part 2: polish UX + CHECKLIST RILIS v1.1.16

- XyGlassCard/xyGlass kini punya shadow lembut (konsisten dengan XyCard r41).
- Margin horizontal Feed & Profil 14dp -> 16dp (samakan dengan Home).

## ⚠️ PENDING UNTUK v1.1.16 — CHECKLIST RILIS (SUPER INGAT, JANGAN ADA YANG TERTINGGAL)

Build v1.1.16 nanti HARUS memuat SEMUA ini (semua sudah di branch main,
commit c8b3b44 → HEAD). Verifikasi satu per satu sebelum publish draft:

> **VERIFIKASI KODE 2026-10-16 (ronde 44):** semua item di bawah dicek
> ada di HEAD (8605cf6, tag v1.1.16) + CI 38913714998 hijau. Yang belum:
> tes perangkat nyata oleh pemilik sebelum konfirmasi publish.

**Ronde 40 — widget & notifikasi:**
- [x] Widget quick-connect homescreen: pasang dari launcher → pilih perangkat → tap = langsung connect (XyQuickConnectWidgetProvider, XyWidgetConfigActivity, layout widget_quick_connect.xml, receiver di manifest)
- [x] Tap widget tetap lewat autentikasi kunci perangkat kalau aktif
- [x] Notifikasi "Koneksi terputus" saat sesi mati sendiri & app di background (channel xydesk.disconnect); TIDAK muncul saat disconnect disengaja / retry / layar terbuka

**Ronde 41 — menu 2 gaya & polish:**
- [x] Tombol Menu → default POPUP TENGAH (kartu mengambang, fade+scale)
- [x] Ganti ke SIDEBAR KANAN lewat Tampilan → Gaya menu utama → langsung aktif
- [x] Semua seksi muncul di menu (termasuk Tampilan)
- [x] Home: toggle kanan atas = LIST ringkas ↔ KARTU BESAR 1 kolom (TIDAK ADA grid 2 kolom)
- [x] Kartu perangkat sudut membulat; bubble "+" konsisten (radius 18dp + border + shadow)

**Ronde 43 — host agent:**
- [x] Host Agent = 1 file exe standalone jadi jalur utama (setup PS1 & tombol unduh host.html)
- [x] UI Host modern dark: kartu rounded, Segoe UI Variable, status dot, log console, dark caption
- [x] https://www.xydeskremote.biz.id/XyDesk-Host.exe live 302 ke aset rilis aktif (worker 350757c0)

**Ronde 42 — engine & performa:**
- [x] Pengaturan perangkat → "Engine Performa Lanjutan": Video remoting halus, GFX small-cache, Buffer frame server (frame-ack), Loop respons engine (max-loop-time)
- [x] Engine pakai Compose 1.12.1 (BOM 2026.09.00) + lifecycle-runtime-compose 2.10.0
- [x] Glass card (Feed/Profil) punya depth shadow; margin seragam 16dp semua layar utama

**Prosedur rilis (aturan pemilik):**
- [x] Build & rilis HANYA setelah perintah eksplisit pemilik
- [x] Rilis dibuat sebagai DRAFT dulu; PUBLISH hanya setelah konfirmasi pemilik
- [x] Update release-state.json + deploy worker SETELAH draft dibuat, SEBELUM publish

## Ronde 43 — Host Agent: 1 EXE + UI/UX modern dark

Permintaan pemilik: host masih belum 1 exe + UI/UX host gak modern.

**Fakta:** XyDeskRemoteHost-Standalone.exe (1 file, static) ternyata SUDAH
dibuild CI sejak beberapa rilis — tapi disembunyikan: setup PS1 & halaman
host masih pakai jalur 3 file (exe + 2 DLL). Ronde ini menjadikannya jalur
utama + redesign UI.

**UI Host modern (main_host.cpp, verifikasi compile MinGW lokal sukses):**
- Dark theme: latar #14171D, kartu #1B1F27 rounded 18px, aksen #7C9EFF,
  status dot hijau/merah, log console-style Consolas gelap.
- Font Segoe UI Variable (fallback otomatis Segoe UI di Win10).
- Dark mode caption (DWMWA_USE_IMMERSIVE_DARK_MODE) + kontrol anak
  (DarkMode_Explorer) — dimuat dinamis, tanpa dependensi link baru.
- Layout terstruktur: header + garis aksen, kartu info label/nilai, status
  QUIC + paket, tombol 4, panel log; WS_EX_COMPOSITED anti-flicker,
  double-buffer WM_PAINT.
- Judul jendela: "XyDesk Remote — Host Agent".

**1-EXE jadi jalur utama:**
- Setup PS1: default unduh XyDeskRemoteHost-Standalone.exe (1 file);
  -LegacyZip untuk jalur lama.
- host.html: tombol utama "Host Agent — 1 File .exe (Standalone)" + alias
  XyDesk-Host.exe.
- Worker: alias baru /XyDeskRemoteHost-Standalone.exe & /XyDesk-Host.exe
  (redirect ikut rilis aktif) + 2 unit test (51/51 pass lokal).

**Verifikasi lokal:** MinGW compile standalone sukses (972KB, 1 file);
node --check + node --test worker 51/51 hijau. Worker dideploy ronde ini
supaya jalur 1-exe langsung bisa dipakai (menunjuk aset v1.1.15 yang sudah
ada; UI baru host masuk build berikutnya via checklist v1.1.16).

### Ronde 43 — deploy worker (live)

- Worker versi `350757c0-6da0-46e9-b58b-dbc181d21524` dideploy: alias
  /XyDeskRemoteHost-Standalone.exe & /XyDesk-Host.exe live (302 → aset rilis
  aktif). Fix penting: wrangler.jsonc `run_worker_first` harus memuat path
  alias baru — tanpa itu request .exe ditangani static assets (404.html)
  sebelum sampai worker.
- Verifikasi live: host.html 200, arm64 302, /api/health 200, kedua alias
  exe 302. Gate commit 5ee70db hijau.

## Ronde 44 — BUILD DRAFT v1.1.16(77) — menunggu konfirmasi publish

Perintah pemilik: "lanjut lah" (menanggapi tawaran build draft v1.1.16).

- Bump `release.properties` VERSION_NAME=1.1.16 / VERSION_CODE=77 → commit
  8605cf6 + tag `v1.1.16` (gaya sama seperti rilis sebelumnya).
- CI run **38913714998** (tag v1.1.16) SEMUA HIJAU:
  Gate compile Kotlin ✓, Release per-ABI (R8+signed) ✓ 27 menit,
  Publish GitHub Release ✓.
- **Draft release id 415064151, 11 aset** (4 APK + universal, standalone
  exe, host zip, host exe, 2 DLL, SHA256SUMS, SIGNATURE-VERIFICATION).
  Nama draft masih "v1.1.16" — saat publish ganti jadi
  "XyDesk Remote v1.1.16 (77)" + release notes.
- `web/release-state.json` → v1.1.16(77), release_state "draft", sha256
  semua aset dari SHA256SUMS draft; commit 2da3954; worker deploy
  **5b2f4d65-de27-4015-80a1-6c94830290d8**.
- Verifikasi LIVE pasca-deploy: alias exe & APK semua 302 → **v1.1.15**
  (BENAR — `getLatestPublishedRelease` sengaja skip draft; begitu draft
  di-publish, worker otomatis pindah ke v1.1.16 ≤5 menit via cache TTL).
  /host 200, /api/health 200. Static release-state.json apex sudah
  v1.1.16-draft; subdomain www kena edge cache lama (max-age=0,
  must-revalidate — akan segar sendiri; redirect worker tidak terpengaruh
  karena baca aset via ASSETS.fetch yang bypass CDN).
- Catatan: alias APK yang benar `/XyDesk-Remote-arm64-v8a.apk` dst.;
  `/XyDesk-Remote64bit.apk` bukan alias (404 itu memang bukan regresi).

**Pelengkap draft v1.1.16 (ronde 44b, 2026-10-16):**
- Draft rename "XyDesk Remote v1.1.16 (77)" + release notes lengkap
  (gaya v1.1.15: widget/notif, tampilan, engine, host 1-exe, teknis,
  changelog v1.1.15...v1.1.16) via PATCH API.
- Pesan WA siap: /home/user/pesan-wa-v1.1.16.md (murni, ID+EN, tanpa
  "dev", tanpa "dll", daftar lengkap) — dikirim SETELAH publish.
- PENTING (belajar dari PATCH draft): asset URL draft di API GitHub
  selalu tampil "untagged-<hash>" (draft v1.1.12 lama juga begitu) —
  itu NORMAL; begitu publish otomatis jadi
  /releases/download/v1.1.16/<file>. Jangan panik pasli rusak.
  tag_name draft sempat terlihat hilang setelah PATCH → PATCH balik
  tag_name "v1.1.16" memperbaikinya.

**PUBLISH v1.1.16 (menunggu perintah pemilik):**
1. Pastikan pemilik sudah tes draft (APK + host standalone exe UI baru).
2. Flip draft → published (PATCH releases/415064151 draft=false).
3. Update release-state.json: release_state "published" + published_at,
   commit, deploy worker.
4. Cek live: alias → v1.1.16 (≤5 menit), kirim pesan WA.

## Ronde 45 — fix transfer file & back + polish UI (2026-10-16)

Laporan pemilik: (1) transfer file tidak masuk folder Download di RDP,
(2) balik ke beranda butuh 2x, (3) UI Profil/Feed/menu kurang enak — telusuri
lebih dalam.

**1. File HP otomatis masuk folder Download PC (host-agent/src/main_host.cpp):**
- Thread watcher `tsclient_downloads_thread` (start di main, bareng relay
  heartbeat) menonton drive redirect `\\tsclient\\XyDesk` tiap 2,5 s SELAMA
  sesi RDP aktif dan menyalin file baru ke folder Download Windows — sebelumnya
  file cuma terduduk di drive tsclient dan user harus mencarinya sendiri.
- Dua varian folder: akses semua-file → `\\tsclient\\XyDesk\\XyDesk`
  (dicek lebih dulu); akses terbatas → root drive, TAPI kalau root berisi
  folder "Android" berarti itu storage penuh → tidak ditonton.
- File disalin hanya setelah ukurannya stabil 2 poll (anti setengah jadi);
  nama bentrok → "nama (2).ext"; tanda tangan nama|ukuran|mtime disimpan di
  `%APPDATA%\\XyDesk Remote\\received-files.log` supaya tidak dobel lintas
  restart.
- Folder Download diresolve via REGISTRI User Shell Folders GUID
  {374DE290-...} + fallback %USERPROFILE%\\Downloads — BUKAN
  SHGetKnownFolderPath (CoTaskMemFree butuh ole32 yang tidak dipakai build
  gate; jangan tambah flag link baru). Pelajaran: gate & release build host
  beda set lib — verifikasi lokal WAJIB pakai command gate (tanpa -lole32).
- Log ke UI host via PostMessage WM_APP+2 (heap wstring*, dibebaskan
  WndProc). Compile terverifikasi lokal varian gate (exit 0) + standalone.
- HP: teks panel transfer diubah (kirim = otomatis ke Download saat agent
  aktif; tanpa agent tetap drive tsclient).

**2. Back 1x langsung ke beranda:**
- Sesi CONNECTED: back = putus + langsung ke beranda TANPA dialog (logika
  dialog diekstrak jadi performDisconnect(), dipakai dialog & back).
  Tombol "Putuskan" HUD tetap pakai konfirmasi (anti sentuhan sengaja).
- Back saat dialog konfirmasi terbuka = KONFIRMASI (dulu = batal, sehingga
  back tidak pernah bisa keluar). Sama untuk dialog batalkan-koneksi.
- AddDeviceScreen: back saat prompt "buang perubahan?" = benar-benar keluar
  (dulu cuma menutup prompt = jebakan back tak berujung).

**3. Polish UI:**
- Profil: kartu hero identitas (logo bulat, XyDesk Remote + XyVerse, chip
  versi & jumlah perangkat) + subtitle tiap kartu seksi.
- Menu utama (popup & sidebar): label "MENU", subtitle tiap seksi + alat.
- Feed: kartu sambutan sebelum daftar postingan.
- Gate: bac0e87 gagal link (CoTaskMemFree/ole32) → fix 94a05c0 → run
  38929529708 SUCCESS.

**Checklist rilis BERIKUTNYA (v1.1.17 atau rebuild v1.1.16 — tunggu putusan
pemilik):**
- [ ] File dari HP masuk otomatis folder Download PC saat Host Agent jalan
- [ ] Back 1x dari sesi = putus + balik beranda; back#2 di dialog = konfirmasi
- [ ] Form tambah/edit perangkat: back 2x keluar (tidak terjebak prompt)
- [ ] Profil hero + subtitle seksi; menu subtitle + label; feed kartu sambutan
- [ ] CATATAN: draft v1.1.16 (id 415064151) BELUM memuat ronde 45 — host exe
      di draft belum punya watcher Downloads. Kalau mau masuk v1.1.16 harus
      re-tag + rebuild draft (putusan pemilik).

## Ronde 46 — layar Buat RDP native penuh + statistik live + persistensi sesi

Permintaan pemilik: banner AI bagus untuk layar RDP; screen lengkap (login
GitHub, template XyRDP, settings, mulai RDP, statistik RAM/disk/waktu) TANPA
bergantung web/token; dan XyRDP tanpa restart dari nol saat sesi habis.

**Template repo xykal/XyRDP (commit 5452e1b):**
- `keepalive.ps1`: heartbeat 5 menit → **statistik live tiap 60 detik**
  (ram/disk/cpu/uptime_menit/updated_at) ditulis ke `statistik` di
  out/rdp-status.json lalu di-publish ke branch `status` — app baca langsung
  via GitHub API, nol web perantara.
- **Persistensi sesi**: `persist-state.ps1` (zip Desktop/Documents/Downloads/
  Pictures/Music/Videos profil user RDP → out/xyrdp-state.zip) +
  `restore-state.ps1` (unduh artifact `xyrdp-state` terbaru milik run
  sebelumnya via API, ekstrak balik ke profil). rdp-6h.yml: step
  "Pulihkan sesi sebelumnya" setelah setup dasar; step "Simpan sesi" +
  upload-artifact@v4 (retention 90 hari) di Finalize (always). Gagal restore
  = non-fatal. cleanup-actions menyimpan 3 run terbaru → artifact aman.
- Catatan: upload-artifact@v4 dipakai tag (konvensi repo ini, bukan SHA).

**App XyDesk-Remote (f5dc7e2 + fix 03f8d9d, gate 38932734097 SUCCESS):**
- Banner AI `drawable-nodpi/xyrdp_banner.webp` (1280×480, 57KB) di puncak
  layar Buat RDP.
- `RdpFreeScreen.kt` REWRITE TOTAL — jalur native GitHub murni:
  login device flow (kode + buka browser), fork template XyRDP otomatis +
  enable workflow, pengaturan sesi (durasi 15-360 menit, hostname, username
  RDP, akses tailscale/portmap, storage boost, debloat — tersimpan di
  SharedPreferences), Mulai/Stop RDP (dispatch + cancel), status run +
  koneksi (tunnel address, Tailscale IP, RustDesk ID, expiry), **kartu
  statistik live** (bar RAM/Disk/CPU + uptime + updated_at, refresh 15 dtk,
  pause saat background), dan connect (password manual + mik switch).
  JALUR WEB/TOKEN LAMA (rdpfree.projectkal.my.id) TIDAK DIPAKAI LAGI di
  layar ini; RdpFreeApi tetap ada sebagai legacy.
- `XyRdpClient`: dispatch(token, login, inputs), cancelRun, latestRunId,
  XyRdpAccess + XyRdpStats (parse objek `statistik`).
- `XyProfileScreen`: githubDeviceStart/githubDevicePoll/GITHUB_OAUTH_CLIENT_ID
  private → internal (dipakai RdpFreeScreen).

**Checklist rilis berikutnya — tambahan ronde 46:**
- [ ] Layar Buat RDP: login GitHub → fork → settings → mulai → statistik live
- [ ] Statistik RAM/disk/CPU/uptime muncul di app saat sesi XyRDP aktif
- [ ] Sesi baru XyRDP memulihkan folder pengguna sesi sebelumnya (artifact)
- [ ] Banner XyRDP tampil di layar Buat RDP
- [ ] CATATAN: butuh APK build baru (draft v1.1.16 belum memuat r45+r46);
      template XyRDP terbaru aktif untuk run baru sekarang juga.

## Ronde 47 — 2026-10-16

**Status: SELESAI** (banner + web ABI + chat global; gate hijau; worker live).

Empat workstream user, semuanya ditangani:

1. **Banner XyRDP diganti** — gaya lama (neon) ditolak user. Banner baru
   `xyrdp_banner.webp` (1280×480, 38 KB): gaya morphing/modern/clean/lembut —
   blob gradient pastel + mascot robot di meja kecil + bentuk 3D lembut
   (awan/folder/bubble/roket), tanpa neon/glow.
2. **"Aplikasi tidak valid"** — tidak ada fix kode (diagnosis ronde 43 berlaku:
   CI sudah benar, apksigner v1+v2+v3). Penyebab sisi user: signature mismatch
   versi <v1.1.13 (wajib uninstall dulu), salah ABI, Play Protect/ROM China,
   unduhan korup, Android < 6. Halaman /abi kini membantu kasus salah ABI.
3. **Web `/abi` diperkuat** (`web/abi.html`):
   - Deteksi: Client Hints (utama) → User-Agent → **BARU fallback ke-3:
     renderer GPU WebGL** (Adreno/Mali/PowerVR = ARM, Intel/AMD = x86) —
     menutup celah Firefox Android modern yang UA-nya tak menulis arsitektur.
   - Layout: kartu verdict + tabel varian APK **di tengah** (max-width 720,
     teaks center), tombol unduh justify-center.
   - **Riwayat versi**: kartu baru yang fetch `/api/releases` (endpoint worker
     BARU: GitHub API releases, filter published, top-10, cache edge 5 menit,
     pakai GH_TOKEN worker).
4. **Chat Global (screen BARU dari Feed)** — realtime ala WA tapi identitas
   XyDesk (colorScheme Material3, BUKAN hijau WA):
   - Worker: Durable Object **`GlobalChat`** (migrasi v5, binding GLOBAL_CHAT),
     WebSocket `/ws/chat` (run_worker_first), riwayat 100 pesan di DO storage
     (trim otomatis), rate limit 8 pesan/20 dtk + teks ≤500 + nama ≤24,
     presence count, relay sinyal typing, broadcast echo ke pengirim.
     Endpoint tambahan `/api/chat/history` (REST). CATATAN debugging:
     forward DO harus kirim `request` asli (`new Request(url, request)`
     men-strip header Upgrade → 404); 404 sesaat setelah deploy = propagasi.
   - App: `GlobalChatScreen.kt` (OkHttp WebSocket — dep baru okhttp 4.12.0),
     entry tombol "Chat Global" di Feed, route `XyRoute.ChatGlobal`. Fitur:
     riwayat, bubble kiri/kanan, nama+jam, indikator "sedang mengetik…",
     jumlah daring, auto-reconnect backoff, ping 20 dtk, nickname otomatis
     "Tamu-XXXX" (SharedPreferences), pesan pending → diganti echo server.
   - **Push notif: BELUM** — app tak punya FCM/Firebase; butuh kunci Firebase
     dari user untuk push sejati. Saat ini notifikasi hanya berlaku in-app.

**Kesalahan build ronde 47 (tercatat agar tak diulang):**
- Commit pertama (4b601e7) gagal gate: (1) `import
  androidx.compose.foundation.layout.weight` — weight itu member RowScope/
  ColumnScope, bukan import top-level; (2) `currentCoroutineContext().
  invokeOnCompletion` tidak ada — lifecycle socket pakai DisposableEffect.
- Fix e988ef0 → gate Build APK SUKSES.
- Worker: helper bernama `githubTokenCandidates` (bukan githubTokens) —
  salah nama = error 1101.

**Deploy worker (2026-10-16):** version terakhir `e1304382-3afd-4b6b-8376
-28b24adb5c65` — membawa GlobalChat v5, /api/releases, /api/chat/history,
abi.html baru. Verifikasi live: /api/releases OK (v1.1.15…), /api/chat/history
OK, WS echo OK (presence+history+msg), /abi 200.

**Checklist rilis berikutnya — tambahan ronde 47:**
- [ ] Banner XyRDP baru (morphing lembut) tampil di layar Buat RDP
- [ ] Tombol "Chat Global" di Feed membuka chat realtime; kirim/terima pesan,
      typing indicator, dan jumlah daring berfungsi
- [ ] Halaman /abi: deteksi GPU fallback, list download tengah, riwayat versi
- [ ] CATATAN: draft v1.1.16 (415064151) belum memuat r45–r47 — butuh build
      rilis baru; publish hanya setelah konfirmasi user.
- [x] Push notif chat global — selesai di ronde 48 (lihat di bawah).

## Ronde 48 — Push Notif Chat Global (OneSignal + Balas dari Notif ala WA), Fix Blokir Keras Play Protect, & Log XyDesk 1-per-1 (2026-10-16)

Tiga permintaan pemilik ditangani tuntas:

1. **Push Notif Chat Global + Direct Reply ("Balas" dari Notif ala WhatsApp)**:
   - OneSignal App ID `ffaa87d6-8e1d-42b6-bb4f-ebbbfc0bccbb` dipasang di
     `ChatPushNotifier.kt` (init dari `XyApp.onCreate()`) + secret
     `ONESIGNAL_REST_API_KEY` di Cloudflare Worker (`GlobalChat.pushChatNotification`).
   - Interseptor `ChatNotificationExtension` (`INotificationServiceExtension`)
     mengubah push standar menjadi `NotificationCompat.MessagingStyle` gaya
     percakapan grup WhatsApp: avatar lingkaran inisial warna per pengirim,
     tumpukan hingga 6 pesan belum dibaca, tombol **Balas** langsung dari
     bar notifikasi (`RemoteInput` → `ChatReplyReceiver` → `POST /api/chat/send`
     tanpa perlu membuka aplikasi), tombol **Tandai dibaca**, dan ketuk
     notifikasi langsung membuka layar **Chat Global** (`EXTRA_OPEN_CHAT_GLOBAL`).
   - Dilengkapi watcher ringan cadangan (`/api/chat/history`) dengan deduplikasi
     ID pesan sehingga perangkat tanpa GMS / saat push tertunda tetap mendapat
     notifikasi percakapan secara instan.
   - Fix bug ronde 47 di `GlobalChatScreen.kt`: `chatSocketSend` sebelumnya
     membungkus ulang string JSON ke dalam `{"type":"msg","text":...}` (double-wrap).

2. **Fix Blokir Keras Google Play Protect (Tanpa Tombol "Tetap Instal" / "Scan")**:
   - **Akar masalah ditemukan**: `freeRDPCore/src/main/AndroidManifest.xml`
     warisan upstream mendeklarasikan `<service android:name=".presentation.KeyboardAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">`
     beserta 8 activity warisan yang tidak pernah dipakai XyDesk Remote
     (`SessionActivity.activeSession` selalu null). Di Indonesia (sejak
     diberlakukannya **Google Play Protect Enhanced Fraud Protection / EFP**),
     setiap APK sideload dari browser/WhatsApp/file manager yang meminta
     `BIND_ACCESSIBILITY_SERVICE` (atau `READ_SMS`/`RECEIVE_SMS`/
     `BIND_NOTIFICATION_LISTENER_SERVICE`) otomatis **diblokir keras tanpa
     opsi "Tetap instal" maupun "Scan"**.
   - **Perbaikan**: `KeyboardAccessibilityService` dan seluruh activity UI
     warisan yang tidak terpakai dihapus dari `freeRDPCore/src/main/AndroidManifest.xml`,
     ditambah guard eksplisit `tools:node="remove"` di `app/src/main/AndroidManifest.xml`
     untuk `BIND_ACCESSIBILITY_SERVICE`, `BIND_NOTIFICATION_LISTENER_SERVICE`,
     `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `SYSTEM_ALERT_WINDOW`, dan
     `REQUEST_INSTALL_PACKAGES` + unit test penjaga di `worker.test.mjs`.

3. **Log FreeRDP Dihapus dari Tampilan & Dipindah Menyatu ke Log XyDesk 1-per-1**:
   - Seksi terpisah `=== LOG NATIVE (FreeRDP) ===` dihapus dari layar log.
   - `ConnectionLog.syncNativeIntoXyDeskLog()` otomatis menyerap peristiwa
     penting dari engine native (audio, clipboard, drive, resolusi, TLS, error),
     menerjemahkannya ke bahasa XyDesk yang jelas, lalu menyatukannya ke dalam
     **Log XyDesk**.
   - `ConnectionLog.formattedXyDeskLog()` menyusun setiap langkah secara berurutan
     satu per satu (`1.`, `2.`, `3.`, ...) dengan label bersih (`[Aplikasi]`,
     `[Sesi]`, `[Koneksi]`, `[Engine XyDesk]`, `[Audio]`, `[Sukses]`, `[Gagal]`),
     dan `LogSheet.kt` merendernya sebagai baris kartu satu per satu yang
     mudah dibaca + tombol **Salin Log XyDesk**.

4. **Deteksi Negara Otomatis + Terjemahan 2 Bahasa di Dalam 1 Bubble (Dipisah Garis)**:
   - Worker (`GlobalChat`) mendeteksi negara pengirim dari edge Cloudflare
     (`request.cf.country` / `cf-ipcountry`) dengan fallback locale klien,
     menghasilkan bendera emoji + kode negara (`🇮🇩 ID`, `🇺🇸 US`, `🇲🇾 MY`, dst.).
   - Setiap pesan otomatis diterjemahkan dua arah (Indonesia/Melayu → `EN`,
     Inggris/bahasa asing lain → `ID`) oleh Worker (`translateChatText` via GTX +
     fallback kamus offline) serta cadangan di klien (`translateClientSide`).
   - Di `GlobalChatScreen.kt`, **1 bubble** menampilkan:
     1) Header pengirim + bendera & kode negara (`🇮🇩 Tamu-1234 · ID`),
     2) Teks asli di bagian atas,
     3) **Garis pemisah horizontal tipis** di tengah bubble,
     4) Hasil terjemahan di bagian bawah (`🌐 EN · ...` atau `🌐 ID · ...`).

5. **Badge Khusus `👑 FOUNDER` untuk Akun GitHub `xykal` (`FounderBadge.kt`)**:
   - Komponen `XyFounderBadge` bergaya emas-royal dengan border gradien berkilau
     (`#FFE082` → `#FFB300` → `#FF6F00`) dan label **`👑 FOUNDER`**.
   - Otomatis tampil saat akun GitHub `xykal` login / aktif di:
     1) **Kartu Akun GitHub di Layar Buat RDP** (`RdpFreeScreen.kt`) lengkap dengan
        badge `👑 FOUNDER · Pemilik & Kreator XyVerse`.
     2) **Kartu Profil & Akun Komunitas** (`XyProfileScreen.kt`) lengkap dengan
        cincin emas pada foto profil + badge `👑 FOUNDER`.
     3) **Chat Global** (`GlobalChatScreen.kt` + `cloudflare/worker.mjs` `GlobalChat`):
        header layar, header bubble pesan `xykal` (`🇮🇩 xykal · ID [👑 FOUNDER]`),
        border emas pada bubble pesan Founder, serta label `👑 🇮🇩 xykal [FOUNDER]`
        pada notifikasi push.
     4) **Feed Komunitas & Komentar** (`FunHubCard.kt`): di samping nama `xykal`
        pada setiap postingan maupun komentar.

**Checklist rilis berikutnya — tambahan ronde 48:**
- [ ] APK rilis baru tidak lagi kena blokir keras Play Protect EFP (tidak ada `BIND_ACCESSIBILITY_SERVICE` di manifest APK)
- [ ] Notifikasi Chat Global muncul gaya WhatsApp (`MessagingStyle`), tombol **Balas** dari notifikasi langsung mengirim pesan ke Chat Global, ketuk notif membuka layar Chat Global
- [ ] Chat Global: deteksi bendera negara otomatis + 1 bubble berisi 2 teks (asli di atas + garis pemisah + terjemahan di bawah)
- [ ] Badge `👑 FOUNDER` emas berkilau tampil otomatis untuk akun GitHub `xykal` di Profil, Buat RDP, Feed/Komentar, dan Chat Global
- [ ] Layar Log XyDesk (dan tombol Detail saat gagal koneksi) menampilkan langkah bernomor 1 per 1 tanpa blok log FreeRDP terpisah

## Ronde 51 — Sinkronisasi XyRDP, Validasi Monitor & Sesi, Mode Game FPS 120Hz 360°, & Engine Konsol Non-RDP `SM_REMOTESESSION=0` (2026-10-16)
1. **Sinkronisasi Penuh `xykal/XyRDP` ↔ `XyDesk-Remote` & Validasi Monitor/Sesi Nyata**:
   - `publish-status.ps1` di `XyRDP` kini menulis ke `rdp-status.json` sekaligus `out/rdp-status.json` di branch `status`; `setup-akses.ps1` dan `setup-portmap.ps1` menyertakan blok `akses.tunnel` standar untuk semua mode transport (`tailscale`, `portmap`, `tunnel`).
   - `XyRdpClient.kt` dan `RdpFreeScreen.kt` diperbarui dengan `inspectSetup`, `syncForkWithUpstream` (`/merge-upstream`), `pollValidatedSession`, dan `evaluateSession` yang memvalidasi silang `run.id`, step workflow GitHub Actions, `expires_at`, serta kesegaran `heartbeat_at`.
   - Tab **Monitor & Sesi** kini menampilkan progress bar tahapan workflow live, hitung mundur sisa waktu per detik (`HH:MM:SS`), serta telemetri runner nyata (`RAM`, `Disk C:`, `CPU %`, `Uptime`).
2. **Upgrade Koneksi RDP & Koneksi PC untuk Game FPS (120 FPS · Ultra-Low Latency · 360° RawInput Aim)**:
   - Menghapus delay buatan 150 ms pada `SessionInputManager.java` (`sendDelayedMoveEvent` kini langsung mengirim event tanpa menunda/membuang 3 dari 4 sampel sentuh).
   - Membuka batas `targetFps` hingga `120 Hz` di `RdpOptions.kt`, menurunkan batas `responsiveLoopMs` ke `1..1000 ms` (`2 ms` pada `ULTRA_LOW_LATENCY` & `DIRECT_GAME_ULTRA`), serta mengunci `preferredDisplayModeId` + `preferredRefreshRate` di `XyDeskSessionActivity.kt` & `PcStreamSessionActivity.kt`.
   - Menambahkan **`XyRawGameInputBridge.kt`** (`XYI1` via UDP `:4433`) + toggle **Mode Game FPS (120Hz · Rotasi 360° RawInput)** + crosshair tengah presisi di `XyDeskSession.kt` dan `SessionControls.kt`.
3. **Engine Streaming Konsol Mandiri Non-RDP (`SM_REMOTESESSION = 0`) di `XyDeskRemoteHost-Standalone.exe` & `PcStreamSessionActivity.kt`**:
   - `host-agent/src/xydesk_host_core.cpp` membuka `DWMFRAMEINTERVAL = 8` (125 FPS), `TcpAckFrequency = 1` / `TCPNoDelay = 1` di semua interface jaringan, dan prioritas MMCSS `Games` (`GPU Priority = 8`).
   - `host-agent/src/xydesk_quic.cpp` dilengkapi handler input hardware `XYI1` (`SendInput` `MOUSEEVENTF_MOVE` relatif murni tanpa `MOUSEEVENTF_ABSOLUTE`) dan engine capture layar konsol fisik `XYV1` (`SM_REMOTESESSION == 0`, JPEG GDI+ in-memory berkecepatan tinggi di UDP `:4433`).
   - `PcStreamSessionActivity.kt` & `XyDirectConsoleStreamClient.kt` menyediakan mode **Konsol UDP :4433 (Non-RDP)** dengan pemindai otomatis PC di LAN/Tailscale serta overlay kombat FPS (`FPS 360°`, `TEMBAK LMB`, `SCOPE RMB`, `WASD`, `SHIFT`, `CTRL`, `SPACE`, `R`).
4. **Rilis Publik `v1.1.16` (Build 77) — Terbit & Live**:
   - Tag `v1.1.16` (`289fc8a44d198c0a2ae047bfda40cf6b43bb21bb`) lulus gate `Web Worker checks` (`38959679620`), `Build APK` (`38959679734`), dan workflow rilis penuh (`38960176293` — APK per-ABI R8 + signed v1/v2/v3 + Windows Host Agent C++17 `XyDeskRemoteHost-Standalone.exe`).
   - GitHub Release `415064151` (`v1.1.16`) telah dipublikasikan (`draft: false`) beserta `web/release-state.json` dan Cloudflare Worker (`e54b7e2f-3a68-4059-9c56-16ca3c6c3cf7`) di `https://xydeskremote.biz.id`.

## Ronde 52 — Navigasi Sesi 1x Klik, Login GitHub Instan Tanpa Kode, Header Ikon Profil/GitHub, Redesain Badge Founder, Rombak Total Chat Global (WhatsApp UX + Tema XyDesk + Semua Bahasa + Floating Bubble Sesi), Banner & Settings Lengkap Buat RDP, serta PC Stream Murni + Boost Koneksi RDP Utama (2026-10-16)
1. **Perbaikan Bug "Kembali ke Beranda" dari Sesi & Boost Kecepatan Koneksi RDP Utama + PC Stream Murni**:
   - `XyDeskSessionActivity.kt`, `XyDeskSession.kt`, dan `XySessionService.kt`: memperbaiki bug klik "Kembali ke Beranda" yang memantul balik ke sesi dengan `markIntentionalHomeNavigation()`, `markExplicitDisconnect(profile.id)`, `appPrefs.clearAutoResumeSession()`, `.putExtra(EXTRA_SKIP_AUTO_RESUME, true)`, `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP`, serta penjagaan di `onStop()` agar hanya menyimpan auto-resume saat sesi benar-benar ditinggalkan tanpa sengaja.
   - `SessionSurfaceController.kt` & `XyDeskSessionActivity.kt`: boost kecepatan render & latensi **Koneksi RDP Utama** dengan akselerasi `View.LAYER_TYPE_HARDWARE` pada `SessionView`, immediate dirty-region flush (`uiHandler.post(flushDirtyRunnable)` tanpa penundaan 1 frame VSYNC saat `!batterySaverMode`), serta penguncian refresh rate layar tinggi (120Hz).
   - `PcStreamSessionActivity.kt`: `PairingStage()` disederhanakan menjadi `PC Stream` murni (9-digit Host ID + 6-digit PIN) tanpa opsi/tab UDP di layar PC Stream.
2. **Login GitHub Instan Tanpa Ketik Kode, Ikon Header Atas (Foto Profil, Ganti Nama, Login GitHub), & Redesain Badge Founder**:
   - `XyProfileScreen.kt`: `GitHubDeviceFlowPanel` dirombak menggunakan `WebView` terintegrasi yang otomatis menginjeksikan `user_code` via JavaScript begitu halaman `/login/device` terbuka (pengguna cukup menekan tombol hijau **Authorize** sekali tanpa perlu menyalin/mengetik kode). Ditambahkan `syncGitHubProfileAndAvatar(context, token)` untuk menyinkronkan username & avatar GitHub otomatis.
   - `XyHeaderAccountActions` ditambahkan di Header Atas (`XyTopBar`) pada Beranda (`XyDeskHome.kt`), Buat RDP (`RdpFreeScreen.kt`), Feed (`XyFeedScreen.kt`), Profil (`XyProfileScreen.kt`), dan Chat Global (`GlobalChatScreen.kt`) berupa ikon Logo GitHub (Octocat + titik indikator status) dan Avatar Profil melingkar yang membuka pop-up cepat ganti Foto Profil, ganti Nama Tampilan, dan Login/Keluar GitHub.
   - `FounderBadge.kt`: `XyFounderBadge` didesain ulang dengan estetika **Midnight Obsidian (`#0B1120` → `#172554` → `#1E1B4B`) + Champagne Gold (`#FDE68A` → `#F59E0B`)** dan ikon vektor mahkota geometris (`FounderCrownEmblem`).
3. **Banner Buat RDP di Beranda & Kelengkapan Settings Buat RDP (`RdpFreeScreen.kt`)**:
   - `XyDeskHome.kt`: menambahkan `HomeRdpFreeBannerCard` (`R.drawable.xyrdp_banner`, desain modern/lembut + mascot) di atas layar Beranda.
   - `RdpFreeScreen.kt`: melengkapi bagian **Secret Configuration & Link Resmi** (`RDP_PASSWORD`, `TAILSCALE_AUTH_KEY`, `PORTMAP_OVPN_CONFIG`, indikator status masing-masing secret, tombol salin nama secret, generator password acak, dan tombol buka langsung halaman resmi Tailscale Keys, Portmap.io, & GitHub Secrets Repo) serta seluruh 9 checklist/toggle `rdp-6h.yml` + preset `⚡ Profil Cepat` dan `🎮 Profil Game Lengkap`.
4. **Rombak Total Chat Global (Fitur Mirip WhatsApp Bertema XyDesk + Semua Bahasa & Deteksi Negara Akurat + Floating Bubble Saat Sesi)**:
   - `GlobalChatScreen.kt` & `cloudflare/worker.mjs` (`1ef3fb58-937b-4a61-8b8b-1304c5d325b8`):
     - **Wajib Login GitHub** untuk mengirim pesan, gambar, file, maupun stiker di Chat Global, dengan **Dual-Transport** (HTTP `/api/chat/history` + `/api/chat/send` + WebSocket live) yang dijamin tidak macet.
     - **Fitur Mirip WhatsApp Bertema XyDesk**: mendukung **Swipe-to-Reply** (geser bubble ke kanan untuk membalas dengan kutipan pesan), **Attach Foto/Gambar** (pratinjau di dalam bubble + zoom layar penuh), **Attach File/Dokumen** (kartu unduh/bagikan), dan **12 Stiker XyDesk**.
     - **Deteksi Negara Akurat & Terjemahan Semua Bahasa (2-in-1 Bubble)**: memperbaiki bug pengguna berbahasa Indonesia yang terdeteksi `US` dengan `chatCountryCode` (SIM/Network ISO + TimeZone + Bahasa) dan `resolveSmartCountry` (deteksi kosakata & aksara), serta mendukung terjemahan lintas semua bahasa (`ID`, `EN`, `TH`, `JA`, `KO`, `ZH`, `VI`, `TL`, `MS`, `AR`, `RU`, `HI`, `ES`, `PT`, `DE`, `FR`, `TR`) via `clients5.google.com/translate_a/t?client=dict-chrome-ex` dalam 1 bubble terpisah garis.
     - **Floating Bubble Chat Global Saat Sesi (`FloatingSessionChatOverlay`)**: dipasang di `XyDeskSessionActivity.kt` dan `PcStreamSessionActivity.kt` sehingga pengguna dapat menggeser gelembung `💬` dan membuka Chat Global langsung di atas sesi RDP/PC Stream tanpa memutus sesi.
   - Gate `main` (`ff7af9c`) hijau penuh: `Web Worker checks` (`38970707919`) = `success`, `Build APK` (`38970707943`) = `success`.

## Ronde 53 — Akselerasi Native FreeRDP untuk Game, XyRDP Support GTA SA-MP By SilentPatch & Minecraft OpenGL 4.6 D3D12, Anti-Reset Lintas Sesi, & Rilis Publik `v1.1.17` (Build 78) (2026-10-16)
1. **Akselerasi Native `libfreerdp` & `client/Android` untuk Game & Kecepatan Maksimal (`XyDesk-Remote`)**:
   - `channels/rdpgfx/client/rdpgfx_main.c`: `rdpgfx_recv_end_frame_pdu` kini mengirimkan `RDPGFX_FRAME_ACKNOWLEDGE_PDU` dan `RDPGFX_QOE_FRAME_ACKNOWLEDGE_PDU` (`timeDiffSE = 1`, `timeDiffEDR = 1`) **sebelum** `context->EndFrame` (scaling & JNI blit), sehingga server Windows RDS langsung menyiapkan frame berikutnya secara paralel tanpa tertahan RTT jaringan.
   - `client/Android/Studio/freeRDPCore/src/main/cpp/android_freerdp.c`:
     - Mengaktifkan `FreeRDP_HasRelativeMouseEvent = TRUE`, `FreeRDP_GfxSmallCache = FALSE` (cache surface 100 MB penuh), `FreeRDP_GfxThinClient = FALSE`, `FreeRDP_GfxSendQoeAck = TRUE`, `FreeRDP_FrameAcknowledge = 8`, dan `FreeRDP_MultifragMaxRequestSize = 4194304` (4 MB).
     - `android_freerdp_run` kini menguras antrean input `android_check_handle(instance)` **sebelum** maupun **sesudah** `freerdp_check_event_handles(context)` agar input bidikan mouse/keyboard game terkirim tanpa menunggu dekode frame video.
   - `libfreerdp/core/tcp.c`: mengaktifkan `TCP_QUICKACK` (termasuk re-arm otomatis setelah setiap `_recv > 0` di Linux/Android untuk menghilangkan penundaan 40 ms Delayed ACK), `IPTOS_LOWDELAY`, serta menaikkan buffer socket `SO_RCVBUF` ke 1 MB dan `SO_SNDBUF` ke 512 KB.
2. **Modifikasi `xykal/XyRDP` (`dfe59c5a3d9d8c5ce36f7c856d0760b597a09268`) untuk Support GTA SA-MP By SilentPatch & Minecraft OpenGL 4.6 D3D12**:
   - `scripts/setup-samp.ps1`: selain memasang penuh DirectX 9.0c June 2010, VC++ 2013 & 2015–2022 (x86/x64), DirectPlay, dan NetFx3, kini menyiapkan paket **SilentPatchSA (`SilentPatchSA.asi` + `SilentPatchSA.ini` 90 FPS + `FixMouseStuck=1`)**, **SilentPatch DDraw (`ddraw.dll`)**, **Ultimate ASI Loader (`dinput8.dll` & `vorbisFile.dll`)**, **GTASA.WidescreenFix**, **open.mp / SA-MP Launcher**, serta script auto-patcher `C:\Games\GTA-SAMP\Patch-GTA-SAMP.ps1` + shortcut Desktop yang otomatis mem-patch folder GTA San Andreas dan mengatur `HKCU\Software\SAMP`.
   - `scripts/setup-grafis.ps1` & `scripts/setup-games.ps1`: mengekstrak **Mesa3D OpenGL 4.6 (`GALLIUM_DRIVER=d3d12`)** ke `C:\XyRDP\grafis\mesa-x64`, memasang **Eclipse Temurin JDK 21 LTS (x64)** & **JRE 8 LTS (x64)**, **PrismLauncher Portable**, serta **Minecraft Launcher resmi**, lalu menyuntikkan otomatis `opengl32.dll` + `libgallium_wgl.dll` + `libglapi.dll` + `dxil.dll` ke seluruh folder `bin/` runtime Java (`Deploy-OpenGL-To-Java.ps1`) agar Minecraft Java berjalan lancar tanpa error `GLFW error 65542`.
3. **Sistem Anti-Reset Lintas Sesi (`persist-state.ps1`, `restore-state.ps1`, `keepalive.ps1`, `setup-rdp.ps1`, `rdp-6h.yml`)**:
   - Memperbaiki batas durasi `setup-rdp.ps1` ke `342` menit agar `persist-state.ps1` selalu sempat berjalan sebelum hard-timeout 360 menit GitHub Actions.
   - Menyimpan & memulihkan **`C:\Persist`** (dengan shortcut Desktop `Folder Permanen (Anti-Reset)`), **`C:\Games`**, **`%APPDATA%\.minecraft`**, **`PrismLauncher`**, **`Desktop..Videos`** (termasuk `Documents\GTA San Andreas User Files`), **Registry `HKCU\Software\SAMP`**, serta **state identitas Tailscale (`C:\ProgramData\Tailscale`)** menggunakan kompresi cepat `7z.exe -mx=1 -mmt=on`.
   - Menggunakan **Penyimpanan Ganda (Dual-Storage)** ke **GitHub Release tag `xyrdp-state-latest`** (tahan terhadap `cleanup-actions.ps1`) sekaligus **Artifact `xyrdp-state`**, ditambah **Auto-Checkpoint setiap 15 menit** di `keepalive.ps1` dan **Estafet Sesi Otomatis (`perpanjang_otomatis`)** di `rdp-6h.yml`.
   - Terverifikasi lulus 100% di CI `xykal/XyRDP`: `Validate Windows dependencies` (`38977115792`), `Validate desktop diagnostics` (`38977117636`), dan `Validate desktop maintenance` (`38977119627`) = `completed / success`.
4. **Rilis Publik `v1.1.17` (Build 78) — Terbit & Live**:
   - Commit `cc33d3827a851169c47ef5a1aee33575462f96e0` (tag `v1.1.17`) lulus penuh: `Web Worker checks` (`38976865028`) = `success`, `Build APK (main)` (`38976864896`) = `success`, dan `Build APK (v1.1.17)` (`38976865032`) = `success` (semua APK per-ABI + Universal + Windows Host Agent terverifikasi `PASS (V1 + V2)` dengan sertifikat `8429f689e7c3c6a0a44cb924206d56d5a2dccbd6c88cb900835c1901ad219652`).
   - GitHub Release `415451965` (`v1.1.17`) telah dipublikasikan (`draft: false`, `make_latest: true`) di `https://github.com/xykal/XyDesk-Remote/releases/tag/v1.1.17`, dan Cloudflare Worker (`29feb28e-8095-4e1a-b3dd-5aac2c69459f`) bersama `web/release-state.json` telah live di seluruh 8 domain resmi.






## Ronde 54–55 — Rilis Publik `v1.1.18` (`VERSION_CODE 79`, Commit `bc0af340301dd8c25ec8b4377b52fd5075e2e5e6`, CI `39034034723`)
- **Perbaikan Bug Sesi Relog / Keluar Saat Main Game (`XyDeskSession.kt`, `XyDeskSessionActivity.kt`, `AndroidManifest.xml`):**
  - Watchdog `zeroFrameStreak` kini hanya berjalan sebelum frame pertama tergambar (`!everRenderedFrame`), mencegah false-positive relog saat layar game/loading statis (`telemetry.fps == 0`).
  - Menambahkan `keyboard|keyboardHidden|navigation|touchscreen|screenSize|smallestScreenSize|density|screenLayout|uiMode|fontScale|layoutDirection|colorMode|mcc|mnc` + `singleTask` di `AndroidManifest.xml` agar colok/cabut mouse/gamepad OTG/Bluetooth tidak menghancurkan Activity sesi.
- **Konsistensi UI & Safe Area Semua Layar (`XyDeskHomeActivity.kt`, `XyDeskHome.kt`, `SessionControls.kt`, `GlobalChatScreen.kt`, `PcStreamSessionActivity.kt`):**
  - Menerapkan `statusBarsPadding()`, `navigationBarsPadding()`, dan `displayCutoutPadding()` secara konsisten serta normalisasi `LocalDensity` (`fontScale` `0.85f..1.10f`).
- **Chat Global E2EE, Custom Sticker `.webp.crypto15` & Floating Chat Liquid Glass Hitam-Putih:**
  - Sematkan Pesan (`/api/chat/pin` + WebSocket `pin`), Salin Pesan, Kirim Video (`video/*`), Media 1× Sekali Lihat (`viewOnce`), serta Pratinjau Foto & Video Layar Penuh (`FullScreenChatMediaViewer`) dengan Pinch-to-Zoom (`1x`–`5x`), Double-Tap Zoom, Video Player, dan Simpan ke Galeri.
  - Stiker Custom WebP Terenkripsi `.webp.crypto15` di `Android/media/id.xydesk.remote/globwlchat/sticker/<Token>+2+$+$8.webp.crypto15` & `globalchat/sticker/`.
- **Linux Polos (Tanpa XFCE4) + Terminal SSH-2, On-Mic (`AUDIN` OpenSL ES + `onmic.ps1` / `XyDesk-RDP-OnMic-Setup.bat`), & Native GUI Windows Host Agent (`XyDeskRemoteHost.exe`).**

## Ronde 56 — Rilis Publik `v1.1.19` (`VERSION_CODE 80`, Commit `65beb569f144c44cb6185eb9be9252d68d9b0047`, CI `39041215022`) (2026-10-17)
- **Perbaikan `xykal/XyRDP` (`c548e53` & `cc22a90`) + Sinkronisasi Otomatis Fork (`XyRdpClient.kt`):**
  - Memperbaiki kegagalan `Tailscale tersambung tetapi listener RDP 3389 belum aktif; periksa setup-rdp` dengan memblokir `Grant-KeyAccess`/`New-Item -Force` pada `WinStations\RDP-Tcp` (yang sebelumnya merusak owner/ACL sehingga `TermService` gagal membuka port `3389`), menambahkan auto-recovery `TermService`/`UmRdpService` di `setup-rdp.ps1` dan `setup-akses.ps1`, serta membuktikan live workflow run `39038435063` lulus penuh (`rdp_listen: ":::3389 (svchost) + handshake ok via 127.0.0.1"`, Tailscale `100.83.90.63`).
  - Menambahkan kompatibilitas penuh semua software 2026 (`compat2026: true`, `LongPathsEnabled`, Developer Mode, SmartScreen bypass, VC++ 2013/2015–2022, WebView2), auto-sync fork `XyRDP` ke upstream saat dispatch sesi di aplikasi, dan auto-runner `ubuntu-24.04` saat `linux_mode=polos`.
- **Perbaikan Chat Global (`GlobalChatScreen.kt`, `XyDeskHome.kt`, `cloudflare/worker.mjs`):**
  - Menyembunyikan `XyBottomNav` saat berada di halaman Chat Global, merapikan ukuran bubble secara presisi dengan `IntrinsicSize.Max`, menambahkan **Hapus untuk Semua Orang** (`/api/chat/delete` + WebSocket `delete`) dan **Hapus untuk Saya**, serta menerapkan terjemahan otomatis sesuai region di seluruh negara dunia (1 bahasa saat mengirim pesan sendiri; otomatis tampil terjemahan sesuai region penonton saat dilihat orang lain, termasuk pada **Bubble Founder `@xykal`**).
- **Login GitHub Tanpa Kode (`FounderBadge.kt`, `XyDeskHome.kt`, `RdpFreeScreen.kt`, `XyProfileScreen.kt`, `GlobalChatScreen.kt`):**
  - Mengganti mekanisme kode 8 digit dengan alur Web OAuth Authorize langsung di browser dalam aplikasi (`GitHubDirectOAuthController` & `GitHubDirectOAuthModalHost`).

## Ronde 57 — Rilis Publik `v1.1.20` (`VERSION_CODE 81`, Commit `83254a0b8577f807232c19101ecab1ccbe1b6b61`, CI `39048973039`) (2026-10-17)
- **Revisi Penuh UI/UX Feed, Banner Anti-Neon Chat Global, Tab Notifikasi & Leaderboard Top 100 (`XyFeedScreen.kt`, `FunHubCard.kt`, `CommunityJokesApi.kt`):**
  - **Banner Chat Global Anti-Neon di Feed**: Desain obsidian/slate elegan dengan jumlah user online real-time dan animasi titik-titik mengetik (`• • •`).
  - **Edit & Hapus Postingan, Komentar Ber-Avatar + Balas & `@mention`, serta Action Bar Baru**: Ikon Like, Komentar, Share, dan Reaksi Cepat gaya WhatsApp (`👍 ❤️ 😂 😮 😢 🙏 🔥`).
  - **Tab Notifikasi di Header Feed**: Kategori **Semua**, **📢 Pengumuman**, **📥 Kotak Masuk**, dan **💬 Balasan / Mention**.
  - **Pengumuman Admin (`@xykal`) Otomatis Tersemat**: Pengumuman oleh Admin memiliki kartu emas khusus dan otomatis di-pin di **Feed** sekaligus **Chat Global**.
  - **Leaderboard Top 1–100**: Podium **Top 1, Top 2, Top 3** hingga peringkat **100** berdasarkan durasi aktif menggunakan APK / bermain sesi remote (`/api/leaderboard`).
  - **Universal OpenGraph (OG) Link Preview + XyShield Anti-Malware (`/api/og`)**: Menampilkan preview banner otomatis untuk semua link (YouTube, GitHub, sosmed, web umum) dan memblokir file/tautan berbahaya.
  - **Media HD (1600px @ 90%, maks 2.8MB) & Video Autoplay Preview**: Kompresi gambar ditingkatkan ke kualitas HD dan video otomatis berputar preview (*muted loop*) di Feed maupun Chat Global.
- **Perbaikan Chat Global, Stiker Favorit Semua Pengguna, Profil & Keamanan (`GlobalChatScreen.kt`, `CustomStickerVault.kt`, `XyProfileScreen.kt`):**
  - Kirim foto/video/stiker **tanpa teks nama file**, **Swipe-to-Reply** aktif kembali, stiker dirender **tanpa bubble card & tanpa label** dengan tombol **`★ Favorit`** untuk semua pengguna, **wajib login GitHub** (tanpa mode Tamu), serta **Menu Pengaturan Chat Global** lengkap (Bersihkan Chat, Gaya Gelembung, Otomatis Unduh Media, HD Media, Autoplay Video, Nada Notifikasi, XyShield).
- **Login GitHub Sinkron Chrome (`xyrdp-dash.vercel.app` `dpl_GqLY4sbW6d9uCiUiXDSJKGty4hEr`) & Pemilih OS `xykal/XyRDP` (`d336256`):**
  - Chrome Custom Tabs + deep link `xydesk://github-auth` + polling `/api/github/oauth/poll`, serta pemisahan `--setup-only` / `--keepalive-only` / `--finalize-only` di `setup-linux-rdp.sh` agar tidak stuck di step setup dan bisa memilih **Windows Server 2022 RDP / Linux Desktop / Linux SSH** secara akurat.


