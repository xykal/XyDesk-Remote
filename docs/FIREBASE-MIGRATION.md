# Migrasi Chat Global: Cloudflare → Firebase

Status: **berjalan**. Project `xyverse-technology-global` (XyVerse Technology
Global) aktif; aplikasi Android `id.xydesk.remote` sudah terdaftar
(appId `1:342677459040:android:10f93115af77e5b25cb305`) dan
`client/Android/Studio/app/google-services.json` resmi sudah tersimpan.
Backend repo-side siap: `firestore.rules`, `firebase.json`,
`firestore.indexes.json`, `functions/` (send callable + push FCM).
Rilis APK **ditahan** sampai seluruh fase selesai dan build verifikasi hijau.

Progress API (oleh Admin SDK, peran Editor sudah diberikan owner):
API Firestore/RTDB/IdentityToolkit/FirebaseRules/CloudFunctions aktif;
Firestore **ruleset sudah dibuat** (`44d63967-b0b5-4fdf-849f-ef22c01dd8d7`)
dan menunggu database untuk di-bind ke release `cloud.firestore`.

Owner tidak memiliki kartu kredit → arsitektur dialihkan ke **mode Spark
(gratis total)**, tanpa Cloud Functions:
- Klien menulis langsung ke Firestore; seluruh validasi pindah ke
  `firestore.rules` (wajib login, penulis==pemilik auth, skema field
  terkunci, edit/hapus lunak revision+1). Ruleset terdaftar:
  `9a0781a1-e064-41c5-a0c4-7fa8bcafd3ae` (menunggu DB untuk di-bind).
- Push tetap lewat relay worker Cloudflare → OneSignal (sudah jalan).
- Functions di `functions/` disimpan untuk fase pasca-Blaze; nanti jalur
  tulis langsung ditutup dan sendChatMessage menjadi satu-satunya jalur.

Database Firestore **sudah dibuat owner** (`asia-southeast2`/Jakarta) dan
ruleset Spark **sudah ter-bind** ke release `cloud.firestore`; uji tulis/
baca/hapus dokumen lolos. Endpoint `POST /api/firebase/token` (tukar
GitHub PAT → Firebase custom token, UID `gh-{id}`) sudah live di worker;
rahasia SA terpasang sebagai Worker secrets.

Auth sudah diaktifkan owner. Rantai identitas **terbukti ujung-ke-ujung**:
custom token → `signInWithCustomToken` 200 → tulis pesan Firestore dengan
token pengguna (lolos rules `authorUid == uid`) → baca balik → bersih.
Transport APK (`FirebaseChatTransport`, REST) selesai dan lolos gate
compile+unit test; aktif lewat flag `chat_backend_firebase` (toggle di
dialog bahasa chat), bawaan tetap Cloudflare. Provider GitHub di Firebase
tidak perlu — identitas tetap GitHub PAT yang diverifikasi worker.
Sisa: uji perangkat nyata mode Firebase, lalu cutover (matikan jalur
DO chat) dan FCM menggantikan relay OneSignal bila Blaze tersedia. Kuota Spark (20rb tulis/hari)
cukup untuk komunitas saat ini; pantau Usage, naik Blaze kapan pun
kartu/virtual debit tersedia (mis. e-Card bank digital).

## Kenapa pindah

Chat global Cloudflare hidup di **satu Durable Object tunggal**
(`GLOBAL_CHAT.idFromName("xydesk-global-chat-v1")`). Semua pesan seluruh dunia
antre diproses satu per satu di satu instance — ini plafon latensinya, dan
tidak hilang dengan optimasi kode. Firestore terdistribusi (tidak ada antre
tunggal global), listener snapshot menggantikan WebSocket + polling, dan
push pindah ke FCM (native Android, tanpa OneSignal).

## Pemetaan arsitektur

| Sekarang (Cloudflare) | Firebase |
| --- | --- |
| Durable Object `GlobalChat` + WS `/ws/chat` | Firestore collection `rooms/global/messages` + snapshot listener |
| `POST /api/chat/send|edit|delete|pin` | Tulis Firestore via Cloud Functions callable (validasi, moderasi, rate-limit) |
| DO storage `msg:*` | Dokumen Firestore (ciphertext E2EE apa adanya) |
| Presence WS broadcast | Realtime Database `presence/` (onDisconnect) |
| OneSignal push | FCM via Cloud Functions trigger `onCreate` |
| `GET /api/chat/translate` + clients5 | Tetap clients5 di APK; endpoint worker dipertahankan selama transisi |
| Login GitHub (PAT di CredentialVault) | Firebase Auth provider GitHub (token ditukar custom token bila perlu) |
| Leaderboard DO | Firestore `leaderboard/` + Functions aggregator |

