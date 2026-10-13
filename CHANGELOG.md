# Changelog XyDesk Remote

Semua perubahan sejak rilis publik v1.0.2 sampai v1.1.8.
Format: versi (kode) — tanggal — poin perubahan.

## v1.1.12
- Sinkron Cloud: setelan & kontrol kustom dicadangkan ke akun GitHub lo (kata sandi/token TIDAK pernah ikut) — habis pasang ulang tinggal pakai kode pemulihan lalu "Pulihkan dari Cloud".
- Drawer kontrol sesi kini meluncur masuk dari tepi kanan (bukan muncul mendadak).
- Feed: ketuk nama penulis = lihat semua postingannya, dengan chip filter sekali ketuk untuk keluar.
- Feed: gambar lampiran/avatar di-cache di memori — scroll balik tampil instan, nggak unduh ulang (hemat kuota).
- Semua teks UI dipangkas ringkas — nggak ada lagi penjelasan berbaris-baris di panel.

## v1.1.11
- Lampiran feed akhirnya terkirim: akar bugnya batas body server 2KB yang menolak semua postingan bergambar; kini sampai 1,5MB (foto/stiker/GIF).
- GIF di feed benar-benar animasi (Android 9+), bukan frame pertama lagi.
- Ketuk lampiran feed = penampil fullscreen: cubit zoom 1–5x, ketuk dua kali zoom/reset, ketuk tutup.
- Layar Feed: tarik ke bawah untuk muat ulang (pull-to-refresh).
- Notifikasi sesi sadar status: "Menyambungkan…" / "Sesi remote aktif" / "Menyambungkan ulang…" + progress saat belum tersambung.
- Musik dihapus total dari aplikasi (pemutar, panel, bridge Spotify, SoundCloud, pustaka media) — APK lebih ringan.
- Kompresi gambar kiriman lebih pintar: kualitas turun bertahap lalu dimensi, foto besar tetap muat.

## v1.1.10
- Anti-blackscreen: watchdog mendeteksi sesi tersambung tanpa gambar (~6 detik) lalu mencoba ulang sekali dengan grafis kompatibel (H264 mati, warna 16-bit); setelan disimpan per perangkat dan otomatis kembali ke kualitas penuh setelah normal.
- Keyboard virtual super lengkap untuk kontrol kustom: kombinasi tombol bebas sampai 24 langkah (Esc, F1–F12, simbol, navigasi, numpad) — chip urutan bisa dihapus satu per satu.
- Panel sesi lebih premium: popup dan sidebar dapat gloss tepi atas, border glow, dan aksen cahaya tepi kiri sidebar.
- Feed: waktu relatif ("baru saja", "5 mnt lalu", "kemarin").
- Log sesi terakhir: penampil log berwarna per tingkat (error merah, peringatan kuning, sukses hijau).
- Profil: tombol "Cek pembaruan" manual tanpa menunggu cek otomatis 12 jam.
- APK Universal baru (semua ABI dalam satu berkas) + tombol unduh langsung 64-bit/32-bit/Universal di situs dengan deteksi arsitektur perangkat otomatis.
- Konsistensi UI lintas DPI: tinggi sentuh tombol minimum 40dp, teks tombol/chip tidak lagi meluber di layar kecil atau font besar.

## v1.1.9
- Login GitHub tanpa token: OAuth Device Flow (ketik kode 8 karakter di github.com/login/device).
- Tata letak kontrol: snap grid 12dp + magnet sejajar + garis bantu ungu saat geser tombol (HUD & gamepad).
- Turbo tombol HUD: tahan = ketik berulang otomatis (opsi per tombol, 0,1 detik).
- Salin tombol di editor tata letak (duplikat cepat).
- Notifikasi sesi: ikon aksi khusus (bukan logo), tombol Musik dihapus, tombol Putuskan kini selalu tampil (batas 3 aksi Android).
- Perbaikan "Kembali ke beranda": membuka Beranda app, bukan menutup app ke desktop HP; sesi tetap hidup.
- Perbaikan nama profil/author komentar yang tampil sebagai "null".
- Sidebar kanan dirampingkan (76%, maks 360dp) — kini benar-benar sidebar, bukan popup.
- Pill telemetri: kolom TRANSPORT (UDP/TCP).
- Ikon adaptif diperbaiki: logo di zona aman 45% — tidak terpotong masker lagi.
- Keamanan instalasi: penandatanganan APK kini V1+V2+V3.
- Popup hitung mundur web berhenti bersih setelah waktu rilis lewat.

