package id.xydesk.remote.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/**
 * Bahasa aplikasi.
 *
 * Multi-bahasa 20 bahasa: tabel inti [XyText] diterjemahkan manual per
 * bahasa (batch `extra` MS/ES/PT/DE/FR dan `extra2` untuk 13 bahasa lain).
 * String sebaris lewat `xy()`: ID/MS memakai template Indonesia, EN memakai
 * template English, bahasa lain diterjemahkan runtime oleh [UiTranslator]
 * (batch via worker + cache permanen; EN tampil selama unduhan berjalan).
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

    /** Versi cache terjemahan runtime; dibaca composable agar render ulang. */
    private val trState = mutableStateOf(0)

    fun translationsVersion(): Int = trState.value

    fun notifyTranslationsUpdated() {
        trState.value += 1
    }

    /** Fallback EN dari tabel, diterjemahkan runtime bila bahasa aktif lain. */
    fun localizedFallback(key: String, enText: String): String {
        val lang = current()
        if (lang == XyLang.ID || lang == XyLang.EN) return enText
        return UiTranslator.lookup(lang, enText) ?: enText
    }

    /** Aman dibaca dari thread mana pun (non-composable). */
    fun current(): XyLang = langState.value

    fun t(key: String): String =
        table[key]?.get(current())
            ?: extra[key]?.get(current())
            ?: extra2[key]?.get(current())
            ?: table[key]?.get(XyLang.EN)
            ?: extra[key]?.get(XyLang.EN)
            ?: extra2[key]?.get(XyLang.EN)
            ?: table[key]?.get(XyLang.ID)
            ?: extra[key]?.get(XyLang.ID)
            ?: extra2[key]?.get(XyLang.ID)
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

    /** Batch 2 tabel inti: RU/AR/HI/JA/KO/ZH/VI/TH/TR/IT/NL/PL/UK. */
    private val extra2: Map<String, Map<XyLang, String>> = mapOf(
        "app.tagline" to mapOf(
            XyLang.RU to "Android RDP-клиент для Windows и Windows Server.",
            XyLang.AR to "عميل RDP لنظام Android لأجهزة Windows وWindows Server.",
            XyLang.HI to "Windows और Windows Server के लिए Android RDP क्लाइंट।",
            XyLang.JA to "WindowsおよびWindows Server向けAndroid RDPクライアント。",
            XyLang.KO to "Windows 및 Windows Server용 Android RDP 클라이언트.",
            XyLang.ZH to "适用于 Windows 和 Windows Server 的 Android RDP 客户端。",
            XyLang.VI to "Ứng dụng RDP Android cho Windows và Windows Server.",
            XyLang.TH to "ไคลเอนต์ RDP บน Android สำหรับ Windows และ Windows Server",
            XyLang.TR to "Windows ve Windows Server için Android RDP istemcisi.",
            XyLang.IT to "Client RDP Android per Windows e Windows Server.",
            XyLang.NL to "Android RDP-client voor Windows en Windows Server.",
            XyLang.PL to "Klient RDP na Androida dla Windows i Windows Server.",
            XyLang.UK to "Android RDP-клієнт для Windows і Windows Server.",
        ),
        "home.devices" to mapOf(XyLang.RU to "Устройства", XyLang.AR to "الأجهزة", XyLang.HI to "डिवाइस", XyLang.JA to "デバイス", XyLang.KO to "기기", XyLang.ZH to "设备", XyLang.VI to "Thiết bị", XyLang.TH to "อุปกรณ์", XyLang.TR to "Cihazlar", XyLang.IT to "Dispositivi", XyLang.NL to "Apparaten", XyLang.PL to "Urządzenia", XyLang.UK to "Пристрої"),
        "home.display" to mapOf(XyLang.RU to "Экран", XyLang.AR to "العرض", XyLang.HI to "डिस्प्ले", XyLang.JA to "表示", XyLang.KO to "디스플레이", XyLang.ZH to "显示", XyLang.VI to "Hiển thị", XyLang.TH to "จอแสดงผล", XyLang.TR to "Ekran", XyLang.IT to "Schermo", XyLang.NL to "Weergave", XyLang.PL to "Ekran", XyLang.UK to "Екран"),
        "home.keyboard" to mapOf(XyLang.RU to "Клавиатура и HUD", XyLang.AR to "لوحة المفاتيح وHUD", XyLang.HI to "कीबोर्ड और HUD", XyLang.JA to "キーボードとHUD", XyLang.KO to "키보드 및 HUD", XyLang.ZH to "键盘与 HUD", XyLang.VI to "Bàn phím & HUD", XyLang.TH to "แป้นพิมพ์และ HUD", XyLang.TR to "Klavye ve HUD", XyLang.IT to "Tastiera e HUD", XyLang.NL to "Toetsenbord en HUD", XyLang.PL to "Klawiatura i HUD", XyLang.UK to "Клавіатура і HUD"),
        "home.transport" to mapOf(XyLang.RU to "Транспорт по умолчанию", XyLang.AR to "النقل الافتراضي", XyLang.HI to "डिफ़ॉल्ट ट्रांसपोर्ट", XyLang.JA to "デフォルトの転送方式", XyLang.KO to "기본 전송 방식", XyLang.ZH to "默认传输方式", XyLang.VI to "Phương thức truyền mặc định", XyLang.TH to "การส่งข้อมูลเริ่มต้น", XyLang.TR to "Varsayılan aktarım", XyLang.IT to "Trasporto predefinito", XyLang.NL to "Standaardtransport", XyLang.PL to "Transport domyślny", XyLang.UK to "Транспорт за замовчуванням"),
        "home.security" to mapOf(XyLang.RU to "Безопасность", XyLang.AR to "الأمان", XyLang.HI to "सुरक्षा", XyLang.JA to "セキュリティ", XyLang.KO to "보안", XyLang.ZH to "安全", XyLang.VI to "Bảo mật", XyLang.TH to "ความปลอดภัย", XyLang.TR to "Güvenlik", XyLang.IT to "Sicurezza", XyLang.NL to "Beveiliging", XyLang.PL to "Bezpieczeństwo", XyLang.UK to "Безпека"),
        "home.about" to mapOf(XyLang.RU to "О программе", XyLang.AR to "حول", XyLang.HI to "परिचय", XyLang.JA to "情報", XyLang.KO to "정보", XyLang.ZH to "关于", XyLang.VI to "Giới thiệu", XyLang.TH to "เกี่ยวกับ", XyLang.TR to "Hakkında", XyLang.IT to "Informazioni", XyLang.NL to "Over", XyLang.PL to "Informacje", XyLang.UK to "Про програму"),
        "home.language" to mapOf(XyLang.RU to "Язык", XyLang.AR to "اللغة", XyLang.HI to "भाषा", XyLang.JA to "言語", XyLang.KO to "언어", XyLang.ZH to "语言", XyLang.VI to "Ngôn ngữ", XyLang.TH to "ภาษา", XyLang.TR to "Dil", XyLang.IT to "Lingua", XyLang.NL to "Taal", XyLang.PL to "Język", XyLang.UK to "Мова"),
        "home.language.sub" to mapOf(
            XyLang.RU to "Язык приложения; применяется сразу",
            XyLang.AR to "لغة التطبيق؛ تُطبّق فورًا",
            XyLang.HI to "ऐप की भाषा; तुरंत लागू होती है",
            XyLang.JA to "アプリの言語（即時反映）",
            XyLang.KO to "앱 언어; 즉시 적용됩니다",
            XyLang.ZH to "应用语言；立即生效",
            XyLang.VI to "Ngôn ngữ ứng dụng; áp dụng ngay",
            XyLang.TH to "ภาษาของแอป มีผลทันที",
            XyLang.TR to "Uygulama dili; hemen uygulanır",
            XyLang.IT to "Lingua dell'app; effetto immediato",
            XyLang.NL to "App-taal; direct van kracht",
            XyLang.PL to "Język aplikacji; działa od razu",
            XyLang.UK to "Мова застосунку; застосовується одразу",
        ),
        "home.new" to mapOf(XyLang.RU to "Новое устройство", XyLang.AR to "جهاز جديد", XyLang.HI to "नया डिवाइस", XyLang.JA to "新しいデバイス", XyLang.KO to "새 기기", XyLang.ZH to "新设备", XyLang.VI to "Thiết bị mới", XyLang.TH to "อุปกรณ์ใหม่", XyLang.TR to "Yeni cihaz", XyLang.IT to "Nuovo dispositivo", XyLang.NL to "Nieuw apparaat", XyLang.PL to "Nowe urządzenie", XyLang.UK to "Новий пристрій"),
        "home.connect" to mapOf(XyLang.RU to "Подключить", XyLang.AR to "اتصال", XyLang.HI to "कनेक्ट करें", XyLang.JA to "接続", XyLang.KO to "연결", XyLang.ZH to "连接", XyLang.VI to "Kết nối", XyLang.TH to "เชื่อมต่อ", XyLang.TR to "Bağlan", XyLang.IT to "Connetti", XyLang.NL to "Verbinden", XyLang.PL to "Połącz", XyLang.UK to "Підключити"),
        "home.edit" to mapOf(XyLang.RU to "Изменить", XyLang.AR to "تعديل", XyLang.HI to "बदलें", XyLang.JA to "編集", XyLang.KO to "편집", XyLang.ZH to "编辑", XyLang.VI to "Sửa", XyLang.TH to "แก้ไข", XyLang.TR to "Düzenle", XyLang.IT to "Modifica", XyLang.NL to "Bewerken", XyLang.PL to "Edytuj", XyLang.UK to "Змінити"),
        "home.delete" to mapOf(XyLang.RU to "Удалить", XyLang.AR to "حذف", XyLang.HI to "हटाएँ", XyLang.JA to "削除", XyLang.KO to "삭제", XyLang.ZH to "删除", XyLang.VI to "Xóa", XyLang.TH to "ลบ", XyLang.TR to "Sil", XyLang.IT to "Elimina", XyLang.NL to "Verwijderen", XyLang.PL to "Usuń", XyLang.UK to "Видалити"),
        "home.empty.title" to mapOf(XyLang.RU to "Устройств пока нет", XyLang.AR to "لا أجهزة بعد", XyLang.HI to "अभी कोई डिवाइस नहीं", XyLang.JA to "デバイスはまだありません", XyLang.KO to "아직 기기가 없습니다", XyLang.ZH to "尚无设备", XyLang.VI to "Chưa có thiết bị", XyLang.TH to "ยังไม่มีอุปกรณ์", XyLang.TR to "Henüz cihaz yok", XyLang.IT to "Ancora nessun dispositivo", XyLang.NL to "Nog geen apparaten", XyLang.PL to "Brak urządzeń", XyLang.UK to "Пристроїв ще немає"),
        "home.empty.body" to mapOf(
            XyLang.RU to "Добавьте адрес Windows или Windows Server, чтобы начать.",
            XyLang.AR to "أضف عنوان Windows أو Windows Server للبدء.",
            XyLang.HI to "शुरू करने के लिए Windows या Windows Server पता जोड़ें।",
            XyLang.JA to "WindowsまたはWindows Serverのアドレスを追加して開始します。",
            XyLang.KO to "시작하려면 Windows 또는 Windows Server 주소를 추가하세요.",
            XyLang.ZH to "添加 Windows 或 Windows Server 地址即可开始。",
            XyLang.VI to "Thêm địa chỉ Windows hoặc Windows Server để bắt đầu.",
            XyLang.TH to "เพิ่มที่อยู่ Windows หรือ Windows Server เพื่อเริ่มต้น",
            XyLang.TR to "Başlamak için bir Windows veya Windows Server adresi ekleyin.",
            XyLang.IT to "Aggiungi un indirizzo Windows o Windows Server per iniziare.",
            XyLang.NL to "Voeg een Windows- of Windows Server-adres toe om te beginnen.",
            XyLang.PL to "Dodaj adres Windows lub Windows Server, aby rozpocząć.",
            XyLang.UK to "Додайте адресу Windows або Windows Server, щоб почати.",
        ),
        "about.version" to mapOf(XyLang.RU to "Версия приложения", XyLang.AR to "إصدار التطبيق", XyLang.HI to "ऐप संस्करण", XyLang.JA to "アプリのバージョン", XyLang.KO to "앱 버전", XyLang.ZH to "应用版本", XyLang.VI to "Phiên bản ứng dụng", XyLang.TH to "เวอร์ชันแอป", XyLang.TR to "Uygulama sürümü", XyLang.IT to "Versione app", XyLang.NL to "App-versie", XyLang.PL to "Wersja aplikacji", XyLang.UK to "Версія застосунку"),
        "about.engine" to mapOf(XyLang.RU to "Движок RDP", XyLang.AR to "محرك RDP", XyLang.HI to "RDP इंजन", XyLang.JA to "RDPエンジン", XyLang.KO to "RDP 엔진", XyLang.ZH to "RDP 引擎", XyLang.VI to "Công cụ RDP", XyLang.TH to "เครื่องมือ RDP", XyLang.TR to "RDP motoru", XyLang.IT to "Motore RDP", XyLang.NL to "RDP-engine", XyLang.PL to "Silnik RDP", XyLang.UK to "Рушій RDP"),
        "about.developer" to mapOf(XyLang.RU to "Разработчик", XyLang.AR to "المطوّر", XyLang.HI to "डेवलपर", XyLang.JA to "開発者", XyLang.KO to "개발자", XyLang.ZH to "开发者", XyLang.VI to "Nhà phát triển", XyLang.TH to "ผู้พัฒนา", XyLang.TR to "Geliştirici", XyLang.IT to "Sviluppatore", XyLang.NL to "Ontwikkelaar", XyLang.PL to "Deweloper", XyLang.UK to "Розробник"),
        "about.license" to mapOf(XyLang.RU to "Посмотреть лицензию", XyLang.AR to "عرض الترخيص", XyLang.HI to "लाइसेंस देखें", XyLang.JA to "ライセンスを表示", XyLang.KO to "라이선스 보기", XyLang.ZH to "查看许可证", XyLang.VI to "Xem giấy phép", XyLang.TH to "ดูใบอนุญาต", XyLang.TR to "Lisansı görüntüle", XyLang.IT to "Vedi licenza", XyLang.NL to "Licentie bekijken", XyLang.PL to "Zobacz licencję", XyLang.UK to "Переглянути ліцензію"),
        "about.license.title" to mapOf(XyLang.RU to "Лицензия", XyLang.AR to "الترخيص", XyLang.HI to "लाइसेंस", XyLang.JA to "ライセンス", XyLang.KO to "라이선스", XyLang.ZH to "许可证", XyLang.VI to "Giấy phép", XyLang.TH to "ใบอนุญาต", XyLang.TR to "Lisans", XyLang.IT to "Licenza", XyLang.NL to "Licentie", XyLang.PL to "Licencja", XyLang.UK to "Ліцензія"),
        "about.support.title" to mapOf(XyLang.RU to "Поддержать меня", XyLang.AR to "ادعمني", XyLang.HI to "मेरा समर्थन करें", XyLang.JA to "支援する", XyLang.KO to "후원하기", XyLang.ZH to "支持我", XyLang.VI to "Ủng hộ tôi", XyLang.TH to "สนับสนุนฉัน", XyLang.TR to "Beni destekle", XyLang.IT to "Supportami", XyLang.NL to "Steun mij", XyLang.PL to "Wesprzyj mnie", XyLang.UK to "Підтримати мене"),
        "about.support.saweria" to mapOf(XyLang.RU to "Поддержать через Saweria", XyLang.AR to "الدعم عبر Saweria", XyLang.HI to "Saweria से समर्थन करें", XyLang.JA to "Saweriaで支援", XyLang.KO to "Saweria로 후원", XyLang.ZH to "通过 Saweria 支持", XyLang.VI to "Ủng hộ qua Saweria", XyLang.TH to "สนับสนุนผ่าน Saweria", XyLang.TR to "Saweria ile destekle", XyLang.IT to "Supporta via Saweria", XyLang.NL to "Steun via Saweria", XyLang.PL to "Wesprzyj przez Saweria", XyLang.UK to "Підтримати через Saweria"),
        "about.support.github" to mapOf(XyLang.RU to "Спонсоры GitHub", XyLang.AR to "رعاة GitHub", XyLang.HI to "GitHub प्रायोजक", XyLang.JA to "GitHubスポンサー", XyLang.KO to "GitHub 스폰서", XyLang.ZH to "GitHub 赞助者", XyLang.VI to "Nhà tài trợ GitHub", XyLang.TH to "ผู้สนับสนุน GitHub", XyLang.TR to "GitHub Sponsorları", XyLang.IT to "Sponsor GitHub", XyLang.NL to "GitHub-sponsors", XyLang.PL to "Sponsorzy GitHub", XyLang.UK to "Спонсори GitHub"),
        "about.support.star" to mapOf(XyLang.RU to "Поставить звезду на GitHub", XyLang.AR to "ضع نجمة على GitHub", XyLang.HI to "GitHub पर स्टार दें", XyLang.JA to "GitHubでスターを付ける", XyLang.KO to "GitHub에 스타 주기", XyLang.ZH to "在 GitHub 上点星", XyLang.VI to "Gắn sao trên GitHub", XyLang.TH to "ให้ดาวบน GitHub", XyLang.TR to "GitHub'da yıldız ver", XyLang.IT to "Metti una stella su GitHub", XyLang.NL to "Geef een ster op GitHub", XyLang.PL to "Daj gwiazdkę na GitHub", XyLang.UK to "Поставити зірку на GitHub"),
        "about.support.starred" to mapOf(XyLang.RU to "Спасибо", XyLang.AR to "شكرًا", XyLang.HI to "धन्यवाद", XyLang.JA to "ありがとう", XyLang.KO to "감사합니다", XyLang.ZH to "谢谢", XyLang.VI to "Cảm ơn", XyLang.TH to "ขอบคุณ", XyLang.TR to "Teşekkürler", XyLang.IT to "Grazie", XyLang.NL to "Bedankt", XyLang.PL to "Dziękuję", XyLang.UK to "Дякую"),
        "about.thanks" to mapOf(
            XyLang.RU to "Спасибо, что пользуетесь XyDesk Remote.",
            XyLang.AR to "شكرًا لاستخدامك XyDesk Remote.",
            XyLang.HI to "XyDesk Remote उपयोग करने के लिए धन्यवाद।",
            XyLang.JA to "XyDesk Remoteをご利用いただきありがとうございます。",
            XyLang.KO to "XyDesk Remote를 사용해 주셔서 감사합니다.",
            XyLang.ZH to "感谢使用 XyDesk Remote。",
            XyLang.VI to "Cảm ơn bạn đã dùng XyDesk Remote.",
            XyLang.TH to "ขอบคุณที่ใช้ XyDesk Remote",
            XyLang.TR to "XyDesk Remote kullandığınız için teşekkürler.",
            XyLang.IT to "Grazie per aver usato XyDesk Remote.",
            XyLang.NL to "Bedankt voor het gebruik van XyDesk Remote.",
            XyLang.PL to "Dziękujemy za korzystanie z XyDesk Remote.",
            XyLang.UK to "Дякуємо за використання XyDesk Remote.",
        ),
        "panel.input" to mapOf(XyLang.RU to "Ввод", XyLang.AR to "الإدخال", XyLang.HI to "इनपुट", XyLang.JA to "入力", XyLang.KO to "입력", XyLang.ZH to "输入", XyLang.VI to "Nhập liệu", XyLang.TH to "การป้อนข้อมูล", XyLang.TR to "Giriş", XyLang.IT to "Input", XyLang.NL to "Invoer", XyLang.PL to "Wejście", XyLang.UK to "Введення"),
        "panel.pointer" to mapOf(XyLang.RU to "Указатель", XyLang.AR to "المؤشر", XyLang.HI to "पॉइंटर", XyLang.JA to "ポインタ", XyLang.KO to "포인터", XyLang.ZH to "指针", XyLang.VI to "Con trỏ", XyLang.TH to "ตัวชี้", XyLang.TR to "İşaretçi", XyLang.IT to "Puntatore", XyLang.NL to "Aanwijzer", XyLang.PL to "Wskaźnik", XyLang.UK to "Вказівник"),
        "panel.buttons" to mapOf(XyLang.RU to "Кнопки", XyLang.AR to "الأزرار", XyLang.HI to "बटन", XyLang.JA to "ボタン", XyLang.KO to "버튼", XyLang.ZH to "按钮", XyLang.VI to "Nút bấm", XyLang.TH to "ปุ่ม", XyLang.TR to "Düğmeler", XyLang.IT to "Pulsanti", XyLang.NL to "Knoppen", XyLang.PL to "Przyciski", XyLang.UK to "Кнопки"),
        "panel.keyboard" to mapOf(XyLang.RU to "Клавиатура", XyLang.AR to "لوحة المفاتيح", XyLang.HI to "कीबोर्ड", XyLang.JA to "キーボード", XyLang.KO to "키보드", XyLang.ZH to "键盘", XyLang.VI to "Bàn phím", XyLang.TH to "แป้นพิมพ์", XyLang.TR to "Klavye", XyLang.IT to "Tastiera", XyLang.NL to "Toetsenbord", XyLang.PL to "Klawiatura", XyLang.UK to "Клавіатура"),
        "panel.screen" to mapOf(XyLang.RU to "Экран", XyLang.AR to "الشاشة", XyLang.HI to "स्क्रीन", XyLang.JA to "画面", XyLang.KO to "화면", XyLang.ZH to "屏幕", XyLang.VI to "Màn hình", XyLang.TH to "หน้าจอ", XyLang.TR to "Ekran", XyLang.IT to "Schermo", XyLang.NL to "Scherm", XyLang.PL to "Ekran", XyLang.UK to "Екран"),
        "panel.session" to mapOf(XyLang.RU to "Сессия", XyLang.AR to "الجلسة", XyLang.HI to "सत्र", XyLang.JA to "セッション", XyLang.KO to "세션", XyLang.ZH to "会话", XyLang.VI to "Phiên", XyLang.TH to "เซสชัน", XyLang.TR to "Oturum", XyLang.IT to "Sessione", XyLang.NL to "Sessie", XyLang.PL to "Sesja", XyLang.UK to "Сеанс"),
        "panel.tips" to mapOf(XyLang.RU to "Советы", XyLang.AR to "نصائح", XyLang.HI to "सुझाव", XyLang.JA to "ヒント", XyLang.KO to "팁", XyLang.ZH to "提示", XyLang.VI to "Mẹo", XyLang.TH to "เคล็ดลับ", XyLang.TR to "İpuçları", XyLang.IT to "Suggerimenti", XyLang.NL to "Tips", XyLang.PL to "Wskazówki", XyLang.UK to "Поради"),
        "panel.close" to mapOf(XyLang.RU to "Закрыть", XyLang.AR to "إغلاق", XyLang.HI to "बंद करें", XyLang.JA to "閉じる", XyLang.KO to "닫기", XyLang.ZH to "关闭", XyLang.VI to "Đóng", XyLang.TH to "ปิด", XyLang.TR to "Kapat", XyLang.IT to "Chiudi", XyLang.NL to "Sluiten", XyLang.PL to "Zamknij", XyLang.UK to "Закрити"),
        "panel.addKey" to mapOf(XyLang.RU to "Добавить кнопку", XyLang.AR to "إضافة زر", XyLang.HI to "बटन जोड़ें", XyLang.JA to "ボタンを追加", XyLang.KO to "버튼 추가", XyLang.ZH to "添加按钮", XyLang.VI to "Thêm nút", XyLang.TH to "เพิ่มปุ่ม", XyLang.TR to "Düğme ekle", XyLang.IT to "Aggiungi pulsante", XyLang.NL to "Knop toevoegen", XyLang.PL to "Dodaj przycisk", XyLang.UK to "Додати кнопку"),
        "panel.editDone" to mapOf(XyLang.RU to "Готово", XyLang.AR to "تم التحرير", XyLang.HI to "संपादन पूर्ण", XyLang.JA to "編集を完了", XyLang.KO to "편집 완료", XyLang.ZH to "完成编辑", XyLang.VI to "Sửa xong", XyLang.TH to "แก้ไขเสร็จ", XyLang.TR to "Düzenleme bitti", XyLang.IT to "Modifica completata", XyLang.NL to "Klaar met bewerken", XyLang.PL to "Zakończ edycję", XyLang.UK to "Готово"),
        "panel.mapMode" to mapOf(XyLang.RU to "Изменить раскладку", XyLang.AR to "تحرير التخطيط", XyLang.HI to "लेआउट बदलें", XyLang.JA to "レイアウトを編集", XyLang.KO to "레이아웃 편집", XyLang.ZH to "编辑布局", XyLang.VI to "Sửa bố cục", XyLang.TH to "แก้ไขเลย์เอาต์", XyLang.TR to "Düzeni düzenle", XyLang.IT to "Modifica layout", XyLang.NL to "Lay-out bewerken", XyLang.PL to "Edytuj układ", XyLang.UK to "Змінити розташування"),
        "panel.resetKeys" to mapOf(XyLang.RU to "Сбросить кнопки", XyLang.AR to "استعادة الأزرار الافتراضية", XyLang.HI to "डिफ़ॉल्ट बटन बहाल करें", XyLang.JA to "デフォルトのボタンに戻す", XyLang.KO to "기본 버튼 복원", XyLang.ZH to "恢复默认按钮", XyLang.VI to "Khôi phục nút mặc định", XyLang.TH to "คืนค่าปุ่มเริ่มต้น", XyLang.TR to "Varsayılan düğmeleri geri yükle", XyLang.IT to "Ripristina pulsanti predefiniti", XyLang.NL to "Standaardknoppen herstellen", XyLang.PL to "Przywróć przyciski domyślne", XyLang.UK to "Скинути кнопки"),
        "panel.disconnect" to mapOf(XyLang.RU to "Отключить сессию", XyLang.AR to "قطع الجلسة", XyLang.HI to "सत्र डिस्कनेक्ट करें", XyLang.JA to "セッションを切断", XyLang.KO to "세션 연결 끊기", XyLang.ZH to "断开会话", XyLang.VI to "Ngắt phiên", XyLang.TH to "ตัดการเชื่อมต่อเซสชัน", XyLang.TR to "Oturumu kes", XyLang.IT to "Disconnetti sessione", XyLang.NL to "Sessie verbreken", XyLang.PL to "Rozłącz sesję", XyLang.UK to "Відключити сеанс"),
        "panel.screenshot" to mapOf(XyLang.RU to "Сделать скриншот", XyLang.AR to "التقاط لقطة شاشة", XyLang.HI to "स्क्रीनशॉट लें", XyLang.JA to "スクリーンショットを撮る", XyLang.KO to "스크린샷 찍기", XyLang.ZH to "截图", XyLang.VI to "Chụp màn hình", XyLang.TH to "ถ่ายภาพหน้าจอ", XyLang.TR to "Ekran görüntüsü al", XyLang.IT to "Cattura schermata", XyLang.NL to "Schermafbeelding maken", XyLang.PL to "Zrób zrzut ekranu", XyLang.UK to "Зробити знімок екрана"),
        "pointer.show" to mapOf(XyLang.RU to "Показать указатель", XyLang.AR to "إظهار المؤشر", XyLang.HI to "पॉइंटर दिखाएँ", XyLang.JA to "ポインタを表示", XyLang.KO to "포인터 표시", XyLang.ZH to "显示指针", XyLang.VI to "Hiện con trỏ", XyLang.TH to "แสดงตัวชี้", XyLang.TR to "İşaretçiyi göster", XyLang.IT to "Mostra puntatore", XyLang.NL to "Aanwijzer tonen", XyLang.PL to "Pokaż wskaźnik", XyLang.UK to "Показати вказівник"),
        "keys.hold" to mapOf(XyLang.RU to "Удерживать", XyLang.AR to "استمرار الضغط", XyLang.HI to "दबाकर रखें", XyLang.JA to "長押し", XyLang.KO to "길게 누르기", XyLang.ZH to "按住", XyLang.VI to "Giữ", XyLang.TH to "กดค้าง", XyLang.TR to "Basılı tut", XyLang.IT to "Tieni premuto", XyLang.NL to "Ingedrukt houden", XyLang.PL to "Przytrzymaj", XyLang.UK to "Утримувати"),
        "keys.tap" to mapOf(XyLang.RU to "Одно нажатие", XyLang.AR to "نقرة واحدة", XyLang.HI to "एक बार टैप", XyLang.JA to "1回タップ", XyLang.KO to "한 번 탭", XyLang.ZH to "轻点一次", XyLang.VI to "Chạm một lần", XyLang.TH to "แตะครั้งเดียว", XyLang.TR to "Tek dokunuş", XyLang.IT to "Tocco singolo", XyLang.NL to "Eén tik", XyLang.PL to "Jedno dotknięcie", XyLang.UK to "Один дотик"),
        "keys.toggle" to mapOf(XyLang.RU to "Переключатель", XyLang.AR to "تبديل", XyLang.HI to "टॉगल", XyLang.JA to "切り替え", XyLang.KO to "전환", XyLang.ZH to "切换", XyLang.VI to "Bật/tắt", XyLang.TH to "สลับ", XyLang.TR to "Aç/kapat", XyLang.IT to "Interruttore", XyLang.NL to "Schakelaar", XyLang.PL to "Przełącznik", XyLang.UK to "Перемикач"),
        "keys.inToolbar" to mapOf(XyLang.RU to "На панели инструментов", XyLang.AR to "في شريط الأدوات", XyLang.HI to "टूलबार में", XyLang.JA to "ツールバー内", XyLang.KO to "도구 모음에", XyLang.ZH to "在工具栏中", XyLang.VI to "Trong thanh công cụ", XyLang.TH to "ในแถบเครื่องมือ", XyLang.TR to "Araç çubuğunda", XyLang.IT to "Nella barra degli strumenti", XyLang.NL to "In werkbalk", XyLang.PL to "Na pasku narzędzi", XyLang.UK to "На панелі інструментів"),
        "keys.onlyScreen" to mapOf(XyLang.RU to "Только на экране", XyLang.AR to "على الشاشة فقط", XyLang.HI to "केवल स्क्रीन पर", XyLang.JA to "画面のみ", XyLang.KO to "화면에만", XyLang.ZH to "仅屏幕显示", XyLang.VI to "Chỉ trên màn hình", XyLang.TH to "เฉพาะหน้าจอ", XyLang.TR to "Yalnızca ekran", XyLang.IT to "Solo schermo", XyLang.NL to "Alleen scherm", XyLang.PL to "Tylko ekran", XyLang.UK to "Тільки екран"),
        "keys.editTitle" to mapOf(XyLang.RU to "ИЗМЕНИТЬ КНОПКУ", XyLang.AR to "تعديل الزر", XyLang.HI to "बटन बदलें", XyLang.JA to "ボタンを編集", XyLang.KO to "버튼 편집", XyLang.ZH to "编辑按钮", XyLang.VI to "SỬA NÚT", XyLang.TH to "แก้ไขปุ่ม", XyLang.TR to "DÜĞMEYİ DÜZENLE", XyLang.IT to "MODIFICA PULSANTE", XyLang.NL to "KNOP BEWERKEN", XyLang.PL to "EDYTUJ PRZYCISK", XyLang.UK to "ЗМІНИТИ КНОПКУ"),
        "keys.addTitle" to mapOf(XyLang.RU to "ДОБАВИТЬ КНОПКУ", XyLang.AR to "إضافة زر", XyLang.HI to "बटन जोड़ें", XyLang.JA to "ボタンを追加", XyLang.KO to "버튼 추가", XyLang.ZH to "添加按钮", XyLang.VI to "THÊM NÚT", XyLang.TH to "เพิ่มปุ่ม", XyLang.TR to "DÜĞME EKLE", XyLang.IT to "AGGIUNGI PULSANTE", XyLang.NL to "KNOP TOEVOEGEN", XyLang.PL to "DODAJ PRZYCISK", XyLang.UK to "ДОДАТИ КНОПКУ"),
        "input.switchHint" to mapOf(XyLang.RU to "Перейти к прямому касанию", XyLang.AR to "التبديل إلى اللمس المباشر", XyLang.HI to "सीधे स्पर्श पर जाएँ", XyLang.JA to "ダイレクトタッチに切り替え", XyLang.KO to "직접 터치로 전환", XyLang.ZH to "切换到直接触控", XyLang.VI to "Chuyển sang chạm trực tiếp", XyLang.TH to "สลับไปสัมผัสโดยตรง", XyLang.TR to "Doğrudan dokunmaya geç", XyLang.IT to "Passa al tocco diretto", XyLang.NL to "Schakel naar direct aanraken", XyLang.PL to "Przełącz na dotyk bezpośredni", XyLang.UK to "Перейти до прямого дотику"),
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
    XyText.translationsVersion()
    return XyText.t(key)
}

/**
 * Teks dua bahasa langsung di tempat: [id] dipakai kalau bahasa Indonesia,
 * [en] kalau English. Ditulis berdampingan dengan pemakaiannya supaya tidak
 * ada kunci terjemahan yang lupa diisi, dan ikut render ulang saat bahasa
 * berganti. Argumen mengisi placeholder {0}, {1}, ...
 */
@Composable
fun xy(id: String, en: String, vararg args: Any?): String {
    val lang = xyLang()
    XyText.translationsVersion()
    val template = when (lang) {
        XyLang.ID, XyLang.MS -> id
        XyLang.EN -> en
        else -> UiTranslator.lookup(lang, en) ?: en
    }
    return fill(template, args)
}

/** Versi non-composable (notice, callback) — pakai bahasa yang sedang aktif. */
fun xyNow(id: String, en: String, vararg args: Any?): String {
    val lang = XyText.current()
    val template = when (lang) {
        XyLang.ID, XyLang.MS -> id
        XyLang.EN -> en
        else -> UiTranslator.lookup(lang, en) ?: en
    }
    return fill(template, args)
}

private fun fill(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    var out = template
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
    return out
}
