# OAuth App GitHub (Device Flow) — Setup Sekali Saja

Fitur **Cloud RDP** butuh 1 OAuth App GitHub milik developer (bukan per user).
OAuth App **tidak bisa dibuat via API** (`POST /applications` sudah ditutup
GitHub) — wajib dibuat manual di UI, ±3 menit. Setelah sekali dibuat, user
cukup login akun GitHub masing-masing dari app (device flow, tanpa client
secret).

## Langkah

1. **Buka halaman pembuat OAuth App**
   - GitHub → avatar kanan-atas → **Settings** → menu kiri **Developer settings**
     (atau langsung: <https://github.com/settings/developers>)
   - Pilih tab **OAuth Apps** → tombol **New OAuth App**

2. **Isi form:**

   | Field | Isi |
   |---|---|
   | Application name | `XyDesk Remote` |
   | Application description | (opsional) `XyDesk Remote — RDP client, login via device flow` |
   | Homepage URL | `https://github.com/xykal/XyDesk-Remote` |
   | Authorization callback URL | `https://localhost` (field wajib; device flow TIDAK memakai callback) |

3. **Save** → GitHub menampilkan halaman app baru dengan:
   - **Client ID** ← **INIPULANGNYA** (format angka panjang / `Iv1....`)
   - **Client Secret** → **ABAIKAN** — device flow tidak memakai secret.
     Jangan pernah commit/lempar secret ke mana pun.

4. **AKTIFKAN DEVICE FLOW** (WAJIB — tanpa ini app akan ditolak GitHub):
   - Di halaman app yang sama, cari bagian **Device Flow**
   - Klik **Enable Device Flow** (muncul konfirmasi; kalau diminta verifikasi
     email GitHub, lakukan)
   - Setelah aktif, statusnya berubah jadi "enabled" dan ada catatan bahwa
     request device code tidak perlu client secret.

5. **Tanam Client ID di kode:**
   - File: `client/Android/Studio/app/src/main/java/id/xydesk/remote/cloud/GitHubDeviceAuth.java`
   - Ganti:
     ```java
     public static final String CLIENT_ID = "GANTI_DENGAN_CLIENT_ID";
     ```
     menjadi:
     ```java
     public static final String CLIENT_ID = "<Client ID dari langkah 3>";
     ```
   - Build ulang (Client ID bersifat **publik** — aman di APK dan di repo).

6. **Uji (cek cepat tanpa build):**
   ```bash
   curl -s https://github.com/login/device/code \
     -d client_id=<CLIENT_ID> -d scope=repo
   ```
   - Success → JSON berisi `device_code`, `user_code`, `verification_uri`
     (`https://github.com/login/device`).
   - Error `bad_client` / `unknown_client_id` → Client ID salah / Device Flow
     belum aktif.

## Kenapa device flow (bukan authorization code)?

- App berjalan di **HP user** — tidak ada tempat aman menyimpan client secret.
  Device flow = **tanpa secret sama sekali**.
- User authorize di browser HP (buka `verification_uri`, ketik `user_code`);
  token scope `repo` langsung ke app.
- User bisa **revoke kapan saja**: Settings → Applications → XyDesk Remote →
  Revoke.

## Pemeliharaan

| Kapan | Aksi |
|---|---|
| Ganti nama/branding app | Update Homepage URL di OAuth App |
| Client ID bocor (mustahil benar-benar rahasia) | Buat OAuth App baru, ganti `CLIENT_ID`, build ulang |
| Device Flow di-disable (mis. cleanup akun) | Enable lagi di halaman OAuth App — **fitur mati** |
