# Audit XyDesk Remote — Perombakan RDP-only (2026 Q4)

Tanggal: 2026-11-01
Keputusan pemilik: XyDesk-Remote hanya untuk koneksi **RDP (FreeRDP)**. Remote PC
berbasis WebRTC/P2P pindah ke repo XyDesk. Akun GitHub + CloudSync + AdMob tetap;
chat/komunitas/feedback/notifikasi dicabut. Versi **2.0.0 (94)**.

## 1. Temuan: kenapa WebRTC PC Stream disingkirkan

- Kontras lapangan 2026-10-31 dengan build yang sama: **ThinkMay lolos ICE, Limore
  Cloud (limorecloud.com) gagal total**. Analisis jaringan menunjukkan VM cloud
  gaming tersebut **memblokir UDP keluar** — kandidat host/srflx mati, jalur
  relay (TURN) mahal dan tidak stabil untuk sesi panjang.
- Akar masalahnya ada di infrastruktur provider VM (bukan di aplikasi), sehingga
  fitur "PC Stream" P2P tidak bisa diandalkan sebagai produk. Keputusan: cabut
  dari sini; remote PC dikerjakan ulang terpisah di repo XyDesk.
- Jalur RDP (TCP murni lewat FreeRDP) **tidak terpengaruh** blok UDP — terbukti
  tetap jalan ke target yang sama.

## 2. Perubahan produk di rilis ini

- Dicabut: modul `core-pc-stream` (WebRTC/XyRDP stream), SSH Terminal, chat global,
  komunitas/jokes, papan feedback, semua notifikasi (FCM/OneSignal), pelaporan
  sesi anonim, tab Berita.
- Dipertahankan: FreeRDP + gamepad, sesi & keamanan, widget, login GitHub + CloudSync,
  AdMob, terjemahan UI, host-agent, update-check, unduhan & statistik rilis.
- Worker `xydesk-remote-web`: endpoint jokes/feedback/chat/firebase-token/push-relay/
  active-sessions + ketiga Durable Object dicabut; jalur lama kini **410 permanen**.

## 3. Empat item HANDOFF yang terbuka (serah terima ke repo XyDesk)

1. **Bukti lapangan Mode Tanpa P2P** — uji HP ke host cloud berfirewall UDP ketat
   (kontras ThinkMay vs Limore Cloud sudah tercatat; lihat butir 1 di atas). Untuk
   host yang UDP-nya diblokir total, daftar TURN perlu varian transport TCP/TLS
   (`turn:...?transport=tcp` / `turns:`) di `TURN_DIRECT_URLS`/`TURN_STATIC_URLS`.
   Kasus re-offer saat host masih `Connected` bisa berakhir `host-sibuk` (host
   hanya mengambil alih sesi `Disconnected/Failed/Closed`, `main.rs:899`) —
   butuh jalur cadangan bila terbukti di lapangan.
2. **Uji Windows asli panel desktop** — TURN relay, tray, capture RDP/GDI di
   Windows sungguhan (Wine/CI bukan bukti lapangan) + **screenshot host Windows
   untuk berita** (ID+QR dan panel saat sesi).
3. **Bukti allocate TURN + jejak rotasi kredensial** — jalankan ulang
   `tool/check_turn_live.js` + `tool/check_turn_auth.py --role host`; rotasi
   `TURN_DIRECT_*` pasca-kebocoran riwayat git belum punya jejak bukti. Isi
   cadangan provider (Open Relay/REST/Cloudflare Realtime) hanya saat kunci
   baru tersedia.
4. **Kuota Workers Cloudflare** — rata-rata ±125 ribu request/hari vs batas
   100 ribu/hari (Error 1027 pernah menimpa seluruh akun). Mitigasi sudah jalan
   (2026-10-31): polling data 1 detik dihapus di PR #24, rate limit edge per IP,
   dan cutover chat ke Firebase (2026-10-24). Dengan endpoint komunitas dicabut
   di rilis 2.0.0 ini, beban worker turun drastis — pantau grafik 7 hari ke
   depan sebelum menutup item ini.

## 4. Jejak keputusan

- Diskusi & keputusan scope: sesi kerja 2026-11-01 (pemilik: "bersihkan biangnya,
  dan hapus semua koneksi kecuali koneksi rdp").
- Kode: branch `feat/rdp-only`, guard `ci/test_recovery_baseline.py` +
  `docs/recovery/PROTECTED-CONNECTION-PATHS.json` menjaga jalur koneksi RDP.
