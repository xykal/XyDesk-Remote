# Cloud RDP — Panduan Setup & Arsitektur (multi-tenant)

Dua cara pakai XyDesk Remote:

| Mode | Kapan dipakai | Yang dibutuhkan |
|---|---|---|
| **Manual RDP** | Sudah punya RDP/Windows sendiri (rumah, kantor, VPS, dsb) | Cukup **IP/host + port + username + password** — connect ke RDP mana aja |
| **Create RDP (Cloud RDP)** | Belum punya mesin, mau bikin dari HP | Akun **GitHub milik user sendiri** + 1 host Windows (lihat prasyarat) |

## Arsitektur Cloud RDP — self-serve per user

```
HP user (XyDesk Remote)                    Akun GitHub USER (milik user sendiri)
┌────────────────────┐  device flow        ┌──────────────────────────┐
│ CloudRdpActivity   │ ────── login ─────► │ OAuth App XyDesk Remote  │
│  isi nama repo     │  (izin ke akun      │ (app publik, device flow)│
│  + user RDP        │   user sendiri)     └──────────────────────────┘
│  create repo       │
│  push template     │  contents API       Repo '<nama>' (PUBLIC, di akun user)
│  poll + download   │ ◄─────────────────  .github/workflows/rdp-vm.yml
└────────┬───────────┘   artifact ter-     setup/setup-windows.ps1
         │                 enskripsi       setup/pubkey.pem + config.json
         ▼                                   │ workflow jalan di akun user
   RDP via Tailscale ◄─────────────────────  ▼
   (host: xxx.ts.net:3389,                   Host Windows milik user
    user + pass dari app)                    (self-hosted runner 'xydesk-win')
```

Poin penting:

- **Workflow & Actions jalan di akun GitHub masing-masing user** — bukan di akun
  developer. App cuma menyediakan kode workflow lengkap (bundel di APK) dan
  mengorkestrasi lewat API atas izin user (device flow OAuth).
- **Repo dibuat otomatis di akun user** (PUBLIC — aturan proyek: tanpa repo
  private) begitu user menekan
  *Create & Setup*. Kalau repo sudah ada, dipakai yang existing.
- **Host RDP = mesin milik user** yang didaftarkan sebagai **self-hosted
  runner** dengan label `xydesk-win`. JANGAN pakai GitHub-hosted runner
  (`windows-latest`) sebagai mesin RDP — itu melanggar ketentuan pemakaian
  Actions, dan akun GitHub user (atau punya lo) bakal kena suspend. Untuk user
  tanpa PC Windows: pakai VPS Windows murah, atau mode Manual RDP.
- Kredensial **end-to-end ter-enskripsi** (RSA-OAEP, kunci sekali-pakai per
  setup): password diacak di host, di-enskripsi dengan `pubkey.pem`, hanya app
  di HP user yang bisa dekripsi. Aman walau repo dibuat public.

## Prasyarat

| Item | Keterangan |
|---|---|
| Host Windows | Windows 10/11 **Pro/Enterprise/Edu** atau Windows Server. **Windows Home TIDAK bisa** jadi target RDP |
| PowerShell 7 (`pwsh`) | Terpasang di host (dipakai workflow + enkripsi kredensial) |
| Tailscale | Terpasang di host **dan** di HP user, login ke tailnet yang sama |
| Akun GitHub user | User login sendiri via device flow (scope `repo`) — token bisa direvoke kapan aja di Settings → Applications |
| OAuth App | **Dibuat sekali oleh developer** (bukan per user): Settings → Developer settings → OAuth Apps → New OAuth App → nama `XyDesk Remote`, homepage `https://github.com/xykal/XyDesk-Remote`, callback `https://localhost`, **AKTIFKAN Device Flow** → salin Client ID ke `GitHubDeviceAuth.CLIENT_ID` lalu build ulang APK. Client ID bersifat publik; client secret TIDAK dipakai |

## Langkah setup host (sekali per mesin, oleh user)

1. Install **Tailscale** di host, login ke tailnet user. Buat **pre-auth key**
   (multi-use) di admin console Tailscale.
2. Di **akun GitHub user** → Settings → Actions → Runners → New self-hosted
   runner (Windows) → `config.cmd` dengan label **`xydesk-win`** → `run.cmd`
   (atau pasang sebagai service).
