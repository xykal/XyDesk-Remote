# Bukti Integritas & Pemindaian Keamanan Rilis

Tujuan: memberi cara verifikasi yang bisa diulang siapa pun (termasuk reviewer
store) tanpa harus mempercayai klaim kami. Tidak ada klaim "100% aman" di sini —
hanya prosedur dan bukti yang bisa dicek.

## 1. Arti status

- `PRD`/`AUDIT`/`UNVERIFIED` mengikuti definisi di dokumen kerja internal.
- Hasil pemindaian pihak ketiga baru berlaku setelah tanggal pemindaian dicatat
  di tabel bagian 4. Jangan tulis "sudah lolos VirusTotal" sebelum ada angkanya.

## 2. Sumber kebenaran integritas: `SHA256SUMS.txt`

Setiap rilis (sejak v0.5.29) menerbitkan `SHA256SUMS.txt` yang dibuat di CI dari
file yang benar-benar diunggah. Cara cek di Windows (PowerShell):

```powershell
$base = "https://rdp.xydesk.my.id"
iwr -UseBasicParsing "$base/arm64-v8a.apk" -OutFile .\xy-apk
iwr -UseBasicParsing "$base/agent.ps1" -OutFile .\agent.ps1
Get-FileHash .\xy-apk -Algorithm SHA256
```

Bandingkan hasilnya dengan baris `app-arm64-v8a-release.apk` di `SHA256SUMS.txt`
(ambil dari halaman rilis GitHub `xykal/XyDesk-Remote`, tag rilis terbaru).

Catatan: `rdp.xydesk.my.id` mengarahkan (HTTP 302) ke URL aset resmi GitHub.
Kalau hash berbeda, hentikan pemasangan dan laporkan.

## 3. Prosedur VirusTotal (manual, tanpa API key)

1. Buka `https://www.virustotal.com/gui/home/upload`.
2. Unggah `app-arm64-v8a-release.apk` (atau varian ABI yang dipakai) + `XyDeskRemoteHost.zip`.
3. Catat: tanggal pemindaian, jumlah engine deteksi, dan hash SHA256 dari tab Details.
4. Tempel hasilnya ke tabel bagian 4 (jangan unggah ulang berkali-kali; satu upload
   per rilis cukup, lalu bagikan tautan hasilnya).

Kalau ingin otomatis di CI, dibutuhkan API key VirusTotal gratis:
`VT_API_KEY` sebagai repository secret, lalu langkah upload dijalankan di job
`publish-release`. Belum diaktifkan karena key belum tersedia.

### Deteksi palsu yang lazim pada klien RDP

- APK RDP memuat pustaka native (FreeRDP, OpenSSL, FFmpeg, OpenH264). Beberapa
  engine memberi label generik `Riskware`/`PUA` karena pola tersebut, bukan karena
  perilaku berbahaya. Baca "Behavior" dan "Relations" sebelum menyimpulkan.
- Aplikasi belum terdaftar di Play Protect (belum di Play Store) sehingga
  pemasangan manual bisa memunculkan peringatan "aplikasi tidak dikenal".
- Deteksi 1-3 dari 70+ engine dengan label generik: kirim tautan hasil ke kami,
  jangan langsung menganggap bersih.

## 4. Catatan hasil pemindaian

| Tanggal (UTC) | Versi | Berkas | Deteksi | Hash cocok | Tautan hasil |
| --- | --- | --- | --- | --- | --- |
| (belum dipindai) | 0.5.29 | app-arm64-v8a-release.apk | - | - | - |

## 5. Bahan cek yang bisa dilakukan sendiri tanpa alat pihak ketiga

- `unzip -l app-arm64-v8a-release.apk | grep '\.so$'` → daftar pustaka native;
  semua harus berasal dari proyek ini/upstream yang tercatat di `LICENSES`.
- Tidak ada SDK iklan/analitik di `app/build.gradle` (hanya AndroidX + Compose +
  modul internal). Kalau ini berubah, perbarui juga
  `docs/STORE_DESCRIPTION.md` bagian "Fitur utama".
- Izin di `AndroidManifest.xml` harus tetap minimal: network (dari modul
  `freeRDPCore`), `RECORD_AUDIO` (diminta saat dipakai, bukan saat pasang),
  `FOREGROUND_SERVICE*` untuk sesi.
- Kredensial disimpan lokal lewat vault AES-256-GCM (`features-security`), tidak
  ada unggahan ke server kami. Server yang disentuh app hanya host RDP pilihan
  pengguna dan portal rilis `rdp.xydesk.my.id` (layar Status).

## 6. Untuk reviewer store (ringkas)

| Pertanyaan umum | Jawaban |
| --- | --- |
| Data apa yang dikumpulkan? | Tidak ada ke server kami. Kredensial tersimpan lokal terenkripsi. |
| Iklan / analitik? | Tidak ada. |
| Izin sensitif? | Mikrofon (opsional, untuk mic RDP), penyimpanan (opsional, drive `XyDesk`), kamera (opsional, webcam remote). |
| Kontak keamanan | Lapor lewat halaman `Feedback` di app atau portal. |
| Cara verifikasi build | `SHA256SUMS.txt` + hasil VirusTotal pada tabel bagian 4. |
