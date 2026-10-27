# RDP Gratis: fork atau "Use this template"?

## Jawaban singkat

**Metode yang dipakai sekarang adalah FORK**, bukan template.

Buktinya di `XyRdpClient.kt:354`:

```kotlin
val code = call(token, "/repos/${XyRdpConfig.TEMPLATE_REPO}/forks", "POST", "{}").first
```

`XyRdpConfig.TEMPLATE_REPO = "xykal/XyRDP"` itu cuma **nama variabel** untuk repo
sumber. Tidak ada satu pun pemanggilan `POST /repos/{owner}/{repo}/generate`
sebelum patch ini. Ada juga jalur alternatif `dashCall("/setup")` untuk token
berbasis cookie dashboard, tapi tetap berujung ke repo hasil fork.

## Kenapa alurnya terasa "sulit banget"

Karena **fork membawa masalah bawaan GitHub**: workflow di fork republik publik
**dimatikan secara default** demi keamanan [9](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows). Akibatnya aplikasi harus melakukan ini semua:

1. `createFork()` — `POST /repos/xykal/XyRDP/forks`
2. `forkExists()` — 3 strategi fallback (cek repo, search API, daftar forks upstream)
3. `markForkConfirmed()` + `SharedPreferences` `fork_confirmed_<login>`
4. `syncForkWithUpstream()` — `POST /merge-upstream`, fallback force-update ref ke SHA upstream
5. `enableWorkflow()` — PUT `actions/permissions`, PUT `actions/permissions/workflow`,
   GET daftar workflow, lalu PUT `.../enable`
6. **Pengguna disuruh buka GitHub sendiri** untuk mengisi Secrets
   `RDP_PASSWORD` dan `TAILSCALE_AUTH_KEY` / `PORTMAP_OVPN_CONFIG`
   (lihat `inspectSetup()` dan kartu "Lengkapi GitHub Secrets")
7. Baru `dispatch()` → `pollValidatedSession()` → connect

Tujuh langkah, sebagian besar tidak terlihat pengguna, dan tiap langkah bisa
gagal sendiri-sendiri. Ditambah 11× `!!` yang bikin crash kalau identitas GitHub
belum selesai diverifikasi.

## Rekomendasi: pindah ke "Use this template"

