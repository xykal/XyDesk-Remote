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

## Catatan rilis v0.5.2

Ronde 5 — kontrol, taskbar, dan tema:

- **Toolbar di atas keyboard dihapus, keyboard virtual dihapus.** Mengetik
  sepenuhnya lewat keyboard HP; karakter IME diteruskan ke sesi sebagai
  `KeyEvent` (jalur scancode/modifier milik inti), karakter tanpa keycode lewat
  jalur unicode.
- **Kontrol = overlay satu-satu.** Tiap aksi satu tombol bulat: bisa digeser
  kapan saja, diubah ukurannya (24..140 dp), diganti jenisnya, ditambah dari
  katalog, dan dihapus. Titik masuk: geser langsung, tahan lama untuk editor,
  banner "atur posisi" dengan chip "+ Tombol", dan daftar tombol di panel kanan.
- **Taskbar tidak lagi tenggelam.** Ukuran desktop yang dikirim adalah area
  gambar yang benar-benar terlihat, tidak ada lagi padding bawah yang dulu
  membuat strip gelap di tepi; muat-ulang otomatis sesudah connect, ganti
  resolusi, dan rotasi.
- **Resolusi per rasio.** Preset dikelompokkan 16:9 / 16:10 / 21:9 / 4:3 /
  potret, ditambah "16:9 pas layar" yang memilih ukuran 16:9 standar terbesar
  yang masih muat.
- **Tema dirapikan.** Panel, dialog, dan pemilih tombol memakai token tema
  (mode gelap dan terang dua-duanya benar), sementara tombol HUD tetap kontras
  tetap karena berada di atas gambar remote. Semua pesan singkat memakai
  notifikasi milik app sendiri, bukan Toast bawaan Android.
- Panel kanan disederhanakan: yang berhubungan dengan keyboard virtual dibuang,
  yang tersisa hanya yang benar-benar berfungsi.

## Catatan rilis v0.5.1

Ronde 4 — perbaikan dari pemakaian nyata di HP:

- Tombol HUD bisa digeser lagi di mode atur posisi (gerak diakumulasi per
  gesture, bukan selisih antar-event), dan tahan-lama membuka editor tombol di
  mode mana pun.
- Baris tombol di atas keyboard hanya muncul saat keyboard benar-benar tampil
  dan saat tombol keyboard HUD aktif; tombol "buka keyboard" dipindah jadi
  tombol HUD biasa (`Keyboard`).
- Preview kartu Windows 11 memakai wallpaper aslinya
  (`drawable-nodpi/xy_win11_wall.jpg`), bukan art prosedural.
- Pointer memakai bentuk kursor dari server (panah, penunjuk, I-beam) lewat
  `RemoteCursor`; jatuh ke panah bawaan kalau server tidak mengirim kursor.
- Sesi tetap jalan saat app ditinggal ke latar: foreground service
  `XySessionService` + notifikasi dengan aksi "Buka" dan "Putuskan"; perilaku
  ini bisa dimatikan di setelan app.
- Fitur baru: "Kirim teks ke remote" (termasuk tempel dari clipboard HP) dan
  "Salin info teknis" (versi inti RDP) untuk laporan bug.
- Kontras teks dan garis dinaikkan di panel, keyboard layar, dan ikon HUD.

## English summary (v0.5.2)

Round 5 — controls, taskbar, and theming:

- **The toolbar above the keyboard and the built-in virtual keyboard are gone.**
  Typing is done entirely with the phone IME; typed characters are forwarded to
  the session as Android `KeyEvent`s (the core's scancode/modifier path), and
  characters without a keycode use the unicode path.
- **Controls are individual overlays.** One action per round button: drag it any
  time, resize it (24..140 dp), change its type, add more from the catalogue, or
  delete it. Entry points: drag directly, long-press for the editor, the arrange
  banner with a "+ Button" chip, and the button list in the right panel.
- **The remote taskbar no longer sinks off-screen.** The desktop size sent is the
  visible picture area, the bottom padding that used to leave a dark strip is
  gone, and the view is re-fitted after connecting, after a resolution change,
  and after rotation.
- **Resolution grouped by aspect ratio** (16:9 / 16:10 / 21:9 / 4:3 / portrait),
  plus "16:9 matched to screen" which picks the largest standard 16:9 size that
  fits.
- **Theme cleanup.** Panels, dialogs, and pickers use theme tokens (dark and
  light are both correct); HUD buttons keep fixed contrast because they sit on
  top of the remote picture. All short messages use the app's own notice widget
  instead of the system Toast.
- Right panel pruned: anything tied to the removed virtual keyboard is gone.

## English summary (v0.5.1)

Round 4, driven by real phone usage:

- HUD buttons drag again in arrange mode (gesture accumulates movement instead
  of reading stale coordinates), and long-press opens the key editor in any
  mode.
- The key row above the system keyboard only shows while that keyboard is
  actually visible, gated by the HUD keyboard button.
- Windows 11 device cards use the real wallpaper asset instead of procedural
  art.
- The pointer now uses the server-side cursor shape (arrow, hand, I-beam) via
  `RemoteCursor`, falling back to the built-in arrow.
- Sessions keep running in the background through a foreground service with
  "Open" / "Disconnect" notification actions (toggleable in settings).
- New: send arbitrary text to the remote (including paste from the phone
  clipboard) and copy technical info for bug reports.
- Higher contrast for panel text, on-screen keyboard, and HUD icons.

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
