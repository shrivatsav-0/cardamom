package com.shrivatsav.cardamom

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {

    private val card = WalletCard(
        id = "test-id",
        name = "Test Card",
        issuer = "Test Issuer",
        code = "12345",
        format = CodeFormat.QR,
        fields = emptyList(),
    )

    @Test
    fun encryptedBackupRoundTripsWithV2Format() {
        val plain = Backup.build(listOf(card))
        val pass = "correct horse battery staple".toCharArray()
        val encrypted = Backup.encrypt(plain, pass)
        assertTrue(Backup.isEncrypted(encrypted))
        // v2 magic, not legacy v1
        assertEquals("CARDAMOM2", String(encrypted, 0, "CARDAMOM2".length, Charsets.UTF_8))
        val decrypted = Backup.decrypt(encrypted, pass)
        val restored = Backup.parse(decrypted)
        assertEquals(1, restored.size)
        assertEquals("Test Card", restored[0].name)
        assertEquals("12345", restored[0].code)
    }

    @Test
    fun wrongPasswordFails() {
        val plain = Backup.build(listOf(card))
        val encrypted = Backup.encrypt(plain, "correct horse battery staple".toCharArray())
        try {
            Backup.decrypt(encrypted, "wrong password 12345".toCharArray())
            fail("expected decryption to fail with wrong password")
        } catch (e: Exception) {
            // AES-GCM authentication failure expected
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortPasswordRejected() {
        Backup.encrypt(Backup.build(emptyList()), "short".toCharArray())
    }

    @Test
    fun backupIncludesDocumentMetadata() {
        val doc = WalletDocument(
            id = "doc-1",
            title = "Passport",
            docType = "National ID",
            issuer = "Govt of India",
            fields = listOf(CardField("Expiry", "2030-01-01")),
        )
        val plain = Backup.build(emptyList(), listOf(doc))
        val docs = Backup.parseDocuments(plain)
        assertEquals(1, docs.size)
        assertEquals("Passport", docs[0].title)
        assertEquals("Govt of India", docs[0].issuer)
        assertEquals("2030-01-01", docs[0].fields.first { it.label == "Expiry" }.value)
        // Local paths must not leak into the backup
        assertTrue(docs[0].pagePaths.isEmpty())
        assertTrue(docs[0].uriPath.isEmpty())
        assertEquals(null, docs[0].pdfPath)
    }

    @Test
    fun legacyCardsOnlyBackupParsesDocumentsAsEmpty() {
        // Pre-v3 backups have no "documents" array -> empty, not a crash.
        val plain = Backup.build(listOf(card))
        assertTrue(Backup.parseDocuments(plain).isEmpty())
    }
}
