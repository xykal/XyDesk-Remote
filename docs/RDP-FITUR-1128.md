# RDP 1.1.28 — menu Sesi, fitur XyRDP & panduan video

Ringkasan perubahan 2026-10-27. Berlaku untuk dua repo: `xykal/XyDesk-Remote`
(aplikasi Android) dan `xykal/XyRDP` (template workflow RDP 6 jam).

---

## 1. Menu Sesi tidak bisa dipencet — akar masalah

Gejala: setelah 1.1.27, membuka Menu Sesi membuat seluruh isi menu terasa mati.

Penyebabnya **bukan** layout bento, melainkan mode fokus slider yang baru
ditambahkan di 1.1.27:

- `SliderFocusState` adalah `object` global. Saat slider disentuh, ia mengatur
  `labelState` dan `SliderFocusHost` meredupkan seluruh UI ke alpha `0.06`.
- Penutupan hanya terjadi di blok `finally` gesture `XySlider`. Kalau gesture
  terputus di luar jalur normal (rotasi, panel ditutup saat jari masih turun,
  activity di-dispose), `end()` tidak pernah dipanggil → state nyangkut.
- Akibatnya menu berikutnya dirender nyaris transparan di atas desktop remote.
  Tombolnya sebenarnya masih ada, tapi tidak terlihat — persis seperti "tidak
  bisa mencet apa-apa".

Perbaikan (`ui/components/XyControls.kt`, `ui/SessionControls.kt`):

| Perubahan | Alasan |
| --- | --- |
| `end()` tidak lagi dipanggil dari `finally`; diganti `releaseGesture()` | Overlay tetap tampil sebentar setelah jari diangkat, nilai bisa disetel lanjut |
| Watchdog 2 detik setelah perubahan nilai terakhir | State global tidak mungkin nyangkut |
| `DisposableEffect` di `SliderFocusHost` | Bersih saat pindah activity / rotasi / logout |
| Lapisan ketuk bening (bukan scrim buram) | Desktop remote di bawah SessionControls tetap terlihat |
| `pointerInput` ketuk hanya dipasang saat `dragging == false` | Mencegah hit-test ulang memutus gesture slider |
| Kunci `pointerInput` slider jadi `Float` mentah | Range yang dibuat ulang tiap recomposition tidak lagi me-restart gesture di tengah drag |
| `LaunchedEffect(panelOpen)` di SessionControls | Setiap Menu Sesi dibuka, sisa state fokus dipaksa bersih |

---

## 2. Banner video panduan

Tab **Sesi** sekarang memuat kartu pertama "Panduan dari pembuat" yang membuka
video resmi pemilik: <https://youtu.be/0pjISgpLpDs?si=EGYS2pZb9yROhwfv>
("TUTORIAL LENGKAP RDP GRATIS!! GITHUB + TAILSCALE METODE", 18:07, kanal Kall).

Badge play digambar lokal dengan Compose — sengaja tanpa thumbnail jaringan
karena modul `app` tidak punya Coil/Glide dan gambar eksternal sering gagal di
mode hemat kuota. Isi kartu juga merangkum urutan: template → 2 Secrets →
Run workflow → sambungkan Tailscale.

---

## 3. Fitur XyRDP yang sebelumnya "belum bisa"

Sebelum ini beberapa toggle di aplikasi tidak punya pasangan di workflow
`rdp-6h.yml`, atau malah mengirim input yang tidak dideklarasikan (GitHub
menolak dengan HTTP 422).

