# Deskripsi Rilis — XyDesk Remote

Dokumen kerja untuk publikasi (belum diterbitkan; dipakai saat store/portal dibuka).
Brand: `XyVerse Technology Global`. Versi acuan: `0.5.29 (46)`.

## Identitas

| Field | Isi |
| --- | --- |
| Nama aplikasi | XyDesk Remote |
| Nama paket | `id.xydesk.remote` |
| Kategori | Tools / Produktivitas |
| Pengembang | XyVerse Technology Global |
| Situs / portal | https://rdp.xydesk.my.id |
| Kebijakan privasi | https://rdp.xydesk.my.id/privacy.html |
| Ketentuan layanan | https://rdp.xydesk.my.id/terms.html |
| Lisensi pihak ketiga | https://rdp.xydesk.my.id/licenses.html |

## Short description (maks 80 karakter)

`Remote Desktop (RDP) ringan untuk Windows: audio, mikrofon, clipboard, GPU.`

EN: `Lightweight Remote Desktop (RDP) for Windows: audio, mic, clipboard, GPU.`

## Deskripsi lengkap (Indonesia)

XyDesk Remote adalah klien Remote Desktop Protocol (RDP) untuk Android yang
dirancang untuk mengendalikan PC atau server Windows dengan latensi rendah.

Fitur utama:

- Sesi RDP penuh dengan dukungan NLA/CredSSP, RDP Gateway, dan RDP-UDP (multitransport).
- Audio PC terdengar di HP, dan mikrofon HP bisa dipakai di PC lewat kanal audio RDP
  (aktifkan izin mikrofon).
- Mode audio kedua "Putar di komputer remote" untuk memindahkan suara lewat jembatan
  UDP :4433 milik XyDeskRemoteHost.exe (Windows), berguna saat kanal audio RDP diblokir.
- Clipboard dua arah (teks + gambar), penyimpanan lokal HP sebagai drive `XyDesk`,
  kamera HP sebagai webcam remote.
- Kendali sentuh lengkap: trackpad presisi, klik kanan/middle, scroll dua jari,
  keyboard virtual, tombol Windows/Ctrl/Alt/Esc.
- Tampilan HUD sesi: statistik FPS/latensi/bitrate, ubah resolusi desktop tanpa
  reconnect (DISP), zoom lokal, dan tombol cepat untuk pengaturan sesi.
- Form koneksi punya mode "Setelan cepat" (hanya opsi penting) dan "Lanjutan"
  (semua tuning: streaming, keamanan, Wake-on-LAN, SSH tunnel, gateway).
- Layar Status: versi aplikasi, status portal rilis, izin mikrofon, dan diagnostik
  yang bisa disalin satu tombol.
- Kredensial disimpan lokal di perangkat dengan enkripsi (vault AES-256-GCM).
  Tidak ada SDK iklan maupun analitik pihak ketiga di dalam aplikasi.

Catatan: aplikasi ini klien RDP. Untuk memakai audio/mikrofon lewat RDP, PC Windows
harus mengizinkan audio redirection (`fDisableAudio`/`fDisableAudioCapture` = 0),
dan izin mikrofon di Android harus diberikan.

## Full description (English)

XyDesk Remote is a Remote Desktop Protocol (RDP) client for Android built for
low-latency control of Windows PCs and servers.

Highlights:

- Full RDP sessions with NLA/CredSSP, RD Gateway, and RDP-UDP (multitransport).
- PC audio plays on your phone, and your phone microphone can be used on the PC
  through the RDP audio channel (grant the microphone permission).
- A second audio mode, "Play on the remote computer", routes sound through the
  UDP :4433 bridge provided by XyDeskRemoteHost.exe (Windows) when the RDP audio
  channel is blocked.
- Two-way clipboard (text and images), phone storage exposed as the `XyDesk`
  drive, and phone camera as a remote webcam.
- Complete touch controls: precision trackpad, right/middle click, two-finger
  scroll, virtual keyboard, Windows/Ctrl/Alt/Esc keys.
- Session HUD: FPS/latency/bitrate stats, desktop resolution changes without
  reconnecting (DISP), local zoom, and quick session settings.
- Connection form has a "Quick setup" mode and an "Advanced" tab for streaming,
  security, Wake-on-LAN, SSH tunnel, and gateway options.
- Status screen with app version, release portal status, microphone permission
  state, and one-tap copy of diagnostics.
- Credentials are stored locally with an AES-256-GCM vault. No advertising or
  third-party analytics SDK is bundled.

