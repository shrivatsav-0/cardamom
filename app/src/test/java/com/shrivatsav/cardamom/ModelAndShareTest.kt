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

    @Test
    fun testSafeDocFileNameMatchesSavedName() {
        val docWithTitle = WalletDocument(title = "Passport - US", docType = "Passport")
        assertEquals("Passport - US", Share.safeDocFileName(docWithTitle))

        val docWithoutTitle = WalletDocument(title = "", docType = "National ID")
        assertEquals("National ID", Share.safeDocFileName(docWithoutTitle))

        val docWithInvalidChars = WalletDocument(title = "My/Doc:Name?*<test>|", docType = "")
        assertEquals("My_Doc_Name_test_", Share.safeDocFileName(docWithInvalidChars))

        val blankDoc = WalletDocument(title = "", docType = "")
        assertEquals("Document", Share.safeDocFileName(blankDoc))
    }

    @Test
    fun testCardDetailsTextContainsAllAttributes() {
        val card = WalletCard(
            name = "Costco Membership",
            issuer = "Costco Wholesale",
            category = "Membership",
            code = "1234567890",
            format = CodeFormat.CODE128,
            fields = listOf(
                CardField("Member ID", "C-987654"),
                CardField("Valid Thru", "12/28")
            ),
            points = "250 pts",
            status = "Executive",
            website = "https://costco.com"
        )
        val details = Share.cardDetailsText(card)
        assertTrue(details.contains("Costco Membership"))
        assertTrue(details.contains("Costco Wholesale"))
        assertTrue(details.contains("Membership"))
        assertTrue(details.contains("1234567890"))
        assertTrue(details.contains("Member ID: C-987654"))
        assertTrue(details.contains("Valid Thru: 12/28"))
        assertTrue(details.contains("Points: 250 pts"))
        assertTrue(details.contains("Status: Executive"))
        assertTrue(details.contains("Website: https://costco.com"))
    }

    @Test
    fun testDocumentDetailsTextContainsAllAttributes() {
        val doc = WalletDocument(
            title = "Driver License",
            docType = "National ID",
            issuer = "State Department",
            fields = listOf(
                CardField("License No", "D1234567"),
                CardField("Expires", "2030-05-01"),
                CardField("Class", "C")
            )
        )
        val details = Share.documentDetailsText(doc)
        assertTrue(details.contains("Driver License"))
        assertTrue(details.contains("Type: National ID"))
        assertTrue(details.contains("Issuer: State Department"))
        assertTrue(details.contains("License No: D1234567"))
        assertTrue(details.contains("Expires: 2030-05-01"))
        assertTrue(details.contains("Class: C"))
    }

    @Test
    fun testShareCardSafeName() {
        assertEquals("Starbucks_Gold", Share.safe("Starbucks Gold"))
        assertEquals("My_Card_123", Share.safe("My #Card @123!"))
        assertEquals("card", Share.safe(""))
        assertEquals("card", Share.safe("___"))
    }

    @Test
    fun testDocumentDetailsNoDuplicateTitleType() {
        val doc = WalletDocument(title = "Passport", docType = "Passport", issuer = "Authority")
        val details = Share.documentDetailsText(doc)
        assertEquals("Passport\nIssuer: Authority", details)
    }
}
