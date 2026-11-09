package com.shrivatsav.cardamom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM file encryption for vault images/documents at rest.
 *
 * Format: "CENC1" (5 bytes) | 12-byte IV | ciphertext.
 * Files WITHOUT the magic are treated as legacy plaintext and returned
 * as-is, so pre-encryption files keep working (gradual migration).
 * New writes are always encrypted via [writeEncryptedBytes] /
 * [compressEncryptedBitmap].
 *
 * Key lives in AndroidKeyStore under the same alias as the JSON vault
 * ("cardamom_key"), so one hardware-backed key protects everything and
 * existing cards.bin/documents.bin keep decrypting. Fail-closed: if the
 * Keystore is unavailable, encryption throws instead of writing plaintext.
 */
object VaultCrypto {
    private const val MAGIC = "CENC1"
    private const val ALIAS = "cardamom_key"

    fun isEncrypted(data: ByteArray): Boolean =
        data.size > MAGIC.length && String(data, 0, MAGIC.length, Charsets.UTF_8) == MAGIC

    fun isEncryptedFile(file: File): Boolean = try {
        if (!file.exists() || file.length() <= MAGIC.length) false
        else file.inputStream().use { it.readNBytes(MAGIC.length) }.let { head ->
            head.size == MAGIC.length && String(head, Charsets.UTF_8) == MAGIC
        }
    } catch (_: Exception) { false }

    private fun key(): SecretKey {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey) ?: run {
                val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                kg.init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).build()
                )
                kg.generateKey()
            }
        } catch (e: Exception) {
            throw IllegalStateException("Android Keystore unavailable; cannot encrypt file", e)
        }
    }

    fun encryptBytes(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return MAGIC.toByteArray(Charsets.UTF_8) + c.iv + c.doFinal(plain)
    }

    fun decryptBytes(data: ByteArray): ByteArray {
        if (!isEncrypted(data)) return data // legacy plaintext
        require(data.size >= MAGIC.length + 12 + 16) { "encrypted file too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        val o = MAGIC.length
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(o, o + 12)))
        return c.doFinal(data, o + 12, data.size - o - 12)
    }

    fun writeEncryptedBytes(file: File, plain: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(encryptBytes(plain))
    }

    fun readDecryptedBytes(file: File): ByteArray = decryptBytes(file.readBytes())

    /** Compress a bitmap and write it encrypted. Returns the target file. */
    fun compressEncryptedBitmap(
        file: File,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90,
    ): File {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(format, quality, out)
        writeEncryptedBytes(file, out.toByteArray())
        return file
    }

    /** Decode a bitmap from a file that may be encrypted or legacy plaintext. */
    fun decodeBitmapFile(file: File): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        val bytes = readDecryptedBytes(file)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun decodeBitmapPath(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try { decodeBitmapFile(File(path)) } catch (_: Exception) { null }
    }

    /**
     * Encrypt every legacy plaintext image/document file under [dir].
     * Returns number of files migrated. Safe to run repeatedly.
     */
    fun migrateDir(dir: File, extensions: Set<String> = setOf("jpg", "jpeg", "png", "pdf", "bin", "dat")): Int {
        if (!dir.exists()) return 0
        var migrated = 0
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            val ext = f.extension.lowercase()
            if (ext in extensions && !isEncryptedFile(f)) {
                try {
                    val plain = f.readBytes()
                    if (plain.isNotEmpty()) {
                        f.writeBytes(encryptBytes(plain))
                        migrated++
                    }
                } catch (_: Exception) { /* leave file as-is on failure */ }
            }
        }
        return migrated
    }

    /** Random helper kept for callers that need fresh nonces outside Cipher. */
    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun ctxFilesDir(ctx: Context): File = ctx.filesDir
}
