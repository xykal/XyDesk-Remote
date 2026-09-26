# XyDesk Remote — Arsitektur & Roadmap
> App Android remote desktop (klien RDP) untuk Windows, dengan sistem HUD multi-panel yang bisa dikustomisasi.
> Status: **v0.2.3 — Cloud RDP dua-fase (public-safe): rahasia via ciphertext, bukan input workflow** — 2026-09-25
> Nama kerja: `XyDesk Remote` (package: `id.xydesk.remote`)

---

## 0. Status Implementasi (M0)

| Item | Status |
|---|---|
| Repo GitHub `xykal/XyDesk-Remote` (public) | ✅ dibuat (awal private, dipublikasi v0.2.0) |
| Tree FreeRDP **3.32.0** vendored di root (pin via git) | ✅ |
| Modul `:freeRDPCore` (native + JNI, Apache-2.0) | ✅ tanpa perubahan |
| Modul `:app` — form koneksi XyDesk (host/IP, port, user, pass, domain) | ✅ |
| Koneksi via URI `rdp://user@host:port/?p=...&domain=...` → SessionActivity core | ✅ |
| CI `Build APK` (JDK 21, NDK 29, CMake 4.1.2, arm64-v8a + x86_64) | ✅ **GREEN** (commit 23ddccb, ±25 menit/run) |
| APK pertama `app-debug.apk` (universal arm64 + x86_64) | ✅ ter-generate di artifact Actions |
| `release.properties` (knob build) + docs BUILD.md + NOTICE | ✅ |

**Keputusan layout M0:** CMake superbuild FreeRDP me-resolve source tree lewat path
relatif dari folder `cpp` (7 level ke atas), sehingga repo mempertahankan layout
tree FreeRDP di root dan perubahan XyDesk dibatasi di `client/Android/Studio`
(detail: `XYDESK-REMOTE-NOTICE.md`).

**Keputusan UI M0:** render sesi sementara memakai `SessionActivity` bawaan inti
FreeRDP (jalur render + input yang sudah teruji). Form koneksi, branding, dan
launcher adalah UI XyDesk. Penggantian ke session surface + HUD XyDesk dilakukan
di M1/M2 sesuai roadmap.

**Koneksi "kayak MS Remote Desktop":** IP/hostname + port (default 3389) +
username + password (+ domain opsional), NLA/TLS default. Valid untuk remote ke
VM/server apa pun dengan RDP aktif (mis. VM self-hosted runner Windows).

---

## 1. Ringkasan Eksekutif

Klien RDP Android yang beneran kuat harus dibangun di atas **FreeRDP** (implementasi RDP open-source, Apache 2.0, masih aktif — versi yang dipakai: 3.32.0). App populer seperti aRDP/bVNC juga dasarnya ini. Jadi kita **nggak bikin protokol RDP dari nol** — yang kita bikin dari nol adalah:

1. **UI + HUD system** (bagian yang bikin app ini beda dari MS Remote Desktop),
2. **Manajemen koneksi** (favorites, keamanan, gateway, import .rdp),
3. **Integrasi native FreeRDP via JNI** dengan telemetri lengkap.

Fitur yang melampaui Microsoft Remote Desktop for Android:
- HUD multi-panel draggable (Stats, Controls, Pointer, Keyboard, File Transfer, Audio, Quick Actions, Diagnostics)
- Telemetri realtime: FPS, ping, bitrate, codec aktif
- Transfer file via drive redirection + clipboard file
- Session recording & screenshot
- Audio dua arah (playback + mic capture)
- Kontrol DPI/resolusi/pointer/shortcut tanpa keluar sesi
- Import file `.rdp`, QR code, RDP Gateway
- Multi-protocol-ready (arsitektur siap ditambah VNC / Android→Android di versi berikutnya tanpa rebuild total)

---

## 2. Keputusan Teknologi

