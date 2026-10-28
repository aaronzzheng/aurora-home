package home.aurora

import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The family PIN (Kid lock, Settings, pairing a phone). Stored as a salted
 * PBKDF2 hash, never as the digits. PINs saved by older versions in plain text
 * are upgraded the first time they're entered correctly.
 */
object Pin {

    const val KEY = "lock_pin"
    private const val ITERATIONS = 20_000

    @JvmStatic
    fun isSet(p: SharedPreferences): Boolean = p.contains(KEY)

    @JvmStatic
    fun set(p: SharedPreferences, pin: String) {
        p.edit().putString(KEY, encode(pin, salt(), ITERATIONS)).apply()
    }

    /** Is [pin] right? Upgrades an old plain-text PIN once it's matched. */
    @JvmStatic
    fun check(p: SharedPreferences, pin: String?): Boolean {
        val stored = p.getString(KEY, null) ?: return false
        if (pin == null) return false
        if (!stored.startsWith("pbkdf2$")) {
            val ok = MessageDigest.isEqual(stored.toByteArray(), pin.toByteArray())
            if (ok) set(p, pin)
            return ok
        }
        return matches(stored, pin)
    }

    // ------------------------------------------------------------------ plain functions (tested)

    @JvmStatic
    fun encode(pin: String, salt: ByteArray, iterations: Int): String =
        "pbkdf2$$iterations$${hex(salt)}$${hex(derive(pin, salt, iterations))}"

    @JvmStatic
    fun matches(stored: String, pin: String): Boolean = try {
        val f = stored.split('$')
        f.size == 4 && MessageDigest.isEqual(unhex(f[3]), derive(pin, unhex(f[2]), f[1].toInt()))
    } catch (e: Exception) {
        false
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    private fun salt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    private fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
