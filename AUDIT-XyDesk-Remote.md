# Audit XyDesk Remote — static review

**Tanggal:** 2026-09-26  
**Ruang lingkup:** alur tombol Simpan & Connect, session lifecycle Kotlin/Java, FreeRDP JNI wrapper, dan native Android event queue.  
**Batasan:** audit statis pada source checkout; belum ada crash log perangkat, Android SDK/NDK lengkap, atau build Android yang sukses. Ini bukan klaim runtime fix terverifikasi.

## Temuan

### P1 — Event queue JNI/native tidak disinkronkan (risiko korupsi memori/crash) — PATCH DIBUAT, BELUM DIBUILD

`android_event.c` mengubah `event_queue->count`, `events`, dan dapat `realloc()` buffer pada saat thread FreeRDP membaca/menggeser queue. Perbaikan workspace menambahkan `CRITICAL_SECTION`, melindungi enqueue serta pop atomik, melakukan pemrosesan payload di luar lock, dan membuang event yang masih pending saat queue dihancurkan. Kegagalan alokasi / `SetEvent` juga tidak meninggalkan event ter-queue yang caller mungkin bebaskan. Event disconnect yang dikonsumsi kini dianggap sukses; sebelumnya rc default `FALSE` memicu jalur kegagalan event loop.

Ini menutup race pada isi queue. Pengamanan terhadap pemanggil yang balapan dengan penghancuran seluruh `event_queue` tetap bergantung pada lifecycle FreeRDP memastikan ClientFree setelah producer berhenti; wajib diuji di build/perangkat.

### P1 — Pelepasan instance ketika koneksi native masih berjalan — PATCH DIBUAT, BELUM DIBUILD

`freeInstance()` sebelumnya hanya menunggu status yang baru menjadi `true` setelah callback `OnConnectionSuccess`; selama handshake belum tercatat, sehingga teardown dapat membebaskan konteks yang masih dipakai thread native. Kini `LibFreeRDP.connect()` mendaftarkan instance sebagai aktif sebelum membuat native worker dan membersihkan/notify bila worker gagal dimulai. `freeInstance()` dapat meminta disconnect dan menunggu terminal callback selama fase handshake. `SessionState.connect()` juga kini memeriksa hasil parse dan hasil mulai thread koneksi daripada mengabaikan keduanya.

Perubahan ini menutup celah lifecycle yang teridentifikasi secara statis, tetapi belum ada verifikasi device/tombstone untuk menyatakan itulah akar crash pengguna.

### P2 — Password sebelumnya ikut masuk ke log

- `SessionManager.connect()` menulis URI RDP ke `ConnectionLog`; URI dapat memuat password pada query `p=`.
- Form klasik `MainActivity` menulis URI yang sama ke Android Logcat.

Keduanya sudah diperbaiki di workspace audit: log kini hanya menulis host dan port. Ini mitigasi kebocoran kredensial, bukan crash fix.

### P2 — Simpan dan connect sebelumnya berjalan paralel; kegagalan persistensi tak tertangani

Pada form Compose, `repo.save()` dijalankan dalam coroutine sementara `connectTo()` langsung dipanggil di luar coroutine. Error Room/Keystore tidak ditangani dan urutan penyimpanan tidak dijamin. Sudah diubah agar penyimpanan dicoba dulu, exception ditangkap dan dilaporkan, lalu koneksi dibuka. Belum diverifikasi di perangkat/build.

### P2 — Hasil parsing konfigurasi FreeRDP diabaikan — DIPERBAIKI DI WORKSPACE

`SessionState.connect()` sekarang melempar error eksplisit bila parser FreeRDP menolak argumen atau native worker gagal dimulai. JNI `freerdp_parse_arguments()` juga tidak lagi melaporkan sukses ketika alokasi gagal, membersihkan referensi JNI lokal, dan membebaskan buffer argumen parsial.

## Verifikasi yang sudah dilakukan

- Menelusuri alur tombol form → repository → `XyDeskSessionActivity` → `SessionManager` → `GlobalApp` → JNI `freerdp_connect`.
- Meninjau lifecycle native connect/disconnect dan queue event FreeRDP.
- `git diff --check`: lulus.
- Gradle compile belum jalan: JVM sandbox hanya Java 11, sementara wrapper Gradle proyek meminta Java 17+.
- Belum ada APK hasil build, tes perangkat, atau Logcat crash untuk mengonfirmasi akar crash yang dialami.

## Perubahan di workspace

1. Simpan profil selesai/tertangani sebelum navigasi ke sesi, dengan pesan error yang tidak membunuh aplikasi.
2. Menghapus password dari URI yang ditulis ke log.
3. Mengunci event queue native lintas thread serta membersihkan event tersisa.
4. Menyinkronkan start native connect dengan release, menandai sesi aktif sejak sebelum thread native dimulai, dan menolak parse/start failure.

## Langkah sebelum menyebut fix valid

1. Jalankan CI GitHub Actions debug melalui workflow `Build APK` pada branch berisi patch.
2. Ambil artifact `xydesk-remote-debug-per-abi`, lalu uji APK `armeabi-v7a` di perangkat fisik.
3. Reproduksi: simpan & connect, koneksi gagal/sukses, cancel saat handshake, tutup layar saat Connecting, lalu uji input dan clipboard.
4. Simpan Logcat bila crash; cocokkan crash tombstone/native stack trace ke event queue atau lifecycle.
5. Build + tes ulang patch queue/lifecycle pada perangkat, khususnya connect/cancel/close saat handshake dan burst input/clipboard/disconnect.

## Status publikasi

Patch masih berada di workspace kerja ini dan belum dipush. Tidak ada GitHub credential aman yang disediakan untuk autentikasi push; token yang sempat muncul di file lampiran harus dicabut/dirotasi, dan tidak digunakan untuk push/build. Karena itu link artifact APK belum tersedia.