| Item | Pilihan | Alasan |
|---|---|---|
| Bahasa UI | **Kotlin 2.x** (mulai M1; M0 = Java minimal untuk menjaga build pertama tetap hijau) | Native, kontrol penuh ke SurfaceView/hardware |
| UI toolkit | **Jetpack Compose (Material 3)** (mulai M1) | HUD custom jauh lebih mudah dari View system |
| minSdk / targetSdk | **29 / 37** | compileSdk 37 via platform stabil `android-37.2` (diwajibkankan androidx.core 1.19) |
| Native | **FreeRDP 3.32.0 via CMake superbuild** (vendored) | Dependensi (OpenSSL, FFmpeg, OpenH264, Opus, dll) di-fetch & build otomatis |
| NDK / CMake | **NDK 29.0.13113456 / CMake 4.1.2** | Persyaratan build Android resmi FreeRDP |
| AGP / Gradle | **9.2.1 / 9.6.1** | versi yang dipakai upstream FreeRDP |
| Render | Native → Bitmap → SessionView (M0); **HardwareBuffer/SurfaceView** (M4) | jalur M0 teruji; M4 upgrade ke hardware decode 4K |
| DB | **Room** (profil koneksi) + **DataStore** (layout HUD, preferensi) | inti FreeRDP sudah memakai Room (SQLCipher) |
| Kredensial | **Android Keystore (AES-GCM)** | Password nggak pernah plaintext di disk |
| Lisensi | FreeRDP = **Apache 2.0** → app boleh **closed-source** | Nggak nge-fork aRDP (GPL) |

---

## 3. Arsitektur 3-Lapis

```
┌─────────────────────────────────────────────────────────────────┐
│                      ANDROID APP (KOTLIN)                       │
│                                                                 │
│  ┌──────────────────┐  ┌───────────────────┐  ┌──────────────┐  │
│  │  UI Compose      │  │  HUD HOST         │  │  Sessions    │  │
│  │  (Material 3)    │  │  panel registry,  │  │  (Room +     │  │
│  │  connect screen, │  │  drag/snap/resize,│  │   Keystore,  │  │
│  │  settings, list  │  │  per-session      │  │   DataStore) │  │
│  │  koneksi         │  │  layout persist   │  │              │  │
│  └────────┬─────────┘  └────────┬──────────┘  └──────┬───────┘  │
│           │                     │                    │          │
│  ┌────────┴─────────────────────┴────────────────────┴─────────┐ │
│  │        :core-rdp  →  SessionManager (state machine)         │ │
│  │   Flow<SessionState> · TelemetryFlow · InputChannel ·       │ │
│  │   ClipboardSync · FileChannel · AudioChannel                │ │
│  └──────────────────────────┬──────────────────────────────────┘ │
│                             │  JNI (thin, typed)                 │
│  ┌──────────────────────────┴──────────────────────────────────┐ │
│  │   NATIVE: libfreerdp-client + custom JNI bridge (C)         │ │
│  │   RDP 8.0/10.0 · TLS · NLA/CredSSP · H.264 · Opus audio    │ │
│  │   RDPDR · RDPECAM · RFX · NSCodec · telemetry sampler       │ │
│  └──────────────────────────┬──────────────────────────────────┘ │
└─────────────────────────────┼────────────────────────────────────┘
                              │  RDP over TLS  :3389
                       ┌──────┴───────┐
                       │   WINDOWS    │
                       │ (10/11 Pro,  │
                       │  Server)     │
                       └──────────────┘
```

### Prinsip
- **JNI setipis mungkin**: bridge cuma meneruskan (config masuk, frame/telemetri/input keluar). Semua logika di Kotlin.
- **Session state machine eksplisit** — semua transisi ada di satu tempat, mudah di-debug.
- **HUD decoupled**: HUD mengkonsumsi `Flow` dari `core-rdp`, tidak pernah menyentuh JNI. Panel baru = file baru, tanpa ubah inti.
- **Protocol-agnostic di level Kotlin**: `core-rdp` mendefinisikan interface `RemoteBackend` (bukan `RdpBackend`), jadi VNC/scrcpy di kemudian hari tinggal implementasi baru.

> **M0 saat ini:** lapis atas masih `app` (Java) → `:freeRDPCore` (JNI bawaan FreeRDP).
> Modulisasi di diagram di atas mulai dirakit di M1.

---

## 4. Layer Native (FreeRDP + JNI)

### 4.1 Build
- CMake superbuild FreeRDP resmi (`freeRDPCore/src/main/cpp`): otomatis download & build OpenSSL, FFmpeg, OpenH264, Opus, libpng, libwebp, libjpeg-turbo, cJSON, uriparser + FreeRDP core → output di `freeRDPCore/src/main/jniLibs/${ABI}/` (gitignored).
- ABI M0: `arm64-v8a` + `x86_64` (satu APK universal, `release.properties`).
- NDK 29.0.13113456, CMake 4.1.2 (di-install via SDK Manager / CI).

