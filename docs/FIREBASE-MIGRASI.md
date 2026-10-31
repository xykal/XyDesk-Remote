# Bisakah migrasi penuh ke Firebase?

**Pertanyaan:** Cloudflare Workers untuk XyDesk saja sudah ~90.000 request/hari
(mendekati batas 100.000/hari akun Free). Apakah lebih baik pindah semuanya ke
Firebase?

**Jawaban singkat: bisa secara teknis, tapi migrasi penuh bukan solusi untuk
masalah kuota — dan biayanya lebih besar dari yang terlihat.** Rekomendasi:
tetap Cloudflare, naik ke Workers Paid $5/bulan, dan pakai Firebase hanya untuk
hal yang memang cocok (Auth/Analytics/Crashlytics). Alasannya di bawah.

---

## 1. Firebase juga punya batas — dan batasnya lebih kecil

Angka 90.000 request/hari = **~2,7 juta request/bulan**.

| Layanan | Kuota gratis (2026) | Konsekuensi buat 90k/hari |
|---|---|---|
| Cloud Functions | **Tidak ada di Spark** (wajib Blaze); 2 juta invocation/bulan gratis di Blaze, lalu $0,40/juta [1](https://firebase.google.com/pricing) · [7](https://deploymanage.com/firebase-pricing) | 2,7 juta/bulan = 0,7 juta di atas kuota → ±$0,28/bulan + GB-detik/CPU-detik |
| Firestore | 50.000 baca/hari, 20.000 tulis/hari, 1 GiB [1](https://firebase.google.com/pricing) · [12](https://firebase.google.com/docs/firestore/quotas) | Kalau tiap request baca 1 dokumen, 90k baca/hari = 40k/hari di atas kuota → ±$0,36/bulan |
| Firebase Hosting | 10 GB/bulan (≈360 MB/hari) transfer, 10 GB storage [6](https://we.inc/guides/firebase-pricing) | Aset web statis XyDesk + unduhan APK bisa menembus ini |
| Cloud Storage | **Wajib Blaze sejak 3 Feb 2026**, tidak ada lagi di Spark [5](https://dev.to/androve2k/spark-vs-blaze-the-firebase-pricing-guide-i-wish-id-read-sooner-onb) · [10](https://agentdeals.dev/vendor/firebase) | Harus ada kartu kredit sejak hari pertama |
| Auth / Crashlytics / Analytics | Gratis (no-cost products) [1](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans) | Ini bagian yang memang murah & cocok |

Artinya: **pindah ke Firebase tidak membuat jadi "gratis tanpa batas"**. Spark
(nya yang benar-benar tanpa kartu kredit) bahkan tidak menyediakan Cloud
Functions sama sekali, jadi API Worker tidak bisa jalan di sana. Kamu tetap
masuk Blaze (pay-as-you-go), dan kuota harian Firestore 50k baca justru **lebih
kecil** dari 100k request Workers Free.

Catatan: biaya murni di Blaze untuk volume ini memang kecil (sekitar
$1–3/bulan tergantung jumlah baca/tulis dan bandwidth). Tapi itu **sebelum**
biaya rewrite, migrasi data, dan risiko protokol.

---

## 2. Yang harus dipindahkan (ini yang mahal)

`cloudflare/worker.mjs` saat ini **3.158 baris** dengan binding:

- **7 custom domain route** (`www`, apex, `api`, `wss`, `cpc`, `report`, `ask`)
  dengan routing berbasis host.
- **D1** (1 database), **KV** (1 namespace), **Durable Objects** 4 binding:
  `ACTIVE_SESSIONS`, `PC_SIGNAL_ROOMS`, `FEEDBACK_BOARD`, `COMMUNITY_JOKES`.
- **Static assets** (`web/`) dengan 30 path `run_worker_first`.
- OAuth GitHub exchange, release/update-check API, download alias, UI translate,
  session heartbeat, PC relay, feedback, stats.

Padanan Firebase-nya tidak 1:1:

| Cloudflare | Firebase | Catatan |
|---|---|---|
| Durable Objects (state per sesi, single-writer) | Firestore + transaksi / Realtime DB presence | **Tidak ada padanan langsung.** Ini bagian paling berisiko: counter sesi aktif dan sinyal PC butuh konsistensi yang tidak otomatis didapat dari Firestore. |
| D1 | Firestore | Perlu redesign query & index |
| KV | Firestore / Realtime DB | Latency edge hilang |
| Worker (edge, 300+ PoP) | Cloud Functions (region tunggal) | Latensi naik buat pengguna jauh dari region |
| Custom domain + routing host | Firebase Hosting rewrites + Cloud Functions | Perlu setup ulang DNS 7 domain |
| `api.xydeskremote.biz.id` (hardcode di APK) | `*.cloudfunctions.net` / Hosting | **Semua klien Android harus diubah** — tidak bisa零 downtime |
| WebSocket (`wss.*`) | Realtime DB / custom server | Perlu redesign signaling PC Stream |

Ada `google-services.json` di repo (project `xyverse-technology-global`), tapi
**tidak ada lagi dependensi Firebase di Gradle** — cuma `play-services-ads`.
Jadi migrasi = nambah BoM + Firestore + Functions SDK, nulis ulang worker,
migrasi data D1/KV/DO, ubah URL di APK, dan koordinasi protokol lintas repo lewat
issue `xykal/XyDesk-Remote#8` / `xykal/XyDesk#34` (sesuai `AGENTS.md`).

Estimasi: **minggu, bukan jam**, dan butuh keputusan soal region, billing, dan
versi minimum APK yang masih bisa connect.

---

## 3. Kenapa 90k itu terjadi (akar masalahnya bukan platform)

Dari audit sebelumnya (`docs/CLOUDFLARE-1027.md`):

1. `run_worker_first` masih memaksa `/` dan `/index.html` lewat Worker →
   **setiap page view = 1 request Worker**.
2. `web/index.html` memicu 5 fetch per page + polling `/api/active-sessions`.
3. 7 domain + crawler bebas (`robots.txt` = `Allow: /`).
4. Default `cache-control: no-store` di `jsonResponse`.

Ini **boros di platform mana pun**. Kalau dipindah ke Firebase apa adanya, 90k
request/hari tetap jadi 90k baca Firestore / 90k invocation Functions — limit
tetap ketemu, cuma ganti nama.

---

## 4. Rekomendasi (urutan prioritas)

### Langkah 1 — paling murah, langsung beres (5 menit)
**Workers Paid $5/bulan.** 10 juta request/bulan termasuk, **tanpa batas harian**,
CPU 30 detik (vs 10 ms). 90k/hari = 2,7 juta/bulan → masuk kuota dengan sisa
besar. Tidak ada perubahan kode, tidak ada downtime. [Cloudflare pricing](https://developers.cloudflare.com/workers/platform/pricing/)

### Langkah 2 — gratis, kurangi volume (sudah sebagian dikerjakan)
- Pindahkan `web/` ke **Cloudflare Pages** (static assets gratis & tidak hitung
  kuota Worker).
- Pindahkan routing host (apex→www, `api/wss/cpc`→health, `report/ask`→feedback)
  ke **Redirect Rules**, lalu hapus `/` + `/index.html` dari `run_worker_first`.
- Set route **fail-open** supaya saat kuota habis pengguna tetap dapat halaman.
- Cache `/api/stats` & `/api/releases` lebih agresif; turunkan polling.

Dengan ini 90k/hari bisa turun ke ~20–30k/hari — **masuk Free lagi** tanpa bayar.

### Langkah 3 — Firebase secara bertahap (kalau memang mau)
Jangan "menyeluruh" sekaligus. Pakai **hybrid**:

- **Firebase Auth + Crashlytics + Analytics** untuk fitur baru (gratis, tidak
  menyentuh jalur koneksi RDP).
- **Firestore/Realtime DB** untuk data baru yang butuh realtime (presence),
  bukan untuk memindahkan Durable Objects yang sudah jalan.
- Worker tetap sebagai edge API + proxy; Firebase tidak perlu menggantikan 7
  domain.

Ini memberi manfaat Firebase tanpa risiko memutus sesi RDP pengguna lama.

---

## 5. Keputusan

| Opsi | Biaya | Risiko | Waktu |
|---|---|---|---|
| Workers Paid | $5/bulan | Sangat rendah | 5 menit |
| Optimasi + Pages + fail-open | $0 | Rendah | 1–2 jam |
| Migrasi Firebase penuh | $1–3/bulan + kartu kredit | **Tinggi** (protokol, DO, 7 domain, APK) | Minggu |
| Hybrid Firebase (Auth/Analytics) | $0 | Rendah | 1–2 hari |

**Saran: ambil opsi 1 + 2 sekarang, opsi 4 menyusul kalau memang butuh fitur
Firebase.** Migrasi penuh baru masuk akal kalau ada alasan produk (mis. mau
Auth/Offline-first di seluruh aplikasi), bukan sekadar untuk menghindari kuota.

Kalau kamu tetap mau jalur Firebase penuh, langkah pertama yang aman adalah
bikin **project Firebase baru + Firestore emulator + kontrak API baru** di branch
terpisah, lalu jalankan kedua backend berdampingan (dual-write) sebelum mematikan
Worker. Jangan cut-over langsung.

---

## Sumber

- [1] Firebase pricing — https://firebase.google.com/pricing
- [5] Spark vs Blaze (2026) — https://dev.to/androve2k/spark-vs-blaze-the-firebase-pricing-guide-i-wish-id-read-sooner-onb
- [6] Firebase free quotas Oct 2026 — https://we.inc/guides/firebase-pricing
- [7] Firestore & Functions free tier — https://deploymanage.com/firebase-pricing
- [10] Cloud Storage wajib Blaze sejak 2026-02-03 — https://agentdeals.dev/vendor/firebase
- [12] Firestore quotas — https://firebase.google.com/docs/firestore/quotas
