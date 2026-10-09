package id.xydesk.remote.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/**
 * Bahasa aplikasi.
 *
 * Dua bahasa penuh untuk seluruh alur utama: Indonesia (default kalau bahasa
 * sistem Indonesia) dan English. Pilihan disimpan di prefs dan dipakai
 * seluruh layar lewat [XyText.t].
 */
enum class XyLang(val code: String, val label: String) {
    ID("id", "Indonesia"),
    EN("en", "English"),
    ;

    companion object {
        /** Pilih default sekali dari bahasa sistem. */
        fun fromSystem(context: Context): XyLang {
            val tag = Locale.getDefault().language
            if (tag.equals("in", true) || tag.equals("id", true)) return ID
            val cfg: Configuration = context.resources.configuration
            val cfgTag = cfg.locales[0]?.language.orEmpty()
            return if (cfgTag.equals("in", true) || cfgTag.equals("id", true)) ID else EN
        }
    }
}

/**
 * Kunci teks. Semua label UI yang dipakai di alur utama ada di sini.
 * Ditulis sebagai objek (bukan resource XML) karena banyak yang dipakai
 * sebagai argumen langsung atau dibentuk runtime.
 */
object XyText {
    /**
     * Bahasa aktif sebagai state Compose — BUKA volatile biasa. Dulu ini
     * `@Volatile var`, jadi mengganti bahasa tidak memberi tahu siapa pun:
     * layar cuma kebagian render ulang kalau kebetulan ada state lain yang
     * berubah, dan setengah UI tampil dengan bahasa lama. Sekarang setiap
     * `xy()` / `t()` / `xyLang()` berlangganan state ini, jadi satu ketuk
     * di pemilih bahasa mengganti SELURUH teks app seketika.
     */
    private val langState = mutableStateOf(XyLang.ID)

    fun setLang(value: XyLang) {
        langState.value = value
    }

    /** Aman dibaca dari thread mana pun (non-composable). */
    fun current(): XyLang = langState.value

    fun t(key: String): String =
        table[key]?.get(current()) ?: table[key]?.get(XyLang.ID) ?: key

    fun t(key: String, vararg args: Any?): String {
        var out = t(key)
        args.forEachIndexed { i, a -> out = out.replace("{" + i + "}", a.toString()) }
        return out
    }