Endpoint resminya `POST /repos/{template_owner}/{template_repo}/generate`
[1](https://docs.github.com/en/rest/repos/repos). Repo hasil generate **bukan fork**:

- GitHub Actions **aktif sejak awal** (tidak ada banner "Workflows aren't being run on this fork") [1](https://github.com/orgs/community/discussions/25659) · [2](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository)
- Tidak ada `merge-upstream` — riwayatnya memang sengaja terpisah dari template [14](https://github.com/github/docs/blob/main/content/repositories/creating-and-managing-repositories/creating-a-template-repository.md)
- Tidak perlu `forkExists()` / `markForkConfirmed()` berlapis
- `enableWorkflow()` jadi no-op (workflow langsung berstatus `active`)

Hasilnya: **7 langkah → 3 langkah** (login → satu tombol "Siapkan" → isi Secrets → mulai).
Jumlah panggilan API dan titik gagal turun drastis.

### Syarat (harus kamu lakukan di GitHub)

1. Buka `https://github.com/xykal/XyRDP` → **Settings** → centang
   **Template repository**. Tanpa ini endpoint `/generate` mengembalikan 422.
2. Token pengguna butuh permission **Administration: write + Contents: read**
   (atau **Contents: read + Repository creation: write**) untuk fine-grained PAT
   [1](https://docs.github.com/en/rest/repos/repos) · [4](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens).
   Periksa juga scope OAuth yang diminta aplikasi — `githubOAuthConfig()` saat
   ini meminta `repo workflow read:user user:email`.

### Trade-off yang jujur

- **Tidak ada sinkronisasi upstream otomatis.** Kalau `xykal/XyRDP` diperbarui,
  repo hasil template tidak ikut berubah. Solusinya: (a) workflow "Update from
  upstream" yang dijalankan manual, atau (b) tombol "Perbarui" di aplikasi yang
  mengambil file workflow terbaru lewat API `contents`. Ini tetap lebih murah
  daripada `syncForkWithUpstream()` yang sekarang melakukan **force update ref**.
- Pengguna lama yang sudah punya fork tetap didukung — `ensurePersonalRepo()`
  fallback otomatis ke `createFork()` kalau `/generate` gagal, dan
  `isForkRepo()` memutuskan apakah perlu sync upstream.

---

## Yang sudah kuterapkan di patch

| File | Perubahan |
|---|---|
| `XyRdpConfig.kt` | `PREFER_TEMPLATE = true` + dokumentasi |
| `XyRdpClient.kt` | `createFromTemplate()` (`POST …/generate`), `ensurePersonalRepo()` (template → fallback fork), `isForkRepo()` |
| `RdpFreeScreen.kt` | Tombol "Siapkan XyRDP" kini memanggil **satu** fungsi; mode disimpan di `xyrdp_repo_mode`; `syncForkWithUpstream()` hanya dipanggil kalau repo benar-benar fork; teks tidak lagi menyebut "fork" |
| `RdpFreeScreen.kt` | `auth()` menggantikan semua `!!` — tidak ada lagi NPE saat OAuth lambat/logout |
| `AddDeviceScreen.kt` | Kartu **"3 langkah langsung konek"** di paling atas + tombol "Pindai PC-ku otomatis"; hint host lebih manusiawi; tombol SSH/VPS disembunyikan di mode cepat (masih ada di tab Lanjutan) |

Port 3389 memang sudah otomatis terisi (`portText` default 3389) — sekarang
diberitahukan eksplisit supaya pengguna tidak bingung mencari kolom port.

---

## Yang masih manual (dan kenapa)

**GitHub Secrets masih harus diisi pengguna.** Untuk mengisinya lewat API,
nilainya harus dienkripsi dengan *libsodium sealed box* memakai public key repo
(`GET /repos/{owner}/{repo}/actions/secrets/public-key`). Itu butuh dependensi
native baru di APK — berat dan berisiko untuk ukuran aplikasi yang sudah ~90 MB.

Jalan tengah yang **sudah kuterapkan** di tab Secrets (`RdpFreeScreen.kt`):

- Kartu **"Kunci akses — 2 langkah"** di paling atas: daftar `RDP_PASSWORD` dan
  `TAILSCALE_AUTH_KEY` / `PORTMAP_OVPN_CONFIG` dengan status *sudah terpasang* /
  *belum* yang dibaca dari `inspectSetup()`.
- Satu tombol utama **"Salin & pasang {NAMA} ↗"** yang otomatis menyalin nilai
  Secret yang *belum* terpasang lalu membuka
  `https://github.com/{login}/XyRDP/settings/secrets/actions/new`.
- Tombol **"Cek status Secret"** memanggil `refreshAll()` tanpa keluar dari tab.
- Draft Tailscale/OVPN kini disimpan **terenkripsi di `CredentialVault`**
  (debounce 400 ms) — sebelumnya hilang tiap kali layar diputar.
- **Kolom password RDP tidak lagi tampil polos**: `isPassword = true`, dan
  `XyField` sudah menyediakan toggle lihat/sembunyikan.

Jadi alurnya sekarang: **Masuk GitHub → Siapkan (1 klik) → Kunci akses (2 klik,
nilai sudah tersalin) → Mulai PC**. Tidak ada nilai kunci yang dikirim ke server
XyDesk/Cloudflare — semuanya tetap di HP (vault) dan GitHub Secrets milik
pengguna.

Kalau nanti mau benar-benar satu klik, baru pertimbangkan libsodium — tapi itu
keputusan terpisah (ukuran APK, audit dependensi native).

---

## Catatan keamanan

Jangan memindahkan kredensial ke `workflow_dispatch` inputs untuk "menghemat
langkah": input workflow **terlihat di log run** dan bisa dibaca siapa pun yang
punya akses repo. Secrets tetap cara yang benar. `SECURITY.md` juga mengingatkan
untuk menghapus token dari log sebelum dibagikan.
