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

## Lanjutan: audit lifecycle capture native (belum live mute)
Ditemukan dan diperbaiki dua urutan yang berbahaya: recorder bisa memanggil audin_receive sebelum callback tujuan disetel; Close membebaskan buffer yang masih dimiliki recorder sebelum Destroy. Free juga kini menutup stream, dan duplicate Open tidak lagi menutup recorder aktif. Kegagalan enqueue buffer/start rekaman tidak lagi dilaporkan sebagai Open sukses. Tidak mengubah DSP/format, kanal, protokol atau nama perangkat.

Verifikasi lokal: dua harness C mengambil body fungsi produksi, memakai API OpenSL/WinPR tiruan, dikompilasi/dijalankan dengan ASan+UBSan. Skenario mencakup callback sebelum Open kembali, callback saat penutupan, duplicate Open, Open gagal, Close plugin berulang, Free tanpa Close, dimensi nol, dua kegagalan enqueue dan start gagal. Counterexample urutan callback lama dan Close lama sama-sama berhasil dikompilasi lalu gagal saat runtime; source perbaikan lulus. Workflow `audin-checks.yml` menjalankan tes yang sama di CI.

**Batas bukti:** ini benar-benar kompilasi/eksekusi program UNIT TEST C host sementara, bukan pemeriksaan source saja. Bukan build APK, Android/NDK, EXE host Windows, ABI/link produksi atau pengujian perangkat OpenSL nyata. Mock tidak membuktikan seluruh threading/teardown vendor Android. Kontrol live mute/native-background masih belum diimplementasi; jangan menandai kewajiban itu selesai.

Hasil CI untuk394b94f: **AUDIN lifecycle39207803271 SUCCESS**, **WebCI39207803194 SUCCESS**. Dua harness C ASan/UBSan dan8 source-contract checks lulus; ini tetap bukan NDK/link/runtime perangkat Android.

## Lanjutan: pelepasan engine harus terkonfirmasi
Audit menemukan `GlobalApp.freeSession` membuang entry registry sebelum native free kembali, dan SessionManager mengosongkan core sebelum free berhasil. Jika free melempar exception, penanda ownership hilang terlalu dini. Kini native free dijalankan terlebih dahulu; entry registry/core baru dibuang setelah sukses. Release untuk owner yang sama diserialisasi, tanpa menahan monitor registry global selama native teardown. Kegagalan cleanup terminal mempertahankan core, memberi error `native_cleanup_unconfirmed`, dan tidak diteruskan ke jalur retry/Disconnected normal.

`LibFreeRDP.freeInstance` kini memakai wait helper yang tetap menunggu worker terminal ketika thread Java diinterupsi, lalu mengembalikan interrupt flag sesudahnya. Interupsi bukan bukti bahwa recorder/worker native berhenti. Ini tidak menambahkan timeout yang membebaskan memory saat native masih berjalan.

Sepuluh tes JVM murni memakai helper produksi (bukan Android/JNI): worker nonaktif, interupsi saat menunggu, spurious notify, stop melempar error, interrupt awal; registry masih memiliki owner saat free, kegagalan mempertahankan owner, repeated/missing free, replacement owner, dan dua release konkuren dengan callback registry tetap bisa membaca. Lokal PASS, bersama9 source-contract checks dan2 tes C mock AUDIN. CI Kotlin/JVM integrasi belum dinyatakan lulus sebelum hasil run tersedia.

**Masih terbuka:** kebijakan stop/background dan kontrol live native mic belum selesai. Wait native masih dapat lama bila engine tidak mengirim terminal; jangan menyatakan mic OFF ketika putus/free tidak terkonfirmasi. Tes ini bukan uji rekaman Android, teardown vendor OpenSL, provisioning atau game pada Windows nyata. Secure Direct dan seluruh gate draft sebelumnya tetap terbuka.

Hasil integrasi603793f: **Kotlin/JVM gate39235701973 SUCCESS**, **WebCI39235702481 SUCCESS**. Sepuluh tes lifecycle Java termasuk pada unit-test :freeRDPCore,9 source-contract checks PASS. Tidak ada build APK/native Android/EXE atau release draft pada verifikasi ini.

