# Kenapa Cloudflare kamu kena limit terus?

**Error 1027 bukan bug kode — itu kuota harian.** Workers Free kasih **100.000 request/hari per AKUN** (bukan per Worker), reset jam **00:00 UTC = 07:00 WIB**. Begitu lewat, semua route yang "fail closed" menampilkan halaman 1027 sampai reset; route "fail open" malah melewati Worker diam-diam. Kuota ini dipakai bareng oleh semua Worker **dan** Pages Functions dalam satu akun — jadi Worker XyVoice/XyCloudStore yang lama ikut menyedot jatah yang sama. [1](https://developers.cloudflare.com/workers/platform/limits/) · [3](https://flarelog.dev/errors/1027) · [5](https://efreedirectory.com/cloudflare-free-plan-limits/)

Dengan 100k/hari, **≈16.000 kunjungan** yang masing-masing memicu 6 request sudah cukup buat habis. Itu tidak banyak kalau kamu punya 7 domain + bot crawler.

---

## Penyebab spesifik di repo kamu (urutan dampak terbesar)

### 1. `run_worker_first` memaksa halaman & aset statis lewat Worker

Di `wrangler.jsonc`, `assets.run_worker_first` berisi **33 path**, termasuk `/`, `/index.html`, dan semua alias unduhan. Padahal **permintaan static assets itu gratis & tidak dihitung kuota** — kecuali kamu memaksa Worker jalan dulu. [10](https://www.reddit.com/r/CloudFlare/comments/1xanbub/how_much_daily_traffic_can_the_cloudflare/)

```
"/api/*", "/", "/index.html", "/arm64-v8a.apk", ... "/onmic.ps1", ...
```

Artinya: **setiap page view = 1 Worker request untuk HTML-nya saja**, belum termasuk fetch di dalam halaman.

Kenapa `/` dan `/index.html` ada di sana? Karena `worker.mjs` baris ~3113–3150 melakukan routing berdasarkan host (apex→www 301, `api/wss/cpc`→health, `report/ask`→feedback). Selama routing itu ada di Worker, dua entry terbesar ini tidak bisa dihapus begitu saja.

### 2. Satu page view memicu 5–8 request

`web/index.html` berisi 5 `fetch()`: `/api/active-sessions`, `/api/stats` (2×), `/release-state.json` (2×), `/api/update-check`. `abi.html` nambah `/api/releases` + `/release-state.json`. Ditambah `/api/active-sessions` dipanggil tiap **60 detik per tab terbuka**.

Hitungan kasar: 1 tab terbuka 8 jam × (3600/60) = **480 request** hanya dari polling. 200 tab seperti itu = **96.000 request/hari** — kuota habis tanpa satu pun unduhan APK.

### 3. 7 custom domain, semuanya ke satu Worker

`xydeskremote.biz.id`, `www`, `api`, `wss`, `cpc`, `report`, `ask`. `robots.txt` isinya `Allow: /` — crawler bebas menjelajah semuanya, dan tiap host diproses Worker yang sama.

### 4. `cache-control: no-store` sebagai default

`HEADERS` di `worker.mjs:46` mengatur `no-store` untuk **semua** `jsonResponse`. Beberapa endpoint memang harus begitu (OAuth, sesi), tapi endpoint agregat seperti `/api/active-sessions` ikut kena — padahal isinya cuma angka global + timestamp, aman dibagikan lintas klien. Akibatnya tidak ada satupun yang bisa disajikan dari cache edge. (`/api/stats` dan `/api/releases` sudah benar: mereka pakai `caches.default` + `max-age=300`.)

### 5. Durable Objects ikut berbiaya

`ACTIVE_SESSIONS`, `COMMUNITY_JOKES`, `PC_SIGNAL_ROOMS`, `FEEDBACK_BOARD` — tiap heartbeat/polling yang diteruskan ke DO adalah request DO tersendiri (kuota sendiri, sama-sama 100k/hari di Free). [20](https://github.com/maksymenkoyr/sine-visuals-lab/issues/529)

### 6. Catatan samping: Vercel juga boros

`vercel.json` me-rewrite `/(.*)` → `/api/index.mjs`, jadi semua request (termasuk gambar & favicon) jadi invocation serverless. Ini bukan penyebab 1027, tapi pola yang sama dan bisa bikin tagihan Vercel membengkak.

---

## Yang sudah kuperbaiki (ada di patch)

| Perubahan | Dampak |
|---|---|
| `worker.mjs` — `/api/active-sessions` kini `public, max-age=15` + `caches.default` | Ratusan tab dalam jendela 15 detik dilayani **satu** jawaban; menghemat 1 Worker request **dan** 1 DO request per polling |
| `web/index.html` — interval 60 dtk → **120 dtk**, idle 5 mnt → **3 mnt**, jeda minimum **30 dtk**, tapi `visibilitychange` tetap force-fetch angka segar | Request polling per tab turun ~50%; gonta-ganti tab tidak bikin burst |
| `wrangler.jsonc` — hapus `/onmic.ps1`, `/XyDesk-RDP-OnMic-Setup.bat`, `/onmic-linux.sh` dari `run_worker_first` (ketiganya murni statis, worker cuma `ASSETS.fetch`) | Request ke skrip setup tidak lagi menyentuh Worker |
| `wrangler.jsonc` — komentar penjelas kuota di atas `run_worker_first` | Mencegah entry statis ditambahkan lagi tanpa sadar |
| 3 tes diperbarui + 1 tes baru (debounce) | **87/87 hijau** (sebelumnya 86) |

Semua perubahan diuji lokal: `node --check` semua `.mjs`, `node --test cloudflare/*.test.mjs` → **87 pass / 0 fail**, `wrangler.jsonc` tetap parse sebagai JSONC.

---

## Yang masih harus kamu lakukan (tidak bisa dari kode)

Ini urutannya, dari yang paling cepat:

1. **Set route ke "fail open".** Kalau kuota habis, pengunjung tetap dapat halaman statis, bukan halaman error 1027. Ini pengaturan per-route di dashboard Cloudflare, bukan di `wrangler.jsonc`. [1](https://developers.cloudflare.com/workers/platform/limits/)
2. **Naikkan ke Workers Paid ($5/bulan).** 10 juta request/bulan termasuk, tanpa batas harian, CPU 30 dtk (vs 10 ms di Free). Ini solusi paling pasti kalau traffic memang sudah di atas 100k/hari. [18](https://developers.cloudflare.com/workers/platform/pricing/)
3. **Pindahkan routing host keluar dari Worker** (perbaikan struktural terbesar):
   - apex→www 301, `api/wss/cpc`→health, `report/ask`→feedback pindah ke **Cloudflare Redirect Rules / Bulk Redirects** (gratis, tidak hitung kuota).
   - Setelah itu hapus `"/"` dan `"/index.html"` dari `run_worker_first`.
   - Hasilnya: HTML/CSS/JS/gambar disajikan gratis oleh Static Assets, dan Worker cuma jalan untuk `/api/*` + alias unduhan.
4. **Pindahkan `web/` ke Cloudflare Pages** dan sisakan Worker khusus API. Static assets di Pages unlimited & gratis; Pages Functions yang hitung kuota. [19](https://community.cloudflare.com/t/clarification-on-free-request-limit-for-workers-and-pages/746426)
5. **Audit domain.** Tujuh custom domain untuk satu Worker itu overkill. Konsolidasi jadi `www` (web) + `api` (API), sisanya jadi redirect 301 di Rules.
6. **Batasi crawler** kalau memang bukan konten yang ingin diindeks: `robots.txt` per-host, atau Cache Rule "Cache Everything" untuk halaman HTML supaya bot dilayani dari edge.
7. **Cek dashboard** Workers & Pages → lihat grafik request per jam. Kalau puncaknya selalu habis menjelang 07:00 WIB, itu konfirmasi pola harian; kalau naik tiba-tiba di jam tertentu, kemungkinan ada bot atau klien lama yang polling agresif.

---

## Perkiraan hematnya

Skenario konservatif: 1.000 tab aktif/hari, rata-rata 10 menit terbuka.

| | Sebelum | Sesudah patch | + Redirect Rules/Pages |
|---|---|---|---|
| Request HTML | 1.000 | 1.000 | **0** |
| Request `/api/active-sessions` | ~10.000 | ~2.500 (cache 15 dtk + 120 dtk) | ~2.500 |
| Fetch lain per page | ~5.000 | ~5.000 | ~5.000 |
| **Total** | **~16.000** | **~8.500** | **~7.500** |

Angkanya cuma ilustrasi, tapi arahnya jelas: **~50% langsung hilang** dari patch ini, dan penghapusan `run_worker_first` untuk HTML memberi ruang paling besar kalau traffic naik.
