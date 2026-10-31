package com.shrivatsav.cardamom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAndShareTest {

    @Test
    fun testCodeFormatIs2D() {
        assertTrue(CodeFormat.QR.is2D)
        assertTrue(CodeFormat.PDF417.is2D)
        assertTrue(CodeFormat.AZTEC.is2D)
        assertTrue(CodeFormat.DATAMATRIX.is2D)

        assertFalse(CodeFormat.CODE128.is2D)
        assertFalse(CodeFormat.EAN13.is2D)
        assertFalse(CodeFormat.CODE39.is2D)
        assertFalse(CodeFormat.UPCA.is2D)
    }

    @Test
    fun testWalletCardDefaultsAndInitials() {
        val card = WalletCard(name = "Target Circle", issuer = "Target")
        assertEquals("T", card.initials)

        val card2 = WalletCard(name = "Best Buy Rewards", issuer = "")
        assertEquals("BB", card2.initials)
    }

    @Test
    fun testBackupCardRoundTrip() {
        val card = WalletCard(
            name = "Test Card",
            issuer = "Test Issuer",
            code = "https://example.com/loyalty/12345",
            format = CodeFormat.QR,
            category = "Loyalty Card"
        )
        val jsonBytes = Backup.build(listOf(card))
        val restored = Backup.parse(jsonBytes)
        assertEquals(1, restored.size)
        assertEquals("Test Card", restored[0].name)
        assertEquals("https://example.com/loyalty/12345", restored[0].code)
        assertEquals(CodeFormat.QR, restored[0].format)
    }
}
