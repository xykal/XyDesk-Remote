package id.xydesk.remote.ui.rdp

/**
 * Konfigurasi fitur "RDP Gratis" — create RDP dari dalam app memakai template
 * [TEMPLATE_REPO]. Setiap pengguna memakai AKUN GITHUB MILIKNYA SENDIRI:
 * app hanya membantu fork + trigger workflow + membaca status sesi dari fork
 * mereka lewat GitHub API. Tidak ada akun pusat, tidak ada server pusat.
 */
internal object XyRdpConfig {
    const val TEMPLATE_REPO = "xykal/XyRDP"
    const val FORK_REPO_NAME = "XyRDP"
    const val WORKFLOW_FILE = "rdp-6h.yml"
    const val STATUS_REF = "status"
    const val STATUS_PATH = "out/rdp-status.json"

    /**
     * Syarat kedua unlock: gabung saluran/grup komunitas. GANTI konstanta ini
     * bila link komunitas resmi berubah — satu-satunya tempat yang dipakai.
     */
    const val CHANNEL_URL = "https://github.com/xykal/XyDesk-Remote"

    /** PAT disimpan terenkripsi di CredentialVault dengan id ini. */
    const val VAULT_PAT_ID = "xyrdp-github-pat"
}