### 4.2 API JNI yang tersedia (bawaan inti FreeRDP, dipakai M0)
`com.freerdp.freerdpcore.services.LibFreeRDP`:
- lifecycle: `newInstance(ctx)`, `freeInstance`, `connect`, `disconnect`, `cancelConnection`
- config: `setConnectionInfo(ctx, inst, BookmarkBase|Uri)` → parse ke argumen FreeRDP (`/v:`, `/u:`, `/p:`, `/domain:`, ...)
- input: `sendCursorEvent`, `sendKeyEvent`, `sendUnicodeKeyEvent`
- graphics: `updateGraphics(inst, Bitmap, x, y, w, h)` (frame dari native → Java)
- clipboard: `sendClipboardData`, `sendClipboardImageData`
- query: `getVersion`, `hasH264Support`, `hasCameraRedirectionSupport`
  (⚠️ `freerdp_get_last_error_string` ter-deklarasikan native PRIVATE di LibFreeRDP tanpa wrapper publik — TIDAK bisa dipanggil dari Java/Kotlin; jangan depend. Ditangkap saat M1.1)
- event listener (GlobalApp): `OnConnectionSuccess/Failure`, `OnDisconnecting/Disconnected`, `OnPreConnect`

**M1:** di atas API ini dibungkus `SessionManager` Kotlin (state machine + Flow) — bukan ganti native-nya.

### 4.3 Render pipeline
- M0: FreeRDP decode frame (RFX/H.264/BMP sesuai codec aktif) → native push Bitmap → `SessionView` (bawaan inti) di dalam `SessionActivity`.
- M4: pipeline di-upgrade ke `HardwareBuffer`/SurfaceView + hardware decode (AVC4204) untuk 4K@60.

### 4.4 Telemetri (sumber data HUD) — mulai M2
Sampler 500ms: `fps`, `frameTimeMs`, `rttMs`, `kbpsDown/Up`, `codec`, `gpuOn`, `resolutionW/H`, `dpi`, `channelStates`, `certInfo`.
Sisi native M2: tambah counter di bridge (atau gunakan statistik yang sudah diekspos FreeRDP + waktu render Java-side).

### 4.5 Channel & fitur native yang tersedia
- **Clipboard** dua arah (teks + image/file)
- **RDPDR**: drive redirection (M3)
- **Audio**: playback + mic capture (M3)
- **NLA / CredSSP** + TLS, certificate prompt (UI trust manager di M3)
- **RDP Gateway** (M3), **RDPECAM** (M4, opsional), keyboard unicode + hardware passthrough

---

## 5. Modul Kotlin (target akhir; M1 mulai dirakit)

```
xydesk-remote/client/Android/Studio/
├── app/                    # shell: launcher, navigation, branding (ADA DI M0)
├── core-rdp/               # (M1) JNI wrapper, SessionManager, state machine,
│                           # TelemetryFlow, InputChannel, ClipboardSync
├── core-hud/               # (M2) HUD host: registry, layout engine (drag/snap/
│                           # resize), persist layout per-sesi, tema HUD
├── core-hud-panels/        # (M2+) panel-panel konkret (lihat §6)
├── features-sessions/      # (M1) favorites (Room), import .rdp/QR, discovery LAN
├── features-security/      # (M1) credential vault (Keystore), cert trust manager
├── core-model/             # data class bersama
└── core-common/            # util, logging, result type
```

### 5.1 Session state machine

```
IDLE → CONNECTING → AUTHENTICATING(NLA) → CERT_PROMPT? → CONNECTED
   ↑                                                            │
   └──────────────── DISCONNECTED / ERROR(code) ←───────────────┘
                                 (SUSPENDED: app ke background,
                                  policy: auto-disconnect / keep / ask)
```

### 5.2 Aman default
- **Certificate**: verifikasi penuh default; dialog trust sekali dengan fingerprint (SHA-256) per host; opsi strict-mode.
- **Password**: AES-GCM, key di Android Keystore; "remember password" eksplisit.
- **Auto-lock**: disconnect otomatis saat background (default ON).
- **Screen security**: `FLAG_SECURE` saat sesi aktif.
- Tidak ada data sesi keluar device (telemetri 100% lokal).

---

## 6. HUD Framework (bagian unggulan — M2)

