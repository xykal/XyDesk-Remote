# XyDesk RDP Gateway (Rust)

Backend keamanan untuk **RDP dari browser** — dua tugas:

1. **`POST /api/rdp/token`** — menerbitkan token sesi terenkripsi (**AES-256-CBC + HMAC-SHA256**, umur ±120 detik) dari kredensial RDP yang dikirim browser. Kredensial **tidak pernah disimpan** di server.
2. **`GET /websocket-tunnel?token=…`** — memverifikasi token (tolak yang dimodifikasi/kedaluwarsa), membuka TCP ke **guacd**, jabat tangan protokol RDP (`select` → `args` → `size` → `audio` → `video` → `connect`), lalu mem-pipe WebSocket ↔ guacd dua arah.

Ditulis dalam **Rust** (axum + tokio) — jalur kriptografi dan protokol diuji unit (`cargo test`: 8/8).

## Deploy (butuh VPS — keputusan pemilik)

```bash
# 1. guacd (Apache Guacamole server daemon)
sudo apt-get install -y guacamole   # atau build dari guacamole-server

# 2. build & jalankan gateway
cargo build --release
GATEWAY_SECRET="ganti-dengan-acak-panjang" \
GUACD_ADDR="127.0.0.1:4822" \
BIND="0.0.0.0:8787" \
ALLOW_ORIGIN="https://www.xydeskremote.biz.id" \
./target/release/xydesk-rdp-gateway

# 3. reverse proxy TLS (Caddy contoh)
# rdp-gateway.xydeskremote.biz.id { reverse_proxy 127.0.0.1:8787 }
```

Env:
| Var | Default | Keterangan |
|---|---|---|
| `GATEWAY_SECRET` | — (wajib) | min 16 karakter; sumber kunci enkripsi + HMAC token |
| `GUACD_ADDR` | `127.0.0.1:4822` | alamat guacd |
| `BIND` | `0.0.0.0:8787` | alamat dengar |
| `ALLOW_ORIGIN` | `https://www.xydeskremote.biz.id` | CORS asal web resmi |

## Keamanan
- Token: HMAC diverifikasi **sebelum** dekripsi (constant-time), terikat waktu (TTL 120 dtk), payload terenkripsi AES-256-CBC.
- Kata sandi RDP pengguna hanya hidup di dalam token berumur pendek → guacd; tidak ditulis ke log.
- Wajib di belakang HTTPS/WSS (TLS di reverse proxy).

## Uji
```bash
cargo test   # 8/8: roundtrip token, tamper ditolak, secret beda ditolak,
             # kedaluwarsa ditolak, token sampah ditolak, format instruksi
             # guacd, sekuens handshake, panjang argumen benar.
```
