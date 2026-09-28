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

## Catatan rilis v0.5.7

Ronde 8 - fokusnya kontrol sesi, terutama resolusi/rasio yang selama ini
membingungkan.

- **Rasio dibetulkan di akarnya.** Dulu "Otomatis" mengikuti dimensi layar
  HP mentah, jadi desktop remote bisa berbentuk 20:9: taskbar mini, teks
  tidak terbaca, dan tidak ada yang menjelaskan kenapa. Sekarang Otomatis
  SELALU 16:9 - ukuran standar terbesar yang muat di layar (1280x720 s/d
  4K). Rasio layar HP masih bisa dipilih eksplisit lewat "Ikuti layar HP",
  ditandai jelas sebagai opsi video/game, bukan default.
- **Panel sesi ditata ulang.** Dua panel kiri/kanan tanpa label (isi nya apa
  saja cuma bisa ditebak) diganti SATU panel dengan empat tab: Layar, Input,
  Tombol, Sesi. Handle tunggal di tepi kanan, berlabel.
- **Resolusi jujur.** Daftar ukuran menulis dimensinya (FHD - 1920x1080),
  panel menampilkan "Desktop sekarang: 1920x1080 - 16:9" dihitung live,
  dan opsi yang aktif ditandai.
- **Keluar konsisten.** Dulu tombol power pakai "2x ketuk" sementara panel
  pakai dialog - dua aturan untuk satu aksi. Semua jalur putus sekarang
  membuka dialog konfirmasi yang sama.
- **Error koneksi yang bisa ditindak.** Kode error dipetakan ke penjelasan
  dua bahasa (host tidak menjawab / timeout / kredensial ditolak / TLS),
  pesan teknis tetap tampil di bawah untuk laporan bug.
- **Slider yang ngawur dibenerin.** Slider ukuran tombol HUD memungkinkan
  32-96 padahal settingannya dipotong 40-80 (angkanya loncat balik sendiri);
  sekarang 40-80. Slider "ukuran tombol bawaan" di Umum dibuang - nilainya
  tidak pernah dibaca siapa pun (sisa model lama).
- **Orientasi ikut bahasa**: Otomatis/Potret/Lanskap (dulu Auto/Portrait/
  Landscape mentah).
- **Rapian internal**: preferensi mati (keyboard overlay, cluster, corner,
  pointer-follows) dibuang dari kode; ukuran live-resize selalu genap dan
  dalam batas server; `smart169` lama otomatis dianggap Otomatis.

## English summary (v0.5.7)

Round 8 reworks session controls: the confusing resolution/ratio model now
guarantees a 16:9 desktop by default (phone ratio is an explicit opt-in),
the two unlabeled edge panels became one four-tab panel (Screen / Input /
Buttons / Session), exit is consistent everywhere (confirm dialog),
connection errors map to actionable bilingual hints, dead preferences and
mismatched slider ranges were cleaned up, and rotation labels follow the
app language.

## Catatan rilis v0.5.6

Ronde 7 — yang ini beresin akar keluhannya: logika yang tidak konsisten,
bukan cuma tempelan UI.

- **BUG KRITIS — kunci profil hilang lewat intent.** `connectIntent()` tidak
  membawa `profile.key`, dan rekonstruksi profil di activity sesi tidak
  mengembalikannya, jadi id profil di sesi jatuh ke `host:port` padahal semua
  setelan per perangkat (audio, mikrofon, gateway, UDP, H.264, clipboard,
  drive, resolusi, rotasi, skala, layout tombol HUD) ditulis pakai kunci
  tetap perangkat. Akibat nyatanya: setelan yang diatur di layar
  Tambah/Ubah perangkat tidak pernah terbaca saat connect. Kini key ikut
  di intent (`xydesk.key`).
- **Auto-reconnect tidak mati lagi gara-gara batal putus.** Dulu menekan
  "Putuskan" lalu membatalkan dialog konfirmasi tetap menandai sesi
  "diputus user", jadi koneksi yang putus sendiri tidak pernah
  disambung ulang. Penanda sekarang baru diset saat putus dikonfirmasi.
