# XyDesk Remote

**An Android RDP client for Windows and Windows Server.** Connect directly to a host you control—no XyDesk cloud relay or account required.

[Download releases](https://github.com/xykal/XyDesk-Remote/releases) · [Build guide](docs/BUILD.md) · [Security reporting](https://github.com/xykal/XyDesk-Remote/security/advisories/new)

## Bahasa Indonesia

XyDesk Remote membantu kamu mengakses PC atau server Windows dari Android lewat RDP.

### Fitur

- Simpan profil koneksi dan kelola kredensial secara lokal.
- Pilih mode Trackpad atau sentuh langsung; gunakan gesture mouse dan scroll.
- Atur tombol HUD, ukuran, aksi, dan posisi sesuai perangkat.
- Ubah zoom lokal, orientasi, dan resolusi desktop.
- Gunakan clipboard, audio, dan kanal perangkat yang diaktifkan untuk profil.
- Verifikasi sertifikat server sebelum mempercayainya.

### Mulai

1. Aktifkan Remote Desktop pada Windows Pro atau Windows Server.
2. Pastikan ponsel dapat menjangkau host melalui jaringan tepercaya atau VPN.
3. Tambahkan alamat host, port, dan akun Windows di aplikasi.
4. Tinjau fingerprint sertifikat ketika diminta, lalu sambungkan.

Jangan membuka port RDP langsung ke internet tanpa perlindungan jaringan yang memadai. Gunakan VPN atau gateway tepercaya bila akses berasal dari luar jaringan lokal.

### Build dari source

Lihat [panduan build](docs/BUILD.md) untuk versi JDK, Android SDK, NDK, dan CMake yang digunakan. GitHub Actions menjalankan pemeriksaan Kotlin dan build APK.

### Keamanan dan privasi

Koneksi RDP dibuat langsung ke host yang kamu pilih. Password profil disimpan lokal melalui penyimpanan kredensial terenkripsi Android; jangan cantumkan password di issue, log, screenshot, atau file konfigurasi publik. Log diagnostik dapat memuat alamat host dan metadata sertifikat—samarkan sebelum membagikannya.

Laporkan dugaan kerentanan secara privat melalui [GitHub Security Advisories](https://github.com/xykal/XyDesk-Remote/security/advisories/new). Jangan menerbitkan detail yang bisa dipakai untuk menyerang pengguna sebelum ada perbaikan.

### Lisensi

FreeRDP dilisensikan di bawah Apache-2.0. Atribusi dan rincian perubahan XyDesk tersedia di [`XYDESK-REMOTE-NOTICE.md`](XYDESK-REMOTE-NOTICE.md).

Dibuat oleh xykal — XyVerse Technology Global.

## English

XyDesk Remote lets you access a Windows PC or server from Android over RDP.

### Features

- Save connection profiles and manage credentials locally.
- Choose Trackpad or direct-touch input, with mouse gestures and scrolling.
- Customize HUD controls, size, actions, and placement per device.
- Adjust local zoom, Windows desktop DPI, orientation, and remote desktop resolution separately.
- Use clipboard, audio, and the device channels enabled for a profile.
- Review a server certificate before trusting it.

### Get started

1. Enable Remote Desktop on Windows Pro or Windows Server.
2. Make the host reachable from your phone through a trusted network or VPN.
3. Add the host address, port, and Windows account in the app.
4. Review the certificate fingerprint when prompted, then connect.

Do not expose RDP directly to the public internet without appropriate network protections. Use a VPN or trusted gateway for access from outside your local network.

### Build from source

See the [build guide](docs/BUILD.md) for the JDK, Android SDK, NDK, and CMake versions. GitHub Actions runs Kotlin checks and APK builds.

### Security and privacy

RDP connections go directly to the host you select. Profile passwords are stored locally using Android's encrypted credential storage. Never include passwords in issues, logs, screenshots, or public configuration files. Diagnostic logs may contain the host address and certificate metadata; redact them before sharing.

Report suspected vulnerabilities privately through [GitHub Security Advisories](https://github.com/xykal/XyDesk-Remote/security/advisories/new). Avoid publishing exploitable details before a fix is available.

### License

FreeRDP is licensed under Apache-2.0. XyDesk attribution and modification details are in [`XYDESK-REMOTE-NOTICE.md`](XYDESK-REMOTE-NOTICE.md).

Built by xykal — XyVerse Technology Global.
