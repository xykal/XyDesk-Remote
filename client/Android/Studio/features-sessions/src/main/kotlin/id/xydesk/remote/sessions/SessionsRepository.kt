package id.xydesk.remote.sessions

import android.content.Context
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.security.CredentialVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * M1 — data layer favorites: metadata di Room, password di
 * [CredentialVault] (AES-GCM, key di Android Keystore).
 *
 * UI (M1.2) cukup consume [favorites] → dapat daftar [ConnectionProfile]
 * siap-connect (password sudah di-dekripsi di memori, tidak pernah di UI).
 */
class SessionsRepository(context: Context) {

    private val dao = XyDeskDatabase.get(context).favoriteDao()
    private val vault = CredentialVault(context)

    /** Daftar favorites, password sudah ter-load (null jika tidak diingat). */
    fun favorites(): Flow<List<ConnectionProfile>> =
        dao.observeAll().map { list ->
            list.map { e ->
                ConnectionProfile(
                    host = e.host,
                    port = e.port,
                    username = e.username,
                    password = if (e.rememberPassword) vault.get(e.id) else null,
                    domain = e.domain,
                    label = e.label,
                )
            }
        }

    /** Simpan/update favorite. [rememberPassword] false = hapus dari vault. */
    suspend fun save(profile: ConnectionProfile, rememberPassword: Boolean) {
        val existing = dao.byId(profile.id)
        if (rememberPassword && !profile.password.isNullOrEmpty()) {
            vault.put(profile.id, profile.password)
        } else {
            vault.remove(profile.id)
        }
        dao.upsert(
            FavoriteEntity(
                id = profile.id,
                host = profile.host,
                port = profile.port,
                username = profile.username,
                domain = profile.domain,
                label = profile.label,
                rememberPassword = rememberPassword,
                createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis(),
                lastUsedAtMs = System.currentTimeMillis(),
            )
        )
    }

    /** Tandai barusan dipakai (urutan "terakhir dipakai" di UI). */
    suspend fun touch(profile: ConnectionProfile) {
        dao.touch(profile.id, System.currentTimeMillis())
    }

    /** Hapus favorite + password-nya dari vault. */
    suspend fun remove(id: String) {
        dao.byId(id)?.let { dao.delete(it) }
        vault.remove(id)
    }

    /** Hapus semua favorites + semua password tersimpan. */
    suspend fun clear() {
        dao.clear()
        vault.clearAll()
    }
}
