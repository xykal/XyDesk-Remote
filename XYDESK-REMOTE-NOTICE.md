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
