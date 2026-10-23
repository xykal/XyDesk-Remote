package id.xydesk.remote.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/**
 * Bahasa aplikasi.
 *
 * Migrasi multi-bahasa bertahap: tabel inti [XyText] diterjemahkan per bahasa
 * (saat ini ID/EN penuh, MS/ES/PT/DE/FR untuk tabel inti), sisanya jatuh ke
 * English lalu Indonesia. String sebaris lewat `xy()` saat ini ID/EN
 * (MS memakai ID yang serumpun) dan dipindahkan ke tabel secara bertahap.
 * Pilihan disimpan di prefs dan dipakai seluruh layar lewat [XyText.t].
 */
enum class XyLang(val code: String, val label: String) {
    ID("id", "Indonesia"),
    EN("en", "English"),
    MS("ms", "Bahasa Melayu"),
    ES("es", "Español"),
    PT("pt", "Português"),
    DE("de", "Deutsch"),
    FR("fr", "Français"),
    RU("ru", "Русский"),
    AR("ar", "العربية"),
    HI("hi", "हिन्दी"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    ZH("zh-CN", "中文简体"),
    VI("vi", "Tiếng Việt"),
    TH("th", "ภาษาไทย"),
    TR("tr", "Türkçe"),
    IT("it", "Italiano"),
    NL("nl", "Nederlands"),
    PL("pl", "Polski"),
    UK("uk", "Українська"),
    ;

    companion object {
        /** Pilih default sekali dari bahasa sistem. */
        fun fromSystem(context: Context): XyLang {
            val tag = Locale.getDefault().language.trim().lowercase(Locale.ROOT)
            val cfg: Configuration = context.resources.configuration
            val cfgTag = cfg.locales[0]?.language.orEmpty().trim().lowercase(Locale.ROOT)
            return when (tag.ifBlank { cfgTag }) {
                "in", "id" -> ID
                "ms" -> MS
                "es" -> ES
                "pt" -> PT
                "de" -> DE
                "fr" -> FR
                "ru" -> RU
                "ar" -> AR
                "hi" -> HI
                "ja" -> JA
                "ko" -> KO
                "zh" -> ZH
                "vi" -> VI
                "th" -> TH
                "tr" -> TR
                "it" -> IT
                "nl" -> NL
                "pl" -> PL
                "uk", "ua" -> UK
                else -> EN
            }
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
        table[key]?.get(current())
            ?: extra[key]?.get(current())
            ?: table[key]?.get(XyLang.EN)
            ?: extra[key]?.get(XyLang.EN)
            ?: table[key]?.get(XyLang.ID)
            ?: extra[key]?.get(XyLang.ID)
            ?: key

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

    /**
     * Terjemahan tambahan tabel inti per bahasa (fase migrasi bahasa UI).
     * Bahasa yang belum diterjemahkan jatuh ke EN lalu ID lewat [t].
     * String sebaris lewat `xy()` saat ini baru ID/EN (MS memakai ID yang
     * serumpun); perluasan berikutnya memindahkan string itu ke tabel ini.
     */
    private val extra: Map<String, Map<XyLang, String>> = mapOf(
        "app.tagline" to mapOf(
            XyLang.MS to "Klien RDP Android untuk Windows dan Windows Server.",
            XyLang.ES to "Cliente RDP de Android para Windows y Windows Server.",
            XyLang.PT to "Cliente RDP Android para Windows e Windows Server.",
            XyLang.DE to "Android-RDP-Client für Windows und Windows Server.",
            XyLang.FR to "Client RDP Android pour Windows et Windows Server.",
        ),
        "home.devices" to mapOf(XyLang.MS to "Peranti", XyLang.ES to "Dispositivos", XyLang.PT to "Dispositivos", XyLang.DE to "Geräte", XyLang.FR to "Appareils"),
        "home.display" to mapOf(XyLang.MS to "Paparan", XyLang.ES to "Pantalla", XyLang.PT to "Tela", XyLang.DE to "Anzeige", XyLang.FR to "Affichage"),
        "home.keyboard" to mapOf(XyLang.MS to "Papan Kekunci & HUD", XyLang.ES to "Teclado y HUD", XyLang.PT to "Teclado e HUD", XyLang.DE to "Tastatur & HUD", XyLang.FR to "Clavier et HUD"),
        "home.transport" to mapOf(XyLang.MS to "Pengangkutan lalai", XyLang.ES to "Transporte predeterminado", XyLang.PT to "Transporte padrão", XyLang.DE to "Standardtransport", XyLang.FR to "Transport par défaut"),
        "home.security" to mapOf(XyLang.MS to "Keselamatan", XyLang.ES to "Seguridad", XyLang.PT to "Segurança", XyLang.DE to "Sicherheit", XyLang.FR to "Sécurité"),
        "home.about" to mapOf(XyLang.MS to "Perihal", XyLang.ES to "Acerca de", XyLang.PT to "Sobre", XyLang.DE to "Info", XyLang.FR to "À propos"),
        "home.language" to mapOf(XyLang.MS to "Bahasa", XyLang.ES to "Idioma", XyLang.PT to "Idioma", XyLang.DE to "Sprache", XyLang.FR to "Langue"),
        "home.language.sub" to mapOf(
            XyLang.MS to "Bahasa aplikasi; terus berkuat kuasa",
            XyLang.ES to "Idioma de la aplicación; se aplica al instante",
            XyLang.PT to "Idioma do aplicativo; aplicado imediatamente",
            XyLang.DE to "App-Sprache; gilt sofort",
            XyLang.FR to "Langue de l'application ; effet immédiat",
        ),
        "home.new" to mapOf(XyLang.MS to "Peranti baharu", XyLang.ES to "Nuevo dispositivo", XyLang.PT to "Novo dispositivo", XyLang.DE to "Neues Gerät", XyLang.FR to "Nouvel appareil"),
        "home.connect" to mapOf(XyLang.MS to "Sambung", XyLang.ES to "Conectar", XyLang.PT to "Conectar", XyLang.DE to "Verbinden", XyLang.FR to "Connecter"),
        "home.edit" to mapOf(XyLang.MS to "Ubah", XyLang.ES to "Editar", XyLang.PT to "Editar", XyLang.DE to "Bearbeiten", XyLang.FR to "Modifier"),
        "home.delete" to mapOf(XyLang.MS to "Padam", XyLang.ES to "Eliminar", XyLang.PT to "Excluir", XyLang.DE to "Löschen", XyLang.FR to "Supprimer"),
        "home.empty.title" to mapOf(XyLang.MS to "Belum ada peranti", XyLang.ES to "Aún no hay dispositivos", XyLang.PT to "Nenhum dispositivo ainda", XyLang.DE to "Noch keine Geräte", XyLang.FR to "Aucun appareil pour le moment"),
        "home.empty.body" to mapOf(
            XyLang.MS to "Tambah alamat Windows atau Windows Server untuk bermula.",
            XyLang.ES to "Agrega una dirección de Windows o Windows Server para empezar.",
            XyLang.PT to "Adicione um endereço Windows ou Windows Server para começar.",
            XyLang.DE to "Füge eine Windows- oder Windows-Server-Adresse hinzu, um zu starten.",
            XyLang.FR to "Ajoutez une adresse Windows ou Windows Server pour commencer.",
        ),
        "about.version" to mapOf(XyLang.MS to "Versi apl", XyLang.ES to "Versión de la aplicación", XyLang.PT to "Versão do aplicativo", XyLang.DE to "App-Version", XyLang.FR to "Version de l'application"),
        "about.engine" to mapOf(XyLang.MS to "Enjin RDP", XyLang.ES to "Motor RDP", XyLang.PT to "Mecanismo RDP", XyLang.DE to "RDP-Engine", XyLang.FR to "Moteur RDP"),
        "about.developer" to mapOf(XyLang.MS to "Pembangun", XyLang.ES to "Desarrollador", XyLang.PT to "Desenvolvedor", XyLang.DE to "Entwickler", XyLang.FR to "Développeur"),
        "about.license" to mapOf(XyLang.MS to "Lihat lesen", XyLang.ES to "Ver licencia", XyLang.PT to "Ver licença", XyLang.DE to "Lizenz ansehen", XyLang.FR to "Voir la licence"),
        "about.license.title" to mapOf(XyLang.MS to "Lesen", XyLang.ES to "Licencia", XyLang.PT to "Licença", XyLang.DE to "Lizenz", XyLang.FR to "Licence"),
        "about.support.title" to mapOf(XyLang.MS to "Sokong saya", XyLang.ES to "Apóyame", XyLang.PT to "Apoie-me", XyLang.DE to "Unterstütze mich", XyLang.FR to "Me soutenir"),
        "about.support.saweria" to mapOf(XyLang.MS to "Sokong melalui Saweria", XyLang.ES to "Apoyar vía Saweria", XyLang.PT to "Apoiar via Saweria", XyLang.DE to "Über Saweria unterstützen", XyLang.FR to "Soutenir via Saweria"),
        "about.support.github" to mapOf(XyLang.MS to "Penaja GitHub", XyLang.ES to "Patrocinadores de GitHub", XyLang.PT to "Patrocinadores do GitHub", XyLang.DE to "GitHub Sponsors", XyLang.FR to "Sponsors GitHub"),
        "about.support.star" to mapOf(XyLang.MS to "Beri bintang di GitHub", XyLang.ES to "Dar estrella en GitHub", XyLang.PT to "Dar estrela no GitHub", XyLang.DE to "Stern auf GitHub geben", XyLang.FR to "Mettre une étoile sur GitHub"),
        "about.support.starred" to mapOf(XyLang.MS to "Terima kasih", XyLang.ES to "Gracias", XyLang.PT to "Obrigado", XyLang.DE to "Danke", XyLang.FR to "Merci"),
        "about.thanks" to mapOf(
            XyLang.MS to "Terima kasih kerana menggunakan XyDesk Remote.",
            XyLang.ES to "Gracias por usar XyDesk Remote.",
            XyLang.PT to "Obrigado por usar o XyDesk Remote.",
            XyLang.DE to "Danke, dass du XyDesk Remote nutzt.",
            XyLang.FR to "Merci d'utiliser XyDesk Remote.",
        ),
        "panel.input" to mapOf(XyLang.MS to "Input", XyLang.ES to "Entrada", XyLang.PT to "Entrada", XyLang.DE to "Eingabe", XyLang.FR to "Entrée"),
        "panel.pointer" to mapOf(XyLang.MS to "Penunjuk", XyLang.ES to "Puntero", XyLang.PT to "Ponteiro", XyLang.DE to "Zeiger", XyLang.FR to "Pointeur"),
        "panel.buttons" to mapOf(XyLang.MS to "Butang", XyLang.ES to "Botones", XyLang.PT to "Botões", XyLang.DE to "Tasten", XyLang.FR to "Boutons"),
        "panel.keyboard" to mapOf(XyLang.MS to "Papan kekunci", XyLang.ES to "Teclado", XyLang.PT to "Teclado", XyLang.DE to "Tastatur", XyLang.FR to "Clavier"),
        "panel.screen" to mapOf(XyLang.MS to "Skrin", XyLang.ES to "Pantalla", XyLang.PT to "Tela", XyLang.DE to "Bildschirm", XyLang.FR to "Écran"),
        "panel.session" to mapOf(XyLang.MS to "Sesi", XyLang.ES to "Sesión", XyLang.PT to "Sessão", XyLang.DE to "Sitzung", XyLang.FR to "Session"),
        "panel.tips" to mapOf(XyLang.MS to "Petua", XyLang.ES to "Consejos", XyLang.PT to "Dicas", XyLang.DE to "Tipps", XyLang.FR to "Astuces"),
        "panel.close" to mapOf(XyLang.MS to "Tutup", XyLang.ES to "Cerrar", XyLang.PT to "Fechar", XyLang.DE to "Schließen", XyLang.FR to "Fermer"),
        "panel.addKey" to mapOf(XyLang.MS to "Tambah butang", XyLang.ES to "Agregar botón", XyLang.PT to "Adicionar botão", XyLang.DE to "Taste hinzufügen", XyLang.FR to "Ajouter un bouton"),
        "panel.editDone" to mapOf(XyLang.MS to "Selesai menyusun", XyLang.ES to "Terminar edición", XyLang.PT to "Concluir edição", XyLang.DE to "Bearbeitung fertig", XyLang.FR to "Terminer l'édition"),
        "panel.mapMode" to mapOf(XyLang.MS to "Susun kedudukan", XyLang.ES to "Editar diseño", XyLang.PT to "Editar layout", XyLang.DE to "Layout bearbeiten", XyLang.FR to "Modifier la disposition"),
        "panel.resetKeys" to mapOf(XyLang.MS to "Kembalikan butang lalai", XyLang.ES to "Restaurar botones predeterminados", XyLang.PT to "Restaurar botões padrão", XyLang.DE to "Standardtasten wiederherstellen", XyLang.FR to "Restaurer les boutons par défaut"),
        "panel.disconnect" to mapOf(XyLang.MS to "Putuskan sesi", XyLang.ES to "Desconectar sesión", XyLang.PT to "Desconectar sessão", XyLang.DE to "Sitzung trennen", XyLang.FR to "Déconnecter la session"),
        "panel.screenshot" to mapOf(XyLang.MS to "Ambil tangkapan skrin", XyLang.ES to "Tomar captura de pantalla", XyLang.PT to "Tirar captura de tela", XyLang.DE to "Screenshot aufnehmen", XyLang.FR to "Prendre une capture d'écran"),
        "pointer.show" to mapOf(XyLang.MS to "Tunjukkan penunjuk", XyLang.ES to "Mostrar puntero", XyLang.PT to "Mostrar ponteiro", XyLang.DE to "Zeiger anzeigen", XyLang.FR to "Afficher le pointeur"),
        "keys.hold" to mapOf(XyLang.MS to "Tahan", XyLang.ES to "Mantener", XyLang.PT to "Segurar", XyLang.DE to "Halten", XyLang.FR to "Maintenir"),
        "keys.tap" to mapOf(XyLang.MS to "Sekali ketik", XyLang.ES to "Un toque", XyLang.PT to "Um toque", XyLang.DE to "Einmal tippen", XyLang.FR to "Un seul appui"),
        "keys.toggle" to mapOf(XyLang.MS to "Togol", XyLang.ES to "Alternar", XyLang.PT to "Alternar", XyLang.DE to "Umschalten", XyLang.FR to "Basculer"),
        "keys.inToolbar" to mapOf(XyLang.MS to "Di bar alat", XyLang.ES to "En la barra de herramientas", XyLang.PT to "Na barra de ferramentas", XyLang.DE to "In der Symbolleiste", XyLang.FR to "Dans la barre d'outils"),
        "keys.onlyScreen" to mapOf(XyLang.MS to "Hanya di skrin", XyLang.ES to "Solo en pantalla", XyLang.PT to "Somente na tela", XyLang.DE to "Nur auf dem Bildschirm", XyLang.FR to "Écran uniquement"),
        "keys.editTitle" to mapOf(XyLang.MS to "UBAH BUTANG", XyLang.ES to "EDITAR BOTÓN", XyLang.PT to "EDITAR BOTÃO", XyLang.DE to "TASTE BEARBEITEN", XyLang.FR to "MODIFIER LE BOUTON"),
        "keys.addTitle" to mapOf(XyLang.MS to "TAMBAH BUTANG", XyLang.ES to "AGREGAR BOTÓN", XyLang.PT to "ADICIONAR BOTÃO", XyLang.DE to "TASTE HINZUFÜGEN", XyLang.FR to "AJOUTER UN BOUTON"),
        "input.switchHint" to mapOf(XyLang.MS to "Beralih ke sentuhan langsung", XyLang.ES to "Cambiar a toque directo", XyLang.PT to "Mudar para toque direto", XyLang.DE to "Zu direkter Berührung wechseln", XyLang.FR to "Passer au toucher direct"),
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
    fill(if (xyLang() == XyLang.ID || xyLang() == XyLang.MS) id else en, args)

/** Versi non-composable (notice, callback) — pakai bahasa yang sedang aktif. */
fun xyNow(id: String, en: String, vararg args: Any?): String =
    fill(if (XyText.current() == XyLang.ID || XyText.current() == XyLang.MS) id else en, args)

private fun fill(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    var out = template
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
    return out
}