- **Ganti bahasa langsung berlaku di seluruh layar.** Bahasa dulu disimpan
  sebagai variabel global biasa: layar hanya kebagian render ulang kalau
  kebetulan ada state lain berubah, jadi setengah UI tertinggal di bahasa
  lama. Sekarang bahasa adalah state Compose yang dilangganan `xy()`/`t()`.
- **Sisa teks satu bahasa dibereskan** (dua bahasa penuh): judul drawer,
  dialog hapus kredensial, dialog sertifikat (Tolak) & NLA (Username/
  Password/Masuk/Batal), overlay error (Detail/Tutup/Batal), status panel
  ("Terhubung" / "menunggu server"), skala tampilan, deteksi bandwidth,
  haptic, kecepatan scroll, info inti, log error, hint kirim teks, dan
  typo "kal mau" diganti "kamu mau" (3 tempat).
- **Ikon seksi Keamanan** tidak lagi kembar dengan Tentang (kini perisai
  XyShield, gaya garis yang sama).
- **Notifikasi sesi** memakai ikon XyDesk sendiri (dulu ikon sistem panah
  upload), teksnya dua bahasa, dan tidak lagi kehilangan nama perangkat
  saat app pindah ke latar.
- **Status akses "semua file"** di layar perangkat kini dicek ulang saat
  kembali dari Setelan — dulu selalu tampil "terbatas" walau izin baru
  saja diberikan.
- **Pesan error unreachable** tidak lagi menyuruh "Coba Cloud RDP" — fitur
  itu sudah dihapus sejak v0.3.
- **Pembersihan**: parameter mati di `DevicesScreen`, composable
  `KeyListHeader` yang tak terpakai, baris no-op di chip akun tersimpan,
  dan trik `if (tick < 0)` di layar Kredensial diganti `remember(tick)`.

## English summary (v0.5.6)

Round 7 fixes the root causes, not just cosmetics: the device key was lost
across the connect intent so every per-device option (audio, mic, gateway,
UDP, H.264, clipboard, drive, resolution, rotation, scale, HUD layout) was
silently ignored; cancelling the disconnect confirmation permanently killed
auto-reconnect; language switches only half-applied because language was
plain global state instead of Compose state; remaining Indonesian-only
strings are bilingual now; the session notification got the app's own icon
and keeps the device label in background; all-files access status refreshes
on resume; dead code removed.

## Catatan rilis v0.5.5

Ronde 6 lanjutan — yang masih setengah jalan dibereskan:

- **Bahasa benar-benar jalan.** Sebelumnya cuma sebagian: katalog tombol HUD,
  daftar resolusi, layar Tambah/Ubah perangkat, layar sesi (dialog sertifikat,
  NLA, log koneksi, layar putus, langkah koneksi), layar Umum/Keamanan/Tentang,
  dan splash masih Indonesia saja. Sekarang semuanya dua bahasa lewat `xy()`
  (composable) / `xyNow()` (di dalam callback & pesan).
- **Splash diperbaiki.** Fase-fase animasinya dulu tumpang tindih: strip
  taskbar masih ikut kelihatan waktu bentuknya sudah jadi rak server. Tiap
  fase sekarang punya jendela waktu sendiri, plus kilau tipis di tepi atas
  layar. Geometri animasinya saya render ulang jadi gambar
  (`assets/splash-frames.png`) supaya bisa dicek tanpa HP.
- **Petunjuk tombol terkunci** muncul sekali di sesi pertama: "tombol terkunci,
  tahan lama untuk memindahkan" — ini yang dulu bikin kontrol terasa tidak jelas.
- **"Buka" pada sesi aktif** memakai intent lengkap (extras profil), jadi
  sesinya bisa dibuka lagi walau activity-nya sudah selesai.
- **Teks audio** (Putar di perangkat / di remote / matikan) ikut dua bahasa.

## English summary (v0.5.5)

