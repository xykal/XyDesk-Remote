# XyDesk Remote — NOTICE

Repo `xykal/XyDesk-Remote` mengandung **tree sumber FreeRDP versi 3.32.0**
(vendored, pin via git) sebagai fondasi klien RDP Android-nya, sesuai lisensi
Apache License 2.0 (lihat `LICENSE`).

FreeRDP — A Remote Desktop Protocol Implementation
Copyright (C) 2012-2026 FreeRDP contributors (Thincast/FreeRDP project)
https://freerdp.com · https://github.com/FreeRDP/FreeRDP

## Perubahan XyDesk terhadap tree FreeRDP

Daftar ini disengaja dipertahankan sekecil mungkin agar upgrade FreeRDP
nantinya (re-merge tree) tetap murah:

1. `client/Android/Studio/build.gradle` — dihapus plugin
   `com.gladed.androidgitversion` (versionName/versionCode sekarang diambil
   dari `release.properties`); default split/ABI disesuaikan (arm64-v8a +
   x86_64, satu APK universal).
2. `client/Android/Studio/settings.gradle` — modul `:aFreeRDP` diganti
   `:app` (aplikasi XyDesk Remote).
3. `client/Android/Studio/aFreeRDP/` — dihapus (digantikan modul `:app`).
4. `client/Android/Studio/release.properties` — file baru (konfigurasi build).
5. `client/Android/Studio/app/` — modul aplikasi XyDesk (baru, milik XyVerse).
6. `.github/workflows/build-apk.yml` — CI XyDesk (workflow CI upstream
   FreeRDP dihapus agar tidak ter-trigger di repo ini).
7. `README.md` — README XyDesk; README upstream diarsipkan sebagai
   `FreeRDP-README.md`.
8. `.gitignore` — ditambah entri artifact Android/Gradle.

Seluruh file lain di dalam tree FreeRDP (termasuk
`client/Android/Studio/freeRDPCore/`) **tidak dimodifikasi** per kondisi
milestone M0.

## Lisensi kode XyDesk

Kode di `client/Android/Studio/app/` dan dokumen XyDesk (`README.md`,
`PLAN.md`, `docs/BUILD.md`, file ini) adalah © XyVerse.
Lisensi distribusi akan ditetapkan saat milestone M5.

## Perubahan tambahan (v0.2.1)
- `client/Android/Studio/freeRDPCore/src/main/cpp/CMakeLists.txt`: ditambah
  "header ordering guard" — dependensi file-level eksplisit pada
  `freerdp/config.h` (header generated) agar ninja tidak mengompilasi
  `freerdp-android` sebelum install step superbuild FreeRDP selesai
  (mengatasi kegagalan build RelWithDebInfo).

## Perubahan tambahan (APK size diet — fase 1)
- `client/Android/cmake/ExternalDeps.cmake`: `copyLibs` kini memanggil
  `StripJniLibs.cmake` setelah `copy_directory` — semua `.so` prebuilt di
  `jniLibs/<abi>/` di-strip (`llvm-strip --strip-unneeded`) dan duplikat
  dev-name `libpng.so` dihapus (runtime hanya memakai SONAME `libpng16.so`).
  Alasan: AGP tidak men-strip prebuilt jniLibs di pipeline ini; APK v1.1.21
  memuat `libfreerdp3.so` 20 MB dengan `debug_info`.
- `client/Android/cmake/StripJniLibs.cmake`: file baru (helper strip di atas).
- `client/Android/cmake/ExternalFFmpeg.cmake`: FFmpeg dikonfigurasi slim —
  `--disable-everything` + hanya decoder/encoder/parser yang benar-benar
  dipakai `libfreerdp/codec/dsp_ffmpeg.c` dan `h264_ffmpeg.c`;
  `avformat`/`avfilter`/`avdevice`/`postproc` tidak dibangun (tidak ada satu
  pun source FreeRDP yang meng-include headernya).
- `client/Android/Studio/freeRDPCore/src/main/cpp/CMakeLists.txt`: POST_BUILD
  strip untuk target `freerdp-android` dan `xydesk-quic` pada build non-Debug.
- `client/Android/Studio/app/build.gradle`: `shrinkResources true` (sebelumnya
  `false`).

## Perubahan tambahan — lifecycle mic native Android (AUDIN)
- `channels/audin/client/opensles/audin_opensl_es.c`: pasang callback/data sebelum recorder dapat memanggilnya, tolak duplicate Open tanpa menutup recorder aktif, jaga callback kosong dan tutup recorder sebelum Free.
- `channels/audin/client/opensles/opensl_io.c`: periksa kegagalan Enqueue/SetRecordState, tolak dimensi nol dan hentikan/hancurkan recorder sebelum membebaskan buffer yang masih dimiliki OpenSL.
- Tidak mengubah format/DSP default, endpoint, tag protokol atau ABI interface AUDIN. Lisensi/copyright upstream dipertahankan.

## Perubahan tambahan — ownership native session Java
- `freeRDPCore` menunda pelepasan entry registry/core sampai native free sukses, menyerialisasi pelepasan owner yang sama, dan mempertahankan wait terminal saat interupsi Java.
- Helper Java murni `SessionReleaseTransaction` dan `NativeWorkerTermination` beserta10 tes JVM ditambahkan. Tidak mengubah ABI JNI, protokol atau DSP. Bukan implementasi live microphone mute.
