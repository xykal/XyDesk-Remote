# XyDesk Remote — Arsitektur & Roadmap
> App Android remote desktop (klien RDP) untuk Windows, dengan sistem HUD multi-panel yang bisa dikustomisasi.
> Status: **M0 SCAFFOLDED** — repo `xykalnotkel/xydesk-remote` + CI GitHub Actions aktif — 2026-09-23
> Nama kerja: `XyDesk Remote` (package: `id.xydesk.remote`)

---

## 0. Status Implementasi (M0)

| Item | Status |
|---|---|
| Repo GitHub `xykalnotkel/xydesk-remote` (private) | ✅ dibuat |
| Tree FreeRDP **3.32.0** vendored di root (pin via git) | ✅ |
| Modul `:freeRDPCore` (native + JNI, Apache-2.0) | ✅ tanpa perubahan |
| Modul `:app` — form koneksi XyDesk (host/IP, port, user, pass, domain) | ✅ |
| Koneksi via URI `rdp://user@host:port/?p=...&domain=...` → SessionActivity core | ✅ |
| CI `Build APK` (JDK 21, NDK 29, CMake 4.1.2, arm64-v8a + x86_64) | ✅ workflow terpasang |
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
| minSdk / targetSdk | **29 / 36** | compileSdk 36 = stabil terbaru di repo Google (37 masih beta) |
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
- query: `getVersion`, `getLastErrorString`, `hasH264`, `hasCameraRedirection`
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
| **M2 — HUD v1 (pembeda utama)** | Session surface XyDesk (ganti SessionActivity) + HUD Host + panel Stats/Controls/Pointer/Keyboard | Pakai HUD 10 menit tanpa buka settings; layout per koneksi diingat; Stats realtime akurat | 2 minggu |
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

## 11. Next Step (M0 → M1)

Setelah smoke test M0 lulus (APK dari CI):
1. Rakit modul `core-rdp` (Kotlin): `SessionManager` + state machine + `TelemetryFlow`
2. `features-sessions`: favorites Room + UI Compose pengganti form M0
3. `features-security`: credential vault Keystore + policy background
4. Deteksi "Windows Home / RDP off" dengan pesan jelas
