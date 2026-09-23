# XyDesk Remote

App Android remote desktop untuk **Windows (RDP)** dengan:
- Form koneksi ala MS Remote Desktop (IP + port + user + password)
- **Cloud RDP**: create RDP dari GitHub langsung di app (login GitHub → isi nama → repo + setup otomatis → connect otomatis via **Tailscale**)
- (M2+) HUD multi-panel yang bisa dikustomisasi

> Repo ini **public** untuk kontribusi, sponsor, collab, dan diskusi (issues/PR).
> Lisensi tree FreeRDP: Apache-2.0. Kode XyDesk: © XyVerse (lihat `XYDESK-REMOTE-NOTICE.md`).

## Struktur repo

Repo mempertahankan **layout tree FreeRDP** di root (kebutuhan path relatif CMake superbuild). Perubahan XyDesk terbatas:

```
<root>                        = tree FreeRDP 3.32.0 (vendored, pin via git)
├── .github/workflows/        ← CI/CD: debug per-ABI, release (R8+signed), keystore
├── client/Android/Studio/
│   ├── build.gradle          ← diedit minimal
│   ├── settings.gradle       ← ':freeRDPCore' + ':app'
│   ├── release.properties    ← knob build (ABI, minify, versi)
│   ├── freeRDPCore/          ← inti FreeRDP Android (native + JNI), tanpa perubahan
│   └── app/                  ← APP XYDESK: form koneksi + Cloud RDP (GitHub)
├── docs/BUILD.md             ← panduan build lokal & CI
├── PLAN.md                   ← arsitektur + roadmap (M0–M6)
├── XYDESK-REMOTE-NOTICE.md   ← atribusi & daftar perubahan terhadap FreeRDP
└── FreeRDP-README.md         ← README asli FreeRDP (arsip)
```

## Build

### CI (GitHub Actions) — otomatis
| Pemicu | Yang terjadi |
|---|---|
| Push ke `main` | Build **DEBUG per-ABI** (armeabi-v7a, arm64-v8a, x86_64) → artifact `xydesk-remote-debug-per-abi` |
| Tag `v*` (misal `v0.2.0`) | Build **RELEASE per-ABI** (R8 + signing resmi) → **GitHub Release** dengan 3 APK |
| Manual (Actions tab) | `release` (tanpa tag) atau `generate-keystore` (sekali saja) |

Durasi: ±25–40 menit/run (superbuild OpenSSL/FFmpeg/OpenH264/Opus + FreeRDP core per ABI).

### Lokal
Lihat `docs/BUILD.md`. Ringkas: Android Studio → import `client/Android/Studio` → NDK `29.0.13113456` + CMake `4.1.2` + platform `android-37.2`.

### Sign release (sekali saja)
1. Tab **Actions** → *Build APK* → Run workflow → input **generate-keystore**
2. Unduh artifact `xydesk-release-keystore`
3. Set secret repo (Settings → Secrets and variables → Actions):
   - `RELEASE_KEY_BASE64` = isi `release.jks` di-base64
   - `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_STORE_PASSWORD` = isi `keystore-creds.env`
4. **Simpan `release.jks` di tempat aman** (bukan di repo!). Ini identitas signing app — kalau bocor, update app di Play/dimana pun akan ditolak.

## Cloud RDP (GitHub + Tailscale)

Di app: **Create Cloud RDP (GitHub + Tailscale)** → login GitHub (device flow, tanpa secret) → isi nama → **Create & Setup**. Yang terjadi otomatis:
1. Repo GitHub private bernama sesuai input dibuat
2. Template workflow + skrip setup di-push ke repo
3. Workflow menjalankan setup di self-hosted Windows runner (label `xydesk-win`): RDP on, join Tailscale, user+password acak
4. App menunggu run selesai → download artifact `rdp-credentials` → cek koneksi → **connect otomatis**

**Prasyarat sekali-setup:**
- OAuth App GitHub (nama `XyDesk Remote`, **Device Flow** di-enable) → salin **client_id** ke `client/Android/Studio/app/src/main/java/id/xydesk/remote/cloud/GitHubDeviceAuth.java` (`CLIENT_ID`). Client secret tidak perlu.
- VM Windows self-hosted runner dengan label **`xydesk-win`**, env **`XYDESK_TAILSCALE_AUTH_KEY`** (pre-auth key Tailscale), Tailscale terpasang
- Tailscale di HP login ke tailnet yang sama

## Menghubungi / Kontribusi
- Issues & PR: terbuka (bug, fitur, docs)
- Roadmap & arsitektur: `PLAN.md`
- Sponsor/collab: hubungi via issues
