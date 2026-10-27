# Audit: crash, disconnect, dan sesi "ke-kill"

**Tanggal:** 27 Oktober 2026 · **Basis:** `main` @ `11f13b3` + patch putaran 3
**Sifat:** audit statis. Tidak ada perangkat fisik / host Windows yang dipakai —
temuan di bawah harus dibuktikan ulang dengan logcat + acceptance matrix.

---

## Ringkasan temuan

| # | Gejala | Akar penyebab (dugaan kuat) | Status |
|---|---|---|---|
| 1 | Stuck "Menyambungkan ulang…" selamanya | `while (manager.instance() != 0L)` tanpa batas waktu | ✅ Diperbaiki |
| 2 | Sesi mati / proses dibunuh saat reconnect | FGS dicabut karena `activeCount()==0` | ✅ Diperbaiki |
| 3 | Aplikasi crash di layar RDP Gratis | 11× `token!!` / `login!!` | ✅ Diperbaiki |
| 4 | Crash saat rotasi / teardown | `rootView!!`, `scrollView!!`, `activeMouseBtn!!` | ✅ Diperbaiki |
| 5 | FGS gagal naik tapi dianggap sukses | `runCatching` menelan exception tanpa log | ✅ Diperbaiki |
| 6 | Auto-disconnect 15 detik di background | `BACKGROUND_DISCONNECT_DELAY_MS = 15_000L` | ⚠️ Perlu keputusan produk |
| 7 | Crash native / use-after-free | Belum bisa diverifikasi statis | ❌ Perlu logcat |

---

## 1. Reconnect tanpa batas waktu (PALING SERING terasa "disconnect")

`XyDeskSession.kt` — `LaunchedEffect(state, everConnected, userDisconnect, applyingResolution)`:

```kotlin
waitingForNativeRelease = manager.instance() != 0L
while (!userDisconnect && !applyingResolution && reconnecting && manager.instance() != 0L) {
    kotlinx.coroutines.delay(500)
}
```

`SessionManager` memang sengaja menahan instance native sampai callback terminal
(`freeInstance()` menunggu worker native berhenti). Tapi kalau callback itu tidak
pernah datang — native hang, worker diblokir OEM, atau proses dibekukan — loop
ini **tidak punya jalan keluar**. UI akan selamanya menampilkan status reconnect
dan tombol retry manual tidak pernah muncul.

**Perbaikan:** batas 20 tick × 500 ms = **10 detik**. Kalau instance masih
tertahan, retry otomatis dihentikan, `reconnectWindowDone = true`, dan muncul
pesan: *"Koneksi lama belum selesai dibersihkan. Tekan 'Coba lagi'…"*. Kita
**tidak** memaksa `manager.connect()` karena itu justru membalap cleanup native
(risiko use-after-free). Pengguna yang memegang kendali.

## 2. Foreground service dicabut di tengah reconnect

Tiga tempat memakai `XySessionRegistry.activeCount() == 0` sebagai syarat
mematikan `XySessionService`:

- `XyDeskSession.kt` — `LaunchedEffect(state, userDisconnect)`
- `XyDeskSessionActivity.onDestroy()`
- `XySessionService.onStartCommand()` → `ACTION_PING`

Padahal saat state berubah ke `Disconnected` / `Error`, `setActive(id, false)`
dipanggil → `activeCount()` jadi **0**, sementara sesi masih terdaftar dan
sedang menunggu backoff 2/4/8/16/30 detik. FGS langsung dimatikan → tidak ada
lagi jangkar proses → Android (terutama OEM agresif: Xiaomi, Oppo, Samsung)
bebas membekukan/mematikan proses. Ini persis terasa seperti "APK ke-kill".

**Perbaikan:** ketiga tempat kini memakai `XySessionRegistry.count() == 0`
(jumlah sesi yang masih terdaftar), bukan `activeCount()`. FGS tetap hidup
selama sesi ada, walau statusnya sedang tidak aktif sesaat.

## 3. NPE di layar RDP Gratis

`RdpFreeScreen.kt` punya 11 pemaksaan non-null: `refreshAll(token!!, login!!)`,
`syncForkWithUpstream(token!!, login!!)`, `enableWorkflow(token!!, login!!)`,
`createFork(token!!, login)` + `login!!` tiga kali, `pollValidatedSession(token!!, login!!)`,
dan `vsReady.validatedHost!!`.

`token` dan `login` diisi **asinkron** dari OAuth + `getVerifiedGitHubLogin()`.
Kalau pengguna menekan tombol sebelum identitas selesai diverifikasi, atau token
baru saja dihapus (logout), atau koneksi lambat — **langsung crash**.

