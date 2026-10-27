# Acceptance checklist — XyDesk Remote

Salin file ini jadi `docs/acceptance/vX.Y.Z.md` untuk setiap rilis.
**Aturan emas:** tidak ada tag `v*` sebelum semua baris wajib (W) berstatus ✅ atau N/A dengan alasan tertulis. Baris ✅ wajib punya bukti: path screenshot/logcat, atau link run CI.

Status: `✅ lulus` · `❌ gagal` · `⚠️ lulus dengan catatan` · `N/A tidak diuji (alasan)`

| Metadata | Nilai |
|---|---|
| Versi / kode | |
| Commit / SHA | |
| File APK (nama + ukuran + SHA-256) | |
| Tanggal uji | |
| Penguji | |
| Perangkat (merek, model, Android, ABI) | |
| Host Windows (edisi, versi, build) | |
| Jenis koneksi (LAN / Wi-Fi 5 GHz / VPN / Tailscale) | |
| Link run `Build APK` | |
| Link run `Emulator smoke tests` | |
| Link run `Web Worker checks` | |

---

## A. Otomatis — dijalankan CI (bukan tugas penguji manual)

| # | Pemeriksaan | W | Bukti | Status |
|---|---|---|---|---|
| A1 | JVM unit test `:core-rdp` + `:core-pc-stream` + `:app` | W | | |
| A2 | `compileDebugKotlin` tanpa error | W | | |
| A3 | `assembleDebug` ketiga ABI | W | | |
| A4 | `Web Worker checks` (86 tes) hijau | W | | |
| A5 | Emulator API 24 — install, cold start, rotasi, font besar, process death, offline | W | | |
| A6 | Emulator API 36 — sama seperti A5 | W | | |
| A7 | Tidak ada `FATAL EXCEPTION` / ANR / `SIGSEGV` di logcat emulator | W | | |

> Catatan: emulator **tidak** membuktikan koneksi RDP, IME Gboard, audio, atau performa GPU. Bagian B–D di bawah tetap wajib. Lihat `PEMBAGIAN-TES.md`.

---

## B. Perangkat fisik — Android

| # | Skenario | W | Bukti | Status |
|---|---|---|---|---|
| B1 | Install APK **arm64-v8a** dari file rilis (bukan debug), verifikasi tanda tangan v1+v2 | W | | |
| B2 | Cold start pertama kali: onboarding, minta izin, tidak crash | W | | |
| B3 | Izin notifikasi/kamera/mikrofon ditolak → aplikasi tetap jalan, pesan jelas | W | | |
| B4 | Rotasi portrait ↔ landscape saat di luar sesi | W | | |
| B5 | Font sistem besar (1.3×) + display size besar → tidak ada teks terpotong | W | | |
| B6 | Dark mode / light mode berganti mengikuti sistem | | | |
| B7 | Proses di-kill dari background (Settings → Force stop) lalu dibuka lagi → state favorit/profil tetap ada | W | | |
| B8 | HP lama 32-bit (`armeabi-v7a`) — install + cold start | | | |
| B9 | Mode hemat baterai aktif → tidak ada ANR saat idle | | | |
| B10 | Screenshot disimpan ke `Pictures/XyDesk`, rekaman ke `Movies/XyDesk` | W | | |

---

## C. Host Windows — koneksi RDP

| # | Skenario | W | Bukti | Status |
|---|---|---|---|---|
| C1 | Windows 10 Pro — koneksi TCP 3389, NLA, login sukses | W | | |
| C2 | Windows 11 Pro — koneksi + transport UDP aktif | W | | |
| C3 | Windows Server — koneksi + multi-monitor | | | |
| C4 | Sertifikat pertama kali: fingerprint tampil, bisa dibandingkan dengan sertifikat host | W | | |
| C5 | Sertifikat berubah (regenerate) → aplikasi menolak / minta konfirmasi ulang, bukan auto-trust | W | | |
| C6 | Password salah → pesan jelas, **tidak** retry otomatis | W | | |
| C7 | Akun tanpa password → pesan CredSSP sesuai README | | | |
| C8 | `DesktopScaleFactor` / DISP: scaling 100/125/150/175% benar-benar berubah di host | W | | |
| C9 | Host menolak DISP → preferensi dikembalikan, UI tidak mengklaim "berhasil diterapkan" | W | | |
| C10 | Resolusi non-default diubah saat sesi hidup → gambar tidak stretch, zoom lokal tetap independen | W | | |
| C11 | UDP mati (firewall blok 3389/UDP) → fallback TCP, tidak disconnect | W | | |

---

## D. Perilaku sesi lintas perangkat

| # | Skenario | W | Bukti | Status |
|---|---|---|---|---|
| D1 | **Gboard**: ketik huruf, angka, spasi, simbol; autocorrect; backspace; emoji/surrogate pair; `finishComposingText` | W | | |
| D2 | IME kedua (mis. Samsung Keyboard / SwiftKey) — komposisi teks tidak dobel atau hilang | W | | |
| D3 | Keyboard eksternal fisik + shortcut (Ctrl+C/V, Alt+Tab, Win) | | | |
| D4 | Clipboard teks HP → Windows Paste; clipboard gambar; clipboard Windows → HP | W | | |
| D5 | Transfer file HP → PC muncul sebagai drive `XyDesk` (`\\tsclient\XyDesk`), ukuran cocok | W | | |
| D6 | Transfer file PC → HP lewat "Segarkan" | W | | |
| D7 | Audio PC terdengar di HP; mic opt-in benar-benar butuh izin runtime; bridge UDP tidak start dua kali | W | | |
| D8 | Trackpad vs sentuh langsung; klik kanan/tengah; scroll inersia; gyro-mouse | W | | |
| D9 | HUD: tambah tombol, drag, resize, reset, collision, ekspor/impor JSON, preset gamepad | W | | |
| D10 | Gamepad fisik (issue #12): stik + 18 tombol terpetakan, tidak ada input nyangkut | | | |
| D11 | **Reconnect**: Wi-Fi dimatikan 5/15/40/70/100 detik → backoff 2/4/8/16/30 dtk, tidak ada native instance tumpang tindih | W | | |
| D12 | Reconnect berhenti saat pengguna menekan Stop; error autentikasi/sertifikat **tidak** di-retry | W | | |
| D13 | Activity di-background saat reconnect → tidak bocor native handle / tidak crash saat teardown | W | | |
| D14 | Rekam layar 720p60 + audio PC + kursor ikut terekam; file MP4 bisa diputar | | | |
| D15 | Wake-on-LAN dari HP menyalakan PC yang sedang sleep | | | |
| D16 | Log diagnostik: tidak ada password, NLA/username, atau isi clipboard | W | | |

---

## E. Sign-off rilis

| # | Syarat | Status |
|---|---|---|
| E1 | Semua baris W di atas ✅ atau N/A beralasan | |
| E2 | Tidak ada ❌ yang belum punya issue dengan nomor | |
| E3 | `CHANGELOG.md` dan `PROGRESS.md` diperbarui dengan **hasil nyata**, bukan rencana | |
| E4 | `Web Worker checks`, `Build APK`, `Emulator smoke tests` hijau pada SHA yang sama | |
| E5 | SHA-256 ketiga APK rilis dicatat dan cocok dengan `SHA256SUMS.txt` | |
| E6 | Draft release berisi aset: 3 APK + universal + host agent + SHA256SUMS + SIGNATURE-VERIFICATION | |

**Keputusan:** ☐ Lanjut publish  ☐ Tunda (alasan: ______________________)

Tanda tangan penguji: ______________________  Tanggal: __________
