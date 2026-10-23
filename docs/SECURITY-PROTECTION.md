# Lapisan Proteksi Anti-Serangan (DDoS/Flood/Scanner)

Tujuan: menekan pemakaian Cloudflare yang tidak wajar tanpa mengganggu
pengguna sah. Disusun berlapis dari yang paling luar (edge) ke paling
dalam (Durable Object).

## Sudah aktif

1. **Worker — limiter per IP** (`apiAbuseLimit` di `cloudflare/worker.mjs`):
   jendela 1 menit per IP — `/api/firebase/token` 15, `/api/push-update` 5,
   `/api/chat/translate` 60, `/api/og` 60, `/api/chat/send` 300,
   `/ws/chat` 120, kelompok `/api/` lain 3000. Ditolak = HTTP 429 +
   `Retry-After`. Ini lapisan termurah: request flood mati di isolate.
2. **Cache identitas GitHub 5 menit** di endpoint token Firebase — flood
   tidak teramplifikasi ke `api.github.com`.
3. **Rate-limit di Durable Object**: jendela kirim per penulis
   (`CHAT_MSG_PER_WINDOW`) tetap berlaku setelah lolos worker.
4. **Zona**: `always_use_https` ON, Browser Integrity Check ON,
   Security Level medium, DDoS L3/4 bawaan Cloudflare (semua plan).
5. **SSRF**: `/api/og` memblokir host privat/lokal & tautan berbahaya
   (XyShield). **E2EE** pesan tidak pernah dibuka server.

## Perlu ditempel owner di dashboard (token API saat ini tidak berizin Rulesets)

Cloudflare Dashboard → zona `xydeskremote.biz.id`:

1. **Security → WAF → Rate limiting rules → Create**:
   - Field: `URI Path` — Operator: `starts with` — Value: `/api/`
   - Characteristics: IP · Period: 10 detik · Requests: 200 · Action: Block (10 detik)
2. **Security → WAF → Custom rules → Create** (tiga aturan, Action: Block):
   - `URI Path contains /.env` OR `contains /wp-admin` OR `contains /.git/`
     OR `contains /phpmyadmin` OR `contains /xmlrpc.php`
   - `starts with URI Path /api/` AND `User Agent equals ""` (kosong)
   - `Host equals api.xydeskremote.biz.id` AND `URI Path contains .php`
3. **Security → Bots → Configure Bot Fight Mode: ON** (plan Free).

## Playbook darurat

- Serangan sedang berlangsung & usage melonjak: Dashboard → zona →
  **Under Attack Mode ON** (sementara; matikan setelah reda — mode ini
  menampilkan interstitial ke browser, API akan ikut tertahan).
- IP/AS spesifik menyumbang traffic: WAF Custom rule `IP Source Address
  is in` → Block.
- Bocor di endpoint baru: turunkan tier-nya di `API_ABUSE_TIERS` lalu
  deploy worker (`Deploy Cloudflare Worker` workflow, input `DEPLOY`).

## Catatan

Request yang ditolak ruleset edge tidak menjalankan worker sama sekali;
limiter worker menangkap yang lolos. Kombinasi keduanya membuat biaya
flood per IP terpotong dari ribuan req/detik menjadi ratusan per menit.