Note: this is an RDP client. RDP audio and microphone require the Windows host to
allow audio redirection (`fDisableAudio`/`fDisableAudioCapture` = 0) and the
Android microphone permission.

## What's new di 0.5.34

- Sisa permukaan gelap disamakan dengan situs: toast/notifikasi memakai
  surface-alt + line-strong + teks tema (bukan hitam pekat + garis putih).
- Latar jendela/splash jadi arang #14161A di themes.xml — tanpa kilatan putih.
- Mark ikon peluncur off-white #E4E7EB, sama dengan mark di favicon situs.

## What's new di 0.5.33

- Mode **Otomatis** kini memakai ukuran 16:9 standar (1280x720 / 1600x900 /
  1920x1080) dengan batas atas FHD — perilaku normal, bukan pengejaran resolusi.
- Pengaturan font smoothing tidak lagi diubah otomatis oleh aplikasi; apa yang
  dipilih user itulah yang dipakai.
- Dialog ubah resolusi kembali ringkas (tanpa peringatan teknis).
- Tema gelap aplikasi disamakan dengan situs resmi: arang netral (#14161A),
  permukaan #1A1D23, garis #2E333B, teks #E4E7EB, aksen perak #D3D7DC —
  tanpa hitam pekat/putih murni, kontras tetap tinggi tapi lebih nyaman.

## What's new di 0.5.32

- ClearType (font smoothing) dimatikan otomatis untuk sesi yang resolusinya bukan
  1:1 dengan layar HP — kondisi itu membuat teks bergerigi karena di-resample.
- Dialog ubah resolusi memperingatkan lebih dulu kalau pilihan bukan 1:1.

## What's new di 0.5.31

- Mode resolusi **Otomatis** kembali memakai 16:9 yang pas 1:1 dengan layar HP,
  jadi piksel remote tidak di-resample lagi oleh HP — inilah penyebab utama teks
  terlihat pecah/kabur. Live-resize dari dalam sesi memakai perhitungan yang sama.

## What's new di 0.5.30

- Teks remote kembali tajam: codec RFX-Progressive (lossy) dimatikan, jalur teks
  memakai AVC444 4:4:4 / bitmap apa adanya sehingga ClearType utuh.
- Penskalaan Windows tidak lagi dipaksa 125%; default kembali 100% karena skala
  bukan 100% membuat aplikasi lama direntangkan bitmap (teks bergerigi). Pilihan
  skala lain tetap tersedia di panel sesi.
- Mipmap trilinear pada kanvas sesi dimatikan (menyebabkan huruf tipis ikut turun
  resolusi saat layar diperkecil).
- Agen host: nilai kebijakan font smoothing dibetulkan ke nama resmi
  (`fNoFontSmoothing = 0`).

## What's new di 0.5.29

- Layar **Status** baru (ikon roda gigi di halaman Perangkat): versi aplikasi,
  status portal rilis, kondisi izin mikrofon, dan tombol salin diagnostik.
- Mode **Setelan cepat / Lanjutan** di form koneksi supaya tidak perlu scroll panjang.
- Wallpaper preview dibersihkan (cincin, garis diagonal, dan titik aksen kuning dihapus).
- Mode "Putar di perangkat ini" memakai kanal RDP murni; jembatan UDP :4433 hanya
  aktif di mode "Putar di komputer remote".
- `SHA256SUMS.txt` dan laporan `VIRUSTOTAL.txt` ikut diterbitkan pada setiap rilis
  untuk pemeriksaan integritas.

## Kata kunci (untuk store, bukan klaim)

`remote desktop`, `rdp`, `rdp client`, `windows remote`, `remote pc`,
`kontrol pc dari hp`, `remote desktop android`, `rdp android`.

## Checklist publikasi (status per dokumen ini)

| Item | Status |
| --- | --- |
| Deskripsi ID + EN | siap (dokumen ini) |
| Ikon & screenshot | belum disiapkan di repo (ambil dari build terpasang) |
| Kebijakan privasi publik | ada (`/privacy.html`) |
| Ketentuan layanan | ada (`/terms.html`) |
| Lisensi pihak ketiga | ada (`/licenses.html`) |
| Buku besar integritas (SHA256) | otomatis per rilis (`SHA256SUMS.txt`) |
| Pemindaian malware (VirusTotal) | otomatis per rilis; hasil di `VIRUSTOTAL.txt` + tabel di `docs/SECURITY_SCAN.md` |
| Format Play Store (AAB) | belum dibuat — rilis saat ini APK per-ABI |
| Biaya akun Play Console | ditunda kall (belum ada anggaran) |