**Perbaikan:** helper `auth(): Pair<String, String>?` dipanggil di dalam setiap
`scope.launch`, dengan pesan *"Sesi GitHub berakhir. Masuk lagi untuk
melanjutkan."* `validatedHost!!` diganti `orEmpty()`. Tidak ada `!!` tersisa di
file ini.

## 4. NPE saat rotasi / teardown

| Lokasi | Sebelum | Sesudah |
|---|---|---|
| `SessionSurfaceController.buildViewTree()` | `if (viewReady) return rootView!!` | `val existingRoot = rootView; if (viewReady && existingRoot != null) return existingRoot` — kalau root sudah dilepas, pohon view dibangun ulang |
| `remoteToScreen()` / `screenToRemote()` | `scrollView!!.getChildAt(0)` | `val scroller = scrollView; if (scroller != null && scroller.childCount > 0) …` |
| `XyDeskSession` pointer Press/Move | `sendButton(activeMouseBtn!!, false)` | `val heldBtn = activeMouseBtn; if (heldBtn != null) …` |
| `AddDeviceScreen` LAN scan | `lanSelfIp!!` | `val selfIp = lanSelfIp; if (!selfIp.isNullOrBlank()) …` |

`configChanges` di manifest sudah mencakup orientation/screenSize/density, jadi
Activity tidak di-recreate saat rotate — tapi view tree dan pointer state tetap
bisa berubah di bawah kaki kode. Pola `!!` di atas adalah bom waktu.

## 5. Kegagalan `startForeground` ditelan diam-diam

`XySessionService.promoteToForeground()` membungkus `startForeground()` dalam
`runCatching { }` tanpa `onFailure`. Kalau `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`
ditolak (kebijakan Android 14+/OEM), service mengira sudah padahal belum, lalu
sistem melempar `ForegroundServiceDidNotStartInTimeException` dan menutup proses.

**Perbaikan:** `Log.e(TAG, …)` + fallback ke overload `startForeground(id, notif)`,
dan log juga di `start()` saat `startForegroundService()` gagal dipanggil.
Manifest sudah benar (`<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" …/>`).

---

## 6. Yang masih perlu keputusan kamu

### Auto-disconnect 15 detik di background

`XyDeskSessionActivity.onStop()` menjadwalkan `bgDisconnect` setelah **15 detik**
kalau `keepAlive` dimatikan. Secara default `keepAlive = true`, jadi ini tidak
aktif — tapi kalau pengguna menonaktifkan keep-alive (mis. untuk hemat baterai),
beralih ke aplikasi lain sebentar saja sudah membuat sesi putus. Itu terasa
seperti "disconnect di beberapa aksi".

Rekomendasi: naikkan ke **60–120 detik**, atau tampilkan hitung mundur di
notifikasi supaya perilakunya tidak mengejutkan. Aku tidak mengubahnya karena
ini keputusan produk, bukan bug.

### Yang tidak bisa aku buktikan tanpa perangkat

- Native crash / use-after-free di jembatan JNI FreeRDP (butuh tombstone + logcat).
- `RemoteScreenRecorder` saat MediaRecorder gagal release.
- Perilaku FGS `specialUse` di Android 16 (API 36/37) pada OEM tertentu.
- ANR karena clipboard reader / sensor listener yang tidak dilepas.

Untuk itu, tambahkan ke CI: tangkap `adb logcat -d` di emulator smoke test
(sudah ada di `ci/emulator_smoke.sh`) dan minta pengguna mengirim log dengan
menu **Salin log diagnostik**. Ingat pesan di README: log bisa memuat host &
metadata sertifikat — samarkan sebelum dibagikan.

---

## Cara verifikasi perbaikan

1. `bash gradlew :app:compileDebugKotlin` — pastikan semua perubahan Kotlin
   lolos compile (belum bisa dijalankan di workspace ini: Java 11, tanpa SDK).
2. Jalankan `Emulator smoke tests` (workflow putaran 1) — rotasi, process death,
   dan offline kini punya deteksi crash/ANR.
3. Uji manual: matikan Wi-Fi 5/15/40 detik, lihat reconnect berhenti setelah
   maksimal 10 detik menunggu native (bukan menggantung), dan FGS tidak hilang
   dari notification shade.
4. Uji RDP Gratis: login GitHub lalu langsung tekan "Siapkan" berulang kali —
   tidak boleh crash walau identitas belum selesai diverifikasi.