## v1.1.8 (69) — 2026-10-12
- **Dukungan Android 6–16**: minSdk 23 (Android 6.0), compile/target API 37 (Android 16). Kanal kamera NDK kini dimuat runtime (dlopen) sehingga perangkat Android 6 tetap jalan; kamera redirect aktif di API 24+.
- **Nama APK baru**: `XyDesk-Remote32bit.apk` (armeabi-v7a), `XyDesk-Remote64bit.apk` (arm64-v8a), `XyDesk-Remote64bit-x86.apk` (x86_64) — pipeline tanda tangan, verifikasi hash, dan alias unduhan web mengikuti.
- **RdpFree native**: layar kontrol XyRDP via API rdpfree.projectkal.my.id — token perangkat di Keystore, poll status, run/stop/cancel, pilih durasi, sambung ke jalur RDP yang sudah ada.
- **Feed komunitas level baru**: lampiran foto/stiker WebP/GIF (≤500KB), edit & hapus postingan milik sendiri, avatar profil tampil di feed, label "Feed" (bukan "Feed Jokes").
- **Profil**: ganti foto profil (tersimpan di perangkat + unggah ke server), login pakai GitHub (PAT read:user di Keystore) untuk nama otomatis, keluar akun.
- **Notifikasi sesi custom**: tombol "Beranda" dan "Musik" bisa dinyalakan/dimatikan dari panel sesi (seksi "Tombol notifikasi sesi").
- **Visual Windows tidak lagi bikin wallpaper hitam**: seksi toggle visual dihapus dari tambah koneksi; wallpaper, animasi menu, dan full-window drag kini aktif di preset bawaan.
- **Animasi bubble rail**: menu bubble rail muncul "disedot" dari tombol panah (scale dari titik tombol) dan menutup balik dengan halus.
- **Popup menu sesi diperluas** (hampir penuh di HP/tablet); varian drawer samping tetap ada.
- **Anti mouse-double**: kursor overlay otomatis disembunyikan saat mouse fisik (OTG/Bluetooth) terpasang.
- **Logo baru di semua permukaan**: launcher adaptive (rounded), monochrome, notifikasi, favicon, OG 1200×630 baru.
- **Web**: popup hitung mundur rilis ungu glossy liquid-glassmorphism dengan blob morph + partikel melayang; OG/SEO/sitemap lengkap untuk Google Search Console.
- Keyboard fisik OTG/Bluetooth, mouse fisik (hover/klik/scroll/two-finger), dan gamepad fisik sudah didukung penuh.
- Kredensial RDP tersimpan per host — tambah koneksi tidak minta password ulang.

## v1.1.7 (68) — draft 2026-10-10
- Logo baru + notifikasi bermerek; semua seksi "visual Windows" yang menyebabkan wallpaper hitam dihapus dari menu lanjutan; combo tombol bebas 2–5 kunci; menu dua varian (popup/drawer); favicon/OG/keywords/sitemap web.

## v1.1.6 (67→68) — draft 2026-10-10
- Kredensial diingat per host (vault Keystore); favorites memuat otomatis; SoundCloud tetap via client_id publik.

## v1.1.5 (67) — draft 2026-10-10
- Nag kanal ditutup permanen setelah dismiss; perbaikan wallpaper hitam sesi.

## v1.1.4 (66) — draft 2026-10-10
- Build live-review: UI matte total, gamepad HUD visual mandiri + stik kanan mouse, pemain musik ungu glossy (pengecualian brand), rail minim dua tombol + bubble, "ke beranda" tanpa memutus sesi, unlock join-saja.

## v1.1.3 — draft 2026-10-09/10
- Ronde 12–13: glassmorphism dikeluarkan dari semua UI app (matte), joystick tidak menggerakkan mouse default (WASD + label), popup menu lega, paksa sembunyikan status bar, penarikan v1.1.2 & perbaikan kritis.

## v1.1.2 — ditarik 2026-10-09
- Ditarik karena regressi; perbaikan masuk v1.1.3+.

## v1.1.1 — draft 2026-10-07
- Kontrol musik penuh (Spotify/SoundCloud/playlist/pustaka), transfer file HP↔PC, ronde UI 3–4.

## v1.1.0 — draft 2026-10-06
- Panel grid drill-in, rekaman 60fps adaptif + audio PC, host agent ber-UI Win32 + tray.

## v1.0.9 — draft 2026-10-06
- Stik analog WASD/panah, overlay pad dihapus, rekaman PC + audio, host agent satu EXE (standalone).

## v1.0.8 — draft 2026-10-06
- Gamepad virtual bisa digeser; panel sesi jadi popup blur.

## v1.0.7 — draft 2026-10-06
- Gamepad virtual (joystick) untuk jalur RDP & PC/Game Stream.

## v1.0.6 — draft 2026-10-06
- Merge PC stream (QUIC streaming sendiri), deploy Worker pertama.

## v1.0.2 — dirilis 2026-10-04
- Follow-up Android: perbaikan input/tampilan, signer tetap sama sampai sekarang.

## v1.0.1 — dirilis 2026-10-03
- Website, SEO, telemetri opt-in.
