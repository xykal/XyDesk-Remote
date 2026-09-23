# Panduan Build — XyDesk Remote

## Prasyarat lokal

| Komponen | Versi | Catatan |
|---|---|---|
| Android Studio | Ladybug / 2024.2+ | import project Gradle |
| JDK | 17+ (21 disarankan) | terbawa dari Android Studio |
| Gradle | 9.6.1 (wrapper) | sudah ada di repo |
| AGP | 9.2.1 | dideklarasikan di `build.gradle` |
| Android NDK | **29.0.13113456** | wajib versi persis (di-set `release.properties`) |
| CMake (SDK) | **4.1.2** | dipasang via SDK Manager |
| compileSdk | 36 (stabil; 37 masih beta di repo Google) | `platforms;android-36` + `build-tools;37.0.0` |

## Build lokal (Android Studio)

1. **File → Open** → pilih folder `client/Android/Studio`.
2. Tunggu Gradle sync. Pastikan di **SDK Manager**:
   - SDK Platform: Android 37.2 (pakai package name `android-37.2`)
   - SDK Tools: NDK (Side by side) **29.0.13113456**, CMake **4.1.2**
3. `Build → Make Project` (atau klik Run).
4. **Build pertama lama (10–40 menit, butuh internet):** CMake superbuild
   otomatis download & kompil OpenSSL, FFmpeg, OpenH264, Opus, libpng,
   webp, libjpeg-turbo, cJSON, uriparser + FreeRDP core untuk tiap ABI.
   Build berikutnya cepat (incremental).
5. Output APK: `client/Android/Studio/app/build/outputs/apk/debug/app-debug.apk`
   (satu APK universal berisi `.so` arm64-v8a + x86_64).

### Catatan penting
- **Jangan commit** folder `freeRDPCore/src/main/jniLibs/` atau `.cxx/`
  (sudah di-.gitignore) — itu output superbuild.
- Device uji: HP asli (arm64). Emulator x86_64 bisa dipakai untuk uji UI
  cepat, tapi performa RDP & hardware decode tidak representatif.
- Target RDP: Windows 10/11 Pro/Enterprise/Edu atau Windows Server dengan
  Remote Desktop + NLA enabled. **Windows Home tidak bisa menjadi target.**

## Build via GitHub Actions (CI)

- Workflow: `.github/workflows/build-apk.yml`
- Trigger: push ke `main`, atau manual (tab **Actions** → *Build APK* →
  *Run workflow*).
- Hasil: artifact **`xydesk-remote-debug`** (unduh dari halaman run).
- Durasi: run pertama ±45–75 menit (download NDK ~1.3GB + kompil
  superbuild 2 ABI); run berikutnya lebih cepat (cache NDK/CMake).

## Ubah konfigurasi build

Semua knob ada di `client/Android/Studio/release.properties`:

| Key | Default M0 | Fungsi |
|---|---|---|
| `ABI_FILTERS` | `arm64-v8a;x86_64` | ABI yang di-build (tambah `armeabi-v7a` kalau perlu) |
| `SPLIT_ENABLED` | `false` | `true` = APK per-ABI (plus universal) |
| `VERSION_NAME` / `VERSION_CODE` | `0.1.0-m0` / `1` | bump tiap milestone |
| `CMAKE_ARGUMENTS` | semua codec `ON` | matikan `WITH_FFMPEG/OH264/OPUS/...` untuk build jauh lebih cepat (mode uji) |
| `COMPILE_API` / `TARGET_API` / `MIN_API` | 37/37/29 | tingkat API |

## M0 — smoke test

1. Enable RDP di target Windows: *Settings → System → Remote Desktop → On*
   (pastikan akun punya password; NLA default on).
2. Jalankan app di HP (satu LAN / port forward 3389).
3. Form: **Host** = IP Windows, **Port** = 3389 (bisa kosong), **Username**,
   **Password** → **Hubungkan**.
4. Kalau muncul dialog sertifikat: cocokkan fingerprint (di M3 ada UI trust
   manager; untuk M0 teruskan).
5. Sukses = desktop Windows tampil, touch = mouse, gesture double-tap = klik
   kanan, keyboard on-screen tersedia, bisa ngetik di Notepad.
6. Keluar sesi via tombol disconnect di toolbar bawaan inti FreeRDP.

## Troubleshooting umum

| Gejala | Solusi |
|---|---|
| CMake error `Could not detect NDK root` | NDK 29.0.13113456 belum terpasang di SDK Manager |
| Build superbuild gagal download (SSL) | Butuh internet; cek proxy korporat |
| `APK broken: native library version` | `.so` tertinggal di `jniLibs` dari build lama — hapus `freeRDPCore/src/main/jniLibs/` lalu clean build |
| Connect gagal: `NLA` | Target harus Windows Pro/Server + NLA on; coba nonaktifkan NLA di target untuk memastikan jalur jaringan dulu |
| APk install gagal di HP 32-bit | M0 hanya build arm64-v8a + x86_64 |
