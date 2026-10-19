# Draft pengujian pemilik — bukan rilis publik

Pemilik kembali meminta: lanjut, kalau seluruh pekerjaan sudah selesai buat release draft agar ia dapat menguji semuanya. Ini izin draft setelah pekerjaan source selesai, bukan izin menyebut fitur yang belum dibuat sudah beres. Tidak mengirim APK debug, merge main otomatis, deploy web/production atau mempublikasikan draft.

## Status yang masih menahan kandidat
- Secure Direct kedua endpoint/pairing/pinning/grant/revoke belum diimplementasikan. Host UDP lama mati default; jangan mengaktifkannya diam-diam agar draft tampak berfungsi.
- Live mic native tanpa memutus sesi belum tersedia. Kebijakan background sudah diterapkan di source3151fc1: URI background tidak meminta native mic/kamera dan sambungan capture yang aktif diminta putus. Ini bukan bukti berhenti secara instan dan bukan kontrol live mute.
- Penyatuan kontrol mic native/bridge, setup driver bertanda tangan, migrasi profil, dan sisa blocker OAuth/security dari plan utama belum boleh ditandai selesai hanya karena gate Kotlin hijau.

## Urutan menuju kandidat yang benar
1. Selesaikan fitur yang dijanjikan, source regression dan pemeriksaan keamanan. Tentukan batas dukungan yang jujur (mis. Roblox pada VM yang ditolak bukan fitur yang bisa diperbaiki lewat bypass).
2. Build kandidat **release signed** untuk APK dan **satu XyDeskRemoteHost.exe**, tanpa varian Standalone/DLL pendamping. Runtime/binary checks memang memerlukan kandidat; jangan membuat syarat melingkar menunggu uji binary sebelum binary dibangun.
3. Verifikasi link/ABI/dependensi EXE, signer APK tetap sesuai identitas resmi, semua aset yang diharapkan, SHA256SUMS dan laporan verifikasi. Jangan mengarang ukuran, hash, tanda tangan, URL aset atau hasil Windows runtime.
4. Buat **draft** dengan commit/tag/daftar aset dan batas uji yang jelas. Catatan draft bukan klaim hasil hardware yang belum diuji. Rilis publik dan deployment tetap memerlukan instruksi terpisah.

**Catatan workflow saat ini:** build-release hanya menerima tag yang reachable dari main atau dispatch release di main; publish-release hanya berjalan untuk tag dan memakai draft:true. Dispatch release pada branch kerja akan SKIP, bukan menghasilkan paket. Jangan mengakali gate atau merge/main/tag tanpa rencana yang sah; siapkan jalur kandidat yang diizinkan terlebih dahulu.

## Matriks tes pemilik setelah kandidat ada
- RDP sendiri dan Buat RDP: profil/endpoint/credential benar, gagal autentikasi, timeout/retry/cancel, profil lama tetap ada.
- Mic native: izin pertama/ditolak/regrant, pilih input remote di Windows/game, level dan suara benar-benar diterima; audio PC→HP diuji terpisah.
- Background: layar mati/pindah aplikasi, efek disconnect seluruh desktop, reconnect background tanpa native capture, foreground connection sesuai preference+izin, rotasi/multi-window dan hasil izin terlambat.
- Mic/stream Direct: pasangan host/app baru, pin salah/berubah, token kadaluarsa/revoke, jaringan lemah, putus saat tombol input ditekan, tidak ada fallback plaintext.
- Windows: startup/shutdown EXE tunggal, endpoint input/output/driver, SA-MP launcher/crash log; lingkungan Roblox yang didukung, tanpa penyamaran VM/anti-cheat bypass.
- Log laporan: versi Android/Windows, ABI/build, langkah repro dan status perangkat. Hapus password, token, cookie, kunci dan URL bertanda tangan sebelum dibagikan.

### Native mic session reconnect control (new source gate)
- Ordinary RDP rail must show Mic RDP, not bridge OFF as if it were native OFF.
- Enable/disable requires confirmation warning that the desktop reconnects.
- RECORD_AUDIO denied/revoked, stale permission result, background, profile/owner
  switch, explicit cancel and teardown failure must not start replacement capture.
- Old AUDIN/native context must reach terminal cleanup before replacement starts;
  no timer-based assumption of capture OFF. Test timeout and delayed callbacks.
- Profile preference remains unchanged; only the current manager's intent changes.
- Verify Windows Remote Audio input and level with native ON, then OFF after
  reconnect. Bridge may not capture concurrently. Camera/playback remain separate.
- These checks still require signed candidate/device tests. Reconnect control is
  implemented, but an engine-supported instantaneous live mute is **not** claimed.