    private val table: Map<String, Map<XyLang, String>> = mapOf(
        // ---- shell / home ----
        "app.tagline" to mapOf(
            XyLang.ID to "Klien RDP Android untuk Windows dan Windows Server.",
            XyLang.EN to "Android RDP client for Windows and Windows Server.",
        ),
        "home.devices" to mapOf(XyLang.ID to "Perangkat", XyLang.EN to "Devices"),
        "home.display" to mapOf(XyLang.ID to "Tampilan", XyLang.EN to "Display"),
        "home.keyboard" to mapOf(XyLang.ID to "Keyboard & HUD", XyLang.EN to "Keyboard & HUD"),
        "home.transport" to mapOf(XyLang.ID to "Transport default", XyLang.EN to "Default transport"),
        "home.security" to mapOf(XyLang.ID to "Keamanan", XyLang.EN to "Security"),
        "home.about" to mapOf(XyLang.ID to "Tentang", XyLang.EN to "About"),
        "home.language" to mapOf(XyLang.ID to "Bahasa", XyLang.EN to "Language"),
        "home.language.sub" to mapOf(
            XyLang.ID to "Bahasa aplikasi; berlaku langsung",
            XyLang.EN to "App language; applies immediately",
        ),
        "home.new" to mapOf(XyLang.ID to "Perangkat baru", XyLang.EN to "New device"),
        "home.connect" to mapOf(XyLang.ID to "Sambung", XyLang.EN to "Connect"),
        "home.edit" to mapOf(XyLang.ID to "Ubah", XyLang.EN to "Edit"),
        "home.delete" to mapOf(XyLang.ID to "Hapus", XyLang.EN to "Delete"),
        "home.empty.title" to mapOf(
            XyLang.ID to "Belum ada perangkat",
            XyLang.EN to "No devices yet",
        ),
        "home.empty.body" to mapOf(
            XyLang.ID to "Tambahkan alamat Windows atau Windows Server untuk mulai.",
            XyLang.EN to "Add a Windows or Windows Server address to start.",
        ),

        // ---- about + support ----
        "about.version" to mapOf(XyLang.ID to "Versi app", XyLang.EN to "App version"),
        "about.engine" to mapOf(XyLang.ID to "Mesin RDP", XyLang.EN to "RDP engine"),
        "about.developer" to mapOf(XyLang.ID to "Pengembang", XyLang.EN to "Developer"),
        "about.license" to mapOf(XyLang.ID to "Lihat lisensi", XyLang.EN to "View license"),
        "about.license.title" to mapOf(XyLang.ID to "Lisensi", XyLang.EN to "License"),
        "about.license.body" to mapOf(
            XyLang.ID to "FreeRDP — Apache License 2.0\n" +
                "Copyright (C) 2012-2026 FreeRDP contributors.\n\n" +
                "XyDesk Remote — \u00a9 XyVerse.\n" +
                "Font: Space Grotesk & Inter (SIL Open Font License 1.1).",
            XyLang.EN to "FreeRDP — Apache License 2.0\n" +
                "Copyright (C) 2012-2026 FreeRDP contributors.\n\n" +
                "XyDesk Remote — \u00a9 XyVerse.\n" +
                "Fonts: Space Grotesk & Inter (SIL Open Font License 1.1).",
        ),
        "about.support.title" to mapOf(
            XyLang.ID to "Dukung saya",
            XyLang.EN to "Support me",
        ),
        "about.support.body" to mapOf(
            XyLang.ID to "Aplikasi ini dibangun sendiri tanpa iklan dan tanpa " +
                "pelacakan. Kalau berguna, dukungan apa pun dipakai langsung untuk " +
                "biaya build, server uji, dan waktu ngoding.",
            XyLang.EN to "This app is built by one person, without ads and without " +
                "tracking. If it helps you, any support goes straight to build " +
                "costs, test servers, and coding time.",
        ),
        "about.support.saweria" to mapOf(
            XyLang.ID to "Dukung via Saweria",
            XyLang.EN to "Support via Saweria",
        ),
        "about.support.github" to mapOf(
            XyLang.ID to "GitHub Sponsor",
            XyLang.EN to "GitHub Sponsors",
        ),
        "about.support.star" to mapOf(
            XyLang.ID to "Beri bintang di GitHub",
            XyLang.EN to "Star on GitHub",
        ),
        "about.support.starred" to mapOf(
            XyLang.ID to "Terima kasih",
            XyLang.EN to "Thanks",
        ),
        "about.thanks" to mapOf(
            XyLang.ID to "Terima kasih sudah memakai XyDesk Remote.",
            XyLang.EN to "Thanks for using XyDesk Remote.",
        ),

        // ---- panel sesi ----
        "panel.input" to mapOf(XyLang.ID to "Input", XyLang.EN to "Input"),
        "panel.pointer" to mapOf(XyLang.ID to "Pointer", XyLang.EN to "Pointer"),
        "panel.buttons" to mapOf(XyLang.ID to "Tombol", XyLang.EN to "Buttons"),
        "panel.keyboard" to mapOf(XyLang.ID to "Keyboard", XyLang.EN to "Keyboard"),
        "panel.screen" to mapOf(XyLang.ID to "Layar", XyLang.EN to "Screen"),
        "panel.session" to mapOf(XyLang.ID to "Sesi", XyLang.EN to "Session"),
        "panel.tips" to mapOf(XyLang.ID to "Tips", XyLang.EN to "Tips"),
        "panel.close" to mapOf(XyLang.ID to "Tutup", XyLang.EN to "Close"),
        "panel.addKey" to mapOf(XyLang.ID to "Tambah tombol", XyLang.EN to "Add button"),
        "panel.editDone" to mapOf(XyLang.ID to "Selesai atur", XyLang.EN to "Done editing"),
        "panel.mapMode" to mapOf(XyLang.ID to "Atur posisi", XyLang.EN to "Edit layout"),
        "panel.resetKeys" to mapOf(
            XyLang.ID to "Kembalikan tombol bawaan",
            XyLang.EN to "Restore default buttons",
        ),
        "panel.disconnect" to mapOf(XyLang.ID to "Putuskan sesi", XyLang.EN to "Disconnect"),
        "panel.screenshot" to mapOf(XyLang.ID to "Ambil screenshot", XyLang.EN to "Screenshot"),
        "pointer.show" to mapOf(XyLang.ID to "Tampilkan pointer", XyLang.EN to "Show pointer"),
        "keys.hold" to mapOf(XyLang.ID to "Tahan", XyLang.EN to "Hold"),
        "keys.tap" to mapOf(XyLang.ID to "Sekali klik", XyLang.EN to "Single tap"),
        "keys.toggle" to mapOf(XyLang.ID to "Toggle", XyLang.EN to "Toggle"),
        "keys.inToolbar" to mapOf(XyLang.ID to "Di toolbar", XyLang.EN to "In toolbar"),
        "keys.onlyScreen" to mapOf(
            XyLang.ID to "Hanya di layar",
            XyLang.EN to "Screen only",
        ),
        "keys.editTitle" to mapOf(XyLang.ID to "UBAH TOMBOL", XyLang.EN to "EDIT BUTTON"),
        "keys.addTitle" to mapOf(XyLang.ID to "TAMBAH TOMBOL", XyLang.EN to "ADD BUTTON"),
        "input.switchHint" to mapOf(
            XyLang.ID to "Pindah ke sentuh langsung",
            XyLang.EN to "Switch to direct touch",
        ),
    )
}

