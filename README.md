# XyDesk Remote

App Android remote desktop untuk **Windows (RDP)** dengan sistem **HUD multi-panel** yang bisa dikustomisasi — diluar fitur standar Microsoft Remote Desktop.

> Status: **Milestone M0** (scaffold + first frame). Rincian: [PLAN.md](PLAN.md)

## Apa ini

- Klien RDP native berbasis **FreeRDP 3.32.0** (Apache-2.0) — sama dengan yang dipakai klien RDP Android populer lainnya, tapi UI & fitur XyDesk dibangun dari nol.
- Form koneksi ala MS Remote Desktop: **host/IP + port + username + password (+ domain)**, NLA/TLS.
- Roadmap: HUD multi-panel (Stats, Controls, Pointer, Keyboard, File Transfer, Audio, Quick Actions, Diagnostics), transfer file, session recording, gateway, H.264 hardware decode, multi-monitor.

## Struktur repo

Repo ini sengaja mempertahankan **layout tree FreeRDP** di root, karena CMake superbuild (`client/Android/Studio/freeRDPCore/src/main/cpp/CMakeLists.txt`) me-resolve sumber FreeRDP dengan path relatif 7 level ke atas dari folder `cpp`. Perubahan XyDesk terhadap tree FreeRDP **sangat terbatas**:

```
<root>                        = tree FreeRDP 3.32.0 (vendored, pin via git)
├── .github/workflows/        ← CI build APK (XyDesk)
├── client/Android/Studio/
│   ├── build.gradle          ← diedit: tanpa plugin androidgitversion
│   ├── settings.gradle       ← diedit: ':freeRDPCore' + ':app'
│   ├── release.properties    ← baru: konfigurasi build
│   ├── freeRDPCore/          ← inti FreeRDP Android (native + JNI), TANPA perubahan
│   └── app/                  ← modul APP XYDESK (UI koneksi M0, HUD menyusul)
├── docs/BUILD.md             ← panduan build lokal & CI
├── PLAN.md                   ← arsitektur + roadmap (M0–M5)
├── XYDESK-REMOTE-NOTICE.md   ← atribusi & daftar perubahan terhadap FreeRDP
└── FreeRDP-README.md         ← README asli FreeRDP (arsip referensi)
```

## Build

### Via GitHub Actions (disarankan untuk M0)
Push ke `main` (atau jalankan manual dari tab **Actions**) → workflow `Build APK` → artifact `xydesk-remote-debug` berisi `app-debug.apk` (arm64-v8a + x86_64).

### Lokal
Lihat [docs/BUILD.md](docs/BUILD.md). Singkatnya: Android Studio + NDK `29.0.13113456` + CMake `4.1.2`, import folder `client/Android/Studio`, build. Build pertama 10–40 menit (superbuild mengompil OpenSSL/FFmpeg/OpenH264/Opus + FreeRDP core).

### Menguji M0
1. Siapkan Windows 10/11 **Pro/Enterprise** atau Server dengan RDP + NLA aktif (Windows Home tidak bisa jadi target RDP).
2. Pasang `app-debug.apk` di HP (arm64).
3. Isi form: IP host, username, password → **Hubungkan**.
4. Layar Windows tampil; pastikan mouse/jari bisa bergerak dan bisa mengetik di Notepad.

## Keamanan
- Tidak ada secret/token apa pun di repo ini. Konfigurasi build hanya via `release.properties` (bukan kredensial).
- Signing release dilakukan di M5 dengan keystore di CI secrets.
- Kredensial RDP di device: disimpan terenkripsi (Keystore) mulai M1 — di M0 password hanya ada di memori proses selama sesi.

## Lisensi
- Tree FreeRDP (termasuk winpr, channels, dll): **Apache-2.0** — lihat `LICENSE`.
- Kode XyDesk di `client/Android/Studio/app` + dokumen: © XyVerse (lihat `XYDESK-REMOTE-NOTICE.md`).