### 6.1 Konsep
- **Panel** = unit komposable terdaftar dengan `id`, `title`, `icon`, mode `Compact`/`Expanded`.
- **HUD Host** = overlay transparan di atas remote canvas:
  - **Drag** bebas + **snap ke 4 sisi**
  - **Resize** (corner handle), compact = icon, tap = expand
  - **Toggle HUD** via gesture 3-jari hold (configurable)
  - **Show/hide per panel** dari HUD menu
- **Layout persist per sesi** (DataStore JSON) → tiap koneksi punya "meja kendali" sendiri.
- **Tema HUD**: Dark, Glass, High-contrast; opacity & scale global.

### 6.2 Katalog panel

| # | Panel | Isi | Milestone |
|---|---|---|---|
| 1 | **Stats** | FPS, frame time, ping RTT, bitrate ↓/↑, codec, GPU on/off, resolusi | M2 |
| 2 | **Controls** | resolusi preset (fit/720p/1080p/1440p/2160p/custom), DPI slider, FPS cap, codec toggle, orientasi, fullscreen | M2 |
| 3 | **Pointer** | direct touch vs virtual trackpad, size pointer, sensitivitas wheel, double-tap = klik kanan | M2 |
| 4 | **Keyboard** | IME mode (unicode/scancode), keyboard on-screen, **shortcut bar**: Win, Ctrl, Alt, Shift, Alt+Tab, Win+Tab, Win+D, Win+E, Win+L, Win+R, Ctrl+Shift+Esc | M2 |
| 5 | **Clipboard** | status sync, copy/paste, transfer file via clipboard | M3 |
| 6 | **File Transfer** | drive ter-mapping, upload/download + progress | M3 |
| 7 | **Quick Actions** | screenshot, record sesi, lock remote, sign out, restart remote, disconnect | M3 |
| 8 | **Audio** | volume playback, mute, mic capture + level | M3 |
| 9 | **Diagnostics** | channel states, cert info, session ID, gateway status, log tail | M4 |
| 10 | **Recording** | status record, durasi, ukuran file, save/share | M3 |

### 6.3 Data flow

```
Native/SessionManager (500ms) ──► TelemetryFlow (StateFlow) ──► HUD panels
Kontrol HUD ──► SessionManager ──► LibFreeRDP (args/input) ──► Native
```

---

## 7. Konektivitas & Sisi Windows

- Target: **Windows 10/11 Pro/Enterprise/Edu + Windows Server** (RDP host aktif, NLA).
  ⚠️ **Windows Home tidak bisa jadi target RDP** — deteksi & pesan jelas di app (M1).
- Port default 3389; port custom, IP/hostname, **RDP Gateway** (M3).
- Auth: username/password (NLA/CredSSP) + domain.
- Import: `.rdp` (parser), QR (`rdp://`), form manual (M0 sudah form manual).
- Discovery LAN (opt-in, M3).
- Multi-monitor/extended (M4).

---

## 8. Roadmap & Acceptance Criteria

| MS | Scope | Acceptance Criteria | Estimasi solo-dev |
|---|---|---|---|
| **M0 — Fondasi & First Frame** | Repo + CI + scaffold + form koneksi + jalur RDP via core | ✅ scaffold & CI siap; **TERUS**: APK terpasang di HP, koneksi ke Windows VM, layar tampil, mouse gerak, ngetik di Notepad | 1–2 minggu |
| **M1 — Core dipakai harian** | Modul `core-rdp` + `features-*`, Kotlin/Compose UI koneksi & favorites, credential vault (Keystore), resolusi/DPI, clipboard teks, policy background, deteksi Windows Home | Sesi 1 jam tanpa crash; reconnect sesuai policy; ganti DPI/resolusi tanpa putus | 2–3 minggu |
| **M2 — HUD v1 (pembeda utama)** | ✅ v1: Session surface XyDesk (Compose) + HUD top bar + panel Stats/Controls + dialog cert/NLA + policy background + deteksi Windows Home. Sisa: layout per koneksi, panel Pointer/Keyboard visual | Pakai HUD 10 menit tanpa buka settings; Stats realtime akurat | (v1 selesai) |
| **M3 — Produktivitas** | File transfer (RDPDR), clipboard file, audio 2 arah, screenshot, session recording, import .rdp/QR, cert trust manager UI, RDP Gateway | Kirim file 100 MB via mapped drive; record sesi 5 menit; mic 2 arah | 2–3 minggu |
| **M4 — Power & Polish** | Hardware decode (AVC4204) + SurfaceView, multi-monitor, panel Diagnostics/Recording, RDPECAM (opsional), tema, tuning | 4K@60 flagship; 1080p@60 mid-range; H264 aktif otomatis | 2–4 minggu |
| **M5 — Ship** | Release signing (CI secrets), CI polish, crash reporting, branding XyDesk + credit XyVerse, distribusi internal | Build release dari CI tanpa langkah manual; lulus smoke test M1–M4 di 3 device | 1 minggu |