Round 6 continued: full bilingual coverage (HUD catalog, resolution list, add/edit
device screen, session dialogs, about/security screens, splash), fixed the splash
morph where phases overlapped (taskbar strip leaking into the server phase), a
one-time hint explaining locked buttons, and "Open" on active sessions now carries
the full profile intent.

## Catatan rilis v0.5.4

Ronde 6 — pembetulan perilaku + identitas XyVerse:

- **Tombol kontrol terkunci**: di layar sesi tombol tidak bisa kegeser lagi
  waktu dipakai. Geser hanya saat "Atur posisi" menyala (tahan lama satu
  tombol juga membuka mode itu). Menu panel menyesuaikan diri dengan mode.
- **Latar tombol** bisa dipilih (Gelap tipis / Terang tipis / Transparan):
  ikon tetap terbaca kalau desktop remote-nya putih.
- **Rail tetap kanan bawah**: buka/tutup keyboard HP kapan saja, dan tombol
  keluar butuh dua kali ketuk (anti kepencet).
- **Resolusi**: daftar dipangkas ke 16:9 saja (Otomatis, 16:9 pas layar,
  720, 900, 1080, 1440, 4K) — rasio lain memang tidak pas di FreeRDP.
- **Splash XyVerse**: animasi morphing HP → monitor → rak server, berhenti
  saat data app siap, memakai logo resmi XyVerse (mark + wordmark).
- **Wallpaper**: preview desktop memakai wallpaper Windows 11 Bloom resmi,
  1920x1080 (sebelumnya gambar buatan sendiri).
- **Bug duplikat diperbaiki**: mengubah perangkat lalu menyimpan tidak lagi
  membuat entri kedua (kunci perangkat tetap).
- **Tema**: token gelap dinaikkan kontrasnya, dan layar sesi mengikuti tema
  app (dulu dipaksa gelap).
- **Bahasa**: label, tombol, judul panel, dan pesan sudah dua bahasa (ID/EN)
  lewat `xy()`/`xyNow()`; teks paragraf panjang masih menyusul.

## English summary (v0.5.4)

Round 6 — behaviour fixes plus XyVerse identity: HUD buttons are locked
unless "Edit layout" is on; button plate is selectable (dark/light/transparent)
so icons stay readable on white desktops; a fixed bottom-right rail shows/hides
the phone keyboard and disconnects on double tap; resolution list trimmed to
16:9 only; XyVerse-branded morphing splash (phone → monitor → server rack)
that exits when app data is ready; official Windows 11 Bloom wallpaper;
fixed the duplicate-device bug on edit-save; better dark-theme contrast and the
session screen now follows the app theme; bilingual labels/buttons/panels.

## Catatan rilis v0.5.3

Lanjutan ronde 5 — sisa UI bawaan diganti milik app sendiri:

- **Slider** (ukuran pointer, ukuran tombol, DPI, kecepatan) sekarang
  `XySlider`: track tipis ber-border + pegangan bulat, ikut tema.
- **Menu drawer** (daftar bagian: Perangkat, Tampilan, Kredensial, Umum,
  Keamanan, Tentang) bukan lagi `ModalNavigationDrawer` Material: panel geser
  milik app dengan scrim dan animasi sendiri.
- **Spinner** proses koneksi (5 langkah) memakai `XySpinner` (busur berputar),
  bukan `CircularProgressIndicator`.
- **Garis pemisah** memakai `XyDivider`.
- Tidak ada lagi Toast, dialog, bottom sheet, drawer, slider, atau indikator
  bawaan Android/Material yang terlihat. Panel/dialog/menu memakai token tema
  (gelap & terang dua-duanya benar); tombol kontrol di layar sesi tetap kontras
  tetap karena berada di atas gambar remote.

## English summary (v0.5.3)

Round 5 continued — the remaining stock UI is replaced with app-owned widgets:
`XySlider` (thin bordered track + round knob, theme-aware), a custom slide-in
drawer instead of Material's `ModalNavigationDrawer`, `XySpinner` instead of
`CircularProgressIndicator`, and `XyDivider`. No visible Android/Material stock
toast, dialog, sheet, drawer, slider, or indicator remains.

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
