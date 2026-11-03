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

/** AES-GCM with a key that lives in the Android Keystore. */
class SecureStore(private val ctx: Context? = null) {
    private val alias = "cardamom_key"
    private var fallbackKey: SecretKey? = null

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
            getFallbackKey()
        }
    }

    private fun getFallbackKey(): SecretKey {
        fallbackKey?.let { return it }
        val fKey = if (ctx != null) {
            val keyFile = File(ctx.filesDir, ".secure_seed")
            val bytes = if (keyFile.exists() && keyFile.length() == 32L) {
                keyFile.readBytes()
            } else {
                val b = ByteArray(32)
                SecureRandom().nextBytes(b)
                try { keyFile.writeBytes(b) } catch (e: Exception) {}
                b
            }
            SecretKeySpec(bytes, "AES")
        } else {
            val b = "CardamomLocalSecureKeySeed32Byte".toByteArray(Charsets.UTF_8).copyOf(32)
            SecretKeySpec(b, "AES")
        }
        fallbackKey = fKey
        return fKey
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
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


    fun delete(id: String) {
        _cards.value.firstOrNull { it.id == id }?.let { c ->
            listOfNotNull(c.originalPath, c.logoPath, c.customPath, c.frontImagePath, c.backImagePath).forEach { File(it).delete() }
        }
        commit(_cards.value.filterNot { it.id == id })
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

    fun delete(id: String) {
        _documents.value.firstOrNull { it.id == id }?.let { d ->
            val paths = (d.pagePaths + listOfNotNull(d.uriPath, d.pdfPath)).toSet()
            paths.forEach { File(it).delete() }
        }
        commit(_documents.value.filterNot { it.id == id })
    }
}


/** Backup / card-file format: JSON, optionally encrypted with a password (PBKDF2 + AES-GCM). */
object Backup {
    private const val MAGIC = "CARDAMOM1"

    fun build(cards: List<WalletCard>): ByteArray {
        val arr = JSONArray(cards.map { it.copy(logoPath = null, customPath = null, frontImagePath = null, backImagePath = null, originalPath = null, originalName = null, originalMime = null).toJson() })
        return JSONObject().put("app", "cardamom").put("version", 1).put("cards", arr).toString().toByteArray(Charsets.UTF_8)
    }

    fun parse(bytes: ByteArray): List<WalletCard> {
        val arr = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("cards")
        return (0 until arr.length()).map { arr.getJSONObject(it).toCard() }
    }

    fun isEncrypted(b: ByteArray) = b.size > MAGIC.length && String(b, 0, MAGIC.length, Charsets.UTF_8) == MAGIC

    private fun derive(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, 120_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(plain: ByteArray, pass: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(pass, salt))
        return MAGIC.toByteArray(Charsets.UTF_8) + salt + c.iv + c.doFinal(plain)
    }

    fun decrypt(b: ByteArray, pass: CharArray): ByteArray {
        val o = MAGIC.length
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(pass, b.copyOfRange(o, o + 16)), GCMParameterSpec(128, b.copyOfRange(o + 16, o + 28)))
        return c.doFinal(b, o + 28, b.size - o - 28)
    }
}
