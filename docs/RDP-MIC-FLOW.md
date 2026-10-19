# RDP biasa + Buat RDP: alur mikrofon dan UI

Instruksi pemilik: mic harus tercakup pada koneksi RDP biasa juga; tinjau UI Buat RDP dan alurnya, lalu draft hanya setelah selesai/verifikasi. Ini tidak terbatas pada PC Stream.

## Alur yang ditelusuri di source
1. **RDP sendiri:** Tambah/Edit perangkat → Audio → `Mic HP → RDP (AUDIN)` → simpan → sambungkan. Ini kanal native FreeRDP, bukan tombol bridge UDP.
2. **Buat RDP/XyRDP:** konfigurasi runner dan pilihan mic → buat/pantau → validasi endpoint aktif → Sambungkan. `forRdpFree(microphone)` menyimpan audio DEVICE, native mic sesuai pilihan, quicAudio=false; tidak memerlukan Host Agent UDP untuk AUDIN. Tidak mengubah kontrak runner/DSP/endpoint.
3. **Sebelum native connect:** Activity meminta izin kamera/mic hanya untuk kanal RDP yang diminta, bukan profil PC lama. Penolakan tidak menghapus pilihan profil. SessionManager memeriksa izin Android saat membentuk URI untuk SETIAP koneksi/reconnect, termasuk yang tidak melalui dialog Activity.
4. **Di Windows/game:** pilih input remote/AUDIN yang tersedia (umumnya Remote Audio), uji level dan penerimaan suara. Dukungan capture Linux tergantung server/konfigurasi. ON di profil atau izin Android bukan bukti negosiasi/capture/server/game berhasil.
5. **Dalam sesi:** menu `Mic RDP · profil & izin` membuka kesiapan. `Mic bridge` tetap jalur berbeda dan tidak mematikan native AUDIN. Saat ini perubahan native mic perlu edit profil dan reconnect; tidak mengklaim ada live native mute. Audio PC→HP juga terpisah dari mic HP→PC.

## Perubahan dan verifikasi
- Filter izin native session-only mempertahankan pilihan tersimpan, membedakan izin camera/mic, menghindari kanal native pada profil PC lama, dan diperiksa lagi saat reconnect.
- Ditambahkan lima JVM regression tests: izin/preferensi, AUDIN tanpa bridge, izin camera/mic independen, revoke/regrant pada sambungan berikutnya, profil PC lama tanpa native capture. Gate Kotlin/JVM39206503863@19a5b97 **SUCCESS**, termasuk suite ini; bukan build APK atau bukti capture runtime.
- WebCI39206504271@19a5b97 **SUCCESS**;8 source-contract tests PASS.
- UI Buat RDP menjelaskan langkah dan kebutuhan server; menghapus label mic “dua arah” yang menyesatkan dan klaim Host Boost pasti120FPS. Tidak mengubah input provisioning atau menjanjikan keberhasilan game.

## Acceptance yang masih terbuka sebelum draft
- Bukti perangkat: izin pertama/ditolak/permanen ditolak/regrant/revoke, connect/reconnect, server Windows/Linux yang didukung, input dipilih di game dan suara benar-benar diterima.
- Native mic belum memiliki kontrol live start/stop/background unified yang tervalidasi. Bridge OFF bukan bukti native capture berhenti; jangan menyatakan mic seluruh sesi OFF. Perlu penanganan dan pengujian lifecycle native sebelum lulus privasi.
- UI provisioning: timeout/cancel/retry, credential salah, endpoint hilang, runner gagal, profil lama tetap ada dan pilihan mic bertahan. Belum mengklaim uji end-to-end provisioning.
- Secure PC Direct dua endpoint tetap belum diimplementasikan; native EXE/APK release/link/runtime/signing/ukuran belum diverifikasi. Karena itu belum boleh membuat draft yang mengklaim semua pekerjaan selesai. Izin pemilik untuk build + draft setelah selesai tetap berlaku; tidak perlu meminta izin per langkah kecil.