**Total realistis: ± 2–3 bulan.** MVP (M0–M2) awal bulan kedua.

---

## 9. Risiko & Mitigasi

| Risiko | Mitigasi |
|---|---|
| Build superbuild lama (first run CI ±45–75 menit) | Cache NDK/CMake di workflow; knob `CMAKE_ARGUMENTS` untuk build cepat tanpa codec (mode uji) |
| Edge case NLA/domain (kerberos dst.) | Error code jelas + panduan; smartcard/kerberos hanya kalau memang dibutuhkan (M5+) |
| H.264 decode beda-beda per device (M4) | Capability probe + fallback RFX/NSC |
| Memory 4K di low-end (M4) | Cap resolusi dinamis + peringatan di HUD Stats |
| Lisensi FFmpeg (LGPL/GPL) | Superbuild FreeRDP sudah configure aman; audit lisensi sekali di M5 |
| Upgrade FreeRDP (3.33+) | Perubahan XyDesk dibatasi & terdokumentasi di `XYDESK-REMOTE-NOTICE.md` → re-merge tree murah |
| Play Store policy remote access | Kategori wajar (RDP client umum di Play); privacy policy siap di M5 |

---

## 10. Environment Development

- Android Studio + NDK 29.0.13113456 + CMake 4.1.2 (SDK Manager)
- Real device recommended; emulator x86_64 untuk uji UI
- Target test: 1 VM Windows 11 (Hyper-V/VMware), RDP + NLA enabled
- CI: GitHub Actions (`build-apk.yml`), artifact APK per run

---

## 11. Progress M1

### M1.1 — Lapis data & sesi (SELESAI, tanpa UI)
| Item | Modul | Status |
|---|---|---|
| `SessionManager` + state machine `SessionState` (Flow) | `:core-rdp` (Kotlin) | ✅ |
| Prompt sertifikat & NLA (safe-default: DENY/tolak tanpa listener) | `:core-rdp` | ✅ |
| Input forwarding (cursor/key/unicode/clipboard) untuk HUD M2 | `:core-rdp` | ✅ |
| `CredentialVault` — AES-256-GCM, key di Android Keystore (non-exportable) | `:features-security` | ✅ |
| Favorites Room (`favorites` table) + `SessionsRepository` (metadata+vault → `ConnectionProfile` siap-connect) | `:features-sessions` | ✅ |
| Gradle: KGP 2.2.21 + KSP 2.2.21-2.0.5 (pair match), Room 2.8.5, coroutines 1.11.0 | root + modul | ✅ |

Catatan M1.1: modul baru dikompilasi CI (digantung di `:app`) tapi **belum
dipakai UI** — wiring UI di M1.2.

### M1.2a — UI Compose home (SELESAI)
- `XyDeskHomeActivity` (Compose Material 3) jadi launcher; `MainActivity`
  (M0) tetap sebagai fallback "Form klasik"
- List favorites (`SessionsRepository.favorites()` via Flow) + hapus
- Form koneksi (host/port/user/pass/domain/label) + "ingat password"
  (→ `CredentialVault`, AES-GCM di Keystore)
- Connect = core `SessionActivity` + URI `rdp://` (jalur render teruji M0)
- Pintu ke Cloud RDP (GitHub)
- Compose: BOM 2026.05.01 + plugin compose 2.2.21 (match KGP)
- OAuth App Device Flow sudah aktif + `CLIENT_ID` tertanam (login GitHub
  dari HP tinggal jalan)

### M1.2b — SELESAI
1. ~~UI Compose~~ → selesai (M1.2a)
2. ~~Session surface + dialog trust + NLA~~ → selesai (M2 v1, di bawah)
3. Policy background (auto-disconnect 15s saat app di-background)
   + deteksi "Windows Home / RDP off" (pre-flight TCP 2.5s →
   `Error("unreachable")` + hint + pintu Cloud RDP)
4. `SessionManager.telemetry` (sampler 500ms) → panel Stats HUD