/** Nama bahasa untuk switcher. */
object LangPrefs {
    @Volatile private var loaded = false

    fun install(context: Context): XyLang {
        val sp = context.getSharedPreferences("xydesk.lang", Context.MODE_PRIVATE)
        val stored = sp.getString("lang", null)
        val lang = stored?.let { code ->
            XyLang.entries.firstOrNull { it.code == code }
        } ?: XyLang.fromSystem(context)
        XyText.setLang(lang)
        loaded = true
        return lang
    }

    fun current(): XyLang = XyText.current()

    fun set(context: Context, lang: XyLang) {
        context.getSharedPreferences("xydesk.lang", Context.MODE_PRIVATE)
            .edit()
            .putString("lang", lang.code)
            .apply()
        XyText.setLang(lang)
    }

    fun isLoaded(): Boolean = loaded
}

/**
 * Bahasa aktif sebagai state Compose: membaca ini membuat composable
 * berlangganan perubahan bahasa — begitu [LangPrefs.set] dipanggil, semua
 * teks yang memakai `xy()` / `t()` render ulang sendiri.
 */
@Composable
fun xyLang(): XyLang = XyText.current()

/** Pintasan composable: pakai [XyText.t] dengan recomposition saat bahasa ganti. */
@Composable
fun t(key: String): String {
    xyLang()
    return XyText.t(key)
}

/**
 * Teks dua bahasa langsung di tempat: [id] dipakai kalau bahasa Indonesia,
 * [en] kalau English. Ditulis berdampingan dengan pemakaiannya supaya tidak
 * ada kunci terjemahan yang lupa diisi, dan ikut render ulang saat bahasa
 * berganti. Argumen mengisi placeholder {0}, {1}, ...
 */
@Composable
fun xy(id: String, en: String, vararg args: Any?): String =
    fill(if (xyLang() == XyLang.EN) en else id, args)

/** Versi non-composable (notice, callback) — pakai bahasa yang sedang aktif. */
fun xyNow(id: String, en: String, vararg args: Any?): String =
    fill(if (XyText.current() == XyLang.EN) en else id, args)

private fun fill(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    var out = template
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
    return out
}