3. Set env runner **sebelum** `run.cmd`:
   ```powershell
   $env:XYDESK_TAILSCALE_AUTH_KEY = "tskey-auth-..."
   ```
4. Pastikan `pwsh` ada di PATH host (minimal 7.x).

## Langkah Create RDP dari app

1. App → **Cloud RDP** → **Login GitHub** (authorize di browser, akun user).
2. Isi form:
   - **nama repo** (mis. `desk-gaming-01`) — jadi repo PUBLIC di akun user
   - **nama user RDP** (opsional, default `xydesk`)
   - **password RDP** (opsional) — kalau diisi wajib **kuat**: min 12
     karakter, campur huruf besar/kecil + angka + simbol (`!@#$%^&*._-`),
     tanpa spasi/kutip, jangan mengandung nama user. Kosongkan = auto-generate
     kuat tiap setup
   - **Tailscale auth key** (opsional) — pre-auth key dari
     `tailscale.com/admin/keys`. Kosongkan = pakai env runner
     `XYDESK_TAILSCALE_AUTH_KEY`
3. **Create & Setup** — app otomatis: create repo PUBLIC di akun user →
   generate kunci sekali-pakai → push template + pubkey → trigger
   `workflow_dispatch` fase `prepare` → app enkripsi rahasia ke kunci host
   (`secrets.enc`) → dispatch fase `setup` → tunggu workflow → download kredensial ter-enskripsi → dekripsi
   di app → cek `host:3389` → **connect RDP otomatis** lewat Tailscale.

## Keamanan

- Password RDP: pilihan user (wajib kuat) atau auto-generate 18 karakter;
  **di-reset tiap setup** (re-run aman).
- **Rahasia tidak pernah lewat input workflow** (di repo public input workflow
  kebaca publik). Alur dua fase: fase `prepare` host generate kunci RSA (sekali
  per mesin) + upload public key; app enkripsi `{user,pass,tskey}` (RSA-OAEP)
  ke kunci host itu jadi `setup/secrets.enc`; fase `setup` host dekripsi,
  setup, lalu kredensial di-enskripsi balik ke `setup/pubkey.pem` (kunci
  sekali-pakai app) jadi `rdp-credentials.enc`. Input workflow cuma `phase`.
- Kunci host persist di `C:\ProgramData\xydesk\hostkey.pem` di VM; kalau VM
  di-reset, hapus folder itu lalu ulangi Create & Setup dari app.
- Kredensial balik ke app **E2E ter-enskripsi** (RSA-OAEP, kunci sekali-pakai)
  — plaintext tidak pernah menyentuh disk host maupun artifact.
- Password & Tailscale key dikirim sebagai **ciphertext** (`secrets.enc`) ke
  repo user — aman walau repo public, hanya host dengan kunci privat-nya yang
  bisa dekripsi. Alternatif paling privat untuk Tailscale key: set
  `XYDESK_TAILSCALE_AUTH_KEY` langsung di env runner, kosongkan di app.
- RDP **tidak diekspos ke internet** — semua lewat tailnet.
- Token device flow scope `repo` (perlu untuk create repo + Actions di akun
  user); bisa direvoke kapan aja dari Settings → Applications.

## Troubleshooting

| Gejala | Penyebab / solusi |
|---|---|
| Workflow tidak mulai | Runner `xydesk-win` user belum nyala / label salah. Cek Settings → Actions → Runners di akun user |
| Workflow gagal | Lihat Actions tab repo user — kemungkinan `XYDESK_TAILSCALE_AUTH_KEY` belum diset di runner, `pwsh` tidak ada, atau nama user RDP kena reserved Windows |
| Artifact gagal dekripsi | Setup harus dari app (yang pegang private key). Jangan jalankan skrip manual tanpa `setup/pubkey.pem` dari app |
| `host:3389` tidak terjangkau | Tailscale di HP belum login / beda tailnet |
| Windows Home | Tidak bisa jadi RDP host — pakai Pro/Enterprise/Edu/Server |
| User tidak punya mesin Windows | Pakai **mode Manual RDP** ke VPS/RDP mana aja, atau sewa VPS Windows buat jadi host |
