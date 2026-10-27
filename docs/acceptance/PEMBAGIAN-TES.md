# Pembagian kerja pengujian

Kesepakatan: **emulator di GitHub Actions = ranah CI/agent; perangkat fisik + host Windows RDP asli = ranah pemilik proyek.** Dokumen ini menjelaskan batasnya supaya tidak ada yang keliru mengartikan "CI hijau" sebagai "sudah teruji".

## 1. Apa yang bisa dibuktikan emulator

| Bisa | Tidak bisa |
|---|---|
| APK terpasang dan native library (`libfreerdp*`, JNI) berhasil dimuat | Handshake RDP/NLA/CredSSP melawan server Windows asli |
| Aplikasi cold start tanpa `FATAL EXCEPTION`, ANR, atau `SIGSEGV` | Kualitas gambar, GFX/AVC420, latensi, 120 Hz, performa GPU |
| Compose UI benar-benar ter-render (`uiautomator dump` berisi view) | IME pihak ketiga (Gboard, Samsung Keyboard) — image `google_apis` tidak membawanya |
| Rotasi, perubahan density, font 1.3×, dark/light | Audio PC → HP, mikrofon, kamera, dan bridge UDP :4433 |
| Process death → launch ulang, state tidak corrupt | Transfer file dua arah lewat drive `\\tsclient\XyDesk` |
| Perubahan status jaringan (offline) tidak bikin crash | Rekaman 720p60 + kursor PC, screenshot nyata |
| Regression dasar per level API (24 dan 36) | Gamepad fisik, gyro-mouse, haptics, sensor |
| Logcat + screenshot sebagai artefak bukti | Perilaku sertifikat/TOFU terhadap host sungguhan |

Intinya: emulator menjawab **"apakah aplikasinya hidup dan tidak crash?"**, bukan **"apakah RDP-nya benar-benar bekerja?"**.

## 2. Matriks yang dipakai

| API | Image | Arsitektur | Alasan |
|---|---|---|---|
| 24 | `google_apis` | `x86_64` | Batas bawah yang didokumentasikan README (`MIN_API` masih 23 — lihat catatan di bawah) |
| 36 | `google_apis` | `x86_64` | Android 16, image stabil terbaru yang tersedia |
| 37 | — | — | **Belum ada system image x86_64** (dicek 2026-10-26 di repository Google). Target SDK 37 diverifikasi lewat compile/lint saja |

**Catatan penting — `MIN_API` tidak cocok dengan dokumen.** `client/Android/Studio/release.properties` berisi `MIN_API=23` (Android 6.0), sementara README dan `docs/BUILD.md` menyatakan Android 7.0 / API 24. Pilih salah satu sebelum matriks dianggap final:

- **Direkomendasikan:** naikkan `MIN_API=24` agar sesuai dokumen dan menghindari kewajiban menguji API 23 (image `android-23;google_apis;x86_64` sebenarnya ada, tapi aplikasi tidak punya native lib `x86` 32-bit).
- Atau: turunkan README/BUILD.md ke API 23 dan tambahkan baris API 23 ke matriks emulator.

## 3. Cara kerja workflow `emulator-tests.yml`

1. Workflow `Build APK` selesai sukses di `main` → memicu `workflow_run`.
2. Job `preflight` memeriksa: run berasal dari repo yang sama (bukan fork), dan artifact `xydesk-remote-debug-per-abi` benar-benar ada. Commit `[skip-debug]` atau push bertag yang melewati `build-debug` akan **dilewati dengan pesan**, bukan gagal.
3. APK `x86_64` (fallback: universal) diunduh dari run tersebut — **tidak ada build native ulang**, jadi CI tidak bertambah ~30 menit.
4. KVM diaktifkan, AVD di-cache, lalu `reactivecircus/android-emulator-runner@v2.38.0` (di-pin ke SHA `a421e438…`) menjalankan `ci/emulator_smoke.sh`.
5. Screenshot, UI hierarchy XML, logcat, dan `summary.txt` diunggah sebagai artefak (retensi 7 hari).

Estimasi: ±8–12 menit per API level, berjalan paralel. Gratis untuk repo publik.

## 4. Roadmap pengujian bertahap

**Fase 1 (yang disiapkan sekarang)** — smoke test berbasis `adb` + `uiautomator`, tanpa perlu modul `androidTest`. Menutup risiko "APK terpasang tapi langsung crash" dan regresi layout.

**Fase 2** — tambahkan instrumented test (`app/src/androidTest/`) dengan Compose `createAndroidComposeRule`, plus dependensi `androidx.test.ext:junit`, `espresso-core`, `compose-ui-test-junit4`, dan runner `androidx.test.runner.AndroidJUnitRunner`. Target tes:
- state machine reconnect dengan `FakeRdpSession` (backoff 2/4/8/16/30 detik, stop oleh pengguna, autentikasi gagal tidak retry);
- `ImeCompositionBuffer` dites lewat `InputConnection` sungguhan, bukan cuma JVM;
- persistensi profil perangkat setelah process death;
- HUD drag/reset/collision dengan gesture Compose.

**Fase 3** — server RDP tiruan di runner (Python `socket` di port 3389, bisa dijangkau emulator lewat `10.0.2.2`). Ini bisa menguji jalur timeout dan reconnect tanpa Windows, tapi **tidak pernah** menggantikan uji host asli. Hanya dipakai untuk regression jaringan.

## 5. Release gate

`TEMPLATE.md` diisi untuk tiap versi. Supaya tidak cuma jadi dokumen, kaitkan ke CI:

1. Jadikan `Web Worker checks`, `Build APK`, dan `Emulator smoke tests` **required status checks** di branch protection `main`.
2. Tambahkan pengecekan di `publish-release.yml`: gagal kalau `docs/acceptance/v<versi>.md` belum ada untuk tag yang dipublish (bisa dibaca dari `release.properties` → `VERSION_NAME`).
3. Tag `v*` hanya dibuat setelah baris wajib di `TEMPLATE.md` berstatus ✅.

Ini yang menutup celah di `AUDIT-XyDesk-Remote.md`: "tidak ada Kotlin/native compile, unit test, APK build, IME test, RDP/DISP test, reconnect test, atau uji visual yang lolos". Sekarang sebagian besar bisa dibuktikan otomatis, sisanya tercatat eksplisit sebagai tugas pemilik proyek.