## Lanjutan: pengaman background native capture
Source kini mengikat izin capture ke visibility Activity (`STARTED`), bukan foreground service keep-alive:
- SessionManager default tidak mengizinkan capture; onStart mengizinkan dan baru menjalankan initial connect yang tertunda. Hasil izin ketika Activity tidak tampil ditunda, bukan memulai native capture dari background.
- Setiap URI baru/reconnect background menghilangkan mic/kamera native tanpa mengubah profil tersimpan. Audio playback/desktop-only keep-alive tetap memakai pengaturan sebelumnya.
- onStop memblokir capture baru, mematikan mic bridge, dan meminta disconnect untuk sambungan yang URI-nya meminta native mic/kamera. Ini **memutus koneksi desktop juga**, bukan live mute AUDIN. Worker memeriksa visibility lagi setelah probe sebelum native start; success callback terlambat saat background juga diarahkan ke disconnect.
- Jangan menyatakan recorder sudah OFF hanya karena onStop atau disconnect diminta. Konfirmasi teardown masih melalui engine/release. Kegagalan/unconfirmed state tetap belum aman untuk diklaim berhenti.
- Sambungan BARU di foreground bisa meminta capture lagi sesuai pilihan profil dan izin saat itu. Bridge tetap butuh aksi eksplisit. Ini bukan latch izin global permanen atau pernyataan native live toggle selesai.

Lima tes JVM CaptureVisibilityPolicyTest ditambahkan: background menolak capture meski izin ada (termasuk argumen AUDIN/rdpecam tidak dikirim), foreground tidak memaksa opsi ON, izin ditolak tidak dilewati, opsi desktop/playback dipertahankan, dan sambungan foreground berikutnya memakai preference+izin tanpa mutasi profil. Source-contract10 PASS lokal; hasil compile/integrasi CI perlu diperiksa. Tetap perlu uji perangkat: layar mati, pindah app, multi-window/rotasi, permission dialog, probe lambat, reconnect dan input suara Windows.

Izin draft pengujian pemilik tetap berlaku setelah pekerjaan source selesai. Verifikasi runtime/signing/ukuran memang memerlukan release-candidate build; jangan membuat syarat melingkar “hardware test sebelum ada binary”. Urutannya: selesaikan fitur dan source gates → build kandidat release bertanda tangan → verifikasi binary/artifact → draft dengan batas uji yang jujur untuk tes pemilik. Saat ini secure Direct dan native live mute masih belum diimplementasikan, jadi belum boleh menyebut semua selesai atau menerbitkan draft seolah fitur itu tersedia.

Pengaman tambahan: hasil izin yang diputar ulang di dalam super.onStart tetap menunggu flag readiness Activity sebelum native connect; pembatalan selama probe juga dicek lagi sebelum worker native dimulai. Success callback setelah permintaan putus tidak boleh menghidupkan kembali sesi itu.

Verifikasi3151fc1: **Kotlin/JVM gate39241778445 SUCCESS**, **WebCI39241779812 SUCCESS**. Termasuk5 tes CaptureVisibilityPolicy baru dan10 source-contract checks. Ini tidak membuktikan teardown/hardware pada Android nyata; stop background tetap berupa permintaan disconnect seluruh sambungan capture, bukan native live mute.

## Session native mic controls (source implementation; runtime verification pending)

The session rail uses **Mic RDP** for ordinary RDP, opening the readiness/control
panel rather than silently switching the independent UDP bridge. Native mic can
be enabled or disabled with explicit **reconnect confirmation**. This is not
in-place/live mute: the desktop disconnects and may need authentication again.

`SessionMicrophoneControl` keeps a session-only override scoped to the profile.
The manager admits changes only for the expected current connected native owner,
while foreground-eligible, and checks RECORD_AUDIO again before enabling. The
new URI still passes the existing permission and visibility filters. Saved
profiles are not modified. A profile switch drops the override.

Bridge capture is stopped first; enabling the bridge while native mic is requested
or a native change is pending is rejected. Native stop is requested, NOT declared
confirmed OFF. Only a terminal event **and successful old-core cleanup** can
schedule the replacement connection. The posted continuation is generation-bound:
explicit cancel/disconnect/release, a new accepted connection, or backgrounding
invalidates it. Cancelled, not-yet-committed activation is suppressed. Failed or
unconfirmed cleanup must not open a replacement native channel.

Nine pure Kotlin tests cover URI omission, immutable saved preferences, permission,
visibility/PC restrictions, owner binding, single consumption, stale generations,
profile changes, committed preferences, invalid handles and stale permission generations even if a native address is reused. Source guards check
integration ordering only. Neither these tests nor the panel prove Android
recorder shutdown latency, Windows input routing, or game capture.