### M2 v1 — HUD + session surface XyDesk (SELESAI)
- `XyDeskSessionActivity` (Compose) + `SessionSurfaceController`:
  menjamu view inti (SessionView/TouchPointerView/ExtendedKeyboardView/
  ScrollView2D, wiring input identik M0) via `AndroidView`
- Render pipeline XyDesk: `GraphicsSink` (thread RDP) →
  `LibFreeRDP.updateGraphics` + `SessionView` invalidation — semantik
  identik jalur M0; `SessionActivity` M0 tetap sebagai "Form klasik"
- Dialog Compose: trust sertifikat (fingerprint SHA-256, timeout=tolak,
  warning changed/mismatch) + prompt NLA + error (hint Windows Home)
  + konfirmasi disconnect
- HUD: top bar (judul + stats ringkas) + panel samping (Stats realtime:
  status/resolusi/aktivitas gambar/zoom/versi FreeRDP; Kontrol: zoom,
  touch pointer, keyboard, disconnect)
- **BUG FIX keamanan M1.1**: konstanta `VERIFY_ACCEPT/VERIFY_DENY`
  tadinya TEBALIK (kontrak inti: 1=accept, 0=deny) — "percaya"
  sebelumnya malah menolak & sebaliknya
- Back semantics: prompt wajib dijawab; Connected=konfirmasi;
  Connecting=batalkan; panel=sembunyikan
- Clipboard 2 arah (teks): remote→local via listener, local→remote via
  PrimaryClipChangedListener (hanya saat Connected)

### M2.5 — polish sesi (SELESAI)
- Zoom & panel **diingat per koneksi** (ConnectionPrefs, SharedPreferences
  per `host:port`)
- **Screenshot** dari panel HUD: surface → PNG (external files dir) →
  share via FileProvider + ACTION_SEND
- **"Percaya & ingat" sertifikat** (CertificateTrustStore): fingerprint
  SHA-256 per host:port; auto-approve kalau fingerprint sama; dialog
  warning kuat + tampilkan fingerprint lama vs baru kalau berubah
  (MITM guard; tanpa fingerprint tersimpan = selalu tanya)

