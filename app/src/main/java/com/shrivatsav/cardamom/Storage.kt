package com.shrivatsav.cardamom

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM with a key that lives in the Android Keystore.
 * Fail-closed: if the Keystore is unavailable we throw rather than
 * falling back to a file-stored or hardcoded key (which would make the
 * "encryption" pointless — the key would sit next to the ciphertext).
 * The [ctx] parameter is retained for call-site compatibility and ignored.
 */
class SecureStore(private val ctx: Context? = null) {
    private val alias = "cardamom_key"

    private fun key(): SecretKey {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey) ?: run {
                val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                kg.init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).build()
                )
                kg.generateKey()
            }
        } catch (e: Exception) {
            throw IllegalStateException("Android Keystore unavailable; vault cannot be unlocked", e)
        }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size >= 12 + 16) { "ciphertext too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return c.doFinal(data, 12, data.size - 12)
    }
}

class CardRepository(private val ctx: Context) {
    private val store = SecureStore(ctx)
    private val file = File(ctx.filesDir, "cards.bin")
    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<WalletCard>> = _cards

    private fun load(): List<WalletCard> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(String(store.decrypt(file.readBytes()), Charsets.UTF_8))
            (0 until arr.length()).map { arr.getJSONObject(it).toCard() }
        }
    } catch (e: Exception) {
        if (file.exists() && file.length() > 0) {
            val backup = File(ctx.filesDir, "cards_corrupted_${System.currentTimeMillis()}.bin")
            file.copyTo(backup, overwrite = true)
        }
        emptyList()
    }
    private fun commit(list: List<WalletCard>) {
        _cards.value = list
        val json = JSONArray(list.map { it.toJson() }).toString()
        file.writeBytes(store.encrypt(json.toByteArray(Charsets.UTF_8)))
        try { CardWidgetProvider.notifyUpdateAll(ctx) } catch (e: Exception) {}
    }

    fun upsert(c: WalletCard) {
        val l = _cards.value.toMutableList()
        val i = l.indexOfFirst { it.id == c.id }
        if (i >= 0) l[i] = c else l.add(0, c)
        commit(l)
    }
    fun saveAll(list: List<WalletCard>) {
        commit(list)
    }


    /**
     * Delete with undo support: files are MOVED to trash/<id>/ instead of
     * being destroyed, so [restore] can bring everything back. Trash older
     * than 7 days is purged on next repository creation.
     */
    fun delete(id: String): WalletCard? {
        val c = _cards.value.firstOrNull { it.id == id } ?: run {
            commit(_cards.value.filterNot { it.id == id })
            return null
        }
        moveToTrash(id, listOfNotNull(c.originalPath, c.logoPath, c.customPath, c.frontImagePath, c.backImagePath))
        commit(_cards.value.filterNot { it.id == id })
        return c
    }

    fun restore(c: WalletCard) {
        val trashDir = File(ctx.filesDir, "trash/${c.id}")
        if (trashDir.exists()) {
            listOfNotNull(c.originalPath, c.logoPath, c.customPath, c.frontImagePath, c.backImagePath).forEach { orig ->
                val trashed = File(trashDir, File(orig).name)
                if (trashed.exists()) {
                    val dest = File(orig)
                    dest.parentFile?.mkdirs()
                    try { trashed.renameTo(dest) } catch (_: Exception) { try { trashed.copyTo(dest, overwrite = true); trashed.delete() } catch (_: Exception) {} }
                }
            }
            try { trashDir.deleteRecursively() } catch (_: Exception) {}
        }
        upsert(c)
    }

    private fun moveToTrash(id: String, paths: List<String>) {
        val trashDir = File(ctx.filesDir, "trash/$id")
        trashDir.mkdirs()
        paths.forEach { p ->
            val src = File(p)
            if (src.exists()) {
                val dest = File(trashDir, src.name)
                try { src.renameTo(dest) } catch (_: Exception) { try { src.copyTo(dest, overwrite = true); src.delete() } catch (_: Exception) {} }
            }
        }
    }
}

class DocumentRepository(private val ctx: Context) {
    private val store = SecureStore(ctx)
    private val file = File(ctx.filesDir, "documents.bin")
    private val _documents = MutableStateFlow(load())
    val documents: StateFlow<List<WalletDocument>> = _documents

