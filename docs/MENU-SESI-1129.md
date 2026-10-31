# Menu Sesi fullscreen tidak bisa diketuk — akar masalah sebenarnya (1.1.29)

Laporan pemilik: setelah 1.1.27/1.1.28, Menu Sesi fullscreen tampil normal tetapi
**tidak ada satu pun tombol yang bisa dipencet**. Perbaikan di 1.1.28 (watchdog
state fokus slider) tidak menyentuh penyebab utamanya. Dokumen ini menjelaskan
akar masalah yang benar.

## Rantai penyebab

1. `XyDeskSession.kt` (root `Box` sesi) punya **lapisan gesture isolator**:

   ```kotlin
   if (connected && (mappingMode || controlsOverlayOpen || privacyCurtain)) {
       Box(
           Modifier
               .fillMaxSize()
               .zIndex(1f)
               .pointerInput(mappingMode, controlsOverlayOpen, privacyCurtain) {
                   awaitEachGesture {
                       awaitFirstDown(requireUnconsumed = false).consume()
                       do {
                           val event = awaitPointerEvent()
                           event.changes.forEach { it.consume() }   // down, move, up
                       } while (event.changes.any { it.pressed })
                   }
               },
       )
   }
   ```

   Tujuannya benar: saat panel/menu terbuka, jari tidak boleh meneruskan klik ke
   surface FreeRDP di bawahnya. Konsekuensinya, lapisan ini **mengonsumsi semua
   pointer event** yang lewat di posisinya.

2. `controlsOverlayOpen` di-set dari `onOverlayActiveChange(anyOverlayOpen)`, dan
   `anyOverlayOpen` di `SessionControls.kt` mencakup `panelOpen`. Jadi **lapisan
   isolator justru hidup persis saat Menu Sesi dibuka**.

3. Sebelum 1.1.27, `SessionControls` tidak punya pembungkus. Semua child-nya
   langsung menjadi child dari root `Box` `XyDeskSession`, dengan zIndex
   masing-masing: HUD `10f`, Canvas `11f`, panel fullscreen **`30f`**. Karena
   `30f > 1f`, panel selalu diproses lebih dulu dalam hit-test — tombol dapat
   event sebelum isolator sempat mengonsumsinya. Aman.

4. PR #25 (1.1.27) menambahkan `SliderFocusHost` yang membungkus **seluruh**
   `SessionControls` dengan `Box(Modifier.fillMaxSize())` **tanpa zIndex**.
   `zIndex` di Compose hanya mengurutkan sibling dalam parent yang sama; semua
   zIndex internal (10, 11, 30) kini terkurung di dalam Box baru yang zIndex-nya
   **0** di level root. Akibatnya isolator `zIndex(1f)` naik di atas seluruh UI
   sesi dan menelan semua klik. Menu tetap tergambar (isolator transparan), tapi
   mati.

Ini menjelaskan mengapa gejalanya muncul **setelah** update 1.1.27, dan mengapa
perbaikan state fokus slider di 1.1.28 tidak menyembuhkannya.

## Perbaikan

- `SliderFocusHost` sekarang menerima parameter `zIndex: Float = 0f` dan
  menerapkannya ke Box pembungkusnya: `Box(Modifier.fillMaxSize().zIndex(zIndex))`.
- `SessionControls` memanggil `SliderFocusHost(zIndex = 2f) { ... }`.
  - `2f > 1f` → seluruh UI sesi kembali diproses sebelum gesture isolator.
  - `2f < 32f` → privacy curtain (`zIndex(32f)`) tetap di atas, tombol
    "matikan stealth" masih bisa diketuk.
  - `XyOverlay` dan `XyDialog` memakai `androidx.compose.ui.window.Dialog`
    (window terpisah), jadi tidak terpengaruh zIndex internal root.
- Modifier `.pointerInput(Unit) {}` kosong pada Column panel fullscreen
  **dihapus**. Modifier itu ikut hit-test walau tidak consume; menyisihkannya
  mengurangi satu lapisan yang bisa memakan event sebelum child tombol.
  Import `pointerInput` yang kini tak terpakai juga dibuang.

Tidak ada perubahan pada `XyDeskSession.kt` — file itu termasuk jalur koneksi
yang diproteksi hash di `docs/recovery/PROTECTED-CONNECTION-PATHS.json`, jadi
perbaikannya sengaja dilakukan di sisi UI agar guard baseline tetap hijau.

## Mengapa yakin

Sebelum 1.1.27 strukturnya persis "panel zIndex 30 > isolator 1" dan menu
berfungsi. `zIndex = 2f` mengembalikan urutan yang sama secara efektif: dalam
`PointerEventPass.Main`, node dengan zIndex lebih tinggi diproses lebih dulu,
sehingga `clickable` pada panel menerima `down`/`up` sebelum isolator
mengonsumsinya. Isolator tetap berfungsi untuk area panel yang benar-benar
kosong (tidak ada child yang consume), yang memang tujuan awalnya.

## Yang belum bisa diverifikasi

Perubahan ini diverifikasi lewat kompilasi Kotlin, unit test JVM, dan parse PSI
149/149 file. **Belum diuji pada perangkat Android fisik maupun host RDP Windows
asli** — itu tetap bagian pemilik. Tidak ada emulator test yang bisa membuka
Menu Sesi tanpa sesi RDP yang benar-benar terhubung.