| Fitur | Dulu | Sekarang |
| --- | --- | --- |
| `akses` Portmap / Railway | Aplikasi punya 3 pilihan, workflow cuma terima `tailscale` | Workflow menerima `tailscale` / `portmap` / `railway`; `scripts/setup-public-tunnel.ps1` menulis `akses.tunnel` yang dibaca aplikasi |
| `portmap_host` / `railway_host` | Input tidak ada di workflow → 422 | Cukup `portmap_host` + `portmap_port`; keduanya diteruskan juga ke auto-renew |
| Boost FPS | Hanya label "Gaming Boost" di `setup-xydesk.ps1` | Input `fps_boost` + `scripts/setup-fps-boost.ps1`: power plan, `DWMFRAMEINTERVAL`, Game Mode, HAGS, prioritas CPU/IO |
| Storage Boost | Bersihkan toolcache standar | Target tambahan (SDK lama, WER, Windows Update cache, DB runner) + laporan `out/storage-boost.json` |
| Reset Anti-Ban SAMP | Tombol cuma memicu sesi 60 menit biasa — GPCI tidak pernah berubah | Input `samp_antiban` + `scripts/setup-samp-antiban.ps1` benar-benar mengganti `MachineGuid` / `HwProfileGuid` / `PlayerName`, laporan `out/samp-antiban.json` |
| MuMu Player | Tidak ada (cuma MEmu dari `setup-games.ps1`) | Input `mumu` + `scripts/setup-mumu.ps1` (unduh dari halaman resmi / winget), shortcut desktop, README keterbatasan |
| Roblox / Minecraft Bedrock | Satu toggle mengunci keduanya | Dua toggle terpisah |
| "Cek Status Roblox Anti-VM" | Pesan sukses hardcode ("120 FPS aktif") tanpa verifikasi | Membuka run GitHub Actions yang sebenarnya |

Semua input baru bersifat `required: false`, jadi repo hasil template lama tetap
bisa menjalankan workflow (fitur baru tinggal pakai default). Penanda
`# rdpfree-workflow-schema: 5` sengaja dipertahankan agar kontrak
`web-next/src/lib/workflow-contract.ts` tetap cocok.

### Kontrak relay web ikut dibenerikan

`web-next/src/lib/workflow.ts` dulu mendeklarasikan `durasi_menit` sebagai
`choice`, padahal workflow asli memakai `type: string`. Akibatnya
`workflowCompatible()` selalu menolak repo asli dan `/api/v1/runs` membalas 409.
Sekarang `durasi_menit` divalidasi sebagai angka 10–360 dan input 1.1.28
ditambahkan ke `defaults`/`choices`.

---

## 4. Optimasi tambahan

- **Polling adaptif** di `RdpFreeScreen`: 8 detik saat boot, 20 detik saat sesi
  hidup, 30 detik saat tidak ada sesi (sebelumnya 8 detik terus-menerus,
  3–5 panggilan GitHub API per siklus).
- **Pesan 422 yang bisa ditindaklanjuti**: kalau repo belum disinkron, aplikasi
  menyuruh menekan tombol **Sync**, bukan cuma "Gagal mengirim workflow".
- **Parse semua `.ps1` di CI** (`validate-desktop.yml`): script baru tidak bisa
  lagi gagal diam-diam karena semua step di `rdp-6h.yml` memakai
  `continue-on-error: true`.

---

## 5. Batasan yang jujur

- Runner GitHub Actions **tidak punya GPU fisik**. Boost FPS menaikkan
  kelancaran *rendering RDP*, bukan FPS game 3D. Game berat tetap bergantung
  pada WARP/Mesa3D.
- Runner GitHub **umumnya tidak mengekspos VT-x/AMD-V**. MuMu bisa terpasang
  tetapi mesin Androidnya bisa gagal boot. MEmu tetap disiapkan sebagai
  fallback, dan status ditandai `fallback` bila instalasi gagal.
- Akses `portmap` / `railway` mengekspos RDP ke jalur publik. **Tailscale tetap
  disarankan.** Jika terpaksa, pakai password RDP yang kuat dan batasi ACL.
- Reset GPCI tidak menjamin lolos blokir server yang mendeteksi IP datacenter.
- Pengujian HP fisik dan host RDP Windows asli tetap menjadi bagian pemilik;
  yang diverifikasi di sini adalah kompilasi Kotlin, unit test, parse
  PowerShell, dan kontrak workflow.