E2EE tidak berubah: `XY-E2EE-GCM-v1` dienkripsi di perangkat; server hanya
menyimpan `e2ee_cipher/e2ee_iv/e2ee_sig`. Firestore tidak pernah melihat
plaintext dari APK versi baru.

## Skema Firestore (draf)

```
rooms/global/messages/{messageId}
  clientMsgId, authorId, name, githubLogin, country, flag
  text (fallback display), e2ee { protocol, cipher, iv, sig }
  replyToId, replyToName, replyToText, replyDepth
  attachment { type, name, mime, size, data?/url }
  revision, editedAt, deleted, deletedAt, at (serverTimestamp)

rooms/global/meta/pinned  { messageId, pinnedBy, at }
leaderboard/{authorKey}   { name, githubLogin, totalSeconds, updatedAt }
users/{uid}               { githubLogin, name, avatarUrl, country, lastSeen }
```

Security rules: baca publik hanya untuk user login; tulis **hanya** lewat
Functions (rules menolak tulis langsung klien ke `messages`), kecuali
`users/{uid}` milik sendiri.

## Yang harus dibuat owner (checklist)

1. console.firebase.google.com → **Add project** (nama bebas, mis. `xydesk-remote`).
2. **Build → Firestore Database** → Create (mode production, region `asia-southeast2` / Jakarta bila tersedia).
3. **Build → Realtime Database** → Create (untuk presence).
4. **Build → Authentication** → Get started → **GitHub** enable (pakai OAuth App GitHub yang sudah ada; salin callback URL Firebase ke GitHub OAuth App).
5. Upgrade ke **Blaze** (perlu kartu; biaya chat skala komunitas kecil — Firestore menagih per baca/tulis dokumen, pantau di Usage).
6. Daftarkan aplikasi Android: package `id.xydesk.remote`, SHA-1 rilis:
   `8429f689e7c3c6a0a44cb924206d56d5a2dccbd6c88cb900835c1901ad219652` (format SHA-1 untuk konsol diambil dari keystore rilis saat pendaftaran).
7. Unduh **`google-services.json`** dan kirim ke workspace ini.
8. (Untuk deploy Functions dari CI) buat Service Account `firebase-adminsdk`, unduh JSON key, simpan sebagai GitHub secret `FIREBASE_SERVICE_ACCOUNT` — jangan commit ke repo.

## Fase pengerjaan (urutan disepakati)

0. ✅ Rilis ditahan: auto-publish dimatikan, draft `v1.1.23` tetap draft.
1. Bahasa UI APK penuh (bertahap per tabel string) — *fondasi + tabel inti
   5 bahasa selesai, gate hijau; batch bahasa & string sebaris berlanjut*.
2. ✅ Threaded reply: `replyDepth` di model/parser/jalur kirim/worker +
   render font menyusut & indentasi per level (maks 4 visual).
3. Firebase (mode Spark): project + aplikasi + config + DB Jakarta +
   rules ter-bind + endpoint token live + rantai Auth terbukti +
   transport APK selesai (flag). Sisa: uji perangkat, cutover, matikan
   jalur DO chat. Functions menyusul pasca-Blaze.
4. Build verifikasi (gate + release) hijau.
5. Draft rilis diperbarui; publish **hanya** atas perintah owner.

## Risiko & catatan jujur

- Firestore membaca per-dokumen per-listener: ruang global yang ramai =
  biaya baca naik linear dengan jumlah pendengar online. Mitigasi: query
  `limit(80)` terbaru, pagination, dan agregasi presence terpisah di RTDB.
- Migrasi riwayat lama: ekspor dari DO storage terbatas API; opsi realistis
  mulai segar di Firebase dengan arsip read-only riwayat lama di web.
- Selama transisi APK mendukung dua transport; flag dimatikan setelah cutover
  stabil, baru kode WS lama dibuang.
