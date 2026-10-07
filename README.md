# XyDesk Remote

Klien RDP Android untuk Windows dan Windows Server. Sambung langsung ke PC milikmu — tanpa relay cloud, tanpa akun.

[**Unduh APK**](https://rdp.xydesk.my.id/) · [Rilis di GitHub](https://github.com/xykal/XyDesk-Remote/releases) · [Panduan build](docs/BUILD.md)

---

## Apa ini

XyDesk Remote menampilkan desktop Windows di HP Android-mu lewat protokol RDP, lengkap dengan kontrol mouse, keyboard, dan tombol yang bisa diatur sendiri. Koneksinya peer-to-peer ke host yang kamu tentukan; tidak ada server XyDesk di tengah yang melihat layar atau menyimpan kredensialmu.

**Butuh:** Windows 10/11 Pro, Enterprise, atau Windows Server (versi Home tidak punya RDP server) · Android 7.0 (API 24) ke atas.

---

## Langkah demi langkah

### 1. Siapkan PC Windows

1. Buka **Settings → System → Remote Desktop**, nyalakan **Enable Remote Desktop**.
2. Catat nama PC atau IP lokalnya: **Settings → Network → Properties**, atau jalankan `ipconfig` di Command Prompt.
3. Pastikan akun Windows-mu punya password. RDP menolak akun tanpa password.
4. Supaya bisa diakses dari luar rumah, pakai **VPN** (WireGuard/Tailscale/ZeroTier) atau teruskan port 3389 di router. Jangan membuka 3389 langsung ke internet.

### 2. Pasang di HP

1. Unduh APK dari [situs resmi](https://rdp.xydesk.my.id/) — pilih sesuai arsitektur HP:
   - `arm64-v8a` → hampir semua HP keluaran 2017 ke atas
   - `armeabi-v7a` → HP lama 32-bit
   - `x86_64` → emulator atau tablet x86
2. Buka file APK-nya, izinkan **Install unknown apps** untuk browser/file manager-mu.
3. Jalankan XyDesk Remote.

### 3. Tambahkan perangkat

1. Ketuk **Tambah perangkat**.
2. Isi **alamat host** (IP atau nama PC) dan **port** (bawaan `3389`).
3. Isi **username** dan **password** akun Windows. Password disimpan terenkripsi di HP, tidak dikirim ke mana pun.
4. Simpan.

### 4. Sambungkan

1. Ketuk profil perangkat itu.
2. Pertama kali, aplikasi menampilkan **fingerprint sertifikat** server. Cocokkan, lalu percayai.
3. Desktop Windows muncul. Selesai.

### 5. Pakai sesinya

| Yang mau dilakukan | Caranya |
|---|---|
| Gerakkan kursor | Mode **Trackpad** (geser di mana saja) atau **sentuh langsung** — ganti di panel **Input** |
| Klik kanan / tengah | Tombol HUD, atau setel di panel **Tombol** |
| Keyboard | Ikon keyboard di rail kanan bawah |
| Salin-tempel teks | Aktif otomatis lewat kanal clipboard |
| Kirim file ke PC | Panel **Sesi → Transfer file** → *Kirim file ke PC*. Di Windows muncul sebagai drive `XyDesk` (`\\tsclient\XyDesk`) |
| Ambil file dari PC | Salin file ke drive `XyDesk` di Windows, lalu di HP buka **Transfer file → Segarkan** |
| Screenshot desktop PC | Panel **Sesi → Ambil screenshot**; tersimpan di `Pictures/XyDesk` |
| Rekam layar PC | Panel **Sesi → Perekaman**; MP4 720p 60 fps + audio PC di `Movies/XyDesk` |
| Kontrol musik HP | Tab **Musik** di panel — putar/jeda, geser posisi, acak, ulang, antrian, dan pustaka |
| Scroll halus | Rail kanan bawah → ikon scroll |
| Gamepad | Aktifkan di panel **Tombol**; stik + 18 tombol yang bisa dipetakan |

Semua pengaturan ada di satu panel: ketuk **Menu** di dalam sesi. Panelnya bertab — **Layar**, **Input**, **Tombol**, **Musik**, **Sesi** — dan tiap kotak melebar di tempat, tidak pindah halaman.

---

## Fitur

- **Koneksi** — profil tak terbatas, Wake-on-LAN, verifikasi sertifikat, auto-reconnect.
- **Tampilan** — zoom, orientasi, resolusi desktop, DPI Windows, multi-monitor.
- **Input** — trackpad atau sentuh langsung, mouse gestures, scroll inertial, gyro-mouse, keyboard eksternal & IME.
- **Tombol HUD** — susun sendiri ukuran, aksi, dan posisi; preset gamepad; profil bisa diekspor/diimpor.
- **Media** — kendalikan pemutar musik HP mana pun (Spotify, SoundCloud, YouTube Music, pemutar lokal) dari dalam sesi; audio tetap di HP.
- **File** — transfer dua arah HP ↔ PC lewat drive yang di-redirect.
- **Rekam & screenshot** — desktop PC saja; layar HP dan kontrol tidak ikut terekam.
- **Audio & perangkat** — audio PC ke HP, mikrofon, kamera, clipboard teks & gambar.

---

## Kalau tidak bisa tersambung

- **Timeout** → PC tidur, atau IP salah, atau tidak satu jaringan. Coba `ping <ip-pc>` dari HP.
- **"CredSSP" / ditolak** → akun tanpa password, atau Remote Desktop belum nyala.
- **Layar hitam setelah login** → di PC, kunci lalu buka lagi sesinya; atau matikan sementara GPU scheduling.
- **Lambat** → gunakan kabel/5 GHz, turunkan resolusi di panel **Layar**, dan pastikan transport UDP aktif.

---

## Keamanan

Koneksi RDP berjalan langsung ke host pilihanmu. Password disimpan lewat penyimpanan kredensial terenkripsi Android dan tidak pernah dikirim ke layanan pihak ketiga. Log diagnostik bisa memuat alamat host dan metadata sertifikat — samarkan sebelum dibagikan.

Laporkan celah keamanan secara privat lewat [GitHub Security Advisories](https://github.com/xykal/XyDesk-Remote/security/advisories/new), jangan di issue publik.

---

## Build dari source

Lihat [docs/BUILD.md](docs/BUILD.md) untuk versi JDK, Android SDK, NDK, dan CMake yang dipakai. GitHub Actions menjalankan pemeriksaan Kotlin, unit test JVM, dan build APK per-ABI.

## Lisensi

FreeRDP dilisensikan Apache-2.0. Atribusi dan rincian perubahan XyDesk ada di [`XYDESK-REMOTE-NOTICE.md`](XYDESK-REMOTE-NOTICE.md).

Dibuat oleh xykal — XyVerse Technology Global.