    private fun load(): List<WalletDocument> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(String(store.decrypt(file.readBytes()), Charsets.UTF_8))
            (0 until arr.length()).map { arr.getJSONObject(it).toDocument() }
        }
    } catch (e: Exception) {
        if (file.exists() && file.length() > 0) {
            val backup = File(ctx.filesDir, "docs_corrupted_${System.currentTimeMillis()}.bin")
            file.copyTo(backup, overwrite = true)
        }
        emptyList()
    }

    private fun commit(list: List<WalletDocument>) {
        _documents.value = list
        val json = JSONArray(list.map { it.toJson() }).toString()
        file.writeBytes(store.encrypt(json.toByteArray(Charsets.UTF_8)))
    }

    fun upsert(d: WalletDocument) {
        val l = _documents.value.toMutableList()
        val i = l.indexOfFirst { it.id == d.id }
        if (i >= 0) l[i] = d else l.add(0, d)
        commit(l)
    }
    fun saveAll(list: List<WalletDocument>) = commit(list)

    fun delete(id: String): WalletDocument? {
        val d = _documents.value.firstOrNull { it.id == id } ?: run {
            commit(_documents.value.filterNot { it.id == id })
            return null
        }
        val paths = (d.pagePaths + listOfNotNull(d.uriPath, d.pdfPath)).toSet()
        val trashDir = File(ctx.filesDir, "trash/$id")
        trashDir.mkdirs()
        paths.forEach { p ->
            val src = File(p)
            if (src.exists()) {
                val dest = File(trashDir, src.name)
                try { src.renameTo(dest) } catch (_: Exception) { try { src.copyTo(dest, overwrite = true); src.delete() } catch (_: Exception) {} }
            }
        }
        commit(_documents.value.filterNot { it.id == id })
        return d
    }

    fun restore(d: WalletDocument) {
        val trashDir = File(ctx.filesDir, "trash/${d.id}")
        if (trashDir.exists()) {
            val paths = (d.pagePaths + listOfNotNull(d.uriPath, d.pdfPath)).toSet()
            paths.forEach { orig ->
                val trashed = File(trashDir, File(orig).name)
                if (trashed.exists()) {
                    val dest = File(orig)
                    dest.parentFile?.mkdirs()
                    try { trashed.renameTo(dest) } catch (_: Exception) { try { trashed.copyTo(dest, overwrite = true); trashed.delete() } catch (_: Exception) {} }
                }
            }
            try { trashDir.deleteRecursively() } catch (_: Exception) {}
        }
        upsert(d)
    }
}


/** Backup / card-file format: JSON, optionally encrypted with a password (PBKDF2 + AES-GCM).
 * v2 format: "CARDAMOM2" | int32 iterations | 16B salt | 12B iv | ciphertext.
 * v1 ("CARDAMOM1", 120k iterations) is still readable for old backups. */
object Backup {
    private const val MAGIC = "CARDAMOM1"
    private const val MAGIC_V2 = "CARDAMOM2"
    const val PBKDF2_ITERATIONS = 600_000
    private const val LEGACY_ITERATIONS = 120_000

    fun build(cards: List<WalletCard>, documents: List<WalletDocument> = emptyList()): ByteArray {
        val arr = JSONArray(cards.map { it.copy(logoPath = null, customPath = null, frontImagePath = null, backImagePath = null, originalPath = null, originalName = null, originalMime = null).toJson() })
        // Documents: metadata + extracted fields only. Local file paths are
        // device-specific and image bytes are NOT embedded (see note below).
        val docArr = JSONArray(documents.map {
            it.copy(uriPath = "", pdfPath = null, pagePaths = emptyList()).toJson()
        })
        return JSONObject().put("app", "cardamom").put("version", 3)
            .put("cards", arr).put("documents", docArr).toString().toByteArray(Charsets.UTF_8)
    }

    fun parse(bytes: ByteArray): List<WalletCard> {
        require(bytes.size <= 20_000_000) { "backup too large" }
        val arr = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("cards")
        require(arr.length() <= 10_000) { "too many cards in backup" }
        return (0 until arr.length()).map { arr.getJSONObject(it).toCard() }
    }

    /** Document metadata from a backup (empty for pre-v3 cards-only backups). */
    fun parseDocuments(bytes: ByteArray): List<WalletDocument> {
        require(bytes.size <= 20_000_000) { "backup too large" }
        val obj = JSONObject(String(bytes, Charsets.UTF_8))
        val arr = obj.optJSONArray("documents") ?: return emptyList()
        require(arr.length() <= 10_000) { "too many documents in backup" }
        return (0 until arr.length()).map { arr.getJSONObject(it).toDocument() }
    }

    fun isEncrypted(b: ByteArray): Boolean {
        if (b.size <= MAGIC_V2.length) return false
        val head = String(b, 0, MAGIC_V2.length, Charsets.UTF_8)
        return head == MAGIC || head == MAGIC_V2
    }

    private fun derive(pass: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        require(iterations in 100_000..5_000_000) { "invalid iteration count" }
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, iterations, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(plain: ByteArray, pass: CharArray): ByteArray {
        require(pass.size >= 12) { "backup password must be at least 12 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(pass, salt, PBKDF2_ITERATIONS))
        val iterBytes = java.nio.ByteBuffer.allocate(4).putInt(PBKDF2_ITERATIONS).array()
        return MAGIC_V2.toByteArray(Charsets.UTF_8) + iterBytes + salt + c.iv + c.doFinal(plain)
    }

    fun decrypt(b: ByteArray, pass: CharArray): ByteArray {
        val head = String(b, 0, MAGIC_V2.length, Charsets.UTF_8)
        return when (head) {
            MAGIC_V2 -> {
                val o = MAGIC_V2.length
                require(b.size >= o + 4 + 16 + 12 + 16) { "ciphertext too short" }
                val iterations = java.nio.ByteBuffer.wrap(b, o, 4).int
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, derive(pass, b.copyOfRange(o + 4, o + 20), iterations), GCMParameterSpec(128, b.copyOfRange(o + 20, o + 32)))
                c.doFinal(b, o + 32, b.size - o - 32)
            }
            MAGIC -> {
                val o = MAGIC.length
                require(b.size >= o + 16 + 12 + 16) { "ciphertext too short" }
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, derive(pass, b.copyOfRange(o, o + 16), LEGACY_ITERATIONS), GCMParameterSpec(128, b.copyOfRange(o + 16, o + 28)))
                c.doFinal(b, o + 28, b.size - o - 28)
            }
            else -> throw IllegalArgumentException("not an encrypted backup")
        }
    }
}