### M2.6 — brand XyDesk + diagnosa crash (SELESAI)
- **Design system custom** "XyDesk" (properti XyVerse, bukan muka bawaan):
  palet violet (gelap #0E0B16 / terang), tipografi Space Grotesk + Inter
  (variable fonts OFL), bentuk sudut 12-28dp, komponen brand
  (XyCard/XyBrandButton/XyGhostButton/XySectionTitle/XyStatusChip/XyMenuItem/XyWordmark)
- **App shell dengan drawer**: Koneksi, Cloud RDP, Pengaturan, Keamanan,
  Tentang (jawab keluhan "gadis sidebar")
- **Pengaturan**: tema (ikut sistem/gelap/terang), auto-disconnect toggle
- **Keamanan**: daftar sertifikat "Percaya & ingat" + lupakan per-host/semua
  + fingerprint signing
- **Logo & ikon custom** (dipilih pengguna) — adaptive icon + header drawer
- **Diagnosa crash**: CrashLog (uncaught exception -> file -> banner di home
  + dialog log), vault.put() non-fatal (keystore gagal = password tetap
  dipakai, hanya tidak diingat), guard inisialisasi sesi
- **Keamanan release**: keystore dirotasi (password lama bocor di log CI
  publik via set -x) — keystore baru + masking ::add-mask::; v0.2.8+
  ditandatangani keystore baru (uninstall versi lama dulu!)

### M2/M3 tersisa (lanjutan)
- Panel Pointer/Keyboard visual, file transfer (RDPDR), clipboard file,
  audio 2 arah, session recording, import .rdp/QR — sesuai roadmap

---

## 12. M6 — Cloud RDP "Create RDP from GitHub" (v1 di-ship bersama v0.2.0)

**Konsep:** user di app cuma **login GitHub + isi nama** → sisanya otomatis:
nama jadi nama **repo GitHub (public — aturan proyek: tanpa repo private)** → app push template setup →
workflow di repo menyiapkan **VM Windows self-hosted runner** (label
`xydesk-win`): RDP on, join **Tailscale**, user + password **acak** →
app poll run → download artifact `rdp-credentials` → cek konektivitas
→ **connect otomatis** (via tailnet, tidak ada port-forward).

### Arsitektur v1
```
HP (XyDesk Remote)                       Repo GitHub (per user)
┌────────────────────┐   device flow    ┌──────────────────────────┐
│ CloudRdpActivity   │ ───────────────► │ repo '<nama>' (public)   │
│  login GitHub      │   (scope: repo)  │  .github/workflows/      │
│  create repo       │ ◄─────────────── │    rdp-vm.yml           │
│  push template     │   poll run +     │  setup/setup-windows.ps1│
│  poll + download   │   artifact zip   └───────────┬──────────────┘
└─────────┬──────────┘                              │ self-hosted
          │ TCP check + SessionActivity             ▼
          ▼                        ┌──────────────────────────────┐
   RDP via Tailscale ◄─────────────│ VM 'xydesk-win' (Windows)    │
   (host: xxx.ts.net:3389)         │ Tailscale + RDP + user xydesk│
                                   └──────────────────────────────┘
```

### Komponen (sudah ada di kode)
- `cloud/GitHubDeviceAuth.java` — device flow (tanpa client secret di device)
- `cloud/GitHubClient.java` — repo/contents/actions/artifacts API (HttpURLConnection, tanpa dependensi)
- `cloud/CloudRdpActivity.java` — UI + orkestrasi pipeline
- `assets/xydesk-cloud/rdp-vm.yml` + `setup-windows.ps1` — template yang di-push ke repo user

### Keamanan (dua-fase, public-safe)
- Repo user **public**; token device flow scope `repo`
- **Rahasia tidak lewat input workflow** (di repo public input workflow kebaca
  publik). Fase `prepare`: host generate kunci RSA (persist di
  `C:\ProgramData\xydesk\hostkey.pem`) + upload public key; app enkripsi
  `{user,pass,tskey}` (RSA-OAEP-SHA1) → `setup/secrets.enc`.
  Fase `setup`: host dekripsi, setup, kredensial di-enskripsi balik ke
  `setup/pubkey.pem` (kunci sekali-pakai app) → `rdp-credentials.enc`.
- Password RDP: pilihan user (wajib kuat) atau auto-generate 18 karakter di VM;
  **di-reset tiap setup** (re-run aman); mengalir VM→artifact→app
- Auth key Tailscale: via ciphertext (secrets.enc) atau env runner
  `XYDESK_TAILSCALE_AUTH_KEY` — tidak pernah di repo sebagai plaintext
- Koneksi RDP selalu TLS + dalam tailnet (tidak ada exposure port ke internet)
- **Skrip host WAJIB jalan di pwsh 7** (API PEM .NET Core tidak ada di Windows
  PowerShell 5.1) — workflow memanggil `pwsh`, bukan `powershell`

### Open items (butus keputusan lo)
1. **OAuth App GitHub** harus dibuat manual di UI (POST /applications sudah mati) —
   enable **Device Flow**, salin client_id ke `GitHubDeviceAuth.CLIENT_ID`.
   **Panduan lengkap langkah demi langkah: `docs/OAUTH-APP-SETUP.md`**
2. **Provisioning VM**: v1 pakai **self-hosted runner yang lo own** (label `xydesk-win`).
   Kalau mau "benar-benar create VM dari nol" (cloud provider), tentukan provider-nya
   (Cloudflare VPS / Hetzner / Oracle / Azure...) → kita bikin adapter provisioner +
   auth key-nya disimpan sebagai GitHub user secret (RSA-OAEP) di tahap berikutnya
3. Multi-VM / pool, hibernate/stop, dan billing info → v2

## 13. Rilis & Distribution (v0.2.0)

- **Split per-ABI** (tanpa universal): `armeabi-v7a`, `arm64-v8a`, `x86_64`
- **Signing resmi**: keystore RSA-4096 (100 tahun) — digenerate sekali di CI
  (job `generate-keystore`), disimpan sebagai secret `RELEASE_KEY_BASE64`
  + salinan JKS di tempat aman milik lo (workspace: `xydesk-remote-release.jks`)
- **GitHub Release**: push tag `v*` → build release (R8) → GitHub Release
  dengan 3 APK per-ABI sebagai asset
- **R8**: aktif di release (`MINIFY_ENABLED=true`), keep rules terdokumentasi
  di `app/proguard-rules.txt` (native hanya menyentuh `LibFreeRDP` — verified
  dari source). Rollback cepat: `MINIFY_ENABLED=false`
- **Repo public** untuk kontribusi/sponsor/collab (secret tidak ada di repo —
  sudah di-scan sebelum dipublikasi)
