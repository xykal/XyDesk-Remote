package id.xydesk.remote.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresi untuk bug sign-extension: pembanding sidik jari dulu memakai
 * `byte.toInt()` sehingga byte >= 0x80 memperluas tanda dan build resmi
 * pun dianggap bajakan. Uji ini gagal pada kode lama dan lulus kini.
 */
class SignerMarkTest {
    private fun markBytes(): ByteArray = SignerMark.MARK.map { it.toByte() }.toByteArray()

    @Test
    fun `sidik jari resmi cocok`() {
        assertTrue(SignerMark.matches(markBytes()))
    }

    @Test
    fun `satu byte berbeda ditolak`() {
        val b = markBytes()
        b[0] = (b[0].toInt() xor 0x01).toByte()
        assertFalse(SignerMark.matches(b))
    }

    @Test
    fun `byte tinggi tidak memperluas tanda`() {
        // 0x84 sebagai Byte = -124; pembanding wajib membacanya 0x84.
        val b = markBytes()
        assertTrue(SignerMark.matches(b))
        assertTrue(b[0].toInt() < 0)
    }

    @Test
    fun `panjang berbeda ditolak`() {
        assertFalse(SignerMark.matches(markBytes().copyOfRange(0, 31)))
    }
}
