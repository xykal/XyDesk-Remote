# Tombol HUD kustom, toolbar keyboard, dan storage HP

XyDesk Remote memakai keyboard HP untuk mengetik dan sekumpulan tombol bulat
bebas untuk sisanya. Dokumen ini menjelaskan model yang dibangun di v0.5.0.

## 1. Satu tombol = satu aksi

Setiap tombol di layar adalah satu tombol bulat penuh (`CircleShape`, diameter
dalam dp). Tidak ada yang digabung jadi satu widget: klik kiri, klik kanan,
klik tengah, scroll naik, scroll turun, dan tombol ganti mode input adalah enam
tombol terpisah dengan ikon, posisi, ukuran, dan aksi masing-masing.

| Properti | Rentang | Cara mengubah |
|---|---|---|
| Posisi | ternormalisasi 0..1 dari area sesi | geser tombolnya; atau mode "Atur posisi" |
| Ukuran | 32..96 dp (diameter, radius = setengah) | tahan tombol, atau daftar tombol di panel kanan |
| Aksi | Sekali klik / Tahan / Toggle | editor tombol |
| Ikon di toolbar | nyala / mati | editor tombol |
| Tombol | apa pun dari katalog (F1..F12, numpad, huruf, simbol, panah, modifier) | tombol "Tambah tombol" |
| Kombinasi | Ctrl+C, Ctrl+Shift+Esc, Alt+Tab, Win+R, ... | grup katalog "Kombinasi siap pakai" |

Arti aksi:

- **Sekali klik** – tekan lalu lepas (mis. huruf, Enter, kombinasi).
- **Tahan** – aktif selama ditahan (drag pakai klik kiri, scroll menerus).
- **Toggle** – klik pertama nyala, klik kedua mati (mis. Ctrl sebagai modifier
  lengket).

Tombol disimpan per perangkat (`xydesk.input` → `<deviceId>.hudkeys`) karena
ukuran layar dan orientasi beda-beda. Format JSON, tanpa aset biner.

## 2. Toolbar di atas keyboard HP

Keyboard HP (IME sistem) jadi permukaan mengetik utama. Di atasnya ditempel
toolbar yang mengikuti tinggi IME sebenarnya, jadi tidak pernah menutupi
keyboard:

- sisi kiri: tombol yang ditandai "di toolbar" plus dua chip ikon (panel, pointer);
- paling ujung kanan: `123` → membuka board lengkap bawaan app (QWERTY + F1..F12
  + numpad);
- saat board terbuka chip berubah jadi `ABC` → menutup board dan mengembalikan
  keyboard HP;
- keyboard HP di-hide → toolbar dan board ikut hilang; toolbar hanya ada saat
  IME tampil.

Catatan implementasi: tinggi IME diambil dari `WindowInsetsCompat.Type.ime()` di
`SessionSurfaceController.installInsetsHandling` lalu diteruskan ke Compose
lewat `SessionSurfaceController.onImeChanged`. Tidak ada polling atau tebakan.

## 3. Storage HP jadi drive di remote

Opsi `drive` dipetakan ke `/drive:sdcard,<path>`. Sejak Android 11 app tidak
bisa lagi membaca `/storage/emulated/0` dengan bebas, jadi path dipilih saat
runtime oleh `LibFreeRDP.appDrivePath(Context)`:

1. kalau app punya izin "semua file" (`MANAGE_EXTERNAL_STORAGE`,
   `Environment.isExternalStorageManager()`) → seluruh storage eksternal;
2. kalau tidak → folder milik app (`Android/data/<paket>/files/Share`) yang
   selalu bisa dipakai tanpa izin apa pun.

Manifest app menyatakan izinnya (lewat merge manifest `freeRDPCore`) dan layar
ubah perangkat menampilkan status sekarang plus tombol yang membuka halaman izin
"semua file" untuk app ini. Drive jalan di kedua kondisi; akses penuh hanya
memperluas apa yang terlihat.

## 4. Diagnosa tanpa ADB

`XyApp` memasang environment log winpr sebelum library native disentuh:

```
WLOG_APPENDER=file
WLOG_LEVEL=INFO
WLOG_FILEAPPENDER_OUTPUT_FILE_PATH=<filesDir app>
WLOG_FILEAPPENDER_OUTPUT_FILE_NAME=freerdp-native.log
```

Dialog log di app (Umum → Masalah koneksi → "Lihat log sesi terakhir") menulis
ekor log native dulu, lalu log app. File itulah yang perlu dikirim saat audio,
mikrofon, clipboard, atau drive bermasalah — di dalamnya ada baris pemuatan
kanal dari sisi native.

## 5. Berkas

| Berkas | Peran |
|---|---|
| `ui/HudKey.kt` | model tombol, JSON, default, katalog |
| `ui/SessionKeyLayer.kt` | layer bebas, geser/tahan, toolbar, picker, editor |
| `ui/SessionControls.kt` | kerangka sesi: panel (kanan: input/pointer/tombol/keyboard, kiri: layar/sesi) |
| `ui/SessionPrefs.kt` | penyimpanan per perangkat: tombol, pointer, skala keyboard |
| `ui/Lang.kt` | tabel teks ID/EN dan preferensi bahasa |
| `ui/Wallpaper.kt` | `DevicePreviewArt` – preview perangkat prosedural (bukan wallpaper RDP) |
| `SessionSurfaceController.kt` | callback IME → penempatan toolbar |
| `LibFreeRDP.java` | `appDrivePath`, `hasAllFilesAccess` |
