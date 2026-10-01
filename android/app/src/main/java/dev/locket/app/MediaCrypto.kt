package dev.locket.app

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val MEDIA_MAGIC = byteArrayOf('L'.code.toByte(), 'K'.code.toByte(), 'T'.code.toByte(), 1)
private const val SALT_BYTES = 16
private const val NONCE_BYTES = 12
private const val TAG_BITS = 128
private const val ITERATIONS = 120_000

object MediaCrypto {
    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotBlank())
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        }
        return ByteArrayOutputStream().apply {
            write(MEDIA_MAGIC)
            write(salt)
            write(nonce)
            write(cipher.doFinal(plain))
        }.toByteArray()
    }

    fun decrypt(payload: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotBlank())
        require(payload.size > MEDIA_MAGIC.size + SALT_BYTES + NONCE_BYTES)
        require(payload.copyOfRange(0, MEDIA_MAGIC.size).contentEquals(MEDIA_MAGIC))
        val saltStart = MEDIA_MAGIC.size
        val nonceStart = saltStart + SALT_BYTES
        val cipherStart = nonceStart + NONCE_BYTES
        val salt = payload.copyOfRange(saltStart, nonceStart)
        val nonce = payload.copyOfRange(nonceStart, cipherStart)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
            doFinal(payload.copyOfRange(cipherStart, payload.size))
        }
    }

    fun isEncrypted(payload: ByteArray): Boolean = payload.size >= MEDIA_MAGIC.size && payload.copyOfRange(0, MEDIA_MAGIC.size).contentEquals(MEDIA_MAGIC)

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
