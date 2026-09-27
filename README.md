# XyDesk Remote

App Android remote desktop untuk **Windows (RDP)** dengan:
- Form koneksi ala MS Remote Desktop (IP + port + user + password)
- Koneksi host apa pun yang RDP-nya aktif (VM, VPS, PC rumah, tailnet) — tanpa akun pihak ketiga
- (M2+) HUD multi-panel yang bisa dikustomisasi

> Repo ini **public** untuk kontribusi, sponsor, collab, dan diskusi (issues/PR).
> Lisensi tree FreeRDP: Apache-2.0. Kode XyDesk: © XyVerse (lihat `XYDESK-REMOTE-NOTICE.md`).

## Struktur repo

Repo mempertahankan **layout tree FreeRDP** di root (kebutuhan path relatif CMake superbuild). Perubahan XyDesk terbatas:

```
<root>                        = tree FreeRDP 3.32.0 (vendored, pin via git)
├── .github/workflows/        ← CI/CD: debug per-ABI, release (R8+signed), keystore
├── client/Android/Studio/
│   ├── build.gradle          ← diedit minimal
│   ├── settings.gradle       ← ':freeRDPCore' + ':app'
│   ├── release.properties    ← knob build (ABI, minify, versi)
│   ├── freeRDPCore/          ← inti FreeRDP Android (native + JNI), tanpa perubahan
│   └── app/                  ← APP XYDESK: home + form koneksi + sesi HUD (Compose)
├── docs/BUILD.md             ← panduan build lokal & CI
├── PLAN.md                   ← arsitektur + roadmap (M0–M6)
├── XYDESK-REMOTE-NOTICE.md   ← atribusi & daftar perubahan terhadap FreeRDP
└── FreeRDP-README.md         ← README asli FreeRDP (arsip)
```

## Build

### CI (GitHub Actions) — otomatis
| Pemicu | Yang terjadi |
|---|---|
| Push ke `main` | Build **DEBUG per-ABI** (armeabi-v7a, arm64-v8a, x86_64) → artifact `xydesk-remote-debug-per-abi` |
| Tag `v*` (misal `v0.2.0`) | Build **RELEASE per-ABI** (R8 + signing resmi) → **GitHub Release** dengan 3 APK |
| Manual (Actions tab) | `release` (tanpa tag) atau `generate-keystore` (sekali saja) |

Durasi: ±25–40 menit/run (superbuild OpenSSL/FFmpeg/OpenH264/Opus + FreeRDP core per ABI).

### Lokal
Lihat `docs/BUILD.md`. Ringkas: Android Studio → import `client/Android/Studio` → NDK `29.0.13113456` + CMake `4.1.2` + platform `android-37.2`.

### Sign release (sekali saja)
1. Tab **Actions** → *Build APK* → Run workflow → input **generate-keystore**
2. Unduh artifact `xydesk-release-keystore`
3. Set secret repo (Settings → Secrets and variables → Actions):
   - `RELEASE_KEY_BASE64` = isi `release.jks` di-base64
   - `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_STORE_PASSWORD` = isi `keystore-creds.env`
4. **Simpan `release.jks` di tempat aman** (bukan di repo!). Ini identitas signing app — kalau bocor, update app di Play/dimana pun akan ditolak.

## Catatan rilis v0.3

Fitur **Cloud RDP (create mesin lewat GitHub Actions)** dihapus dari app.
Alasannya teknis dan bukan soal selera: alur itu memakai GitHub Actions +
self-hosted runner sebagai control plane provisioning infrastruktur, memaksa
repo berisi kredensial host dibuat public, dan itu melanggar ketentuan
pemakaian Actions (compute harus terkait build/test/deploy repo yang
bersangkutan). App sekarang murni klien RDP: daftar perangkat, profil
kredensial lokal, HUD sesi.

## Catatan rilis v0.5

Ronde ini seluruhnya soal input dan kontrol:

- **Tombol HUD satu-per-satu.** Tiap tombol berdiri sendiri, bulat penuh,
  bisa digeser, ukurannya 32..96 dp, dan aksinya diatur per tombol (sekali
  klik / tahan / toggle). Klik kiri, klik kanan, klik tengah, scroll naik,
  scroll turun, dan ganti mode input dipisah total dengan ikon sendiri-sendiri.
  Ada katalog lengkap: modifier, kombinasi siap pakai (Ctrl+C, Ctrl+Shift+Esc,
  Alt+Tab, Win+R, ...), F1..F12, numpad, huruf, simbol, panah. Ada mode
  "atur posisi" (screen mapping) untuk menggeser tombol bebas.
- **Toolbar di atas keyboard HP.** Chip paling ujung `123` membuka board
  lengkap bawaan app; `ABC` mengembalikan keyboard HP. Keyboard HP di-hide,
  toolbar dan board ikut hilang (tinggi IME dibaca live dari window insets).
- **Storage HP jadi drive remote.** `/drive:sdcard` memakai path yang
  benar-benar bisa dibaca app di Android modern; layar perangkat punya tombol
  izin "semua file" kalau user mau seluruh isi storage terlihat.
- **Tentang + Dukung saya.** Saweria (kallsptra), GitHub Sponsors, dan bintang
  repo; bahasa app Indonesia/English bisa diganti langsung dari Tentang.
- **Log native ke file.** winpr menulis `freerdp-native.log` sehingga masalah
  audio/mikrofon/clipboard/drive bisa dikirim tanpa ADB.
- Preview kartu perangkat memakai art app sendiri (bukan wallpaper RDP/OS).

Detail teknis: `docs/HUD-BUTTONS.md` (EN) dan `docs/HUD-BUTTONS.id.md` (ID).

## English summary (v0.5)

- Free-floating, fully round HUD buttons: one button = one action, draggable,
  32..96 dp, per-button action (tap / hold / toggle). Left, right, middle,
  scroll up, scroll down, and input-mode switch are separate buttons with
  distinct icons; a full catalogue covers modifiers, ready combos, F1..F12,
  numpad, letters, symbols, arrows, plus a screen-mapping mode.
- Toolbar pinned above the phone keyboard: the rightmost chip `123` opens the
  built-in board, `ABC` goes back to the phone keyboard; hiding the phone
  keyboard hides the toolbar and board automatically.
- Phone storage is redirected as a remote drive using a path the app can
  actually read on modern Android, with an optional "all files" grant.
- About page with a "Support me" section (Saweria, GitHub Sponsors, repo
  stars) and an in-app Indonesian/English language switch.
- Native FreeRDP logs are written to `freerdp-native.log` so audio,
  microphone, clipboard, and drive issues can be diagnosed without ADB.
- Device cards use the app's own procedural preview art, not an RDP wallpaper.

## Menghubungi / Kontribusi
- Issues & PR: terbuka (bug, fitur, docs)
- Roadmap & arsitektur: `PLAN.md`
- Sponsor/collab: hubungi via issues
