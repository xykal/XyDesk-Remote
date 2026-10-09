# AGENTS.md — XyDesk-Remote

Panduan singkat untuk agent yang bekerja di repo ini. Repo saudara: `xykal/XyDesk` (host Rust/NSIS + klien Android native `id.xyverse.xydesk`).

## Koordinasi lewat GitHub
- Kontrak bersama (jembatan audio UDP :4433, nama endpoint `XyDesk Virtual Microphone` / `CABLE Input`, DSP mic, field `meta` host, versi) dibahas di issue berlabel `koordinasi`: `xykal/XyDesk-Remote#8` (sisi ini) dan `xykal/XyDesk#34` (sisi XyDesk). Usulkan perubahan di issue dulu, lalu link PR-nya.
- Di awal sesi baca issue/PR `koordinasi` di kedua repo. Kalau agent lain punya PR di path yang sama, komentar di sana; jangan buat perubahan saingan.
- Perubahan ke `xykal/XyDesk` hanya lewat PR (`main` dilindungi, berlaku juga untuk admin). Jangan ubah `VERSION`, nama endpoint, tag protokol, atau default DSP di sana tanpa komentar di issue.
- Setelah merge yang menyentuh kontrak: komentar SHA, apa yang berubah, apa yang diharapkan dari sisi lain. Awali komentar dengan peran, contoh `[XyDesk-Remote]`.
- Beda desain: tulis satu opsi + trade-off per pihak di issue; kall yang memutuskan.

## Identitas dan gaya
- Commit sebagai `xykal <xykal@users.noreply.github.com>`, pesan commit menjelaskan perubahan nyata.
- Jangan commit token/secret; rahasia hanya lewat CI Secrets.
