package id.xydesk.remote.ui.rdp

/**
 * Konfigurasi fitur "RDP Gratis" — create RDP dari dalam app memakai template
 * [TEMPLATE_REPO]. Setiap pengguna memakai AKUN GITHUB MILIKNYA SENDIRI:
 * app hanya membantu fork + trigger workflow + membaca status sesi dari fork
 * mereka lewat GitHub API. Tidak ada akun pusat, tidak ada server pusat.
 */
internal object XyRdpConfig {
    const val TEMPLATE_REPO = "xykal/XyRDP"
    /**
     * Metode pembuatan repo pengguna. Default: "Use this template"
     * (POST /repos/{owner}/{repo}/generate). Repo hasil template bukan fork,
     * jadi GitHub Actions aktif sejak awal dan tidak perlu merge-upstream.
     * Kalau repo sumber belum ditandai "Template repository" di GitHub,
     * XyRdpClient otomatis fallback ke metode fork lama.
     */
    const val PREFER_TEMPLATE = true
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
